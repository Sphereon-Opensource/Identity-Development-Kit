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

/** Reflected CRC-32 lookup table (polynomial 0xEDB88320), used by the GZIP trailer. */
private val CRC32_TABLE: IntArray =
    IntArray(256) { n ->
        var c = n
        repeat(8) {
            c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1
        }
        c
    }

/** CRC-32 (RFC 1952 / ISO 3309) of [data], returned as an unsigned value in `[0, 2^32)`. */
internal fun crc32(data: ByteArray): Long {
    var crc = -1 // 0xFFFFFFFF
    for (b in data) {
        crc = CRC32_TABLE[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
    }
    return crc.inv().toLong() and 0xFFFFFFFFL
}

/** Adler-32 (RFC 1950) of [data], returned as an unsigned value in `[0, 2^32)`. */
internal fun adler32(data: ByteArray): Long {
    val mod = 65521L
    var a = 1L
    var b = 0L
    var i = 0
    // RFC 1950 Appendix A: accumulate up to NMAX bytes before reducing, so the modulo runs once
    // per ~5.5 KB block instead of once per byte (no correctness change — sums can't overflow Long).
    while (i < data.size) {
        val end = minOf(i + ADLER_NMAX, data.size)
        while (i < end) {
            a += data[i].toInt() and 0xFF
            b += a
            i++
        }
        a %= mod
        b %= mod
    }
    return (b shl 16) or a
}

private const val ADLER_NMAX = 5552
