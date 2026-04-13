/*
 * Copyright 2023-2026 Sphereon International B.V.
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
 */

package com.sphereon.data.store.blob

import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.data.store.blob.cas.ContentAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ContentAddressTest {
    @Test
    fun computeSha256ProducesCorrectDigestLength() {
        val data = "hello content addressing".encodeToByteArray()
        val address = ContentAddress.compute(data, DigestAlg.SHA256)
        assertEquals(DigestAlg.SHA256, address.algorithm)
        assertEquals(32, address.digest.size)
    }

    @Test
    fun computeSha512ProducesCorrectDigestLength() {
        val data = "hello sha512".encodeToByteArray()
        val address = ContentAddress.compute(data, DigestAlg.SHA512)
        assertEquals(DigestAlg.SHA512, address.algorithm)
        assertEquals(64, address.digest.size)
    }

    @Test
    fun multibaseStringRoundtrip() {
        val data = "roundtrip test data".encodeToByteArray()
        val address = ContentAddress.compute(data, DigestAlg.SHA256)

        val encoded = address.toMultibaseString()
        assertTrue(encoded.startsWith("z"), "BASE58BTC multibase should start with 'z'")

        val decoded = ContentAddress.fromMultibaseString(encoded)
        assertEquals(address, decoded)
    }

    @Test
    fun digestStringRoundtrip() {
        val data = "digest string test".encodeToByteArray()
        val address = ContentAddress.compute(data, DigestAlg.SHA256)

        val digestStr = address.toDigestString()
        assertTrue(digestStr.startsWith("SHA256:"), "Digest string should start with algorithm name")

        val decoded = ContentAddress.fromDigestString(digestStr)
        assertEquals(address, decoded)
    }

    @Test
    fun sameDataSameAddress() {
        val data = "deterministic content".encodeToByteArray()
        val a1 = ContentAddress.compute(data, DigestAlg.SHA256)
        val a2 = ContentAddress.compute(data, DigestAlg.SHA256)
        assertEquals(a1, a2)
    }

    @Test
    fun differentDataDifferentAddress() {
        val a1 = ContentAddress.compute("data one".encodeToByteArray(), DigestAlg.SHA256)
        val a2 = ContentAddress.compute("data two".encodeToByteArray(), DigestAlg.SHA256)
        assertNotEquals(a1, a2)
    }

    @Test
    fun hashlinkFormat() {
        val data = "hashlink test".encodeToByteArray()
        val address = ContentAddress.compute(data, DigestAlg.SHA256)
        val hl = address.toHashlink()
        assertTrue(hl.startsWith("hl:z"))
    }

    @Test
    fun allSupportedAlgorithmsRoundtrip() {
        val data = "multi-alg test".encodeToByteArray()
        val algorithms = listOf(DigestAlg.SHA256, DigestAlg.SHA384, DigestAlg.SHA512)

        for (alg in algorithms) {
            val address = ContentAddress.compute(data, alg)
            val encoded = address.toMultibaseString()
            val decoded = ContentAddress.fromMultibaseString(encoded)
            assertEquals(address, decoded, "Multibase roundtrip failed for ${alg.name}")

            val digestStr = address.toDigestString()
            val decodedFromDigest = ContentAddress.fromDigestString(digestStr)
            assertEquals(address, decodedFromDigest, "Digest string roundtrip failed for ${alg.name}")
        }
    }

    @Test
    fun computeWithEmptyData() {
        val address = ContentAddress.compute(byteArrayOf(), DigestAlg.SHA256)
        assertEquals(DigestAlg.SHA256, address.algorithm)
        assertEquals(32, address.digest.size)
    }

    @Test
    fun fromDigestStringWithInvalidFormatThrows() {
        var thrown = false
        try {
            ContentAddress.fromDigestString("invalidNoColon")
        } catch (_: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown, "fromDigestString should throw on invalid format without colon")
    }

    @Test
    fun fromMultibaseStringWithGarbageThrows() {
        var thrown = false
        try {
            ContentAddress.fromMultibaseString("not-a-valid-multibase!!!")
        } catch (_: Exception) {
            thrown = true
        }
        assertTrue(thrown, "fromMultibaseString should throw on garbage input")
    }

    @Test
    fun equalsAndHashCodeContract() {
        val data = "equality check".encodeToByteArray()
        val a1 = ContentAddress.compute(data, DigestAlg.SHA256)
        val a2 = ContentAddress.compute(data, DigestAlg.SHA256)
        assertEquals(a1, a2, "Same data should produce equal addresses")
        assertEquals(a1.hashCode(), a2.hashCode(), "Equal addresses should have same hashCode")
    }

    @Test
    fun toStringReturnsMultibaseString() {
        val data = "toString test".encodeToByteArray()
        val address = ContentAddress.compute(data, DigestAlg.SHA256)
        val multibase = address.toMultibaseString()
        assertEquals(multibase, address.toString())
    }
}
