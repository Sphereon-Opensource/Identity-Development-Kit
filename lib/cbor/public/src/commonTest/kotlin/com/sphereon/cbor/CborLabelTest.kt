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

import com.sphereon.core.api.encodeToHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CborLabelTest {

    // NumberLabel tests

    @Test
    fun testNumberLabelPositive() {
        val label = NumberLabel(42)
        assertEquals(42L, label.value)
        assertEquals(LabelType.Number, label.type)
        assertEquals(CDDL.uint, label.cddl)
    }

    @Test
    fun testNumberLabelNegative() {
        val label = NumberLabel(-42)
        assertEquals(42L, label.value) // value is absolute
        assertEquals(LabelType.Number, label.type)
        assertEquals(CDDL.nint, label.cddl)
    }

    @Test
    fun testNumberLabelZero() {
        val label = NumberLabel(0)
        assertEquals(0L, label.value)
        assertEquals(CDDL.uint, label.cddl)
    }

    @Test
    fun testNumberLabelFromInt() {
        val label = NumberLabel(100)
        assertEquals(100L, label.value)
    }

    @Test
    fun testNumberLabelFromLong() {
        val label = NumberLabel(1000000000000L)
        assertEquals(1000000000000L, label.value)
    }

    @Test
    fun testNumberLabelToCborStructure() {
        val positiveLabel = NumberLabel(42)
        val positiveCbor = positiveLabel.toCborStructure()
        assertIs<CborUInt>(positiveCbor)
        assertEquals(42L, positiveCbor.value)

        val negativeLabel = NumberLabel(-42)
        val negativeCbor = negativeLabel.toCborStructure()
        assertIs<CborNInt>(negativeCbor)
        assertEquals(42L, negativeCbor.value)
    }

    @Test
    fun testNumberLabelEncode() {
        val label = NumberLabel(42)
        val encoded = cborSerializer.encode(label)
        assertEquals("182a", encoded.encodeToHex())
    }

    @Test
    fun testNumberLabelFromCborStructure() {
        val uint = CborUInt(42)
        val label = NumberLabel.fromCborStructure(uint)
        assertEquals(42L, label.value)

        val nint = CborNInt(42)
        val negLabel = NumberLabel.fromCborStructure(nint)
        assertEquals(42L, negLabel.value)
    }

    @Test
    fun testNumberLabelFromCborStructureInvalid() {
        val str = CborString("test")
        assertFailsWith<IllegalArgumentException> {
            NumberLabel.fromCborStructure(str)
        }
    }

    @Test
    fun testNumberLabelToString() {
        val label = NumberLabel(42)
        assertEquals("42", label.toString())
    }

    @Test
    fun testNumberLabelToJsonSimple() {
        val label = NumberLabel(42)
        val json = label.toJsonSimple()
        assertEquals("42", json.toString())
    }

    // StringLabel tests

    @Test
    fun testStringLabelBasic() {
        val label = StringLabel("key")
        assertEquals("key", label.value)
        assertEquals(LabelType.String, label.type)
        assertEquals(CDDL.tstr, label.cddl)
    }

    @Test
    fun testStringLabelEmpty() {
        val label = StringLabel("")
        assertEquals("", label.value)
    }

    @Test
    fun testStringLabelToCborStructure() {
        val label = StringLabel("test")
        val cbor = label.toCborStructure()
        assertIs<CborString>(cbor)
        assertEquals("test", cbor.value)
    }

    @Test
    fun testStringLabelEncode() {
        val label = StringLabel("a")
        val encoded = cborSerializer.encode(label)
        assertEquals("6161", encoded.encodeToHex())
    }

    @Test
    fun testStringLabelFromCborStructure() {
        val str = CborString("test")
        val label = StringLabel.fromCborStructure(str)
        assertEquals("test", label.value)
    }

    @Test
    fun testStringLabelFromCborStructureInvalid() {
        val uint = CborUInt(42)
        assertFailsWith<IllegalArgumentException> {
            StringLabel.fromCborStructure(uint)
        }
    }

    @Test
    fun testStringLabelToString() {
        val label = StringLabel("key")
        assertEquals("key", label.toString())
    }

    @Test
    fun testStringLabelToJsonSimple() {
        val label = StringLabel("key")
        val json = label.toJsonSimple()
        assertEquals("\"key\"", json.toString())
    }

    // CoseLabel.fromCborStructure tests

    @Test
    fun testCoseLabelFromUInt() {
        val uint = CborUInt(42)
        val label = CoseLabel.fromCborStructure(uint)
        assertIs<NumberLabel>(label)
        assertEquals(42L, label.value)
    }

    @Test
    fun testCoseLabelFromNInt() {
        val nint = CborNInt(42)
        val label = CoseLabel.fromCborStructure(nint)
        assertIs<NumberLabel>(label)
        assertEquals(42L, label.value)
    }

    @Test
    fun testCoseLabelFromString() {
        val str = CborString("key")
        val label = CoseLabel.fromCborStructure(str)
        assertIs<StringLabel>(label)
        assertEquals("key", label.value)
    }

    @Test
    fun testCoseLabelFromCoseLabel() {
        val original = NumberLabel(42)
        val label = CoseLabel.fromCborStructure(original)
        assertEquals(original, label)
    }

    @Test
    fun testCoseLabelFromInvalidType() {
        val array = CborArray(mutableListOf())
        assertFailsWith<IllegalStateException> {
            CoseLabel.fromCborStructure(array)
        }
    }

    // Label with Map tests

    @Test
    fun testLabelRequiredFromMap() {
        val map = CborMap(mutableMapOf<CoseLabel<*>, CborItem<*>>(
            NumberLabel(1) to CborString("value1"),
            StringLabel("key") to CborUInt(42)
        ))

        val value1: CborString = NumberLabel(1).required(map)
        assertEquals("value1", value1.value)

        val value2: CborUInt = StringLabel("key").required(map)
        assertEquals(42L, value2.value)
    }

    @Test
    fun testLabelRequiredFromMapMissing() {
        val map = CborMap(mutableMapOf<CoseLabel<*>, CborItem<*>>(
            NumberLabel(1) to CborString("value")
        ))

        assertFailsWith<IllegalArgumentException> {
            NumberLabel(99).required<CborString>(map)
        }
    }

    @Test
    fun testLabelOptionalFromMap() {
        val map = CborMap(mutableMapOf<CoseLabel<*>, CborItem<*>>(
            NumberLabel(1) to CborString("value")
        ))

        val value1: CborString? = NumberLabel(1).optional(map)
        assertEquals("value", value1?.value)

        val value2: CborString? = NumberLabel(99).optional(map)
        assertEquals(null, value2)
    }

    @Test
    fun testLabelRequiredAsCborMap() {
        val inner = CborMap(mutableMapOf<CborItem<*>, CborItem<*>>(StringLabel("inner") to CborUInt(1)))
        val map = CborMap(mutableMapOf<CoseLabel<*>, CborItem<*>>(
            NumberLabel(1) to inner
        ))

        val result = NumberLabel(1).requiredAsCborMap(map)
        assertEquals(1, result.value.size)
    }

    @Test
    fun testLabelRequiredAsCborArray() {
        val inner = CborArray(mutableListOf<CborItem<*>>(CborUInt(1), CborUInt(2)))
        val map = CborMap(mutableMapOf<CoseLabel<*>, CborItem<*>>(
            NumberLabel(1) to inner
        ))

        val result: CborArray<CborItem<*>> = NumberLabel(1).requiredAsCborArray(map)
        assertEquals(2, result.value.size)
    }

    @Test
    fun testLabelOptionalAsCborMap() {
        val map = CborMap(mutableMapOf<CoseLabel<*>, CborItem<*>>())
        val result = NumberLabel(1).optionalAsCborMap(map)
        assertEquals(null, result)
    }

    @Test
    fun testLabelOptionalAsCborArray() {
        val map = CborMap(mutableMapOf<CoseLabel<*>, CborItem<*>>())
        val result = NumberLabel(1).optionalAsCborArray(map)
        assertEquals(null, result)
    }

    // Extension function tests

    @Test
    fun testIntToNumberLabel() {
        val label = 42.toNumberLabel()
        assertEquals(42L, label.value)
    }

    @Test
    fun testLongToNumberLabel() {
        // longToNumberLabel converts to Int, so use a value that fits in Int
        val label = 42L.longToNumberLabel()
        assertEquals(42L, label.value)
    }

    @Test
    fun testStringToStringLabel() {
        val label = "key".toStringLabel()
        assertEquals("key", label.value)
    }

    // NumberLabeledMap tests

    @Test
    fun testNumberLabeledMapBasic() {
        val bytes = cborSerializer.encode(CborMap(mutableMapOf(
            NumberLabel(1) to CborString("value"),
            NumberLabel(2) to CborUInt(42)
        )))

        val labeledMap = bytes.toNumberLabeledMap()

        val value1: CborString = labeledMap.requiredLabel(1)
        assertEquals("value", value1.value)

        val value2: CborUInt = labeledMap.requiredLabel(2)
        assertEquals(42L, value2.value)
    }

    @Test
    fun testNumberLabeledMapOptional() {
        val bytes = cborSerializer.encode(CborMap(mutableMapOf(
            NumberLabel(1) to CborString("value")
        )))

        val labeledMap = bytes.toNumberLabeledMap()

        val value: CborString? = labeledMap.optionalLabel(99)
        assertEquals(null, value)
    }

    @Test
    fun testNumberLabeledMapHasLabel() {
        val bytes = cborSerializer.encode(CborMap(mutableMapOf(
            NumberLabel(1) to CborString("value")
        )))

        val labeledMap = bytes.toNumberLabeledMap()

        assertTrue(labeledMap.hasLabel(1))
        assertEquals(false, labeledMap.hasLabel(99))
    }

    @Test
    fun testNumberLabeledMapGetLabels() {
        val bytes = cborSerializer.encode(CborMap(mutableMapOf(
            NumberLabel(1) to CborString("a"),
            NumberLabel(2) to CborString("b")
        )))

        val labeledMap = bytes.toNumberLabeledMap()
        val labels = labeledMap.getLabels()

        assertEquals(2, labels.size)
    }

    @Test
    fun testNumberLabeledMapEncode() {
        val bytes = cborSerializer.encode(CborMap(mutableMapOf(
            NumberLabel(1) to CborString("value")
        )))

        val labeledMap = bytes.toNumberLabeledMap()
        val reEncoded = labeledMap.encode()

        // Should be able to decode the re-encoded bytes
        val decoded = cborSerializer.decode<CborMap<NumberLabel, CborItem<*>>>(reEncoded)
        assertEquals(1, decoded.value.size)
    }

    @Test
    fun testNumberLabeledMapEquality() {
        val bytes = cborSerializer.encode(CborMap(mutableMapOf(
            NumberLabel(1) to CborString("value")
        )))

        val map1 = bytes.toNumberLabeledMap()
        val map2 = bytes.toNumberLabeledMap()

        assertEquals(map1, map2)
        assertEquals(map1.hashCode(), map2.hashCode())
    }

    // LabelType enum
    @Test
    fun testLabelType() {
        assertEquals(LabelType.String, StringLabel("key").type)
        assertEquals(LabelType.Number, NumberLabel(1).type)
    }

    // Major type tests
    @Test
    fun testNumberLabelMajorType() {
        val positiveLabel = NumberLabel(42)
        assertEquals(MajorType.UNSIGNED_INTEGER, positiveLabel.majorType)

        val negativeLabel = NumberLabel(-42)
        assertEquals(MajorType.NEGATIVE_INTEGER, negativeLabel.majorType)
    }

    @Test
    fun testStringLabelMajorType() {
        val label = StringLabel("key")
        assertEquals(MajorType.UNICODE_STRING, label.majorType)
    }

    // Equality tests
    @Test
    fun testNumberLabelEquality() {
        val label1 = NumberLabel(42)
        val label2 = NumberLabel(42)
        val label3 = NumberLabel(43)

        assertEquals(label1, label2)
        assertNotEquals(label1, label3)
    }

    @Test
    fun testStringLabelEquality() {
        val label1 = StringLabel("key")
        val label2 = StringLabel("key")
        val label3 = StringLabel("other")

        assertEquals(label1, label2)
        assertNotEquals(label1, label3)
    }
}
