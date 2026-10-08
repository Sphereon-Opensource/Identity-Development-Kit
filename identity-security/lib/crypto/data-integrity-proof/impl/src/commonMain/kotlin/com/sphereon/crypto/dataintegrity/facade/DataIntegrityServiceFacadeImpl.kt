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

package com.sphereon.crypto.dataintegrity.facade

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.dataintegrity.command.AddProofInput
import com.sphereon.crypto.dataintegrity.command.AddProofOutput
import com.sphereon.crypto.dataintegrity.command.AddProofServiceCommand
import com.sphereon.crypto.dataintegrity.command.VerifyProofInput
import com.sphereon.crypto.dataintegrity.command.VerifyProofServiceCommand
import com.sphereon.crypto.dataintegrity.model.DataIntegrityVerificationResult
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DataIntegrityServiceFacade>())
class DataIntegrityServiceFacadeImpl(
    private val addProofCommand: AddProofServiceCommand,
    private val verifyProofCommand: VerifyProofServiceCommand,
) : DataIntegrityServiceFacade {
    override suspend fun addProof(input: AddProofInput): IdkResult<AddProofOutput, IdkError> = addProofCommand.execute(input)

    override suspend fun verifyProof(input: VerifyProofInput): IdkResult<DataIntegrityVerificationResult, IdkError> = verifyProofCommand.execute(input)
}
