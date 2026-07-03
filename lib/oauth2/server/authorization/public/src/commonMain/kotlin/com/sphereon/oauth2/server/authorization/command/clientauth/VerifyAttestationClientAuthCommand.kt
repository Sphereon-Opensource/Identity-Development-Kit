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

package com.sphereon.oauth2.server.authorization.command.clientauth

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.server.authorization.command.ClientAuthenticationEndpoint
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthentication

/**
 * Arguments for verifying attestation-based client authentication
 * (draft-ietf-oauth-attestation-based-client-auth-07).
 *
 * @property clientId The `client_id` presented on the request, possibly empty when the request
 *   identifies the client implicitly through the attestation `sub` claim.
 * @property attestationJwt Compact-serialized client attestation JWT (`OAuth-Client-Attestation`
 *   header value).
 * @property popJwt Compact-serialized PoP JWT signed by the client instance key
 *   (`OAuth-Client-Attestation-PoP` header value).
 * @property tokenEndpointUrl Absolute URL of the AS endpoint that received the request, used as
 *   one of the acceptable PoP `aud` values.
 */
data class VerifyAttestationClientAuthArgs(
    val clientId: String,
    val attestationJwt: String,
    val popJwt: String,
    val tokenEndpointUrl: String,
    val endpoint: ClientAuthenticationEndpoint = ClientAuthenticationEndpoint.TOKEN,
)

/**
 * Verify attestation-based client authentication command.
 *
 * Implements the 14-step verification path from
 * draft-ietf-oauth-attestation-based-client-auth-07 Section 4 / 5:
 * `typ` enforcement, attester signature against the client-pinned trust list,
 * attestation expiration / freshness, `cnf.jwk` extraction, PoP signature
 * against `cnf.jwk`, `iss == attestation.sub`, `aud`, PoP `iat` freshness, and
 * challenge replay protection.
 *
 * Bound and contributed at `SessionScope` because it reads the per-session
 * `OAuth2ServersConfigProvider` for the active server's attestation feature
 * config and uses the session-scoped `AttestationChallengeStorage`.
 */
interface VerifyAttestationClientAuthCommand : ServiceCommand<VerifyAttestationClientAuthArgs, VerifiedClientAuthentication, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.clientauth.verify-attestation"
    }
}
