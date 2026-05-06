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

package com.sphereon.did.methods.webvh.resolver.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.did.methods.webvh.command.ReplayWebvhLogInput
import com.sphereon.did.methods.webvh.command.ReplayWebvhLogOutput
import com.sphereon.did.methods.webvh.command.ReplayWebvhLogServiceCommand
import com.sphereon.did.methods.webvh.resolver.ReplaySelector
import com.sphereon.did.methods.webvh.resolver.WebvhLogReplayer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * `did.webvh.replay-log` ServiceCommand. Thin wrapper over [WebvhLogReplayer]
 * that adds the standard command surface (audit, authorization, telemetry,
 * `applyDuring` extension hooks) without changing replay semantics. Internal
 * IDK callers (`WebvhDidResolverImpl`, `FetchWebvhLogServiceCommandImpl`)
 * continue to use [WebvhLogReplayer] directly to avoid double command-overhead
 * for the same operation; external callers go through this command.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ReplayWebvhLogServiceCommand>())
class ReplayWebvhLogServiceCommandImpl(
    execution: SessionExecution,
    private val replayer: WebvhLogReplayer,
) : TypedServiceCommandAdapter<ReplayWebvhLogInput, ReplayWebvhLogOutput, IdkError>(
        commandId = ReplayWebvhLogServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ReplayWebvhLogInput>(),
        outputTypeToken = typeToken<ReplayWebvhLogOutput>(),
    ),
    ReplayWebvhLogServiceCommand {
    override val commandId: String get() = ReplayWebvhLogServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ReplayWebvhLogInput

    override suspend fun doExecute(
        args: ReplayWebvhLogInput,
        applyDuring: (ReplayWebvhLogInput) -> ReplayWebvhLogInput,
    ): IdkResult<ReplayWebvhLogOutput, IdkError> {
        val input = applyDuring(args)
        val selectorProblem = validateSelector(input)
        if (selectorProblem != null) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = selectorProblem))
        }
        val selector = resolveSelector(input)
        val replay =
            replayer
                .replay(
                    did = input.did,
                    entries = input.entries,
                    witnessFile = input.witnessFile,
                    selector = selector,
                ).let { result ->
                    if (result.isErr) {
                        return Err(result.error)
                    }
                    result.value
                }
        return Ok(
            ReplayWebvhLogOutput(
                didDocument = replay.didDocument,
                selectedEntry = replay.selectedEntry,
                activeParameters = replay.activeParameters,
            ),
        )
    }

    /** At most one selector field may be set. Reject ambiguous inputs at the boundary. */
    private fun validateSelector(input: ReplayWebvhLogInput): String? {
        val setCount =
            listOf(input.versionId, input.versionTime, input.versionNumber).count { it != null }
        return if (setCount > 1) {
            "ReplayWebvhLogInput accepts at most one of versionId, versionTime, versionNumber"
        } else {
            null
        }
    }

    private fun resolveSelector(input: ReplayWebvhLogInput): ReplaySelector {
        val byId = input.versionId
        if (byId != null) {
            return ReplaySelector.ByVersionId(byId)
        }
        val byNumber = input.versionNumber
        if (byNumber != null) {
            return ReplaySelector.ByVersionNumber(byNumber)
        }
        val byTime = input.versionTime
        if (byTime != null) {
            return ReplaySelector.ByVersionTime(byTime)
        }
        return ReplaySelector.LATEST
    }
}
