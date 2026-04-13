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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CborCollectionTest {

    // RFC 8949 test vectors for arrays

    @Test
    fun testEmptyArray() {
        val encoded = cborSerializer.encode(CborArray(mutableListOf()))
        assertEquals("80", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>(encoded)
        assertEquals(0, decoded.value.size)
    }

    @Test
    fun testArrayOneTwoThree() {
        val array = CborArray(mutableListOf(CborUInt(1), CborUInt(2), CborUInt(3)))
        val encoded = cborSerializer.encode(array)
        assertEquals("83010203", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>(encoded)
        assertEquals(3, decoded.value.size)
        assertEquals(1, decoded.value[0].asInt)
        assertEquals(2, decoded.value[1].asInt)
        assertEquals(3, decoded.value[2].asInt)
    }

    @Test
    fun testNestedArray() {
        // [1, [2, 3], [4, 5]]
        val inner1 = CborArray(mutableListOf(CborUInt(2), CborUInt(3)))
        val inner2 = CborArray(mutableListOf(CborUInt(4), CborUInt(5)))
        val outer = CborArray(mutableListOf(CborUInt(1), inner1, inner2))
        val encoded = cborSerializer.encode(outer)
        assertEquals("8301820203820405", encoded.encodeToHex())

        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>(encoded)
        assertEquals(3, decoded.value.size)
        assertEquals(1, decoded.value[0].asInt)
        assertIs<CborArray<*>>(decoded.value[1])
        assertIs<CborArray<*>>(decoded.value[2])
    }

    @Test
    fun testLongArray() {
        // Array with 25 elements (requires 2-byte length encoding)
        val items = (1..25).map { CborUInt(it.toLong()) }.toMutableList()
        val array = CborArray(items)
        val encoded = cborSerializer.encode(array)
        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>(encoded)
        assertEquals(25, decoded.value.size)
    }

    @Test
    fun testIndefiniteLengthArray() {
        // Create indefinite length array
        val array = CborArray(mutableListOf(CborUInt(1), CborUInt(2), CborUInt(3)), indefiniteLength = true)
        val encoded = cborSerializer.encode(array)
        assertTrue(encoded.encodeToHex().startsWith("9f"))
        assertTrue(encoded.encodeToHex().endsWith("ff"))

        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>(encoded)
        assertEquals(3, decoded.value.size)
        assertTrue(decoded.indefiniteLength)
    }

    @Test
    fun testDecodeIndefiniteLengthArray() {
        // [_ 1, 2, 3]
        val hex = "9f010203ff"
        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>(hex.decodeFromHex())
        assertEquals(3, decoded.value.size)
        assertTrue(decoded.indefiniteLength)
    }

    @Test
    fun testMixedTypeArray() {
        val array = CborArray(mutableListOf(
            CborUInt(1),
            CborString("hello"),
            CborByteString(byteArrayOf(0x01, 0x02)),
            CborSimple.TRUE
        ))
        val encoded = cborSerializer.encode(array)
        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>(encoded)

        assertEquals(4, decoded.value.size)
        assertIs<CborUInt>(decoded.value[0])
        assertIs<CborString>(decoded.value[1])
        assertIs<CborByteString>(decoded.value[2])
        assertIs<CborBool>(decoded.value[3])
    }

    // Array accessor methods
    @Test
    fun testArrayRequired() {
        val array = CborArray(mutableListOf(CborUInt(1), CborString("test")))
        val item: CborUInt = array.required(0)
        assertEquals(1L, item.value)
    }

    @Test
    fun testArrayRequiredOutOfBounds() {
        val array = CborArray(mutableListOf(CborUInt(1)))
        assertFailsWith<IllegalArgumentException> {
            array.required<CborUInt>(5)
        }
    }

    @Test
    fun testArrayOptional() {
        val array = CborArray(mutableListOf<CborItem<*>>(CborUInt(1), CborString("test")))
        val item: CborUInt? = array.optional<CborUInt>(0)
        assertEquals(1L, item?.value)

        val missing: CborUInt? = array.optional<CborUInt>(10)
        assertEquals(null, missing)
    }

    // RFC 8949 test vectors for maps

    @Test
    fun testEmptyMap() {
        val encoded = cborSerializer.encode(CborMap(mutableMapOf()))
        assertEquals("a0", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborMap<CborItem<*>, CborItem<*>>>(encoded)
        assertEquals(0, decoded.value.size)
    }

    @Test
    fun testSimpleMap() {
        // {1: 2, 3: 4}
        val map = CborMap(mutableMapOf(
            NumberLabel(1) to CborUInt(2),
            NumberLabel(3) to CborUInt(4)
        ))
        val encoded = cborSerializer.encode(map)
        val decoded = cborSerializer.decode<CborMap<CborItem<*>, CborItem<*>>>(encoded)
        assertEquals(2, decoded.value.size)
    }

    @Test
    fun testStringKeyMap() {
        // {"a": 1, "b": 2}
        val map = CborMap(mutableMapOf(
            StringLabel("a") to CborUInt(1),
            StringLabel("b") to CborUInt(2)
        ))
        val encoded = cborSerializer.encode(map)
        val decoded = cborSerializer.decode<CborMap<CborItem<*>, CborItem<*>>>(encoded)
        assertEquals(2, decoded.value.size)
    }

    @Test
    fun testMixedKeyMap() {
        // {"a": 1, 2: "b"}
        val map = CborMap(mutableMapOf(
            StringLabel("a") to CborUInt(1),
            NumberLabel(2) to CborString("b")
        ))
        val encoded = cborSerializer.encode(map)
        val decoded = cborSerializer.decode<CborMap<CborItem<*>, CborItem<*>>>(encoded)
        assertEquals(2, decoded.value.size)
    }

    @Test
    fun testNestedMap() {
        // {"a": {"b": 1}}
        val inner = CborMap(mutableMapOf(
            StringLabel("b") to CborUInt(1)
        ))
        val outer = CborMap(mutableMapOf(
            StringLabel("a") to inner
        ))
        val encoded = cborSerializer.encode(outer)
        val decoded = cborSerializer.decode<CborMap<CborItem<*>, CborItem<*>>>(encoded)
        assertEquals(1, decoded.value.size)
    }

    @Test
    fun testIndefiniteLengthMap() {
        val map = CborMap(mutableMapOf(
            StringLabel("a") to CborUInt(1)
        ), indefiniteLength = true)
        val encoded = cborSerializer.encode(map)
        assertTrue(encoded.encodeToHex().startsWith("bf"))
        assertTrue(encoded.encodeToHex().endsWith("ff"))

        val decoded = cborSerializer.decode<CborMap<CborItem<*>, CborItem<*>>>(encoded)
        assertTrue(decoded.indefiniteLength)
    }

    @Test
    fun testDecodeIndefiniteLengthMap() {
        // {_ "a": 1, "b": 2}
        // bf = indefinite map start, 61 61 = "a", 01 = 1, 61 62 = "b", 02 = 2, ff = break
        val hex = "bf6161016162 02ff"
        val decoded = cborSerializer.decode<CborMap<CborItem<*>, CborItem<*>>>(hex.replace(" ", "").decodeFromHex())
        assertTrue(decoded.indefiniteLength)
        assertEquals(2, decoded.value.size)
    }

    // Map accessor methods
    @Test
    fun testMapGetStringLabel() {
        val map = CborMap(mutableMapOf(
            StringLabel("key") to CborUInt(42)
        ))
        val value: CborUInt = map.getStringLabel("key", required = true)
        assertEquals(42L, value.value)
    }

    @Test
    fun testMapGetStringLabelMissing() {
        val map = CborMap(mutableMapOf(
            StringLabel("key") to CborUInt(42)
        ))
        assertFailsWith<IllegalArgumentException> {
            map.getStringLabel<CborUInt>("missing", required = true)
        }
    }

    @Test
    fun testMapGetStringLabelOptional() {
        val map = CborMap(mutableMapOf(
            StringLabel("key") to CborUInt(42)
        ))
        val value: CborUInt? = map.getStringLabel("missing", required = false)
        assertEquals(null, value)
    }

    @Test
    fun testMapGetNumberLabel() {
        val map = CborMap(mutableMapOf(
            NumberLabel(1) to CborString("test")
        ))
        val value: CborString = map.getNumberLabel(1L, required = true)
        assertEquals("test", value.value)
    }

    @Test
    fun testMapGetOperator() {
        val map = CborMap(mutableMapOf(
            StringLabel("key") to CborUInt(42)
        ))
        val value: CborUInt = map[StringLabel("key")]
        assertEquals(42L, value.value)
    }

    // Map extension function tests
    @Test
    fun testMapGetStringLabelExtension() {
        val map = mapOf("key" to "value")
        val value: String = map.getStringLabel("key")
        assertEquals("value", value)
    }

    @Test
    fun testMapGetNumberLabelExtension() {
        val map = mapOf(1 to "value")
        val value: String = map.getNumberLabel(1L)
        assertEquals("value", value)
    }

    // JSON conversion tests
    @Test
    fun testArrayToJsonSimple() {
        val array = CborArray(mutableListOf(CborUInt(1), CborUInt(2)))
        val json = array.toJsonSimple()
        assertTrue(json.toString().contains("1"))
        assertTrue(json.toString().contains("2"))
    }

    @Test
    fun testArrayToJsonWithCDDL() {
        val array = CborArray(mutableListOf(CborUInt(1), CborString("test")))
        val json = array.toJsonWithCDDL()
        assertTrue(json.toString().contains("uint"))
        assertTrue(json.toString().contains("tstr"))
    }

    @Test
    fun testMapToJsonSimple() {
        val map = CborMap(mutableMapOf(
            StringLabel("key") to CborUInt(42)
        ))
        val json = map.toJsonSimple()
        assertTrue(json.toString().contains("key"))
        assertTrue(json.toString().contains("42"))
    }

    @Test
    fun testMapToJsonWithCDDL() {
        val map = CborMap(mutableMapOf(
            StringLabel("key") to CborUInt(42)
        ))
        val json = map.toJsonWithCDDL()
        assertTrue(json.toString().contains("uint"))
    }

    @Test
    fun testMapToJsonWithCDDLObject() {
        val map = CborMap(mutableMapOf(
            StringLabel("key") to CborUInt(42)
        ))
        val json = map.toJsonWithCDDLObject()
        assertTrue(json.toString().contains("key"))
        assertTrue(json.toString().contains("cddl"))
    }

    // Major type tests
    @Test
    fun testArrayMajorType() {
        val array = CborArray(mutableListOf())
        assertEquals(MajorType.ARRAY, array.majorType)
    }

    @Test
    fun testMapMajorType() {
        val map = CborMap(mutableMapOf())
        assertEquals(MajorType.MAP, map.majorType)
    }

    // Equality tests
    @Test
    fun testArrayEquality() {
        val array1 = CborArray(mutableListOf(CborUInt(1), CborUInt(2)))
        val array2 = CborArray(mutableListOf(CborUInt(1), CborUInt(2)))
        val array3 = CborArray(mutableListOf(CborUInt(1), CborUInt(3)))

        assertEquals(array1, array2)
        assertNotEquals(array1, array3)
    }

    @Test
    fun testMapEquality() {
        val map1 = CborMap(mutableMapOf(StringLabel("a") to CborUInt(1)))
        val map2 = CborMap(mutableMapOf(StringLabel("a") to CborUInt(1)))
        val map3 = CborMap(mutableMapOf(StringLabel("a") to CborUInt(2)))

        assertEquals(map1, map2)
        assertNotEquals(map1, map3)
    }

    @Test
    fun testIndefiniteLengthEquality() {
        val map1 = CborMap(mutableMapOf(StringLabel("a") to CborUInt(1)), indefiniteLength = true)
        val map2 = CborMap(mutableMapOf(StringLabel("a") to CborUInt(1)), indefiniteLength = false)

        assertNotEquals(map1, map2)
    }

    // toString tests
    @Test
    fun testArrayToString() {
        val array = CborArray(mutableListOf(CborUInt(1), CborUInt(2)))
        val str = array.toString()
        assertTrue(str.contains("CborArray"))
    }

    @Test
    fun testMapToString() {
        val map = CborMap(mutableMapOf(StringLabel("key") to CborUInt(42)))
        val str = map.toString()
        assertTrue(str.contains("CborMap"))
    }

    // asList/asMap accessors
    @Test
    fun testAsList() {
        val array = CborArray(mutableListOf(CborUInt(1), CborUInt(2)))
        val list = array.asList
        assertEquals(2, list.size)
    }

    @Test
    fun testAsListOnNonArray() {
        val uint = CborUInt(42)
        assertFailsWith<IllegalArgumentException> {
            uint.asList
        }
    }

    @Test
    fun testAsMap() {
        val map = CborMap(mutableMapOf(StringLabel("key") to CborUInt(42)))
        val mapValue = map.asMap
        assertEquals(1, mapValue.size)
    }

    @Test
    fun testAsMapOnNonMap() {
        val uint = CborUInt(42)
        assertFailsWith<IllegalArgumentException> {
            uint.asMap
        }
    }

    // toCborItem conversion tests
    @Test
    fun testListToCborItem() {
        val list = listOf("a", "b", "c")
        val item = list.toCborItem()
        assertIs<CborArray<*>>(item)
        assertEquals(3, (item as CborArray<*>).value.size)
    }

    @Test
    fun testArrayToCborItem() {
        val array = arrayOf(1, 2, 3)
        val item = array.toCborItem()
        assertIs<CborArray<*>>(item)
        assertEquals(3, (item as CborArray<*>).value.size)
    }

    @Test
    fun testMapToCborItem() {
        val map = mapOf("key" to "value")
        val item = map.toCborItem()
        assertIs<CborMap<*, *>>(item)
        assertEquals(1, (item as CborMap<*, *>).value.size)
    }

    // CDDL type tests
    @Test
    fun testCDDLList() {
        val item = CDDL.list.newList(mutableListOf(CborUInt(1), CborUInt(2)))
        assertIs<CborArray<*>>(item)
        assertEquals(2, item.value.size)
    }

    @Test
    fun testCDDLMap() {
        val item = CDDL.map.newMap(mutableMapOf(CborString("key") to CborUInt(42)))
        assertIs<CborMap<*, *>>(item)
        assertEquals(1, item.value.size)
    }

    // Extension function tests
    @Test
    fun testCborViewArrayToCborItem() {
        // Test empty array returns null
        val emptyArray = arrayOf<CborStructure<*, *>>()
        assertEquals(null, emptyArray.cborViewArrayToCborItem())
    }

    @Test
    fun testCborViewListToCborItem() {
        // Test empty list returns null
        val emptyList = listOf<CborStructure<*, *>>()
        assertEquals(null, emptyList.cborViewListToCborItem())
    }

    // Build functions
    @Test
    fun testBuildCborArray() {
        val array = buildCborArray {
            addInt(1)
            addInt(2)
            addString("test")
        }
        assertIs<CborArray<*>>(array)
        assertEquals(3, (array as CborArray<*>).value.size)
    }

    // Round-trip tests
    @Test
    fun testRoundTripArray() {
        val array = CborArray(mutableListOf(
            CborUInt(1),
            CborString("test"),
            CborArray(mutableListOf(CborUInt(2), CborUInt(3)))
        ))
        val encoded = cborSerializer.encode(array)
        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>(encoded)

        assertEquals(3, decoded.value.size)
        assertEquals(1, decoded.value[0].asInt)
        assertEquals("test", decoded.value[1].asStr)
    }

    @Test
    fun testRoundTripMap() {
        val map = CborMap(mutableMapOf(
            StringLabel("string") to CborString("value"),
            NumberLabel(1) to CborUInt(42)
        ))
        val encoded = cborSerializer.encode(map)
        val decoded = cborSerializer.decode<CborMap<CborItem<*>, CborItem<*>>>(encoded)

        assertEquals(2, decoded.value.size)
    }

    // Decode from known test vectors
    @Test
    fun testDecodeArrayKnownVector() {
        // [1, 2, 3]
        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>("83010203".decodeFromHex())
        assertEquals(3, decoded.value.size)
        assertEquals(1, decoded.value[0].asInt)
        assertEquals(2, decoded.value[1].asInt)
        assertEquals(3, decoded.value[2].asInt)
    }

    @Test
    fun testDecodeMapKnownVector() {
        // {1: 2, 3: 4}
        val decoded = cborSerializer.decode<CborMap<CborItem<*>, CborItem<*>>>("a201020304".decodeFromHex())
        assertEquals(2, decoded.value.size)
    }

    @Test
    fun testDecodeNestedArrayKnownVector() {
        // [1, [2, 3], [4, 5]]
        val decoded = cborSerializer.decode<CborArray<CborItem<*>>>("8301820203820405".decodeFromHex())
        assertEquals(3, decoded.value.size)
        assertEquals(1, decoded.value[0].asInt)

        val inner1 = decoded.value[1] as CborArray<*>
        assertEquals(2, inner1.value.size)

        val inner2 = decoded.value[2] as CborArray<*>
        assertEquals(2, inner2.value.size)
    }
}
