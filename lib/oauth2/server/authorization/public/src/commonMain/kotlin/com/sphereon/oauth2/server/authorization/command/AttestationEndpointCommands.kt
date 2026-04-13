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

import com.sphereon.core.api.service.ServiceCommand

/**
 * Arguments for creating an attestation challenge.
 */
data class CreateAttestationChallengeArgs(
    val clientId: String? = null,
)

/**
 * Response from creating an attestation challenge.
 */
data class AttestationChallengeResponse(
    val attestationChallenge: String,
)

/**
 * Create attestation challenge command.
 *
 * draft-ietf-oauth-attestation-based-client-auth Section 4:
 * Generates a challenge nonce that the client must include in the
 * attestation PoP JWT to prove freshness.
 */
interface CreateAttestationChallengeCommand : ServiceCommand<CreateAttestationChallengeArgs, AttestationChallengeResponse> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.attestation.challenge.create"
    }
}
