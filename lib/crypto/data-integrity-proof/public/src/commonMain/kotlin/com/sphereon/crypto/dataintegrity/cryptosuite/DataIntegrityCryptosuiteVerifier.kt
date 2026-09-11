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
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolutionPolicy
import kotlinx.serialization.json.JsonObject

/**
 * Cryptosuite contract for the W3C VC-DI 1.0 §3.1 cryptosuite-level verify
 * algorithm.
 *
 * Implementations are registered into a `Set<DataIntegrityCryptosuiteVerifier>`
 * via Metro multibinding and discovered by `CryptosuiteRegistry`.
 */
interface DataIntegrityCryptosuiteVerifier : DataIntegrityCryptosuite {
    /**
     * Verify [proof] against [unsecuredDocument].
     *
     * Per spec §3.1 returns a struct of `{verified, verifiedDocument}`. We
     * also return implementation-specific [CryptosuiteVerification.errors]
     * to let the orchestrator surface failure detail in its aggregate
     * `errors` list.
     *
     * Returns Err(IdkError) only when the verifier cannot make a decision
     * (e.g. cannot resolve the verification method); a failed signature
     * check returns Ok with `verified = false`.
     */
    suspend fun verifyProof(
        unsecuredDocument: JsonObject,
        proof: DataIntegrityProof,
        verificationMethodResolutionPolicy: VerificationMethodResolutionPolicy =
            VerificationMethodResolutionPolicy.empty(),
    ): IdkResult<CryptosuiteVerification, IdkError>
}

/**
 * Per-proof outcome from a single cryptosuite verifier, mirroring W3C VC-DI
 * 1.0 §3.1's "cryptosuite verification result" struct (`verified` +
 * `verifiedDocument`) plus an implementation-specific [errors] list.
 */
data class CryptosuiteVerification(
    val verified: Boolean,
    val verifiedDocument: JsonObject? = null,
    val errors: List<String> = emptyList(),
)
