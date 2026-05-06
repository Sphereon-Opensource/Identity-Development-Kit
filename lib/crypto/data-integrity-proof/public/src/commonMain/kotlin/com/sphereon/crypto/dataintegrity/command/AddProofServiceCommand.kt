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
import com.sphereon.crypto.dataintegrity.model.ProofOptions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * W3C VC-DI 1.0 §4.3 "Add Proof" algorithm exposed as an IDK ServiceCommand.
 *
 * Supports single proof, proof set, and proof chain semantics:
 * - One [ProofOptions] in [AddProofInput.proofs]: produces a single proof
 *   embedded as `proof` (object) in the secured document.
 * - Multiple options without `previousProof`: produces a proof set,
 *   embedded as `proof` (array of objects) with no ordering guarantee.
 * - Multiple options where each links to its predecessor via
 *   `ProofOptions.previousProof`: produces a proof chain. Each link is
 *   computed over the input document with all prior proofs already
 *   inserted, per spec §4.5.
 */
interface AddProofServiceCommand : ServiceCommand<AddProofInput, AddProofOutput, com.sphereon.core.api.error.IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE

    companion object {
        const val COMMAND_ID = "crypto.dataintegrity.add-proof"
    }
}

@Serializable
data class AddProofInput(
    /** The unsecured JSON document to attach the proof(s) to. */
    val unsecuredDocument: JsonObject,
    /**
     * One or more proof requests. Order matters for proof chains: each
     * entry whose [ProofOptions.previousProof] is non-null is computed
     * AFTER the proof(s) it references have been added.
     */
    val proofs: List<ProofOptions>,
)

@Serializable
data class AddProofOutput(
    /** The secured document (unsecured + the appended proofs). */
    val securedDocument: JsonObject,
)
