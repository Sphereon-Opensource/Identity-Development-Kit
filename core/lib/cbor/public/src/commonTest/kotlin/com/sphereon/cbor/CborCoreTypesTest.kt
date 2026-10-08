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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for core CBOR types including CborInt, CborNil, indefinite-length strings, maps, and arrays
 */
class CborCoreTypesTest {
    // ========== CborInt tests ==========

    @Test
    fun testCborIntPositive() {
        val cborInt = CborInt(42)
        assertEquals(42L, cborInt.value)
        assertEquals(CDDL.uint, cborInt.cddl)

        // Test encoding
        val encoded = Cbor.encode(cborInt)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborUInt>(decoded)
        assertEquals(42L, decoded.value)
    }

    @Test
    fun testCborIntNegative() {
        val cborInt = CborInt(-42)
        assertEquals(42L, cborInt.value) // abs value
        assertEquals(CDDL.nint, cborInt.cddl)

        // Test encoding
        val encoded = Cbor.encode(cborInt)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborNInt>(decoded)
    }

    @Test
    fun testCborIntZero() {
        val cborInt = CborInt(0)
        assertEquals(0L, cborInt.value)
        assertEquals(CDDL.uint, cborInt.cddl)
    }

    // ========== CborNil tests ==========

    @Test
    fun testCborNil() {
        val nil = CborNil()
        assertEquals(null, nil.value)
        assertEquals(CDDL.nil, nil.cddl)

        // Test JSON conversion
        val json = nil.toJsonSimple()
        assertEquals(kotlinx.serialization.json.JsonNull, json)
    }

    @Test
    fun testCborNull() {
        val nullItem = CborNull()
        assertEquals(null, nullItem.value)
        assertEquals(CDDL.Null, nullItem.cddl)

        // Test JSON conversion
        val json = nullItem.toJsonSimple()
        assertEquals(kotlinx.serialization.json.JsonNull, json)
    }

    // ========== CborStringIndefLength tests ==========

    @Test
    fun testCborStringIndefLength() {
        val chunks = listOf("hello", "world")
        val item = CborStringIndefLength(chunks)
        assertEquals(chunks, item.value)

        // Test encoding and decoding
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborStringIndefLength>(decoded)
        assertEquals(chunks, decoded.value)
    }

    @Test
    fun testCborStringIndefLengthSingle() {
        val chunks = listOf("single")
        val item = CborStringIndefLength(chunks)

        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborStringIndefLength>(encoded)
        assertEquals(chunks, decoded.value)
    }

    // ========== CborByteStringIndefLength tests ==========

    @Test
    fun testCborByteStringIndefLength() {
        val chunks = listOf(byteArrayOf(1, 2), byteArrayOf(3, 4))
        val item = CborByteStringIndefLength(chunks)
        assertEquals(2, item.value.size)

        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborByteStringIndefLength>(decoded)
    }

    // ========== CborMap tests ==========

    @Test
    fun testCborMapGetStringLabel() {
        val map =
            CborMap(
                mutableMapOf(
                    StringLabel("key1") to CborUInt(42),
                    StringLabel("key2") to CborString("value"),
                ),
            )

        val result = map.getStringLabel<CborUInt>("key1")
        assertNotNull(result)
        assertEquals(42L, result.value)
    }

    @Test
    fun testCborMapGetNumberLabel() {
        val map =
            CborMap(
                mutableMapOf(
                    NumberLabel(1) to CborString("value1"),
                    NumberLabel(2) to CborString("value2"),
                ),
            )

        val result = map.getNumberLabel<CborString>(1L)
        assertNotNull(result)
        assertEquals("value1", result.value)
    }

    @Test
    fun testCborMapIndefiniteLength() {
        val map =
            CborMap(
                mutableMapOf(CborString("a") to CborUInt(1)),
                indefiniteLength = true,
            )

        assertTrue(map.indefiniteLength)
        val encoded = Cbor.encode(map)
        val decoded = Cbor.decode<CborMap<CborString, CborUInt>>(encoded)
        assertTrue(decoded.indefiniteLength)
    }

    @Test
    fun testCborMapToJson() {
        val map =
            CborMap(
                mutableMapOf(
                    CborString("key") to CborUInt(42),
                ),
            )

        val json = map.toJsonSimple()
        assertNotNull(json)
        assertEquals("42", json["key"]?.toString())
    }

    @Test
    fun testCborMapToJsonWithCDDL() {
        val map =
            CborMap(
                mutableMapOf(
                    CborString("key") to CborUInt(42),
                ),
            )

        val json = map.toJsonWithCDDL()
        assertTrue(json.isNotEmpty())
    }

    @Test
    fun testCborMapToJsonWithCDDLObject() {
        val map =
            CborMap(
                mutableMapOf(
                    CborString("key") to CborUInt(42),
                ),
            )

        val json = map.toJsonWithCDDLObject()
        assertNotNull(json)
    }

    // ========== CborArray tests ==========

    @Test
    fun testCborArrayRequired() {
        val array =
            CborArray(
                mutableListOf(
                    CborUInt(1),
                    CborString("test"),
                    CborUInt(2),
                ),
            )

        val first = array.required<CborUInt>(0)
        assertEquals(1L, first.value)

        val second = array.required<CborString>(1)
        assertEquals("test", second.value)
    }

    @Test
    fun testCborArrayOptional() {
        val array =
            CborArray(
                mutableListOf(
                    CborString("a"),
                    CborString("b"),
                ),
            )

        val item = array.optional<CborString>(1)
        assertNotNull(item)
        assertEquals("b", item.value)

        val notFound = array.optional<CborString>(10)
        assertEquals(null, notFound)
    }

    @Test
    fun testCborArrayRequiredOutOfBounds() {
        val array = CborArray(mutableListOf(CborUInt(1)))

        assertFailsWith<IllegalArgumentException> {
            array.required<CborUInt>(10)
        }
    }

    // ========== CborSimple branches ==========

    @Test
    fun testCborSimpleTrue() {
        val trueVal = CborTrue()
        assertTrue(trueVal.value == true)
        assertEquals(CDDL.True, trueVal.cddl)

        val json = trueVal.toJsonSimple()
        assertIs<kotlinx.serialization.json.JsonPrimitive>(json)
    }

    @Test
    fun testCborSimpleFalse() {
        val falseVal = CborFalse()
        assertTrue(falseVal.value == false)
        assertEquals(CDDL.False, falseVal.cddl)

        val json = falseVal.toJsonSimple()
        assertIs<kotlinx.serialization.json.JsonPrimitive>(json)
    }

    @Test
    fun testCborUndefined() {
        val undefined = CborUndefined()
        assertEquals(Unit, undefined.value)
        assertEquals(CDDL.undefined, undefined.cddl)

        val json = undefined.toJsonSimple()
        assertEquals(kotlinx.serialization.json.JsonNull, json)
    }

    // ========== CoseLabel tests ==========

    @Test
    fun testCoseLabelFromCborItemNumber() {
        val uint = CborUInt(42)
        val label = CoseLabel.fromCborItem(uint)
        assertIs<NumberLabel>(label)
        assertEquals(42L, (label as NumberLabel).value)
    }

    @Test
    fun testCoseLabelFromCborItemNegative() {
        // CborNInt stores the value, fromCborItem creates NumberLabel with negated value
        // NumberLabel stores abs(value) in .value, and sign in cddl
        val nint = CborNInt(5)
        val label = CoseLabel.fromCborItem(nint)
        assertIs<NumberLabel>(label)
        // NumberLabel(-5) stores abs(-5)=5 in value, CDDL.nint in cddl
        assertEquals(5L, (label as NumberLabel).value)
        assertEquals(CDDL.nint, label.cddl)
    }

    @Test
    fun testCoseLabelFromCborItemString() {
        val str = CborString("key")
        val label = CoseLabel.fromCborItem(str)
        assertIs<StringLabel>(label)
        assertEquals("key", (label as StringLabel).value)
    }

    @Test
    fun testNumberLabelToCbor() {
        val label = NumberLabel(42)
        val cbor = label.toCborItem()
        assertIs<CborUInt>(cbor)
        assertEquals(42L, cbor.value)
    }

    @Test
    fun testNumberLabelNegativeToCbor() {
        val label = NumberLabel(-10)
        val cbor = label.toCborItem()
        assertIs<CborNInt>(cbor)
    }

    @Test
    fun testStringLabelToCbor() {
        val label = StringLabel("test")
        val cbor = label.toCborItem()
        assertIs<CborString>(cbor)
        assertEquals("test", cbor.value)
    }

    @Test
    fun testNumberLabelEquality() {
        val label1 = NumberLabel(42)
        val label2 = NumberLabel(42)
        val label3 = NumberLabel(43)

        assertEquals(label1, label2)
        assertEquals(label1.hashCode(), label2.hashCode())
        assertFalse(label1.equals(label3))
    }

    @Test
    fun testStringLabelEquality() {
        val label1 = StringLabel("test")
        val label2 = StringLabel("test")
        val label3 = StringLabel("other")

        assertEquals(label1, label2)
        assertEquals(label1.hashCode(), label2.hashCode())
        assertFalse(label1.equals(label3))
    }

    // ========== CborTagged tests ==========

    @Test
    fun testCborTaggedCustomTag() {
        // Tag 24 (ENCODED_CBOR) decodes to CborEncodedItem, so use a different tag
        val tagged = CborTagged(42, CborString("test"))

        val encoded = Cbor.encode(tagged)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborTagged<*>>(decoded)
        assertEquals(42, (decoded as CborTagged<*>).tagNumber)
    }

    @Test
    fun testCborEncodedItemDecode() {
        // Tag 24 (ENCODED_CBOR) decodes to CborEncodedItem
        val inner = CborString("test")
        val bytes = Cbor.encode(inner)
        val tagged = CborTagged(CborTagged.ENCODED_CBOR, CborByteString(bytes))

        val encoded = Cbor.encode(tagged)
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborEncodedItem<*>>(decoded)
    }

    @Test
    fun testCborTaggedEquality() {
        val tagged1 = CborTagged(42, CborString("test"))
        val tagged2 = CborTagged(42, CborString("test"))
        val tagged3 = CborTagged(43, CborString("test"))

        assertEquals(tagged1, tagged2)
        assertFalse(tagged1.equals(tagged3))
    }

    // ========== NumberLabeledMap tests ==========

    @Test
    fun testNumberLabeledMapOptionalLabel() {
        val labeledMap = NumberLabeledMap()
        val result = labeledMap.optionalLabel<CborItem<*>>(999)
        assertEquals(null, result)
    }

    @Test
    fun testNumberLabeledMapHasLabel() {
        val labeledMap = NumberLabeledMap()
        assertFalse(labeledMap.hasLabel(1))
    }

    @Test
    fun testNumberLabeledMapGetLabels() {
        val labeledMap = NumberLabeledMap()
        val labels = labeledMap.getLabels()
        assertTrue(labels.isEmpty())
    }

    // ========== Diagnostics tests ==========

    @Test
    fun testDiagnosticsNegativeInt() {
        val item = CborNInt(-42)
        val diag = Cbor.toDiagnostics(item)
        assertTrue(diag.contains("-"))
    }

    @Test
    fun testDiagnosticsMap() {
        val item = CborMap(mutableMapOf(CborString("key") to CborUInt(1)))
        val diag = Cbor.toDiagnostics(item)
        assertTrue(diag.contains("{"))
        assertTrue(diag.contains("}"))
    }

    @Test
    fun testDiagnosticsArray() {
        val item = CborArray(mutableListOf(CborUInt(1), CborUInt(2)))
        val diag = Cbor.toDiagnostics(item)
        assertTrue(diag.contains("["))
        assertTrue(diag.contains("]"))
    }

    // ========== Extension function tests ==========

    @Test
    fun testStringToCborString() {
        val cbor = "test".toCborString()
        assertIs<CborString>(cbor)
        assertEquals("test", cbor.value)
    }

    @Test
    fun testByteArrayToCborByteString() {
        val cbor = byteArrayOf(1, 2, 3).toCborByteString()
        assertIs<CborByteString>(cbor)
        assertEquals(3, cbor.value.size)
    }

    @Test
    fun testBoolToCborBool() {
        val trueItem = true.toCborBool()
        val falseItem = false.toCborBool()

        assertIs<CborTrue>(trueItem)
        assertIs<CborFalse>(falseItem)
    }

    @Test
    fun testFloatToCborFloat() {
        val cbor = 3.14f.toCborFloat()
        assertIs<CborFloat32>(cbor)
    }

    @Test
    fun testDoubleToCborDouble() {
        val cbor = 3.14159.toCborFloat64()
        assertIs<CborDouble>(cbor)
    }

    // ========== CborItem equals/hashCode tests ==========

    @Test
    fun testCborUIntEquality() {
        val uint1 = CborUInt(42)
        val uint2 = CborUInt(42)
        val uint3 = CborUInt(43)

        assertEquals(uint1, uint2)
        assertEquals(uint1.hashCode(), uint2.hashCode())
        assertFalse(uint1.equals(uint3))
    }

    @Test
    fun testCborStringEquality() {
        val str1 = CborString("test")
        val str2 = CborString("test")
        val str3 = CborString("other")

        assertEquals(str1, str2)
        assertEquals(str1.hashCode(), str2.hashCode())
        assertFalse(str1.equals(str3))
    }

    @Test
    fun testCborItemNotEqualToDifferentType() {
        val uint = CborUInt(42)
        val str = CborString("42")

        assertFalse(uint.equals(str))
    }

    @Test
    fun testCborItemNotEqualToNull() {
        val uint = CborUInt(42)
        assertFalse(uint.equals(null))
    }

    @Test
    fun testCborItemNotEqualToOtherObject() {
        val uint = CborUInt(42)
        assertFalse(uint.equals("string"))
    }

    // ========== CborMap equals/hashCode tests ==========

    @Test
    fun testCborMapEquality() {
        val map1 = CborMap(mutableMapOf(CborString("k") to CborUInt(1)))
        val map2 = CborMap(mutableMapOf(CborString("k") to CborUInt(1)))
        val map3 = CborMap(mutableMapOf(CborString("k") to CborUInt(2)))

        assertEquals(map1, map2)
        assertEquals(map1.hashCode(), map2.hashCode())
        assertFalse(map1.equals(map3))
    }

    @Test
    fun testCborMapIndefiniteLengthEquality() {
        val map1 = CborMap(mutableMapOf(CborString("k") to CborUInt(1)), indefiniteLength = true)
        val map2 = CborMap(mutableMapOf(CborString("k") to CborUInt(1)), indefiniteLength = false)

        assertFalse(map1.equals(map2))
    }
}
