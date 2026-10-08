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

package com.sphereon.crypto.core.generic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MultihashTest {
    @Test
    fun encodeSha256() {
        val digest = ByteArray(32) { it.toByte() }
        val multihash = MultihashCodec.encode(digest, MultihashAlgorithm.SHA2_256)

        // First byte: 0x12 (sha2-256 code)
        assertEquals(0x12, multihash[0].toInt() and 0xFF)
        // Second byte: 0x20 (32 = digest length)
        assertEquals(0x20, multihash[1].toInt() and 0xFF)
        // Total: 1 (code) + 1 (length) + 32 (digest) = 34
        assertEquals(34, multihash.size)
    }

    @Test
    fun roundTripAllAlgorithms() {
        for (algo in MultihashAlgorithm.entries) {
            val digest = ByteArray(algo.digestLength) { (it % 256).toByte() }
            val encoded = MultihashCodec.encode(digest, algo)
            val (decodedAlgo, decodedDigest) = MultihashCodec.decode(encoded)
            assertEquals(algo, decodedAlgo, "Algorithm mismatch for ${algo.name}")
            assertEquals(digest.toList(), decodedDigest.toList(), "Digest mismatch for ${algo.name}")
        }
    }

    @Test
    fun isMultihash() {
        val digest = ByteArray(32) { 0 }
        val multihash = MultihashCodec.encode(digest, MultihashAlgorithm.SHA2_256)
        assertTrue(MultihashCodec.isMultihash(multihash))
        assertTrue(!MultihashCodec.isMultihash(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun multibaseRoundTrip() {
        val digest = ByteArray(32) { (it * 7).toByte() }
        for (base in MultibaseEncoding.entries) {
            val encoded = MultihashCodec.encodeToMultibase(digest, MultihashAlgorithm.SHA2_256, base)
            val (algo, decoded) = MultihashCodec.decodeFromMultibase(encoded)
            assertEquals(MultihashAlgorithm.SHA2_256, algo)
            assertEquals(digest.toList(), decoded.toList(), "Multibase round-trip failed for $base")
        }
    }

    @Test
    fun rejectsWrongDigestLength() {
        val wrongSize = ByteArray(16) { 0 }
        assertFailsWith<IllegalArgumentException> {
            MultihashCodec.encode(wrongSize, MultihashAlgorithm.SHA2_256)
        }
    }

    @Test
    fun fromPlainHex() {
        val hexDigest = "a" + "b".repeat(63) // 32 bytes in hex
        val multihash = MultihashCodec.fromPlainHex(hexDigest, MultihashAlgorithm.SHA2_256)
        val (algo, digest) = MultihashCodec.decode(multihash)
        assertEquals(MultihashAlgorithm.SHA2_256, algo)
        assertEquals(32, digest.size)
    }

    @Test
    fun lookupByDigestAlg() {
        assertEquals(MultihashAlgorithm.SHA2_256, MultihashAlgorithm.fromDigestAlg(DigestAlg.SHA256))
        assertEquals(MultihashAlgorithm.SHA2_512, MultihashAlgorithm.fromDigestAlg(DigestAlg.SHA512))
        assertEquals(MultihashAlgorithm.SHA3_256, MultihashAlgorithm.fromDigestAlg(DigestAlg.SHA3_256))
    }
}
