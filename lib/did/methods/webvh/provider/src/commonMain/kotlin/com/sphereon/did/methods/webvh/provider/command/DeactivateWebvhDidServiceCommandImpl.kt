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
import com.sphereon.di.session.SessionScope
import com.sphereon.did.methods.webvh.command.DeactivateWebvhDidInput
import com.sphereon.did.methods.webvh.command.DeactivateWebvhDidOutput
import com.sphereon.did.methods.webvh.command.DeactivateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.log.WebvhLogWriter
import com.sphereon.did.methods.webvh.model.WebvhLogEntry
import com.sphereon.did.methods.webvh.model.WebvhParameters
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.datetime.Clock

/**
 * Appends a deactivation entry to the log per spec §3.6: sets
 * `parameters.deactivated = true` AND clears `updateKeys` (empty list,
 * locking the DID against further updates).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeactivateWebvhDidServiceCommand>())
class DeactivateWebvhDidServiceCommandImpl(
    execution: SessionExecution,
    private val signer: WebvhEntrySigner,
    private val didWebCompanionService: com.sphereon.did.methods.webvh.provider.companion.WebvhDidWebCompanionService,
) : TypedServiceCommandAdapter<DeactivateWebvhDidInput, DeactivateWebvhDidOutput, IdkError>(
        commandId = DeactivateWebvhDidServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DeactivateWebvhDidInput>(),
        outputTypeToken = typeToken<DeactivateWebvhDidOutput>(),
    ),
    DeactivateWebvhDidServiceCommand {
    override val commandId: String get() = DeactivateWebvhDidServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is DeactivateWebvhDidInput

    override suspend fun doExecute(
        args: DeactivateWebvhDidInput,
        applyDuring: (DeactivateWebvhDidInput) -> DeactivateWebvhDidInput,
    ): IdkResult<DeactivateWebvhDidOutput, IdkError> {
        val input = applyDuring(args)
        if (input.existingLog.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "existingLog is required for deactivation"))
        }
        if (input.signingKeyRefs.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "signingKeyRefs is required"))
        }

        val prior = input.existingLog.last()
        val nextVersionNumber = input.existingLog.size + 1
        val versionTime = input.versionTime ?: Clock.System.now().toString()
        if (versionTime <= prior.versionTime) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "versionTime '$versionTime' MUST be strictly after prior '${prior.versionTime}'"))
        }

        val verificationMethodId =
            prior.state.verificationMethod
                ?.firstOrNull()
                ?.id
                ?: "${input.did}#key-1"

        val entryWithoutProof =
            WebvhLogEntry(
                versionId = prior.versionId,
                versionTime = versionTime,
                parameters =
                    WebvhParameters(
                        deactivated = true,
                        updateKeys = emptyList(),
                        nextKeyHashes = emptyList(),
                    ),
                state = prior.state,
                proof = emptyList(),
            )
        val signedEntry =
            signer
                .signEntry(
                    entryWithoutProof = entryWithoutProof,
                    versionNumber = nextVersionNumber,
                    signingKeyRef = input.signingKeyRefs.first(),
                    verificationMethod = verificationMethodId,
                    createdAt = versionTime,
                ).getOrElseErr { return Err(it) }

        val newLog = input.existingLog + signedEntry
        val companion = didWebCompanionService.buildIfEnabled(input.did, signedEntry.state)
        return Ok(
            DeactivateWebvhDidOutput(
                deactivationEntry = signedEntry,
                newLogJsonl = WebvhLogWriter.write(newLog),
                didWebDocument = companion?.didWebDocument,
                didWebJson = companion?.didWebJson,
            ),
        )
    }
}

private inline fun <V, E> IdkResult<V, E>.getOrElseErr(onErr: (E) -> Nothing): V =
    if (isOk) {
        value
    } else {
        onErr(error)
    }
