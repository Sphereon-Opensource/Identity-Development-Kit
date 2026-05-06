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

package com.sphereon.did.methods.webvh.provider.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.dataintegrity.command.AddProofInput
import com.sphereon.crypto.dataintegrity.command.AddProofServiceCommand
import com.sphereon.crypto.dataintegrity.eddsajcs2022.EddsaJcs2022Cryptosuite
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.model.ProofOptions
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.di.session.SessionScope
import com.sphereon.did.methods.webvh.command.CreateWitnessProofInput
import com.sphereon.did.methods.webvh.command.CreateWitnessProofOutput
import com.sphereon.did.methods.webvh.command.CreateWitnessProofServiceCommand
import com.sphereon.did.methods.webvh.model.WebvhWitnessProof
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Witness-side: signs a `did:webvh` versionId with the witness's own
 * Ed25519 KMS key, producing a single [WebvhWitnessProof] for the
 * controller to merge into `did-witness.json`.
 *
 * Per spec §3.4 the witness DID MUST be a `did:key`. The proof's
 * `verificationMethod` is the witness's key URL. The document being signed
 * is `{"versionId": "<value>"}`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateWitnessProofServiceCommand>())
class CreateWitnessProofServiceCommandImpl(
    execution: SessionExecution,
    private val addProofCommand: AddProofServiceCommand,
) : TypedServiceCommandAdapter<CreateWitnessProofInput, CreateWitnessProofOutput, IdkError>(
        commandId = CreateWitnessProofServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateWitnessProofInput>(),
        outputTypeToken = typeToken<CreateWitnessProofOutput>(),
    ),
    CreateWitnessProofServiceCommand {
    override val commandId: String get() = CreateWitnessProofServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateWitnessProofInput

    override suspend fun doExecute(
        args: CreateWitnessProofInput,
        applyDuring: (CreateWitnessProofInput) -> CreateWitnessProofInput,
    ): IdkResult<CreateWitnessProofOutput, IdkError> {
        val input = applyDuring(args)

        val witnessDid =
            input.witnessDid
                ?: return Err(
                    IdkError.fromString(
                        "CreateWitnessProof v1 requires witnessDid to be provided. Auto-derivation from signing key is a follow-up.",
                        code = "ILLEGAL_ARGUMENT",
                    ),
                )
        if (!witnessDid.startsWith("did:key:")) {
            return Err(IdkError.fromString("Witness DID MUST be a did:key per spec §3.4: $witnessDid", code = "ILLEGAL_ARGUMENT"))
        }

        // Document signed: {"versionId": "<value>"}.
        val document: JsonObject = buildJsonObject { put("versionId", JsonPrimitive(input.versionId)) }
        val verificationMethod = "$witnessDid#${witnessDid.removePrefix("did:key:")}"

        val proofOptions =
            ProofOptions(
                cryptosuite = EddsaJcs2022Cryptosuite.ID,
                verificationMethod = verificationMethod,
                proofPurpose = ProofPurpose.ASSERTION_METHOD,
                signingKeyRef = input.signingKeyRef,
            )
        val addResult = addProofCommand.execute(AddProofInput(unsecuredDocument = document, proofs = listOf(proofOptions)))
        if (addResult.isErr) {
            return Err(addResult.error)
        }

        val secured = addResult.value.securedDocument
        val proofElement =
            secured["proof"]
                ?: return Err(IdkError.fromString("AddProofServiceCommand returned secured document without proof", code = ERROR_PROOF_GENERATION))
        val proofObj =
            (proofElement as? JsonObject)
                ?: return Err(IdkError.fromString("Witness signing produced a proof set/chain rather than a single proof", code = ERROR_PROOF_GENERATION))
        val diProof =
            try {
                DI_JSON.decodeFromJsonElement(DataIntegrityProof.serializer(), proofObj)
            } catch (expected: Exception) {
                return Err(IdkError.fromString("Failed to decode signed proof: ${expected.message}", code = ERROR_PROOF_GENERATION, exception = expected))
            }

        return Ok(
            CreateWitnessProofOutput(
                witnessProof = WebvhWitnessProof(versionId = input.versionId, proof = diProof),
            ),
        )
    }

    companion object {
        private val DI_JSON =
            kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        private const val ERROR_PROOF_GENERATION = "PROOF_GENERATION_ERROR"
    }
}
