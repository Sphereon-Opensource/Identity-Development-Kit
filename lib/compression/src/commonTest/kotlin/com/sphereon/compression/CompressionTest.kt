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
 */

package com.sphereon.compression

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompressionTest {
    private val samples: List<ByteArray> =
        listOf(
            ByteArray(0),
            byteArrayOf(0x00),
            "hello status list".encodeToByteArray(),
            ByteArray(131_072 / 8), // a fully-zero 131072-bit status-list bitstring (W3C minimum)
            ByteArray(20_000) { (it * 31 % 251).toByte() }, // high-entropy
        )

    @Test
    fun roundTripsAllAlgorithms() =
        runTest {
            for (algo in CompressionAlgorithm.entries) {
                for (sample in samples) {
                    val compressed = compress(sample, algo)
                    val restored = decompress(compressed, algo)
                    assertContentEquals(sample, restored, "round-trip failed for $algo, size=${sample.size}")
                }
            }
        }

    @Test
    fun mostlyZeroBitstringCompressesStrongly() =
        runTest {
            // The whole point of status lists: a 16 KB all-zero bitstring must shrink dramatically.
            val bitstring = ByteArray(131_072 / 8)
            val gzip = compress(bitstring, CompressionAlgorithm.GZIP)
            val zlib = compress(bitstring, CompressionAlgorithm.DEFLATE_ZLIB)
            assertTrue(gzip.size < 200, "GZIP of all-zero 16KB should be tiny, was ${gzip.size}")
            assertTrue(zlib.size < 200, "ZLIB of all-zero 16KB should be tiny, was ${zlib.size}")
        }

    @Test
    fun gzipEmitsCorrectMagicAndZlibHeader() =
        runTest {
            val gzip = compress("x".encodeToByteArray(), CompressionAlgorithm.GZIP)
            assertEquals(0x1F, gzip[0].toInt() and 0xFF)
            assertEquals(0x8B, gzip[1].toInt() and 0xFF)
            assertEquals(0x08, gzip[2].toInt() and 0xFF)

            val zlib = compress("x".encodeToByteArray(), CompressionAlgorithm.DEFLATE_ZLIB)
            val cmf = zlib[0].toInt() and 0xFF
            val flg = zlib[1].toInt() and 0xFF
            assertEquals(8, cmf and 0x0F, "ZLIB CM must be 8 (deflate)")
            assertEquals(0, ((cmf shl 8) or flg) % 31, "ZLIB header checksum must be valid")
        }

    @Test
    fun decompressRejectsCorruptTrailer() =
        runTest {
            val gzip = compress("payload".encodeToByteArray(), CompressionAlgorithm.GZIP).copyOf()
            gzip[gzip.size - 1] = (gzip[gzip.size - 1] + 1).toByte() // corrupt ISIZE
            assertFailsWith<IllegalArgumentException> { decompress(gzip, CompressionAlgorithm.GZIP) }
        }
}
