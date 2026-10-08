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

package com.sphereon.crypto.jose.jwe.command

import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * JVM DEFLATE (RFC 1951) — note `nowrap = true`. By default `java.util.zip.Deflater` emits
 * ZLIB-format output (RFC 1950: 2-byte header + DEFLATE stream + Adler32 trailer). RFC 7516
 * §4.1.3 references *raw DEFLATE* (RFC 1951), so `nowrap=true` suppresses the zlib framing
 * and produces the bare DEFLATE bitstream the JWE spec mandates. Same flag on the Inflater
 * side keeps the round-trip consistent.
 */
internal actual suspend fun deflate(plaintext: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true) // nowrap
    return try {
        deflater.setInput(plaintext)
        deflater.finish()
        val buffer = ByteArray(BUFFER_SIZE)
        val output = mutableListOf<Byte>()
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            for (i in 0 until n) output.add(buffer[i])
        }
        output.toByteArray()
    } finally {
        deflater.end()
    }
}

internal actual suspend fun inflate(compressed: ByteArray): ByteArray {
    val inflater = Inflater(true) // nowrap
    return try {
        inflater.setInput(compressed)
        val buffer = ByteArray(BUFFER_SIZE)
        val output = mutableListOf<Byte>()
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) {
                    throw IllegalArgumentException("Truncated DEFLATE stream")
                }
                break
            }
            for (i in 0 until n) output.add(buffer[i])
        }
        output.toByteArray()
    } finally {
        inflater.end()
    }
}

private const val BUFFER_SIZE = 4096
