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

import com.sphereon.cbor.CborConst.CDDL_LITERAL
import com.sphereon.cbor.CborConst.KEY_LITERAL
import com.sphereon.cbor.CborConst.VALUE_LITERAL
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for CDDL JSON conversion methods including newCborItemFromJson, fromJson, and toTag
 */
class CDDLJsonConversionTest {

    // ========== CDDL newCborItemFromJson branches ==========

    @Test
    fun testNewCborItemFromJsonNull() {
        val result = CDDL.any.newCborItemFromJson(JsonNull, CDDL.any)
        assertIs<CborNull>(result)
    }

    @Test
    fun testNewCborItemFromJsonNullElement() {
        val result = CDDL.any.newCborItemFromJson(null, CDDL.any)
        assertIs<CborNull>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithStringPrimitive() {
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive("test"), CDDL.any)
        assertIs<CborString>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithJsonArray() {
        val json = buildJsonArray {
            add(JsonPrimitive(1))
            add(JsonPrimitive(2))
        }
        val result = CDDL.any.newCborItemFromJson(json, CDDL.any)
        assertIs<CborArray<*>>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithCborItemValueJson() {
        val json = buildJsonObject {
            put(CDDL_LITERAL, JsonPrimitive("tstr"))
            put(VALUE_LITERAL, JsonPrimitive("test"))
        }
        val result = CDDL.any.newCborItemFromJson(json, CDDL.any)
        assertIs<CborString>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithCborItemJsonWithKey() {
        val json = buildJsonObject {
            put(CDDL_LITERAL, JsonPrimitive("tstr"))
            put(VALUE_LITERAL, JsonPrimitive("test"))
            put(KEY_LITERAL, JsonPrimitive("mykey"))
        }
        val result = CDDL.any.newCborItemFromJson(json, CDDL.any)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithRegularObject() {
        val json = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive(42))
        }
        val result = CDDL.any.newCborItemFromJson(json, CDDL.any)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithPrimitiveAndNullCddl() {
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive(42), null)
        assertNotNull(result)
    }

    @Test
    fun testNewCborItemFromJsonWithBoolCddl() {
        val result = CDDL.bool.newCborItemFromJson(JsonPrimitive(true), CDDL.bool)
        assertIs<CborTrue>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithFalseCddl() {
        val result = CDDL.False.newCborItemFromJson(JsonPrimitive(false), CDDL.False)
        assertIs<CborFalse>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithTrueCddl() {
        val result = CDDL.True.newCborItemFromJson(JsonPrimitive(true), CDDL.True)
        assertIs<CborTrue>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithBstrCddl() {
        // bstr fromJson expects base64url encoded string
        val result = CDDL.bstr.fromJson(JsonPrimitive("AQIDBA"))
        assertIs<CborByteString>(result)
        assertEquals(4, result.value.size)
    }

    @Test
    fun testNewCborItemFromJsonWithFloat16Cddl() {
        val result = CDDL.float16.newCborItemFromJson(JsonPrimitive(3.14f), CDDL.float16)
        assertIs<CborFloat16>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithFloat32Cddl() {
        val result = CDDL.float32.newCborItemFromJson(JsonPrimitive(3.14f), CDDL.float32)
        assertIs<CborFloat32>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithTextCddl() {
        val result = CDDL.text.newCborItemFromJson(JsonPrimitive("test"), CDDL.text)
        assertIs<CborString>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithTimeCddl() {
        val result = CDDL.time.newCborItemFromJson(JsonPrimitive(1705363200), CDDL.time)
        assertIs<CborTime>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithNilCddl() {
        val result = CDDL.nil.newCborItemFromJson(JsonNull, CDDL.nil)
        assertIs<CborNull>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithUndefinedCddl() {
        val result = CDDL.undefined.newCborItem(Unit)
        assertIs<CborUndefined>(result)
    }

    // ========== CDDL newCborItem branches ==========

    @Test
    fun testNewCborItemWithNull() {
        val result = CDDL.nil.newCborItem(null)
        assertIs<CborNull>(result)
    }

    @Test
    fun testNewCborItemWithUnit() {
        val result = CDDL.undefined.newCborItem(Unit)
        assertIs<CborUndefined>(result)
    }

    @Test
    fun testNewCborItemTstrWithJsonPrimitive() {
        val result = CDDL.tstr.newCborItem(JsonPrimitive("test"))
        assertIs<CborString>(result)
    }

    @Test
    fun testNewCborItemBoolWithJsonPrimitive() {
        val result = CDDL.bool.newCborItem(JsonPrimitive(true))
        assertIs<CborTrue>(result)
    }

    @Test
    fun testNewCborItemBstrIndefLength() {
        val chunks: List<cddl_bstr> = listOf(byteArrayOf(1, 2), byteArrayOf(3, 4))
        val result = CDDL.bstr_indef_length.newByteString(chunks)
        assertIs<CborByteStringIndefLength>(result)
    }

    @Test
    fun testNewCborItemFloat16() {
        val result = CDDL.float16.newCborItem(1.5f)
        assertIs<CborFloat16>(result)
    }

    @Test
    fun testNewCborItemFloat32() {
        val result = CDDL.float32.newCborItem(2.5f)
        assertIs<CborFloat32>(result)
    }

    @Test
    fun testNewCborItemBytes() {
        val result = CDDL.bytes.newCborItem(byteArrayOf(1, 2, 3))
        assertIs<CborByteString>(result)
    }

    @Test
    fun testNewCborItemText() {
        val result = CDDL.text.newCborItem("hello")
        assertIs<CborString>(result)
    }

    @Test
    fun testNewCborItemNInt() {
        val result = CDDL.nint.newCborItem(-42)
        assertIs<CborNInt>(result)
    }

    @Test
    fun testNewCborItemNIntWithLong() {
        val result = CDDL.nint.newCborItem(-100L)
        assertIs<CborNInt>(result)
    }

    @Test
    fun testNewCborItemTime() {
        val result = CDDL.time.newCborItem(1705363200L)
        assertIs<CborTime>(result)
    }

    @Test
    fun testNewCborItemListWithCborArray() {
        val array = CborArray(mutableListOf(CborUInt(1)))
        val result = CDDL.list.newCborItem(array)
        assertIs<CborArray<*>>(result)
    }

    @Test
    fun testNewCborItemListWithMutableList() {
        val list = mutableListOf<CborItem<*>>(CborUInt(1), CborString("two"), CborTrue())
        val result = CDDL.list.newCborItem(list)
        assertIs<CborArray<*>>(result)
    }

    @Test
    fun testNewCborItemMapWithCborMap() {
        val map = CborMap(mutableMapOf(CborString("key") to CborUInt(1)))
        val result = CDDL.map.newCborItem(map)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testNewCborItemMapWithMutableMap() {
        val map = mutableMapOf<Any, Any>("key" to "value")
        val result = CDDL.map.newCborItem(map)
        assertIs<CborMap<*, *>>(result)
    }

    // ========== CDDL.any newCborItem branches ==========

    @Test
    fun testAnyNewCborItemWithString() {
        val result = CDDL.any.newCborItem("test")
        assertIs<CborString>(result)
    }

    @Test
    fun testAnyNewCborItemWithLong() {
        val result = CDDL.any.newCborItem(42L)
        assertIs<CborUInt>(result)
    }

    @Test
    fun testAnyNewCborItemWithInt() {
        val result = CDDL.int.newInt(42)
        assertIs<CborUInt>(result)
    }

    @Test
    fun testAnyNewCborItemWithByteArray() {
        val result = CDDL.any.newCborItem(byteArrayOf(1, 2, 3))
        assertIs<CborByteString>(result)
    }

    @Test
    fun testAnyNewCborItemWithFloat() {
        val result = CDDL.any.newCborItem(3.14f)
        assertIsCborFloat(result)
    }

    @Test
    fun testAnyNewCborItemWithDouble() {
        val result = CDDL.any.newCborItem(3.14159)
        assertIs<CborDouble>(result)
    }

    @Test
    fun testAnyNewCborItemWithBoolean() {
        val resultTrue = CDDL.any.newCborItem(true)
        assertIs<CborTrue>(resultTrue)

        val resultFalse = CDDL.any.newCborItem(false)
        assertIs<CborFalse>(resultFalse)
    }

    @Test
    fun testAnyNewCborItemWithList() {
        val list = mutableListOf<CborItem<*>>(CborUInt(1), CborUInt(2), CborUInt(3))
        val result = CDDL.any.newCborItem(list)
        assertIs<CborArray<*>>(result)
    }

    @Test
    fun testAnyNewCborItemWithMap() {
        val map = mutableMapOf<Any, Any>("key" to "value")
        val result = CDDL.any.newCborItem(map)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testAnyNewCborItemWithJsonPrimitiveBoolTrue() {
        val result = CDDL.any.newCborItem(JsonPrimitive(true))
        assertIs<CborTrue>(result)
    }

    @Test
    fun testAnyNewCborItemWithJsonPrimitiveBoolFalse() {
        val result = CDDL.any.newCborItem(JsonPrimitive(false))
        assertIs<CborFalse>(result)
    }

    @Test
    fun testAnyNewCborItemWithJsonPrimitiveFloat() {
        val result = CDDL.any.newCborItem(JsonPrimitive(3.14))
        assertIs<CborDouble>(result)
    }

    @Test
    fun testAnyNewCborItemWithJsonPrimitiveInt() {
        val result = CDDL.any.newCborItem(JsonPrimitive(42))
        assertIs<CborUInt>(result)
    }

    // ========== CDDL specific fromJson methods ==========

    @Test
    fun testTstrFromJson() {
        val result = CDDL.tstr.fromJson(JsonPrimitive("hello"))
        assertEquals("hello", result.value)
    }

    @Test
    fun testUintFromJson() {
        val result = CDDL.uint.fromJson(JsonPrimitive(42))
        assertEquals(42L, result.value)
    }

    @Test
    fun testNintFromJson() {
        // nint.fromJson parses the long value directly - CborNInt stores absolute value
        val result = CDDL.nint.fromJson(JsonPrimitive(42))
        assertEquals(42L, result.value)
    }

    @Test
    fun testIntFromJsonPositive() {
        val result = CDDL.int.fromJson(JsonPrimitive(42))
        assertIs<CborUInt>(result)
    }

    @Test
    fun testIntFromJsonNegative() {
        val result = CDDL.int.fromJson(JsonPrimitive(-42))
        assertIs<CborNInt>(result)
    }

    @Test
    fun testBstrFromJson() {
        val result = CDDL.bstr.fromJson(JsonPrimitive("AQIDBA"))
        assertEquals(4, result.value.size)
    }

    @Test
    fun testBytesFromJson() {
        val result = CDDL.bytes.fromJson(JsonPrimitive("AQIDBA"))
        assertEquals(4, result.value.size)
    }

    @Test
    fun testTextFromJson() {
        val result = CDDL.text.fromJson(JsonPrimitive("test"))
        assertEquals("test", result.value)
    }

    @Test
    fun testBoolFromJsonTrue() {
        val result = CDDL.bool.fromJson(JsonPrimitive(true))
        assertIs<CborTrue>(result)
    }

    @Test
    fun testBoolFromJsonFalse() {
        val result = CDDL.bool.fromJson(JsonPrimitive(false))
        assertIs<CborFalse>(result)
    }

    @Test
    fun testFloatFromJson() {
        val result = CDDL.float.fromJson(JsonPrimitive(3.14f))
        assertIs<CborFloat32>(result)
    }

    @Test
    fun testFloat16FromJson() {
        val result = CDDL.float16.fromJson(JsonPrimitive(1.5f))
        assertIs<CborFloat16>(result)
    }

    @Test
    fun testFloat32FromJson() {
        val result = CDDL.float32.fromJson(JsonPrimitive(2.5f))
        assertIs<CborFloat32>(result)
    }

    @Test
    fun testFloat64FromJson() {
        val result = CDDL.float64.fromJson(JsonPrimitive(3.14159))
        assertIs<CborDouble>(result)
    }

    @Test
    fun testTdateFromJson() {
        val result = CDDL.tdate.fromJson(JsonPrimitive("2024-06-15T10:30:00Z"))
        assertEquals("2024-06-15T10:30:00Z", result.value)
    }

    @Test
    fun testFullDateFromJson() {
        val result = CDDL.full_date.fromJson(JsonPrimitive("2024-06-15T00:00:00Z"))
        assertEquals("2024-06-15T00:00:00Z", result.value)
    }

    @Test
    fun testTimeFromJson() {
        val result = CDDL.time.fromJson(JsonPrimitive(1705363200))
        assertEquals(1705363200L, result.value)
    }

    // ========== CDDL toTag ==========

    @Test
    fun testCDDLToTag() {
        val tag = CDDL.tstr.toTag()
        assertNotNull(tag)
    }

    @Test
    fun testCDDLToTagWithAdditionalInfo() {
        val tag = CDDL.bstr.toTag(24)
        assertNotNull(tag)
    }

    // ========== CDDL util ==========

    @Test
    fun testCDDLUtilFromFormatAllTypes() {
        assertEquals(CDDL.tstr, CDDL.util.fromFormat("tstr"))
        assertEquals(CDDL.uint, CDDL.util.fromFormat("uint"))
        assertEquals(CDDL.nint, CDDL.util.fromFormat("nint"))
        assertEquals(CDDL.int, CDDL.util.fromFormat("int"))
        assertEquals(CDDL.bstr, CDDL.util.fromFormat("bstr"))
        assertEquals(CDDL.bool, CDDL.util.fromFormat("bool"))
        assertEquals(CDDL.map, CDDL.util.fromFormat("map"))
        assertEquals(CDDL.list, CDDL.util.fromFormat("list"))
        assertEquals(CDDL.Null, CDDL.util.fromFormat("null"))
        assertEquals(CDDL.any, CDDL.util.fromFormat("any"))
        assertEquals(CDDL.float, CDDL.util.fromFormat("float"))
        assertEquals(CDDL.float16, CDDL.util.fromFormat("float16"))
        assertEquals(CDDL.float32, CDDL.util.fromFormat("float32"))
        assertEquals(CDDL.float64, CDDL.util.fromFormat("float64"))
        assertEquals(CDDL.tdate, CDDL.util.fromFormat("tdate"))
        assertEquals(CDDL.full_date, CDDL.util.fromFormat("full-date"))
        assertEquals(CDDL.time, CDDL.util.fromFormat("time"))
        assertEquals(CDDL.text, CDDL.util.fromFormat("text"))
        assertEquals(CDDL.bytes, CDDL.util.fromFormat("bytes"))
        assertEquals(CDDL.nil, CDDL.util.fromFormat("nil"))
        assertEquals(CDDL.undefined, CDDL.util.fromFormat("undefined"))
    }

    // ========== CDDL new* methods ==========

    @Test
    fun testCDDLNewString() {
        val result = CDDL.tstr.newString("test")
        assertEquals("test", result.value)
    }

    @Test
    fun testCDDLNewUint() {
        val result = CDDL.uint.newUint(100L)
        assertEquals(100L, result.value)
    }

    @Test
    fun testCDDLNewNInt() {
        // newNInt takes the absolute value to store
        val result = CDDL.nint.newNInt(50L)
        assertEquals(50L, result.value)
    }

    @Test
    fun testCDDLNewByteString() {
        val result = CDDL.bstr.newByteString(byteArrayOf(1, 2, 3))
        assertEquals(3, result.value.size)
    }

    @Test
    fun testCDDLNewBytes() {
        val result = CDDL.bytes.newBytes(byteArrayOf(1, 2))
        assertEquals(2, result.value.size)
    }

    @Test
    fun testCDDLNewText() {
        val result = CDDL.text.newText("hello")
        assertEquals("hello", result.value)
    }

    @Test
    fun testCDDLNewFloat() {
        val result = CDDL.float.newFloat(1.5f)
        assertEquals(1.5f, result.value)
    }

    @Test
    fun testCDDLNewFloat16() {
        val result = CDDL.float16.newFloat16(1.0f)
        assertIs<CborFloat16>(result)
    }

    @Test
    fun testCDDLNewFloat32() {
        val result = CDDL.float32.newFloat32(2.0f)
        assertIs<CborFloat32>(result)
    }

    @Test
    fun testCDDLNewFloat64() {
        val result = CDDL.float64.newFloat64(3.14159)
        assertEquals(3.14159, result.value)
    }

    @Test
    fun testCDDLNewBool() {
        val trueResult = CDDL.bool.newBool(true)
        assertIs<CborTrue>(trueResult)

        val falseResult = CDDL.bool.newBool(false)
        assertIs<CborFalse>(falseResult)
    }

    @Test
    fun testCDDLNewTDate() {
        val result = CDDL.tdate.newTDate("2024-06-15T10:30:00Z")
        assertEquals("2024-06-15T10:30:00Z", result.value)
    }

    @Test
    fun testCDDLNewFullDate() {
        val result = CDDL.full_date.newFullDate("2024-06-15T00:00:00Z")
        assertEquals("2024-06-15T00:00:00Z", result.value)
    }

    @Test
    fun testCDDLNewTime() {
        val result = CDDL.time.newTime(1705363200L)
        assertEquals(1705363200L, result.value)
    }

    @Test
    fun testCDDLNewNil() {
        val result = CDDL.nil.newNil()
        assertIs<CborNull>(result)
    }

    @Test
    fun testCDDLNewNull() {
        val result = CDDL.Null.newNull()
        assertIs<CborNull>(result)
    }

    @Test
    fun testCDDLNewTrue() {
        val result = CDDL.True.newTrue()
        assertIs<CborTrue>(result)
    }

    @Test
    fun testCDDLNewFalse() {
        val result = CDDL.False.newFalse()
        assertIs<CborFalse>(result)
    }

    @Test
    fun testCDDLNewUndefined() {
        val result = CDDL.undefined.newUndefined()
        assertIs<CborUndefined>(result)
    }

    @Test
    fun testCDDLNewList() {
        val result = CDDL.list.newList(mutableListOf(CborUInt(1), CborUInt(2)))
        assertEquals(2, result.value.size)
    }

    @Test
    fun testCDDLNewMap() {
        val result = CDDL.map.newMap(mutableMapOf(CborString("key") to CborUInt(1)))
        assertEquals(1, result.value.size)
    }

    @Test
    fun testCDDLNewStringIndefLength() {
        val result = CDDL.tstr_indef_length.newStringIndefLength(listOf("hello", "world"))
        assertEquals(2, result.value.size)
    }

    // ========== Map extension functions ==========

    @Test
    fun testMapGetStringLabel() {
        val map = mapOf("key" to "value")
        val result = map.getStringLabel<String>("key")
        assertEquals("value", result)
    }

    @Test
    fun testMapGetNumberLabel() {
        val map = mapOf(42 to "value")
        val result = map.getNumberLabel<String>(42L)
        assertEquals("value", result)
    }

    // ========== Additional CDDL.list branch tests ==========

    @Test
    fun testListFromJsonWithJsonArray() {
        val json = buildJsonArray {
            add(JsonPrimitive(1))
            add(JsonPrimitive(2))
            add(JsonPrimitive(3))
        }
        val result = CDDL.list.fromJson(json)
        assertIs<CborArray<*>>(result)
        assertEquals(3, result.value.size)
    }

    @Test
    fun testListFromJsonWithCborItemJsonArray() {
        // Test with array of CDDL-formatted JSON objects
        val json = buildJsonArray {
            add(buildJsonObject {
                put(CDDL_LITERAL, JsonPrimitive("uint"))
                put(VALUE_LITERAL, JsonPrimitive(100))
            })
            add(buildJsonObject {
                put(CDDL_LITERAL, JsonPrimitive("tstr"))
                put(VALUE_LITERAL, JsonPrimitive("test"))
            })
        }
        val result = CDDL.list.fromJson(json)
        assertIs<CborArray<*>>(result)
        assertEquals(2, result.value.size)
    }

    @Test
    fun testListNewCborItemFromJsonWithArray() {
        val json = buildJsonArray {
            add(JsonPrimitive("a"))
            add(JsonPrimitive("b"))
        }
        val result = CDDL.list.newCborItemFromJson(json, CDDL.list)
        assertIs<CborArray<*>>(result)
    }

    @Test
    fun testListNewCborItemWithJsonArray() {
        val json = buildJsonArray {
            add(JsonPrimitive(1))
        }
        val result = CDDL.list.newCborItem(json)
        assertIs<CborArray<*>>(result)
    }

    @Test
    fun testListNewCborItemWithCborItemList() {
        val list = listOf(CborString("a"), CborString("b"))
        val result = CDDL.list.newCborItem(list)
        assertIs<CborArray<*>>(result)
    }

    // ========== Additional CDDL.map branch tests ==========

    @Test
    fun testMapFromJsonWithJsonObject() {
        val json = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive(42))
        }
        val result = CDDL.map.fromJson(json)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testMapFromJsonWithCborItemJsonObject() {
        // Test with map containing CDDL-formatted JSON values
        val json = buildJsonObject {
            put("mykey", buildJsonObject {
                put(CDDL_LITERAL, JsonPrimitive("uint"))
                put(VALUE_LITERAL, JsonPrimitive(42))
            })
        }
        val result = CDDL.map.fromJson(json)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testMapNewCborItemFromJsonWithObject() {
        val json = buildJsonObject {
            put("key", JsonPrimitive("value"))
        }
        val result = CDDL.map.newCborItemFromJson(json, CDDL.map)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testMapNewCborItemWithJsonObject() {
        val json = buildJsonObject {
            put("test", JsonPrimitive(123))
        }
        val result = CDDL.map.newCborItem(json)
        assertIs<CborMap<*, *>>(result)
    }

    // ========== CDDL.bstr_indef_length tests ==========

    @Test
    fun testBstrIndefLengthFromJsonNotImplemented() {
        // bstr_indef_length.fromJson is not implemented
        val json = buildJsonArray {
            add(JsonPrimitive("AQID"))
        }
        assertFailsWith<NotImplementedError> {
            CDDL.bstr_indef_length.fromJson(json)
        }
    }

    // ========== CDDL.tstr_indef_length tests ==========

    @Test
    fun testTstrIndefLengthFromJsonNotImplemented() {
        // tstr_indef_length.fromJson is not implemented
        val json = buildJsonArray {
            add(JsonPrimitive("hello"))
        }
        assertFailsWith<NotImplementedError> {
            CDDL.tstr_indef_length.fromJson(json)
        }
    }

    // ========== CDDL.any additional branches ==========

    @Test
    fun testAnyNewCborItemFromJsonWithJsonNull() {
        val result = CDDL.any.newCborItemFromJson(JsonNull, CDDL.any)
        assertIs<CborNull>(result)
    }

    // ========== CDDL util fromTag ==========

    @Test
    fun testCDDLUtilFromTagWithAdditionalInfo() {
        // Test fromTag with additional info (like #7.22)
        val nil = CDDL.util.fromTag("#7.22")
        assertEquals(CDDL.nil, nil)
    }

    @Test
    fun testCDDLUtilFromTagWithFloat() {
        // Test tag for float64 (#7.27)
        val float64 = CDDL.util.fromTag("#7.27")
        assertEquals(CDDL.float64, float64)
    }

    // ========== CDDL int edge cases ==========

    @Test
    fun testIntNewCborItemWithZero() {
        val result = CDDL.int.newCborItem(0)
        assertIs<CborUInt>(result)
        assertEquals(0L, result.value)
    }

    @Test
    fun testIntNewIntWithZero() {
        // Use the newInt method
        val result = CDDL.int.newInt(0)
        assertIs<CborUInt>(result)
    }

    @Test
    fun testIntFromJsonWithZero() {
        val result = CDDL.int.fromJson(JsonPrimitive(0))
        assertIs<CborUInt>(result)
    }

    // ========== CDDL newCborItem with JsonObject ==========

    @Test
    fun testMapNewCborItemWithCborMap() {
        val cborMap = CborMap(mutableMapOf(CborString("key") to CborUInt(1)))
        val result = CDDL.map.newCborItem(cborMap)
        assertIs<CborMap<*, *>>(result)
    }

    // ========== CDDL toTag variations ==========

    @Test
    fun testCDDLListToTag() {
        val tag = CDDL.list.toTag()
        assertTrue(tag.startsWith("#"))
    }

    @Test
    fun testCDDLMapToTag() {
        val tag = CDDL.map.toTag()
        assertTrue(tag.startsWith("#"))
    }

    @Test
    fun testCDDLNIntToTag() {
        val tag = CDDL.nint.toTag()
        assertTrue(tag.startsWith("#"))
    }

    // ========== CDDL list fromJson branches ==========

    @Test
    fun testListFromJsonWithNestedJsonObject() {
        // Test the JsonObject branch that is NOT CborItemValueJson
        val json = buildJsonArray {
            add(buildJsonObject {
                put("key1", JsonPrimitive("value1"))
                put("key2", JsonPrimitive(42))
            })
        }
        val result = CDDL.list.fromJson(json)
        assertIs<CborArray<*>>(result)
        assertEquals(1, result.value.size)
        // The inner object should be converted to a CborMap
        assertIs<CborMap<*, *>>(result.value[0])
    }

    @Test
    fun testListFromJsonWithNestedJsonArray() {
        // Test the JsonArray branch (nested array)
        val json = buildJsonArray {
            add(buildJsonArray {
                add(JsonPrimitive(1))
                add(JsonPrimitive(2))
            })
        }
        val result = CDDL.list.fromJson(json)
        assertIs<CborArray<*>>(result)
        assertEquals(1, result.value.size)
        assertIs<CborArray<*>>(result.value[0])
    }

    @Test
    fun testListFromJsonWithCborItemValueJson() {
        // Test the JsonObject with CborItemValueJson branch
        val json = buildJsonArray {
            add(buildJsonObject {
                put(CDDL_LITERAL, JsonPrimitive("uint"))
                put(VALUE_LITERAL, JsonPrimitive(100))
            })
        }
        val result = CDDL.list.fromJson(json)
        assertIs<CborArray<*>>(result)
        assertEquals(1, result.value.size)
        assertIs<CborUInt>(result.value[0])
    }

    // ========== CDDL map fromJson branches ==========

    @Test
    fun testMapFromJsonWithNestedArray() {
        // Test map with array value
        val json = buildJsonObject {
            put("arr", buildJsonArray {
                add(JsonPrimitive(1))
                add(JsonPrimitive(2))
            })
        }
        val result = CDDL.map.fromJson(json)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testMapFromJsonWithNestedMap() {
        // Test map with nested map value
        val json = buildJsonObject {
            put("nested", buildJsonObject {
                put("inner", JsonPrimitive("value"))
            })
        }
        val result = CDDL.map.fromJson(json)
        assertIs<CborMap<*, *>>(result)
    }

    // ========== CDDL base newCborItem branches ==========

    @Test
    fun testNewCborItemWithLongPositive() {
        val result = CDDL.uint.newCborItem(42L)
        assertIs<CborUInt>(result)
    }

    @Test
    fun testNewCborItemWithCborItemDirectly() {
        // Pass a CborItem directly - should return it
        val original = CborString("test")
        val result = CDDL.tstr.newCborItem(original)
        assertIs<CborString>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithNestedCborItemJson() {
        // Test CborItemJson with both key and value
        val json = buildJsonObject {
            put(CDDL_LITERAL, JsonPrimitive("uint"))
            put(VALUE_LITERAL, JsonPrimitive(42))
            put(KEY_LITERAL, JsonPrimitive("myKey"))
        }
        val result = CDDL.any.newCborItemFromJson(json, CDDL.any)
        assertIs<CborMap<*, *>>(result)
    }

    // ========== CDDL any fromJson branches ==========

    @Test
    fun testAnyFromJsonWithPrimitiveBoolean() {
        val result = CDDL.any.fromJson(JsonPrimitive(true))
        assertIs<CborTrue>(result)
    }

    @Test
    fun testAnyFromJsonWithPrimitiveNumber() {
        val result = CDDL.any.fromJson(JsonPrimitive(42))
        assertIs<CborUInt>(result)
    }

    @Test
    fun testAnyFromJsonWithPrimitiveFloat() {
        val result = CDDL.any.fromJson(JsonPrimitive(3.14))
        assertIs<CborDouble>(result)
    }

    @Test
    fun testAnyFromJsonWithJsonArray() {
        val json = buildJsonArray {
            add(JsonPrimitive(1))
        }
        val result = CDDL.any.fromJson(json)
        assertIs<CborArray<*>>(result)
    }

    // ========== CDDL toTag with alias branches ==========

    @Test
    fun testCDDLToTagWithAliasNoAdditionalInfo() {
        // bool has aliasFor = [False, True], so toTag without additionalInfo should follow alias
        val tag = CDDL.bool.toTag()
        assertNotNull(tag)
    }

    @Test
    fun testCDDLToTagWithAliasAndAdditionalInfo() {
        // Test toTag with alias and additionalInfo
        val tag = CDDL.bool.toTag(20) // 20 is FALSE
        assertNotNull(tag)
    }

    // ========== CDDL equals/hashCode ==========

    @Test
    fun testCDDLEquals() {
        // Same object
        assertTrue(CDDL.tstr.equals(CDDL.tstr))

        // Different types
        assertFalse(CDDL.tstr.equals(CDDL.bstr))
    }

    @Test
    fun testCDDLEqualsWithNonCDDL() {
        assertFalse(CDDL.tstr.equals("not a CDDL"))
    }

    @Test
    fun testCDDLHashCode() {
        val hash1 = CDDL.tstr.hashCode()
        val hash2 = CDDL.tstr.hashCode()
        assertEquals(hash1, hash2)
    }

    @Test
    fun testCDDLToString() {
        val str = CDDL.tstr.toString()
        assertTrue(str.contains("tstr"))
    }

    // ========== CDDL newCborItemFromJson with regular object ==========

    @Test
    fun testNewCborItemFromJsonWithNonCborItemValueJsonObject() {
        // Test with a regular JsonObject (not CborItemValueJson)
        val json = buildJsonObject {
            put("normalKey", JsonPrimitive("normalValue"))
            put("anotherKey", JsonPrimitive(123))
        }
        val result = CDDL.any.newCborItemFromJson(json, CDDL.any)
        // Should be converted to a CborMap
        assertIs<CborMap<*, *>>(result)
    }

    // ========== More targeted CDDL newCborItem branches ==========

    @Test
    fun testCDDLTstrNewCborItemWithString() {
        val result = CDDL.tstr.newCborItem("hello")
        assertIs<CborString>(result)
    }

    @Test
    fun testCDDLFalseNewCborItem() {
        val result = CDDL.False.newCborItem(false)
        assertIs<CborSimple<*>>(result)
    }

    @Test
    fun testCDDLNullNewCborItem() {
        val result = CDDL.Null.newCborItem(null)
        assertIs<CborItem<*>>(result)
    }

    @Test
    fun testCDDLTrueNewCborItem() {
        val result = CDDL.True.newCborItem(true)
        assertIs<CborSimple<*>>(result)
    }

    @Test
    fun testCDDLBstrNewCborItemWithByteArray() {
        val result = CDDL.bstr.newCborItem(byteArrayOf(1, 2, 3))
        assertIs<CborByteString>(result)
    }

    @Test
    fun testCDDLBstrIndefLengthNewByteString() {
        val result = CDDL.bstr_indef_length.newByteString(listOf(byteArrayOf(1, 2), byteArrayOf(3, 4)))
        assertIs<CborByteStringIndefLength>(result)
    }

    @Test
    fun testCDDLBytesNewCborItem() {
        val result = CDDL.bytes.newCborItem(byteArrayOf(1, 2, 3))
        assertIs<CborByteString>(result)
    }

    @Test
    fun testCDDLFloatNewCborItem() {
        val result = CDDL.float.newCborItem(1.5f)
        assertIs<CborFloat32>(result)
    }

    @Test
    fun testCDDLFloat16NewCborItem() {
        val result = CDDL.float16.newCborItem(1.5f)
        assertIs<CborFloat16>(result)
    }

    @Test
    fun testCDDLFloat32NewCborItem() {
        val result = CDDL.float32.newCborItem(1.5f)
        assertIs<CborFloat32>(result)
    }

    @Test
    fun testCDDLFloat64NewCborItem() {
        val result = CDDL.float64.newCborItem(1.5)
        assertIs<CborDouble>(result)
    }

    @Test
    fun testCDDLFullDateNewCborItem() {
        val result = CDDL.full_date.newCborItem("2024-06-15T00:00:00Z")
        assertIs<CborFullDate>(result)
    }

    @Test
    fun testCDDLIntNewCborItemWithPositive() {
        val result = CDDL.int.newCborItem(42)
        assertIs<CborUInt>(result)
    }

    @Test
    fun testCDDLListNewCborItemWithMutableList() {
        val result = CDDL.list.newCborItem(mutableListOf(CborUInt(1), CborUInt(2)))
        assertIs<CborArray<*>>(result)
    }

    @Test
    fun testCDDLListNewCborItemWithExistingCborArray() {
        val existingArray = CborArray(mutableListOf(CborUInt(1)))
        val result = CDDL.list.newCborItem(existingArray)
        assertIs<CborArray<*>>(result)
    }

    @Test
    fun testCDDLMapNewCborItemWithMutableMap() {
        val result = CDDL.map.newCborItem(mutableMapOf(CborString("key") to CborUInt(1) as CborItem<*>))
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testCDDLMapNewCborItemWithExistingCborMap() {
        val existingMap = CborMap(mutableMapOf(CborString("key") to CborUInt(1) as CborItem<*>))
        val result = CDDL.map.newCborItem(existingMap)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testCDDLNilNewCborItem() {
        val result = CDDL.nil.newCborItem(null)
        assertIs<CborSimple<*>>(result)
    }

    @Test
    fun testCDDLNintNewCborItemWithNumber() {
        val result = CDDL.nint.newCborItem(42)
        assertIs<CborNInt>(result)
    }

    @Test
    fun testCDDLTdateNewCborItem() {
        val result = CDDL.tdate.newCborItem("2024-06-15T10:30:00Z")
        assertIs<CborTDate>(result)
    }

    @Test
    fun testCDDLTextNewCborItem() {
        val result = CDDL.text.newCborItem("hello")
        assertIs<CborString>(result)
    }

    @Test
    fun testCDDLTimeNewCborItem() {
        val result = CDDL.time.newCborItem(1705363200L)
        assertIs<CborTime>(result)
    }

    @Test
    fun testCDDLUintNewCborItemWithLong() {
        val result = CDDL.uint.newCborItem(42L)
        assertIs<CborUInt>(result)
    }

    @Test
    fun testCDDLUintNewCborItemWithNumber() {
        val result = CDDL.uint.newCborItem(42)
        assertIs<CborUInt>(result)
    }

    @Test
    fun testCDDLUndefinedNewCborItem() {
        val result = CDDL.undefined.newCborItem(Unit)
        assertIs<CborSimple<*>>(result)
    }

    // ========== CDDL newCborItem with JsonElement ==========

    @Test
    fun testCDDLTstrNewCborItemWithJsonPrimitive() {
        val json = JsonPrimitive("hello")
        val result = CDDL.tstr.newCborItem(json)
        assertIs<CborString>(result)
    }

    @Test
    fun testCDDLBoolNewCborItemWithJsonPrimitive() {
        val json = JsonPrimitive(true)
        val result = CDDL.bool.newCborItem(json)
        assertIs<CborSimple<*>>(result)
    }

    @Test
    fun testCDDLBstrNewCborItemWithJsonPrimitive() {
        val json = JsonPrimitive("dGVzdA") // Base64URL encoded "test"
        val result = CDDL.bstr.newCborItem(json)
        assertIs<CborByteString>(result)
    }

    @Test
    fun testCDDLFloatNewCborItemWithJsonPrimitive() {
        val json = JsonPrimitive(3.14f)
        val result = CDDL.float.newCborItem(json)
        assertIs<CborFloat32>(result)
    }

    @Test
    fun testCDDLFloat64NewCborItemWithJsonPrimitive() {
        val json = JsonPrimitive(3.14)
        val result = CDDL.float64.newCborItem(json)
        assertIs<CborDouble>(result)
    }

    @Test
    fun testCDDLIntNewCborItemWithJsonPrimitive() {
        val json = JsonPrimitive(42)
        val result = CDDL.int.newCborItem(json)
        assertIs<CborItem<*>>(result)
    }

    @Test
    fun testCDDLUintNewCborItemWithJsonPrimitive() {
        val json = JsonPrimitive(42)
        val result = CDDL.uint.newCborItem(json)
        assertIs<CborUInt>(result)
    }

    @Test
    fun testCDDLNintNewCborItemWithJsonPrimitive() {
        val json = JsonPrimitive(-42)
        val result = CDDL.nint.newCborItem(json)
        assertIs<CborNInt>(result)
    }

    @Test
    fun testCDDLListNewCborItemWithJsonArray() {
        val json = buildJsonArray {
            add(JsonPrimitive(1))
            add(JsonPrimitive(2))
        }
        val result = CDDL.list.newCborItem(json)
        assertIs<CborArray<*>>(result)
    }

    @Test
    fun testCDDLMapNewCborItemWithJsonObject() {
        val json = buildJsonObject {
            put("key", JsonPrimitive("value"))
        }
        val result = CDDL.map.newCborItem(json)
        assertIs<CborMap<*, *>>(result)
    }

    // ========== CDDL any newCborItem branches ==========

    @Test
    fun testCDDLAnyNewCborItemWithString() {
        val result = CDDL.any.newCborItem("hello")
        assertIs<CborString>(result)
    }

    @Test
    fun testCDDLAnyNewCborItemWithLong() {
        val result = CDDL.any.newCborItem(42L)
        assertIs<CborUInt>(result)
    }

    @Test
    fun testCDDLAnyNewCborItemWithByteArray() {
        val result = CDDL.any.newCborItem(byteArrayOf(1, 2, 3))
        assertIs<CborByteString>(result)
    }

    @Test
    fun testCDDLAnyNewCborItemWithDouble() {
        val result = CDDL.any.newCborItem(3.14)
        assertIs<CborDouble>(result)
    }

    @Test
    fun testCDDLAnyNewCborItemWithFloat() {
        val result = CDDL.any.newCborItem(3.14f)
        assertIsCborFloat(result)
    }

    @Test
    fun testCDDLAnyNewCborItemWithBoolean() {
        val result = CDDL.any.newCborItem(true)
        assertIs<CborSimple<*>>(result)
    }

    @Test
    fun testCDDLAnyNewCborItemWithMutableList() {
        val result = CDDL.any.newCborItem(mutableListOf(CborUInt(1)))
        assertIs<CborArray<*>>(result)
    }

    @Test
    fun testCDDLAnyNewCborItemWithMutableMap() {
        val result = CDDL.any.newCborItem(mutableMapOf(CborString("key") to CborUInt(1) as CborItem<*>))
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testCDDLAnyNewCborItemWithJsonPrimitiveString() {
        val json = JsonPrimitive("hello")
        val result = CDDL.any.newCborItem(json)
        assertIs<CborString>(result)
    }

    @Test
    fun testCDDLAnyNewCborItemWithJsonPrimitiveBool() {
        val json = JsonPrimitive(true)
        val result = CDDL.any.newCborItem(json)
        assertIs<CborSimple<*>>(result)
    }

    @Test
    fun testCDDLAnyNewCborItemWithJsonPrimitiveFloat() {
        val json = JsonPrimitive(3.14)
        val result = CDDL.any.newCborItem(json)
        assertIs<CborDouble>(result)
    }

    @Test
    fun testCDDLAnyNewCborItemWithJsonPrimitiveInt() {
        val json = JsonPrimitive(42)
        val result = CDDL.any.newCborItem(json)
        assertIs<CborItem<*>>(result)
    }

    @Test
    fun testCDDLAnyFromJsonWithJsonArray() {
        val json = buildJsonArray {
            add(JsonPrimitive(1))
        }
        val result = CDDL.any.fromJson(json)
        assertIs<CborArray<*>>(result)
    }

    @Test
    fun testCDDLAnyFromJsonWithJsonObject() {
        val json = buildJsonObject {
            put("key", JsonPrimitive("value"))
        }
        val result = CDDL.any.fromJson(json)
        assertIs<CborMap<*, *>>(result)
    }

    // ========== CDDL newCborItemFromJson branches ==========

    @Test
    fun testCDDLNewCborItemFromJsonWithTstr() {
        val result = CDDL.tstr.newCborItemFromJson(JsonPrimitive("hello"), CDDL.tstr)
        assertIs<CborString>(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithNullReturnsNull() {
        val result = CDDL.any.newCborItemFromJson(JsonNull, CDDL.Null)
        assertIs<CborNull>(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithFalse() {
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive(false), CDDL.False)
        assertIs<CborSimple<*>>(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithTrue() {
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive(true), CDDL.True)
        assertIs<CborSimple<*>>(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithBool() {
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive(true), CDDL.bool)
        assertIs<CborSimple<*>>(result)
    }

    @Test
    fun testCDDLBstrFromJson() {
        val result = CDDL.bstr.fromJson(JsonPrimitive("dGVzdA"))
        assertIs<CborByteString>(result)
    }

    @Test
    fun testCDDLBytesFromJson() {
        val result = CDDL.bytes.fromJson(JsonPrimitive("dGVzdA"))
        assertIs<CborByteString>(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithFloat() {
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive(3.14f), CDDL.float)
        assertIs<CborFloat32>(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithFloat16() {
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive(1.5f), CDDL.float16)
        assertIs<CborFloat16>(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithFloat32() {
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive(1.5f), CDDL.float32)
        assertIs<CborFloat32>(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithFloat64() {
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive(3.14), CDDL.float64)
        assertIs<CborDouble>(result)
    }

    @Test
    fun testCDDLFullDateFromJson() {
        val result = CDDL.full_date.fromJson(JsonPrimitive("2024-06-15T00:00:00Z"))
        assertIs<CborFullDate>(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithInt() {
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive(42), CDDL.int)
        assertIs<CborItem<*>>(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithNil() {
        val result = CDDL.any.newCborItemFromJson(JsonNull, CDDL.nil)
        assertIs<CborSimple<*>>(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithNint() {
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive(-42), CDDL.nint)
        assertIs<CborNInt>(result)
    }

    @Test
    fun testCDDLTdateFromJson() {
        val result = CDDL.tdate.fromJson(JsonPrimitive("2024-06-15T10:30:00Z"))
        assertIs<CborTDate>(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithText() {
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive("hello"), CDDL.text)
        assertIs<CborString>(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithTime() {
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive(1705363200), CDDL.time)
        assertIs<CborTime>(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithUint() {
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive(42), CDDL.uint)
        assertIs<CborUInt>(result)
    }

    @Test
    fun testCDDLNewCborItemFromJsonWithUndefined() {
        val result = CDDL.any.newCborItemFromJson(JsonNull, CDDL.undefined)
        assertIs<CborSimple<*>>(result)
    }

    // ========== CDDL toTag branches ==========

    @Test
    fun testCDDLToTagWithNoAlias() {
        val tag = CDDL.tstr.toTag(null)
        assertNotNull(tag)
        assertTrue(tag.contains("#"))
    }

    @Test
    fun testCDDLToTagWithAliasAndNullAdditionalInfo() {
        // bool has aliases (False, True)
        val tag = CDDL.bool.toTag(null)
        assertNotNull(tag)
    }

    @Test
    fun testCDDLToTagWithTstrAndAdditionalInfo() {
        val tag = CDDL.tstr.toTag(25)
        assertNotNull(tag)
        assertTrue(tag.contains("."))
    }

    // ========== CDDL map.fromJson branches ==========

    @Test
    fun testCDDLMapFromJsonWithPrimitiveValue() {
        val json = buildJsonObject {
            put("key", JsonPrimitive(42))
        }
        val result = CDDL.map.fromJson(json)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testCDDLMapFromJsonWithArrayValue() {
        val json = buildJsonObject {
            put("key", buildJsonArray {
                add(JsonPrimitive(1))
            })
        }
        val result = CDDL.map.fromJson(json)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testCDDLMapFromJsonWithObjectValue() {
        val json = buildJsonObject {
            put("outer", buildJsonObject {
                put("inner", JsonPrimitive("value"))
            })
        }
        val result = CDDL.map.fromJson(json)
        assertIs<CborMap<*, *>>(result)
    }

    // ========== CDDL newCborItem branches for specific types ==========

    @Test
    fun testCDDLUndefinedNewUndefined() {
        val result = CDDL.undefined.newUndefined()
        assertIs<CborUndefined>(result)
    }

    @Test
    fun testCDDLNilNewNil() {
        // CDDL.nil.newNil() returns CborSimple.NULL which is CborNull
        val result = CDDL.nil.newNil()
        assertIs<CborNull>(result)
    }

    @Test
    fun testCDDLNullNewNull() {
        val result = CDDL.Null.newNull()
        assertIs<CborNull>(result)
    }

    @Test
    fun testCDDLTrueNewTrue() {
        val result = CDDL.True.newTrue()
        assertIs<CborTrue>(result)
    }

    @Test
    fun testCDDLFalseNewFalse() {
        val result = CDDL.False.newFalse()
        assertIs<CborFalse>(result)
    }

    // ========== CDDL newCborItem with different value types ==========

    @Test
    fun testCDDLNewCborItemWithTstrValue() {
        val result = CDDL.tstr.newCborItem("hello")
        assertIs<CborString>(result)
        assertEquals("hello", result.value)
    }

    @Test
    fun testCDDLNewCborItemWithUintValue() {
        val result = CDDL.uint.newCborItem(42L)
        assertIs<CborUInt>(result)
    }

    @Test
    fun testCDDLNewCborItemWithBstrValue() {
        val result = CDDL.bstr.newCborItem(byteArrayOf(1, 2, 3))
        assertIs<CborByteString>(result)
    }

    @Test
    fun testCDDLNewCborItemWithBoolTrueValue() {
        val result = CDDL.bool.newCborItem(true)
        assertIs<CborTrue>(result)
    }

    @Test
    fun testCDDLNewCborItemWithBoolFalseValue() {
        val result = CDDL.bool.newCborItem(false)
        assertIs<CborFalse>(result)
    }

    @Test
    fun testCDDLNewCborItemWithFloat64Value() {
        val result = CDDL.float64.newCborItem(3.14159)
        assertIs<CborDouble>(result)
    }

    @Test
    fun testCDDLNewCborItemWithFloatValue() {
        val result = CDDL.float.newCborItem(3.14f)
        assertIs<CborFloat>(result)
    }

    @Test
    fun testCDDLNewCborItemWithListValue() {
        val list = listOf("a", "b", "c")
        val result = CDDL.list.newCborItem(list)
        assertIs<CborArray<*>>(result)
    }

    @Test
    fun testCDDLNewCborItemWithMapValue() {
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val result = CDDL.map.newCborItem(map)
        assertIs<CborMap<*, *>>(result)
    }

    // ========== CDDL fromJson for different types ==========

    @Test
    fun testCDDLTstrFromJson() {
        val result = CDDL.tstr.fromJson(JsonPrimitive("test"))
        assertIs<CborString>(result)
    }

    @Test
    fun testCDDLUintFromJson() {
        val result = CDDL.uint.fromJson(JsonPrimitive(42))
        assertIs<CborUInt>(result)
    }

    @Test
    fun testCDDLIntFromJsonNegative() {
        val result = CDDL.int.fromJson(JsonPrimitive(-42))
        assertIs<CborNInt>(result)
    }

    @Test
    fun testCDDLBstrFromJsonBase64() {
        val result = CDDL.bstr.fromJson(JsonPrimitive("AQID")) // base64 encoded
        assertIs<CborByteString>(result)
    }

    @Test
    fun testCDDLBoolFromJsonTrue() {
        val result = CDDL.bool.fromJson(JsonPrimitive(true))
        assertIs<CborTrue>(result)
    }

    @Test
    fun testCDDLFloat64FromJson() {
        val result = CDDL.float64.fromJson(JsonPrimitive(3.14))
        assertIs<CborDouble>(result)
    }

    // ========== CDDL newCborItem nint branch ==========

    @Test
    fun testCDDLNewCborItemNintWithNumberValue() {
        val result = CDDL.nint.newCborItem(42)
        assertIs<CborNInt>(result)
        assertEquals(42L, result.value)
    }

    @Test
    fun testCDDLNewCborItemNintWithLongValue() {
        val result = CDDL.nint.newCborItem(100L)
        assertIs<CborNInt>(result)
    }

    @Test
    fun testCDDLNintFromJson() {
        val result = CDDL.nint.fromJson(JsonPrimitive(42))
        assertIs<CborNInt>(result)
    }

    // ========== CDDL newCborItem uint branch ==========

    @Test
    fun testCDDLNewCborItemUintWithLongValue() {
        val result = CDDL.uint.newCborItem(100L)
        assertIs<CborUInt>(result)
    }

    @Test
    fun testCDDLNewCborItemUintWithNumberValue() {
        val result = CDDL.uint.newCborItem(42)
        assertIs<CborUInt>(result)
    }

    // ========== CDDL newCborItem nil branch ==========

    @Test
    fun testCDDLNewCborItemNilBranch() {
        // Test the nil branch in the when(this) statement at line 266
        // Need to pass non-null, non-Unit value to avoid early returns at lines 196-199
        val result = CDDL.nil.newCborItem("")
        assertIs<CborNull>(result)
    }

    // ========== CDDL newCborItem undefined branch ==========

    @Test
    fun testCDDLNewCborItemUndefinedBranch() {
        val result = CDDL.undefined.newCborItem(Unit)
        assertIs<CborUndefined>(result)
    }
}
