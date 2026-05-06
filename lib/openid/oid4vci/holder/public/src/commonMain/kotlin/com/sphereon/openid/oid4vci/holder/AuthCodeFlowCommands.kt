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

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import kotlinx.serialization.Serializable

// ============================================================================
// BuildAuthorizationRequestCommand
// ============================================================================

/**
 * Arguments for building an OID4VCI Authorization Code Flow authorization request URL.
 *
 * @property authorizationEndpoint The authorization endpoint URL of the authorization server.
 * @property clientId The OAuth 2.0 client identifier.
 * @property redirectUri The redirect URI to which the authorization server will redirect after authorization.
 * @property credentialConfigurationIds The credential configuration IDs being requested, used to build
 *   the authorization_details parameter (RFC 9396).
 * @property scope Optional OAuth 2.0 scope string.
 * @property issuerState Optional issuer state from the credential offer (OID4VCI Section 5.1.1).
 * @property usePar If true, use Pushed Authorization Requests (RFC 9126) instead of a direct redirect.
 * @property parEndpoint The PAR endpoint URL; required when [usePar] is true.
 */
data class BuildAuthorizationRequestArgs(
    val authorizationEndpoint: String,
    val clientId: String,
    val redirectUri: String,
    val credentialConfigurationIds: List<String>,
    val scope: String? = null,
    val issuerState: String? = null,
    val usePar: Boolean = false,
    val parEndpoint: String? = null,
    /** Per-configuration credential identifiers to include in authorization_details (configId -> identifiers). */
    val credentialIdentifiers: Map<String, List<String>>? = null,
    /** Locations (resource indicators) to include in each authorization_details entry. */
    val locations: List<String>? = null,
)

/**
 * Result of building an authorization request URL.
 *
 * @property authorizationUrl The URL to redirect the user to for authorization.
 * @property codeVerifier The PKCE code verifier that must be kept and supplied when exchanging the
 *   authorization code for tokens.
 * @property state The randomly generated state parameter included in the authorization request.
 */
@Serializable
data class AuthorizationRequestResult(
    val authorizationUrl: String,
    val codeVerifier: String,
    val state: String,
)

/**
 * Command for building an OID4VCI Authorization Code Flow authorization request URL.
 *
 * This command:
 * 1. Generates a PKCE code_verifier and code_challenge (S256).
 * 2. Generates a random state parameter.
 * 3. Builds authorization_details JSON for each credential configuration ID.
 * 4. Optionally uses PAR (Pushed Authorization Requests) when [BuildAuthorizationRequestArgs.usePar] is true.
 * 5. Returns the authorization URL, code_verifier, and state for use in the redirect and subsequent token exchange.
 *
 * Command ID: `oid4vci.holder.buildauthrequest`
 *
 * Reference: OID4VCI 1.0 Section 5 — Authorization Code Flow
 */
interface BuildAuthorizationRequestCommand : ServiceCommand<BuildAuthorizationRequestArgs, AuthorizationRequestResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.holder.buildauthrequest"
    }
}

// ============================================================================
// ExchangeAuthorizationCodeCommand
// ============================================================================

/**
 * Arguments for exchanging an authorization code for tokens at the token endpoint.
 *
 * @property tokenEndpoint The token endpoint URL of the authorization server.
 * @property code The authorization code received from the authorization server redirect.
 * @property codeVerifier The PKCE code verifier generated during the authorization request.
 * @property redirectUri The redirect URI that was used in the authorization request.
 * @property clientId Optional OAuth 2.0 client identifier; include for public clients.
 */
data class ExchangeAuthorizationCodeArgs(
    val tokenEndpoint: String,
    val code: String,
    val codeVerifier: String,
    val redirectUri: String,
    val clientId: String? = null,
)

/**
 * Command for exchanging an authorization code for tokens.
 *
 * POSTs form-encoded parameters to the token endpoint using the `authorization_code` grant type
 * and returns [TokenResponseWithContext] containing the access token and optional c_nonce.
 *
 * Command ID: `oid4vci.holder.exchangecode`
 *
 * Reference: OID4VCI 1.0 Section 8 — Token Request (Authorization Code Flow)
 */
interface ExchangeAuthorizationCodeCommand : ServiceCommand<ExchangeAuthorizationCodeArgs, TokenResponseWithContext, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.holder.exchangecode"
    }
}
