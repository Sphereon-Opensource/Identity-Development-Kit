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

package com.sphereon.oauth2.server.authorization.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.server.authorization.model.IaeAuthorizationCodeResponse
import com.sphereon.oauth2.server.authorization.model.IaeErrorResponse
import com.sphereon.oauth2.server.authorization.model.IaeInteractionRequiredResponse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ============================================================================
// IaeResult — sealed return type shared by both IAE commands
// ============================================================================

/**
 * Discriminated result type for IAE command responses.
 *
 * OID4VCI 1.1 Section 6:
 * - [InteractionRequired] — server requires a further interaction round-trip.
 * - [AuthorizationCode] — flow completed; client must exchange the code at the token endpoint.
 * - [Error] — flow failed; client should surface the error to the user.
 */
sealed class IaeResult {
    /** Server issued an interaction challenge. Client must submit a follow-up request. */
    data class InteractionRequired(
        val response: IaeInteractionRequiredResponse,
    ) : IaeResult()

    /** All interactions satisfied; authorization code issued. */
    data class AuthorizationCode(
        val response: IaeAuthorizationCodeResponse,
    ) : IaeResult()

    /** Flow failed. */
    data class Error(
        val response: IaeErrorResponse,
    ) : IaeResult()
}

// ============================================================================
// HandleIaeInitialRequestCommand
// ============================================================================

/**
 * Arguments for the IAE initial authorization request.
 *
 * OID4VCI 1.1 Section 6.2: Initial Request
 *
 * @property clientId OAuth2 client_id.
 * @property responseType Must be "code".
 * @property redirectUri Client redirect URI.
 * @property interactionTypesSupported Ordered list of interaction type URNs the client supports.
 *   See [com.sphereon.oauth2.server.authorization.model.IaeInteractionTypes].
 * @property authorizationDetails RFC 9396 authorization_details, if present.
 * @property scope Requested OAuth2 scope, if present.
 * @property codeChallenge PKCE code_challenge, if present.
 * @property codeChallengeMethod PKCE code_challenge_method, if present.
 * @property issuerState Credential offer issuer_state, forwarded from the OID4VCI offer if present.
 * @property request Optional signed request object (JAR, RFC 9101). When present, authorization
 *   parameters are extracted from the JWT and override any individually supplied form params.
 */
data class HandleIaeInitialRequestArgs(
    val clientId: String,
    val responseType: String,
    val redirectUri: String,
    val interactionTypesSupported: List<String>,
    val authorizationDetails: List<JsonElement>? = null,
    val scope: String? = null,
    val codeChallenge: String? = null,
    val codeChallengeMethod: String? = null,
    val issuerState: String? = null,
    val request: String? = null,
)

/**
 * Handle an IAE initial authorization request.
 *
 * OID4VCI 1.1 Section 6.2 — Initial Request
 *
 * Validates the incoming request, creates an [IaeSession], and responds with
 * the first interaction challenge (or immediately with a code if no interaction
 * is needed).
 *
 * Command ID: `oauth2.iae.initial`
 */
interface HandleIaeInitialRequestCommand : ServiceCommand<HandleIaeInitialRequestArgs, IaeResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.iae.initial"
    }
}

// ============================================================================
// HandleIaeFollowUpCommand
// ============================================================================

/**
 * Arguments for an IAE follow-up request.
 *
 * OID4VCI 1.1 Section 6.3: Follow-up Request
 *
 * @property authSession The auth_session token from the most recent server response.
 *   Rotates on every response; must not be reused.
 * @property openid4vpResponse The OpenID4VP VP Token response object, present when the
 *   client is responding to an [IaeInteractionTypes.OPENID4VP_PRESENTATION] challenge.
 * @property codeVerifier PKCE code_verifier, supplied when the AS verifies PKCE inline.
 *   May alternatively be deferred to the token endpoint.
 */
data class HandleIaeFollowUpArgs(
    val authSession: String,
    val openid4vpResponse: JsonObject? = null,
    val codeVerifier: String? = null,
)

/**
 * Handle an IAE follow-up request.
 *
 * OID4VCI 1.1 Section 6.3 — Follow-up Request
 *
 * Looks up the session by [HandleIaeFollowUpArgs.authSession], verifies the
 * submitted interaction response (VP presentation or web-auth callback), and
 * either issues an authorization code or requests another interaction round-trip.
 *
 * Command ID: `oauth2.iae.followup`
 */
interface HandleIaeFollowUpCommand : ServiceCommand<HandleIaeFollowUpArgs, IaeResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.iae.followup"
    }
}
