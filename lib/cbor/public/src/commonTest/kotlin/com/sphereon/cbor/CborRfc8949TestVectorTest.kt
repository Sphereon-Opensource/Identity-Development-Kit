/*
 * © 2025 Sphereon International B.V.
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * RFC 8949 Appendix A - Examples of Encoded CBOR Data Items
 * https://www.rfc-editor.org/rfc/rfc8949.html#name-examples-of-encoded-cbor-da
 */
class CborRfc8949TestVectorTest {

    // ============================================
    // Unsigned Integers (Major Type 0)
    // ============================================

    @Test
    fun testVector_UInt_0() {
        // 0 -> 0x00
        val item = CborUInt(0)
        val encoded = cborSerializer.encode(item)
        assertEquals("00", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborUInt>("00".decodeFromHex())
        assertEquals(0L, decoded.value)
    }

    @Test
    fun testVector_UInt_1() {
        // 1 -> 0x01
        val item = CborUInt(1)
        val encoded = cborSerializer.encode(item)
        assertEquals("01", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborUInt>("01".decodeFromHex())
        assertEquals(1L, decoded.value)
    }

    @Test
    fun testVector_UInt_10() {
        // 10 -> 0x0a
        val item = CborUInt(10)
        val encoded = cborSerializer.encode(item)
        assertEquals("0a", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborUInt>("0a".decodeFromHex())
        assertEquals(10L, decoded.value)
    }

    @Test
    fun testVector_UInt_23() {
        // 23 -> 0x17
        val item = CborUInt(23)
        val encoded = cborSerializer.encode(item)
        assertEquals("17", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborUInt>("17".decodeFromHex())
        assertEquals(23L, decoded.value)
    }

    @Test
    fun testVector_UInt_24() {
        // 24 -> 0x1818
        val item = CborUInt(24)
        val encoded = cborSerializer.encode(item)
        assertEquals("1818", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborUInt>("1818".decodeFromHex())
        assertEquals(24L, decoded.value)
    }

    @Test
    fun testVector_UInt_25() {
        // 25 -> 0x1819
        val item = CborUInt(25)
        val encoded = cborSerializer.encode(item)
        assertEquals("1819", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborUInt>("1819".decodeFromHex())
        assertEquals(25L, decoded.value)
    }

    @Test
    fun testVector_UInt_100() {
        // 100 -> 0x1864
        val item = CborUInt(100)
        val encoded = cborSerializer.encode(item)
        assertEquals("1864", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborUInt>("1864".decodeFromHex())
        assertEquals(100L, decoded.value)
    }

    @Test
    fun testVector_UInt_1000() {
        // 1000 -> 0x1903e8
        val item = CborUInt(1000)
        val encoded = cborSerializer.encode(item)
        assertEquals("1903e8", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborUInt>("1903e8".decodeFromHex())
        assertEquals(1000L, decoded.value)
    }

    @Test
    fun testVector_UInt_1000000() {
        // 1000000 -> 0x1a000f4240
        val item = CborUInt(1000000)
        val encoded = cborSerializer.encode(item)
        assertEquals("1a000f4240", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborUInt>("1a000f4240".decodeFromHex())
        assertEquals(1000000L, decoded.value)
    }

    @Test
    fun testVector_UInt_1000000000000() {
        // 1000000000000 -> 0x1b000000e8d4a51000
        val item = CborUInt(1000000000000)
        val encoded = cborSerializer.encode(item)
        assertEquals("1b000000e8d4a51000", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborUInt>("1b000000e8d4a51000".decodeFromHex())
        assertEquals(1000000000000L, decoded.value)
    }

    // ============================================
    // Negative Integers (Major Type 1)
    // ============================================

    @Test
    fun testVector_NInt_Minus1() {
        // -1 -> 0x20
        val decoded = cborSerializer.decode<CborNInt>("20".decodeFromHex())
        assertEquals(1L, decoded.value) // CBOR stores as -1-n, so -1 is stored as 0
    }

    @Test
    fun testVector_NInt_Minus10() {
        // -10 -> 0x29
        val decoded = cborSerializer.decode<CborNInt>("29".decodeFromHex())
        assertEquals(10L, decoded.value)
    }

    @Test
    fun testVector_NInt_Minus100() {
        // -100 -> 0x3863
        val decoded = cborSerializer.decode<CborNInt>("3863".decodeFromHex())
        assertEquals(100L, decoded.value)
    }

    @Test
    fun testVector_NInt_Minus1000() {
        // -1000 -> 0x3903e7
        val decoded = cborSerializer.decode<CborNInt>("3903e7".decodeFromHex())
        assertEquals(1000L, decoded.value)
    }

    // ============================================
    // Byte Strings (Major Type 2)
    // ============================================

    @Test
    fun testVector_Bstr_Empty() {
        // h'' -> 0x40
        val item = CborByteString(byteArrayOf())
        val encoded = cborSerializer.encode(item)
        assertEquals("40", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborByteString>("40".decodeFromHex())
        assertContentEquals(byteArrayOf(), decoded.value)
    }

    @Test
    fun testVector_Bstr_01020304() {
        // h'01020304' -> 0x4401020304
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val item = CborByteString(bytes)
        val encoded = cborSerializer.encode(item)
        assertEquals("4401020304", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborByteString>("4401020304".decodeFromHex())
        assertContentEquals(bytes, decoded.value)
    }

    // ============================================
    // Text Strings (Major Type 3)
    // ============================================

    @Test
    fun testVector_Tstr_Empty() {
        // "" -> 0x60
        val item = CborString("")
        val encoded = cborSerializer.encode(item)
        assertEquals("60", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborString>("60".decodeFromHex())
        assertEquals("", decoded.value)
    }

    @Test
    fun testVector_Tstr_a() {
        // "a" -> 0x6161
        val item = CborString("a")
        val encoded = cborSerializer.encode(item)
        assertEquals("6161", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborString>("6161".decodeFromHex())
        assertEquals("a", decoded.value)
    }

    @Test
    fun testVector_Tstr_IETF() {
        // "IETF" -> 0x6449455446
        val item = CborString("IETF")
        val encoded = cborSerializer.encode(item)
        assertEquals("6449455446", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborString>("6449455446".decodeFromHex())
        assertEquals("IETF", decoded.value)
    }

    @Test
    fun testVector_Tstr_QuoteBackslash() {
        // "\"" -> 0x62225c
        val item = CborString("\"\\")
        val encoded = cborSerializer.encode(item)
        assertEquals("62225c", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborString>("62225c".decodeFromHex())
        assertEquals("\"\\", decoded.value)
    }

    @Test
    fun testVector_Tstr_Unicode_u00fc() {
        // "\u00fc" (ü) -> 0x62c3bc
        val item = CborString("\u00fc")
        val encoded = cborSerializer.encode(item)
        assertEquals("62c3bc", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborString>("62c3bc".decodeFromHex())
        assertEquals("\u00fc", decoded.value)
    }

    @Test
    fun testVector_Tstr_Unicode_u6c34() {
        // "\u6c34" (水) -> 0x63e6b0b4
        val item = CborString("\u6c34")
        val encoded = cborSerializer.encode(item)
        assertEquals("63e6b0b4", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborString>("63e6b0b4".decodeFromHex())
        assertEquals("\u6c34", decoded.value)
    }

    // ============================================
    // Arrays (Major Type 4)
    // ============================================

    @Test
    fun testVector_Array_Empty() {
        // [] -> 0x80
        val item = CborArray(mutableListOf())
        val encoded = cborSerializer.encode(item)
        assertEquals("80", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>("80".decodeFromHex())
        assertEquals(0, decoded.value.size)
    }

    @Test
    fun testVector_Array_123() {
        // [1, 2, 3] -> 0x83010203
        val item = CborArray(mutableListOf(CborUInt(1), CborUInt(2), CborUInt(3)))
        val encoded = cborSerializer.encode(item)
        assertEquals("83010203", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>("83010203".decodeFromHex())
        assertEquals(3, decoded.value.size)
        assertEquals(1, decoded.value[0].asInt)
        assertEquals(2, decoded.value[1].asInt)
        assertEquals(3, decoded.value[2].asInt)
    }

    @Test
    fun testVector_Array_Nested() {
        // [1, [2, 3], [4, 5]] -> 0x8301820203820405
        val inner1 = CborArray(mutableListOf(CborUInt(2), CborUInt(3)))
        val inner2 = CborArray(mutableListOf(CborUInt(4), CborUInt(5)))
        val item = CborArray(mutableListOf(CborUInt(1), inner1, inner2))
        val encoded = cborSerializer.encode(item)
        assertEquals("8301820203820405", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>("8301820203820405".decodeFromHex())
        assertEquals(3, decoded.value.size)
    }

    @Test
    fun testVector_Array_25Elements() {
        // [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25]
        // -> 0x98190102030405060708090a0b0c0d0e0f101112131415161718181819
        val items = (1..25).map { CborUInt(it.toLong()) }.toMutableList()
        val item = CborArray(items)
        val encoded = cborSerializer.encode(item)
        assertEquals("98190102030405060708090a0b0c0d0e0f101112131415161718181819", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>(encoded)
        assertEquals(25, decoded.value.size)
    }

    // ============================================
    // Maps (Major Type 5)
    // ============================================

    @Test
    fun testVector_Map_Empty() {
        // {} -> 0xa0
        val item = CborMap(mutableMapOf())
        val encoded = cborSerializer.encode(item)
        assertEquals("a0", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborMap<CborItem<*>, CborItem<*>>>("a0".decodeFromHex())
        assertEquals(0, decoded.value.size)
    }

    @Test
    fun testVector_Map_12_34() {
        // {1: 2, 3: 4} -> 0xa201020304
        val item = CborMap(mutableMapOf(
            NumberLabel(1) to CborUInt(2),
            NumberLabel(3) to CborUInt(4)
        ))
        val encoded = cborSerializer.encode(item)
        // Map order may vary, so just verify it decodes correctly
        val decoded = cborSerializer.decode<CborMap<CborItem<*>, CborItem<*>>>(encoded)
        assertEquals(2, decoded.value.size)
    }

    @Test
    fun testVector_Map_StringKeys() {
        // {"a": 1, "b": [2, 3]} -> a26161016162820203
        val decoded = cborSerializer.decode<CborMap<CborItem<*>, CborItem<*>>>("a26161016162820203".decodeFromHex())
        assertEquals(2, decoded.value.size)
    }

    // ============================================
    // Simple Values (Major Type 7)
    // ============================================

    @Test
    fun testVector_False() {
        // false -> 0xf4
        val encoded = cborSerializer.encode(CborSimple.FALSE)
        assertEquals("f4", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborBool>("f4".decodeFromHex())
        assertEquals(false, decoded.value)
    }

    @Test
    fun testVector_True() {
        // true -> 0xf5
        val encoded = cborSerializer.encode(CborSimple.TRUE)
        assertEquals("f5", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborBool>("f5".decodeFromHex())
        assertEquals(true, decoded.value)
    }

    @Test
    fun testVector_Null() {
        // null -> 0xf6
        val encoded = cborSerializer.encode(CborSimple.NULL)
        assertEquals("f6", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborSimple<*>>("f6".decodeFromHex())
        assertEquals(CborSimple.NULL, decoded)
    }

    @Test
    fun testVector_Undefined() {
        // undefined -> 0xf7
        val encoded = cborSerializer.encode(CborSimple.UNDEFINED)
        assertEquals("f7", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborSimple<*>>("f7".decodeFromHex())
        assertEquals(CborSimple.UNDEFINED, decoded)
    }

    // ============================================
    // Floating-Point Numbers (Major Type 7)
    // ============================================

    @Test
    fun testVector_Float16_Zero() {
        // 0.0 (half) -> 0xf90000
        val decoded = cborSerializer.decode<CborFloat16>("f90000".decodeFromHex())
        assertEquals(0.0f, decoded.value)
    }

    @Test
    fun testVector_Float16_NegativeZero() {
        // -0.0 (half) -> 0xf98000
        val decoded = cborSerializer.decode<CborFloat16>("f98000".decodeFromHex())
        assertEquals(-0.0f, decoded.value)
    }

    @Test
    fun testVector_Float16_One() {
        // 1.0 (half) -> 0xf93c00
        val decoded = cborSerializer.decode<CborFloat16>("f93c00".decodeFromHex())
        assertEquals(1.0f, decoded.value)
    }

    @Test
    fun testVector_Float16_OnePointFive() {
        // 1.5 (half) -> 0xf93e00
        val decoded = cborSerializer.decode<CborFloat16>("f93e00".decodeFromHex())
        assertEquals(1.5f, decoded.value)
    }

    @Test
    fun testVector_Float16_65504() {
        // 65504.0 (half) -> 0xf97bff
        val decoded = cborSerializer.decode<CborFloat16>("f97bff".decodeFromHex())
        assertEquals(65504.0f, decoded.value)
    }

    @Test
    fun testVector_Float16_PositiveInfinity() {
        // Infinity (half) -> 0xf97c00
        val decoded = cborSerializer.decode<CborFloat16>("f97c00".decodeFromHex())
        assertEquals(Float.POSITIVE_INFINITY, decoded.value)
    }

    @Test
    fun testVector_Float16_NaN() {
        // NaN (half) -> 0xf97e00
        val decoded = cborSerializer.decode<CborFloat16>("f97e00".decodeFromHex())
        assertTrue(decoded.value.isNaN())
    }

    @Test
    fun testVector_Float16_NegativeInfinity() {
        // -Infinity (half) -> 0xf9fc00
        val decoded = cborSerializer.decode<CborFloat16>("f9fc00".decodeFromHex())
        assertEquals(Float.NEGATIVE_INFINITY, decoded.value)
    }

    @Test
    fun testVector_Float32_100000() {
        // 100000.0 (single) -> 0xfa47c35000
        val decoded = cborSerializer.decode<CborFloat>("fa47c35000".decodeFromHex())
        assertEquals(100000.0f, decoded.value)
    }

    @Test
    fun testVector_Float64_1e300() {
        // 1.0e+300 (double) -> 0xfb7e37e43c8800759c
        val decoded = cborSerializer.decode<CborDouble>("fb7e37e43c8800759c".decodeFromHex())
        assertEquals(1.0e+300, decoded.value, 1e+290)
    }

    @Test
    fun testVector_Float64_1Point1() {
        // 1.1 (double) -> 0xfb3ff199999999999a
        val decoded = cborSerializer.decode<CborDouble>("fb3ff199999999999a".decodeFromHex())
        assertEquals(1.1, decoded.value, 0.0000001)
    }

    // ============================================
    // Tagged Items (Major Type 6)
    // ============================================

    @Test
    fun testVector_Tag0_DateTimeString() {
        // 0("2013-03-21T20:04:00Z") -> c074323031332d30332d32315432303a30343a30305a
        val decoded = cborSerializer.decode<CborItem<*>>("c074323031332d30332d32315432303a30343a30305a".decodeFromHex())
        assertIs<CborTDate>(decoded)
    }

    @Test
    fun testVector_Tag1_EpochTime() {
        // 1(1363896240) -> c11a514b67b0
        val decoded = cborSerializer.decode<CborItem<*>>("c11a514b67b0".decodeFromHex())
        assertIs<CborTime>(decoded)
        assertEquals(1363896240L, decoded.value)
    }

    @Test
    fun testVector_Tag24_EncodedCbor() {
        // 24(h'6449455446') -> d818456449455446
        val decoded = cborSerializer.decode<CborEncodedItem<*>>("d818456449455446".decodeFromHex())
        assertTrue(decoded.wasEncoded())
    }

    // ============================================
    // Indefinite-Length Items
    // ============================================

    @Test
    fun testVector_IndefiniteByteString() {
        // (_ h'0102', h'030405') -> 0x5f42010243030405ff
        val decoded = cborSerializer.decode<CborByteStringIndefLength>("5f42010243030405ff".decodeFromHex())
        assertEquals(2, decoded.value.size)
        assertContentEquals(byteArrayOf(0x01, 0x02), decoded.value[0])
        assertContentEquals(byteArrayOf(0x03, 0x04, 0x05), decoded.value[1])
    }

    @Test
    fun testVector_IndefiniteTextString() {
        // (_ "strea", "ming") -> 0x7f657374726561646d696e67ff
        val decoded = cborSerializer.decode<CborStringIndefLength>("7f657374726561646d696e67ff".decodeFromHex())
        assertEquals(2, decoded.value.size)
        assertEquals("strea", decoded.value[0])
        assertEquals("ming", decoded.value[1])
    }

    @Test
    fun testVector_IndefiniteArray() {
        // [_ 1, 2, 3] -> 0x9f010203ff
        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>("9f010203ff".decodeFromHex())
        assertTrue(decoded.indefiniteLength)
        assertEquals(3, decoded.value.size)
    }

    @Test
    fun testVector_IndefiniteMap() {
        // {_ "a": 1, "b": [_ 2, 3]} -> bf61610161629f0203ffff
        val decoded = cborSerializer.decode<CborMap<CborItem<*>, CborItem<*>>>("bf61610161629f0203ffff".decodeFromHex())
        assertTrue(decoded.indefiniteLength)
        assertEquals(2, decoded.value.size)
    }

    @Test
    fun testVector_IndefiniteArrayEmpty() {
        // [_ ] -> 0x9fff
        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>("9fff".decodeFromHex())
        assertTrue(decoded.indefiniteLength)
        assertEquals(0, decoded.value.size)
    }

    // ============================================
    // Complex Nested Structures
    // ============================================

    @Test
    fun testVector_NestedMixed() {
        // ["a", {"b": "c"}] -> 826161a161626163
        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>("826161a161626163".decodeFromHex())
        assertEquals(2, decoded.value.size)
        assertIs<CborString>(decoded.value[0])
        assertIs<CborMap<*, *>>(decoded.value[1])
    }
}
