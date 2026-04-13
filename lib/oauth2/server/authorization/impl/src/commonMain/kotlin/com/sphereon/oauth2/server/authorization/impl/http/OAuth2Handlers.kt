/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.http

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAssertion
import com.sphereon.oauth2.common.model.ClientAttestation
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientCredentials
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.*
import com.sphereon.oauth2.server.authorization.impl.oidc.OidcScopeClaimsMapper
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.ConsentDecision
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService

/**
 * Functional handlers for OAuth2 Authorization Server endpoints.
 *
 * These handlers contain pure business logic with no HTTP framework dependencies.
 * They work with simple data types and return Result<T> for error handling.
 *
 * Based on RFCs:
 * - RFC 6749: OAuth 2.0 Authorization Framework
 * - RFC 7636: PKCE
 * - RFC 7662: Token Introspection
 * - RFC 8693: Token Exchange
 * - RFC 9126: Pushed Authorization Requests (PAR)
 * - RFC 9449: DPoP
 */
class OAuth2Handlers(
    private val authorizationServerService: AuthorizationServerService,
    private val configProvider: OAuth2ServersConfigProvider? = null,
    private val scopeClaimsMapper: OidcScopeClaimsMapper? = null
) {

    private val commands = authorizationServerService.commands

    // ========================================================================
    // Token Endpoint (RFC 6749 Section 3.2)
    // ========================================================================

    /**
     * Handle token endpoint request.
     *
     * RFC 6749 Section 3.2: Token Endpoint
     * POST /token
     *
     * Supports:
     * - authorization_code grant
     * - refresh_token grant
     * - client_credentials grant
     * - token-exchange grant (RFC 8693)
     *
     * @param requestBody Multi-value form-encoded request body
     * @param requestHeaders HTTP headers (for client authentication)
     * @param httpUrl Full URL of the token endpoint (for DPoP)
     * @return Token response with access token, refresh token, etc.
     */
    suspend fun handleTokenRequest(
        requestBody: Map<String, List<String>>,
        requestHeaders: Map<String, String>,
        httpUrl: String
    ): IdkResult<TokenResponse, IdkError> {
        // Parse the token request
        val tokenRequest = commands.parseTokenRequest.execute(
            ParseTokenRequestArgs(requestBody, requestHeaders)
        ).getOrElse { error -> return Err(error) }

        // Verify client authentication
        val verifiedAuth = commands.verifyClientAuthentication.execute(
            VerifyClientAuthenticationArgs(
                clientAuthentication = tokenRequest.clientAuthentication,
                clientId = tokenRequest.clientId,
                tokenEndpointUrl = httpUrl
            )
        ).getOrElse { error -> return Err(error) }

        // Process based on grant type
        val result = when (val params = tokenRequest.grantParameters) {
            is GrantParameters.AuthorizationCode -> {
                val verified = commands.verifyAuthorizationCodeGrant.execute(
                    VerifyAuthorizationCodeGrantArgs(
                        code = params.code,
                        redirectUri = params.redirectUri,
                        clientId = tokenRequest.clientId,
                        codeVerifier = params.codeVerifier
                    )
                ).getOrElse { error -> return Err(error) }

                // Access token: no identity claims (RFC 9068)
                val accessToken = commands.createAccessToken.execute(
                    CreateAccessTokenArgs(
                        subject = verified.subject,
                        clientId = tokenRequest.clientId,
                        scope = verified.scope,
                        dpopJkt = verified.dpopJkt
                    )
                ).getOrElse { error -> return Err(error) }

                val refreshToken = commands.createRefreshToken.execute(
                    CreateRefreshTokenArgs(
                        subject = verified.subject,
                        clientId = tokenRequest.clientId,
                        scope = verified.scope,
                        dpopJkt = verified.dpopJkt
                    )
                ).getOrElse { error -> return Err(error) }

                // Issue ID token when OIDC is enabled and openid scope is granted
                val grantedScopes = verified.scope?.split(" ")?.toSet() ?: emptySet()
                val oidcEnabled = configProvider?.serverConfig?.oidc?.isEnabled == true
                val idToken = if (oidcEnabled && "openid" in grantedScopes) {
                    // Scope-based claim filtering for id_token:
                    // Standard OIDC claims are scope-filtered; custom claims pass through
                    val idTokenClaims = if (scopeClaimsMapper != null && verified.userClaims.isNotEmpty()) {
                        val scopeFiltered = scopeClaimsMapper.filterClaims(verified.userClaims, grantedScopes)
                        val standardClaimKeys = scopeClaimsMapper.allStandardClaimKeys()
                        val customClaims = verified.userClaims.filterKeys { it !in standardClaimKeys }
                        scopeFiltered + customClaims
                    } else {
                        verified.userClaims
                    }

                    commands.createIdToken.execute(
                        CreateIdTokenArgs(
                            subject = verified.subject,
                            clientId = tokenRequest.clientId,
                            nonce = verified.codeData.nonce,
                            authTime = verified.codeData.authTime,
                            accessToken = accessToken.value,
                            authorizationCode = params.code,
                            userClaims = idTokenClaims
                        )
                    ).getOrElse { error -> return Err(error) }.value
                } else null

                commands.createTokenResponse.execute(
                    CreateTokenResponseArgs(
                        accessToken = accessToken.value,
                        tokenType = if (verified.dpopJkt != null) "DPoP" else "Bearer",
                        refreshToken = refreshToken.value,
                        scope = verified.scope,
                        idToken = idToken
                    )
                )
            }

            is GrantParameters.RefreshToken -> {
                val verified = commands.verifyRefreshTokenGrant.execute(
                    VerifyRefreshTokenGrantArgs(
                        refreshToken = params.refreshToken,
                        clientId = tokenRequest.clientId,
                        requestedScope = params.scope
                    )
                ).getOrElse { error -> return Err(error) }

                // Create new access token
                val accessToken = commands.createAccessToken.execute(
                    CreateAccessTokenArgs(
                        subject = verified.subject,
                        clientId = tokenRequest.clientId,
                        scope = verified.scope,
                        dpopJkt = verified.dpopJkt
                    )
                ).getOrElse { error -> return Err(error) }

                commands.createTokenResponse.execute(
                    CreateTokenResponseArgs(
                        accessToken = accessToken.value,
                        tokenType = if (verified.dpopJkt != null) "DPoP" else "Bearer",
                        scope = verified.scope
                    )
                )
            }

            is GrantParameters.ClientCredentials -> {
                val verified = commands.verifyClientCredentialsGrant.execute(
                    VerifyClientCredentialsGrantArgs(
                        clientId = tokenRequest.clientId,
                        requestedScope = params.scope
                    )
                ).getOrElse { error -> return Err(error) }

                // Create access token
                val accessToken = commands.createAccessToken.execute(
                    CreateAccessTokenArgs(
                        subject = verified.subject,
                        clientId = tokenRequest.clientId,
                        scope = verified.scope
                    )
                ).getOrElse { error -> return Err(error) }

                commands.createTokenResponse.execute(
                    CreateTokenResponseArgs(
                        accessToken = accessToken.value,
                        tokenType = "Bearer",
                        scope = verified.scope
                    )
                )
            }

            is GrantParameters.TokenExchange -> {
                val verified = commands.verifyTokenExchangeGrant.execute(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = params.subjectToken,
                        subjectTokenType = params.subjectTokenType,
                        actorToken = params.actorToken,
                        actorTokenType = params.actorTokenType,
                        resources = params.resources,
                        audiences = params.audiences,
                        scope = params.scope,
                        requestedTokenType = params.requestedTokenType,
                        clientId = tokenRequest.clientId
                    )
                ).getOrElse { error -> return Err(error) }

                // Build additional claims (include act for delegation)
                val additionalClaims = buildMap<String, Any> {
                    putAll(verified.additionalClaims)
                    verified.actorClaim?.let { put("act", it) }
                }

                // Create access token
                val accessToken = commands.createAccessToken.execute(
                    CreateAccessTokenArgs(
                        subject = verified.subject,
                        clientId = verified.clientId,
                        scope = verified.scope,
                        audience = verified.audience,
                        additionalClaims = additionalClaims
                    )
                ).getOrElse { error -> return Err(error) }

                // No refresh token for token exchange (RFC 8693 Section 2.1)
                commands.createTokenResponse.execute(
                    CreateTokenResponseArgs(
                        accessToken = accessToken.value,
                        tokenType = "Bearer",
                        scope = verified.scope,
                        issuedTokenType = verified.issuedTokenType
                    )
                )
            }

            is GrantParameters.PreAuthorizedCode -> {
                // TODO: Implement pre-authorized code flow (OpenID4VCI)
                Err(IdkError(
                    code = "unsupported_grant_type",
                    message = IdkError.Message(
                        i18nKey = "oauth2.as.error.unsupported_grant_type",
                        defaultMessage = "Unsupported grant type: urn:ietf:params:oauth:grant-type:pre-authorized_code"
                    )
                ))
            }
        }

        return result
    }

    // ========================================================================
    // Authorization Endpoint (RFC 6749 Section 3.1)
    // ========================================================================

    /**
     * Handle authorization endpoint request - initial request parsing and validation.
     *
     * RFC 6749 Section 3.1: Authorization Endpoint
     * GET /authorize?response_type=code&client_id=...
     *
     * This handler:
     * 1. Parses the authorization request
     * 2. Validates the request
     * 3. Creates an authorization session
     *
     * The flow then typically requires:
     * - User authentication (via UserAuthenticationProvider)
     * - User consent (via ConsentProvider)
     * - Authorization code generation (via handleAuthorizationApproval)
     *
     * @param queryParameters Query parameters from the request
     * @return Authorization session for tracking the flow
     */
    suspend fun handleAuthorizationRequest(
        queryParameters: Map<String, String>
    ): IdkResult<AuthorizationSession, IdkError> {
        // Parse authorization request
        val authRequest = commands.parseAuthorizationRequest.execute(
            ParseAuthorizationRequestArgs(queryParameters)
        ).getOrElse { error -> return Err(error) }

        // Verify authorization request
        val verified = commands.verifyAuthorizationRequest.execute(authRequest)
            .getOrElse { error -> return Err(error) }

        // Create authorization session
        return commands.createAuthorizationSession.execute(verified)
    }

    /**
     * Handle authorization approval - generate authorization code after user consent.
     *
     * RFC 6749 Section 4.1.2: Authorization Response
     *
     * Called after user authentication and consent have been obtained.
     *
     * @param session Authorization session
     * @param userId Authenticated user ID
     * @param consent User consent decision
     * @return Authorization response with code and redirect URI
     */
    suspend fun handleAuthorizationApproval(
        session: AuthorizationSession,
        userId: String,
        consent: ConsentDecision,
        userClaims: Map<String, Any> = emptyMap()
    ): IdkResult<AuthorizationResponseData, IdkError> {
        // Create authorization code
        val code = commands.createAuthorizationCode.execute(
            CreateAuthorizationCodeArgs(session, userId, consent, userClaims)
        ).getOrElse { error -> return Err(error) }

        // Create authorization response
        return commands.createAuthorizationResponse.execute(
            CreateAuthorizationResponseArgs(
                code = code.value,
                state = session.state,
                redirectUri = session.redirectUri
            )
        )
    }

    /**
     * Handle authorization error - create error response.
     *
     * RFC 6749 Section 4.1.2.1: Error Response
     *
     * @param error Error code (invalid_request, unauthorized_client, etc.)
     * @param errorDescription Human-readable error description
     * @param state State parameter from request
     * @param redirectUri Redirect URI for error response
     * @return Authorization error response
     */
    suspend fun handleAuthorizationError(
        error: String,
        errorDescription: String?,
        state: String?,
        redirectUri: String
    ): IdkResult<AuthorizationErrorResponseData, IdkError> {
        return commands.createAuthorizationErrorResponse.execute(
            CreateAuthorizationErrorResponseArgs(
                error = error,
                errorDescription = errorDescription,
                state = state,
                redirectUri = redirectUri
            )
        )
    }

    // ========================================================================
    // PAR Endpoint (RFC 9126)
    // ========================================================================

    /**
     * Handle pushed authorization request.
     *
     * RFC 9126: Pushed Authorization Requests (PAR)
     * POST /par
     *
     * Allows clients to push authorization request parameters to the AS
     * via a direct HTTP POST before redirecting the user.
     *
     * @param requestBody Multi-value form-encoded request body
     * @param requestHeaders HTTP headers (for client authentication)
     * @return PAR response with request_uri and expires_in
     */
    suspend fun handlePushedAuthorizationRequest(
        requestBody: Map<String, List<String>>,
        requestHeaders: Map<String, String>
    ): IdkResult<PushedAuthorizationResponse, IdkError> {
        // Convert multi-value body to single-value for client auth extraction
        val singleValueBody = requestBody.mapValues { (_, values) -> values.first() }

        // Extract client authentication from headers and body
        val clientAuth = extractClientAuthentication(requestHeaders, singleValueBody)

        // Parse PAR request
        val authRequest = commands.parsePushedAuthorizationRequest.execute(
            ParsePushedAuthorizationRequestArgs(requestBody, clientAuth)
        ).getOrElse { error -> return Err(error) }

        // Verify PAR request
        val verified = commands.verifyPushedAuthorizationRequest.execute(
            VerifyPushedAuthorizationRequestArgs(authRequest, authRequest.clientId)
        ).getOrElse { error -> return Err(error) }

        // Create request_uri
        val requestUriData = commands.createRequestUri.execute(verified)
            .getOrElse { error -> return Err(error) }

        // Create PAR response
        return commands.createPushedAuthorizationResponse.execute(
            CreatePushedAuthorizationResponseArgs(
                requestUri = requestUriData.requestUri,
                expiresIn = requestUriData.expiresIn
            )
        )
    }

    // ========================================================================
    // Token Introspection Endpoint (RFC 7662)
    // ========================================================================

    /**
     * Handle token introspection request.
     *
     * RFC 7662: Token Introspection
     * POST /introspect
     *
     * Allows authorized clients to query token metadata and validity.
     *
     * @param requestBody Multi-value form-encoded request body
     * @return Token introspection response
     */
    suspend fun handleIntrospectionRequest(
        requestBody: Map<String, List<String>>
    ): IdkResult<TokenIntrospectionResponse, IdkError> {
        // Parse introspection request
        val introspectionRequest = commands.parseIntrospectionRequest.execute(
            ParseIntrospectionRequestArgs(requestBody)
        ).getOrElse { error -> return Err(error) }

        // Introspect token
        return commands.introspectToken.execute(
            IntrospectTokenArgs(
                token = introspectionRequest.token,
                tokenTypeHint = introspectionRequest.tokenTypeHint,
                clientId = introspectionRequest.clientId
            )
        )
    }

    // ========================================================================
    // Token Revocation Endpoint (RFC 7009)
    // ========================================================================

    /**
     * Handle token revocation request.
     *
     * RFC 7009: Token Revocation
     * POST /revoke
     *
     * Allows clients to revoke their tokens.
     * Per RFC 7009, always returns success (even for invalid/unknown tokens).
     *
     * @param requestBody Multi-value form-encoded request body
     * @return Unit on success (always 200)
     */
    suspend fun handleRevocationRequest(
        requestBody: Map<String, List<String>>
    ): IdkResult<Unit, IdkError> {
        // Parse revocation request
        val revocationRequest = commands.parseRevocationRequest.execute(
            ParseRevocationRequestArgs(requestBody)
        ).getOrElse { error -> return Err(error) }

        // Revoke token
        return commands.revokeToken.execute(
            RevokeTokenArgs(
                token = revocationRequest.token,
                tokenTypeHint = revocationRequest.tokenTypeHint,
                clientId = revocationRequest.clientId
            )
        )
    }

    // ========================================================================
    // Discovery Endpoint (RFC 8414)
    // ========================================================================

    /**
     * Handle discovery endpoint request.
     *
     * RFC 8414: OAuth 2.0 Authorization Server Metadata
     * GET /.well-known/oauth-authorization-server
     *
     * Returns the authorization server metadata document.
     *
     * @param serverId Optional server ID (null = default)
     * @param baseUrlOverride Optional base URL override
     * @return Authorization server metadata
     */
    suspend fun handleDiscoveryRequest(
        serverId: String? = null,
        baseUrlOverride: String? = null
    ): IdkResult<AuthorizationServerMetadata, IdkError> {
        return commands.buildServerMetadata.execute(
            BuildServerMetadataArgs(
                serverId = serverId,
                baseUrlOverride = baseUrlOverride
            )
        )
    }

    // ========================================================================
    // UserInfo Endpoint (OpenID Connect Core 1.0 Section 5.3)
    // ========================================================================

    /**
     * Handle UserInfo request.
     *
     * @param accessToken Bearer token from Authorization header
     * @return UserInfo response with claims filtered by granted scopes
     */
    suspend fun handleUserInfoRequest(
        accessToken: String
    ): IdkResult<UserInfoResponse, IdkError> {
        return commands.getUserInfo.execute(
            GetUserInfoArgs(accessToken = accessToken)
        )
    }

    // ========================================================================
    // JWKS Endpoint
    // ========================================================================

    /**
     * Handle JWKS request.
     *
     * Returns the server's public signing key(s).
     */
    suspend fun handleJwksRequest(): IdkResult<JwksResult, IdkError> {
        return commands.getJwks.execute(GetJwksArgs())
    }

    // ========================================================================
    // Helper Functions
    // ========================================================================

    /**
     * Extract client authentication from headers and body.
     *
     * Supports:
     * - attest_jwt_client_auth (OAuth-Client-Attestation headers)
     * - private_key_jwt / client_secret_jwt (client_assertion in body)
     * - client_secret_basic (Authorization header with Basic auth)
     * - client_secret_post (client_id and client_secret in body)
     * - none (public client with only client_id)
     */
    private fun extractClientAuthentication(
        headers: Map<String, String>,
        body: Map<String, String>
    ): ClientAuthenticationConfig {
        // 1. Attestation headers (highest priority)
        val attestationHeader = headers["OAuth-Client-Attestation"]
            ?: headers["oauth-client-attestation"]
        val attestationPopHeader = headers["OAuth-Client-Attestation-PoP"]
            ?: headers["oauth-client-attestation-pop"]
        if (attestationHeader != null && attestationPopHeader != null) {
            return ClientAuthenticationConfig.AttestationJwt(
                ClientAttestation(attestationHeader, attestationPopHeader)
            )
        }

        // 2. JWT assertion in body (RFC 7523)
        val assertionType = body["client_assertion_type"]
        val assertion = body["client_assertion"]
        if (assertionType != null && assertion != null) {
            val clientId = body["client_id"] ?: ""
            return when (assertionType) {
                "urn:ietf:params:oauth:client-assertion-type:jwt-bearer" ->
                    ClientAuthenticationConfig.PrivateKeyJwt(ClientAssertion(clientId, assertionType, assertion))
                else ->
                    ClientAuthenticationConfig.SecretJwt(ClientAssertion(clientId, assertionType, assertion))
            }
        }

        // 3. Basic auth (Authorization header)
        val authHeader = headers["authorization"] ?: headers["Authorization"]
        if (authHeader != null) {
            val parts = authHeader.trim().split(" ", limit = 2)
            if (parts.size == 2 && parts[0].equals("Basic", ignoreCase = true)) {
                try {
                    val decoded = parts[1].decodeFromBase64().decodeToString()
                    val credentials = decoded.split(":", limit = 2)
                    if (credentials.size == 2) {
                        return ClientAuthenticationConfig.Basic(
                            credentials = ClientCredentials(
                                clientId = credentials[0],
                                clientSecret = credentials[1]
                            )
                        )
                    }
                } catch (_: Exception) {
                    // Fall through
                }
            }
        }

        // 4. Post auth (client_secret in body)
        val clientSecret = body["client_secret"]
        if (clientSecret != null) {
            return ClientAuthenticationConfig.Post(
                credentials = ClientCredentials(
                    clientId = body["client_id"] ?: "",
                    clientSecret = clientSecret
                )
            )
        }

        // 5. No authentication (public client)
        return ClientAuthenticationConfig.None(
            clientId = body["client_id"] ?: ""
        )
    }
}
