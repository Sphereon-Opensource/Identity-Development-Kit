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

package com.sphereon.oauth2.server.authorization.error

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType

/**
 * Authorization Server errors
 *
 * RFC 6749 defines standard error codes for OAuth 2.0
 */
sealed interface AuthorizationServerError : IdkErrorType {

    // ============================================================================
    // Authorization Endpoint Errors (RFC 6749 Section 4.1.2.1)
    // ============================================================================

    /**
     * The request is missing a required parameter, includes an invalid parameter value,
     * includes a parameter more than once, or is otherwise malformed.
     */
    data class InvalidRequest(
        val details: String,
        override val code: String = "invalid_request",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.invalid_request",
            defaultMessage = "Invalid request: $details"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : AuthorizationServerError

    /**
     * The client is not authorized to request an authorization code using this method.
     */
    data class UnauthorizedClient(
        val clientId: String,
        override val code: String = "unauthorized_client",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.unauthorized_client",
            defaultMessage = "Client '$clientId' is not authorized"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("client_id" to clientId)
    ) : AuthorizationServerError

    /**
     * The resource owner or authorization server denied the request.
     */
    data class AccessDenied(
        val reason: String,
        override val code: String = "access_denied",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.access_denied",
            defaultMessage = "Access denied: $reason"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : AuthorizationServerError

    /**
     * The authorization server does not support obtaining an authorization code using this method.
     */
    data class UnsupportedResponseType(
        val responseType: String,
        override val code: String = "unsupported_response_type",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.unsupported_response_type",
            defaultMessage = "Unsupported response type: $responseType"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("response_type" to responseType)
    ) : AuthorizationServerError

    /**
     * The requested scope is invalid, unknown, or malformed.
     */
    data class InvalidScope(
        val scope: String,
        val allowedScopes: List<String>? = null,
        override val code: String = "invalid_scope",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.invalid_scope",
            defaultMessage = "Invalid scope: $scope"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf(
            "scope" to scope,
            "allowed_scopes" to allowedScopes
        )
    ) : AuthorizationServerError

    /**
     * The authorization server encountered an unexpected condition that prevented it
     * from fulfilling the request.
     */
    data class ServerError(
        val details: String,
        override val exception: Throwable? = null,
        override val code: String = "server_error",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.server_error",
            defaultMessage = "Server error: $details"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : AuthorizationServerError

    /**
     * The authorization server is currently unable to handle the request due to
     * a temporary overloading or maintenance of the server.
     */
    data class TemporarilyUnavailable(
        val retryAfter: Int? = null,
        override val code: String = "temporarily_unavailable",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.temporarily_unavailable",
            defaultMessage = "Server temporarily unavailable"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.WARNING,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("retry_after" to retryAfter)
    ) : AuthorizationServerError

    // ============================================================================
    // Token Endpoint Errors (RFC 6749 Section 5.2)
    // ============================================================================

    /**
     * Client authentication failed (e.g., unknown client, no client authentication included,
     * or unsupported authentication method).
     */
    data class InvalidClient(
        val details: String,
        override val code: String = "invalid_client",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.invalid_client",
            defaultMessage = "Invalid client: $details"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : AuthorizationServerError

    /**
     * The provided authorization grant (e.g., authorization code, resource owner credentials)
     * or refresh token is invalid, expired, revoked, does not match the redirection URI used
     * in the authorization request, or was issued to another client.
     */
    data class InvalidGrant(
        val details: String,
        override val code: String = "invalid_grant",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.invalid_grant",
            defaultMessage = "Invalid grant: $details"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : AuthorizationServerError

    /**
     * The authorization grant type is not supported by the authorization server.
     */
    data class UnsupportedGrantType(
        val grantType: String,
        override val code: String = "unsupported_grant_type",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.unsupported_grant_type",
            defaultMessage = "Unsupported grant type: $grantType"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("grant_type" to grantType)
    ) : AuthorizationServerError

    // ============================================================================
    // Token Exchange Errors (RFC 8693)
    // ============================================================================

    /**
     * The requested resource or audience is invalid, unknown, or malformed.
     * RFC 8693 Section 2.2.1
     */
    data class InvalidTarget(
        val resource: String? = null,
        val audience: String? = null,
        val reason: String,
        override val code: String = "invalid_target",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.invalid_target",
            defaultMessage = "Invalid target: $reason"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = buildMap {
            resource?.let { put("resource", it) }
            audience?.let { put("audience", it) }
        }
    ) : AuthorizationServerError

    // ============================================================================
    // DPoP Errors (RFC 9449)
    // ============================================================================

    /**
     * Invalid or missing DPoP proof
     */
    data class InvalidDpopProof(
        val details: String,
        override val code: String = "invalid_dpop_proof",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.invalid_dpop_proof",
            defaultMessage = "Invalid DPoP proof: $details"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : AuthorizationServerError

    /**
     * DPoP nonce required
     */
    data class UseDpopNonce(
        val dpopNonce: String,
        override val code: String = "use_dpop_nonce",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.use_dpop_nonce",
            defaultMessage = "DPoP nonce required"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.WARNING,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("dpop_nonce" to dpopNonce)
    ) : AuthorizationServerError

    // ============================================================================
    // Client Attestation Errors (draft-ietf-oauth-attestation-based-client-auth)
    // ============================================================================

    /**
     * The client attestation or attestation PoP is invalid.
     * draft-ietf-oauth-attestation-based-client-auth Section 5
     */
    data class InvalidClientAttestation(
        val details: String,
        override val code: String = "invalid_client_attestation",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.invalid_client_attestation",
            defaultMessage = "Invalid client attestation: $details"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : AuthorizationServerError

    /**
     * The AS requires the client to use a fresh attestation with a challenge nonce.
     * Returns the challenge in meta for the HTTP layer to emit as a response header.
     */
    data class UseAttestationChallenge(
        val challenge: String,
        override val code: String = "use_attestation_challenge",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.use_attestation_challenge",
            defaultMessage = "Attestation challenge required"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.WARNING,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("attestation_challenge" to challenge)
    ) : AuthorizationServerError

    /**
     * The client attestation is stale and must be refreshed.
     */
    data class UseFreshAttestation(
        val details: String,
        override val code: String = "use_fresh_attestation",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.use_fresh_attestation",
            defaultMessage = "Fresh attestation required: $details"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.WARNING,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : AuthorizationServerError

    // ============================================================================
    // Storage/Infrastructure Errors
    // ============================================================================

    /**
     * Storage operation failed
     */
    data class StorageError(
        val operation: String,
        val details: String,
        override val exception: Throwable? = null,
        override val code: String = "storage_error",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.storage_error",
            defaultMessage = "Storage error during $operation: $details"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("operation" to operation)
    ) : AuthorizationServerError

    /**
     * Session not found or expired
     */
    data class SessionNotFound(
        val sessionId: String,
        override val code: String = "session_not_found",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.session_not_found",
            defaultMessage = "Session not found: $sessionId"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("session_id" to sessionId)
    ) : AuthorizationServerError

    /**
     * Client not found in registry
     */
    data class ClientNotFound(
        val clientId: String,
        override val code: String = "client_not_found",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "oauth2.as.error.client_not_found",
            defaultMessage = "Client not found: $clientId"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("client_id" to clientId)
    ) : AuthorizationServerError
}
