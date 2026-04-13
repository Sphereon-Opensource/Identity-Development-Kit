/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.log.Log
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
import kotlin.random.Random
import kotlin.time.Clock

private const val HEX_RADIX = 16
private const val HEX_PAD_LENGTH = 2
private const val RANDOM_TOKEN_BYTES = 32

/**
 * Context for initiating a federated authentication flow.
 *
 * @property flow The flow type: "federation" for normal login, "reconciliation" for IDV
 * @property oid4vpSessionId OID4VP session ID (only for reconciliation)
 */
data class FlowContext(
    val flow: String = "federation",
    val oid4vpSessionId: String? = null,
)

/**
 * Outcome of a federation callback, dispatched by flow type.
 */
sealed class FederationCallbackOutcome {
    /** Normal federation login — session is ready for authorization code issuance. */
    data class FederationComplete(
        val sessionId: String,
    ) : FederationCallbackOutcome()

    /** Reconciliation flow — claims sent to auth-bridge, redirect browser to frontend. */
    data class ReconciliationComplete(
        val redirectUrl: String,
    ) : FederationCallbackOutcome()
}

/**
 * Handler for reconciliation completion, called when the federation callback
 * is for a reconciliation flow. The STS service provides the implementation.
 */
fun interface ReconciliationCallbackHandler {
    /**
     * Complete reconciliation by sending extracted OIDC claims to the auth-bridge.
     *
     * @param claims Raw OIDC claims from the upstream IdP (ID token + userinfo merged)
     * @param providerId The OIDC provider ID that was used
     * @param issuer The IdP issuer URL
     * @param oid4vpSessionId The OID4VP session to complete
     * @return The redirect URL for the browser (typically frontend login page)
     */
    suspend fun onReconciliationComplete(
        claims: Map<String, Any>,
        providerId: String,
        issuer: String,
        oid4vpSessionId: String,
    ): String
}

/**
 * Federated User Authentication Provider with multi-provider support.
 *
 * Implements UserAuthenticationProvider by delegating authentication to upstream
 * OIDC Providers (e.g., Keycloak, SURF). Supports multiple providers with a
 * configurable default, and two flow types:
 *
 * - **Federation**: Normal login flow — exchanges code, caches user claims, returns
 *   session ID for authorization code issuance.
 * - **Reconciliation**: IDV flow — exchanges code, extracts raw claims, sends them
 *   to the auth-bridge via [ReconciliationCallbackHandler], returns redirect URL.
 *
 * Flow type and provider are stored in the pending federation state and looked up
 * on callback via the opaque state token.
 *
 * @param oauth2Client Reusable OIDC RP client for metadata, token exchange, userinfo
 * @param providers Map of provider ID → config for all enabled federation providers
 * @param defaultProviderId The default provider for federation login
 * @param tokenClaimExtractor JWT claim extractor for ID token decoding
 * @param claimMapper Optional projection/mapping applied to federation claims (not reconciliation)
 * @param reconciliationHandler Optional handler for reconciliation flow completion
 */
class FederatedUserAuthenticationProvider(
    private val oauth2Client: OAuth2Client,
    private val providers: Map<String, FederationProviderConfig>,
    private val defaultProviderId: String,
    private val tokenClaimExtractor: OidcTokenClaimExtractor,
    private val claimMapper: ((Map<String, Any>) -> Map<String, Any>)? = null,
    private val reconciliationHandler: ReconciliationCallbackHandler? = null,
) : UserAuthenticationProvider {
    private val log = Log.app().withTag("Federation")

    /**
     * Backward-compatible constructor for single-provider usage.
     */
    constructor(
        oauth2Client: OAuth2Client,
        providerConfig: FederationProviderConfig,
        tokenClaimExtractor: OidcTokenClaimExtractor,
        claimMapper: ((Map<String, Any>) -> Map<String, Any>)? = null,
    ) : this(
        oauth2Client = oauth2Client,
        providers = mapOf(providerConfig.id to providerConfig),
        defaultProviderId = providerConfig.id,
        tokenClaimExtractor = tokenClaimExtractor,
        claimMapper = claimMapper,
        reconciliationHandler = null,
    )

    /** Get a provider config by ID. */
    fun getProvider(providerId: String): FederationProviderConfig? = providers[providerId]

    /** Get all enabled providers (for the /federation/providers endpoint). */
    fun getEnabledProviders(): List<FederationProviderConfig> = providers.values.filter { it.enabled }

    private fun resolveProvider(providerId: String?): FederationProviderConfig? {
        val id = providerId ?: defaultProviderId
        return providers[id]?.takeIf { it.enabled }
    }

    override suspend fun getAuthenticatedUser(sessionId: String): IdkResult<AuthenticatedUser?, AuthenticationError> {
        val pending = pendingFederations.values.find { it.sessionId == sessionId && it.completed }
        if (pending != null) {
            return Ok(
                AuthenticatedUser(
                    userId = pending.userId ?: return Ok(null),
                    authenticatedAt = pending.authenticatedAt ?: Clock.System.now(),
                    authenticationMethod = AuthenticationMethod.OAUTH,
                    acr = "urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport",
                    amr = listOf("fed"),
                ),
            )
        }
        return Ok(null)
    }

    /**
     * Initiate authentication using the hint's provider ID or the default provider.
     */
    override suspend fun initiateAuthentication(
        sessionId: String,
        returnUrl: String,
        hint: AuthenticationHint?,
    ): IdkResult<String, AuthenticationError> {
        val providerId = hint?.providerId ?: defaultProviderId
        return initiateProviderAuthentication(
            sessionId = sessionId,
            returnUrl = returnUrl,
            providerId = providerId,
            hint = hint,
        )
    }

    /**
     * Initiate authentication for a specific provider and flow.
     *
     * @param sessionId The local session ID (OAuth2 session for federation, generated for reconciliation)
     * @param returnUrl The STS base URL used to construct the callback redirect URI
     * @param providerId Which OIDC provider to use
     * @param callbackPath Override the callback path (e.g., for reconciliation)
     * @param flowContext Flow type and optional OID4VP session context
     * @param hint Optional authentication hint (login_hint, etc.)
     * @return The upstream IdP authorization URL to redirect the browser to
     */
    suspend fun initiateProviderAuthentication(
        sessionId: String,
        returnUrl: String,
        providerId: String,
        callbackPath: String? = null,
        flowContext: FlowContext? = null,
        hint: AuthenticationHint? = null,
    ): IdkResult<String, AuthenticationError> {
        val providerConfig =
            resolveProvider(providerId)
                ?: return Err(AuthenticationError.Generic(message = "Unknown or disabled federation provider: $providerId"))

        // Fetch upstream metadata
        val metadata =
            oauth2Client
                .fetchAuthorizationServerMetadata(providerConfig.issuerUrl)
                .getOrElse {
                    return Err(AuthenticationError.Generic(message = "Failed to fetch upstream metadata for $providerId: ${it.message.defaultMessage}"))
                }

        // Generate state and nonce
        val state = generateSecureToken()
        val nonce = generateSecureToken()

        // Build the callback redirect URI (STS base URL + callback path)
        val effectiveCallbackPath = callbackPath ?: providerConfig.callbackPath
        val callbackRedirectUri = returnUrl.substringBefore("?").trimEnd('/') + effectiveCallbackPath

        // Build client authentication for PAR and token exchange
        val clientAuth =
            providerConfig.clientSecret?.let { secret ->
                ClientAuthenticationConfig.Post(
                    credentials =
                        ClientCredentials(
                            clientId = providerConfig.clientId,
                            clientSecret = secret,
                        ),
                )
            }

        // Initiate authorization (includes PKCE, uses PAR if upstream supports it)
        val authResult =
            oauth2Client
                .initiateAuthorization(
                    authorizationServerMetadata = metadata,
                    clientId = providerConfig.clientId,
                    redirectUri = callbackRedirectUri,
                    scope = providerConfig.scopes.joinToString(" "),
                    state = state,
                    clientAuthentication = clientAuth,
                    additionalParameters =
                        buildMap {
                            put("nonce", nonce)
                            hint?.loginHint?.let { put("login_hint", it) }
                        },
                ).getOrElse {
                    return Err(AuthenticationError.Generic(message = "Failed to initiate upstream authorization for $providerId: ${it.message.defaultMessage}"))
                }

        // Cache pending federation state (keyed by opaque state token)
        pendingFederations[state] =
            PendingFederation(
                sessionId = sessionId,
                state = state,
                nonce = nonce,
                pkceData = authResult.pkceData,
                metadata = metadata,
                returnUrl = returnUrl,
                callbackRedirectUri = callbackRedirectUri,
                completed = false,
                providerId = providerId,
                flowContext = flowContext,
            )

        // If authorizationEndpointOverride is set, rewrite the authorization URL to use the
        // external/browser-accessible endpoint instead of the internal one from metadata.
        val authUrl =
            if (providerConfig.authorizationEndpointOverride != null && metadata.authorizationEndpoint != null) {
                authResult.authorizationUrl.replace(metadata.authorizationEndpoint!!, providerConfig.authorizationEndpointOverride!!)
            } else {
                authResult.authorizationUrl
            }

        return Ok(authUrl)
    }

    /**
     * Handle the federation callback from the upstream IdP.
     *
     * Decodes the pending state, exchanges the authorization code for tokens,
     * and routes based on flow type:
     * - **federation**: Caches user claims, returns [FederationCallbackOutcome.FederationComplete]
     * - **reconciliation**: Sends raw claims to auth-bridge via [reconciliationHandler],
     *   returns [FederationCallbackOutcome.ReconciliationComplete]
     *
     * @param code Authorization code from upstream
     * @param state Opaque state parameter for CSRF verification and session lookup
     * @return The callback outcome (federation complete or reconciliation redirect)
     */
    suspend fun handleFederationCallback(
        code: String,
        state: String,
    ): IdkResult<FederationCallbackOutcome, AuthenticationError> {
        val pending =
            pendingFederations[state]
                ?: return Err(AuthenticationError.InvalidCredentials("Unknown or expired federation state"))

        val providerConfig =
            resolveProvider(pending.providerId)
                ?: return Err(AuthenticationError.Generic(message = "Provider '${pending.providerId}' no longer available"))

        // Exchange code for tokens using the correct provider's credentials
        val rawClaims =
            exchangeCodeAndExtractClaims(code, pending, providerConfig)
                .getOrElse { return Err(it) }

        // Route based on flow type
        val flow = pending.flowContext?.flow ?: "federation"
        return when (flow) {
            "reconciliation" -> handleReconciliationOutcome(rawClaims, pending, providerConfig)
            else -> handleFederationOutcome(rawClaims, state, pending, providerConfig)
        }
    }

    // ========================================================================
    // Private: Code exchange and claim extraction
    // ========================================================================

    private suspend fun exchangeCodeAndExtractClaims(
        code: String,
        pending: PendingFederation,
        providerConfig: FederationProviderConfig,
    ): IdkResult<Map<String, Any>, AuthenticationError> {
        // Build client authentication
        val clientAuth =
            providerConfig.clientSecret?.let { secret ->
                ClientAuthenticationConfig.Post(
                    credentials =
                        ClientCredentials(
                            clientId = providerConfig.clientId,
                            clientSecret = secret,
                        ),
                )
            } ?: ClientAuthenticationConfig.None(clientId = providerConfig.clientId)

        // Exchange code for tokens
        val tokenResponse =
            oauth2Client
                .exchangeAuthorizationCode(
                    authorizationServerMetadata = pending.metadata,
                    clientAuthentication = clientAuth,
                    authorizationCode = code,
                    redirectUri = pending.callbackRedirectUri,
                    pkceData = pending.pkceData,
                ).getOrElse {
                    return Err(AuthenticationError.Generic(message = "Token exchange failed for ${providerConfig.id}: ${it.message.defaultMessage}"))
                }

        val idToken = tokenResponse.idToken
        val accessToken = tokenResponse.accessToken

        // Extract user claims from ID token if available
        val userClaims: Map<String, Any> =
            if (idToken != null) {
                tokenClaimExtractor
                    .extractAllClaims(idToken)
                    .getOrElse {
                        return Err(AuthenticationError.InvalidCredentials("Failed to decode upstream ID token: ${it.message.defaultMessage}"))
                    }.mapValues { (_, v) -> v.toString().removeSurrounding("\"") }
            } else {
                emptyMap()
            }

        // Fetch claims from upstream userinfo endpoint (SURFconext returns extra claims here)
        val userinfoResult: FetchUserInfoResult? =
            if (accessToken != null && pending.metadata.userinfoEndpoint != null) {
                val result = oauth2Client.fetchUserInfo(accessToken, pending.metadata)
                if (result.isOk) {
                    log.debug("UserInfo fetched successfully from ${pending.metadata.userinfoEndpoint}: ${result.value.claims.keys}")
                    result.value
                } else {
                    log.debug("UserInfo fetch failed from ${pending.metadata.userinfoEndpoint}: ${result.error.message}")
                    null
                }
            } else {
                log.debug("Skipping UserInfo: accessToken=${accessToken != null}, userinfoEndpoint=${pending.metadata.userinfoEndpoint}")
                null
            }

        // Merge claims (userinfo takes precedence over ID token)
        val mergedClaims =
            buildMap<String, Any> {
                putAll(userClaims)
                userinfoResult?.claims?.forEach { (key, value) ->
                    put(key, value.toString().removeSurrounding("\""))
                }
            }

        log.debug("ID token claims: ${userClaims.keys}")
        log.debug("Merged claims (${mergedClaims.size} total):")
        mergedClaims.forEach { (k, v) -> log.debug("  $k = $v") }

        return Ok(mergedClaims)
    }

    // ========================================================================
    // Private: Flow-specific outcome handling
    // ========================================================================

    private suspend fun handleFederationOutcome(
        rawClaims: Map<String, Any>,
        state: String,
        pending: PendingFederation,
        providerConfig: FederationProviderConfig,
    ): IdkResult<FederationCallbackOutcome, AuthenticationError> {
        log.debug("Raw claims from upstream (${rawClaims.size} total):")
        rawClaims.forEach { (k, v) -> log.debug("  RAW: $k = $v") }

        // Apply claim mapping/projection if configured (whitelist + rename + transform).
        val mergedClaims =
            try {
                claimMapper?.invoke(rawClaims) ?: rawClaims
            } catch (e: IllegalArgumentException) {
                return Err(AuthenticationError.InvalidCredentials("Upstream claims rejected: ${e.message ?: "invalid claim mapping"}"))
            } catch (e: IllegalStateException) {
                return Err(AuthenticationError.InvalidCredentials("Upstream claims rejected: ${e.message ?: "required claims missing"}"))
            }

        log.debug("Projected claims (${mergedClaims.size} total):")
        mergedClaims.forEach { (k, v) -> log.debug("  PROJECTED: $k = $v") }

        // Extract user identifier
        val userId =
            mergedClaims[providerConfig.identifierClaimName]?.toString()
                ?: return Err(AuthenticationError.InvalidCredentials("Missing '${providerConfig.identifierClaimName}' claim in upstream response"))

        // Cache user info
        userClaimsCache[userId] =
            CachedUserInfo(
                userId = userId,
                claims = mergedClaims,
                cachedAt = Clock.System.now(),
            )

        // Mark federation as completed
        pendingFederations[state] =
            pending.copy(
                completed = true,
                userId = userId,
                authenticatedAt = Clock.System.now(),
            )

        return Ok(FederationCallbackOutcome.FederationComplete(pending.sessionId))
    }

    private suspend fun handleReconciliationOutcome(
        rawClaims: Map<String, Any>,
        pending: PendingFederation,
        providerConfig: FederationProviderConfig,
    ): IdkResult<FederationCallbackOutcome, AuthenticationError> {
        val handler =
            reconciliationHandler
                ?: return Err(AuthenticationError.Generic(message = "Reconciliation handler not configured"))
        val oid4vpSessionId =
            pending.flowContext?.oid4vpSessionId
                ?: return Err(AuthenticationError.Generic(message = "Missing OID4VP session ID for reconciliation flow"))

        // Clean up pending state (reconciliation doesn't create a local auth session)
        pendingFederations.remove(pending.state)

        val redirectUrl =
            handler.onReconciliationComplete(
                claims = rawClaims,
                providerId = pending.providerId,
                issuer = providerConfig.issuerUrl,
                oid4vpSessionId = oid4vpSessionId,
            )

        return Ok(FederationCallbackOutcome.ReconciliationComplete(redirectUrl))
    }

    // ========================================================================
    // Interface methods
    // ========================================================================

    override suspend fun authenticateWithCredentials(credentials: UserCredentials): IdkResult<String?, AuthenticationError> =
        Err(AuthenticationError.Generic(message = "Federated provider does not support direct credential authentication"))

    override suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError> {
        userClaimsCache.remove(userId)
        return Ok(Unit)
    }

    override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> {
        val cached =
            userClaimsCache[userId]
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
                attributes =
                    cached.claims.filterKeys { key ->
                        key !in setOf("sub", "preferred_username", "name", "email", "email_verified", "phone_number", "phone_number_verified")
                    },
            ),
        )
    }

    override suspend fun isAuthenticationMethodAvailable(method: AuthenticationMethod): IdkResult<Boolean, AuthenticationError> = Ok(method == AuthenticationMethod.OAUTH)

    private fun generateSecureToken(): String {
        val bytes = Random.Default.nextBytes(RANDOM_TOKEN_BYTES)
        return bytes.joinToString("") { it.toUByte().toString(HEX_RADIX).padStart(HEX_PAD_LENGTH, '0') }
    }

    companion object {
        // Static maps — must survive across SessionScope instances (each HTTP request = new session)
        // In production, this should be backed by a persistent session store
        private val userClaimsCache = mutableMapOf<String, CachedUserInfo>()
        private val pendingFederations = mutableMapOf<String, PendingFederation>()
    }

    private data class CachedUserInfo(
        val userId: String,
        val claims: Map<String, Any>,
        val cachedAt: kotlin.time.Instant,
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
        val authenticatedAt: kotlin.time.Instant? = null,
        val providerId: String = "default",
        val flowContext: FlowContext? = null,
    )
}
