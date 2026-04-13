package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.client.client.OAuth2Client
import com.sphereon.oauth2.client.command.FetchUserInfoResult
import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientCredentials
import com.sphereon.oauth2.common.token.OidcTokenClaimExtractor
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig
import com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import com.sphereon.oauth2.server.authorization.provider.UserInfo
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Federated User Authentication Provider
 *
 * Implements UserAuthenticationProvider by delegating authentication to an upstream
 * OIDC Provider (e.g., Keycloak, SURF). This is a reusable IDK component for the
 * common STS pattern: Authorization Server → upstream IdP.
 *
 * Flow:
 * 1. initiateAuthentication() → builds redirect URL to upstream IdP
 * 2. Upstream IdP authenticates user, redirects back to callback
 * 3. handleFederationCallback() → exchanges code, validates ID token, caches claims
 * 4. getUserInfo() → returns cached claims from upstream
 *
 * This provider does NOT have its own user store. All user data comes from the
 * upstream IdP's ID token and/or UserInfo endpoint (federated passthrough mode).
 */
class FederatedUserAuthenticationProvider(
    private val oauth2Client: OAuth2Client,
    private val providerConfig: FederationProviderConfig,
    private val tokenClaimExtractor: OidcTokenClaimExtractor,
    private val claimMapper: ((Map<String, Any>) -> Map<String, Any>)? = null
) : UserAuthenticationProvider {

    companion object {
        // Static maps — must survive across SessionScope instances (each HTTP request = new session)
        // In production, this should be backed by a persistent session store
        private val userClaimsCache = mutableMapOf<String, CachedUserInfo>()
        private val pendingFederations = mutableMapOf<String, PendingFederation>()
    }

    override suspend fun getAuthenticatedUser(
        sessionId: String
    ): IdkResult<AuthenticatedUser?, AuthenticationError> {
        val pending = pendingFederations.values.find { it.sessionId == sessionId && it.completed }
        if (pending != null) {
            return Ok(
                AuthenticatedUser(
                    userId = pending.userId ?: return Ok(null),
                    authenticatedAt = pending.authenticatedAt ?: Clock.System.now(),
                    authenticationMethod = AuthenticationMethod.OAUTH,
                    acr = "urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport",
                    amr = listOf("fed")
                )
            )
        }
        return Ok(null)
    }

    override suspend fun initiateAuthentication(
        sessionId: String,
        returnUrl: String,
        hint: AuthenticationHint?
    ): IdkResult<String, AuthenticationError> {
        // Fetch upstream metadata
        val metadata = oauth2Client.fetchAuthorizationServerMetadata(providerConfig.issuerUrl)
            .getOrElse {
                return Err(AuthenticationError.Generic(message = "Failed to fetch upstream metadata: ${it.message.defaultMessage}"))
            }

        // Generate state and nonce
        val state = generateSecureToken()
        val nonce = generateSecureToken()

        // Build the callback redirect URI (STS base URL + callback path)
        val callbackRedirectUri = returnUrl.substringBefore("?").trimEnd('/') + providerConfig.callbackPath

        // Build client authentication for PAR and token exchange
        val clientAuth = providerConfig.clientSecret?.let { secret ->
            ClientAuthenticationConfig.Post(
                credentials = ClientCredentials(
                    clientId = providerConfig.clientId,
                    clientSecret = secret
                )
            )
        }

        // Initiate authorization (includes PKCE, uses PAR if upstream supports it)
        val authResult = oauth2Client.initiateAuthorization(
            authorizationServerMetadata = metadata,
            clientId = providerConfig.clientId,
            redirectUri = callbackRedirectUri,
            scope = providerConfig.scopes.joinToString(" "),
            state = state,
            clientAuthentication = clientAuth,
            additionalParameters = buildMap {
                put("nonce", nonce)
                hint?.loginHint?.let { put("login_hint", it) }
            }
        ).getOrElse {
            return Err(AuthenticationError.Generic(message = "Failed to initiate upstream authorization: ${it.message.defaultMessage}"))
        }

        // Cache pending federation state
        pendingFederations[state] = PendingFederation(
            sessionId = sessionId,
            state = state,
            nonce = nonce,
            pkceData = authResult.pkceData,
            metadata = metadata,
            returnUrl = returnUrl,
            callbackRedirectUri = callbackRedirectUri,
            completed = false
        )

        // If authorizationEndpointOverride is set, rewrite the authorization URL to use the
        // external/browser-accessible endpoint instead of the internal one from metadata.
        // This is needed when the issuer URL is a Docker-internal hostname (e.g. http://keycloak:8080)
        // but the browser must reach the external URL (e.g. http://localhost:8083).
        val authUrl = if (providerConfig.authorizationEndpointOverride != null && metadata.authorizationEndpoint != null) {
            authResult.authorizationUrl.replace(metadata.authorizationEndpoint!!, providerConfig.authorizationEndpointOverride!!)
        } else {
            authResult.authorizationUrl
        }

        return Ok(authUrl)
    }

    /**
     * Handle the federation callback from the upstream IdP.
     *
     * Called when the upstream IdP redirects back after authentication.
     * Exchanges the authorization code for tokens, validates the ID token,
     * and caches the user claims.
     *
     * @param code Authorization code from upstream
     * @param state State parameter for CSRF verification
     * @return The session ID to resume the local authorization flow, or error
     */
    suspend fun handleFederationCallback(
        code: String,
        state: String
    ): IdkResult<String, AuthenticationError> {
        val pending = pendingFederations[state]
            ?: return Err(AuthenticationError.InvalidCredentials("Unknown or expired federation state"))

        // Build client authentication
        val secret = providerConfig.clientSecret
        val clientAuth = if (secret != null) {
            ClientAuthenticationConfig.Post(
                credentials = ClientCredentials(
                    clientId = providerConfig.clientId,
                    clientSecret = secret
                )
            )
        } else {
            ClientAuthenticationConfig.None(clientId = providerConfig.clientId)
        }

        // Use the same redirect URI that was sent during authorization
        val redirectUri = pending.callbackRedirectUri

        // Exchange code for tokens
        val tokenResponse = oauth2Client.exchangeAuthorizationCode(
            authorizationServerMetadata = pending.metadata,
            clientAuthentication = clientAuth,
            authorizationCode = code,
            redirectUri = pending.callbackRedirectUri,
            pkceData = pending.pkceData
        ).getOrElse {
            return Err(AuthenticationError.Generic(message = "Token exchange failed: ${it.message.defaultMessage}"))
        }

        val idToken = tokenResponse.idToken
        val accessToken = tokenResponse.accessToken

        // Extract user claims from ID token if available
        // Per OIDC Core 3.1.3.7: signature validation MAY be skipped when the ID token
        // is received directly from the token endpoint (back-channel).
        val userClaims: Map<String, Any> = if (idToken != null) {
            tokenClaimExtractor.extractAllClaims(idToken).getOrElse {
                return Err(AuthenticationError.InvalidCredentials("Failed to decode upstream ID token: ${it.message.defaultMessage}"))
            }.mapValues { (_, v) -> v.toString().removeSurrounding("\"") }
        } else {
            emptyMap()
        }

        // Optionally fetch fresh claims from upstream userinfo
        val userinfoResult: FetchUserInfoResult? = if (accessToken != null && pending.metadata.userinfoEndpoint != null) {
            when (val result = oauth2Client.fetchUserInfo(accessToken, pending.metadata)) {
                is Ok -> result.value
                is Err -> null
                else -> null
            }
        } else null

        // Merge claims (userinfo takes precedence over ID token)
        val rawMergedClaims = buildMap<String, Any> {
            putAll(userClaims)
            userinfoResult?.claims?.forEach { (key, value) ->
                put(key, value.toString().removeSurrounding("\""))
            }
        }

        // Apply claim mapping/projection if configured (whitelist + rename + transform).
        // Fail-closed projection errors must be returned as auth errors, not leaked as uncaught exceptions.
        val mergedClaims = try {
            claimMapper?.invoke(rawMergedClaims) ?: rawMergedClaims
        } catch (e: IllegalArgumentException) {
            return Err(AuthenticationError.InvalidCredentials("Upstream claims rejected: ${e.message ?: "invalid claim mapping"}"))
        } catch (e: IllegalStateException) {
            return Err(AuthenticationError.InvalidCredentials("Upstream claims rejected: ${e.message ?: "required claims missing"}"))
        }

        // Extract user identifier
        val userId = mergedClaims[providerConfig.identifierClaimName]?.toString()
            ?: return Err(AuthenticationError.InvalidCredentials("Missing '${providerConfig.identifierClaimName}' claim in upstream response"))

        // Cache user info
        userClaimsCache[userId] = CachedUserInfo(
            userId = userId,
            claims = mergedClaims,
            cachedAt = Clock.System.now()
        )

        // Mark federation as completed
        pendingFederations[state] = pending.copy(
            completed = true,
            userId = userId,
            authenticatedAt = Clock.System.now()
        )

        return Ok(pending.sessionId)
    }

    override suspend fun authenticateWithCredentials(
        credentials: UserCredentials
    ): IdkResult<String?, AuthenticationError> {
        return Err(AuthenticationError.Generic(message = "Federated provider does not support direct credential authentication"))
    }

    override suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError> {
        userClaimsCache.remove(userId)
        return Ok(Unit)
    }

    override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> {
        val cached = userClaimsCache[userId]
            ?: return Err(AuthenticationError.UserNotFound("No cached claims for user: $userId"))

        return Ok(
            UserInfo(
                userId = cached.userId,
                username = cached.claims["preferred_username"]?.toString(),
                displayName = cached.claims["name"]?.toString(),
                email = cached.claims["email"]?.toString(),
                emailVerified = cached.claims["email_verified"]?.toString()?.toBooleanStrictOrNull(),
                phoneNumber = cached.claims["phone_number"]?.toString(),
                phoneNumberVerified = cached.claims["phone_number_verified"]?.toString()?.toBooleanStrictOrNull(),
                attributes = cached.claims.filterKeys { key ->
                    key !in setOf("sub", "preferred_username", "name", "email", "email_verified", "phone_number", "phone_number_verified")
                }
            )
        )
    }

    override suspend fun isAuthenticationMethodAvailable(
        method: AuthenticationMethod
    ): IdkResult<Boolean, AuthenticationError> {
        return Ok(method == AuthenticationMethod.OAUTH)
    }

    private fun generateSecureToken(): String {
        val bytes = Random.Default.nextBytes(32)
        return bytes.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
    }

    private data class CachedUserInfo(
        val userId: String,
        val claims: Map<String, Any>,
        val cachedAt: kotlinx.datetime.Instant
    )

    private data class PendingFederation(
        val sessionId: String,
        val state: String,
        val nonce: String,
        val pkceData: PkceData?,
        val metadata: AuthorizationServerMetadata,
        val returnUrl: String,
        val callbackRedirectUri: String,
        val completed: Boolean = false,
        val userId: String? = null,
        val authenticatedAt: kotlinx.datetime.Instant? = null
    )
}
