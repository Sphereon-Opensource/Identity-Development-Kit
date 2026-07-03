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
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.server.authorization.wallet.WalletInstanceAttestationEvidence

enum class ClientAuthenticationEndpoint {
    TOKEN,
    PAR,
    REVOCATION,
    INTROSPECTION,
    OTHER,
}

/**
 * Arguments for verifying client authentication.
 */
data class VerifyClientAuthenticationArgs(
    val clientAuthentication: ClientAuthenticationConfig,
    val clientId: String,
    val tokenEndpointUrl: String,
    val endpoint: ClientAuthenticationEndpoint = ClientAuthenticationEndpoint.OTHER,
)

/**
 * Result of successful client authentication verification.
 */
data class VerifiedClientAuthentication(
    val clientId: String,
    val method: ClientAuthenticationMethod,
    val clientInstanceKey: Jwk? = null,
    val walletInstanceAttestation: WalletInstanceAttestationEvidence? = null,
)

/**
 * Verify client authentication command.
 *
 * Dispatches by ClientAuthenticationConfig variant to verify the client's identity:
 * - Basic/Post: verify client_id + secret against ClientRegistry
 * - SecretJwt/PrivateKeyJwt: validate JWT assertion signature + claims
 * - AttestationJwt: full attestation-based client auth verification (draft-ietf-oauth-attestation-based-client-auth)
 * - None/Anonymous: pass through
 */
interface VerifyClientAuthenticationCommand : ServiceCommand<VerifyClientAuthenticationArgs, VerifiedClientAuthentication, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.clientauth.verify"
    }
}
