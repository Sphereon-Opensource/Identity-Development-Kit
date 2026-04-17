/*
 * © 2026 Sphereon International B.V.
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
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.util.RequestUtils
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.RoutedHttpAdapter
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.describe.httpRoutes
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.command.GetJwksArgs
import com.sphereon.oauth2.server.authorization.command.GetUserInfoArgs
import com.sphereon.oauth2.server.authorization.command.HandleIaeFollowUpArgs
import com.sphereon.oauth2.server.authorization.command.HandleIaeFollowUpCommand
import com.sphereon.oauth2.server.authorization.command.HandleIaeInitialRequestArgs
import com.sphereon.oauth2.server.authorization.command.HandleIaeInitialRequestCommand
import com.sphereon.oauth2.server.authorization.command.IaeResult
import com.sphereon.oauth2.server.authorization.command.JwksResult
import com.sphereon.oauth2.server.authorization.command.UserInfoResponse
import com.sphereon.oauth2.server.authorization.impl.oidc.OidcScopeClaimsMapper
import com.sphereon.oauth2.server.authorization.impl.provider.CompositeUserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.impl.provider.FederatedUserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.impl.provider.FederationCallbackOutcome
import com.sphereon.oauth2.server.authorization.impl.provider.FlowContext
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.ConsentDecision
import com.sphereon.oauth2.server.authorization.model.IaeAuthorizationCodeResponse
import com.sphereon.oauth2.server.authorization.model.IaeErrorResponse
import com.sphereon.oauth2.server.authorization.model.IaeInteractionRequiredResponse
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeData
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeStorage
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Universal HTTP Adapter for OAuth2 Authorization Server.
 *
 * This adapter provides a framework-agnostic HTTP API layer that can be used with:
 * - Spring Boot
 * - Ktor Server
 * - AWS Lambda
 * - Azure Functions
 * - Google Cloud Functions
 *
 * **SINGLE SOURCE OF TRUTH** for all OAuth2 Authorization Server endpoint routing.
 *
 * Based on RFCs:
 * - RFC 6749: OAuth 2.0 Authorization Framework
 * - RFC 7636: PKCE
 * - RFC 7009: Token Revocation
 * - RFC 7662: Token Introspection
 * - RFC 8414: Authorization Server Metadata (Discovery)
 * - RFC 9126: Pushed Authorization Requests (PAR)
 * - RFC 9449: DPoP
 *
 * Endpoints:
 * - POST /token - Token endpoint
 * - GET /authorize - Authorization endpoint (initial request)
 * - POST /authorize - Authorization endpoint (form post)
 * - POST /par - Pushed Authorization Request endpoint
 * - POST /introspect - Token introspection endpoint
 * - POST /revoke - Token revocation endpoint
 * - GET /.well-known/oauth-authorization-server - Discovery endpoint
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class OAuth2HttpAdapter(
    private val authorizationServerService: AuthorizationServerService,
    private val configProvider: OAuth2ServersConfigProvider,
    private val userAuthProvider: UserAuthenticationProvider,
    private val scopeClaimsMapper: OidcScopeClaimsMapper,
    private val handleIaeInitialRequestCommand: HandleIaeInitialRequestCommand,
    private val handleIaeFollowUpCommand: HandleIaeFollowUpCommand,
    private val preAuthorizedCodeStorage: PreAuthorizedCodeStorage,
) : RoutedHttpAdapter() {
    companion object {
        const val ID: String = "OAUTH2_AUTHZ"

        // Static map — must survive across SessionScope instances (each HTTP request = new session)
        // In production, this should be backed by a persistent session store
        private val pendingSessions = mutableMapOf<String, AuthorizationSession>()
    }

    override val id: String = ID

    @ContributesTo(SessionScope::class)
    interface Graph {
        val oAuth2HttpAdapter: OAuth2HttpAdapter
    }

    private fun GenericHttpRequest.forwardedScheme(): String {
        val proto = RequestUtils.extractHeaderValue(headers, "X-Forwarded-Proto")
        return if (proto.equals("https", ignoreCase = true)) "https" else "http"
    }

    private fun GenericHttpRequest.hostHeader(): String = RequestUtils.extractHeaderValue(headers, "Host") ?: "localhost"

    private fun GenericHttpRequest.resolveBaseUrl(tenantPath: String? = null): String {
        // Prefer configured issuer URL — correct behind reverse proxies with path prefixes
        // (e.g. Caddy routing /auth/* to the AS, where request headers lose the prefix)
        val configuredIssuer = configProvider.serverConfig.issuer?.trimEnd('/')
        if (configuredIssuer != null) {
            return if (tenantPath != null) "$configuredIssuer/$tenantPath" else configuredIssuer
        }
        val host = hostHeader()
        val scheme = forwardedScheme()
        return if (tenantPath != null) "$scheme://$host/$tenantPath" else "$scheme://$host"
    }

    override val mount: HttpAdapterMount =
        HttpAdapterMount(
            serverPrefix = "",
            adapterBasePath = "/",
        )

    override val routes =
        httpRoutes {
            post("/token") {
                operationId("token")
                consumes(MediaType.ApplicationFormUrlEncoded)
                produces(MediaType.ApplicationJson)
                handle { req -> handleTokenEndpoint(req) }
            }
            get("/authorize") {
                operationId("authorize")
                handle { req -> handleAuthorizationEndpoint(req) }
            }
            get("/authorize/callback") {
                operationId("authorizeCallback")
                handle { req -> handleAuthorizationCallback(req) }
            }
            post("/par") {
                operationId("pushedAuthorizationRequest")
                consumes(MediaType.ApplicationFormUrlEncoded)
                produces(MediaType.ApplicationJson)
                handle { req -> handleParEndpoint(req) }
            }
            post("/introspect") {
                operationId("introspectToken")
                consumes(MediaType.ApplicationFormUrlEncoded)
                produces(MediaType.ApplicationJson)
                handle { req -> handleIntrospectionEndpoint(req) }
            }
            post("/revoke") {
                operationId("revokeToken")
                consumes(MediaType.ApplicationFormUrlEncoded)
                produces(MediaType.ApplicationJson)
                handle { req -> handleRevocationEndpoint(req) }
            }
            get("/.well-known/oauth-authorization-server") {
                operationId("serverMetadataDefault")
                produces(MediaType.ApplicationJson)
                handle { req -> handleDiscoveryEndpoint(req) }
            }
            get("/.well-known/oauth-authorization-server/{tenant-path}") {
                operationId("serverMetadata")
                produces(MediaType.ApplicationJson)
                handle { req -> handleDiscoveryEndpoint(req) }
            }
            // OIDC endpoints
            get("/.well-known/openid-configuration") {
                operationId("openidConfigurationDefault")
                produces(MediaType.ApplicationJson)
                handle { req -> handleOidcDiscoveryEndpoint(req) }
            }
            get("/.well-known/openid-configuration/{tenant-path}") {
                operationId("openidConfiguration")
                produces(MediaType.ApplicationJson)
                handle { req -> handleOidcDiscoveryEndpoint(req) }
            }
            get("/userinfo") {
                operationId("userinfoGet")
                produces(MediaType.ApplicationJson)
                handle { req -> handleUserInfoEndpoint(req) }
            }
            post("/userinfo") {
                operationId("userinfoPost")
                produces(MediaType.ApplicationJson)
                handle { req -> handleUserInfoEndpoint(req) }
            }
            // JWKS endpoint (always available - needed for JWT access token verification)
            get("/.well-known/jwks.json") {
                operationId("jwks")
                produces(MediaType.ApplicationJson)
                handle { req -> handleJwksEndpoint(req) }
            }
            // Federation callback (handles upstream IdP redirect back to STS)
            get("/federation/callback") {
                operationId("federationCallback")
                produces(MediaType.ApplicationJson)
                handle { req -> handleFederationCallbackEndpoint(req) }
            }
            // Federation providers list (for login UI provider selection)
            get("/federation/providers") {
                operationId("federationProviders")
                produces(MediaType.ApplicationJson)
                handle { req -> handleFederationProvidersEndpoint(req) }
            }
            // Reconciliation authorize (initiates OIDC flow for IDV via STS)
            get("/reconciliation/authorize") {
                operationId("reconciliationAuthorize")
                produces(MediaType.ApplicationJson)
                handle { req -> handleReconciliationAuthorizeEndpoint(req) }
            }
            // IAE (Interactive Authorization Endpoint) — OID4VCI 1.1 Section 6
            post("/iae") {
                operationId("interactiveAuthorization")
                consumes(MediaType.ApplicationFormUrlEncoded)
                produces(MediaType.ApplicationJson)
                handle { req -> handleIaeEndpoint(req) }
            }
            // Internal: pre-authorized code registration (called by OID4VCI issuer in separate-process deployments)
            post("/internal/preauth/register") {
                operationId("registerPreAuthCode")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleRegisterPreAuthCode(req) }
            }
        }

    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

    private val handlers = OAuth2Handlers(authorizationServerService, configProvider, scopeClaimsMapper)

    // pendingSessions is in companion object (must survive across SessionScope instances)

    // Routing is inherited from RoutedHttpAdapter (single source of truth is routes above).

    // ========================================================================
    // Endpoint Handlers
    // ========================================================================

    /**
     * Token Endpoint Handler
     * RFC 6749 Section 3.2: Token Endpoint
     * POST /token
     */
    private suspend fun handleTokenEndpoint(request: GenericHttpRequest): GenericHttpResponse {
        // Parse form-encoded body
        val requestBody =
            parseFormBody(request.body)
                ?: return errorResponse(400, "invalid_request", "Missing or invalid request body")

        // Extract full URL for DPoP
        val httpUrl = buildFullUrl(request)

        // Call handler
        val result =
            handlers.handleTokenRequest(
                requestBody = requestBody,
                requestHeaders = request.headers,
                httpUrl = httpUrl,
            )

        return result.fold(
            success = { tokenResponse ->
                // Success: Return 200 with token response
                val responseBody = json.encodeToString(tokenResponse)
                GenericHttpResponse(
                    statusCode = 200,
                    headers =
                        mapOf(
                            "Content-Type" to "application/json",
                            "Cache-Control" to "no-store",
                            "Pragma" to "no-cache",
                        ),
                    body = responseBody,
                )
            },
            failure = { error ->
                mapErrorToResponse(error)
            },
        )
    }

    /**
     * Authorization Endpoint Handler
     * RFC 6749 Section 3.1: Authorization Endpoint
     * GET /authorize
     *
     * This is a browser-facing OAuth 2.0 authorization endpoint.
     *
     * It:
     * 1. Creates the authorization session
     * 2. Initiates user authentication
     * 3. Returns a redirect response
     *
     * On success, the redirect targets the authentication system.
     * On authentication-initiation failure, the redirect targets the client redirect_uri
     * with an OAuth error response.
     */
    private suspend fun handleAuthorizationEndpoint(request: GenericHttpRequest): GenericHttpResponse {
        // Parse query parameters
        @Suppress("UNCHECKED_CAST")
        val queryParams = request.queryParameters.filterValues { it != null } as Map<String, String>

        // Check for login_hint parameter for auth method routing
        val loginHint = queryParams["login_hint"]

        // Call handler to create authorization session
        val result = handlers.handleAuthorizationRequest(queryParams)

        return result.fold(
            success = { session ->
                // Check if this is a wallet (OID4VP) "resume" flow via login_hint
                if (loginHint != null && loginHint.startsWith("oid4vp:")) {
                    return handleWalletAuthorization(session, loginHint)
                }

                // Build the return URL (authorization callback) from the request
                val returnUrl = "${request.resolveBaseUrl()}/authorize/callback?session_id=${session.sessionId}"

                // Initiate authentication via the provider (federation, composite, or other)
                val providerParam = queryParams["provider"]
                val hint =
                    if (loginHint != null || providerParam != null) {
                        AuthenticationHint(loginHint = loginHint, providerId = providerParam)
                    } else {
                        null
                    }
                val authResult =
                    userAuthProvider.initiateAuthentication(
                        sessionId = session.sessionId,
                        returnUrl = returnUrl,
                        hint = hint,
                    )
                if (authResult.isErr) {
                    // Redirect back to client with error (e.g., provider unreachable)
                    val separator = if (session.redirectUri.contains("?")) "&" else "?"
                    val errorDesc = authResult.error.message
                    val errorUri = "${session.redirectUri}${separator}error=server_error&error_description=${urlEncode(errorDesc)}"
                    val locationUri = if (session.state != null) "$errorUri&state=${urlEncode(session.state!!)}" else errorUri
                    return GenericHttpResponse(
                        statusCode = 302,
                        headers = mapOf("Location" to locationUri, "Cache-Control" to "no-store"),
                        body = "",
                    )
                }

                pendingSessions[session.sessionId] = session
                GenericHttpResponse(
                    statusCode = 302,
                    headers =
                        mapOf(
                            "Location" to authResult.value,
                            "Cache-Control" to "no-store",
                        ),
                    body = "",
                )
            },
            failure = { error ->
                mapErrorToResponse(error)
            },
        )
    }

    /**
     * Resumes the authorization flow after user authentication (e.g., login form).
     * Looks up the pending session, verifies the user is authenticated, and issues
     * an authorization code redirecting to the client's redirect_uri.
     */
    private suspend fun handleAuthorizationCallback(request: GenericHttpRequest): GenericHttpResponse {
        @Suppress("UNCHECKED_CAST")
        val queryParams = request.queryParameters.filterValues { it != null } as Map<String, String>
        val sessionId =
            queryParams["session_id"]
                ?: return errorResponse(400, "invalid_request", "Missing session_id parameter")

        val session =
            pendingSessions.remove(sessionId)
                ?: return errorResponse(400, "invalid_request", "No pending authorization session for session_id: $sessionId")

        val authUserResult = userAuthProvider.getAuthenticatedUser(sessionId)
        if (authUserResult.isErr) {
            return errorResponse(500, "server_error", "Failed to get authenticated user: ${authUserResult.error.message}")
        }
        val authenticatedUser =
            authUserResult.value
                ?: return errorResponse(500, "server_error", "User not authenticated after login")

        val consent =
            ConsentDecision(
                userId = authenticatedUser.userId,
                clientId = session.clientId,
                granted = true,
                grantedScopes = session.scope?.split(" "),
                grantedAt = Clock.System.now(),
            )

        val approvalResult =
            handlers.handleAuthorizationApproval(
                session = session,
                userId = authenticatedUser.userId,
                consent = consent,
                acr = authenticatedUser.acr,
                amr = authenticatedUser.amr,
            )
        if (approvalResult.isOk) {
            return GenericHttpResponse(
                statusCode = 302,
                headers = mapOf("Location" to approvalResult.value.redirectUri, "Cache-Control" to "no-store"),
                body = "",
            )
        }

        val redirectUri = session.redirectUri
        val separator = if (redirectUri.contains("?")) "&" else "?"
        val errorUri = "${redirectUri}${separator}error=server_error&error_description=${urlEncode(approvalResult.error.message.defaultMessage ?: "Authorization approval failed")}"
        val locationUri = if (session.state != null) "$errorUri&state=${urlEncode(session.state!!)}" else errorUri
        return GenericHttpResponse(statusCode = 302, headers = mapOf("Location" to locationUri, "Cache-Control" to "no-store"), body = "")
    }

    /**
     * Handle wallet authorization via login_hint=oid4vp:{sessionId}.
     *
     * In the hybrid approach, the wallet authentication is already complete by the time
     * the STS /authorize is called. The login_hint tells us which OID4VP session to check.
     * If the user is authenticated, we immediately issue an authorization code.
     */
    private suspend fun handleWalletAuthorization(
        session: AuthorizationSession,
        loginHint: String,
    ): GenericHttpResponse {
        val oid4vpSessionId = loginHint.removePrefix("oid4vp:")

        // Store the OID4VP session ID mapping so getAuthenticatedUser can find it
        // The auth provider needs to know which OID4VP session this OAuth session maps to
        val authResult = userAuthProvider.getAuthenticatedUser(oid4vpSessionId)

        if (authResult.isErr) {
            val redirectUri = session.redirectUri
            val state = session.state
            val separator = if (redirectUri.contains("?")) "&" else "?"
            val errorUri = "${redirectUri}${separator}error=access_denied&error_description=${urlEncode("Wallet authentication failed: ${authResult.error.message}")}"
            return if (state != null) {
                GenericHttpResponse(statusCode = 302, headers = mapOf("Location" to "$errorUri&state=${urlEncode(state)}", "Cache-Control" to "no-store"), body = "")
            } else {
                GenericHttpResponse(statusCode = 302, headers = mapOf("Location" to errorUri, "Cache-Control" to "no-store"), body = "")
            }
        }

        val authenticatedUser =
            authResult.value
                ?: return run {
                    val redirectUri = session.redirectUri
                    val state = session.state
                    val separator = if (redirectUri.contains("?")) "&" else "?"
                    val errorUri = "${redirectUri}${separator}error=access_denied&error_description=${urlEncode("Wallet session not authenticated")}"
                    if (state != null) {
                        GenericHttpResponse(statusCode = 302, headers = mapOf("Location" to "$errorUri&state=${urlEncode(state)}", "Cache-Control" to "no-store"), body = "")
                    } else {
                        GenericHttpResponse(statusCode = 302, headers = mapOf("Location" to errorUri, "Cache-Control" to "no-store"), body = "")
                    }
                }

        // Fetch user claims from the wallet provider to carry through the authorization code
        val userClaims =
            userAuthProvider.getUserInfo(authenticatedUser.userId).let { result ->
                if (result.isOk) {
                    val info = result.value
                    buildMap<String, Any> {
                        info.username?.let { put("preferred_username", it) }
                        info.displayName?.let { put("name", it) }
                        info.email?.let { put("email", it) }
                        info.emailVerified?.let { put("email_verified", it) }
                        info.phoneNumber?.let { put("phone_number", it) }
                        info.phoneNumberVerified?.let { put("phone_number_verified", it) }
                        putAll(info.attributes)
                    }
                } else {
                    emptyMap()
                }
            }

        // Auto-approve: the wallet auth is already complete, issue authorization code
        val consent =
            ConsentDecision(
                userId = authenticatedUser.userId,
                clientId = session.clientId,
                granted = true,
                grantedScopes = session.scope?.split(" "),
                grantedAt = Clock.System.now(),
            )

        val approvalResult =
            handlers.handleAuthorizationApproval(
                session = session,
                userId = authenticatedUser.userId,
                consent = consent,
                userClaims = userClaims,
                acr = authenticatedUser.acr,
                amr = authenticatedUser.amr,
            )

        if (approvalResult.isOk) {
            return GenericHttpResponse(
                statusCode = 302,
                headers =
                    mapOf(
                        "Location" to approvalResult.value.redirectUri,
                        "Cache-Control" to "no-store",
                    ),
                body = "",
            )
        }

        // Error: redirect back to client with error
        val redirectUri = session.redirectUri
        val state = session.state
        val separator = if (redirectUri.contains("?")) "&" else "?"
        val errorUri = "${redirectUri}${separator}error=server_error&error_description=${urlEncode(approvalResult.error.message.defaultMessage ?: "Authorization approval failed")}"
        return if (state != null) {
            GenericHttpResponse(statusCode = 302, headers = mapOf("Location" to "$errorUri&state=${urlEncode(state)}", "Cache-Control" to "no-store"), body = "")
        } else {
            GenericHttpResponse(statusCode = 302, headers = mapOf("Location" to errorUri, "Cache-Control" to "no-store"), body = "")
        }
    }

    /**
     * PAR Endpoint Handler
     * RFC 9126: Pushed Authorization Requests
     * POST /par
     */
    private suspend fun handleParEndpoint(request: GenericHttpRequest): GenericHttpResponse {
        // Parse form-encoded body
        val requestBody =
            parseFormBody(request.body)
                ?: return errorResponse(400, "invalid_request", "Missing or invalid request body")

        // Call handler
        val result =
            handlers.handlePushedAuthorizationRequest(
                requestBody = requestBody,
                requestHeaders = request.headers,
            )

        return result.fold(
            success = { parResponse ->
                // Success: Return 201 with request_uri
                val responseBody = json.encodeToString(parResponse)
                GenericHttpResponse(
                    statusCode = 201,
                    headers =
                        mapOf(
                            "Content-Type" to "application/json",
                            "Cache-Control" to "no-cache",
                        ),
                    body = responseBody,
                )
            },
            failure = { error ->
                mapErrorToResponse(error)
            },
        )
    }

    /**
     * Token Introspection Endpoint Handler
     * RFC 7662: Token Introspection
     * POST /introspect
     */
    private suspend fun handleIntrospectionEndpoint(request: GenericHttpRequest): GenericHttpResponse {
        // Parse form-encoded body
        val requestBody =
            parseFormBody(request.body)
                ?: return errorResponse(400, "invalid_request", "Missing or invalid request body")

        // Call handler
        val result = handlers.handleIntrospectionRequest(requestBody)

        return result.fold(
            success = { introspectionResponse ->
                // Success: Return 200 with introspection response
                val responseBody = json.encodeToString(introspectionResponse)
                GenericHttpResponse(
                    statusCode = 200,
                    headers =
                        mapOf(
                            "Content-Type" to "application/json",
                        ),
                    body = responseBody,
                )
            },
            failure = { error ->
                mapErrorToResponse(error)
            },
        )
    }

    /**
     * Token Revocation Endpoint Handler
     * RFC 7009: Token Revocation
     * POST /revoke
     */
    private suspend fun handleRevocationEndpoint(request: GenericHttpRequest): GenericHttpResponse {
        // Parse form-encoded body
        val requestBody =
            parseFormBody(request.body)
                ?: return errorResponse(400, "invalid_request", "Missing or invalid request body")

        // Call handler
        val result = handlers.handleRevocationRequest(requestBody)

        return result.fold(
            success = {
                // RFC 7009: Always return 200 with empty body on success
                GenericHttpResponse(
                    statusCode = 200,
                    headers =
                        mapOf(
                            "Content-Type" to "application/json",
                            "Cache-Control" to "no-store",
                        ),
                    body = "",
                )
            },
            failure = { error ->
                // Per RFC 7009, client auth failure returns 401
                // Other errors still return 200 in most cases, but we follow
                // the error mapping for explicit server/config errors
                mapErrorToResponse(error)
            },
        )
    }

    /**
     * Discovery Endpoint Handler
     * RFC 8414: OAuth 2.0 Authorization Server Metadata
     * GET /.well-known/oauth-authorization-server
     * GET /.well-known/oauth-authorization-server/{tenant-path}
     */
    private suspend fun handleDiscoveryEndpoint(request: GenericHttpRequest): GenericHttpResponse {
        // Extract tenant path from URL if present
        val tenantPath = request.queryParameters["tenant-path"]

        // Build base URL: prefer configured issuer, fall back to request headers
        val baseUrlOverride = request.resolveBaseUrl(tenantPath)

        // Call handler
        val result =
            handlers.handleDiscoveryRequest(
                baseUrlOverride = baseUrlOverride,
            )

        return result.fold(
            success = { metadata ->
                val responseBody = json.encodeToString(metadata)
                GenericHttpResponse(
                    statusCode = 200,
                    headers =
                        mapOf(
                            "Content-Type" to "application/json",
                            "Cache-Control" to "max-age=3600",
                        ),
                    body = responseBody,
                )
            },
            failure = { error ->
                mapErrorToResponse(error)
            },
        )
    }

    // ========================================================================
    // OIDC Endpoint Handlers
    // ========================================================================

    /**
     * OIDC Discovery Endpoint Handler
     * OpenID Connect Discovery 1.0
     * GET /.well-known/openid-configuration
     *
     * Gated by config.oidc.isEnabled — returns 404 when OIDC is disabled.
     */
    private suspend fun handleOidcDiscoveryEndpoint(request: GenericHttpRequest): GenericHttpResponse {
        if (!configProvider.serverConfig.oidc.isEnabled) {
            return GenericHttpResponse(
                statusCode = 404,
                headers = mapOf("Content-Type" to "application/json"),
                body = json.encodeToString(mapOf("error" to "not_found", "error_description" to "OIDC is not enabled")),
            )
        }
        return handleDiscoveryEndpoint(request)
    }

    /**
     * UserInfo Endpoint Handler
     * OpenID Connect Core 1.0 Section 5.3
     * GET/POST /userinfo
     *
     * Gated by config.oidc.isEnabled — returns 404 when OIDC is disabled.
     */
    private suspend fun handleUserInfoEndpoint(request: GenericHttpRequest): GenericHttpResponse {
        if (!configProvider.serverConfig.oidc.isEnabled) {
            return GenericHttpResponse(
                statusCode = 404,
                headers = mapOf("Content-Type" to "application/json"),
                body = json.encodeToString(mapOf("error" to "not_found", "error_description" to "OIDC is not enabled")),
            )
        }

        // Extract Bearer token from Authorization header
        val authHeader = request.headers["authorization"] ?: request.headers["Authorization"]
        val accessToken =
            authHeader?.let { header ->
                val parts = header.trim().split(" ", limit = 2)
                if (parts.size == 2 && parts[0].equals("Bearer", ignoreCase = true)) parts[1] else null
            } ?: return errorResponse(401, "invalid_token", "Missing or invalid Bearer token")

        val result = handlers.handleUserInfoRequest(accessToken)

        return result.fold(
            success = { userInfoResponse ->
                val responseBody = json.encodeToString(userInfoResponse)
                GenericHttpResponse(
                    statusCode = 200,
                    headers = mapOf("Content-Type" to "application/json"),
                    body = responseBody,
                )
            },
            failure = { error -> mapErrorToResponse(error) },
        )
    }

    /**
     * JWKS Endpoint Handler
     * GET /.well-known/jwks.json
     *
     * Always available (needed for JWT access token verification regardless of OIDC mode).
     */
    private suspend fun handleJwksEndpoint(request: GenericHttpRequest): GenericHttpResponse {
        val result = handlers.handleJwksRequest()

        return result.fold(
            success = { jwksResult ->
                val responseBody = json.encodeToString(jwksResult)
                GenericHttpResponse(
                    statusCode = 200,
                    headers =
                        mapOf(
                            "Content-Type" to "application/json",
                            "Cache-Control" to "max-age=3600",
                        ),
                    body = responseBody,
                )
            },
            failure = { error -> mapErrorToResponse(error) },
        )
    }

    /**
     * Federation Callback Endpoint Handler
     * Handles the redirect back from an upstream IdP during federated authentication.
     * GET /federation/callback?code=...&state=...
     *
     * After completing upstream authentication:
     * 1. Retrieves the pending authorization session
     * 2. Auto-approves with the federated user identity
     * 3. Redirects back to the client with an authorization code
     */
    private suspend fun handleFederationCallbackEndpoint(request: GenericHttpRequest): GenericHttpResponse {
        val provider =
            findFederatedProvider(userAuthProvider)
                ?: return errorResponse(404, "not_found", "Federation is not configured")

        val code =
            request.queryParameters["code"]
                ?: return errorResponse(400, "invalid_request", "Missing 'code' parameter")
        val state =
            request.queryParameters["state"]
                ?: return errorResponse(400, "invalid_request", "Missing 'state' parameter")

        // Exchange upstream code for tokens and route based on flow type
        val callbackResult = provider.handleFederationCallback(code = code, state = state)
        if (callbackResult.isErr) {
            return errorResponse(400, "federation_error", callbackResult.error.message)
        }

        return when (val outcome = callbackResult.value) {
            is FederationCallbackOutcome.ReconciliationComplete -> {
                // Reconciliation flow — redirect browser to frontend (claims already sent to auth-bridge)
                GenericHttpResponse(
                    statusCode = 302,
                    headers = mapOf("Location" to outcome.redirectUrl, "Cache-Control" to "no-store"),
                    body = "",
                )
            }

            is FederationCallbackOutcome.FederationComplete -> {
                handleFederationCompleteOutcome(provider, outcome.sessionId)
            }
        }
    }

    /**
     * Handle the normal federation login completion: issue an authorization code
     * and redirect back to the client.
     */
    private suspend fun handleFederationCompleteOutcome(
        provider: FederatedUserAuthenticationProvider,
        sessionId: String,
    ): GenericHttpResponse {
        // Retrieve the pending local authorization session
        val session =
            pendingSessions.remove(sessionId)
                ?: return errorResponse(400, "invalid_request", "No pending authorization session for this callback")

        // Get authenticated user from the provider
        val authUserResult = provider.getAuthenticatedUser(sessionId)
        if (authUserResult.isErr) {
            return errorResponse(500, "server_error", "Failed to get authenticated user: ${authUserResult.error.message}")
        }
        val authenticatedUser =
            authUserResult.value
                ?: return errorResponse(500, "server_error", "User not authenticated after federation callback")

        // Fetch user claims from the provider to carry through the authorization code
        val userClaims =
            provider.getUserInfo(authenticatedUser.userId).let { result ->
                if (result.isOk) {
                    val info = result.value
                    buildMap<String, Any> {
                        info.username?.let { put("preferred_username", it) }
                        info.displayName?.let { put("name", it) }
                        info.email?.let { put("email", it) }
                        info.emailVerified?.let { put("email_verified", it) }
                        info.phoneNumber?.let { put("phone_number", it) }
                        info.phoneNumberVerified?.let { put("phone_number_verified", it) }
                        putAll(info.attributes)
                    }
                } else {
                    emptyMap()
                }
            }

        // Auto-approve: create consent and generate authorization code
        val consent =
            ConsentDecision(
                userId = authenticatedUser.userId,
                clientId = session.clientId,
                granted = true,
                grantedScopes = session.scope?.split(" "),
                grantedAt = Clock.System.now(),
            )

        val approvalResult =
            handlers.handleAuthorizationApproval(
                session = session,
                userId = authenticatedUser.userId,
                consent = consent,
                userClaims = userClaims,
                acr = authenticatedUser.acr,
                amr = authenticatedUser.amr,
            )
        if (approvalResult.isOk) {
            return GenericHttpResponse(
                statusCode = 302,
                headers =
                    mapOf(
                        "Location" to approvalResult.value.redirectUri,
                        "Cache-Control" to "no-store",
                    ),
                body = "",
            )
        }

        // Error: redirect back to client with error
        val redirectUri = session.redirectUri
        val sessionState = session.state
        val separator = if (redirectUri.contains("?")) "&" else "?"
        val errorUri = "${redirectUri}${separator}error=server_error&error_description=${urlEncode(approvalResult.error.message.defaultMessage ?: "Authorization approval failed")}"
        return if (sessionState != null) {
            GenericHttpResponse(statusCode = 302, headers = mapOf("Location" to "$errorUri&state=${urlEncode(sessionState)}", "Cache-Control" to "no-store"), body = "")
        } else {
            GenericHttpResponse(statusCode = 302, headers = mapOf("Location" to errorUri, "Cache-Control" to "no-store"), body = "")
        }
    }

    /**
     * GET /federation/providers — returns the list of enabled federation providers.
     * Used by the frontend login UI to render provider selection buttons.
     */
    private fun handleFederationProvidersEndpoint(request: GenericHttpRequest): GenericHttpResponse {
        val provider =
            findFederatedProvider(userAuthProvider)
                ?: return errorResponse(404, "not_found", "Federation is not configured")

        val enabledProviders =
            provider.getEnabledProviders().map { config ->
                kotlinx.serialization.json.JsonObject(
                    mapOf(
                        "id" to kotlinx.serialization.json.JsonPrimitive(config.id),
                        "name" to kotlinx.serialization.json.JsonPrimitive(config.name),
                        "enabled" to kotlinx.serialization.json.JsonPrimitive(config.enabled),
                    ),
                )
            }
        val body = kotlinx.serialization.json.JsonArray(enabledProviders)

        return GenericHttpResponse(
            statusCode = 200,
            headers = mapOf("Content-Type" to "application/json", "Cache-Control" to "no-store"),
            body = body.toString(),
        )
    }

    /**
     * GET /reconciliation/authorize?oid4vp_session={id}&provider={providerId}
     *
     * Initiates an OIDC authorization request to the upstream IdP for identity
     * verification (reconciliation). The STS acts as the single OIDC RP for all flows.
     * After the IdP authenticates the user, the callback returns to /federation/callback
     * where the reconciliation flow is detected and claims are forwarded to the auth-bridge.
     */
    private suspend fun handleReconciliationAuthorizeEndpoint(request: GenericHttpRequest): GenericHttpResponse {
        val provider =
            findFederatedProvider(userAuthProvider)
                ?: return errorResponse(404, "not_found", "Federation is not configured")

        val oid4vpSessionId =
            request.queryParameters["oid4vp_session"]
                ?: return errorResponse(400, "invalid_request", "Missing 'oid4vp_session' parameter")
        val providerId =
            request.queryParameters["provider"]
                ?: return errorResponse(400, "invalid_request", "Missing 'provider' parameter")

        // Verify the provider exists and is enabled
        provider.getProvider(providerId)
            ?: return errorResponse(400, "invalid_request", "Unknown or disabled provider: $providerId")

        // Determine the STS base URL for building the callback redirect URI
        val stsBaseUrl = configProvider.serverConfig.baseUrl

        // Generate a session ID for this reconciliation flow
        val reconciliationSessionId =
            "recon-" +
                kotlin.random.Random.Default
                    .nextBytes(16)
                    .joinToString("") { it.toUByte().toString(16).padStart(2, '0') }

        // Initiate authentication with reconciliation flow context
        val authUrlResult =
            provider.initiateProviderAuthentication(
                sessionId = reconciliationSessionId,
                returnUrl = stsBaseUrl,
                providerId = providerId,
                flowContext =
                    FlowContext(
                        flow = "reconciliation",
                        oid4vpSessionId = oid4vpSessionId,
                    ),
            )

        if (authUrlResult.isErr) {
            return errorResponse(500, "server_error", "Failed to initiate reconciliation: ${authUrlResult.error.message}")
        }

        return GenericHttpResponse(
            statusCode = 302,
            headers = mapOf("Location" to authUrlResult.value, "Cache-Control" to "no-store"),
            body = "",
        )
    }

    /**
     * IAE (Interactive Authorization Endpoint) Handler
     * OID4VCI 1.1 Section 6: Interactive Authorization Endpoint
     * POST /iae
     *
     * Handles both initial requests (no auth_session) and follow-up requests (auth_session present).
     */
    private suspend fun handleIaeEndpoint(request: GenericHttpRequest): GenericHttpResponse {
        val requestBody =
            parseFormBody(request.body)
                ?: return errorResponse(400, "invalid_request", "Missing or invalid request body")

        // Single-value map for easy parameter access
        val params = requestBody.mapValues { (_, values) -> values.first() }

        val authSession = params["auth_session"]

        return if (authSession != null) {
            // Follow-up request: auth_session is present
            val openid4vpResponseStr = params["openid4vp_response"]
            val openid4vpResponse: JsonObject? =
                if (openid4vpResponseStr != null) {
                    try {
                        json.parseToJsonElement(openid4vpResponseStr).jsonObject
                    } catch (expected: Exception) {
                        return errorResponse(400, "invalid_request", "Invalid openid4vp_response JSON: ${expected.message}")
                    }
                } else {
                    null
                }

            val followUpResult =
                handleIaeFollowUpCommand.execute(
                    HandleIaeFollowUpArgs(
                        authSession = authSession,
                        openid4vpResponse = openid4vpResponse,
                        codeVerifier = params["code_verifier"],
                    ),
                )

            followUpResult.fold(
                success = { iaeResult -> iaeResultToResponse(iaeResult) },
                failure = { error -> errorResponse(400, "invalid_request", error.message.defaultMessage) },
            )
        } else {
            // Initial request: auth_session is absent
            val clientId =
                params["client_id"]
                    ?: return errorResponse(400, "invalid_request", "Missing required parameter: client_id")
            val redirectUri =
                params["redirect_uri"]
                    ?: return errorResponse(400, "invalid_request", "Missing required parameter: redirect_uri")

            val interactionTypesStr = params["interaction_types_supported"] ?: ""
            val interactionTypes =
                if (interactionTypesStr.isBlank()) {
                    emptyList()
                } else {
                    interactionTypesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                }

            val authorizationDetailsStr = params["authorization_details"]
            val authorizationDetails =
                if (authorizationDetailsStr != null) {
                    try {
                        val element = json.parseToJsonElement(authorizationDetailsStr)
                        (element as? kotlinx.serialization.json.JsonArray)?.toList()
                    } catch (expected: Exception) {
                        return errorResponse(400, "invalid_request", "Invalid authorization_details JSON: ${expected.message}")
                    }
                } else {
                    null
                }

            val initialResult =
                handleIaeInitialRequestCommand.execute(
                    HandleIaeInitialRequestArgs(
                        clientId = clientId,
                        responseType = params["response_type"] ?: "code",
                        redirectUri = redirectUri,
                        interactionTypesSupported = interactionTypes,
                        authorizationDetails = authorizationDetails,
                        scope = params["scope"],
                        codeChallenge = params["code_challenge"],
                        codeChallengeMethod = params["code_challenge_method"],
                        request = params["request"],
                    ),
                )

            initialResult.fold(
                success = { iaeResult -> iaeResultToResponse(iaeResult) },
                failure = { error -> errorResponse(400, "invalid_request", error.message.defaultMessage) },
            )
        }
    }

    /**
     * Map an [IaeResult] to the appropriate [GenericHttpResponse].
     *
     * OID4VCI 1.1 Section 6:
     * - InteractionRequired → HTTP 200, require_interaction JSON
     * - AuthorizationCode → HTTP 200, ok JSON
     * - Error → HTTP 400, error JSON
     */
    private fun iaeResultToResponse(result: IaeResult): GenericHttpResponse =
        when (result) {
            is IaeResult.InteractionRequired -> {
                GenericHttpResponse(
                    statusCode = 200,
                    headers = mapOf("Content-Type" to "application/json", "Cache-Control" to "no-store"),
                    body = json.encodeToString(IaeInteractionRequiredResponse.serializer(), result.response),
                )
            }

            is IaeResult.AuthorizationCode -> {
                GenericHttpResponse(
                    statusCode = 200,
                    headers = mapOf("Content-Type" to "application/json", "Cache-Control" to "no-store"),
                    body = json.encodeToString(IaeAuthorizationCodeResponse.serializer(), result.response),
                )
            }

            is IaeResult.Error -> {
                GenericHttpResponse(
                    statusCode = 400,
                    headers = mapOf("Content-Type" to "application/json", "Cache-Control" to "no-store"),
                    body = json.encodeToString(IaeErrorResponse.serializer(), result.response),
                )
            }
        }

    // ========================================================================
    // Helper Functions
    // ========================================================================

    /**
     * Parse form-encoded request body into a multi-value map.
     * Format: key1=value1&key2=value2&key2=value3
     *
     * Returns Map<String, List<String>> to support repeated parameters
     * (e.g., resource=A&resource=B per RFC 8693/RFC 8707).
     */
    private fun parseFormBody(body: String?): Map<String, List<String>>? {
        if (body == null || body.isBlank()) {
            return null
        }

        return try {
            body
                .split('&')
                .mapNotNull { param ->
                    val parts = param.split('=', limit = 2)
                    if (parts.size == 2) {
                        urlDecode(parts[0]) to urlDecode(parts[1])
                    } else {
                        null
                    }
                }.groupBy({ it.first }, { it.second })
        } catch (_: Exception) {
            null
        }
    }

    /**
     * URL encode a string for use in query parameters.
     */
    private fun urlEncode(value: String): String {
        val sb = StringBuilder()
        for (c in value) {
            when {
                c.isLetterOrDigit() || c in "-_.~" -> {
                    sb.append(c)
                }

                c == ' ' -> {
                    sb.append('+')
                }

                else -> {
                    val bytes = c.toString().encodeToByteArray()
                    for (b in bytes) {
                        sb.append('%')
                        sb.append(((b.toInt() shr 4) and 0xF).toString(16).uppercase())
                        sb.append((b.toInt() and 0xF).toString(16).uppercase())
                    }
                }
            }
        }
        return sb.toString()
    }

    /**
     * URL decode a string (simple implementation).
     */
    private fun urlDecode(value: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            when {
                value[i] == '+' -> {
                    sb.append(' ')
                    i++
                }

                value[i] == '%' && i + 2 < value.length -> {
                    val hex = value.substring(i + 1, i + 3)
                    val code = hex.toIntOrNull(16)
                    if (code != null) {
                        sb.append(code.toChar())
                        i += 3
                    } else {
                        sb.append(value[i])
                        i++
                    }
                }

                else -> {
                    sb.append(value[i])
                    i++
                }
            }
        }
        return sb.toString()
    }

    /**
     * Build full URL from request for DPoP verification.
     */
    private fun buildFullUrl(request: GenericHttpRequest): String {
        val host = request.hostHeader()
        val scheme = request.forwardedScheme()
        return "$scheme://$host${request.path}"
    }

    /**
     * Map IdkError to HTTP response.
     * Uses error codes from AuthorizationServerError (preserved through IdkError).
     */
    private fun mapErrorToResponse(error: IdkError): GenericHttpResponse {
        val errorDescription = error.message.defaultMessage
        return when (error.code) {
            "invalid_request" -> errorResponse(400, "invalid_request", errorDescription)
            "invalid_client" -> errorResponse(401, "invalid_client", errorDescription)
            "unauthorized_client" -> errorResponse(401, "unauthorized_client", errorDescription)
            "invalid_grant" -> errorResponse(400, "invalid_grant", errorDescription)
            "unsupported_grant_type" -> errorResponse(400, "unsupported_grant_type", errorDescription)
            "invalid_scope" -> errorResponse(400, "invalid_scope", errorDescription)
            "invalid_target" -> errorResponse(400, "invalid_target", errorDescription)
            "access_denied" -> errorResponse(403, "access_denied", errorDescription)
            "unsupported_response_type" -> errorResponse(400, "unsupported_response_type", errorDescription)
            "temporarily_unavailable" -> errorResponse(503, "temporarily_unavailable", errorDescription)
            "invalid_dpop_proof" -> errorResponse(400, "invalid_dpop_proof", errorDescription)
            "use_dpop_nonce" -> errorResponse(400, "use_dpop_nonce", errorDescription)
            "server_error" -> errorResponse(500, "server_error", errorDescription)
            "storage_error" -> errorResponse(500, "server_error", errorDescription)
            "session_not_found" -> errorResponse(400, "invalid_request", errorDescription)
            "client_not_found" -> errorResponse(401, "invalid_client", errorDescription)
            else -> errorResponse(500, "server_error", errorDescription ?: "An unexpected error occurred")
        }
    }

    /**
     * Create an OAuth2 error response.
     * RFC 6749 Section 5.2: Error Response
     */
    private fun errorResponse(
        statusCode: Int,
        error: String,
        errorDescription: String? = null,
    ): GenericHttpResponse {
        val errorBody =
            buildMap {
                put("error", error)
                if (errorDescription != null) {
                    put("error_description", errorDescription)
                }
            }

        return GenericHttpResponse(
            statusCode = statusCode,
            headers =
                mapOf(
                    "Content-Type" to "application/json",
                    "Cache-Control" to "no-store",
                ),
            body = json.encodeToString(errorBody),
        )
    }

    private fun findFederatedProvider(provider: UserAuthenticationProvider): FederatedUserAuthenticationProvider? =
        when (provider) {
            is FederatedUserAuthenticationProvider -> provider
            is CompositeUserAuthenticationProvider -> provider.federationProvider as? FederatedUserAuthenticationProvider
            else -> null
        }

    /**
     * Internal endpoint for cross-service pre-authorized code registration.
     * Used when the OID4VCI issuer runs in a separate process.
     *
     * Requires Basic auth with a client_id:client_secret configured in
     * `oauth2.servers.{id}.internal-clients`.
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private suspend fun handleRegisterPreAuthCode(request: GenericHttpRequest): GenericHttpResponse {
        // Validate Basic auth
        val authHeader = request.headers["authorization"] ?: request.headers["Authorization"] ?: ""
        if (!authHeader.startsWith("Basic ", ignoreCase = true)) {
            return errorResponse(401, "invalid_client", "Basic authentication required")
        }
        val decoded =
            try {
                kotlin.io.encoding.Base64
                    .decode(authHeader.removePrefix("Basic ").removePrefix("basic ").trim())
                    .decodeToString()
            } catch (_: Exception) {
                return errorResponse(401, "invalid_client", "Invalid Basic auth encoding")
            }
        val parts = decoded.split(":", limit = 2)
        if (parts.size != 2) {
            return errorResponse(401, "invalid_client", "Invalid Basic auth format")
        }
        val (clientId, clientSecret) = parts

        val config = configProvider.getDefaultServer()
        val allowedClientId = config.internalClients["issuer"]?.first
        val allowedClientSecret = config.internalClients["issuer"]?.second
        if (allowedClientId == null || clientId != allowedClientId || clientSecret != allowedClientSecret) {
            return errorResponse(401, "invalid_client", "Invalid client credentials")
        }

        val body = request.body ?: return errorResponse(400, "invalid_request", "Missing request body")
        val req =
            try {
                json.decodeFromString<PreAuthCodeRegistrationRequest>(body)
            } catch (expected: Exception) {
                return errorResponse(400, "invalid_request", "Invalid JSON: ${expected.message}")
            }

        val now = Clock.System.now()
        val data =
            PreAuthorizedCodeData(
                sessionId = req.sessionId,
                credentialConfigurationIds = req.credentialConfigurationIds,
                txCodeRequired = req.txCodeRequired,
                txCodeHash = req.txCodeHash,
                issuerIdentifier = req.issuerIdentifier,
                useCredentialIdentifiers = req.useCredentialIdentifiers,
                createdAt = now,
                expiresAt = now + 10.minutes,
            )

        preAuthorizedCodeStorage.storePreAuthorizedCode(req.code, data).getOrElse { error ->
            return errorResponse(500, "server_error", "Failed to store code: ${error.details}")
        }

        return GenericHttpResponse(
            statusCode = 200,
            headers = mapOf("Content-Type" to "application/json"),
            body = json.encodeToString(mapOf("status" to "ok")),
        )
    }
}

@Serializable
data class PreAuthCodeRegistrationRequest(
    val code: String,
    val sessionId: String,
    val credentialConfigurationIds: List<String>,
    val txCodeRequired: Boolean = false,
    val txCodeHash: String? = null,
    val issuerIdentifier: String? = null,
    val useCredentialIdentifiers: Boolean = true,
)
