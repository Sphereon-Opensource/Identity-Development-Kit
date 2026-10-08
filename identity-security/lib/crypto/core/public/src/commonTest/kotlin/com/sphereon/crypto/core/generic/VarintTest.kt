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

class VarintTest {
    @Test
    fun encodeSingleByte() {
        assertEquals(listOf<Byte>(0), Varint.encode(0).toList())
        assertEquals(listOf<Byte>(1), Varint.encode(1).toList())
        assertEquals(listOf<Byte>(0x12), Varint.encode(0x12).toList())
        assertEquals(listOf<Byte>(127), Varint.encode(127).toList())
    }

    @Test
    fun encodeMultiByte() {
        // 128 = 0x80 → varint: [0x80, 0x01]
        val encoded128 = Varint.encode(128)
        assertEquals(2, encoded128.size)
        assertEquals(0x80.toByte(), encoded128[0])
        assertEquals(0x01.toByte(), encoded128[1])

        // 300 = 0x012C → varint: [0xAC, 0x02]
        val encoded300 = Varint.encode(300)
        assertEquals(2, encoded300.size)
    }

    @Test
    fun roundTrip() {
        val values = listOf(0, 1, 18, 127, 128, 255, 256, 300, 1000, 16384, 65535)
        for (value in values) {
            val encoded = Varint.encode(value)
            val (decoded, bytesConsumed) = Varint.decode(encoded)
            assertEquals(value, decoded, "Round-trip failed for $value")
            assertEquals(encoded.size, bytesConsumed)
        }
    }

    @Test
    fun decodeWithOffset() {
        val prefix = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val varint = Varint.encode(0x12)
        val combined = prefix + varint
        val (decoded, consumed) = Varint.decode(combined, 2)
        assertEquals(0x12, decoded)
        assertEquals(1, consumed)
    }

    @Test
    fun rejectsNegative() {
        assertFailsWith<IllegalArgumentException> { Varint.encode(-1) }
    }
}
