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

import com.sphereon.crypto.dataintegrity.registry.CryptosuiteRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CryptosuiteRegistryImplTest {
    @Test
    fun resolvesByCryptosuiteId() {
        val registry =
            CryptosuiteRegistryImpl(
                creators =
                    setOf(
                        TestCryptosuiteCreator(TEST_CRYPTOSUITE_ID),
                        TestCryptosuiteCreator(TEST_CRYPTOSUITE_ID_ALT),
                    ),
                verifiers = setOf(TestCryptosuiteVerifier(TEST_CRYPTOSUITE_ID)),
            )
        assertNotNull(registry.getCreator(TEST_CRYPTOSUITE_ID))
        assertNotNull(registry.getCreator(TEST_CRYPTOSUITE_ID_ALT))
        assertNotNull(registry.getVerifier(TEST_CRYPTOSUITE_ID))
        assertNull(registry.getVerifier(TEST_CRYPTOSUITE_ID_ALT))
        assertEquals(setOf(TEST_CRYPTOSUITE_ID, TEST_CRYPTOSUITE_ID_ALT), registry.supportedCreators())
        assertEquals(setOf(TEST_CRYPTOSUITE_ID), registry.supportedVerifiers())
    }

    @Test
    fun emptyRegistryReturnsNull() {
        val registry = CryptosuiteRegistryImpl(emptySet(), emptySet())
        assertNull(registry.getCreator("anything"))
        assertNull(registry.getVerifier("anything"))
        assertEquals(emptySet(), registry.supportedCreators())
    }
}
