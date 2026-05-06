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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.dataintegrity.algorithm.AddProofAlgorithm
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AddProofServiceCommand>())
class AddProofServiceCommandImpl(
    execution: SessionExecution,
    private val algorithm: AddProofAlgorithm,
) : TypedServiceCommandAdapter<AddProofInput, AddProofOutput, IdkError>(
        commandId = AddProofServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<AddProofInput>(),
        outputTypeToken = typeToken<AddProofOutput>(),
    ),
    AddProofServiceCommand {
    override val commandId: String get() = AddProofServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is AddProofInput

    override suspend fun doExecute(
        args: AddProofInput,
        applyDuring: (AddProofInput) -> AddProofInput,
    ): IdkResult<AddProofOutput, IdkError> {
        val input = applyDuring(args)
        val result = algorithm.addProofs(input.unsecuredDocument, input.proofs)
        return if (result.isOk) {
            Ok(AddProofOutput(securedDocument = result.value))
        } else {
            Err(result.error)
        }
    }
}
