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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.dataintegrity.algorithm.VerifyProofAlgorithm
import com.sphereon.crypto.dataintegrity.model.DataIntegrityVerificationResult
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<VerifyProofServiceCommand>())
class VerifyProofServiceCommandImpl(
    execution: SessionExecution,
    private val algorithm: VerifyProofAlgorithm,
) : TypedServiceCommandAdapter<VerifyProofInput, DataIntegrityVerificationResult, IdkError>(
        commandId = VerifyProofServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifyProofInput>(),
        outputTypeToken = typeToken<DataIntegrityVerificationResult>(),
    ),
    VerifyProofServiceCommand {
    override val commandId: String get() = VerifyProofServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyProofInput

    override suspend fun doExecute(
        args: VerifyProofInput,
        applyDuring: (VerifyProofInput) -> VerifyProofInput,
    ): IdkResult<DataIntegrityVerificationResult, IdkError> {
        val input = applyDuring(args)
        return algorithm.verify(
            securedDocument = input.securedDocument,
            expectedProofPurpose = input.expectedProofPurpose,
            expectedMediaType = input.expectedMediaType,
            verificationMethodResolutionPolicy = input.verificationMethodResolutionPolicy,
        )
    }
}
