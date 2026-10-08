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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for CBOR encoding and decoding including length encodings, all major types, and diagnostics
 */
class CborEncoderDecoderTest {
    // ========== encodeLength branches ==========

    @Test
    fun testEncodeLengthSmall() {
        // Test length < 24
        val item = CborUInt(23)
        val encoded = Cbor.encode(item)
        assertNotNull(encoded)
        val decoded = Cbor.decode<CborUInt>(encoded)
        assertEquals(23L, decoded.value)
    }

    @Test
    fun testEncodeLengthOneByte() {
        // Test length 24-255 (uses 1 extra byte)
        val item = CborUInt(200)
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborUInt>(encoded)
        assertEquals(200L, decoded.value)
    }

    @Test
    fun testEncodeLengthTwoBytes() {
        // Test length 256-65535 (uses 2 extra bytes)
        val item = CborUInt(1000)
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborUInt>(encoded)
        assertEquals(1000L, decoded.value)
    }

    @Test
    fun testEncodeLengthFourBytes() {
        // Test length > 65535 (uses 4 extra bytes)
        val item = CborUInt(100000)
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborUInt>(encoded)
        assertEquals(100000L, decoded.value)
    }

    @Test
    fun testEncodeLengthEightBytes() {
        // Test very large values (uses 8 bytes)
        val item = CborUInt(5000000000L)
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborUInt>(encoded)
        assertEquals(5000000000L, decoded.value)
    }

    // ========== decode majorType branches ==========

    @Test
    fun testDecodeUnsignedInteger() {
        val item = CborUInt(42)
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborUInt>(decoded)
    }

    @Test
    fun testDecodeNegativeInteger() {
        val item = CborNInt(-42)
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborNInt>(decoded)
    }

    @Test
    fun testDecodeByteString() {
        val item = CborByteString(byteArrayOf(1, 2, 3))
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborByteString>(decoded)
    }

    @Test
    fun testDecodeByteStringIndefinite() {
        val item = CborByteStringIndefLength(listOf(byteArrayOf(1, 2), byteArrayOf(3, 4)))
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborByteStringIndefLength>(decoded)
    }

    @Test
    fun testDecodeUnicodeString() {
        val item = CborString("hello")
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborString>(decoded)
    }

    @Test
    fun testDecodeUnicodeStringIndefinite() {
        val item = CborStringIndefLength(listOf("hello", "world"))
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborStringIndefLength>(decoded)
    }

    @Test
    fun testDecodeArray() {
        val item = CborArray(mutableListOf(CborUInt(1), CborUInt(2)))
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborArray<*>>(decoded)
    }

    @Test
    fun testDecodeMap() {
        val item = CborMap(mutableMapOf(CborString("key") to CborUInt(42)))
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborMap<*, *>>(decoded)
    }

    @Test
    fun testDecodeTagged() {
        val item = CborTagged(42, CborString("test"))
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborTagged<*>>(decoded)
    }

    @Test
    fun testDecodeFloat16() {
        // Float16 encoding - f9 3e 00 = half-precision 1.5
        val encoded = byteArrayOf(0xf9.toByte(), 0x3e, 0x00)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborFloat16>(decoded)
    }

    @Test
    fun testDecodeFloat32() {
        val item = CborFloat32(3.14f)
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborFloat>(decoded)
    }

    @Test
    fun testDecodeFloat64() {
        val item = CborDouble(3.14159)
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborDouble>(decoded)
    }

    @Test
    fun testDecodeSimpleTrue() {
        val encoded = Cbor.encode(CborSimple.TRUE)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertEquals(CborSimple.TRUE, decoded)
    }

    @Test
    fun testDecodeSimpleFalse() {
        val encoded = Cbor.encode(CborSimple.FALSE)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertEquals(CborSimple.FALSE, decoded)
    }

    @Test
    fun testDecodeSimpleNull() {
        val encoded = Cbor.encode(CborSimple.NULL)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertEquals(CborSimple.NULL, decoded)
    }

    @Test
    fun testDecodeSimpleUndefined() {
        val encoded = Cbor.encode(CborSimple.UNDEFINED)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertEquals(CborSimple.UNDEFINED, decoded)
    }

    // ========== Error cases ==========

    @Test
    fun testDecodeInvalidAdditionalInfoUint() {
        // Additional info 31 not allowed for unsigned int
        assertFailsWith<IllegalArgumentException> {
            Cbor.decode<CborItem<*>>("1f".decodeFromHex())
        }
    }

    @Test
    fun testDecodeInvalidAdditionalInfoNint() {
        // Additional info 31 not allowed for negative int
        assertFailsWith<IllegalArgumentException> {
            Cbor.decode<CborItem<*>>("3f".decodeFromHex())
        }
    }

    @Test
    fun testDecodeInvalidAdditionalInfoTag() {
        // Additional info 31 not allowed for tags
        assertFailsWith<IllegalArgumentException> {
            Cbor.decode<CborItem<*>>("df".decodeFromHex())
        }
    }

    @Test
    fun testDecodeBreakOutsideIndefinite() {
        // BREAK (0xff) outside indefinite-length item
        assertFailsWith<IllegalArgumentException> {
            Cbor.decode<CborItem<*>>("ff".decodeFromHex())
        }
    }

    @Test
    fun testDecodeLeftoverBytes() {
        // Extra bytes after valid CBOR
        val validCbor = Cbor.encode(CborUInt(42))
        val withExtra = validCbor + byteArrayOf(0x00)
        assertFailsWith<IllegalArgumentException> {
            Cbor.decode<CborItem<*>>(withExtra)
        }
    }

    @Test
    fun testDecodeOutOfBounds() {
        // Truncated data
        assertFailsWith<IllegalArgumentException> {
            Cbor.decode<CborItem<*>>("19".decodeFromHex()) // Needs 2 more bytes
        }
    }

    // ========== Encode null ==========

    @Test
    fun testEncodeNull() {
        val encoded = Cbor.encode(null)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborNull>(decoded)
    }

    // ========== Decode with offset ==========

    @Test
    fun testDecodeWithOffset() {
        val item1 = CborUInt(42)
        val item2 = CborString("hello")
        val encoded1 = Cbor.encode(item1)
        val encoded2 = Cbor.encode(item2)
        val combined = encoded1 + encoded2

        val (offset1, decoded1) = Cbor.decode(combined, 0)
        assertIs<CborUInt>(decoded1)
        assertEquals(42L, decoded1.value)

        val (_, decoded2) = Cbor.decode(combined, offset1)
        assertIs<CborString>(decoded2)
        assertEquals("hello", decoded2.value)
    }

    // ========== toDiagnostics branches ==========

    @Test
    fun testToDiagnosticsSimple() {
        val item = CborUInt(42)
        val diag = Cbor.toDiagnostics(item)
        assertEquals("42", diag)
    }

    @Test
    fun testToDiagnosticsWithEmbeddedCbor() {
        val inner = CborString("test")
        val bytes = Cbor.encode(inner)
        val tagged = CborTagged(CborTagged.ENCODED_CBOR, CborByteString(bytes))
        val diag = Cbor.toDiagnostics(tagged, setOf(DiagnosticOption.EMBEDDED_CBOR))
        assertTrue(diag.contains("<<"))
    }

    @Test
    fun testToDiagnosticsWithPrettyPrint() {
        val item =
            CborArray(
                mutableListOf(
                    CborMap(mutableMapOf(CborString("a") to CborUInt(1))),
                    CborMap(mutableMapOf(CborString("b") to CborUInt(2))),
                ),
            )
        val diag = Cbor.toDiagnostics(item, setOf(DiagnosticOption.PRETTY_PRINT))
        assertTrue(diag.contains("\n"))
    }

    @Test
    fun testToDiagnosticsWithBstrLength() {
        val item = CborByteString(byteArrayOf(1, 2, 3, 4, 5))
        val diag = Cbor.toDiagnostics(item, setOf(DiagnosticOption.BSTR_PRINT_LENGTH))
        assertEquals("5 bytes", diag)
    }

    @Test
    fun testToDiagnosticsEncoded() {
        val item = CborUInt(42)
        val encoded = Cbor.encode(item)
        val diag = Cbor.toDiagnosticsEncoded(encoded)
        assertEquals("42", diag)
    }

    // ========== Float16 special values ==========
    // Note: CborFloat16 uses raw CBOR half-precision format (0xf9 prefix)
    // We test decoding from raw bytes since the encoder uses 32-bit format

    @Test
    fun testFloat16Zero() {
        // f9 00 00 = half-precision 0.0
        val encoded = byteArrayOf(0xf9.toByte(), 0x00, 0x00)
        val decoded = Cbor.decode<CborFloat16>(encoded)
        assertEquals(0.0f, decoded.value)
    }

    @Test
    fun testFloat16Infinity() {
        // f9 7c 00 = half-precision +Infinity
        val encoded = byteArrayOf(0xf9.toByte(), 0x7c, 0x00)
        val decoded = Cbor.decode<CborFloat16>(encoded)
        assertEquals(Float.POSITIVE_INFINITY, decoded.value)
    }

    @Test
    fun testFloat16NegativeInfinity() {
        // f9 fc 00 = half-precision -Infinity
        val encoded = byteArrayOf(0xf9.toByte(), 0xfc.toByte(), 0x00)
        val decoded = Cbor.decode<CborFloat16>(encoded)
        assertEquals(Float.NEGATIVE_INFINITY, decoded.value)
    }

    @Test
    fun testFloat16NaN() {
        // f9 7e 00 = half-precision NaN
        val encoded = byteArrayOf(0xf9.toByte(), 0x7e, 0x00)
        val decoded = Cbor.decode<CborFloat16>(encoded)
        assertTrue(decoded.value.isNaN())
    }

    // ========== Large arrays and maps ==========

    @Test
    fun testLargeArray() {
        val items = (1..100).map { CborUInt(it.toLong()) }.toMutableList()
        val array = CborArray(items)
        val encoded = Cbor.encode(array)
        val decoded = Cbor.decode<CborArray<CborUInt>>(encoded)
        assertEquals(100, decoded.value.size)
    }

    @Test
    fun testLargeMap() {
        val pairs = (1..50).associate { CborString("key$it") to CborUInt(it.toLong()) }.toMutableMap()
        val map = CborMap(pairs)
        val encoded = Cbor.encode(map)
        val decoded = Cbor.decode<CborMap<CborString, CborUInt>>(encoded)
        assertEquals(50, decoded.value.size)
    }

    // ========== Indefinite length arrays and maps ==========

    @Test
    fun testIndefiniteLengthArray() {
        val array = CborArray(mutableListOf(CborUInt(1), CborUInt(2)), indefiniteLength = true)
        val encoded = Cbor.encode(array)
        val decoded = Cbor.decode<CborArray<CborUInt>>(encoded)
        assertEquals(2, decoded.value.size)
        assertTrue(decoded.indefiniteLength)
    }

    @Test
    fun testIndefiniteLengthMap() {
        val map = CborMap(mutableMapOf(CborString("a") to CborUInt(1)), indefiniteLength = true)
        val encoded = Cbor.encode(map)
        val decoded = Cbor.decode<CborMap<CborString, CborUInt>>(encoded)
        assertEquals(1, decoded.value.size)
        assertTrue(decoded.indefiniteLength)
    }

    // ========== Additional toDiagnostics branches ==========

    @Test
    fun testToDiagnosticsNegativeInteger() {
        val item = CborNInt(42)
        val diag = Cbor.toDiagnostics(item)
        assertTrue(diag.contains("-42"))
    }

    @Test
    fun testToDiagnosticsNumberLabelPositive() {
        val map = CborMap(mutableMapOf(NumberLabel(5) to CborString("value")))
        val diag = Cbor.toDiagnostics(map)
        assertTrue(diag.contains("5"))
    }

    @Test
    fun testToDiagnosticsNumberLabelNegative() {
        val label = NumberLabel(-3)
        val map = CborMap(mutableMapOf(label to CborString("value")))
        val diag = Cbor.toDiagnostics(map)
        assertTrue(diag.contains("-3") || diag.contains("3"))
    }

    @Test
    fun testToDiagnosticsStringWithQuotes() {
        val str = CborString("hello \"world\"")
        val diag = Cbor.toDiagnostics(str)
        assertTrue(diag.contains("\\\""))
    }

    @Test
    fun testToDiagnosticsStringWithBackslash() {
        val str = CborString("path\\to\\file")
        val diag = Cbor.toDiagnostics(str)
        assertTrue(diag.contains("\\\\"))
    }

    @Test
    fun testToDiagnosticsStringLabel() {
        val map = CborMap(mutableMapOf(StringLabel("myKey") to CborUInt(42)))
        val diag = Cbor.toDiagnostics(map)
        assertTrue(diag.contains("myKey"))
    }

    @Test
    fun testToDiagnosticsStringLabelWithQuotes() {
        val label = StringLabel("key\"with\"quotes")
        val map = CborMap(mutableMapOf(label to CborUInt(1)))
        val diag = Cbor.toDiagnostics(map)
        assertTrue(diag.contains("\\\""))
    }

    @Test
    fun testToDiagnosticsIndefiniteLengthByteString() {
        val chunks = listOf(byteArrayOf(1, 2), byteArrayOf(3, 4))
        val item = CborByteStringIndefLength(chunks)
        val diag = Cbor.toDiagnostics(item)
        assertTrue(diag.contains("(_"))
    }

    @Test
    fun testToDiagnosticsIndefiniteLengthByteStringPrintLength() {
        val chunks = listOf(byteArrayOf(1, 2), byteArrayOf(3, 4))
        val item = CborByteStringIndefLength(chunks)
        val diag = Cbor.toDiagnostics(item, setOf(DiagnosticOption.BSTR_PRINT_LENGTH))
        assertTrue(diag.contains("indefinite"))
    }

    @Test
    fun testToDiagnosticsIndefiniteLengthString() {
        val chunks = listOf("hello", "world")
        val item = CborStringIndefLength(chunks)
        val diag = Cbor.toDiagnostics(item)
        assertTrue(diag.contains("(_"))
    }

    @Test
    fun testToDiagnosticsIndefiniteLengthStringWithQuotes() {
        val chunks = listOf("hello\\world", "test\"quote")
        val item = CborStringIndefLength(chunks)
        val diag = Cbor.toDiagnostics(item)
        assertNotNull(diag)
    }

    @Test
    fun testToDiagnosticsIndefiniteLengthArray() {
        val array = CborArray(mutableListOf(CborUInt(1)), indefiniteLength = true)
        val diag = Cbor.toDiagnostics(array)
        assertTrue(diag.contains("[_"))
    }

    @Test
    fun testToDiagnosticsIndefiniteLengthMapDiag() {
        val map = CborMap(mutableMapOf(CborString("key") to CborUInt(1)), indefiniteLength = true)
        val diag = Cbor.toDiagnostics(map)
        assertTrue(diag.contains("{_"))
    }

    @Test
    fun testToDiagnosticsIndefiniteLengthMapPretty() {
        val map = CborMap(mutableMapOf(CborString("key") to CborUInt(1)), indefiniteLength = true)
        val diag = Cbor.toDiagnostics(map, setOf(DiagnosticOption.PRETTY_PRINT))
        assertTrue(diag.contains("{_"))
    }

    @Test
    fun testToDiagnosticsArrayFitsOnSingleLine() {
        val array = CborArray(mutableListOf(CborUInt(1), CborUInt(2)))
        val diag = Cbor.toDiagnostics(array, setOf(DiagnosticOption.PRETTY_PRINT))
        assertEquals("[1, 2]", diag)
    }

    @Test
    fun testToDiagnosticsEmptyMap() {
        val map = CborMap<CborItem<*>, CborItem<*>>(mutableMapOf())
        val diag = Cbor.toDiagnostics(map)
        assertEquals("{}", diag)
    }

    @Test
    fun testToDiagnosticsEmptyMapPretty() {
        val map = CborMap<CborItem<*>, CborItem<*>>(mutableMapOf())
        val diag = Cbor.toDiagnostics(map, setOf(DiagnosticOption.PRETTY_PRINT))
        assertEquals("{}", diag)
    }

    @Test
    fun testToDiagnosticsEncodedCborWithError() {
        val invalidBytes = byteArrayOf(0xFF.toByte()) // Invalid CBOR
        val tagged = CborTagged(CborTagged.ENCODED_CBOR, CborByteString(invalidBytes))
        val diag = Cbor.toDiagnostics(tagged, setOf(DiagnosticOption.EMBEDDED_CBOR))
        assertTrue(diag.contains("Error"))
    }

    @Test
    fun testToDiagnosticsFloat() {
        val float = CborFloat32(3.14f)
        val diag = Cbor.toDiagnostics(float)
        assertTrue(diag.contains("3.14"))
    }

    @Test
    fun testToDiagnosticsDouble() {
        val double = CborDouble(3.14159)
        val diag = Cbor.toDiagnostics(double)
        assertTrue(diag.contains("3.14159"))
    }

    @Test
    fun testToDiagnosticsTrue() {
        val diag = Cbor.toDiagnostics(CborTrue())
        assertEquals("true", diag)
    }

    @Test
    fun testToDiagnosticsFalse() {
        val diag = Cbor.toDiagnostics(CborFalse())
        assertEquals("false", diag)
    }

    @Test
    fun testToDiagnosticsNullValue() {
        val diag = Cbor.toDiagnostics(CborNull())
        assertEquals("null", diag)
    }

    @Test
    fun testToDiagnosticsUndefinedValue() {
        val diag = Cbor.toDiagnostics(CborUndefined())
        assertEquals("undefined", diag)
    }

    @Test
    fun testToDiagnosticsTagged() {
        val tagged = CborTagged(100, CborString("test"))
        val diag = Cbor.toDiagnostics(tagged)
        assertTrue(diag.contains("100("))
    }

    @Test
    fun testToDiagnosticsRawCbor() {
        val innerData = CborString("test")
        val encoded = Cbor.encode(innerData)
        val raw = RawCbor(encoded)
        val diag = Cbor.toDiagnostics(raw)
        assertTrue(diag.contains("test"))
    }

    @Test
    fun testToDiagnosticsOneByteBstr() {
        val item = CborByteString(byteArrayOf(1))
        val diag = Cbor.toDiagnostics(item, setOf(DiagnosticOption.BSTR_PRINT_LENGTH))
        assertEquals("1 byte", diag)
    }

    @Test
    fun testToDiagnosticsEncodedCborTag() {
        val innerData = CborString("test")
        val encoded = Cbor.encode(innerData)
        val tagged = CborTagged(CborTagged.ENCODED_CBOR, CborByteString(encoded))
        val diag = Cbor.toDiagnostics(tagged)
        assertTrue(diag.contains("<<"))
        assertTrue(diag.contains(">>"))
    }

    // ========== allDataItemsNonCompound branches ==========

    @Test
    fun testToDiagnosticsArrayWithNestedArray() {
        val innerArray = CborArray(mutableListOf(CborUInt(1)))
        val array = CborArray(mutableListOf(innerArray, CborUInt(2)))
        val diag = Cbor.toDiagnostics(array, setOf(DiagnosticOption.PRETTY_PRINT))
        assertTrue(diag.contains("\n"))
    }

    @Test
    fun testToDiagnosticsArrayWithNestedMap() {
        val innerMap = CborMap(mutableMapOf(CborString("key") to CborUInt(1)))
        val array = CborArray(mutableListOf(innerMap))
        val diag = Cbor.toDiagnostics(array, setOf(DiagnosticOption.PRETTY_PRINT))
        assertTrue(diag.contains("\n"))
    }

    @Test
    fun testToDiagnosticsArrayWithEncodedCborTag() {
        val innerData = CborString("test")
        val encoded = Cbor.encode(innerData)
        val tagged = CborTagged(CborTagged.ENCODED_CBOR, CborByteString(encoded))
        val array = CborArray(mutableListOf(tagged))
        val diag = Cbor.toDiagnostics(array, setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT))
        assertTrue(diag.contains("\n"))
    }

    // ========== Additional encoding/decoding branches ==========

    @Test
    fun testEncodeDecodeLargeUInt() {
        // Test encoding uint values that require different byte lengths
        val values =
            listOf(
                23L, // 1 byte
                24L, // 2 bytes (additional info 24)
                255L, // 2 bytes
                256L, // 3 bytes
                65535L, // 3 bytes
                65536L, // 5 bytes
                4294967295L, // 5 bytes (max 32-bit)
                4294967296L, // 9 bytes (64-bit)
            )
        for (value in values) {
            val item = CborUInt(value)
            val encoded = Cbor.encode(item)
            val decoded = Cbor.decode<CborUInt>(encoded)
            assertEquals(value, decoded.value)
        }
    }

    @Test
    fun testEncodeDecodeLargeNInt() {
        // Test encoding nint values that require different byte lengths
        val values = listOf(24L, 255L, 256L, 65535L, 65536L)
        for (value in values) {
            val item = CborNInt(value)
            val encoded = Cbor.encode(item)
            val decoded = Cbor.decode<CborNInt>(encoded)
            assertEquals(value, decoded.value)
        }
    }

    @Test
    fun testEncodeDecodeEmptyByteString() {
        val item = CborByteString(byteArrayOf())
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborByteString>(encoded)
        assertEquals(0, decoded.value.size)
    }

    @Test
    fun testEncodeDecodeEmptyString() {
        val item = CborString("")
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborString>(encoded)
        assertEquals("", decoded.value)
    }

    @Test
    fun testEncodeDecodeEmptyArray() {
        val item = CborArray(mutableListOf<CborItem<*>>())
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborArray<*>>(encoded)
        assertEquals(0, decoded.value.size)
    }

    @Test
    fun testEncodeDecodeEmptyMap() {
        val item = CborMap(mutableMapOf<CborItem<*>, CborItem<*>>())
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborMap<*, *>>(encoded)
        assertEquals(0, decoded.value.size)
    }

    @Test
    fun testEncodeDecodeLargeByteString() {
        // Test a byte string that requires a 2-byte length encoding
        val data = ByteArray(300) { it.toByte() }
        val item = CborByteString(data)
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborByteString>(encoded)
        assertEquals(300, decoded.value.size)
    }

    @Test
    fun testEncodeDecodeLargeArray() {
        // Test an array with many elements (>23)
        val items = (0 until 30).map { CborUInt(it.toLong()) }.toMutableList()
        val item = CborArray(items)
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborArray<*>>(encoded)
        assertEquals(30, decoded.value.size)
    }

    @Test
    fun testEncodeDecodeLargeMap() {
        // Test a map with many entries (>23)
        val entries =
            (0 until 30)
                .associate {
                    CborString("key$it") to CborUInt(it.toLong())
                }.toMutableMap()
        val item = CborMap(entries)
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborMap<*, *>>(encoded)
        assertEquals(30, decoded.value.size)
    }

    // ========== Diagnostics with different options ==========

    @Test
    fun testToDiagnosticsWithPrettyPrintOnly() {
        val map =
            CborMap(
                mutableMapOf(
                    CborString("key1") to CborUInt(1),
                    CborString("key2") to CborUInt(2),
                ),
            )
        val diag = Cbor.toDiagnostics(map, setOf(DiagnosticOption.PRETTY_PRINT))
        assertTrue(diag.contains("\n"))
    }

    @Test
    fun testToDiagnosticsWithEmptyOptions() {
        val map =
            CborMap(
                mutableMapOf(
                    CborString("key") to CborUInt(1),
                ),
            )
        val diag = Cbor.toDiagnostics(map, emptySet())
        // Without PRETTY_PRINT, there should be no newlines
        assertTrue(!diag.contains("\n"))
    }

    @Test
    fun testToDiagnosticsIndefiniteLengthMapItem() {
        val map =
            CborMap(
                mutableMapOf(
                    CborString("key") to CborUInt(1),
                ),
                indefiniteLength = true,
            )
        val diag = Cbor.toDiagnostics(map)
        assertTrue(diag.contains("{_"))
    }

    @Test
    fun testToDiagnosticsIndefiniteLengthArrayItem() {
        val array = CborArray(mutableListOf(CborUInt(1), CborUInt(2)), indefiniteLength = true)
        val diag = Cbor.toDiagnostics(array)
        assertTrue(diag.contains("[_"))
    }

    @Test
    fun testToDiagnosticsBstrEmpty() {
        val item = CborByteString(byteArrayOf())
        val diag = Cbor.toDiagnostics(item, setOf(DiagnosticOption.BSTR_PRINT_LENGTH))
        assertEquals("0 bytes", diag)
    }

    @Test
    fun testToDiagnosticsBstrMultipleBytes() {
        val item = CborByteString(byteArrayOf(1, 2, 3, 4, 5))
        val diag = Cbor.toDiagnostics(item, setOf(DiagnosticOption.BSTR_PRINT_LENGTH))
        assertEquals("5 bytes", diag)
    }

    // ========== Float encoding branches ==========

    @Test
    fun testEncodeDecodeFloatEncoding() {
        // Just verify float types can be encoded and decoded without error
        val double = CborDouble(3.141592653589793)
        val encodedDouble = Cbor.encode(double)
        val decodedDouble = Cbor.decode<CborItem<*>>(encodedDouble)
        assertNotNull(decodedDouble)

        val float16 = CborFloat16(1.5f)
        val encodedFloat16 = Cbor.encode(float16)
        val decodedFloat16 = Cbor.decode<CborItem<*>>(encodedFloat16)
        assertNotNull(decodedFloat16)
    }

    // ========== Tagged values ==========

    @Test
    fun testEncodeDecodeTaggedDateValue() {
        val item = CborTagged(CborTagged.DATE_TIME_STRING, CborString("2024-06-15T10:30:00Z"))
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborTagged<*>>(decoded)
        assertEquals(CborTagged.DATE_TIME_STRING, (decoded as CborTagged<*>).tagNumber)
    }

    @Test
    fun testEncodeDecodeTaggedTimestampValue() {
        val item = CborTagged(CborTagged.DATE_TIME_NUMBER, CborUInt(1718442600))
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborTagged<*>>(decoded)
        assertEquals(CborTagged.DATE_TIME_NUMBER, (decoded as CborTagged<*>).tagNumber)
    }

    @Test
    fun testEncodeDecodeNestedTaggedValue() {
        val inner = CborTagged(100, CborString("inner"))
        val outer = CborTagged(200, inner)
        val encoded = Cbor.encode(outer)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborTagged<*>>(decoded)
        assertEquals(200, (decoded as CborTagged<*>).tagNumber)
        assertIs<CborTagged<*>>((decoded as CborTagged<*>).taggedItem)
    }

    // ========== CborEncodedItem branches ==========

    @Test
    fun testCborEncodedItemWithArrayValue() {
        val original = CborArray(mutableListOf(CborUInt(1), CborString("test")))
        val encoded = CborEncodedItem(Cbor.encode(original), original)
        assertNotNull(encoded.value)
    }

    @Test
    fun testCborEncodedItemTagNumberValue() {
        val original = CborString("test")
        val encoded = CborEncodedItem(Cbor.encode(original), original)
        // CborEncodedItem wraps its value in a CborTagged with tag number 24 (ENCODED_CBOR)
        assertEquals(CborTagged.ENCODED_CBOR, encoded.value.tagNumber)
    }
}
