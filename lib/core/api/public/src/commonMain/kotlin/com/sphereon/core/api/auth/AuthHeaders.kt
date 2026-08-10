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

package com.sphereon.core.api.auth

import com.sphereon.core.api.binary.BinaryRequest
import com.sphereon.core.api.http.util.RequestUtils
import com.sphereon.core.api.tracing.TraceContext
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.context.IdentityConstants
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmStatic

/**
 * Standard authentication and authorization headers.
 *
 * Defined in IDK so all layers (IDK, EDK, VDX) use consistent header names.
 * These headers are used for:
 * - Authentication (tokens, credentials)
 * - Authenticated bearer tokens
 * - Distributed tracing
 * - Policy hints
 */
object AuthHeaders {
    // ========================================
    // Authentication Headers
    // ========================================

    /**
     * Standard HTTP Host header.
     */
    const val HOST = "Host"

    /**
     * Standard Authorization header for bearer tokens.
     * Format: "Bearer {token}"
     */
    const val AUTHORIZATION = "Authorization"

    /**
     * API key authentication (alternative to Bearer).
     */
    const val X_API_KEY = "X-API-Key"

    // Rejected inbound names. These constants exist only for centralized
    // stripping and negative tests; none may establish authenticated identity.
    const val X_TENANT_ID = "X-Tenant-Id"
    const val X_USER_ID = "X-User-Id"
    const val X_PRINCIPAL_ID = "X-Principal-Id"
    const val X_SERVICE_ID = "X-Service-Id"

    // ========================================
    // Tracing Headers (W3C Trace Context)
    // ========================================

    /**
     * W3C traceparent header for distributed tracing.
     * Format: "00-{traceId}-{spanId}-{traceFlags}"
     * See: https://www.w3.org/TR/trace-context/#traceparent-header
     */
    const val TRACEPARENT = "traceparent"

    /**
     * W3C tracestate header for vendor-specific trace context.
     * See: https://www.w3.org/TR/trace-context/#tracestate-header
     */
    const val TRACESTATE = "tracestate"

    /**
     * Request ID (unique per request).
     */
    const val X_REQUEST_ID = "X-Request-Id"

    /**
     * Correlation ID (groups related requests).
     */
    const val X_CORRELATION_ID = "X-Correlation-Id"

    // ========================================
    // Policy Headers
    // ========================================

    /**
     * JSON-encoded additional context for policy evaluation.
     */
    const val X_POLICY_CONTEXT = "X-Policy-Context"

    /**
     * Requested scope for the operation.
     */
    const val X_SCOPE = "X-Scope"

    // ========================================
    // Proxy / Forwarding Headers
    // ========================================

    /**
     * Client IP address when behind a reverse proxy.
     */
    const val X_FORWARDED_FOR = "X-Forwarded-For"

    /**
     * Original protocol (http/https) when behind a reverse proxy.
     */
    const val X_FORWARDED_PROTO = "X-Forwarded-Proto"

    /**
     * Original host requested by the client.
     */
    const val X_FORWARDED_HOST = "X-Forwarded-Host"

    /**
     * Original port when behind a reverse proxy.
     */
    const val X_FORWARDED_PORT = "X-Forwarded-Port"

    /**
     * Path prefix prepended by the reverse proxy.
     */
    const val X_FORWARDED_PREFIX = "X-Forwarded-Prefix"

    // ========================================
    // Transport Metadata Headers
    // ========================================

    /**
     * Original HTTP method (when tunneling through POST).
     */
    const val X_HTTP_METHOD = "X-HTTP-Method"

    /**
     * Original HTTP path.
     */
    const val X_HTTP_PATH = "X-HTTP-Path"

    /**
     * Content encoding (gzip, etc.).
     */
    const val CONTENT_ENCODING = "Content-Encoding"

    /**
     * Idempotency key for safe retries.
     */
    const val X_IDEMPOTENCY_KEY = "X-Idempotency-Key"
}

/**
 * Authentication and tracing context.
 *
 * This data class holds all authentication and authorization
 * Identity fields are populated only after JWT validation; [fromHeaders] never
 * derives tenant, user, principal, service, scopes, or policy context from
 * caller-controlled HTTP metadata.
 *
 * @property token The bearer token (without "Bearer " prefix)
 * @property apiKey API key if present
 * @property tenantId Tenant identifier
 * @property userId User identifier
 * @property principalId Principal identifier
 * @property serviceId Service identifier (for S2S calls)
 * @property traceparent W3C traceparent header value for distributed tracing
 * @property tracestate W3C tracestate header value for vendor-specific trace context
 * @property requestId Request-specific ID
 * @property correlationId Correlation ID for request grouping
 * @property policyContext Additional policy evaluation context
 * @property scopes Requested operation scopes
 */
@JsExportCompat
@Serializable
data class AuthContext(
    val token: String? = null,
    val apiKey: String? = null,
    val tenantId: String? = null,
    val userId: String? = null,
    val principalId: String? = null,
    val serviceId: String? = null,
    val traceparent: String? = null,
    val tracestate: String? = null,
    val requestId: String? = null,
    val correlationId: String? = null,
    val policyContext: Map<String, String> = emptyMap(),
    val scopes: Set<String> = emptySet(),
) {
    /**
     * Whether this context has any authentication credentials.
     */
    val isAuthenticated: Boolean
        get() = token != null || apiKey != null

    /**
     * Whether this is a service-to-service call.
     */
    val isServiceCall: Boolean
        get() = serviceId != null

    /**
     * The effective principal (user or service).
     */
    val effectivePrincipal: String?
        get() = principalId ?: userId ?: serviceId

    /**
     * Extracts the trace ID from the W3C traceparent header.
     * Delegates to [TraceContext.fromW3CTraceparent] for spec-compliant parsing.
     * Returns null if traceparent is not set or invalid.
     */
    val traceId: String?
        get() = traceparent?.let { TraceContext.fromW3CTraceparent(it)?.traceId }

    /**
     * Extracts the span ID from the W3C traceparent header.
     * Delegates to [TraceContext.fromW3CTraceparent] for spec-compliant parsing.
     * Returns null if traceparent is not set or invalid.
     */
    val spanId: String?
        get() = traceparent?.let { TraceContext.fromW3CTraceparent(it)?.spanId }

    /**
     * Creates a copy with an additional scope.
     */
    fun withScope(scope: String): AuthContext = copy(scopes = scopes + scope)

    /**
     * Creates a copy with additional policy context.
     */
    fun withPolicyContext(
        key: String,
        value: String,
    ): AuthContext = copy(policyContext = policyContext + (key to value))

    fun toHeaders(): Map<String, String> =
        buildMap {
            token?.let { put(AuthHeaders.AUTHORIZATION, "Bearer $it") }
            apiKey?.let { put(AuthHeaders.X_API_KEY, it) }
            traceparent?.let { put(AuthHeaders.TRACEPARENT, it) }
            tracestate?.let { put(AuthHeaders.TRACESTATE, it) }
            requestId?.let { put(AuthHeaders.X_REQUEST_ID, it) }
            correlationId?.let { put(AuthHeaders.X_CORRELATION_ID, it) }
        }

    companion object {
        /**
         * Empty context with no authentication.
         */
        val ANONYMOUS = AuthContext()

        /**
         * Extracts auth context from a map of headers.
         */
        @JvmStatic
        fun fromHeaders(headers: Map<String, String>): AuthContext {
            fun get(name: String) = RequestUtils.extractHeaderValue(headers, name)

            return AuthContext(
                token = get(AuthHeaders.AUTHORIZATION)?.removePrefix("Bearer ")?.trim(),
                apiKey = get(AuthHeaders.X_API_KEY),
                tenantId = null,
                userId = null,
                principalId = null,
                serviceId = null,
                traceparent = get(AuthHeaders.TRACEPARENT),
                tracestate = get(AuthHeaders.TRACESTATE),
                requestId = get(AuthHeaders.X_REQUEST_ID),
                correlationId = get(AuthHeaders.X_CORRELATION_ID),
                policyContext = emptyMap(),
                scopes = emptySet(),
            )
        }
    }
}

/**
 * Extension function to extract AuthContext from a BinaryRequest.
 */
fun BinaryRequest.extractAuthContext(): AuthContext = AuthContext.fromHeaders(headers)

/**
 * Extension function to add auth context headers to a BinaryRequest.
 */
fun BinaryRequest.withAuthContext(authContext: AuthContext): BinaryRequest = withHeaders(authContext.toHeaders())

/**
 * Extension function to convert SessionContext to AuthContext for header propagation.
 *
 * This extracts authentication and tenant context from a SessionContext,
 * enabling consistent header propagation in transport clients.
 *
 * Note: Some fields (correlationId, policyContext, scopes) are not present
 * in SessionContext and will be empty in the resulting AuthContext.
 * Use [AuthContext.withPolicyContext] or [AuthContext.withScope] to add them.
 */
fun com.sphereon.di.session.SessionContext.toAuthContext(
    traceparent: String? = null,
    tracestate: String? = null,
    correlationId: String? = null,
    policyContext: Map<String, String> = emptyMap(),
    serviceId: String? = null,
): AuthContext =
    AuthContext(
        token = context.secureDetails?.jwt,
        tenantId = context.tenant.tenantId.takeIf { !it.isAnonymousIdentityValue() },
        principalId = context.principal?.toString()?.takeIf { !it.isAnonymousIdentityValue() },
        userId = null, // User ID not directly available in SessionContext
        serviceId = serviceId,
        traceparent = traceparent,
        tracestate = tracestate,
        correlationId = correlationId,
        policyContext = policyContext,
    )

private fun String.isAnonymousIdentityValue(): Boolean = this == IdentityConstants.ANONYMOUS_ID || this.equals("anonymous", ignoreCase = true)
