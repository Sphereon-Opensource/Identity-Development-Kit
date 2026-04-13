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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CborIntegerTest {

    // RFC 8949 test vectors for unsigned integers
    @Test
    fun testUIntZero() {
        val encoded = cborSerializer.encode(CborUInt(0))
        assertEquals("00", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborUInt>(encoded)
        assertEquals(0L, decoded.value)
    }

    @Test
    fun testUIntOne() {
        val encoded = cborSerializer.encode(CborUInt(1))
        assertEquals("01", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborUInt>(encoded)
        assertEquals(1L, decoded.value)
    }

    @Test
    fun testUIntTen() {
        val encoded = cborSerializer.encode(CborUInt(10))
        assertEquals("0a", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborUInt>(encoded)
        assertEquals(10L, decoded.value)
    }

    @Test
    fun testUIntTwentyThree() {
        val encoded = cborSerializer.encode(CborUInt(23))
        assertEquals("17", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborUInt>(encoded)
        assertEquals(23L, decoded.value)
    }

    @Test
    fun testUIntTwentyFour() {
        val encoded = cborSerializer.encode(CborUInt(24))
        assertEquals("1818", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborUInt>(encoded)
        assertEquals(24L, decoded.value)
    }

    @Test
    fun testUIntTwentyFive() {
        val encoded = cborSerializer.encode(CborUInt(25))
        assertEquals("1819", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborUInt>(encoded)
        assertEquals(25L, decoded.value)
    }

    @Test
    fun testUIntHundred() {
        val encoded = cborSerializer.encode(CborUInt(100))
        assertEquals("1864", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborUInt>(encoded)
        assertEquals(100L, decoded.value)
    }

    @Test
    fun testUIntThousand() {
        val encoded = cborSerializer.encode(CborUInt(1000))
        assertEquals("1903e8", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborUInt>(encoded)
        assertEquals(1000L, decoded.value)
    }

    @Test
    fun testUIntMillion() {
        val encoded = cborSerializer.encode(CborUInt(1000000))
        assertEquals("1a000f4240", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborUInt>(encoded)
        assertEquals(1000000L, decoded.value)
    }

    @Test
    fun testUIntLargeValue() {
        val encoded = cborSerializer.encode(CborUInt(1000000000000))
        assertEquals("1b000000e8d4a51000", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborUInt>(encoded)
        assertEquals(1000000000000L, decoded.value)
    }

    @Test
    fun testUInt255() {
        val encoded = cborSerializer.encode(CborUInt(255))
        assertEquals("18ff", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborUInt>(encoded)
        assertEquals(255L, decoded.value)
    }

    @Test
    fun testUInt256() {
        val encoded = cborSerializer.encode(CborUInt(256))
        assertEquals("190100", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborUInt>(encoded)
        assertEquals(256L, decoded.value)
    }

    @Test
    fun testUInt65535() {
        val encoded = cborSerializer.encode(CborUInt(65535))
        assertEquals("19ffff", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborUInt>(encoded)
        assertEquals(65535L, decoded.value)
    }

    @Test
    fun testUInt65536() {
        val encoded = cborSerializer.encode(CborUInt(65536))
        assertEquals("1a00010000", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborUInt>(encoded)
        assertEquals(65536L, decoded.value)
    }

    @Test
    fun testUIntFromNumber() {
        val uint = CborUInt(42 as Number)
        assertEquals(42L, uint.value)
    }

    // RFC 8949 test vectors for negative integers
    @Test
    fun testNIntMinusOne() {
        val encoded = cborSerializer.encode(CborNInt(1))
        assertEquals("20", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborNInt>(encoded)
        assertEquals(1L, decoded.value)
    }

    @Test
    fun testNIntMinusTen() {
        val encoded = cborSerializer.encode(CborNInt(10))
        assertEquals("29", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborNInt>(encoded)
        assertEquals(10L, decoded.value)
    }

    @Test
    fun testNIntMinusHundred() {
        val encoded = cborSerializer.encode(CborNInt(100))
        assertEquals("3863", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborNInt>(encoded)
        assertEquals(100L, decoded.value)
    }

    @Test
    fun testNIntMinusThousand() {
        val encoded = cborSerializer.encode(CborNInt(1000))
        assertEquals("3903e7", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborNInt>(encoded)
        assertEquals(1000L, decoded.value)
    }

    // CDDL int type tests (auto-selects uint or nint)
    @Test
    fun testCDDLIntPositive() {
        val item = CDDL.int.newInt(42)
        assertIs<CborUInt>(item)
        assertEquals(42L, item.value)
    }

    @Test
    fun testCDDLIntNegative() {
        val item = CDDL.int.newInt(-42)
        assertIs<CborNInt>(item)
        assertEquals(-42L, item.value)
    }

    @Test
    fun testCDDLIntZero() {
        val item = CDDL.int.newInt(0)
        assertIs<CborUInt>(item)
        assertEquals(0L, item.value)
    }

    @Test
    fun testCDDLLongPositive() {
        val item = CDDL.int.newLong(1000000000000L)
        assertIs<CborUInt>(item)
        assertEquals(1000000000000L, item.value)
    }

    @Test
    fun testCDDLLongNegative() {
        val item = CDDL.int.newLong(-1000000000000L)
        assertIs<CborNInt>(item)
        assertEquals(-1000000000000L, item.value)
    }

    // CborItem accessor tests
    @Test
    fun testAsInt() {
        val uint = CborUInt(42)
        assertEquals(42, uint.asInt)

        val nint = CborNInt(42)
        assertEquals(-42, nint.asInt)
    }

    @Test
    fun testAsLong() {
        val uint = CborUInt(1000000000000L)
        assertEquals(1000000000000L, uint.asLong)

        val nint = CborNInt(1000000000000L)
        assertEquals(-1000000000000L, nint.asLong)
    }

    @Test
    fun testAsIntOverflow() {
        val uint = CborUInt(Long.MAX_VALUE)
        assertFailsWith<IllegalArgumentException> {
            uint.asInt
        }
    }

    // Extension function tests
    @Test
    fun testToCborUInt() {
        val uint = 42L.toCborUInt()
        assertEquals(42L, uint.value)
    }

    @Test
    fun testToUInt() {
        val uint = CborUInt(42)
        assertEquals(42u, uint.toUInt())
    }

    @Test
    fun testToCborUIntFromUint() {
        val uint = 42u.toCborUIntFromUint()
        assertEquals(42L, uint.value)
    }

    // Major type tests
    @Test
    fun testUIntMajorType() {
        val uint = CborUInt(42)
        assertEquals(MajorType.UNSIGNED_INTEGER, uint.majorType)
    }

    @Test
    fun testNIntMajorType() {
        val nint = CborNInt(42)
        assertEquals(MajorType.NEGATIVE_INTEGER, nint.majorType)
    }

    // Equality tests
    @Test
    fun testUIntEquality() {
        val uint1 = CborUInt(42)
        val uint2 = CborUInt(42)
        val uint3 = CborUInt(43)

        assertEquals(uint1, uint2)
        assertNotEquals(uint1, uint3)
    }

    @Test
    fun testNIntEquality() {
        val nint1 = CborNInt(42)
        val nint2 = CborNInt(42)
        val nint3 = CborNInt(43)

        assertEquals(nint1, nint2)
        assertNotEquals(nint1, nint3)
    }

    @Test
    fun testHashCode() {
        val uint1 = CborUInt(42)
        val uint2 = CborUInt(42)

        assertEquals(uint1.hashCode(), uint2.hashCode())
    }

    // Round-trip encoding tests
    @Test
    fun testRoundTripEncoding() {
        val testValues = listOf(0L, 1L, 23L, 24L, 255L, 256L, 65535L, 65536L, 1000000L, Long.MAX_VALUE / 2)

        for (value in testValues) {
            val uint = CborUInt(value)
            val encoded = cborSerializer.encode(uint)
            val decoded = cborSerializer.decode<CborUInt>(encoded)
            assertEquals(value, decoded.value, "Round-trip failed for value: $value")
        }
    }

    // Decode from known test vectors
    @Test
    fun testDecodeKnownVectors() {
        // 0
        assertEquals(0L, cborSerializer.decode<CborUInt>("00".decodeFromHex()).value)
        // 1
        assertEquals(1L, cborSerializer.decode<CborUInt>("01".decodeFromHex()).value)
        // 10
        assertEquals(10L, cborSerializer.decode<CborUInt>("0a".decodeFromHex()).value)
        // 23
        assertEquals(23L, cborSerializer.decode<CborUInt>("17".decodeFromHex()).value)
        // 24
        assertEquals(24L, cborSerializer.decode<CborUInt>("1818".decodeFromHex()).value)
        // 100
        assertEquals(100L, cborSerializer.decode<CborUInt>("1864".decodeFromHex()).value)
        // 1000
        assertEquals(1000L, cborSerializer.decode<CborUInt>("1903e8".decodeFromHex()).value)
    }

    @Test
    fun testDecodeNIntKnownVectors() {
        // -1
        assertEquals(1L, cborSerializer.decode<CborNInt>("20".decodeFromHex()).value)
        // -10
        assertEquals(10L, cborSerializer.decode<CborNInt>("29".decodeFromHex()).value)
        // -100
        assertEquals(100L, cborSerializer.decode<CborNInt>("3863".decodeFromHex()).value)
        // -1000
        assertEquals(1000L, cborSerializer.decode<CborNInt>("3903e7".decodeFromHex()).value)
    }

    // Error handling tests
    @Test
    fun testInvalidAdditionalInfo() {
        // Additional info 31 is not allowed for unsigned integer
        assertFailsWith<IllegalArgumentException> {
            cborSerializer.decode<CborUInt>("1f".decodeFromHex())
        }
    }

    @Test
    fun testTruncatedData() {
        // 2-byte integer with missing second byte
        assertFailsWith<IllegalArgumentException> {
            cborSerializer.decode<CborUInt>("18".decodeFromHex())
        }
    }

    // JSON conversion tests
    @Test
    fun testToJsonSimple() {
        val uint = CborUInt(42)
        val json = uint.toJsonSimple()
        assertEquals("42", json.toString())
    }

    @Test
    fun testToJsonWithCDDL() {
        val uint = CborUInt(42)
        val json = uint.toJsonWithCDDL()
        assertTrue(json.toString().contains("uint"))
        assertTrue(json.toString().contains("42"))
    }

    // toString tests
    @Test
    fun testToStringUInt() {
        val uint = CborUInt(42)
        val str = uint.toString()
        assertTrue(str.contains("42"))
    }

    // toCborItem conversion tests
    @Test
    fun testIntToCborItem() {
        val item = 42.toCborItem()
        assertIs<CborUInt>(item)
        assertEquals(42L, (item as CborUInt).value)
    }

    @Test
    fun testLongToCborItem() {
        val item = 1000000000000L.toCborItem()
        assertIs<CborUInt>(item)
        assertEquals(1000000000000L, (item as CborUInt).value)
    }

    @Test
    fun testByteToCborItem() {
        val item = (42.toByte()).toCborItem()
        assertIs<CborUInt>(item)
        assertEquals(42L, (item as CborUInt).value)
    }

    @Test
    fun testShortToCborItem() {
        val item = (1000.toShort()).toCborItem()
        assertIs<CborUInt>(item)
        assertEquals(1000L, (item as CborUInt).value)
    }

    @Test
    fun testUIntToCborItem() {
        val item = 42u.toCborItem()
        assertIs<CborUInt>(item)
        assertEquals(42L, (item as CborUInt).value)
    }

    // encodeCbor convenience method
    @Test
    fun testEncodeCbor() {
        val uint = CborUInt(42)
        val encoded = uint.encodeCbor()
        assertEquals("182a", encoded.encodeToHex())
    }
}
