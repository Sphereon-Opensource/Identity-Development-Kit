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

package com.sphereon.cbor

import com.sphereon.core.api.decodeFromHex
import com.sphereon.core.api.encodeToHex
import kotlin.test.Test
import kotlin.test.assertEquals

class StringTest {
    @Test
    fun testMultipleSimpleBytestrings() {
        val emptyHex = "40"
        val emptyByteString = CborByteString(byteArrayOf())
        assertEquals(emptyByteString, Cbor.decode(emptyHex.decodeFromHex()))
        assertEquals(emptyHex, Cbor.encode(emptyByteString).encodeToHex())

        val shortByteStringHex = "41ff"
        val shortByteString = CborByteString(byteArrayOf(0xFF.toByte()))
        assertEquals(shortByteString, Cbor.decode(shortByteStringHex.decodeFromHex()))
        assertEquals(shortByteStringHex, Cbor.encode(shortByteString).encodeToHex())

        val oneTo0a = "4a0102030405060708090a"
        val oneTo0aByteString = CborByteString(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a))
        assertEquals(oneTo0aByteString, Cbor.decode(oneTo0a.decodeFromHex()))
        assertEquals(oneTo0a, Cbor.encode(oneTo0aByteString).encodeToHex())

        val indefiniteByteStringHex = "5f42dead41beff"
        val byteString = Cbor.decode<CborByteStringIndefLength>(indefiniteByteStringHex.decodeFromHex())
        assertEquals(indefiniteByteStringHex, Cbor.encode(byteString).encodeToHex())
        assertEquals(2, byteString.value.size)
        assertEquals("dead", byteString.value[0].encodeToHex())
        assertEquals("be", byteString.value[1].encodeToHex())
    }
}
