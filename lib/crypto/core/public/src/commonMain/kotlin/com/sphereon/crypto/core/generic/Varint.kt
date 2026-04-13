/*
 * Copyright (c) 2026 Sphereon International B.V.
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

/**
 * Unsigned varint encoding/decoding per the multiformats spec.
 * Used by multicodec and multihash for self-describing binary formats.
 */
private const val VARINT_DATA_BITS = 7
private const val VARINT_DATA_MASK = 0x7F
private const val VARINT_CONTINUATION_BIT = 0x80
private const val BYTE_MASK = 0xFF
private const val MAX_VARINT_SHIFT = 35

object Varint {
    /**
     * Encode a non-negative integer as an unsigned varint byte array.
     * Each byte uses 7 data bits + 1 continuation bit (MSB).
     */
    fun encode(value: Int): ByteArray {
        require(value >= 0) { "Varint value must be non-negative: $value" }
        if (value == 0) {
            return byteArrayOf(0)
        }

        val bytes = mutableListOf<Byte>()
        var remaining = value
        while (remaining > 0) {
            var byte = remaining and VARINT_DATA_MASK
            remaining = remaining ushr VARINT_DATA_BITS
            if (remaining > 0) {
                byte = byte or VARINT_CONTINUATION_BIT
            }
            bytes.add(byte.toByte())
        }
        return bytes.toByteArray()
    }

    /**
     * Decode an unsigned varint from the beginning of a byte array.
     * @return Pair of (decoded value, number of bytes consumed)
     */
    fun decode(
        bytes: ByteArray,
        offset: Int = 0,
    ): Pair<Int, Int> {
        require(offset < bytes.size) { "Offset $offset beyond array size ${bytes.size}" }

        var result = 0
        var shift = 0
        var pos = offset
        while (pos < bytes.size) {
            val byte = bytes[pos].toInt() and BYTE_MASK
            result = result or ((byte and VARINT_DATA_MASK) shl shift)
            pos++
            if (byte and VARINT_CONTINUATION_BIT == 0) {
                return result to (pos - offset)
            }
            shift += VARINT_DATA_BITS
            require(shift < MAX_VARINT_SHIFT) { "Varint too long" }
        }
        throw IllegalArgumentException("Truncated varint at offset $offset")
    }
}
