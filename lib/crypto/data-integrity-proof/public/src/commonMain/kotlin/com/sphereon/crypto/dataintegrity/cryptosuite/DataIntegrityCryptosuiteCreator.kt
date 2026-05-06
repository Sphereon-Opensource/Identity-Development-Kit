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

package com.sphereon.crypto.dataintegrity.cryptosuite

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.model.ProofOptions
import kotlinx.serialization.json.JsonObject

/**
 * Cryptosuite contract for the W3C VC-DI 1.0 "Add Proof" operation.
 *
 * Each concrete implementation handles one cryptosuite (e.g. `eddsa-jcs-2022`).
 * Implementations are registered into a `Set<DataIntegrityCryptosuiteCreator>`
 * via Metro multibinding and discovered by `CryptosuiteRegistry`.
 */
interface DataIntegrityCryptosuiteCreator : DataIntegrityCryptosuite {
    /**
     * Compute a proof for [unsecuredDocument] under the given [options].
     *
     * The returned [DataIntegrityProof] carries the `cryptosuite`, the
     * verification method reference, the proof purpose, the multibase-encoded
     * signature in `proofValue`, and any optional metadata that was set on
     * [options]. The orchestrating "Add Proof" algorithm is responsible for
     * inserting the proof into the secured document.
     */
    suspend fun createProof(
        unsecuredDocument: JsonObject,
        options: ProofOptions,
    ): IdkResult<DataIntegrityProof, IdkError>
}
