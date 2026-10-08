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
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CryptosuiteRegistry>())
class CryptosuiteRegistryImpl(
    creators: Set<DataIntegrityCryptosuiteCreator>,
    verifiers: Set<DataIntegrityCryptosuiteVerifier>,
) : CryptosuiteRegistry {
    private val creatorsById: Map<String, DataIntegrityCryptosuiteCreator> =
        creators.associateBy { it.cryptosuiteId }
    private val verifiersById: Map<String, DataIntegrityCryptosuiteVerifier> =
        verifiers.associateBy { it.cryptosuiteId }

    override fun getCreator(cryptosuiteId: String): DataIntegrityCryptosuiteCreator? = creatorsById[cryptosuiteId]

    override fun getVerifier(cryptosuiteId: String): DataIntegrityCryptosuiteVerifier? = verifiersById[cryptosuiteId]

    override fun supportedCreators(): Set<String> = creatorsById.keys

    override fun supportedVerifiers(): Set<String> = verifiersById.keys
}
