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
 *
 */

package com.sphereon.crypto.dataintegrity.command

import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.dataintegrity.model.DataIntegrityVerificationResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolutionPolicy

/**
 * W3C VC-DI 1.0 §4.4 "Verify Proof" algorithm exposed as an IDK ServiceCommand.
 *
 * Verifies a single proof, a proof set, or a proof chain on the input
 * secured document. The output mirrors the spec's verification result
 * shape ([DataIntegrityVerificationResult]).
 */
interface VerifyProofServiceCommand : ServiceCommand<VerifyProofInput, DataIntegrityVerificationResult, com.sphereon.core.api.error.IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ

    companion object {
        const val COMMAND_ID = "crypto.dataintegrity.verify-proof"
    }
}

@Serializable
data class VerifyProofInput(
    /** The secured document, containing one `proof` field (object or array). */
    val securedDocument: JsonObject,
    /**
     * Optional explicit expected purpose. When non-null, every proof on the
     * document MUST have `proofPurpose` matching this value; otherwise
     * verification fails. When null, proofs are accepted regardless of
     * their purpose value (still cryptographically verified).
     */
    val expectedProofPurpose: String? = null,
    /**
     * Optional expected media type to surface in the result; spec §4.4
     * allows the caller to constrain it.
     */
    val expectedMediaType: String? = null,
    /** Verifier-owned exact-reference policy; never derived from proof input. */
    @Transient
    val verificationMethodResolutionPolicy: VerificationMethodResolutionPolicy =
        VerificationMethodResolutionPolicy.empty(),
)
