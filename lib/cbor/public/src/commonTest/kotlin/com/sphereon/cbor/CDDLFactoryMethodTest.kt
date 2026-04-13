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

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for CDDL factory methods including newCborItem for all CDDL types
 */
class CDDLFactoryMethodTest {

    // ========== CDDL.tstr newCborItem branches ==========

    @Test
    fun testTstrNewCborItemWithString() {
        val result = CDDL.tstr.newCborItem("hello")
        assertIs<CborString>(result)
        assertEquals("hello", result.value)
    }

    @Test
    fun testTstrNewCborItemWithJsonPrimitive() {
        val result = CDDL.tstr.newCborItem(JsonPrimitive("world"))
        assertIs<CborString>(result)
        assertEquals("world", result.value)
    }

    @Test
    fun testTstrNewCborItemWithNull() {
        val result = CDDL.tstr.newCborItem(null)
        assertIs<CborNull>(result)
    }

    // ========== CDDL.uint newCborItem branches ==========

    @Test
    fun testUintNewCborItemWithLong() {
        val result = CDDL.uint.newCborItem(42L)
        assertIs<CborUInt>(result)
        assertEquals(42L, result.value)
    }

    @Test
    fun testUintNewCborItemWithInt() {
        val result = CDDL.uint.newCborItem(42)
        assertIs<CborUInt>(result)
    }

    @Test
    fun testUintNewCborItemWithJsonPrimitive() {
        val result = CDDL.uint.newCborItem(JsonPrimitive(123))
        assertIs<CborUInt>(result)
        assertEquals(123L, result.value)
    }

    // ========== CDDL.nint newCborItem branches ==========

    @Test
    fun testNintNewCborItemWithLong() {
        val result = CDDL.nint.newCborItem(-42L)
        assertIs<CborNInt>(result)
        assertEquals(-42L, result.value)
    }

    @Test
    fun testNintNewCborItemWithInt() {
        val result = CDDL.nint.newCborItem(-42)
        assertIs<CborNInt>(result)
    }

    @Test
    fun testNintNewCborItemWithJsonPrimitive() {
        val result = CDDL.nint.newCborItem(JsonPrimitive(-123))
        assertIs<CborNInt>(result)
    }

    // ========== CDDL.int newCborItem branches ==========

    @Test
    fun testIntNewCborItemWithPositiveInt() {
        val result = CDDL.int.newCborItem(42)
        // CDDL.int returns CborUInt for positive values
        assertIs<CborUInt>(result)
    }

    @Test
    fun testIntNewCborItemWithNegativeInt() {
        val result = CDDL.int.newCborItem(-42)
        // CDDL.int returns CborNInt for negative values
        assertIs<CborNInt>(result)
    }

    @Test
    fun testIntNewCborItemWithJsonPrimitive() {
        val result = CDDL.int.newCborItem(JsonPrimitive(123))
        assertIs<CborUInt>(result)
    }

    // ========== CDDL.bstr newCborItem branches ==========

    @Test
    fun testBstrNewCborItemWithByteArray() {
        val result = CDDL.bstr.newCborItem(byteArrayOf(1, 2, 3))
        assertIs<CborByteString>(result)
    }

    @Test
    fun testBstrNewCborItemWithJsonPrimitive() {
        // Base64 encoded "test"
        val result = CDDL.bstr.newCborItem(JsonPrimitive("dGVzdA"))
        assertIs<CborByteString>(result)
    }

    // ========== CDDL.bytes newCborItem branches ==========

    @Test
    fun testBytesNewCborItemWithByteArray() {
        val result = CDDL.bytes.newCborItem(byteArrayOf(4, 5, 6))
        assertIs<CborByteString>(result)
    }

    @Test
    fun testBytesNewCborItemWithJsonPrimitive() {
        val result = CDDL.bytes.newCborItem(JsonPrimitive("AQID"))
        assertIs<CborByteString>(result)
    }

    // ========== CDDL.bstr_indef_length newCborItem branches ==========

    @Test
    fun testBstrIndefLengthNewByteString() {
        val chunks = listOf(byteArrayOf(1, 2), byteArrayOf(3, 4))
        val result = CDDL.bstr_indef_length.newByteString(chunks)
        assertIs<CborByteStringIndefLength>(result)
        assertEquals(2, result.value.size)
    }

    // ========== CDDL.tstr_indef_length newCborItem branches ==========

    @Test
    fun testTstrIndefLengthNewStringIndefLength() {
        val chunks = listOf("hello", "world")
        val result = CDDL.tstr_indef_length.newStringIndefLength(chunks)
        assertIs<CborStringIndefLength>(result)
        assertEquals(2, result.value.size)
    }

    // ========== CDDL.text newCborItem branches ==========

    @Test
    fun testTextNewCborItemWithString() {
        val result = CDDL.text.newCborItem("text value")
        assertIs<CborString>(result)
    }

    @Test
    fun testTextNewCborItemWithJsonPrimitive() {
        val result = CDDL.text.newCborItem(JsonPrimitive("json text"))
        assertIs<CborString>(result)
    }

    // ========== CDDL.bool newCborItem branches ==========

    @Test
    fun testBoolNewCborItemTrue() {
        val result = CDDL.bool.newCborItem(true)
        assertEquals(CborSimple.TRUE, result)
    }

    @Test
    fun testBoolNewCborItemFalse() {
        val result = CDDL.bool.newCborItem(false)
        assertEquals(CborSimple.FALSE, result)
    }

    @Test
    fun testBoolNewCborItemWithJsonPrimitive() {
        val result = CDDL.bool.newCborItem(JsonPrimitive(true))
        assertEquals(CborSimple.TRUE, result)
    }

    // ========== CDDL.True/False newCborItem branches ==========

    @Test
    fun testTrueNewCborItem() {
        val result = CDDL.True.newCborItem(true)
        assertEquals(CborSimple.TRUE, result)
    }

    @Test
    fun testFalseNewCborItem() {
        val result = CDDL.False.newCborItem(false)
        assertEquals(CborSimple.FALSE, result)
    }

    // ========== CDDL.nil/Null newCborItem branches ==========

    @Test
    fun testNilNewCborItem() {
        val result = CDDL.nil.newCborItem(null)
        assertEquals(CborSimple.NULL, result)
    }

    @Test
    fun testNullNewCborItem() {
        val result = CDDL.Null.newCborItem(null)
        assertEquals(CborSimple.NULL, result)
    }

    // ========== CDDL.undefined newCborItem branches ==========

    @Test
    fun testUndefinedNewCborItem() {
        val result = CDDL.undefined.newCborItem(Unit)
        assertEquals(CborSimple.UNDEFINED, result)
    }

    @Test
    fun testUndefinedNewCborItemAnyUnit() {
        // When value is Unit, should return undefined
        val result = CDDL.any.newCborItem(Unit)
        assertEquals(CborSimple.UNDEFINED, result)
    }

    // ========== CDDL.float newCborItem branches ==========

    @Test
    fun testFloatNewCborItemWithFloat() {
        val result = CDDL.float.newCborItem(3.14f)
        assertIs<CborFloat32>(result)
    }

    @Test
    fun testFloatNewCborItemWithJsonPrimitive() {
        val result = CDDL.float.newCborItem(JsonPrimitive(2.71f))
        assertIs<CborFloat32>(result)
    }

    // ========== CDDL.float16 newCborItem branches ==========

    @Test
    fun testFloat16NewCborItemWithFloat() {
        val result = CDDL.float16.newCborItem(1.5f)
        assertIs<CborFloat16>(result)
    }

    @Test
    fun testFloat16NewCborItemWithJsonPrimitive() {
        val result = CDDL.float16.newCborItem(JsonPrimitive(1.5f))
        assertIs<CborFloat16>(result)
    }

    // ========== CDDL.float32 newCborItem branches ==========

    @Test
    fun testFloat32NewCborItemWithFloat() {
        val result = CDDL.float32.newCborItem(3.14f)
        assertIs<CborFloat32>(result)
    }

    @Test
    fun testFloat32NewCborItemWithJsonPrimitive() {
        val result = CDDL.float32.newCborItem(JsonPrimitive(3.14f))
        assertIs<CborFloat32>(result)
    }

    // ========== CDDL.float64 newCborItem branches ==========

    @Test
    fun testFloat64NewCborItemWithDouble() {
        val result = CDDL.float64.newCborItem(3.14159)
        assertIs<CborDouble>(result)
    }

    @Test
    fun testFloat64NewCborItemWithJsonPrimitive() {
        val result = CDDL.float64.newCborItem(JsonPrimitive(2.71828))
        assertIs<CborDouble>(result)
    }

    // ========== CDDL.tdate newCborItem branches ==========

    @Test
    fun testTdateNewCborItemWithString() {
        val result = CDDL.tdate.newCborItem("2023-01-15T12:00:00Z")
        assertIs<CborTDate>(result)
    }

    @Test
    fun testTdateNewCborItemWithJsonPrimitive() {
        val result = CDDL.tdate.newCborItem(JsonPrimitive("2023-06-20T10:30:00Z"))
        assertIs<CborTDate>(result)
    }

    // ========== CDDL.full_date newCborItem branches ==========

    @Test
    fun testFullDateNewCborItemWithString() {
        val result = CDDL.full_date.newCborItem("2023-01-15")
        assertIs<CborFullDate>(result)
    }

    @Test
    fun testFullDateNewCborItemWithJsonPrimitive() {
        val result = CDDL.full_date.newCborItem(JsonPrimitive("2023-06-20"))
        assertIs<CborFullDate>(result)
    }

    // ========== CDDL.time newCborItem branches ==========

    @Test
    fun testTimeNewCborItemWithLong() {
        val result = CDDL.time.newCborItem(1673784000L)
        assertIs<CborTime>(result)
    }

    @Test
    fun testTimeNewCborItemWithJsonPrimitive() {
        val result = CDDL.time.newCborItem(JsonPrimitive(1673784000L))
        assertIs<CborTime>(result)
    }

    // ========== CDDL.list newCborItem branches ==========

    @Test
    fun testListNewCborItemWithMutableList() {
        // Use Long values which are supported cddl types
        val list = mutableListOf<Any>(1L, 2L, 3L)
        val result = CDDL.list.newCborItem(list)
        assertIs<CborArray<*>>(result)
        assertEquals(3, result.value.size)
    }

    @Test
    fun testListNewCborItemWithCborArray() {
        val array = CborArray(mutableListOf(CborUInt(1), CborUInt(2)))
        val result = CDDL.list.newCborItem(array)
        assertIs<CborArray<*>>(result)
        assertEquals(array, result)
    }

    @Test
    fun testListNewCborItemWithJsonArray() {
        val jsonArray = JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2)))
        val result = CDDL.list.newCborItem(jsonArray)
        assertIs<CborArray<*>>(result)
    }

    @Test
    fun testListNewCborItemWithCborItems() {
        val list = mutableListOf<CborItem<*>>(CborUInt(1), CborString("test"))
        val result = CDDL.list.newCborItem(list)
        assertIs<CborArray<*>>(result)
    }

    // ========== CDDL.map newCborItem branches ==========

    @Test
    fun testMapNewCborItemWithMutableMap() {
        val map = mutableMapOf<Any, Any>("key" to "value")
        val result = CDDL.map.newCborItem(map)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testMapNewCborItemWithCborMap() {
        val cborMap = CborMap(mutableMapOf(CborString("key") to CborUInt(42)))
        val result = CDDL.map.newCborItem(cborMap)
        assertIs<CborMap<*, *>>(result)
        assertEquals(cborMap, result)
    }

    @Test
    fun testMapNewCborItemWithJsonObject() {
        val jsonObject = JsonObject(mapOf("key" to JsonPrimitive("value")))
        val result = CDDL.map.newCborItem(jsonObject)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testMapNewCborItemWithCborItemKeys() {
        val map = mutableMapOf<CborItem<*>, CborItem<*>>(
            CborString("key") to CborUInt(42)
        )
        val result = CDDL.map.newCborItem(map)
        assertIs<CborMap<*, *>>(result)
    }

    // ========== CDDL.any newCborItem branches ==========

    @Test
    fun testAnyNewCborItemWithString() {
        val result = CDDL.any.newCborItem("test string")
        assertIs<CborString>(result)
    }

    @Test
    fun testAnyNewCborItemWithLong() {
        val result = CDDL.any.newCborItem(42L)
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
        val result = CDDL.any.newCborItem(true)
        assertEquals(CborSimple.TRUE, result)
    }

    @Test
    fun testAnyNewCborItemWithNull() {
        val result = CDDL.any.newCborItem(null)
        assertEquals(CborSimple.NULL, result)
    }

    @Test
    fun testAnyNewCborItemWithList() {
        // Use Long (cddl_int) and String (cddl_tstr) which are supported types
        val list = mutableListOf<Any>(1L, "test")
        val result = CDDL.any.newCborItem(list)
        assertIs<CborArray<*>>(result)
    }

    @Test
    fun testAnyNewCborItemWithMap() {
        // Use Long for values which is a supported type
        val map = mutableMapOf<Any, Any>("key" to 42L)
        val result = CDDL.any.newCborItem(map)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testAnyNewCborItemWithJsonPrimitiveString() {
        val result = CDDL.any.newCborItem(JsonPrimitive("hello"))
        assertIs<CborString>(result)
    }

    @Test
    fun testAnyNewCborItemWithJsonPrimitiveTrue() {
        val result = CDDL.any.newCborItem(JsonPrimitive(true))
        assertEquals(CborSimple.TRUE, result)
    }

    @Test
    fun testAnyNewCborItemWithJsonPrimitiveFalse() {
        val result = CDDL.any.newCborItem(JsonPrimitive(false))
        assertEquals(CborSimple.FALSE, result)
    }

    @Test
    fun testAnyNewCborItemWithJsonPrimitiveFloat() {
        val result = CDDL.any.newCborItem(JsonPrimitive(3.14))
        assertIs<CborDouble>(result)
    }

    @Test
    fun testAnyNewCborItemWithJsonPrimitiveExponent() {
        // JsonPrimitive with a numeric exponent value (not string)
        // On JVM: JsonPrimitive(1e10).content = "1.0E10" → float detection → CborDouble
        // On JS/WasmJs: JsonPrimitive(1e10).content = "10000000000" → integer → CborUInt
        val result = CDDL.any.newCborItem(JsonPrimitive(1e10))
        if (hasDistinctFloatType) {
            assertIsCborDouble(result)
        } else {
            assertTrue(result is CborDouble || result is CborUInt,
                "Expected CborDouble or CborUInt but got ${result::class.simpleName}")
        }
    }

    @Test
    fun testAnyNewCborItemWithJsonPrimitiveInt() {
        // JsonPrimitive(42) creates a numeric primitive that gets parsed as Long
        val result = CDDL.any.newCborItem(JsonPrimitive(42))
        assertIs<CborUInt>(result)
    }

    @Test
    fun testAnyNewCborItemWithTdate() {
        val result = CDDL.any.newCborItem("2023-01-15T12:00:00Z" as cddl_tdate)
        assertIs<CborItem<*>>(result)
    }

    @Test
    fun testAnyNewCborItemWithFullDate() {
        val result = CDDL.any.newCborItem("2023-01-15" as cddl_full_date)
        assertIs<CborItem<*>>(result)
    }

    // ========== newCborItemFromJson branches ==========

    @Test
    fun testNewCborItemFromJsonWithNull() {
        val result = CDDL.any.newCborItemFromJson(null)
        assertIs<CborNull>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithJsonNull() {
        val result = CDDL.any.newCborItemFromJson(JsonNull)
        assertIs<CborNull>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithString() {
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive("test"))
        assertIs<CborString>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithArray() {
        val array = JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2)))
        val result = CDDL.any.newCborItemFromJson(array)
        assertIs<CborArray<*>>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithObject() {
        val obj = JsonObject(mapOf("key" to JsonPrimitive("value")))
        val result = CDDL.any.newCborItemFromJson(obj)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithCddlType() {
        val result = CDDL.uint.newCborItemFromJson(JsonPrimitive(42), CDDL.uint)
        assertIs<CborUInt>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithCddlTypes() {
        // Test with JsonNull for null types
        assertIs<CborNull>(CDDL.Null.newCborItemFromJson(JsonNull, CDDL.Null))
        assertEquals(CborSimple.FALSE, CDDL.False.newCborItemFromJson(JsonPrimitive(false), CDDL.False))
        assertEquals(CborSimple.TRUE, CDDL.True.newCborItemFromJson(JsonPrimitive(true), CDDL.True))

        // String JsonPrimitives are converted to CborString before CDDL type check
        assertIs<CborString>(CDDL.tstr.newCborItemFromJson(JsonPrimitive("test"), CDDL.tstr))
        assertIs<CborString>(CDDL.text.newCborItemFromJson(JsonPrimitive("text"), CDDL.text))

        // Numeric JsonPrimitives go through CDDL type handling
        assertIs<CborFloat32>(CDDL.float.newCborItemFromJson(JsonPrimitive(3.14), CDDL.float))
        assertIs<CborFloat16>(CDDL.float16.newCborItemFromJson(JsonPrimitive(1.5), CDDL.float16))
        assertIs<CborFloat32>(CDDL.float32.newCborItemFromJson(JsonPrimitive(3.14), CDDL.float32))
        assertIs<CborDouble>(CDDL.float64.newCborItemFromJson(JsonPrimitive(3.14159), CDDL.float64))

        assertIs<CborUInt>(CDDL.int.newCborItemFromJson(JsonPrimitive(42), CDDL.int))
        assertIs<CborNull>(CDDL.nil.newCborItemFromJson(JsonNull, CDDL.nil))
        assertIs<CborNInt>(CDDL.nint.newCborItemFromJson(JsonPrimitive(-42), CDDL.nint))
        assertIs<CborTime>(CDDL.time.newCborItemFromJson(JsonPrimitive(1673784000), CDDL.time))
        assertIs<CborUInt>(CDDL.uint.newCborItemFromJson(JsonPrimitive(42), CDDL.uint))

        // Boolean JsonPrimitives
        assertIs<CborBool>(CDDL.bool.newCborItemFromJson(JsonPrimitive(true), CDDL.bool))
    }

    @Test
    fun testBstrFromJsonDirectly() {
        // Test bstr.fromJson directly (the newCborItemFromJson converts strings first)
        val result = CDDL.bstr.fromJson(JsonPrimitive("dGVzdA"))
        assertIs<CborByteString>(result)
    }

    @Test
    fun testBytesFromJsonDirectly() {
        val result = CDDL.bytes.fromJson(JsonPrimitive("dGVzdA"))
        assertIs<CborByteString>(result)
    }

    @Test
    fun testNewCborItemFromJsonWithBool() {
        val result = CDDL.bool.newCborItemFromJson(JsonPrimitive(true), CDDL.bool)
        assertEquals(CborSimple.TRUE, result)
    }

    // ========== CborItemJson tests ==========

    @Test
    fun testCborItemJsonFormat() {
        val jsonObject = JsonObject(mapOf(
            "cddl" to JsonPrimitive("uint"),
            "value" to JsonPrimitive(42)
        ))
        val result = CDDL.any.newCborItemFromJson(jsonObject)
        assertIs<CborUInt>(result)
    }

    @Test
    fun testCborItemJsonFormatWithKey() {
        val jsonObject = JsonObject(mapOf(
            "cddl" to JsonPrimitive("tstr"),
            "key" to JsonPrimitive("myKey"),
            "value" to JsonPrimitive("myValue")
        ))
        val result = CDDL.any.newCborItemFromJson(jsonObject)
        assertIs<CborMap<*, *>>(result)
    }

    // ========== CDDL.util tests ==========

    @Test
    fun testCddlUtilFromFormat() {
        assertEquals(CDDL.uint, CDDL.util.fromFormat("uint"))
        assertEquals(CDDL.tstr, CDDL.util.fromFormat("tstr"))
        assertEquals(CDDL.bstr, CDDL.util.fromFormat("bstr"))
        assertEquals(CDDL.bool, CDDL.util.fromFormat("bool"))
        assertEquals(CDDL.nil, CDDL.util.fromFormat("nil"))
        assertEquals(CDDL.Null, CDDL.util.fromFormat("null"))
        assertEquals(CDDL.float, CDDL.util.fromFormat("float"))
        assertEquals(CDDL.float16, CDDL.util.fromFormat("float16"))
        assertEquals(CDDL.float32, CDDL.util.fromFormat("float32"))
        assertEquals(CDDL.float64, CDDL.util.fromFormat("float64"))
        assertEquals(CDDL.tdate, CDDL.util.fromFormat("tdate"))
        assertEquals(CDDL.full_date, CDDL.util.fromFormat("full-date"))
        assertEquals(CDDL.time, CDDL.util.fromFormat("time"))
        assertEquals(CDDL.list, CDDL.util.fromFormat("list"))
        assertEquals(CDDL.map, CDDL.util.fromFormat("map"))
        assertEquals(CDDL.any, CDDL.util.fromFormat("any"))
    }

    @Test
    fun testCddlUtilEntries() {
        val entries = CDDL.util.entries
        assertNotNull(entries)
        assertTrue(entries.contains(CDDL.uint))
        assertTrue(entries.contains(CDDL.tstr))
        assertTrue(entries.contains(CDDL.list))
        assertTrue(entries.contains(CDDL.map))
    }

    // ========== CDDL equality and hashCode ==========

    @Test
    fun testCddlEquality() {
        assertEquals(CDDL.uint, CDDL.uint)
        assertEquals(CDDL.tstr, CDDL.tstr)
        assertFalse(CDDL.uint.equals(CDDL.tstr))
    }

    @Test
    fun testCddlHashCode() {
        assertEquals(CDDL.uint.hashCode(), CDDL.uint.hashCode())
    }

    @Test
    fun testCddlFormat() {
        assertEquals("uint", CDDL.uint.format)
        assertEquals("tstr", CDDL.tstr.format)
        assertEquals("bstr", CDDL.bstr.format)
    }

    @Test
    fun testCddlMajorType() {
        assertEquals(MajorType.UNSIGNED_INTEGER, CDDL.uint.majorType)
        assertEquals(MajorType.UNICODE_STRING, CDDL.tstr.majorType)
        assertEquals(MajorType.BYTE_STRING, CDDL.bstr.majorType)
        assertEquals(MajorType.ARRAY, CDDL.list.majorType)
        assertEquals(MajorType.MAP, CDDL.map.majorType)
    }

    @Test
    fun testCddlToTag() {
        assertNotNull(CDDL.uint.toTag())
        assertNotNull(CDDL.tstr.toTag())
        assertNotNull(CDDL.uint.toTag(5))
    }

    // ========== list.fromJson with nested structures ==========

    @Test
    fun testListFromJsonWithNestedArray() {
        val nested = JsonArray(listOf(
            JsonPrimitive(1),
            JsonArray(listOf(JsonPrimitive(2), JsonPrimitive(3)))
        ))
        val result = CDDL.list.fromJson(nested)
        assertIs<CborArray<*>>(result)
        assertEquals(2, result.value.size)
    }

    @Test
    fun testListFromJsonWithNestedObject() {
        val nested = JsonArray(listOf(
            JsonPrimitive(1),
            JsonObject(mapOf("key" to JsonPrimitive("value")))
        ))
        val result = CDDL.list.fromJson(nested)
        assertIs<CborArray<*>>(result)
    }

    // ========== map.fromJson with nested structures ==========

    @Test
    fun testMapFromJsonWithNestedArray() {
        val nested = JsonObject(mapOf(
            "key" to JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2)))
        ))
        val result = CDDL.map.fromJson(nested)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testMapFromJsonWithNestedObject() {
        val nested = JsonObject(mapOf(
            "outer" to JsonObject(mapOf("inner" to JsonPrimitive("value")))
        ))
        val result = CDDL.map.fromJson(nested)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testMapFromJsonWithPrimitive() {
        val obj = JsonObject(mapOf(
            "string" to JsonPrimitive("test"),
            "number" to JsonPrimitive(42)
        ))
        val result = CDDL.map.fromJson(obj)
        assertIs<CborMap<*, *>>(result)
        assertEquals(2, result.value.size)
    }
}
