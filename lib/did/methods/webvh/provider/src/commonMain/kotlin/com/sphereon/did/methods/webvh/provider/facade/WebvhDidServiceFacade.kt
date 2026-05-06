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
import com.sphereon.core.api.service.ServiceFacade
import com.sphereon.did.methods.webvh.command.CreateWebvhDidInput
import com.sphereon.did.methods.webvh.command.CreateWebvhDidOutput
import com.sphereon.did.methods.webvh.command.CreateWitnessProofInput
import com.sphereon.did.methods.webvh.command.CreateWitnessProofOutput
import com.sphereon.did.methods.webvh.command.DeactivateWebvhDidInput
import com.sphereon.did.methods.webvh.command.DeactivateWebvhDidOutput
import com.sphereon.did.methods.webvh.command.UpdateWebvhDidInput
import com.sphereon.did.methods.webvh.command.UpdateWebvhDidOutput
import com.sphereon.did.methods.webvh.command.UpdateWitnessFileInput
import com.sphereon.did.methods.webvh.command.UpdateWitnessFileOutput

/**
 * Convenience entry point for `did:webvh` lifecycle. Delegates to the matching
 * `ServiceCommand`s without wrapping.
 */
interface WebvhDidServiceFacade : ServiceFacade {
    override val serviceId: String get() = "did.webvh"

    suspend fun create(input: CreateWebvhDidInput): IdkResult<CreateWebvhDidOutput, IdkError>

    suspend fun update(input: UpdateWebvhDidInput): IdkResult<UpdateWebvhDidOutput, IdkError>

    suspend fun deactivate(input: DeactivateWebvhDidInput): IdkResult<DeactivateWebvhDidOutput, IdkError>

    suspend fun createWitnessProof(input: CreateWitnessProofInput): IdkResult<CreateWitnessProofOutput, IdkError>

    suspend fun updateWitnessFile(input: UpdateWitnessFileInput): IdkResult<UpdateWitnessFileOutput, IdkError>
}
