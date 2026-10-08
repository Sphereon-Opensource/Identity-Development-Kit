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

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CDDLTypeTest {
    // CDDL format strings
    @Test
    fun testCDDLFormats() {
        assertEquals("tstr", CDDL.tstr.format)
        assertEquals("uint", CDDL.uint.format)
        assertEquals("nint", CDDL.nint.format)
        assertEquals("int", CDDL.int.format)
        assertEquals("bstr", CDDL.bstr.format)
        assertEquals("bytes", CDDL.bytes.format)
        assertEquals("text", CDDL.text.format)
        assertEquals("bool", CDDL.bool.format)
        assertEquals("true", CDDL.True.format)
        assertEquals("false", CDDL.False.format)
        assertEquals("nil", CDDL.nil.format)
        assertEquals("null", CDDL.Null.format)
        assertEquals("undefined", CDDL.undefined.format)
        assertEquals("float", CDDL.float.format)
        assertEquals("float16", CDDL.float16.format)
        assertEquals("float32", CDDL.float32.format)
        assertEquals("float64", CDDL.float64.format)
        assertEquals("map", CDDL.map.format)
        assertEquals("list", CDDL.list.format)
        assertEquals("any", CDDL.any.format)
        assertEquals("tdate", CDDL.tdate.format)
        assertEquals("time", CDDL.time.format)
        assertEquals("full-date", CDDL.full_date.format)
    }

    // Major types
    @Test
    fun testCDDLMajorTypes() {
        assertEquals(MajorType.UNICODE_STRING, CDDL.tstr.majorType)
        assertEquals(MajorType.UNSIGNED_INTEGER, CDDL.uint.majorType)
        assertEquals(MajorType.NEGATIVE_INTEGER, CDDL.nint.majorType)
        assertEquals(null, CDDL.int.majorType) // int is alias
        assertEquals(MajorType.BYTE_STRING, CDDL.bstr.majorType)
        assertEquals(MajorType.SPECIAL, CDDL.bool.majorType)
        assertEquals(MajorType.SPECIAL, CDDL.float.majorType)
        assertEquals(MajorType.MAP, CDDL.map.majorType)
        assertEquals(MajorType.ARRAY, CDDL.list.majorType)
        assertEquals(MajorType.TAG, CDDL.tdate.majorType)
    }

    // Info values
    @Test
    fun testCDDLInfoValues() {
        assertEquals(20, CDDL.False.info)
        assertEquals(21, CDDL.True.info)
        assertEquals(22, CDDL.nil.info)
        assertEquals(22, CDDL.Null.info)
        assertEquals(23, CDDL.undefined.info)
        assertEquals(25, CDDL.float16.info)
        assertEquals(26, CDDL.float32.info)
        assertEquals(27, CDDL.float64.info)
        assertEquals(0, CDDL.tdate.info) // DATE_TIME_STRING
        assertEquals(1, CDDL.time.info) // DATE_TIME_NUMBER
        assertEquals(1004, CDDL.full_date.info) // FULL_DATE_STRING
    }

    // Alias types
    @Test
    fun testCDDLAliases() {
        assertTrue(CDDL.int.aliasFor.contains(CDDL.uint))
        assertTrue(CDDL.int.aliasFor.contains(CDDL.nint))
        assertTrue(CDDL.bool.aliasFor.contains(CDDL.True))
        assertTrue(CDDL.bool.aliasFor.contains(CDDL.False))
        assertTrue(CDDL.bytes.aliasFor.contains(CDDL.bstr))
        assertTrue(CDDL.text.aliasFor.contains(CDDL.tstr))
        assertTrue(CDDL.Null.aliasFor.contains(CDDL.nil))
    }

    // util.fromFormat
    @Test
    fun testFromFormat() {
        assertEquals(CDDL.tstr, CDDL.util.fromFormat("tstr"))
        assertEquals(CDDL.uint, CDDL.util.fromFormat("uint"))
        assertEquals(CDDL.bool, CDDL.util.fromFormat("bool"))
        assertEquals(CDDL.any, CDDL.util.fromFormat("any"))
    }

    @Test
    fun testFromFormatInvalid() {
        assertFailsWith<NoSuchElementException> {
            CDDL.util.fromFormat("invalid")
        }
    }

    // util.fromTag
    @Test
    fun testFromTag() {
        // fromTag expects format like "#0.1" with major type and additional info
        val result = CDDL.util.fromTag("#7.20")
        assertEquals(CDDL.False, result)
    }

    @Test
    fun testFromTagInvalid() {
        assertFailsWith<IllegalArgumentException> {
            CDDL.util.fromTag("invalid")
        }
    }

    // util.fromMajorType
    @Test
    fun testFromMajorType() {
        // fromMajorType requires both majorType and additionalInfo to match entries
        assertEquals(CDDL.uint, CDDL.util.fromMajorType(MajorType.UNSIGNED_INTEGER, null))
        assertEquals(CDDL.nint, CDDL.util.fromMajorType(MajorType.NEGATIVE_INTEGER, null))
        assertEquals(CDDL.bstr, CDDL.util.fromMajorType(MajorType.BYTE_STRING, null))
        assertEquals(CDDL.tstr, CDDL.util.fromMajorType(MajorType.UNICODE_STRING, null))
        assertEquals(CDDL.list, CDDL.util.fromMajorType(MajorType.ARRAY, null))
        assertEquals(CDDL.map, CDDL.util.fromMajorType(MajorType.MAP, null))
        // SPECIAL requires info to match specific simple values
        assertEquals(CDDL.False, CDDL.util.fromMajorType(MajorType.SPECIAL, 20))
        assertEquals(CDDL.True, CDDL.util.fromMajorType(MajorType.SPECIAL, 21))
    }

    // toTag
    @Test
    fun testToTag() {
        val tag = CDDL.uint.toTag()
        assertTrue(tag.startsWith("#"))
    }

    // newCborItemFromJson tests

    @Test
    fun testNewCborItemFromJsonNull() {
        val result = CDDL.any.newCborItemFromJson(JsonNull)
        assertIs<CborNull>(result)
    }

    @Test
    fun testNewCborItemFromJsonString() {
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive("test"))
        assertIs<CborString>(result)
        assertEquals("test", (result as CborString).value)
    }

    @Test
    fun testNewCborItemFromJsonNumber() {
        // Need to pass the cddl parameter explicitly (second param) for type-specific conversion
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive(42), CDDL.uint)
        assertIs<CborUInt>(result)
        assertEquals(42L, (result as CborUInt).value)
    }

    @Test
    fun testNewCborItemFromJsonBoolean() {
        // Need to pass the cddl parameter explicitly for type-specific conversion
        val trueResult = CDDL.any.newCborItemFromJson(JsonPrimitive(true), CDDL.bool)
        assertEquals(CborSimple.TRUE, trueResult)

        val falseResult = CDDL.any.newCborItemFromJson(JsonPrimitive(false), CDDL.bool)
        assertEquals(CborSimple.FALSE, falseResult)
    }

    @Test
    fun testNewCborItemFromJsonArray() {
        val array = JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2)))
        val result = CDDL.any.newCborItemFromJson(array)
        assertIs<CborArray<*>>(result)
        assertEquals(2, (result as CborArray<*>).value.size)
    }

    @Test
    fun testNewCborItemFromJsonObject() {
        val obj = JsonObject(mapOf("key" to JsonPrimitive("value")))
        val result = CDDL.any.newCborItemFromJson(obj)
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testNewCborItemFromJsonFloat() {
        // Need to pass the cddl parameter explicitly for type-specific conversion
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive(3.14f), CDDL.float)
        assertIs<CborFloat32>(result)
    }

    @Test
    fun testNewCborItemFromJsonDouble() {
        // Need to pass the cddl parameter explicitly for type-specific conversion
        val result = CDDL.any.newCborItemFromJson(JsonPrimitive(3.14), CDDL.float64)
        assertIs<CborDouble>(result)
    }

    // newCborItem tests

    @Test
    fun testNewCborItemString() {
        val result = CDDL.tstr.newCborItem("test")
        assertIs<CborString>(result)
    }

    @Test
    fun testNewCborItemByteArray() {
        val result = CDDL.bstr.newCborItem(byteArrayOf(0x01, 0x02))
        assertIs<CborByteString>(result)
    }

    @Test
    fun testNewCborItemNull() {
        val result = CDDL.nil.newCborItem(null)
        assertIs<CborSimple<*>>(result)
    }

    @Test
    fun testNewCborItemUnit() {
        val result = CDDL.any.newCborItem(Unit)
        assertEquals(CborSimple.UNDEFINED, result)
    }

    // CDDL equality
    @Test
    fun testCDDLEquality() {
        assertEquals(CDDL.tstr, CDDL.tstr)
        assertTrue(CDDL.tstr != CDDL.bstr)
    }

    @Test
    fun testCDDLHashCode() {
        assertEquals(CDDL.tstr.hashCode(), CDDL.tstr.hashCode())
    }

    @Test
    fun testCDDLToString() {
        val str = CDDL.tstr.toString()
        assertTrue(str.contains("tstr"))
        assertTrue(str.contains("CDDL"))
    }

    // CborItemJson tests

    @Test
    fun testCborItemJsonIsCborItemValueJson() {
        val validJson =
            JsonObject(
                mapOf(
                    "cddl" to JsonPrimitive("uint"),
                    "value" to JsonPrimitive(42),
                ),
            )
        assertTrue(CborItemJson.isCborItemValueJson(validJson))

        val invalidJson = JsonObject(mapOf("key" to JsonPrimitive("value")))
        assertEquals(false, CborItemJson.isCborItemValueJson(invalidJson))

        val notObject = JsonPrimitive("test")
        assertEquals(false, CborItemJson.isCborItemValueJson(notObject))
    }

    @Test
    fun testCborItemJsonIsCborItemJson() {
        val validJson =
            JsonObject(
                mapOf(
                    "key" to JsonPrimitive("myKey"),
                    "cddl" to JsonPrimitive("uint"),
                    "value" to JsonPrimitive(42),
                ),
            )
        assertTrue(CborItemJson.isCborItemJson(validJson))

        val invalidJson =
            JsonObject(
                mapOf(
                    "cddl" to JsonPrimitive("uint"),
                    "value" to JsonPrimitive(42),
                ),
            )
        assertEquals(false, CborItemJson.isCborItemJson(invalidJson))
    }

    @Test
    fun testCborItemJsonFromJsonPrimitive() {
        val result = CborItemJson.fromJsonPrimitive(JsonPrimitive("test"), CDDL.tstr, "myKey")
        assertIs<CborItemJson>(result)
        assertEquals("myKey", result.key)
        assertEquals(CDDL.tstr, result.cddl)
    }

    @Test
    fun testCborItemJsonFromJsonPrimitiveWithoutKey() {
        val result = CborItemJson.fromJsonPrimitive(JsonPrimitive(42), CDDL.uint)
        assertEquals(CDDL.uint, result.cddl)
    }

    @Test
    fun testCborItemJsonFromJsonObjectAsValueJson() {
        val json =
            JsonObject(
                mapOf(
                    "cddl" to JsonPrimitive("uint"),
                    "value" to JsonPrimitive(42),
                ),
            )
        val result = CborItemJson.fromJsonObjectAsValueJson(json)
        assertEquals(CDDL.uint, result.cddl)
    }

    @Test
    fun testCborItemJsonFromJsonObjectAsCborItemJson() {
        val json =
            JsonObject(
                mapOf(
                    "key" to JsonPrimitive("myKey"),
                    "cddl" to JsonPrimitive("uint"),
                    "value" to JsonPrimitive(42),
                ),
            )
        val result = CborItemJson.fromJsonObjectAsCborItemJson(json)
        assertEquals("myKey", result.key)
    }

    @Test
    fun testCborItemJsonFromDTO() {
        val original = CborItemJson("key", JsonPrimitive(42), CDDL.uint)
        val result = CborItemJson.fromDTO(original)
        assertEquals(original, result)
    }

    @Test
    fun testCborItemJsonToJsonWithCDDL() {
        val item = CborItemJson("key", JsonPrimitive(42), CDDL.uint)
        val json = item.toJsonWithCDDL()
        assertIs<JsonObject>(json)
    }

    @Test
    fun testCborItemJsonToJsonSimple() {
        val item = CborItemJson("key", JsonPrimitive(42), CDDL.uint)
        val json = item.toJsonSimple()
        assertIs<JsonObject>(json)
    }

    @Test
    fun testCborItemJsonToJson() {
        val item = CborItemJson("key", JsonPrimitive(42), CDDL.uint)

        val withCDDL = item.toJson(includeCDDL = true)
        val withoutCDDL = item.toJson(includeCDDL = false)

        assertNotEquals(withCDDL.toString(), withoutCDDL.toString())
    }

    // JsonObject extension
    @Test
    fun testJsonObjectToCborJsonItem() {
        val json =
            JsonObject(
                mapOf(
                    "cddl" to JsonPrimitive("uint"),
                    "value" to JsonPrimitive(42),
                ),
            )
        val result = json.jsonObjectToCborJsonItem()
        assertEquals(CDDL.uint, result.cddl)
    }

    // MajorType tests
    @Test
    fun testMajorTypeFromInt() {
        assertEquals(MajorType.UNSIGNED_INTEGER, MajorType.fromInt(0))
        assertEquals(MajorType.NEGATIVE_INTEGER, MajorType.fromInt(1))
        assertEquals(MajorType.BYTE_STRING, MajorType.fromInt(2))
        assertEquals(MajorType.UNICODE_STRING, MajorType.fromInt(3))
        assertEquals(MajorType.ARRAY, MajorType.fromInt(4))
        assertEquals(MajorType.MAP, MajorType.fromInt(5))
        assertEquals(MajorType.TAG, MajorType.fromInt(6))
        assertEquals(MajorType.SPECIAL, MajorType.fromInt(7))
    }

    @Test
    fun testMajorTypeType() {
        assertEquals(0, MajorType.UNSIGNED_INTEGER.type)
        assertEquals(1, MajorType.NEGATIVE_INTEGER.type)
        assertEquals(2, MajorType.BYTE_STRING.type)
        assertEquals(3, MajorType.UNICODE_STRING.type)
        assertEquals(4, MajorType.ARRAY.type)
        assertEquals(5, MajorType.MAP.type)
        assertEquals(6, MajorType.TAG.type)
        assertEquals(7, MajorType.SPECIAL.type)
    }

    // CDDL.any tests
    @Test
    fun testCDDLAnyNewAny() {
        val item = CDDL.any.newAny("test")
        assertIs<CborAny<*>>(item)
    }

    @Test
    fun testCDDLAnyFromJson() {
        val result = CDDL.any.fromJson(JsonPrimitive("test"))
        assertIs<CborString>(result)
    }

    // CDDL.any newCborItem for various types
    @Test
    fun testCDDLAnyNewCborItemVariousTypes() {
        // String
        val strResult = CDDL.any.newCborItem("test")
        assertIs<CborString>(strResult)

        // Long (cddl_uint is Long)
        val longResult = CDDL.any.newCborItem(42L)
        assertIs<CborUInt>(longResult)

        // ByteArray
        val bytesResult = CDDL.any.newCborItem(byteArrayOf(0x01))
        assertIs<CborByteString>(bytesResult)

        // Float
        val floatResult = CDDL.any.newCborItem(3.14f)
        assertIsCborFloat(floatResult)

        // Double
        val doubleResult = CDDL.any.newCborItem(3.14)
        assertIsCborDouble(doubleResult)

        // Boolean
        val boolResult = CDDL.any.newCborItem(true)
        assertEquals(CborSimple.TRUE, boolResult)

        // List (use Long values since cddl_int is Long)
        val listResult = CDDL.any.newCborItem(mutableListOf<Long>(1L, 2L, 3L))
        assertIs<CborArray<*>>(listResult)

        // Map (use Long values for consistency)
        val mapResult = CDDL.any.newCborItem(mutableMapOf("key" to "value"))
        assertIs<CborMap<*, *>>(mapResult)
    }

    // CDDL serializer
    @Test
    fun testCDDLSerializerDescriptor() {
        val descriptor = CDDLSerializer.descriptor
        assertEquals("CDDL", descriptor.serialName)
    }
}
