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

package com.sphereon.crypto.dataintegrity.registry

import com.sphereon.crypto.dataintegrity.cryptosuite.DataIntegrityCryptosuiteCreator
import com.sphereon.crypto.dataintegrity.cryptosuite.DataIntegrityCryptosuiteVerifier

/**
 * Lookup table for VC-DI cryptosuites.
 *
 * Cryptosuites are discovered via Metro multibinding: every concrete impl
 * uses `@ContributesIntoSet(SessionScope::class, binding = binding<DataIntegrityCryptosuiteCreator>())`
 * (and the same for [DataIntegrityCryptosuiteVerifier]) so the registry's
 * constructor receives all of them in one set.
 */
interface CryptosuiteRegistry {
    /** Returns the creator registered for [cryptosuiteId], or null. */
    fun getCreator(cryptosuiteId: String): DataIntegrityCryptosuiteCreator?

    /** Returns the verifier registered for [cryptosuiteId], or null. */
    fun getVerifier(cryptosuiteId: String): DataIntegrityCryptosuiteVerifier?

    /** Distinct cryptosuite ids supported by registered creators. */
    fun supportedCreators(): Set<String>

    /** Distinct cryptosuite ids supported by registered verifiers. */
    fun supportedVerifiers(): Set<String>
}
