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
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
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
import com.sphereon.oauth2.server.authorization.command.JwksResult
import com.sphereon.oauth2.server.authorization.command.UserInfoResponse
import com.sphereon.oauth2.server.authorization.impl.oidc.OidcScopeClaimsMapper
import com.sphereon.oauth2.server.authorization.impl.provider.CompositeUserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.impl.provider.FederatedUserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.ConsentDecision
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet

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
    private val scopeClaimsMapper: OidcScopeClaimsMapper
) : RoutedHttpAdapter() {

    companion object {
        const val ID: String = "OAUTH2_AUTHZ"

        // Static map — must survive across SessionScope instances (each HTTP request = new session)
        // In production, this should be backed by a persistent session store
        private val pendingSessions = mutableMapOf<String, AuthorizationSession>()
    }

    override val id: String = ID

    @ContributesTo(SessionScope::class)
    interface Component {
        val oAuth2HttpAdapter: OAuth2HttpAdapter
    }

    override val mount: HttpAdapterMount = HttpAdapterMount(
        serverPrefix = "",
        adapterBasePath = "/"
    )

    override val routes = httpRoutes {
        post("/token") {
            operationId("token")
            consumes(MediaType.ApplicationFormUrlEncoded)
            produces(MediaType.ApplicationJson)
            handle { req -> handleTokenEndpoint(req) }
        }
        get("/authorize") {
            operationId("authorize")
            produces(MediaType.ApplicationJson)
            handle { req -> handleAuthorizationEndpoint(req) }
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
    }

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

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
        val requestBody = parseFormBody(request.body)
            ?: return errorResponse(400, "invalid_request", "Missing or invalid request body")

        // Extract full URL for DPoP
        val httpUrl = buildFullUrl(request)

        // Call handler
        val result = handlers.handleTokenRequest(
            requestBody = requestBody,
            requestHeaders = request.headers,
            httpUrl = httpUrl
        )

        return result.fold(
            success = { tokenResponse ->
                // Success: Return 200 with token response
                val responseBody = json.encodeToString(tokenResponse)
                GenericHttpResponse(
                    statusCode = 200,
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Cache-Control" to "no-store",
                        "Pragma" to "no-cache"
                    ),
                    body = responseBody
                )
            },
            failure = { error ->
                mapErrorToResponse(error)
            }
        )
    }

    /**
     * Authorization Endpoint Handler
     * RFC 6749 Section 3.1: Authorization Endpoint
     * GET /authorize
     *
     * When a federated authentication provider is configured:
     * 1. Creates authorization session
     * 2. Redirects to upstream IdP for authentication
     *
     * When no federation is configured, returns the session as JSON
     * for the application to handle authentication externally.
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

                // Build the return URL (STS base URL) from the request
                val host = request.headers["host"] ?: request.headers["Host"] ?: "localhost"
                val scheme = if (request.headers["x-forwarded-proto"] == "https") "https" else "http"
                val returnUrl = "$scheme://$host"

                // Initiate authentication via the provider (federation, composite, or other)
                val hint = loginHint?.let { AuthenticationHint(loginHint = it) }
                val authResult = userAuthProvider.initiateAuthentication(
                    sessionId = session.sessionId,
                    returnUrl = returnUrl,
                    hint = hint
                )
                if (authResult.isOk) {
                    pendingSessions[session.sessionId] = session
                    return GenericHttpResponse(
                        statusCode = 302,
                        headers = mapOf(
                            "Location" to authResult.value,
                            "Cache-Control" to "no-store"
                        ),
                        body = ""
                    )
                }

                // Authentication initiation not available (no provider configured) —
                // fall back to returning session as JSON for the application to handle externally
                val sessionJson = buildMap {
                    put("sessionId", session.sessionId)
                    put("clientId", session.clientId)
                    session.scope?.let { put("scope", it) }
                    session.state?.let { put("state", it) }
                    session.redirectUri.let { put("redirectUri", it) }
                }
                GenericHttpResponse(
                    statusCode = 200,
                    headers = mapOf("Content-Type" to "application/json", "Cache-Control" to "no-store"),
                    body = json.encodeToString(sessionJson)
                )
            },
            failure = { error ->
                mapErrorToResponse(error)
            }
        )
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
        loginHint: String
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

        val authenticatedUser = authResult.value
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
        val userClaims = userAuthProvider.getUserInfo(authenticatedUser.userId).let { result ->
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
            } else emptyMap()
        }

        // Auto-approve: the wallet auth is already complete, issue authorization code
        val consent = ConsentDecision(
            userId = authenticatedUser.userId,
            clientId = session.clientId,
            granted = true,
            grantedScopes = session.scope?.split(" "),
            grantedAt = Clock.System.now()
        )

        val approvalResult = handlers.handleAuthorizationApproval(
            session = session,
            userId = authenticatedUser.userId,
            consent = consent,
            userClaims = userClaims
        )

        if (approvalResult.isOk) {
            return GenericHttpResponse(
                statusCode = 302,
                headers = mapOf(
                    "Location" to approvalResult.value.redirectUri,
                    "Cache-Control" to "no-store"
                ),
                body = ""
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
        val requestBody = parseFormBody(request.body)
            ?: return errorResponse(400, "invalid_request", "Missing or invalid request body")

        // Call handler
        val result = handlers.handlePushedAuthorizationRequest(
            requestBody = requestBody,
            requestHeaders = request.headers
        )

        return result.fold(
            success = { parResponse ->
                // Success: Return 201 with request_uri
                val responseBody = json.encodeToString(parResponse)
                GenericHttpResponse(
                    statusCode = 201,
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Cache-Control" to "no-cache"
                    ),
                    body = responseBody
                )
            },
            failure = { error ->
                mapErrorToResponse(error)
            }
        )
    }

    /**
     * Token Introspection Endpoint Handler
     * RFC 7662: Token Introspection
     * POST /introspect
     */
    private suspend fun handleIntrospectionEndpoint(request: GenericHttpRequest): GenericHttpResponse {
        // Parse form-encoded body
        val requestBody = parseFormBody(request.body)
            ?: return errorResponse(400, "invalid_request", "Missing or invalid request body")

        // Call handler
        val result = handlers.handleIntrospectionRequest(requestBody)

        return result.fold(
            success = { introspectionResponse ->
                // Success: Return 200 with introspection response
                val responseBody = json.encodeToString(introspectionResponse)
                GenericHttpResponse(
                    statusCode = 200,
                    headers = mapOf(
                        "Content-Type" to "application/json"
                    ),
                    body = responseBody
                )
            },
            failure = { error ->
                mapErrorToResponse(error)
            }
        )
    }

    /**
     * Token Revocation Endpoint Handler
     * RFC 7009: Token Revocation
     * POST /revoke
     */
    private suspend fun handleRevocationEndpoint(request: GenericHttpRequest): GenericHttpResponse {
        // Parse form-encoded body
        val requestBody = parseFormBody(request.body)
            ?: return errorResponse(400, "invalid_request", "Missing or invalid request body")

        // Call handler
        val result = handlers.handleRevocationRequest(requestBody)

        return result.fold(
            success = {
                // RFC 7009: Always return 200 with empty body on success
                GenericHttpResponse(
                    statusCode = 200,
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Cache-Control" to "no-store"
                    ),
                    body = ""
                )
            },
            failure = { error ->
                // Per RFC 7009, client auth failure returns 401
                // Other errors still return 200 in most cases, but we follow
                // the error mapping for explicit server/config errors
                mapErrorToResponse(error)
            }
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

        // Build base URL from request
        val host = request.headers["host"] ?: request.headers["Host"] ?: "localhost"
        val scheme = if (request.headers["x-forwarded-proto"] == "https") "https" else "http"
        val baseUrlOverride = if (tenantPath != null) "$scheme://$host/$tenantPath" else "$scheme://$host"

        // Call handler
        val result = handlers.handleDiscoveryRequest(
            baseUrlOverride = baseUrlOverride
        )

        return result.fold(
            success = { metadata ->
                val responseBody = json.encodeToString(metadata)
                GenericHttpResponse(
                    statusCode = 200,
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Cache-Control" to "max-age=3600"
                    ),
                    body = responseBody
                )
            },
            failure = { error ->
                mapErrorToResponse(error)
            }
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
                body = json.encodeToString(mapOf("error" to "not_found", "error_description" to "OIDC is not enabled"))
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
                body = json.encodeToString(mapOf("error" to "not_found", "error_description" to "OIDC is not enabled"))
            )
        }

        // Extract Bearer token from Authorization header
        val authHeader = request.headers["authorization"] ?: request.headers["Authorization"]
        val accessToken = authHeader?.let { header ->
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
                    body = responseBody
                )
            },
            failure = { error -> mapErrorToResponse(error) }
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
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Cache-Control" to "max-age=3600"
                    ),
                    body = responseBody
                )
            },
            failure = { error -> mapErrorToResponse(error) }
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
        val provider = findFederatedProvider(userAuthProvider)
            ?: return errorResponse(404, "not_found", "Federation is not configured")

        val code = request.queryParameters["code"]
            ?: return errorResponse(400, "invalid_request", "Missing 'code' parameter")
        val state = request.queryParameters["state"]
            ?: return errorResponse(400, "invalid_request", "Missing 'state' parameter")

        // Exchange upstream code for tokens and validate ID token
        val callbackResult = provider.handleFederationCallback(code = code, state = state)
        if (callbackResult.isErr) {
            return errorResponse(400, "federation_error", callbackResult.error.message)
        }
        val sessionId = callbackResult.value

        // Retrieve the pending local authorization session
        val session = pendingSessions.remove(sessionId)
            ?: return errorResponse(400, "invalid_request", "No pending authorization session for this callback")

        // Get authenticated user from the provider
        val authUserResult = provider.getAuthenticatedUser(sessionId)
        if (authUserResult.isErr) {
            return errorResponse(500, "server_error", "Failed to get authenticated user: ${authUserResult.error.message}")
        }
        val authenticatedUser = authUserResult.value
            ?: return errorResponse(500, "server_error", "User not authenticated after federation callback")

        // Fetch user claims from the provider to carry through the authorization code
        val userClaims = provider.getUserInfo(authenticatedUser.userId).let { result ->
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
            } else emptyMap()
        }

        // Auto-approve: create consent and generate authorization code
        val consent = ConsentDecision(
            userId = authenticatedUser.userId,
            clientId = session.clientId,
            granted = true,
            grantedScopes = session.scope?.split(" "),
            grantedAt = Clock.System.now()
        )

        val approvalResult = handlers.handleAuthorizationApproval(
            session = session,
            userId = authenticatedUser.userId,
            consent = consent,
            userClaims = userClaims
        )
        if (approvalResult.isOk) {
            return GenericHttpResponse(
                statusCode = 302,
                headers = mapOf(
                    "Location" to approvalResult.value.redirectUri,
                    "Cache-Control" to "no-store"
                ),
                body = ""
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
            body.split('&')
                .mapNotNull { param ->
                    val parts = param.split('=', limit = 2)
                    if (parts.size == 2) {
                        urlDecode(parts[0]) to urlDecode(parts[1])
                    } else {
                        null
                    }
                }
                .groupBy({ it.first }, { it.second })
        } catch (e: Exception) {
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
                c.isLetterOrDigit() || c in "-_.~" -> sb.append(c)
                c == ' ' -> sb.append('+')
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
        // Extract host from headers
        val host = request.headers["host"] ?: request.headers["Host"] ?: "localhost"
        val scheme = if (request.headers["x-forwarded-proto"] == "https") "https" else "http"

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
    private fun errorResponse(statusCode: Int, error: String, errorDescription: String? = null): GenericHttpResponse {
        val errorBody = buildMap {
            put("error", error)
            if (errorDescription != null) {
                put("error_description", errorDescription)
            }
        }

        return GenericHttpResponse(
            statusCode = statusCode,
            headers = mapOf(
                "Content-Type" to "application/json",
                "Cache-Control" to "no-store"
            ),
            body = json.encodeToString(errorBody)
        )
    }

    private fun findFederatedProvider(provider: UserAuthenticationProvider): FederatedUserAuthenticationProvider? {
        return when (provider) {
            is FederatedUserAuthenticationProvider -> provider
            is CompositeUserAuthenticationProvider -> provider.federationProvider as? FederatedUserAuthenticationProvider
            else -> null
        }
    }
}
