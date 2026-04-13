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

package com.sphereon.oauth2.server.resource.error

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.oauth2.server.resource.model.AuthenticationScheme

/**
 * Resource Server errors
 *
 * RFC 6750 Section 3 defines error codes for Bearer token authentication
 * RFC 9449 Section 7.1 defines error responses for DPoP
 */
sealed interface ResourceServerError : IdkErrorType {

    // ============================================================================
    // Authorization Header Errors
    // ============================================================================

    /**
     * Missing Authorization header
     *
     * HTTP 401 Unauthorized with WWW-Authenticate header
     */
    data class MissingAuthorizationHeader(
        val allowedSchemes: List<AuthenticationScheme>,
        override val code: String = "missing_authorization",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.rs.error.missing_authorization",
            defaultMessage = "Missing Authorization header"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("allowed_schemes" to allowedSchemes.map { it.value })
    ) : ResourceServerError

    /**
     * Invalid Authorization header format
     *
     * HTTP 400 Bad Request
     */
    data class MalformedAuthorizationHeader(
        val header: String,
        override val code: String = "malformed_authorization",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.rs.error.malformed_authorization",
            defaultMessage = "Malformed Authorization header: $header"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("header" to header)
    ) : ResourceServerError

    /**
     * Unsupported authentication scheme
     *
     * HTTP 401 Unauthorized with WWW-Authenticate header
     */
    data class UnsupportedAuthenticationScheme(
        val scheme: String,
        val allowedSchemes: List<AuthenticationScheme>,
        override val code: String = "unsupported_scheme",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.rs.error.unsupported_scheme",
            defaultMessage = "Unsupported authentication scheme: $scheme"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf(
            "scheme" to scheme,
            "allowed_schemes" to allowedSchemes.map { it.value }
        )
    ) : ResourceServerError

    // ============================================================================
    // Token Validation Errors (RFC 6750 Section 3.1)
    // ============================================================================

    /**
     * Invalid or expired token
     *
     * HTTP 401 Unauthorized
     * WWW-Authenticate: Bearer error="invalid_token"
     */
    data class InvalidToken(
        val reason: String,
        override val code: String = "invalid_token",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.rs.error.invalid_token",
            defaultMessage = "Invalid token: $reason"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("reason" to reason)
    ) : ResourceServerError

    /**
     * Token audience mismatch
     *
     * HTTP 403 Forbidden
     * WWW-Authenticate: Bearer error="insufficient_scope"
     */
    data class AudienceMismatch(
        val expected: String,
        val actual: List<String>,
        override val code: String = "audience_mismatch",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.rs.error.audience_mismatch",
            defaultMessage = "Token audience mismatch: expected '$expected', got '${actual.joinToString(", ")}'"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf(
            "expected" to expected,
            "actual" to actual
        )
    ) : ResourceServerError

    /**
     * Token has insufficient scope
     *
     * HTTP 403 Forbidden
     * WWW-Authenticate: Bearer error="insufficient_scope", scope="required_scope"
     */
    data class InsufficientScope(
        val required: String,
        val actual: String?,
        override val code: String = "insufficient_scope",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.rs.error.insufficient_scope",
            defaultMessage = "Insufficient scope: required '$required', got '${actual ?: "none"}'"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf(
            "required" to required,
            "actual" to actual
        )
    ) : ResourceServerError

    // ============================================================================
    // DPoP Errors (RFC 9449 Section 7.1)
    // ============================================================================

    /**
     * Missing or invalid DPoP proof
     *
     * HTTP 401 Unauthorized
     * WWW-Authenticate: DPoP error="invalid_dpop_proof"
     */
    data class InvalidDpopProof(
        val reason: String,
        override val code: String = "invalid_dpop_proof",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.rs.error.invalid_dpop_proof",
            defaultMessage = "Invalid DPoP proof: $reason"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("reason" to reason)
    ) : ResourceServerError

    /**
     * DPoP binding mismatch
     *
     * HTTP 401 Unauthorized
     * The access token's cnf.jkt doesn't match the DPoP proof's JWK
     */
    data class DpopBindingMismatch(
        val reason: String,
        override val code: String = "dpop_binding_mismatch",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.rs.error.dpop_binding_mismatch",
            defaultMessage = "DPoP binding mismatch: $reason"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("reason" to reason)
    ) : ResourceServerError

    // ============================================================================
    // Token Introspection Errors
    // ============================================================================

    /**
     * Token introspection failed
     *
     * HTTP 401 Unauthorized
     * Could not verify token with authorization server
     */
    data class IntrospectionFailed(
        val reason: String,
        override val code: String = "introspection_failed",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.rs.error.introspection_failed",
            defaultMessage = "Token introspection failed: $reason"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("reason" to reason)
    ) : ResourceServerError

    /**
     * No valid authorization server found
     *
     * HTTP 401 Unauthorized
     * Could not verify token with any configured authorization server
     */
    data class NoValidAuthorizationServer(
        override val code: String = "no_valid_as",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.rs.error.no_valid_as",
            defaultMessage = "Could not verify token with any authorization server"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : ResourceServerError

    // ============================================================================
    // Server Errors
    // ============================================================================

    /**
     * Server error during token verification
     *
     * HTTP 500 Internal Server Error
     */
    data class ServerError(
        val details: String,
        override val code: String = "server_error",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.rs.error.server_error",
            defaultMessage = "Server error: $details"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("details" to details)
    ) : ResourceServerError
}
