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

package com.sphereon.openid.oid4vci.holder

import com.sphereon.core.api.service.ServiceCommand
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ============================================================================
// InitiateIaeCommand arguments
// ============================================================================

/**
 * Arguments for submitting the initial IAE request to the AS IAE endpoint.
 *
 * OID4VCI 1.1 Section 6.1.1: Initial Request
 *
 * @property iaeEndpoint URL of the AS IAE endpoint (POST /iae).
 * @property clientId The client_id of the wallet/holder.
 * @property redirectUri The redirect_uri registered for the client.
 * @property interactionTypesSupported List of interaction type URNs the wallet supports.
 *   Sent as a comma-separated string per the spec.
 * @property authorizationDetails Optional authorization_details JSON array (RFC 9396).
 * @property scope Optional OAuth 2.0 scope string.
 * @property codeChallenge PKCE code_challenge, required when redirect_to_web is supported.
 * @property codeChallengeMethod PKCE code_challenge_method (e.g. "S256").
 */
data class InitiateIaeArgs(
    val iaeEndpoint: String,
    val clientId: String,
    val redirectUri: String,
    val interactionTypesSupported: List<String>,
    val authorizationDetails: List<JsonElement>? = null,
    val scope: String? = null,
    val codeChallenge: String? = null,
    val codeChallengeMethod: String? = null,
)

// ============================================================================
// FollowUpIaeCommand arguments
// ============================================================================

/**
 * Arguments for submitting an IAE follow-up request to the AS IAE endpoint.
 *
 * OID4VCI 1.1 Section 6.3: Follow-up Request
 *
 * @property iaeEndpoint URL of the AS IAE endpoint (POST /iae).
 * @property authSession The auth_session token returned by the most recent IAE response.
 * @property openid4vpResponse VP Token response object, present when responding to an
 *   openid4vp_presentation challenge.
 * @property codeVerifier PKCE code_verifier, if applicable.
 */
data class FollowUpIaeArgs(
    val iaeEndpoint: String,
    val authSession: String,
    val openid4vpResponse: JsonObject? = null,
    val codeVerifier: String? = null,
)

// ============================================================================
// IaeHolderResult — sealed result type for the client-side IAE follow-up
// ============================================================================

/**
 * Discriminated result type for client-side IAE responses.
 *
 * OID4VCI 1.1 Section 6: Interactive Authorization Endpoint
 */
@Serializable
sealed class IaeHolderResult {
    /** Server requires another interaction round-trip. */
    @Serializable
    data class InteractionRequired(
        val type: String,
        @SerialName("auth_session") val authSession: String,
        @SerialName("openid4vp_request") val openid4vpRequest: JsonObject? = null,
        @SerialName("request_uri") val requestUri: String? = null,
        @SerialName("expires_in") val expiresIn: Int? = null,
    ) : IaeHolderResult()

    /** All interactions satisfied; client must exchange the code at the token endpoint. */
    @Serializable
    data class AuthorizationCode(
        val code: String,
    ) : IaeHolderResult()

    /** Flow failed. */
    @Serializable
    data class Error(
        val error: String,
        @SerialName("error_description") val errorDescription: String? = null,
    ) : IaeHolderResult()
}

// ============================================================================
// FollowUpIaeCommand interface
// ============================================================================

/**
 * Submits an IAE follow-up request to the AS IAE endpoint.
 *
 * OID4VCI 1.1 Section 6.3 — Follow-up Request
 *
 * POSTs form-encoded parameters (auth_session, openid4vp_response, code_verifier) to
 * the IAE endpoint and maps the JSON response to [IaeHolderResult].
 *
 * Command ID: `oid4vci.holder.iae.followup`
 */
interface FollowUpIaeCommand : ServiceCommand<FollowUpIaeArgs, IaeHolderResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.holder.iae.followup"
    }
}

// ============================================================================
// InitiateIaeCommand interface
// ============================================================================

/**
 * Submits the initial IAE request to the AS IAE endpoint.
 *
 * OID4VCI 1.1 Section 6.1.1 — Initial Request
 *
 * POSTs form-encoded parameters (response_type=code, client_id, redirect_uri,
 * interaction_types_supported, and optional fields) to the IAE endpoint and
 * maps the JSON response to [IaeHolderResult].
 *
 * Command ID: `oid4vci.holder.iae.initiate`
 */
interface InitiateIaeCommand : ServiceCommand<InitiateIaeArgs, IaeHolderResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.holder.iae.initiate"
    }
}
