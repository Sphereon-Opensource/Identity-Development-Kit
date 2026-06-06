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

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/** JVM raw DEFLATE — `nowrap = true` suppresses the zlib header/trailer (RFC 1951). */
internal actual suspend fun rawDeflate(data: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
    return try {
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream(maxOf(64, data.size / 2))
        val buffer = ByteArray(BUFFER_SIZE)
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            out.write(buffer, 0, n)
        }
        out.toByteArray()
    } finally {
        deflater.end()
    }
}

internal actual suspend fun rawInflate(data: ByteArray): ByteArray {
    val inflater = Inflater(true)
    return try {
        inflater.setInput(data)
        val out = ByteArrayOutputStream(maxOf(64, data.size * 2))
        val buffer = ByteArray(BUFFER_SIZE)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n > 0) {
                out.write(buffer, 0, n)
                continue
            }
            // n == 0: the empty-payload stream finishes here with no output, so check finished()
            // before treating an exhausted input as a truncated stream.
            if (inflater.finished()) break
            if (inflater.needsInput() || inflater.needsDictionary()) {
                throw IllegalArgumentException("Truncated DEFLATE stream")
            }
            break
        }
        out.toByteArray()
    } finally {
        inflater.end()
    }
}

private const val BUFFER_SIZE = 16384
