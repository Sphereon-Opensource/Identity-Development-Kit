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

package com.sphereon.oauth2.server.authorization.error

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.error.Retryability
import kotlin.time.Duration.Companion.seconds

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
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.invalid_request",
                defaultMessage = "Invalid request: $details",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : AuthorizationServerError

    /**
     * The client is not authorized to request an authorization code using this method.
     */
    data class UnauthorizedClient(
        val clientId: String,
        override val code: String = "unauthorized_client",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.unauthorized_client",
                defaultMessage = "Client '$clientId' is not authorized",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("client_id" to clientId),
    ) : AuthorizationServerError

    /**
     * The resource owner or authorization server denied the request.
     */
    data class AccessDenied(
        val reason: String,
        override val code: String = "access_denied",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.access_denied",
                defaultMessage = "Access denied: $reason",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : AuthorizationServerError

    /**
     * The authorization server does not support obtaining an authorization code using this method.
     */
    data class UnsupportedResponseType(
        val responseType: String,
        override val code: String = "unsupported_response_type",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.unsupported_response_type",
                defaultMessage = "Unsupported response type: $responseType",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("response_type" to responseType),
    ) : AuthorizationServerError

    /**
     * The requested scope is invalid, unknown, or malformed.
     */
    data class InvalidScope(
        val scope: String,
        val allowedScopes: List<String>? = null,
        override val code: String = "invalid_scope",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.invalid_scope",
                defaultMessage = "Invalid scope: $scope",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> =
            mapOf(
                "scope" to scope,
                "allowed_scopes" to allowedScopes,
            ),
    ) : AuthorizationServerError

    /**
     * The authorization details request is not authorized for this client.
     *
     * RFC 9396 §6: the AS MUST validate that the requested authorization_details entries are
     * within the scope authorized for the client. Emitted when a client requests a
     * `credential_configuration_id` not in its registered allow-list.
     */
    data class InvalidAuthorizationDetails(
        val details: String,
        val credentialConfigurationId: String? = null,
        val clientId: String? = null,
        override val code: String = "invalid_authorization_details",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.invalid_authorization_details",
                defaultMessage = "Invalid authorization_details: $details",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> =
            buildMap {
                credentialConfigurationId?.let { put("credential_configuration_id", it) }
                clientId?.let { put("client_id", it) }
            },
    ) : AuthorizationServerError

    /**
     * The authorization server does not support the use of the `request` parameter
     * (OpenID Connect Core 1.0 §6 / JAR). Returned post-redirect when the client embeds a
     * Request Object directly in the authorization request, allowing the AS to surface the
     * rejection through the validated redirect URI per OIDC §3.1.2.6.
     */
    data class RequestNotSupported(
        override val code: String = "request_not_supported",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.request_not_supported",
                defaultMessage = "request parameter is not supported by this authorization server",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : AuthorizationServerError

    /**
     * The authorization server does not support the use of the `request_uri` parameter
     * (OpenID Connect Core 1.0 §6 / JAR). PAR-issued URNs (`urn:ietf:params:oauth:request_uri:`)
     * are exempt — those flow through the PAR retrieval path. Any other `request_uri` value is
     * rejected post-redirect so the error is delivered to the validated redirect URI per
     * OIDC §3.1.2.6.
     */
    data class RequestUriNotSupported(
        override val code: String = "request_uri_not_supported",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.request_uri_not_supported",
                defaultMessage = "request_uri parameter is not supported by this authorization server",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : AuthorizationServerError

    /**
     * RFC 9101 §5: the JAR `request` JWT was rejected (bad signature, missing/incorrect `typ`,
     * mismatched `iss`/`aud`, expired, unsigned, or signed with an algorithm the AS does not
     * accept). Delivered post-redirect when the redirect URI has been validated; otherwise
     * surfaced as a 400 by the HTTP adapter.
     */
    data class InvalidRequestObject(
        val details: String,
        override val code: String = "invalid_request_object",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.invalid_request_object",
                defaultMessage = "Invalid request object: $details",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : AuthorizationServerError

    /**
     * RFC 9101 §5: the `request_uri` value was rejected (not pre-registered when registration is
     * required, or the URI fetch failed). Distinct from [RequestUriNotSupported], which fires when
     * the AS refuses to accept any `request_uri` at all.
     */
    data class InvalidRequestUri(
        val details: String,
        override val code: String = "invalid_request_uri",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.invalid_request_uri",
                defaultMessage = "Invalid request_uri: $details",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : AuthorizationServerError

    /**
     * The authorization server encountered an unexpected condition that prevented it
     * from fulfilling the request.
     */
    data class ServerError(
        val details: String,
        override val exception: Throwable? = null,
        override val code: String = "server_error",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.server_error",
                defaultMessage = "Server error: $details",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : AuthorizationServerError {
        override val retryability: Retryability get() = Retryability.TRANSIENT
    }

    /**
     * The authorization server is currently unable to handle the request due to
     * a temporary overloading or maintenance of the server.
     */
    data class TemporarilyUnavailable(
        val retryAfterSeconds: Int? = null,
        override val code: String = "temporarily_unavailable",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.temporarily_unavailable",
                defaultMessage = "Server temporarily unavailable",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.WARNING,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("retry_after" to retryAfterSeconds),
    ) : AuthorizationServerError {
        override val retryability: Retryability get() = Retryability.TRANSIENT
        override val retryAfter: kotlin.time.Duration? get() = retryAfterSeconds?.seconds
    }

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
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.invalid_client",
                defaultMessage = "Invalid client: $details",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : AuthorizationServerError

    /**
     * The provided authorization grant (e.g., authorization code, resource owner credentials)
     * or refresh token is invalid, expired, revoked, does not match the redirection URI used
     * in the authorization request, or was issued to another client.
     */
    data class InvalidGrant(
        val details: String,
        override val code: String = "invalid_grant",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.invalid_grant",
                defaultMessage = "Invalid grant: $details",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : AuthorizationServerError

    /**
     * RFC 8628 §3.5 `authorization_pending`: the user has not yet completed the verification
     * step for the device-code grant. The device SHOULD continue polling at the cadence the AS
     * established at issuance, optionally bumped by a previous `slow_down`.
     */
    data class AuthorizationPending(
        override val code: String = "authorization_pending",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.authorization_pending",
                defaultMessage = "Authorization pending: end user has not yet completed verification",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.INFO,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : AuthorizationServerError

    /**
     * RFC 8628 §3.5 `slow_down`: the device polled before the per-record `interval` elapsed. The
     * AS bumps the effective interval (the spec recommends ~5s on top of the baseline) so the
     * device backs off. The bumped interval is communicated through the response payload by the
     * HTTP layer; this error variant carries no extra meta.
     */
    data class SlowDown(
        override val code: String = "slow_down",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.slow_down",
                defaultMessage = "Slow down: device is polling too frequently",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.INFO,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : AuthorizationServerError

    /**
     * RFC 8628 §3.5 `expired_token`: the device-code record aged past `expires_in` without the
     * user completing verification. The device MUST stop polling and re-initiate at
     * `/device_authorization` if it still wants to authorise.
     */
    data class ExpiredToken(
        override val code: String = "expired_token",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.expired_token",
                defaultMessage = "Expired token: device code has expired",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : AuthorizationServerError

    /**
     * The authorization grant type is not supported by the authorization server.
     */
    data class UnsupportedGrantType(
        val grantType: String,
        override val code: String = "unsupported_grant_type",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.unsupported_grant_type",
                defaultMessage = "Unsupported grant type: $grantType",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("grant_type" to grantType),
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
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.invalid_target",
                defaultMessage = "Invalid target: $reason",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> =
            buildMap {
                resource?.let { put("resource", it) }
                audience?.let { put("audience", it) }
            },
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
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.invalid_dpop_proof",
                defaultMessage = "Invalid DPoP proof: $details",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : AuthorizationServerError

    /**
     * DPoP nonce required
     */
    data class UseDpopNonce(
        val dpopNonce: String,
        override val code: String = "use_dpop_nonce",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.use_dpop_nonce",
                defaultMessage = "DPoP nonce required",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.WARNING,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("dpop_nonce" to dpopNonce),
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
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.invalid_client_attestation",
                defaultMessage = "Invalid client attestation: $details",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : AuthorizationServerError

    /**
     * The AS requires the client to use a fresh attestation with a challenge nonce.
     * Returns the challenge in meta for the HTTP layer to emit as a response header.
     */
    data class UseAttestationChallenge(
        val challenge: String,
        override val code: String = "use_attestation_challenge",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.use_attestation_challenge",
                defaultMessage = "Attestation challenge required",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.WARNING,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("attestation_challenge" to challenge),
    ) : AuthorizationServerError

    /**
     * The client attestation is stale and must be refreshed.
     */
    data class UseFreshAttestation(
        val details: String,
        override val code: String = "use_fresh_attestation",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.use_fresh_attestation",
                defaultMessage = "Fresh attestation required: $details",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.WARNING,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
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
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.storage_error",
                defaultMessage = "Storage error during $operation: $details",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("operation" to operation),
    ) : AuthorizationServerError

    /**
     * Session not found or expired
     */
    data class SessionNotFound(
        val sessionId: String,
        override val code: String = "session_not_found",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.session_not_found",
                defaultMessage = "Session not found: $sessionId",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("session_id" to sessionId),
    ) : AuthorizationServerError

    /**
     * RFC 9470 §3 — `insufficient_user_authentication`. The user IS authenticated, but
     * the achieved authentication does not satisfy the request's `acr_values` and/or
     * `max_age` parameters. Carries the [acrValues] and [maxAge] the AS expects on the
     * re-authentication request, so a downstream handler (HTTP layer or resource server)
     * can surface them via `WWW-Authenticate: insufficient_user_authentication` (RFC
     * 9470 §3) or as an `error_description` query param on an authorize-error redirect.
     *
     * The two reasons MUST stay distinguishable in [meta] so a client adapter can react
     * differently — `STALE_AUTH` typically means "force a fresh login regardless of
     * factor", while `INSUFFICIENT_ACR` typically means "step up to a higher AAL".
     */
    data class UnmetAuthRequirements(
        val requiredAal: String,
        val currentAal: String,
        val acrValues: String?,
        val maxAge: Long?,
        val reason: String,
        override val code: String = "insufficient_user_authentication",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.insufficient_user_authentication",
                defaultMessage =
                    "Insufficient user authentication: required AAL=$requiredAal, current AAL=$currentAal" +
                        (acrValues?.let { " (acr_values='$it')" }.orEmpty()) +
                        (maxAge?.let { " (max_age=${it}s)" }.orEmpty()),
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> =
            mapOf(
                "required_aal" to requiredAal,
                "current_aal" to currentAal,
                "acr_values" to acrValues,
                "max_age" to maxAge,
                "reason" to reason,
            ),
    ) : AuthorizationServerError

    /**
     * The user IS authenticated AND the request itself is well-formed, but at least
     * one [com.sphereon.oauth2.server.authorization.requiredaction.RequiredActionEvaluator]
     * returned an obligation the user has not yet satisfied (mandatory MFA enrollment,
     * password rotation due, new ToS version unaccepted, ...).
     *
     * Wire shape: surfaced as `interaction_required` per OIDC Core §3.1.2.6 — the client
     * MAY retry the authorize request without `prompt=none` to give the user a chance to
     * complete the missing step. The matching IDV graph kickoff is the AS orchestrator's
     * job (out of scope for this iteration; the gate refuses the mint and the orchestrator
     * routes off the meta).
     *
     * [actionIds] is the canonical machine list for clients to dispatch on; [actionLabels]
     * carries the human-readable display strings the orchestrator can use as fallback
     * UI copy when no per-action graph is wired.
     */
    data class RequiredActionsPending(
        val actionIds: List<String>,
        val actionLabels: List<String>,
        val actionMetadata: List<Map<String, String>> = emptyList(),
        override val code: String = "interaction_required",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.required_actions_pending",
                defaultMessage = "Required actions pending: ${actionIds.joinToString(", ")}",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> =
            mapOf(
                "required_action_ids" to actionIds,
                "required_action_labels" to actionLabels,
                "required_action_metadata" to actionMetadata,
            ),
    ) : AuthorizationServerError

    /**
     * Client not found in registry
     */
    data class ClientNotFound(
        val clientId: String,
        override val code: String = "client_not_found",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.as.error.client_not_found",
                defaultMessage = "Client not found: $clientId",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("client_id" to clientId),
    ) : AuthorizationServerError
}
