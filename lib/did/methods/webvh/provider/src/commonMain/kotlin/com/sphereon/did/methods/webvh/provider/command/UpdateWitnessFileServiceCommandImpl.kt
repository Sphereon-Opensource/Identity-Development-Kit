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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.did.methods.webvh.command.UpdateWitnessFileInput
import com.sphereon.did.methods.webvh.command.UpdateWitnessFileOutput
import com.sphereon.did.methods.webvh.command.UpdateWitnessFileServiceCommand
import com.sphereon.did.methods.webvh.model.WebvhWitnessFile
import com.sphereon.did.methods.webvh.model.WebvhWitnessFileEntry
import com.sphereon.did.methods.webvh.model.WebvhWitnessProof
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

/**
 * Merges incoming witness proofs into a `did-witness.json` file. Per spec
 * §3.4 a witness's proof on a versionId implies endorsement of all earlier
 * entries; we keep at most one proof per witness per versionId, replacing
 * older entries when an incoming proof references a newer versionId.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UpdateWitnessFileServiceCommand>())
class UpdateWitnessFileServiceCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<UpdateWitnessFileInput, UpdateWitnessFileOutput, IdkError>(
        commandId = UpdateWitnessFileServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<UpdateWitnessFileInput>(),
        outputTypeToken = typeToken<UpdateWitnessFileOutput>(),
    ),
    UpdateWitnessFileServiceCommand {
    private val json =
        Json {
            encodeDefaults = false
            explicitNulls = false
            prettyPrint = false
        }

    override val commandId: String get() = UpdateWitnessFileServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is UpdateWitnessFileInput

    override suspend fun doExecute(
        args: UpdateWitnessFileInput,
        applyDuring: (UpdateWitnessFileInput) -> UpdateWitnessFileInput,
    ): IdkResult<UpdateWitnessFileOutput, IdkError> {
        val input = applyDuring(args)

        // Group by versionId, then within each group by witness DID (verificationMethod's DID part).
        val merged = (input.existingWitnessFile?.proofs ?: emptyList()).toMutableList()
        for (incoming in input.incomingProofs) {
            insertOrReplace(merged, incoming)
        }
        val file = WebvhWitnessFile(proofs = merged)
        val jsonText = json.encodeToString(WebvhWitnessFile.serializer(), file)
        return Ok(UpdateWitnessFileOutput(witnessFile = file, witnessFileJson = jsonText))
    }

    private fun insertOrReplace(
        existing: MutableList<WebvhWitnessFileEntry>,
        incoming: WebvhWitnessProof,
    ) {
        val witnessDid = incoming.proof.verificationMethod.substringBefore('#')
        // Drop any prior proof from this witness for any versionId — per spec a newer endorsement
        // supersedes older ones (since a proof on versionId N implies endorsement of all N-1 entries).
        for (entry in existing) {
            entry as Any // keep mutable list type happy
        }
        for (i in existing.indices) {
            val entry = existing[i]
            val pruned = entry.proof.filter { p -> p.verificationMethod.substringBefore('#') != witnessDid }
            if (pruned.size != entry.proof.size) {
                existing[i] = entry.copy(proof = pruned)
            }
        }
        existing.removeAll { it.proof.isEmpty() }

        // Append/merge into the entry for this versionId.
        val idx = existing.indexOfFirst { it.versionId == incoming.versionId }
        if (idx >= 0) {
            existing[idx] = existing[idx].copy(proof = existing[idx].proof + incoming.proof)
        } else {
            existing.add(WebvhWitnessFileEntry(versionId = incoming.versionId, proof = listOf(incoming.proof)))
        }
    }
}
