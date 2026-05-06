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
import com.sphereon.core.api.service.ServiceFacade
import com.sphereon.crypto.dataintegrity.command.AddProofInput
import com.sphereon.crypto.dataintegrity.command.AddProofOutput
import com.sphereon.crypto.dataintegrity.command.VerifyProofInput
import com.sphereon.crypto.dataintegrity.model.DataIntegrityVerificationResult

/**
 * Convenience entry point for the W3C VC-DI 1.0 add-proof / verify-proof
 * algorithms. Delegates to the matching `ServiceCommand`s without wrapping.
 */
interface DataIntegrityServiceFacade : ServiceFacade {
    override val serviceId: String get() = "crypto.dataintegrity"

    suspend fun addProof(input: AddProofInput): IdkResult<AddProofOutput, IdkError>

    suspend fun verifyProof(input: VerifyProofInput): IdkResult<DataIntegrityVerificationResult, IdkError>
}
