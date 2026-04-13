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
import kotlin.test.assertTrue

class CborDiagnosticTest {
    // Basic diagnostic notation tests

    @Test
    fun testDiagnosticUnsignedInteger() {
        val item = CborUInt(42)
        val diagnostic = Cbor.toDiagnostics(item)
        assertEquals("42", diagnostic)
    }

    @Test
    fun testDiagnosticNegativeInteger() {
        val item = CborNInt(42)
        val diagnostic = Cbor.toDiagnostics(item)
        assertEquals("-42", diagnostic)
    }

    @Test
    fun testDiagnosticString() {
        val item = CborString("hello")
        val diagnostic = Cbor.toDiagnostics(item)
        assertEquals("\"hello\"", diagnostic)
    }

    @Test
    fun testDiagnosticStringWithEscapes() {
        val item = CborString("hello\"world\\")
        val diagnostic = Cbor.toDiagnostics(item)
        assertEquals("\"hello\\\"world\\\\\"", diagnostic)
    }

    @Test
    fun testDiagnosticByteString() {
        val item = CborByteString(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()))
        val diagnostic = Cbor.toDiagnostics(item)
        assertEquals("h'deadbeef'", diagnostic)
    }

    @Test
    fun testDiagnosticEmptyByteString() {
        val item = CborByteString(byteArrayOf())
        val diagnostic = Cbor.toDiagnostics(item)
        assertEquals("h''", diagnostic)
    }

    @Test
    fun testDiagnosticSimpleTrue() {
        val diagnostic = Cbor.toDiagnostics(CborSimple.TRUE)
        assertEquals("true", diagnostic)
    }

    @Test
    fun testDiagnosticSimpleFalse() {
        val diagnostic = Cbor.toDiagnostics(CborSimple.FALSE)
        assertEquals("false", diagnostic)
    }

    @Test
    fun testDiagnosticSimpleNull() {
        val diagnostic = Cbor.toDiagnostics(CborSimple.NULL)
        assertEquals("null", diagnostic)
    }

    @Test
    fun testDiagnosticSimpleUndefined() {
        val diagnostic = Cbor.toDiagnostics(CborSimple.UNDEFINED)
        assertEquals("undefined", diagnostic)
    }

    @Test
    fun testDiagnosticFloat() {
        val item = CborFloat32(3.14f)
        val diagnostic = Cbor.toDiagnostics(item)
        assertTrue(diagnostic.contains("3.14"))
    }

    @Test
    fun testDiagnosticDouble() {
        val item = CborDouble(3.14159265359)
        val diagnostic = Cbor.toDiagnostics(item)
        assertTrue(diagnostic.contains("3.14"))
    }

    // Array diagnostic tests

    @Test
    fun testDiagnosticEmptyArray() {
        val item = CborArray(mutableListOf())
        val diagnostic = Cbor.toDiagnostics(item)
        assertEquals("[]", diagnostic)
    }

    @Test
    fun testDiagnosticSimpleArray() {
        val item = CborArray(mutableListOf(CborUInt(1), CborUInt(2), CborUInt(3)))
        val diagnostic = Cbor.toDiagnostics(item)
        assertEquals("[1, 2, 3]", diagnostic)
    }

    @Test
    fun testDiagnosticIndefiniteArray() {
        val item = CborArray(mutableListOf(CborUInt(1), CborUInt(2)), indefiniteLength = true)
        val diagnostic = Cbor.toDiagnostics(item)
        assertEquals("[_ 1, 2]", diagnostic)
    }

    // Map diagnostic tests

    @Test
    fun testDiagnosticEmptyMap() {
        val item = CborMap(mutableMapOf())
        val diagnostic = Cbor.toDiagnostics(item)
        assertEquals("{}", diagnostic)
    }

    @Test
    fun testDiagnosticSimpleMap() {
        val item =
            CborMap(
                mutableMapOf(
                    CborString("a") to CborUInt(1),
                ),
            )
        val diagnostic = Cbor.toDiagnostics(item)
        assertEquals("{\"a\": 1}", diagnostic)
    }

    @Test
    fun testDiagnosticIndefiniteMap() {
        val item =
            CborMap(
                mutableMapOf(
                    CborString("a") to CborUInt(1),
                ),
                indefiniteLength = true,
            )
        val diagnostic = Cbor.toDiagnostics(item)
        assertEquals("{_ \"a\": 1}", diagnostic)
    }

    // Tagged item diagnostic tests

    @Test
    fun testDiagnosticTaggedItem() {
        val item = CborTagged(42, CborString("test"))
        val diagnostic = Cbor.toDiagnostics(item)
        assertEquals("42(\"test\")", diagnostic)
    }

    @Test
    fun testDiagnosticEncodedCborTag() {
        val inner = CborString("IETF")
        val bytes = Cbor.encode(inner)
        val item = CborTagged(24, CborByteString(bytes))
        val diagnostic = Cbor.toDiagnostics(item, setOf(DiagnosticOption.EMBEDDED_CBOR))
        assertTrue(diagnostic.contains("<<"))
        assertTrue(diagnostic.contains(">>"))
    }

    // Indefinite byte string diagnostic test

    @Test
    fun testDiagnosticIndefiniteByteString() {
        val item =
            CborByteStringIndefLength(
                listOf(
                    byteArrayOf(0xDE.toByte(), 0xAD.toByte()),
                    byteArrayOf(0xBE.toByte()),
                ),
            )
        val diagnostic = Cbor.toDiagnostics(item)
        assertEquals("(_ h'dead', h'be')", diagnostic)
    }

    // Indefinite text string diagnostic test

    @Test
    fun testDiagnosticIndefiniteTextString() {
        val item = CborStringIndefLength(listOf("hello", "world"))
        val diagnostic = Cbor.toDiagnostics(item)
        assertEquals("(_ \"hello\", \"world\")", diagnostic)
    }

    // Pretty print tests

    @Test
    fun testDiagnosticPrettyPrintArray() {
        val item =
            CborArray(
                mutableListOf(
                    CborMap(mutableMapOf(CborString("a") to CborUInt(1))),
                    CborMap(mutableMapOf(CborString("b") to CborUInt(2))),
                ),
            )
        val diagnostic = Cbor.toDiagnostics(item, setOf(DiagnosticOption.PRETTY_PRINT))
        assertTrue(diagnostic.contains("\n"))
    }

    @Test
    fun testDiagnosticPrettyPrintMap() {
        val item =
            CborMap(
                mutableMapOf(
                    CborString("key1") to CborUInt(1),
                    CborString("key2") to CborUInt(2),
                ),
            )
        val diagnostic = Cbor.toDiagnostics(item, setOf(DiagnosticOption.PRETTY_PRINT))
        assertTrue(diagnostic.contains("\n"))
    }

    @Test
    fun testDiagnosticPrettyPrintSimpleArray() {
        // Simple arrays with non-compound items should fit on single line
        val item = CborArray(mutableListOf(CborUInt(1), CborUInt(2), CborUInt(3)))
        val diagnostic = Cbor.toDiagnostics(item, setOf(DiagnosticOption.PRETTY_PRINT))
        assertEquals("[1, 2, 3]", diagnostic)
    }

    // Byte string length option

    @Test
    fun testDiagnosticBstrPrintLength() {
        val item = CborByteString(byteArrayOf(0x01, 0x02, 0x03))
        val diagnostic = Cbor.toDiagnostics(item, setOf(DiagnosticOption.BSTR_PRINT_LENGTH))
        assertEquals("3 bytes", diagnostic)
    }

    @Test
    fun testDiagnosticBstrPrintLengthSingle() {
        val item = CborByteString(byteArrayOf(0x01))
        val diagnostic = Cbor.toDiagnostics(item, setOf(DiagnosticOption.BSTR_PRINT_LENGTH))
        assertEquals("1 byte", diagnostic)
    }

    @Test
    fun testDiagnosticIndefiniteBstrPrintLength() {
        val item =
            CborByteStringIndefLength(
                listOf(
                    byteArrayOf(0x01, 0x02),
                    byteArrayOf(0x03),
                ),
            )
        val diagnostic = Cbor.toDiagnostics(item, setOf(DiagnosticOption.BSTR_PRINT_LENGTH))
        assertEquals("indefinite-size byte-string", diagnostic)
    }

    // toDiagnosticsEncoded tests

    @Test
    fun testDiagnosticsEncoded() {
        val item = CborUInt(42)
        val encoded = Cbor.encode(item)
        val diagnostic = Cbor.toDiagnosticsEncoded(encoded)
        assertEquals("42", diagnostic)
    }

    @Test
    fun testDiagnosticsEncodedWithOptions() {
        val item = CborArray(mutableListOf(CborUInt(1), CborUInt(2)))
        val encoded = Cbor.encode(item)
        val diagnostic = Cbor.toDiagnosticsEncoded(encoded, setOf(DiagnosticOption.PRETTY_PRINT))
        assertEquals("[1, 2]", diagnostic)
    }

    // RawCbor diagnostic test

    @Test
    fun testDiagnosticRawCbor() {
        val inner = CborUInt(42)
        val encoded = Cbor.encode(inner)
        val raw = RawCbor(encoded)
        val diagnostic = Cbor.toDiagnostics(raw)
        assertEquals("42", diagnostic)
    }

    // Nested structure diagnostic tests

    @Test
    fun testDiagnosticNestedArray() {
        val item =
            CborArray(
                mutableListOf(
                    CborUInt(1),
                    CborArray(mutableListOf(CborUInt(2), CborUInt(3))),
                    CborUInt(4),
                ),
            )
        val diagnostic = Cbor.toDiagnostics(item)
        assertEquals("[1, [2, 3], 4]", diagnostic)
    }

    @Test
    fun testDiagnosticNestedMap() {
        val inner = CborMap(mutableMapOf(CborString("inner") to CborUInt(1)))
        val outer = CborMap(mutableMapOf(CborString("outer") to inner))
        val diagnostic = Cbor.toDiagnostics(outer)
        assertEquals("{\"outer\": {\"inner\": 1}}", diagnostic)
    }

    // Complex test vectors from decoded CBOR

    @Test
    fun testDiagnosticFromEncodedVector() {
        // [1, [2, 3], [4, 5]]
        val encoded = "8301820203820405".decodeFromHex()
        val diagnostic = Cbor.toDiagnosticsEncoded(encoded)
        assertEquals("[1, [2, 3], [4, 5]]", diagnostic)
    }

    @Test
    fun testDiagnosticFromEncodedMapVector() {
        // Note: Definite-length maps decode keys to CoseLabel which toDiagnostics doesn't handle.
        // Using indefinite-length map which keeps keys as CborString: {_ "a": 1}
        val encoded = "bf616101ff".decodeFromHex()
        val diagnostic = Cbor.toDiagnosticsEncoded(encoded)
        assertEquals("{_ \"a\": 1}", diagnostic)
    }

    // DiagnosticOption enum tests

    @Test
    fun testDiagnosticOptionValues() {
        assertEquals(3, DiagnosticOption.entries.size)
        assertTrue(DiagnosticOption.entries.contains(DiagnosticOption.EMBEDDED_CBOR))
        assertTrue(DiagnosticOption.entries.contains(DiagnosticOption.PRETTY_PRINT))
        assertTrue(DiagnosticOption.entries.contains(DiagnosticOption.BSTR_PRINT_LENGTH))
    }

    // Multiple options combined

    @Test
    fun testDiagnosticMultipleOptions() {
        val inner = CborString("test")
        val bytes = Cbor.encode(inner)
        val encodedItem = CborTagged(24, CborByteString(bytes))
        val outer = CborArray(mutableListOf(encodedItem, CborByteString(byteArrayOf(0x01, 0x02, 0x03))))

        val diagnostic =
            Cbor.toDiagnostics(
                outer,
                setOf(
                    DiagnosticOption.EMBEDDED_CBOR,
                    DiagnosticOption.BSTR_PRINT_LENGTH,
                ),
            )

        assertTrue(diagnostic.contains("<<"))
        assertTrue(diagnostic.contains("3 bytes"))
    }

    // Error handling in embedded CBOR

    @Test
    fun testDiagnosticInvalidEmbeddedCbor() {
        // Create a tag 24 with invalid CBOR content
        val invalidBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val item = CborTagged(24, CborByteString(invalidBytes))
        val diagnostic = Cbor.toDiagnostics(item, setOf(DiagnosticOption.EMBEDDED_CBOR))
        assertTrue(diagnostic.contains("Error Decoding CBOR"))
    }
}
