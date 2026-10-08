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

package com.sphereon.did.methods.webvh.provider.facade

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.did.methods.webvh.command.CreateWebvhDidInput
import com.sphereon.did.methods.webvh.command.CreateWebvhDidOutput
import com.sphereon.did.methods.webvh.command.CreateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.CreateWitnessProofInput
import com.sphereon.did.methods.webvh.command.CreateWitnessProofOutput
import com.sphereon.did.methods.webvh.command.CreateWitnessProofServiceCommand
import com.sphereon.did.methods.webvh.command.DeactivateWebvhDidInput
import com.sphereon.did.methods.webvh.command.DeactivateWebvhDidOutput
import com.sphereon.did.methods.webvh.command.DeactivateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.UpdateWebvhDidInput
import com.sphereon.did.methods.webvh.command.UpdateWebvhDidOutput
import com.sphereon.did.methods.webvh.command.UpdateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.UpdateWitnessFileInput
import com.sphereon.did.methods.webvh.command.UpdateWitnessFileOutput
import com.sphereon.did.methods.webvh.command.UpdateWitnessFileServiceCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WebvhDidServiceFacade>())
class WebvhDidServiceFacadeImpl(
    private val createCommand: CreateWebvhDidServiceCommand,
    private val updateCommand: UpdateWebvhDidServiceCommand,
    private val deactivateCommand: DeactivateWebvhDidServiceCommand,
    private val createWitnessProofCommand: CreateWitnessProofServiceCommand,
    private val updateWitnessFileCommand: UpdateWitnessFileServiceCommand,
) : WebvhDidServiceFacade {
    override suspend fun create(input: CreateWebvhDidInput): IdkResult<CreateWebvhDidOutput, IdkError> = createCommand.execute(input)

    override suspend fun update(input: UpdateWebvhDidInput): IdkResult<UpdateWebvhDidOutput, IdkError> = updateCommand.execute(input)

    override suspend fun deactivate(input: DeactivateWebvhDidInput): IdkResult<DeactivateWebvhDidOutput, IdkError> = deactivateCommand.execute(input)

    override suspend fun createWitnessProof(input: CreateWitnessProofInput): IdkResult<CreateWitnessProofOutput, IdkError> = createWitnessProofCommand.execute(input)

    override suspend fun updateWitnessFile(input: UpdateWitnessFileInput): IdkResult<UpdateWitnessFileOutput, IdkError> = updateWitnessFileCommand.execute(input)
}
