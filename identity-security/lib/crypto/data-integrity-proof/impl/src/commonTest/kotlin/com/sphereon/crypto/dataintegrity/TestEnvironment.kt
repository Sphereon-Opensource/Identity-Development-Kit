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

package com.sphereon.crypto.dataintegrity

import com.sphereon.crypto.dataintegrity.algorithm.AddProofAlgorithm
import com.sphereon.crypto.dataintegrity.algorithm.AddProofAlgorithmImpl
import com.sphereon.crypto.dataintegrity.algorithm.VerifyProofAlgorithm
import com.sphereon.crypto.dataintegrity.algorithm.VerifyProofAlgorithmImpl
import com.sphereon.crypto.dataintegrity.cryptosuite.DataIntegrityCryptosuiteCreator
import com.sphereon.crypto.dataintegrity.cryptosuite.DataIntegrityCryptosuiteVerifier
import com.sphereon.crypto.dataintegrity.registry.CryptosuiteRegistry
import com.sphereon.crypto.dataintegrity.registry.CryptosuiteRegistryImpl

/**
 * Hand-wired registry + algorithms for unit tests, bypassing the Metro DI graph.
 *
 * Tests of the Metro graph itself are an integration concern (covered in the
 * services that aggregate `:lib:crypto:data-integrity-proof:impl` together
 * with concrete cryptosuite modules).
 */
internal data class TestEnvironment(
    val registry: CryptosuiteRegistry,
    val addProof: AddProofAlgorithm,
    val verifyProof: VerifyProofAlgorithm,
)

internal fun makeTestEnvironment(
    creators: Set<DataIntegrityCryptosuiteCreator> = setOf(TestCryptosuiteCreator()),
    verifiers: Set<DataIntegrityCryptosuiteVerifier> = setOf(TestCryptosuiteVerifier()),
): TestEnvironment {
    val registry = CryptosuiteRegistryImpl(creators, verifiers)
    return TestEnvironment(
        registry = registry,
        addProof = AddProofAlgorithmImpl(registry),
        verifyProof = VerifyProofAlgorithmImpl(registry),
    )
}
