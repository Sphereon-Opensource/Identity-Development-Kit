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
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.Inflater
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * Cross-checks our hand-written GZIP/ZLIB framing against `java.util.zip` (a real, independent
 * implementation) — both directions — so the framing is proven spec-correct, not merely
 * self-consistent with our own decoder.
 */
class CompressionInteropTest {
    private val payload = ("the quick brown fox ".repeat(500)).encodeToByteArray()

    @Test
    fun ourGzipIsReadableByJava() =
        runTest {
            val ours = compress(payload, CompressionAlgorithm.GZIP)
            val viaJava = GZIPInputStream(ours.inputStream()).use { it.readBytes() }
            assertContentEquals(payload, viaJava)
        }

    @Test
    fun javaGzipIsReadableByUs() =
        runTest {
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos).use { it.write(payload) }
            val decoded = decompress(bos.toByteArray(), CompressionAlgorithm.GZIP)
            assertContentEquals(payload, decoded)
        }

    @Test
    fun ourZlibIsReadableByJavaInflater() =
        runTest {
            val ours = compress(payload, CompressionAlgorithm.DEFLATE_ZLIB)
            val inflater = Inflater() // zlib-wrapped (nowrap=false)
            inflater.setInput(ours)
            val out = ByteArray(payload.size + 64)
            val n = inflater.inflate(out)
            inflater.end()
            assertContentEquals(payload, out.copyOf(n))
        }
}
