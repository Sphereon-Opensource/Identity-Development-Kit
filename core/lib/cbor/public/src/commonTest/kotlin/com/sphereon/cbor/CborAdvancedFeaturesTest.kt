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

import com.sphereon.cbor.dsl.cborArray
import com.sphereon.core.api.Encoding
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for advanced CBOR features including NumberLabeledMap, JSON conversion, and diagnostics
 */
class CborAdvancedFeaturesTest {
    // ========== CDDL branches ==========

    @Test
    fun testCDDLNewCborItemFromJsonObject() {
        val jsonObj =
            buildJsonObject {
                put("key", JsonPrimitive("value"))
            }
        val result = CDDL.map.newCborItemFromJson(jsonObj, CDDL.map)
        assertNotNull(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonArray() {
        val jsonArray =
            buildJsonArray {
                add(JsonPrimitive(1))
                add(JsonPrimitive(2))
            }
        val result = CDDL.list.newCborItemFromJson(jsonArray, CDDL.list)
        assertNotNull(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonPrimitiveNumber() {
        val result = CDDL.int.newCborItemFromJson(JsonPrimitive(42), CDDL.int)
        assertIs<CborUInt>(result)
        assertEquals(42L, result.value)
    }

    @Test
    fun testCDDLNewCborItemFromJsonPrimitiveNegative() {
        val result = CDDL.int.newCborItemFromJson(JsonPrimitive(-42), CDDL.int)
        assertIs<CborNInt>(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonNull() {
        val result = CDDL.Null.newCborItemFromJson(JsonNull, CDDL.Null)
        assertIs<CborNull>(result)
    }

    @Test
    fun testCDDLUtilFromFormat() {
        assertEquals(CDDL.tstr, CDDL.util.fromFormat("tstr"))
        assertEquals(CDDL.uint, CDDL.util.fromFormat("uint"))
        assertEquals(CDDL.nint, CDDL.util.fromFormat("nint"))
        assertEquals(CDDL.int, CDDL.util.fromFormat("int"))
        assertEquals(CDDL.bstr, CDDL.util.fromFormat("bstr"))
        assertEquals(CDDL.bool, CDDL.util.fromFormat("bool"))
        assertEquals(CDDL.map, CDDL.util.fromFormat("map"))
        assertEquals(CDDL.Null, CDDL.util.fromFormat("null"))
        assertEquals(CDDL.any, CDDL.util.fromFormat("any"))
    }

    @Test
    fun testCDDLNewBool() {
        val trueItem = CDDL.bool.newCborItem(true)
        assertIs<CborTrue>(trueItem)

        val falseItem = CDDL.bool.newCborItem(false)
        assertIs<CborFalse>(falseItem)
    }

    @Test
    fun testCDDLNewFloat() {
        val float32 = CDDL.float.newCborItem(3.14f)
        assertIs<CborFloat32>(float32)

        val float64 = CDDL.float64.newFloat64(3.14159)
        assertIs<CborDouble>(float64)
    }

    @Test
    fun testCDDLNewInt() {
        val positive = CDDL.int.newInt(42)
        assertIs<CborUInt>(positive)

        val negative = CDDL.int.newInt(-42)
        assertIs<CborNInt>(negative)
    }

    @Test
    fun testCDDLNewLong() {
        val posLong = CDDL.int.newLong(1000000000L)
        assertIs<CborUInt>(posLong)

        val negLong = CDDL.int.newLong(-1000000000L)
        assertIs<CborNInt>(negLong)
    }

    // ========== NumberLabeledMap branches ==========

    /**
     * Test subclass to expose protected putLabel method
     */
    private class TestNumberLabeledMap : NumberLabeledMap() {
        fun testPutLabel(
            label: Int,
            value: CborItem<*>?,
        ) {
            putLabel(label, value)
        }

        override fun connectLabels(): CborMap<NumberLabel, CborItem<*>> {
            // Override to avoid throwing in test
            return labeledItems
        }
    }

    @Test
    fun testNumberLabeledMapWithData() {
        val labeledMap = TestNumberLabeledMap()
        labeledMap.testPutLabel(1, CborString("value1"))
        labeledMap.testPutLabel(2, CborUInt(42))

        assertTrue(labeledMap.hasLabel(1))
        assertTrue(labeledMap.hasLabel(2))
        assertFalse(labeledMap.hasLabel(3))

        val labels = labeledMap.getLabels()
        assertEquals(2, labels.size)

        val value1 = labeledMap.optionalLabel<CborString>(1)
        assertNotNull(value1)
        assertEquals("value1", value1.value)

        val value2 = labeledMap.requiredLabel<CborUInt>(2)
        assertEquals(42L, value2.value)
    }

    @Test
    fun testNumberLabeledMapRequiredLabelMissing() {
        val labeledMap = TestNumberLabeledMap()
        // requiredLabel returns null when label is missing (no exception thrown)
        val result = labeledMap.requiredLabel<CborItem<*>?>(999)
        assertNull(result)
    }

    @Test
    fun testNumberLabeledMapPutNullValue() {
        val labeledMap = TestNumberLabeledMap()
        labeledMap.testPutLabel(1, null) // Should be ignored
        assertFalse(labeledMap.hasLabel(1))
    }

    // ========== CborMap toJson branches ==========

    @Test
    fun testCborMapToJsonWithNestedStructures() {
        val innerMap = CborMap(mutableMapOf(CborString("inner") to CborUInt(1)))
        val innerArray = CborArray(mutableListOf(CborUInt(1), CborUInt(2)))
        val map =
            CborMap(
                mutableMapOf(
                    CborString("nested_map") to innerMap,
                    CborString("nested_array") to innerArray,
                    CborString("string") to CborString("value"),
                    CborString("number") to CborUInt(42),
                ),
            )

        val jsonSimple = map.toJsonSimple()
        assertIs<JsonObject>(jsonSimple)
        assertTrue(jsonSimple.containsKey("nested_map"))
        assertTrue(jsonSimple.containsKey("nested_array"))

        val jsonWithCddl = map.toJsonWithCDDL()
        assertNotNull(jsonWithCddl)

        val jsonWithCddlObj = map.toJsonWithCDDLObject()
        assertIs<JsonObject>(jsonWithCddlObj)
    }

    @Test
    fun testCborMapToJsonWithNullValue() {
        val map =
            CborMap(
                mutableMapOf(
                    CborString("null_value") to CborNull(),
                ),
            )

        val json = map.toJsonSimple()
        assertIs<JsonObject>(json)
        assertEquals(JsonNull, json["null_value"])
    }

    // ========== CborArray toJson branches ==========

    @Test
    fun testCborArrayToJsonWithNestedStructures() {
        val innerMap = CborMap(mutableMapOf(CborString("key") to CborUInt(1)))
        val innerArray = CborArray(mutableListOf(CborString("a"), CborString("b")))
        val array =
            CborArray(
                mutableListOf(
                    CborUInt(1),
                    CborString("test"),
                    innerMap,
                    innerArray,
                    CborNull(),
                ),
            )

        val jsonSimple = array.toJsonSimple()
        assertIs<JsonArray>(jsonSimple)
        assertEquals(5, jsonSimple.size)

        val jsonWithCddl = array.toJsonWithCDDL()
        assertIs<JsonArray>(jsonWithCddl)
    }

    // ========== Cbor diagnostic branches ==========

    @Test
    fun testCborDiagnosticsWithOptions() {
        val item =
            CborMap(
                mutableMapOf(
                    CborString("key") to CborByteString(byteArrayOf(1, 2, 3)),
                ),
            )
        val diag = Cbor.toDiagnostics(item)
        assertNotNull(diag)
        assertTrue(diag.contains("{"))
    }

    @Test
    fun testCborDiagnosticsWithTagged() {
        val tagged = CborTagged(100, CborString("test"))
        val diag = Cbor.toDiagnostics(tagged)
        assertNotNull(diag)
        assertTrue(diag.contains("100"))
    }

    @Test
    fun testCborDiagnosticsWithFloat() {
        val float = CborFloat32(3.14f)
        val diag = Cbor.toDiagnostics(float)
        assertNotNull(diag)
    }

    // ========== CoseLabel additional branches ==========

    @Test
    fun testCoseLabelFromCborStructureWithUInt() {
        val uint = CborUInt(0)
        val label = CoseLabel.fromCborItem(uint)
        assertIs<NumberLabel>(label)
        assertEquals(0L, label.value)
    }

    @Test
    fun testCoseLabelFromCborStructureInvalid() {
        val bool = CborTrue()
        assertFailsWith<IllegalStateException> {
            CoseLabel.fromCborItem(bool)
        }
    }

    // ========== CborTagged branches ==========

    @Test
    fun testCborTaggedWithDifferentContentTypes() {
        val taggedString = CborTagged(100, CborString("test"))
        assertEquals(100, taggedString.tagNumber)
        assertEquals("test", (taggedString.taggedItem as CborString).value)

        val taggedArray = CborTagged(101, CborArray(mutableListOf(CborUInt(1))))
        assertEquals(101, taggedArray.tagNumber)
    }

    @Test
    fun testCborTaggedToJson() {
        val tagged = CborTagged(42, CborString("test"))
        val json = tagged.toJsonSimple()
        assertIs<JsonPrimitive>(json)
        assertEquals("test", json.content)
    }

    // ========== CborItem toString ==========

    @Test
    fun testCborItemToString() {
        val uint = CborUInt(42)
        val str = uint.toString()
        assertTrue(str.contains("42"))
        assertTrue(str.contains("uint"))
    }

    // ========== CborAny ==========

    @Test
    fun testCborAnyCreation() {
        val any = CborAny(42)
        assertEquals(42, any.value)
        assertEquals(CDDL.any, any.cddl)
    }

    // ========== cborArray DSL tests ==========

    @Test
    fun testCborArrayDslBasic() {
        val array =
            cborArray {
                +CborUInt(1)
                +CborString("test")
            }
        assertIs<CborArray<*>>(array)
        assertEquals(2, array.value.size)
    }

    // ========== String extension tests ==========

    @Test
    fun testStringToCborFullDate() {
        val dateStr = "2024-06-15T00:00:00Z"
        val fullDate = dateStr.toCborFullDate()
        assertIs<CborFullDate>(fullDate)
    }

    // ========== CborByteString extensions ==========

    @Test
    fun testByteArrayToCborByteStringRoundTrip() {
        val original = byteArrayOf(1, 2, 3, 4, 5)
        val cbor = original.toCborByteString()
        assertEquals(5, cbor.value.size)
        assertTrue(original.contentEquals(cbor.value))
    }

    // ========== Additional CDDL newCborItem branches ==========

    @Test
    fun testCDDLNewCborItemString() {
        val result = CDDL.tstr.newCborItem("test")
        assertIs<CborString>(result)
        assertEquals("test", result.value)
    }

    @Test
    fun testCDDLNewCborItemByteArray() {
        val result = CDDL.bstr.newCborItem(byteArrayOf(1, 2, 3))
        assertIs<CborByteString>(result)
        assertEquals(3, result.value.size)
    }

    // ========== CborEncodedItem tests ==========

    @Test
    fun testCborEncodedItemRoundTrip() {
        val innerMap = CborMap(mutableMapOf(CborString("key") to CborUInt(42)))
        val encoded = Cbor.encode(innerMap)

        // Create tag 24 encoded CBOR
        val tagged = CborTagged(CborTagged.ENCODED_CBOR, CborByteString(encoded))
        val tagEncoded = Cbor.encode(tagged)

        val decoded = Cbor.decode<CborItem<*>>(tagEncoded)
        assertIs<CborEncodedItem<*>>(decoded)
    }

    // ========== CborSimple additional tests ==========

    @Test
    fun testCborSimpleEncodeDecode() {
        // Test CborTrue
        val trueItem = CborTrue()
        val trueEncoded = Cbor.encode(trueItem)
        val trueDecoded = Cbor.decode<CborItem<*>>(trueEncoded)
        assertIs<CborTrue>(trueDecoded)

        // Test CborFalse
        val falseItem = CborFalse()
        val falseEncoded = Cbor.encode(falseItem)
        val falseDecoded = Cbor.decode<CborItem<*>>(falseEncoded)
        assertIs<CborFalse>(falseDecoded)

        // Test CborNull (decoder returns CborNull for null/nil simple value)
        val nullItem = CborNull()
        val nullEncoded = Cbor.encode(nullItem)
        val nullDecoded = Cbor.decode<CborItem<*>>(nullEncoded)
        assertIs<CborNull>(nullDecoded)

        // Test CborUndefined
        val undefinedItem = CborUndefined()
        val undefinedEncoded = Cbor.encode(undefinedItem)
        val undefinedDecoded = Cbor.decode<CborItem<*>>(undefinedEncoded)
        assertIs<CborUndefined>(undefinedDecoded)
    }

    // ========== CborMap with number keys ==========

    @Test
    fun testCborMapWithNumberKeys() {
        val map =
            CborMap(
                mutableMapOf(
                    CborUInt(1) to CborString("one"),
                    CborUInt(2) to CborString("two"),
                    CborNInt(3) to CborString("negative three"),
                ),
            )

        val encoded = Cbor.encode(map)
        val decoded = Cbor.decode<CborMap<*, *>>(encoded)
        assertEquals(3, decoded.value.size)
    }

    // ========== CborArray indefinite length ==========

    @Test
    fun testCborArrayIndefiniteLength() {
        val array =
            CborArray(
                mutableListOf(CborUInt(1), CborUInt(2), CborUInt(3)),
                indefiniteLength = true,
            )

        assertTrue(array.indefiniteLength)
        val encoded = Cbor.encode(array)
        val decoded = Cbor.decode<CborArray<CborUInt>>(encoded)
        assertTrue(decoded.indefiniteLength)
    }

    // ========== CborByteString hex conversion ==========

    @Test
    fun testCborByteStringHexConversion() {
        val bytes = byteArrayOf(0x01, 0x02, 0x0A, 0x0F)
        val byteString = CborByteString(bytes)
        val hex = byteString.encodeValueTo(Encoding.HEX)
        assertNotNull(hex)
        assertTrue(hex.isNotEmpty())
    }

    // ========== CborMap JSON conversion branches ==========

    @Test
    fun testCborMapToJsonWithCDDLPrimitiveValues() {
        // Test with primitive int values to trigger isPrimitive branch
        val map =
            CborMap(
                mutableMapOf(
                    CborString("intKey") to CborUInt(42),
                    CborString("strKey") to CborString("hello"),
                ),
            )
        val json = map.toJsonWithCDDL()
        assertIs<JsonArray>(json)
        assertEquals(2, json.size)
    }

    @Test
    fun testCborMapToJsonWithCDDLObjectPrimitiveValues() {
        // Test toJsonWithCDDLObject with primitive values
        val map =
            CborMap(
                mutableMapOf(
                    CborString("numKey") to CborUInt(100),
                    CborString("boolKey") to CborTrue(),
                ),
            )
        val json = map.toJsonWithCDDLObject()
        assertIs<JsonObject>(json)
        assertEquals(2, json.size)
    }

    @Test
    fun testCborMapToJsonWithCDDLArrayValues() {
        // Test with array values to trigger isArray branch
        val innerArray = CborArray(mutableListOf(CborUInt(1), CborUInt(2)))
        val map =
            CborMap(
                mutableMapOf(
                    CborString("arrayKey") to innerArray,
                ),
            )
        val json = map.toJsonWithCDDL()
        assertIs<JsonArray>(json)
        assertEquals(1, json.size)
    }

    @Test
    fun testCborMapToJsonWithCDDLNestedMap() {
        // Test with nested map to trigger isObject branch
        val innerMap = CborMap(mutableMapOf(CborString("inner") to CborUInt(99)))
        val map =
            CborMap(
                mutableMapOf(
                    CborString("mapKey") to innerMap,
                ),
            )
        val json = map.toJsonWithCDDL()
        assertIs<JsonArray>(json)
        assertEquals(1, json.size)
    }

    @Test
    fun testCborMapToJsonWithCDDLObjectArrayValues() {
        // Test toJsonWithCDDLObject with array values
        val innerArray = CborArray(mutableListOf(CborString("a"), CborString("b")))
        val map =
            CborMap(
                mutableMapOf(
                    CborString("arrKey") to innerArray,
                ),
            )
        val json = map.toJsonWithCDDLObject()
        assertIs<JsonObject>(json)
    }

    @Test
    fun testCborMapToJsonWithCDDLObjectNestedMap() {
        // Test toJsonWithCDDLObject with nested map values
        val innerMap = CborMap(mutableMapOf(CborString("nested") to CborString("value")))
        val map =
            CborMap(
                mutableMapOf(
                    CborString("mapKey") to innerMap,
                ),
            )
        val json = map.toJsonWithCDDLObject()
        assertIs<JsonObject>(json)
    }

    @Test
    fun testCborMapToJsonCborItem() {
        val map =
            CborMap(
                mutableMapOf(
                    CborString("key") to CborUInt(42),
                ),
            )
        val cborItemJson = map.toJsonCborItem()
        assertEquals(CDDL.map, cborItemJson.cddl)
        assertNotNull(cborItemJson.value)
    }

    // ========== CborMap with null values ==========

    @Test
    fun testCborMapWithNullableValues() {
        // Test encoding map that has nullable value type
        val map =
            CborMap<CborString, CborItem<*>?>(
                mutableMapOf(
                    CborString("key1") to CborUInt(1),
                    CborString("key2") to CborString("value"),
                ),
            )
        val encoded = Cbor.encode(map)
        val decoded = Cbor.decode<CborMap<*, *>>(encoded)
        assertEquals(2, decoded.value.size)
    }

    @Test
    fun testCborMapIndefiniteLengthWithMultipleEntries() {
        // Test indefinite length map with multiple entries to cover more decode branches
        val map =
            CborMap(
                mutableMapOf(
                    CborString("a") to CborUInt(1),
                    CborString("b") to CborUInt(2),
                    CborString("c") to CborUInt(3),
                ),
                indefiniteLength = true,
            )
        val encoded = Cbor.encode(map)
        val decoded = Cbor.decode<CborMap<*, *>>(encoded)
        assertEquals(3, decoded.value.size)
        assertTrue(decoded.indefiniteLength)
    }

    // ========== CborSimple branches ==========

    @Test
    fun testCborSimpleEqualsWithDifferentValues() {
        val true1 = CborTrue()
        val true2 = CborTrue()
        val false1 = CborFalse()

        assertEquals(true1, true2)
        assertFalse(true1.equals(false1))
    }

    @Test
    fun testCborSimpleToStringUndefined() {
        val undefined = CborUndefined()
        val str = undefined.toString()
        assertTrue(str.contains("UNDEFINED"))
    }

    @Test
    fun testCborSimpleHashCodes() {
        val true1 = CborTrue()
        val true2 = CborTrue()
        assertEquals(true1.hashCode(), true2.hashCode())

        val null1 = CborNull()
        val null2 = CborNull()
        assertEquals(null1.hashCode(), null2.hashCode())
    }

    // ========== CborNil and CborNull fromJson branches ==========

    @Test
    fun testCborNilCreation() {
        // Test CborNil directly
        val nil = CborNil()
        assertNotNull(nil)
        assertEquals(CDDL.nil, nil.cddl)
        val encoded = Cbor.encode(nil)
        val decoded: CborItem<*> = Cbor.decode(encoded)
        assertIs<CborNull>(decoded)
    }

    @Test
    fun testCborNullFromJsonNull() {
        val result = CDDL.Null.fromJson(JsonNull)
        assertIs<CborNull>(result)
    }

    // ========== CborTrue/CborFalse fromJson branches ==========

    @Test
    fun testCborTrueFromJsonTrue() {
        val result = CDDL.True.fromJson(JsonPrimitive(true))
        assertIs<CborTrue>(result)
    }

    @Test
    fun testCborFalseFromJsonFalse() {
        val result = CDDL.False.fromJson(JsonPrimitive(false))
        assertIs<CborFalse>(result)
    }

    // ========== CborItemJson fromJsonArray branches ==========

    @Test
    fun testCborItemJsonIsCborItemValueJsonWithSize2() {
        // Test valid CDDL format with 2 elements
        val json =
            buildJsonObject {
                put("cddl", JsonPrimitive("tstr"))
                put("value", JsonPrimitive("test"))
            }
        assertTrue(CborItemJson.isCborItemValueJson(json))
    }

    @Test
    fun testCborItemJsonIsCborItemValueJsonWithSize3() {
        // Test valid CDDL format with 3 elements
        val json =
            buildJsonObject {
                put("cddl", JsonPrimitive("tstr"))
                put("value", JsonPrimitive("test"))
                put("key", JsonPrimitive("mykey"))
            }
        assertTrue(CborItemJson.isCborItemValueJson(json))
    }

    @Test
    fun testCborItemJsonIsCborItemValueJsonInvalid() {
        // Test invalid format (missing required keys)
        val json =
            buildJsonObject {
                put("foo", JsonPrimitive("bar"))
            }
        assertFalse(CborItemJson.isCborItemValueJson(json))
    }

    @Test
    fun testCborItemJsonIsCborItemJson() {
        // Test valid ICborItemJson format (3 keys with KEY_LITERAL)
        val json =
            buildJsonObject {
                put("cddl", JsonPrimitive("tstr"))
                put("value", JsonPrimitive("test"))
                put("key", JsonPrimitive("mykey"))
            }
        assertTrue(CborItemJson.isCborItemJson(json))
    }

    @Test
    fun testCborItemJsonIsCborItemJsonInvalid() {
        // Test invalid (not 3 keys)
        val json =
            buildJsonObject {
                put("cddl", JsonPrimitive("tstr"))
                put("value", JsonPrimitive("test"))
            }
        assertFalse(CborItemJson.isCborItemJson(json))
    }

    @Test
    fun testCborItemJsonFromJsonPrimitiveWithKey() {
        val result = CborItemJson.fromJsonPrimitive(JsonPrimitive("value"), CDDL.tstr, "myKey")
        assertIs<CborItemJson>(result)
        assertEquals("myKey", (result as CborItemJson).key)
    }

    @Test
    fun testCborItemJsonFromJsonPrimitiveWithoutKey() {
        val result = CborItemJson.fromJsonPrimitive(JsonPrimitive(42), CDDL.int, null)
        assertNotNull(result)
        assertEquals(CDDL.int, result.cddl)
    }

    @Test
    fun testCborItemJsonFromJsonObjectAsValueJsonWithKey() {
        // Test fromJsonObjectAsValueJson with KEY_LITERAL present
        val json =
            buildJsonObject {
                put("cddl", JsonPrimitive("tstr"))
                put("value", JsonPrimitive("testvalue"))
                put("key", JsonPrimitive("testkey"))
            }
        val result = CborItemJson.fromJsonObjectAsValueJson(json)
        assertIs<ICborItemJson>(result)
        assertEquals("testkey", (result as ICborItemJson).key)
    }

    @Test
    fun testCborItemJsonFromJsonObjectAsValueJsonWithoutKey() {
        // Test fromJsonObjectAsValueJson without KEY_LITERAL
        val json =
            buildJsonObject {
                put("cddl", JsonPrimitive("uint"))
                put("value", JsonPrimitive(42))
            }
        val result = CborItemJson.fromJsonObjectAsValueJson(json)
        assertNotNull(result)
        assertEquals(CDDL.uint, result.cddl)
    }

    @Test
    fun testCborItemJsonFromJsonObjectAsCborItemJson() {
        // Test fromJsonObjectAsCborItemJson
        val json =
            buildJsonObject {
                put("cddl", JsonPrimitive("tstr"))
                put("value", JsonPrimitive("test"))
                put("key", JsonPrimitive("thekey"))
            }
        val result = CborItemJson.fromJsonObjectAsCborItemJson(json)
        assertEquals("thekey", result.key)
    }

    @Test
    fun testCborItemJsonFromJsonObjectAsCborItemJsonInvalid() {
        // Test invalid JSON object (not 3 keys)
        val json =
            buildJsonObject {
                put("cddl", JsonPrimitive("tstr"))
                put("value", JsonPrimitive("test"))
            }
        assertFailsWith<IllegalStateException> {
            CborItemJson.fromJsonObjectAsCborItemJson(json)
        }
    }

    @Test
    fun testCborItemJsonFromDTO() {
        val json =
            buildJsonObject {
                put("cddl", JsonPrimitive("tstr"))
                put("value", JsonPrimitive("test"))
                put("key", JsonPrimitive("thekey"))
            }
        val itemJson = CborItemJson.fromJsonObjectAsCborItemJson(json)
        val dto = CborItemJson.fromDTO(itemJson)
        assertEquals("thekey", dto.key)
        assertEquals(CDDL.tstr, dto.cddl)
    }

    @Test
    fun testCborItemJsonToJsonWithCDDL() {
        val item = CborItemJson("mykey", JsonPrimitive("myvalue"), CDDL.tstr)
        val json = item.toJsonWithCDDL()
        assertIs<JsonObject>(json)
    }

    @Test
    fun testCborItemJsonToJsonSimple() {
        val item = CborItemJson("mykey", JsonPrimitive("myvalue"), CDDL.tstr)
        val json = item.toJsonSimple()
        assertIs<JsonObject>(json)
    }

    @Test
    fun testCborItemJsonToJson() {
        val item = CborItemJson("mykey", JsonPrimitive("myvalue"), CDDL.tstr)
        val withCddl = item.toJson(includeCDDL = true)
        val withoutCddl = item.toJson(includeCDDL = false)
        assertIs<JsonObject>(withCddl)
        assertIs<JsonObject>(withoutCddl)
    }

    @Test
    fun testCborItemJsonToJsonCborItem() {
        val item = CborItemJson("mykey", JsonPrimitive("myvalue"), CDDL.tstr)
        val cborItem = item.toJsonCborItem()
        assertEquals(item, cborItem)
    }

    // ========== toCborItem branches ==========

    @Test
    fun testToCborItemByte() {
        val byte: Byte = 42
        val result = byte.toCborItem()
        assertIs<CborUInt>(result)
    }

    @Test
    fun testToCborItemShort() {
        val short: Short = 100
        val result = short.toCborItem()
        assertIs<CborUInt>(result)
    }

    @Test
    fun testToCborItemUInt() {
        val uint: UInt = 1000u
        val result = uint.toCborItem()
        assertIs<CborUInt>(result)
    }

    @Test
    fun testToCborItemDouble() {
        val double = 3.14159
        val result = double.toCborItem()
        assertIsCborDouble(result)
    }

    @Test
    fun testToCborItemFloat() {
        val float = 3.14f
        val result = float.toCborItem()
        assertIsCborFloat(result)
    }

    @Test
    fun testToCborItemArray() {
        val array = arrayOf(1, 2, 3)
        val result = array.toCborItem()
        assertIs<CborArray<*>>(result)
    }

    @Test
    fun testToCborItemMap() {
        val map = mapOf("key1" to 1, "key2" to 2)
        val result = map.toCborItem()
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testToCborItemNull() {
        val nullVal: String? = null
        val result = nullVal.toCborItem()
        assertIs<CborNull>(result)
    }

    // ========== CborMap branch coverage tests ==========

    @Test
    fun testCborMapGetStringLabelRequiredMissing() {
        val map = CborMap<CborItem<*>, CborItem<*>?>(mutableMapOf())
        assertFailsWith<IllegalArgumentException> {
            map.getStringLabel<CborItem<*>>("nonexistent", true)
        }
    }

    @Test
    fun testCborMapGetStringLabelOptionalMissing() {
        val map = CborMap<CborItem<*>, CborItem<*>?>(mutableMapOf())
        val result: CborItem<*>? = map.getStringLabel("nonexistent", false)
        assertNull(result)
    }

    @Test
    fun testCborMapGetNumberLabelRequiredMissing() {
        val map = CborMap<CborItem<*>, CborItem<*>?>(mutableMapOf())
        assertFailsWith<IllegalArgumentException> {
            map.getNumberLabel<CborItem<*>>(1L, true)
        }
    }

    @Test
    fun testCborMapGetNumberLabelOptionalMissing() {
        val map = CborMap<CborItem<*>, CborItem<*>?>(mutableMapOf())
        val result: CborItem<*>? = map.getNumberLabel(1L, false)
        assertNull(result)
    }

    @Test
    fun testCborMapToJsonWithCDDLWithPrimitive() {
        // Test the isPrimitive branch in toJsonWithCDDL
        val map =
            CborMap<CborItem<*>, CborItem<*>?>(
                mutableMapOf(
                    CborString("key1") to CborUInt(42L),
                    CborString("key2") to CborString("value"),
                ),
            )
        val result = map.toJsonWithCDDL()
        assertIs<JsonArray>(result)
        assertEquals(2, result.size)
    }

    @Test
    fun testCborMapToJsonWithCDDLWithArray() {
        // Test the isArray branch in toJsonWithCDDL
        val nestedArray = CborArray(mutableListOf(CborUInt(1L), CborUInt(2L)))
        val map =
            CborMap<CborItem<*>, CborItem<*>?>(
                mutableMapOf(
                    CborString("arr") to nestedArray,
                ),
            )
        val result = map.toJsonWithCDDL()
        assertIs<JsonArray>(result)
        assertEquals(1, result.size)
    }

    @Test
    fun testCborMapToJsonWithCDDLWithNestedMap() {
        // Test the isObject branch in toJsonWithCDDL
        val nestedMap =
            CborMap<CborItem<*>, CborItem<*>?>(
                mutableMapOf(
                    CborString("inner") to CborUInt(99L),
                ),
            )
        val map =
            CborMap<CborItem<*>, CborItem<*>?>(
                mutableMapOf(
                    CborString("nested") to nestedMap,
                ),
            )
        val result = map.toJsonWithCDDL()
        assertIs<JsonArray>(result)
    }

    @Test
    fun testCborMapToJsonWithCDDLObjectWithPrimitive() {
        // Test the isPrimitive branch in toJsonWithCDDLObject
        val map =
            CborMap<CborItem<*>, CborItem<*>?>(
                mutableMapOf(
                    CborString("key1") to CborUInt(42L),
                ),
            )
        val result = map.toJsonWithCDDLObject()
        assertIs<JsonObject>(result)
    }

    @Test
    fun testCborMapToJsonWithCDDLObjectWithArray() {
        // Test the isArray branch in toJsonWithCDDLObject
        val nestedArray = CborArray(mutableListOf(CborUInt(1L)))
        val map =
            CborMap<CborItem<*>, CborItem<*>?>(
                mutableMapOf(
                    CborString("arr") to nestedArray,
                ),
            )
        val result = map.toJsonWithCDDLObject()
        assertIs<JsonObject>(result)
    }

    @Test
    fun testCborMapIndefiniteLengthEncode() {
        // Test the indefiniteLength=true branch in encode()
        val map =
            CborMap<CborItem<*>, CborItem<*>?>(
                mutableMapOf(CborString("key") to CborUInt(1L)),
                indefiniteLength = true,
            )
        val encoded = Cbor.encode(map)
        assertNotNull(encoded)
        // Indefinite length encoding should start with 0xbf and end with 0xff
        assertEquals(0xbf.toByte(), encoded[0])
        assertEquals(0xff.toByte(), encoded[encoded.size - 1])
    }

    @Test
    fun testCborMapIndefiniteLengthRoundTrip() {
        // Test roundtrip with indefinite length map
        val original =
            CborMap<CborItem<*>, CborItem<*>?>(
                mutableMapOf(
                    CborString("a") to CborUInt(1L),
                    CborString("b") to CborUInt(2L),
                ),
                indefiniteLength = true,
            )
        val encoded = Cbor.encode(original)
        val decoded = Cbor.decode<CborMap<*, *>>(encoded)
        assertIs<CborMap<*, *>>(decoded)
        assertEquals(2, (decoded as CborMap<*, *>).value.size)
    }

    @Test
    fun testCborMapEqualsWithSameInstance() {
        val map =
            CborMap<CborItem<*>, CborItem<*>?>(
                mutableMapOf(
                    CborString("key") to CborUInt(1L),
                ),
            )
        assertTrue(map.equals(map))
    }

    @Test
    fun testCborMapEqualsWithDifferentType() {
        val map = CborMap<CborItem<*>, CborItem<*>?>(mutableMapOf())
        assertFalse(map.equals("not a map"))
    }

    @Test
    fun testCborMapEqualsWithDifferentIndefiniteLength() {
        val map1 =
            CborMap<CborItem<*>, CborItem<*>?>(
                mutableMapOf(CborString("key") to CborUInt(1L)),
                indefiniteLength = false,
            )
        val map2 =
            CborMap<CborItem<*>, CborItem<*>?>(
                mutableMapOf(CborString("key") to CborUInt(1L)),
                indefiniteLength = true,
            )
        assertFalse(map1.equals(map2))
    }

    @Test
    fun testCborMapHashCode() {
        val map1 =
            CborMap<CborItem<*>, CborItem<*>?>(
                mutableMapOf(CborString("key") to CborUInt(1L)),
                indefiniteLength = false,
            )
        val map2 =
            CborMap<CborItem<*>, CborItem<*>?>(
                mutableMapOf(CborString("key") to CborUInt(1L)),
                indefiniteLength = true,
            )
        // Hash codes should differ due to different indefiniteLength
        assertTrue(map1.hashCode() != map2.hashCode())
    }

    @Test
    fun testCborMapEncodeWithNullValue() {
        // Test encoding map with null value
        val map =
            CborMap<CborItem<*>, CborItem<*>?>(
                mutableMapOf(
                    CborString("key") to null,
                ),
            )
        val encoded = Cbor.encode(map)
        assertNotNull(encoded)
    }

    @Test
    fun testCborMapIndefiniteLengthEncodeWithNullValue() {
        // Test indefinite length encoding map with null value
        val map =
            CborMap<CborItem<*>, CborItem<*>?>(
                mutableMapOf(CborString("key") to null),
                indefiniteLength = true,
            )
        val encoded = Cbor.encode(map)
        assertNotNull(encoded)
        assertEquals(0xbf.toByte(), encoded[0])
        assertEquals(0xff.toByte(), encoded[encoded.size - 1])
    }

    @Test
    fun testCborMapToString() {
        val map =
            CborMap<CborItem<*>, CborItem<*>?>(
                mutableMapOf(CborString("key") to CborUInt(1L)),
                indefiniteLength = true,
            )
        val str = map.toString()
        assertTrue(str.contains("CborMap"))
        assertTrue(str.contains("indefiniteLength=true"))
    }

    @Test
    fun testMapExtensionGetStringLabelRequiredMissing() {
        // Test extension function on regular Map
        val map: Map<Any, Any?> = mapOf<Any, Any?>()
        assertFailsWith<IllegalArgumentException> {
            map.getStringLabel<Any?>("missing", true)
        }
    }

    @Test
    fun testMapExtensionGetNumberLabelRequiredMissing() {
        // Test extension function on regular Map
        val map: Map<Any, Any?> = mapOf<Any, Any?>()
        assertFailsWith<IllegalArgumentException> {
            map.getNumberLabel<Any?>(1L, true)
        }
    }

    // ========== Additional CborSimple decode branch tests ==========

    @Test
    fun testCborSimpleDecodeInvalidTwoByteValue() {
        // Create a two-byte simple value < 32 which should throw
        // Two-byte encoding: 0xF8 (major type 7, info 24) followed by value
        val invalidBytes = byteArrayOf(0xF8.toByte(), 0x1F) // info=24, value=31 (< 32)
        assertFailsWith<IllegalArgumentException> {
            Cbor.decode<CborSimple<*>>(invalidBytes)
        }
    }

    @Test
    fun testCborSimpleDecodeUnknownValue() {
        // Test decoding unknown simple value (e.g., simple value 16)
        // Simple value 16 is encoded as 0xF0 (majorType7 << 5 | 16)
        val unknownSimpleBytes = byteArrayOf(0xF0.toByte()) // simple(16)
        assertFailsWith<IllegalArgumentException> {
            Cbor.decode<CborSimple<*>>(unknownSimpleBytes)
        }
    }

    @Test
    fun testCborSimpleToStringForFalse() {
        val str = CborSimple.FALSE.toString()
        assertTrue(str.contains("FALSE"))
    }

    @Test
    fun testCborSimpleToStringForTrue() {
        val str = CborSimple.TRUE.toString()
        assertTrue(str.contains("TRUE"))
    }

    @Test
    fun testCborSimpleToStringForNull() {
        val str = CborSimple.NULL.toString()
        assertTrue(str.contains("NULL"))
    }

    @Test
    fun testCborSimpleEqualsWithNonCborSimple() {
        assertFalse(CborSimple.FALSE.equals("not a simple"))
        assertFalse(CborSimple.TRUE.equals(123))
        assertFalse(CborSimple.NULL.equals(null))
    }

    // ========== CborByteStringIndefLength tests ==========

    @Test
    fun testCborByteStringIndefLengthEncode() {
        val chunks =
            listOf(
                byteArrayOf(0x01, 0x02, 0x03),
                byteArrayOf(0x04, 0x05),
            )
        val item = CborByteStringIndefLength(chunks)
        val encoded = Cbor.encode(item)
        assertNotNull(encoded)
        // Should start with 0x5F (major type 2 << 5 | 31) and end with 0xFF
        assertEquals(0x5F.toByte(), encoded[0])
        assertEquals(0xFF.toByte(), encoded[encoded.size - 1])
    }

    @Test
    fun testCborByteStringIndefLengthRoundtrip() {
        val chunks =
            listOf(
                byteArrayOf(0x01, 0x02),
                byteArrayOf(0x03, 0x04, 0x05),
            )
        val original = CborByteStringIndefLength(chunks)
        val encoded = Cbor.encode(original)
        val decoded = Cbor.decode<CborByteStringIndefLength>(encoded)
        assertEquals(2, decoded.value.size)
        assertTrue(chunks[0].contentEquals(decoded.value[0]))
        assertTrue(chunks[1].contentEquals(decoded.value[1]))
    }

    @Test
    fun testCborByteStringIndefLengthSingleChunk() {
        val chunks = listOf(byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
        val item = CborByteStringIndefLength(chunks)
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborByteStringIndefLength>(encoded)
        assertEquals(1, decoded.value.size)
    }

    // ========== CborStringIndefLength tests ==========

    @Test
    fun testCborStringIndefLengthEncode() {
        val chunks = listOf("Hello", "World")
        val item = CborStringIndefLength(chunks)
        val encoded = Cbor.encode(item)
        assertNotNull(encoded)
        // Should start with 0x7F (major type 3 << 5 | 31) and end with 0xFF
        assertEquals(0x7F.toByte(), encoded[0])
        assertEquals(0xFF.toByte(), encoded[encoded.size - 1])
    }

    @Test
    fun testCborStringIndefLengthRoundtrip() {
        val chunks = listOf("foo", "bar", "baz")
        val original = CborStringIndefLength(chunks)
        val encoded = Cbor.encode(original)
        val decoded = Cbor.decode<CborStringIndefLength>(encoded)
        assertEquals(3, decoded.value.size)
        assertEquals("foo", decoded.value[0])
        assertEquals("bar", decoded.value[1])
        assertEquals("baz", decoded.value[2])
    }

    @Test
    fun testCborStringIndefLengthSingleChunk() {
        val chunks = listOf("single")
        val item = CborStringIndefLength(chunks)
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborStringIndefLength>(encoded)
        assertEquals(1, decoded.value.size)
        assertEquals("single", decoded.value[0])
    }

    @Test
    fun testCborStringIndefLengthEmptyStrings() {
        val chunks = listOf("", "nonempty", "")
        val item = CborStringIndefLength(chunks)
        val encoded = Cbor.encode(item)
        val decoded = Cbor.decode<CborStringIndefLength>(encoded)
        assertEquals(3, decoded.value.size)
    }

    // ========== CDDL branch coverage tests ==========

    @Test
    fun testCDDLListFromJsonWithNestedArray() {
        // Test the JsonArray branch in list.fromJson
        val nestedArray =
            buildJsonArray {
                add(
                    buildJsonArray {
                        add(JsonPrimitive(1))
                        add(JsonPrimitive(2))
                    },
                )
            }
        val result = CDDL.list.fromJson(nestedArray)
        assertEquals(1, result.value.size)
        assertIs<CborArray<*>>(result.value[0])
    }

    @Test
    fun testCDDLListFromJsonWithObject() {
        // Test the JsonObject branch in list.fromJson
        val arrayWithObject =
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("key1", JsonPrimitive("value1"))
                    },
                )
            }
        val result = CDDL.list.fromJson(arrayWithObject)
        assertEquals(1, result.value.size)
        assertIs<CborMap<*, *>>(result.value[0])
    }

    @Test
    fun testCDDLToTagWithAlias() {
        // Test toTag with aliasFor types (bytes is aliased to bstr)
        val tag = CDDL.bytes.toTag(null)
        assertNotNull(tag)
    }

    @Test
    fun testCDDLToTagWithMajorType() {
        // Test toTag with majorType set
        val tag = CDDL.uint.toTag(null)
        assertNotNull(tag)
        assertTrue(tag.contains("#"))
    }

    @Test
    fun testCDDLToTagWithAdditionalInfo() {
        // Test toTag with additionalInfo
        val tag = CDDL.tdate.toTag(0)
        assertNotNull(tag)
    }

    @Test
    fun testCDDLEqualsWithSameInstance() {
        assertTrue(CDDL.uint.equals(CDDL.uint))
    }

    @Test
    fun testCDDLEqualsWithDifferentType() {
        assertFalse(CDDL.uint.equals("not a CDDL"))
    }

    @Test
    fun testCDDLEqualsWithDifferentFormat() {
        assertFalse(CDDL.uint.equals(CDDL.nint))
    }

    @Test
    fun testCDDLHashCode() {
        val hash1 = CDDL.uint.hashCode()
        val hash2 = CDDL.uint.hashCode()
        assertEquals(hash1, hash2)
    }

    @Test
    fun testCDDLHashCodeWithNullMajorType() {
        // int has null majorType (it's an alias)
        val hash = CDDL.int.hashCode()
        assertNotNull(hash)
    }

    @Test
    fun testCDDLHashCodeWithNullInfo() {
        // uint has null info
        val hash = CDDL.uint.hashCode()
        assertNotNull(hash)
    }

    @Test
    fun testCDDLToString() {
        val str = CDDL.uint.toString()
        assertTrue(str.contains("CDDL"))
        assertTrue(str.contains("uint"))
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithCborItemValueJson() {
        // Test the CborItemJson.isCborItemValueJson branch
        val jsonWithCddl =
            buildJsonObject {
                put("cddl", JsonPrimitive("uint"))
                put("value", JsonPrimitive(42))
            }
        val result = CDDL.any.newCborItemFromJson(jsonWithCddl, null)
        assertNotNull(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithKeyInCborItemJson() {
        // Test the key !== null branch in newCborItemFromJson
        val jsonWithKey =
            buildJsonObject {
                put("key", JsonPrimitive("mykey"))
                put("cddl", JsonPrimitive("uint"))
                put("value", JsonPrimitive(42))
            }
        val result = CDDL.any.newCborItemFromJson(jsonWithKey, null)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithNonPrimitiveCddl() {
        // Test when cddl key is present but not a primitive - should return CborNull
        val jsonWithNullCddl =
            buildJsonObject {
                put("cddl", buildJsonObject { }) // cddl is object, not primitive
                put("value", JsonPrimitive(42))
            }
        val result = CDDL.any.newCborItemFromJson(jsonWithNullCddl, null)
        assertIs<CborNull>(result)
    }

    @Test
    fun testCDDLIntNewIntNegative() {
        val result = CDDL.int.newInt(-5)
        assertIs<CborNInt>(result)
    }

    @Test
    fun testCDDLIntNewIntPositive() {
        val result = CDDL.int.newInt(5)
        assertIs<CborUInt>(result)
    }

    @Test
    fun testCDDLIntNewLongNegative() {
        val result = CDDL.int.newLong(-100L)
        assertIs<CborNInt>(result)
    }

    @Test
    fun testCDDLIntNewLongPositive() {
        val result = CDDL.int.newLong(100L)
        assertIs<CborUInt>(result)
    }

    @Test
    fun testCDDLIntFromJsonNegative() {
        val result = CDDL.int.fromJson(JsonPrimitive(-42))
        assertIs<CborNInt>(result)
    }

    @Test
    fun testCDDLIntFromJsonPositive() {
        val result = CDDL.int.fromJson(JsonPrimitive(42))
        assertIs<CborUInt>(result)
    }

    @Test
    fun testCDDLAnyFromJson() {
        val result = CDDL.any.fromJson(JsonPrimitive("test"))
        assertIs<CborString>(result)
    }

    @Test
    fun testCDDLAnyFromJsonNumber() {
        val result = CDDL.any.fromJson(JsonPrimitive(123))
        assertIs<CborUInt>(result)
    }

    // ========== Cbor decoder error branch tests ==========

    @Test
    fun testCborDecodeUnsignedIntWithIndefiniteLength() {
        // Major type 0 (unsigned int) with additional info 31 should throw
        // 0x1F = 0b00011111 = major type 0, additional info 31
        val invalidBytes = byteArrayOf(0x1F)
        assertFailsWith<IllegalArgumentException> {
            Cbor.decode<CborItem<*>>(invalidBytes)
        }
    }

    @Test
    fun testCborDecodeNegativeIntWithIndefiniteLength() {
        // Major type 1 (negative int) with additional info 31 should throw
        // 0x3F = 0b00111111 = major type 1, additional info 31
        val invalidBytes = byteArrayOf(0x3F)
        assertFailsWith<IllegalArgumentException> {
            Cbor.decode<CborItem<*>>(invalidBytes)
        }
    }

    @Test
    fun testCborDecodeTagWithIndefiniteLength() {
        // Major type 6 (tag) with additional info 31 should throw
        // 0xDF = 0b11011111 = major type 6, additional info 31
        val invalidBytes = byteArrayOf(0xDF.toByte())
        assertFailsWith<IllegalArgumentException> {
            Cbor.decode<CborItem<*>>(invalidBytes)
        }
    }

    @Test
    fun testCborDecodeBreakOutsideIndefiniteLength() {
        // Major type 7 (special) with additional info 31 is BREAK
        // 0xFF = 0b11111111 = major type 7, additional info 31
        val breakBytes = byteArrayOf(0xFF.toByte())
        assertFailsWith<IllegalArgumentException> {
            Cbor.decode<CborItem<*>>(breakBytes)
        }
    }

    @Test
    fun testCborDecodeWithLeftoverBytes() {
        // Encode a simple value but add extra bytes
        val encoded = Cbor.encode(CborUInt(42L))
        val withExtra = encoded + byteArrayOf(0x00)
        assertFailsWith<IllegalArgumentException> {
            Cbor.decode<CborItem<*>>(withExtra)
        }
    }

    @Test
    fun testCborDecodeFloat16() {
        // Float16 is encoded with additional info 25 (0xF9)
        // 0xF9 = majorType7 (0xE0) + 25
        // Test encoding/decoding of half precision float
        val bytes = byteArrayOf(0xF9.toByte(), 0x3C, 0x00) // 1.0 in half precision
        val result = Cbor.decode<CborItem<*>>(bytes)
        assertIs<CborFloat16>(result)
    }

    @Test
    fun testCborEncodeWithLargeLength() {
        // Test encoding with lengths that require different byte sizes
        val shortArray = CborArray((0 until 23).map { CborUInt(it.toLong()) }.toMutableList())
        val encodedShort = Cbor.encode(shortArray)
        assertNotNull(encodedShort)

        val mediumArray = CborArray((0 until 25).map { CborUInt(it.toLong()) }.toMutableList())
        val encodedMedium = Cbor.encode(mediumArray)
        assertNotNull(encodedMedium)

        val largeArray = CborArray((0 until 300).map { CborUInt(it.toLong()) }.toMutableList())
        val encodedLarge = Cbor.encode(largeArray)
        assertNotNull(encodedLarge)
    }

    @Test
    fun testCborDecodeInvalidAdditionalInfo() {
        // Additional info values 28-30 are reserved and should throw
        // 0x1C = 0b00011100 = major type 0, additional info 28
        val invalidBytes = byteArrayOf(0x1C)
        assertFailsWith<IllegalArgumentException> {
            Cbor.decode<CborItem<*>>(invalidBytes)
        }
    }

    @Test
    fun testCborDecodeOutOfBounds() {
        // Try to decode from empty array
        val emptyBytes = byteArrayOf()
        assertFailsWith<IllegalArgumentException> {
            Cbor.decode<CborItem<*>>(emptyBytes)
        }
    }

    @Test
    fun testCborDecodeInsufficientData() {
        // Additional info 24 requires 1 more byte, but we don't provide it
        val insufficientBytes = byteArrayOf(0x18) // major type 0, additional info 24, but no data byte
        // wasmJs throws RuntimeError (array bounds) instead of IllegalArgumentException
        assertFails {
            Cbor.decode<CborItem<*>>(insufficientBytes)
        }
    }

    // ========== Half-float decoding branches ==========

    @Test
    fun testCborDecodeFloat16PositiveInfinity() {
        // Positive infinity in half-float: exp=31, mant=0, sign=0
        // 0x7C00 = 0b0111110000000000
        val bytes = byteArrayOf(0xF9.toByte(), 0x7C, 0x00)
        val result = Cbor.decode<CborFloat16>(bytes)
        assertEquals(Float.POSITIVE_INFINITY, result.value)
    }

    @Test
    fun testCborDecodeFloat16NegativeInfinity() {
        // Negative infinity in half-float: exp=31, mant=0, sign=1
        // 0xFC00 = 0b1111110000000000
        val bytes = byteArrayOf(0xF9.toByte(), 0xFC.toByte(), 0x00)
        val result = Cbor.decode<CborFloat16>(bytes)
        assertEquals(Float.NEGATIVE_INFINITY, result.value)
    }

    @Test
    fun testCborDecodeFloat16NaN() {
        // NaN in half-float: exp=31, mant!=0
        // 0x7E00 = 0b0111111000000000 (quiet NaN)
        val bytes = byteArrayOf(0xF9.toByte(), 0x7E, 0x00)
        val result = Cbor.decode<CborFloat16>(bytes)
        assertTrue(result.value.isNaN())
    }

    @Test
    fun testCborDecodeFloat16Denormalized() {
        // Denormalized half-float: exp=0, mant!=0
        // This represents a very small number
        val bytes = byteArrayOf(0xF9.toByte(), 0x00, 0x01)
        val result = Cbor.decode<CborFloat16>(bytes)
        assertNotNull(result.value)
        assertTrue(result.value > 0)
    }

    @Test
    fun testCborDecodeFloat16NegativeNumber() {
        // Negative number in half-float: sign bit set
        // -1.0 in half-float: 0xBC00 = 0b1011110000000000
        val bytes = byteArrayOf(0xF9.toByte(), 0xBC.toByte(), 0x00)
        val result = Cbor.decode<CborFloat16>(bytes)
        assertEquals(-1.0f, result.value)
    }

    @Test
    fun testCborDecodeFloat16Zero() {
        // Positive zero in half-float: 0x0000
        val bytes = byteArrayOf(0xF9.toByte(), 0x00, 0x00)
        val result = Cbor.decode<CborFloat16>(bytes)
        assertEquals(0.0f, result.value)
    }

    @Test
    fun testCborDecodeFloat16NegativeZero() {
        // Negative zero in half-float: 0x8000
        val bytes = byteArrayOf(0xF9.toByte(), 0x80.toByte(), 0x00)
        val result = Cbor.decode<CborFloat16>(bytes)
        assertEquals(-0.0f, result.value)
    }

    // ========== Additional CDDL fromJson branches ==========

    @Test
    fun testCDDLBstrIndefLengthNewByteString() {
        val chunks = listOf(byteArrayOf(1, 2), byteArrayOf(3, 4))
        val result = CDDL.bstr_indef_length.newByteString(chunks)
        assertIs<CborByteStringIndefLength>(result)
    }

    @Test
    fun testCDDLTstrIndefLengthNewStringIndefLength() {
        val chunks = listOf("hello", "world")
        val result = CDDL.tstr_indef_length.newStringIndefLength(chunks)
        assertIs<CborStringIndefLength>(result)
    }

    @Test
    fun testCDDLFloatNewFloat() {
        val result = CDDL.float.newFloat(3.14f)
        assertIs<CborFloat>(result)
    }

    @Test
    fun testCDDLFloat16NewFloat16() {
        val result = CDDL.float16.newFloat16(1.5f)
        assertIs<CborFloat16>(result)
    }

    @Test
    fun testCDDLFloat32NewFloat32() {
        val result = CDDL.float32.newFloat32(2.5f)
        assertIs<CborFloat32>(result)
    }

    @Test
    fun testCDDLFloat64NewFloat64() {
        val result = CDDL.float64.newFloat64(1.23456789)
        assertIs<CborDouble>(result)
    }

    @Test
    fun testCDDLTdateNewTDate() {
        val result = CDDL.tdate.newTDate("2024-01-15T10:30:00Z")
        assertIs<CborTDate>(result)
    }

    @Test
    fun testCDDLFullDateNewFullDate() {
        val result = CDDL.full_date.newFullDate("2024-01-15")
        assertIs<CborFullDate>(result)
    }

    @Test
    fun testCDDLTimeNewTime() {
        val result = CDDL.time.newTime(1705312200L)
        assertIs<CborTime>(result)
    }

    @Test
    fun testCDDLTextNewText() {
        val result = CDDL.text.newText("hello")
        assertIs<CborString>(result)
    }

    @Test
    fun testCDDLBytesNewBytes() {
        val result = CDDL.bytes.newBytes(byteArrayOf(1, 2, 3))
        assertIs<CborByteString>(result)
    }

    // ========== CborTagged branch coverage tests ==========

    @Test
    fun testCborTaggedEqualsWithSameInstance() {
        val tagged = CborTagged(100, CborUInt(42L))
        assertTrue(tagged.equals(tagged))
    }

    @Test
    fun testCborTaggedEqualsWithNull() {
        val tagged = CborTagged(100, CborUInt(42L))
        assertFalse(tagged.equals(null))
    }

    @Test
    fun testCborTaggedEqualsWithDifferentClass() {
        val tagged = CborTagged(100, CborUInt(42L))
        assertFalse(tagged.equals("not a tagged"))
    }

    @Test
    fun testCborTaggedEqualsWithDifferentTagNumber() {
        val tagged1 = CborTagged(100, CborUInt(42L))
        val tagged2 = CborTagged(200, CborUInt(42L))
        assertFalse(tagged1.equals(tagged2))
    }

    @Test
    fun testCborTaggedEqualsWithDifferentTaggedItem() {
        val tagged1 = CborTagged(100, CborUInt(42L))
        val tagged2 = CborTagged(100, CborUInt(99L))
        assertFalse(tagged1.equals(tagged2))
    }

    @Test
    fun testCborTaggedEqualsWithEqual() {
        val tagged1 = CborTagged(100, CborUInt(42L))
        val tagged2 = CborTagged(100, CborUInt(42L))
        assertTrue(tagged1.equals(tagged2))
    }

    @Test
    fun testCborTaggedHashCode() {
        val tagged1 = CborTagged(100, CborUInt(42L))
        val tagged2 = CborTagged(100, CborUInt(42L))
        assertEquals(tagged1.hashCode(), tagged2.hashCode())
    }

    @Test
    fun testCborTaggedDecodeGenericTag() {
        // Create a tagged item with a non-standard tag (not 0, 1, 24, or 1004)
        val tagged = CborTagged(999, CborString("test"))
        val encoded = Cbor.encode(tagged)
        val decoded = Cbor.decode<CborTagged<*>>(encoded)
        assertEquals(999, decoded.tagNumber)
    }

    @Test
    fun testCborTaggedToJsonSimple() {
        val tagged = CborTagged(100, CborString("test"))
        val json = tagged.toJsonSimple()
        assertNotNull(json)
    }

    @Test
    fun testCborTaggedEncodeAndDecode() {
        val original = CborTagged(55799, CborUInt(123L)) // Self-describe CBOR tag
        val encoded = Cbor.encode(original)
        val decoded = Cbor.decode<CborTagged<*>>(encoded)
        assertEquals(55799, decoded.tagNumber)
    }

    // ========== CborTDate and CborTime decode branches ==========

    @Test
    fun testCborTDateDecode() {
        // Tag 0 with a date-time string
        val tdate = CborTDate("2024-01-15T10:30:00Z")
        val encoded = Cbor.encode(tdate)
        val decoded = Cbor.decode<CborTagged<*>>(encoded)
        assertEquals(CborTagged.DATE_TIME_STRING, decoded.tagNumber)
        assertEquals("2024-01-15T10:30:00Z", (decoded.taggedItem as CborString).value)
    }

    @Test
    fun testCborTimeDecode() {
        // Tag 1 with epoch time
        val time = CborTime(1705312200L)
        val encoded = Cbor.encode(time)
        val decoded = Cbor.decode<CborTagged<*>>(encoded)
        assertEquals(CborTagged.DATE_TIME_NUMBER, decoded.tagNumber)
        assertEquals(1705312200L, (decoded.taggedItem as CborUInt).value)
    }

    @Test
    fun testCborFullDateDecode() {
        // Tag 1004 with a full date string
        val fullDate = CborFullDate("2024-01-15")
        val encoded = Cbor.encode(fullDate)
        val decoded = Cbor.decode<CborTagged<*>>(encoded)
        assertEquals(CborTagged.FULL_DATE_STRING, decoded.tagNumber)
        assertEquals("2024-01-15", (decoded.taggedItem as CborString).value)
    }

    // ========== CborEncodedItem decode branch ==========

    @Test
    fun testCborEncodedItemDecode() {
        // Tag 24 with encoded CBOR data
        val innerItem = CborUInt(42L)
        val encodedItem = CborEncodedItem(Cbor.encode(innerItem), innerItem)
        val encoded = Cbor.encode(encodedItem)
        val decoded = Cbor.decode<CborEncodedItem<*>>(encoded)
        assertNotNull(decoded)
    }

    // ========== CborItem property branch tests ==========

    @Test
    fun testCborItemAsStrSuccess() {
        val item: CborItem<*> = CborString("test")
        assertEquals("test", item.asStr)
    }

    @Test
    fun testCborItemAsStrFails() {
        val item: CborItem<*> = CborUInt(42L)
        assertFailsWith<IllegalArgumentException> {
            item.asStr
        }
    }

    @Test
    fun testCborItemAsBoolSuccess() {
        val item: CborItem<*> = CborTrue()
        assertEquals(true, item.asBool)
    }

    @Test
    fun testCborItemAsBoolFails() {
        val item: CborItem<*> = CborString("test")
        assertFailsWith<IllegalArgumentException> {
            item.asBool
        }
    }

    @Test
    fun testCborItemAsBstrSuccess() {
        val item: CborItem<*> = CborByteString(byteArrayOf(1, 2, 3))
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(item.asBstr))
    }

    @Test
    fun testCborItemAsBstrFails() {
        val item: CborItem<*> = CborString("test")
        assertFailsWith<IllegalArgumentException> {
            item.asBstr
        }
    }

    @Test
    fun testCborItemAsLongFromUInt() {
        val item: CborItem<*> = CborUInt(100L)
        assertEquals(100L, item.asLong)
    }

    @Test
    fun testCborItemAsLongFromNInt() {
        val item: CborItem<*> = CborNInt(50L)
        assertEquals(-50L, item.asLong)
    }

    @Test
    fun testCborItemAsLongFails() {
        val item: CborItem<*> = CborString("test")
        assertFailsWith<IllegalArgumentException> {
            item.asLong
        }
    }

    @Test
    fun testCborItemAsIntFromUInt() {
        val item: CborItem<*> = CborUInt(100L)
        assertEquals(100, item.asInt)
    }

    @Test
    fun testCborItemAsIntFromNInt() {
        val item: CborItem<*> = CborNInt(50L)
        assertEquals(-50, item.asInt)
    }

    @Test
    fun testCborItemAsIntOverflowUInt() {
        val item: CborItem<*> = CborUInt(Long.MAX_VALUE)
        assertFailsWith<IllegalArgumentException> {
            item.asInt
        }
    }

    @Test
    fun testCborItemAsIntOverflowNInt() {
        val item: CborItem<*> = CborNInt(Long.MAX_VALUE)
        assertFailsWith<IllegalArgumentException> {
            item.asInt
        }
    }

    @Test
    fun testCborItemAsIntFails() {
        val item: CborItem<*> = CborString("test")
        assertFailsWith<IllegalArgumentException> {
            item.asInt
        }
    }

    @Test
    fun testCborItemAsMapSuccess() {
        val map =
            CborMap<CborItem<*>, CborItem<*>?>(
                mutableMapOf(
                    CborString("key") to CborUInt(1L),
                ),
            )
        val item: CborItem<*> = map
        assertNotNull(item.asMap)
    }

    @Test
    fun testCborItemAsMapFails() {
        val item: CborItem<*> = CborString("test")
        assertFailsWith<IllegalArgumentException> {
            item.asMap
        }
    }

    @Test
    fun testCborItemAsListSuccess() {
        val array = CborArray(mutableListOf(CborUInt(1L)))
        val item: CborItem<*> = array
        assertNotNull(item.asList)
    }

    @Test
    fun testCborItemAsListFails() {
        val item: CborItem<*> = CborString("test")
        assertFailsWith<IllegalArgumentException> {
            item.asList
        }
    }

    @Test
    fun testCborItemAsTaggedSubjectSuccess() {
        val tagged = CborTagged(100, CborUInt(42L))
        assertEquals(CborUInt(42L), tagged.asTaggedSubject)
    }

    @Test
    fun testCborItemAsTaggedSubjectFails() {
        val item: CborItem<*> = CborUInt(42L)
        assertFailsWith<IllegalArgumentException> {
            item.asTaggedSubject
        }
    }

    @Test
    fun testCborItemToJsonWithCddl() {
        val item = CborUInt(42L)
        val json = item.toJson(includeCDDL = true)
        assertIs<JsonObject>(json)
    }

    @Test
    fun testCborItemToJsonWithoutCddl() {
        val item = CborUInt(42L)
        val json = item.toJson(includeCDDL = false)
        assertIs<JsonPrimitive>(json)
    }

    @Test
    fun testCborItemEqualsWithSameInstance() {
        val item = CborUInt(42L)
        assertTrue(item.equals(item))
    }

    @Test
    fun testCborItemEqualsWithDifferentType() {
        val item = CborUInt(42L)
        assertFalse(item.equals("not a cbor item"))
    }

    @Test
    fun testCborItemEqualsWithDifferentValue() {
        val item1 = CborUInt(42L)
        val item2 = CborUInt(99L)
        assertFalse(item1.equals(item2))
    }

    @Test
    fun testCborItemEqualsWithDifferentMajorType() {
        val item1 = CborUInt(42L)
        val item2 = CborString("42")
        assertFalse(item1.equals(item2))
    }

    @Test
    fun testCborItemHashCode() {
        val item1 = CborUInt(42L)
        val item2 = CborUInt(42L)
        assertEquals(item1.hashCode(), item2.hashCode())
    }

    @Test
    fun testCborItemToBstr() {
        val item = CborUInt(42L)
        val bstr = item.toBstr()
        assertIs<CborByteString>(bstr)
    }
}
