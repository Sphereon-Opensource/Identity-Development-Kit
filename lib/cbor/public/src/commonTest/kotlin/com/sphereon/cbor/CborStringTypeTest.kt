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

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFromHex
import com.sphereon.core.api.encodeToHex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CborStringTypeTest {

    // RFC 8949 test vectors for text strings
    @Test
    fun testEmptyString() {
        val encoded = cborSerializer.encode(CborString(""))
        assertEquals("60", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborString>(encoded)
        assertEquals("", decoded.value)
    }

    @Test
    fun testSingleCharString() {
        val encoded = cborSerializer.encode(CborString("a"))
        assertEquals("6161", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborString>(encoded)
        assertEquals("a", decoded.value)
    }

    @Test
    fun testIETF() {
        val encoded = cborSerializer.encode(CborString("IETF"))
        assertEquals("6449455446", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborString>(encoded)
        assertEquals("IETF", decoded.value)
    }

    @Test
    fun testEscapeQuote() {
        val encoded = cborSerializer.encode(CborString("\"\\"))
        assertEquals("62225c", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborString>(encoded)
        assertEquals("\"\\", decoded.value)
    }

    @Test
    fun testUnicodeU00FC() {
        val encoded = cborSerializer.encode(CborString("\u00fc"))
        assertEquals("62c3bc", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborString>(encoded)
        assertEquals("\u00fc", decoded.value)
    }

    @Test
    fun testUnicodeU6C34() {
        val encoded = cborSerializer.encode(CborString("\u6c34"))
        assertEquals("63e6b0b4", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborString>(encoded)
        assertEquals("\u6c34", decoded.value)
    }

    @Test
    fun testLongerString() {
        val longString = "Hello, World! This is a test string."
        val encoded = cborSerializer.encode(CborString(longString))
        val decoded = cborSerializer.decode<CborString>(encoded)
        assertEquals(longString, decoded.value)
    }

    @Test
    fun testStringWith256Characters() {
        val string256 = "a".repeat(256)
        val encoded = cborSerializer.encode(CborString(string256))
        val decoded = cborSerializer.decode<CborString>(encoded)
        assertEquals(string256, decoded.value)
    }

    // RFC 8949 test vectors for byte strings
    @Test
    fun testEmptyByteString() {
        val encoded = cborSerializer.encode(CborByteString(byteArrayOf()))
        assertEquals("40", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborByteString>(encoded)
        assertContentEquals(byteArrayOf(), decoded.value)
    }

    @Test
    fun testByteString01020304() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val encoded = cborSerializer.encode(CborByteString(bytes))
        assertEquals("4401020304", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborByteString>(encoded)
        assertContentEquals(bytes, decoded.value)
    }

    @Test
    fun testByteStringFF() {
        val bytes = byteArrayOf(0xFF.toByte())
        val encoded = cborSerializer.encode(CborByteString(bytes))
        assertEquals("41ff", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborByteString>(encoded)
        assertContentEquals(bytes, decoded.value)
    }

    @Test
    fun testLongByteString() {
        val bytes = ByteArray(256) { it.toByte() }
        val encoded = cborSerializer.encode(CborByteString(bytes))
        val decoded = cborSerializer.decode<CborByteString>(encoded)
        assertContentEquals(bytes, decoded.value)
    }

    // Indefinite length byte string tests
    @Test
    fun testIndefiniteLengthByteString() {
        val hex = "5f42dead41beff"
        val decoded = cborSerializer.decode<CborByteStringIndefLength>(hex.decodeFromHex())
        assertEquals(2, decoded.value.size)
        assertEquals("dead", decoded.value[0].encodeToHex())
        assertEquals("be", decoded.value[1].encodeToHex())

        val reEncoded = cborSerializer.encode(decoded)
        assertEquals(hex, reEncoded.encodeToHex())
    }

    @Test
    fun testIndefiniteLengthByteStringRoundTrip() {
        val chunks = listOf(
            byteArrayOf(0x01, 0x02),
            byteArrayOf(0x03, 0x04, 0x05)
        )
        val item = CborByteStringIndefLength(chunks)
        val encoded = cborSerializer.encode(item)
        val decoded = cborSerializer.decode<CborByteStringIndefLength>(encoded)

        assertEquals(2, decoded.value.size)
        assertContentEquals(chunks[0], decoded.value[0])
        assertContentEquals(chunks[1], decoded.value[1])
    }

    // Indefinite length text string tests
    @Test
    fun testIndefiniteLengthTextString() {
        val chunks = listOf("Hello", ", ", "World!")
        val item = CborStringIndefLength(chunks)
        val encoded = cborSerializer.encode(item)
        val decoded = cborSerializer.decode<CborStringIndefLength>(encoded)

        assertEquals(3, decoded.value.size)
        assertEquals("Hello", decoded.value[0])
        assertEquals(", ", decoded.value[1])
        assertEquals("World!", decoded.value[2])
    }

    // Extension function tests
    @Test
    fun testToCborString() {
        val str = "test"
        val item = str.toCborString()
        assertEquals("test", item.value)
    }

    @Test
    fun testToCborByteString() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        val item = bytes.toCborByteString()
        assertContentEquals(bytes, item.value)
    }

    @Test
    fun testToCborStringArray() {
        val strings = arrayOf("a", "b", "c")
        val array = strings.toCborStringArray()
        assertEquals(3, array.value.size)
        assertEquals("a", array.value[0].value)
        assertEquals("b", array.value[1].value)
        assertEquals("c", array.value[2].value)
    }

    @Test
    fun testToStringArray() {
        val cborStrings = CborArray(mutableListOf(CborString("a"), CborString("b")))
        val strings = cborStrings.toStringArray()
        assertContentEquals(arrayOf("a", "b"), strings)
    }

    // String to byte string conversion with encoding detection
    @Test
    fun testStringToCborByteStringHex() {
        val hexString = "deadbeef"
        val item = hexString.toCborByteString(Encoding.HEX)
        assertContentEquals(hexString.decodeFromHex(), item.value)
    }

    @Test
    fun testStringToCborByteStringBase64() {
        val base64String = "SGVsbG8="
        val item = base64String.toCborByteString(Encoding.BASE64)
        assertEquals("Hello", item.value.decodeToString())
    }

    @Test
    fun testStringToCborByteStringBase64Url() {
        val base64UrlString = "SGVsbG8"
        val item = base64UrlString.toCborByteString(Encoding.BASE64URL)
        assertEquals("Hello", item.value.decodeToString())
    }

    @Test
    fun testStringToCborByteStringAutoDetectHex() {
        val hexString = "deadbeef"
        val item = hexString.toCborByteString()
        assertContentEquals(hexString.decodeFromHex(), item.value)
    }

    // CborByteString methods
    @Test
    fun testCborByteStringFromCborItem() {
        val uint = CborUInt(42)
        val byteString = CborByteString.fromCborItem(uint)
        val decoded = cborSerializer.decode<CborUInt>(byteString.value)
        assertEquals(42L, decoded.value)
    }

    @Test
    fun testCborDecodeValue() {
        val uint = CborUInt(42)
        val byteString = CborByteString(cborSerializer.encode(uint))
        val decoded = byteString.cborDecodeValue<CborUInt>()
        assertEquals(42L, decoded.value)
    }

    @Test
    fun testEncodeValueTo() {
        val bytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val byteString = CborByteString(bytes)
        assertEquals("deadbeef", byteString.encodeValueTo(Encoding.HEX))
    }

    // Major type tests
    @Test
    fun testStringMajorType() {
        val str = CborString("test")
        assertEquals(MajorType.UNICODE_STRING, str.majorType)
    }

    @Test
    fun testByteStringMajorType() {
        val bytes = CborByteString(byteArrayOf(0x01))
        assertEquals(MajorType.BYTE_STRING, bytes.majorType)
    }

    // Equality tests
    @Test
    fun testStringEquality() {
        val str1 = CborString("test")
        val str2 = CborString("test")
        val str3 = CborString("other")

        assertEquals(str1, str2)
        assertNotEquals(str1, str3)
    }

    @Test
    fun testByteStringEquality() {
        val bytes1 = CborByteString(byteArrayOf(0x01, 0x02))
        val bytes2 = CborByteString(byteArrayOf(0x01, 0x02))
        val bytes3 = CborByteString(byteArrayOf(0x01, 0x03))

        assertEquals(bytes1, bytes2)
        assertNotEquals(bytes1, bytes3)
    }

    @Test
    fun testStringHashCode() {
        val str1 = CborString("test")
        val str2 = CborString("test")
        assertEquals(str1.hashCode(), str2.hashCode())
    }

    @Test
    fun testByteStringHashCode() {
        val bytes1 = CborByteString(byteArrayOf(0x01, 0x02))
        val bytes2 = CborByteString(byteArrayOf(0x01, 0x02))
        assertEquals(bytes1.hashCode(), bytes2.hashCode())
    }

    // toString tests
    @Test
    fun testStringToString() {
        val str = CborString("test")
        assertTrue(str.toString().contains("test"))
    }

    @Test
    fun testByteStringToString() {
        val bytes = CborByteString(byteArrayOf(0xDE.toByte(), 0xAD.toByte()))
        val str = bytes.toString()
        assertTrue(str.contains("dead"))
    }

    // JSON conversion tests
    @Test
    fun testStringToJsonSimple() {
        val str = CborString("test")
        val json = str.toJsonSimple()
        assertEquals("\"test\"", json.toString())
    }

    @Test
    fun testByteStringToJsonSimple() {
        val bytes = CborByteString(byteArrayOf(0x01, 0x02, 0x03))
        val json = bytes.toJsonSimple()
        // Should be base64url encoded
        assertTrue(json.toString().isNotEmpty())
    }

    // asStr accessor test
    @Test
    fun testAsStr() {
        val str = CborString("test")
        assertEquals("test", str.asStr)
    }

    @Test
    fun testAsStrOnNonString() {
        val uint = CborUInt(42)
        assertFailsWith<IllegalArgumentException> {
            uint.asStr
        }
    }

    // asBstr accessor test
    @Test
    fun testAsBstr() {
        val bytes = byteArrayOf(0x01, 0x02)
        val byteString = CborByteString(bytes)
        assertContentEquals(bytes, byteString.asBstr)
    }

    @Test
    fun testAsBstrOnNonByteString() {
        val str = CborString("test")
        assertFailsWith<IllegalArgumentException> {
            str.asBstr
        }
    }

    // Array encoding/decoding helpers
    @Test
    fun testEncodeToHexArray() {
        val array = CborArray(mutableListOf(
            CborByteString(byteArrayOf(0xDE.toByte(), 0xAD.toByte())),
            CborByteString(byteArrayOf(0xBE.toByte(), 0xEF.toByte()))
        ))
        val hexArray = array.encodeToHexArray()
        assertContentEquals(arrayOf("dead", "beef"), hexArray)
    }

    @Test
    fun testEncodeToBase64Array() {
        val array = CborArray(mutableListOf(
            CborByteString("Hello".encodeToByteArray())
        ))
        val base64Array = array.encodeToBase64Array()
        assertEquals("SGVsbG8=", base64Array[0])
    }

    @Test
    fun testEncodeToBase64UrlArray() {
        val array = CborArray(mutableListOf(
            CborByteString("Hello".encodeToByteArray())
        ))
        val base64UrlArray = array.encodeToBase64UrlArray()
        assertEquals("SGVsbG8", base64UrlArray[0])
    }

    @Test
    fun testEncodeToCborByteArray() {
        val strings = arrayOf("deadbeef", "cafebabe")
        val cborArray = strings.encodeToCborByteArray(Encoding.HEX)
        assertEquals(2, cborArray.value.size)
        assertContentEquals("deadbeef".decodeFromHex(), cborArray.value[0].value)
        assertContentEquals("cafebabe".decodeFromHex(), cborArray.value[1].value)
    }

    // CDDL type tests
    @Test
    fun testCDDLTstr() {
        val item = CDDL.tstr.newString("test")
        assertEquals("test", item.value)
    }

    @Test
    fun testCDDLBstr() {
        val bytes = byteArrayOf(0x01, 0x02)
        val item = CDDL.bstr.newByteString(bytes)
        assertContentEquals(bytes, item.value)
    }

    @Test
    fun testCDDLText() {
        val item = CDDL.text.newText("test")
        assertEquals("test", item.value)
    }

    @Test
    fun testCDDLBytes() {
        val bytes = byteArrayOf(0x01, 0x02)
        val item = CDDL.bytes.newBytes(bytes)
        assertContentEquals(bytes, item.value)
    }

    // toCborItem conversion
    @Test
    fun testStringToCborItem() {
        val item = "test".toCborItem()
        assertIs<CborString>(item)
        assertEquals("test", (item as CborString).value)
    }

    @Test
    fun testByteArrayToCborItem() {
        val bytes = byteArrayOf(0x01, 0x02)
        val item = bytes.toCborItem()
        assertIs<CborByteString>(item)
        assertContentEquals(bytes, (item as CborByteString).value)
    }

    // Round-trip tests
    @Test
    fun testRoundTripString() {
        val testStrings = listOf(
            "",
            "a",
            "Hello, World!",
            "\u00fc",
            "\u6c34",
            "a".repeat(1000)
        )

        for (str in testStrings) {
            val item = CborString(str)
            val encoded = cborSerializer.encode(item)
            val decoded = cborSerializer.decode<CborString>(encoded)
            assertEquals(str, decoded.value, "Round-trip failed for string: '$str'")
        }
    }

    @Test
    fun testRoundTripByteString() {
        val testByteArrays = listOf(
            byteArrayOf(),
            byteArrayOf(0x00),
            byteArrayOf(0xFF.toByte()),
            ByteArray(256) { it.toByte() },
            ByteArray(1000) { (it % 256).toByte() }
        )

        for (bytes in testByteArrays) {
            val item = CborByteString(bytes)
            val encoded = cborSerializer.encode(item)
            val decoded = cborSerializer.decode<CborByteString>(encoded)
            assertContentEquals(bytes, decoded.value, "Round-trip failed for byte array of size: ${bytes.size}")
        }
    }

    // toBstr method test
    @Test
    fun testToBstr() {
        val str = CborString("test")
        val bstr = str.toBstr()
        assertIs<CborByteString>(bstr)
        // The encoded CBOR representation of "test"
        val decoded = cborSerializer.decode<CborString>(bstr.value)
        assertEquals("test", decoded.value)
    }
}
