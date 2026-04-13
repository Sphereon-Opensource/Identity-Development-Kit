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

package com.sphereon.oauth2.server.authorization.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ============================================================================
// IAE Session (server-side state)
// ============================================================================

/**
 * Status of an IAE session as it progresses through the multi-round-trip flow.
 *
 * OID4VCI 1.1 Section 6 — Interactive Authorization Endpoint
 */
@Serializable
enum class IaeSessionStatus {
    /** Session created, no interaction round-trip started yet. */
    INITIAL,

    /** Server has issued an interaction challenge; waiting for client response. */
    INTERACTION_REQUIRED,

    /** Client has submitted an interaction response; server is verifying. */
    INTERACTION_SUBMITTED,

    /** All required interactions passed; authorization code issued. */
    AUTHORIZED,

    /** Flow failed due to a client or server error. */
    FAILED,

    /** Session exceeded its lifetime before completion. */
    EXPIRED,
}

/**
 * Server-side state that tracks an IAE multi-round-trip flow.
 *
 * OID4VCI 1.1 Section 6 — Interactive Authorization Endpoint
 *
 * @property sessionId Stable, internal session identifier. Never exposed to the client.
 * @property authSession Short-lived token returned to the client in each response.
 *   Rotates on every server response to prevent replay. See [previousAuthSessions].
 * @property clientId OAuth2 client_id from the initial authorization request.
 * @property redirectUri redirect_uri from the initial authorization request.
 * @property responseType Value from the initial authorization request (always "code" for IAE).
 * @property scope Requested scope, if present.
 * @property authorizationDetails RFC 9396 authorization_details, if present.
 * @property codeChallenge PKCE code_challenge, preserved across all rounds.
 * @property codeChallengeMethod PKCE code_challenge_method, preserved across all rounds.
 * @property interactionTypesSupported Interaction type URNs from the initial request.
 * @property status Current phase of the IAE flow.
 * @property currentInteractionType URN of the interaction type currently being challenged.
 * @property vpNonce Nonce bound to [authSession] for VP presentation (Section 6.3.2).
 * @property vpSessionId Correlation ID of the OID4VP verifier session created for this IAE round-trip.
 *   Used in the follow-up handler to retrieve the original [AuthorizationRequest] and DCQL query
 *   from the verifier's [com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore].
 * @property vpVerificationResult Result of the VP verification, kept for audit.
 * @property authorizationCode Issued after a successful interaction (status = AUTHORIZED).
 * @property createdAt Creation timestamp (epoch seconds).
 * @property expiresAt Expiry timestamp (epoch seconds).
 * @property previousAuthSessions Prior auth_session values; used for replay detection.
 */
@Serializable
data class IaeSession(
    val sessionId: String,
    val authSession: String,
    val clientId: String,
    val redirectUri: String,
    val responseType: String,
    val scope: String? = null,
    val authorizationDetails: List<JsonElement>? = null,
    val codeChallenge: String? = null,
    val codeChallengeMethod: String? = null,
    val interactionTypesSupported: List<String>,
    val status: IaeSessionStatus,
    val currentInteractionType: String? = null,
    val vpNonce: String? = null,
    val vpSessionId: String? = null,
    val vpVerificationResult: JsonElement? = null,
    val authorizationCode: String? = null,
    val createdAt: Long,
    val expiresAt: Long,
    val previousAuthSessions: List<String> = emptyList(),
)

// ============================================================================
// Wire response models
// ============================================================================

/**
 * Wire format returned when the server requires further interaction.
 *
 * OID4VCI 1.1 Section 6.2: status = "require_interaction"
 *
 * @property status Always "require_interaction".
 * @property type Interaction type URN (see [IaeInteractionTypes]).
 * @property authSession Short-lived session token; must be included in the follow-up request.
 * @property openid4vpRequest Full OpenID4VP authorization request object, present when
 *   [type] is [IaeInteractionTypes.OPENID4VP_PRESENTATION].
 * @property requestUri URL to which the user should be redirected, present when
 *   [type] is [IaeInteractionTypes.REDIRECT_TO_WEB].
 * @property expiresIn Lifetime of [requestUri] in seconds, present for redirect_to_web.
 */
@Serializable
data class IaeInteractionRequiredResponse(
    val status: String = "require_interaction",
    val type: String,
    @SerialName("auth_session") val authSession: String,
    @SerialName("openid4vp_request") val openid4vpRequest: JsonObject? = null,
    @SerialName("request_uri") val requestUri: String? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
)

/**
 * Wire format returned when the IAE flow completes successfully.
 *
 * OID4VCI 1.1 Section 6.4: status = "ok"
 *
 * @property status Always "ok".
 * @property code OAuth2 authorization code; client exchanges this at the token endpoint.
 */
@Serializable
data class IaeAuthorizationCodeResponse(
    val status: String = "ok",
    val code: String,
)

/**
 * Wire format returned when the IAE flow encounters an error.
 *
 * OID4VCI 1.1 Section 6 / RFC 6749 Section 4.1.2.1
 *
 * @property error Machine-readable error code (see [IaeErrors]).
 * @property errorDescription Human-readable description, OPTIONAL.
 */
@Serializable
data class IaeErrorResponse(
    val error: String,
    @SerialName("error_description") val errorDescription: String? = null,
)

// ============================================================================
// Constants
// ============================================================================

/**
 * Error code constants for IAE responses.
 *
 * OID4VCI 1.1 Section 6.2.3 and RFC 6749 Section 4.1.2.1
 */
object IaeErrors {
    /** The initial request did not include any interaction_types_supported. Section 6.2.3. */
    const val MISSING_INTERACTION_TYPE = "missing_interaction_type"

    /** The request is malformed or missing required parameters. */
    const val INVALID_REQUEST = "invalid_request"

    /** The resource owner or authorization server denied the request. */
    const val ACCESS_DENIED = "access_denied"
}

/**
 * Interaction type URN constants.
 *
 * OID4VCI 1.1 Section 6.1
 */
object IaeInteractionTypes {
    /** The client must present a Verifiable Presentation via OpenID4VP. */
    const val OPENID4VP_PRESENTATION = "urn:openid:dcp:iae:openid4vp_presentation"

    /** The user must complete an interaction via a redirect to an external web page. */
    const val REDIRECT_TO_WEB = "urn:openid:dcp:iae:redirect_to_web"
}
