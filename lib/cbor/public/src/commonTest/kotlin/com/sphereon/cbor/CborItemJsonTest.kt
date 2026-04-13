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
 * Tests for CborItemJson branches and functionality
 */
class CborItemJsonTest {

    // ========== CborItemJson basic tests ==========

    @Test
    fun testCborItemJsonCreation() {
        val itemJson = CborItemJson("testKey", JsonPrimitive("testValue"), CDDL.tstr)
        assertEquals("testKey", itemJson.key)
        assertEquals(JsonPrimitive("testValue"), itemJson.value)
        assertEquals(CDDL.tstr, itemJson.cddl)
    }

    @Test
    fun testCborItemJsonToJsonWithCDDL() {
        val itemJson = CborItemJson("myKey", JsonPrimitive(42), CDDL.uint)
        val json = itemJson.toJsonWithCDDL()
        assertIs<JsonObject>(json)
        assertTrue(json.containsKey("myKey"))
        val innerObj = json["myKey"]
        assertIs<JsonObject>(innerObj)
        assertTrue(innerObj.containsKey(VALUE_LITERAL))
        assertTrue(innerObj.containsKey(CDDL_LITERAL))
    }

    @Test
    fun testCborItemJsonToJsonSimple() {
        val itemJson = CborItemJson("myKey", JsonPrimitive("myValue"), CDDL.tstr)
        val json = itemJson.toJsonSimple()
        assertIs<JsonObject>(json)
        assertTrue(json.containsKey("myKey"))
        assertEquals(JsonPrimitive("myValue"), json["myKey"])
    }

    @Test
    fun testCborItemJsonToJsonWithIncludeCDDLTrue() {
        val itemJson = CborItemJson("key", JsonPrimitive(100), CDDL.uint)
        val json = itemJson.toJson(includeCDDL = true)
        assertIs<JsonObject>(json)
        // Should contain nested object with value and cddl
        val innerObj = json["key"]
        assertIs<JsonObject>(innerObj)
        assertTrue(innerObj.containsKey(VALUE_LITERAL))
    }

    @Test
    fun testCborItemJsonToJsonWithIncludeCDDLFalse() {
        val itemJson = CborItemJson("key", JsonPrimitive(100), CDDL.uint)
        val json = itemJson.toJson(includeCDDL = false)
        assertIs<JsonObject>(json)
        // Should be simple key-value
        assertEquals(JsonPrimitive(100), json["key"])
    }

    @Test
    fun testCborItemJsonToJsonCborItem() {
        val itemJson = CborItemJson("key", JsonPrimitive("value"), CDDL.tstr)
        val result = itemJson.toJsonCborItem()
        assertEquals(itemJson, result)
    }

    // ========== isCborItemValueJson tests ==========

    @Test
    fun testIsCborItemValueJsonWithValidObject2Elements() {
        val jsonObject = buildJsonObject {
            put(CDDL_LITERAL, JsonPrimitive("tstr"))
            put(VALUE_LITERAL, JsonPrimitive("test"))
        }
        assertTrue(CborItemJson.isCborItemValueJson(jsonObject))
    }

    @Test
    fun testIsCborItemValueJsonWithValidObject3Elements() {
        val jsonObject = buildJsonObject {
            put(CDDL_LITERAL, JsonPrimitive("tstr"))
            put(VALUE_LITERAL, JsonPrimitive("test"))
            put(KEY_LITERAL, JsonPrimitive("mykey"))
        }
        assertTrue(CborItemJson.isCborItemValueJson(jsonObject))
    }

    @Test
    fun testIsCborItemValueJsonWithInvalidObjectMissingCddl() {
        val jsonObject = buildJsonObject {
            put(VALUE_LITERAL, JsonPrimitive("test"))
        }
        assertFalse(CborItemJson.isCborItemValueJson(jsonObject))
    }

    @Test
    fun testIsCborItemValueJsonWithInvalidObjectMissingValue() {
        val jsonObject = buildJsonObject {
            put(CDDL_LITERAL, JsonPrimitive("tstr"))
        }
        assertFalse(CborItemJson.isCborItemValueJson(jsonObject))
    }

    @Test
    fun testIsCborItemValueJsonWithInvalidSize() {
        val jsonObject = buildJsonObject {
            put(CDDL_LITERAL, JsonPrimitive("tstr"))
            put(VALUE_LITERAL, JsonPrimitive("test"))
            put(KEY_LITERAL, JsonPrimitive("key"))
            put("extra", JsonPrimitive("extra"))
        }
        assertFalse(CborItemJson.isCborItemValueJson(jsonObject))
    }

    @Test
    fun testIsCborItemValueJsonWithNonObject() {
        val jsonPrimitive = JsonPrimitive("test")
        assertFalse(CborItemJson.isCborItemValueJson(jsonPrimitive))
    }

    @Test
    fun testIsCborItemValueJsonWithArray() {
        val jsonArray = buildJsonArray {
            add(JsonPrimitive("test"))
        }
        assertFalse(CborItemJson.isCborItemValueJson(jsonArray))
    }

    // ========== isCborItemJson tests ==========

    @Test
    fun testIsCborItemJsonWithValidObject() {
        val jsonObject = buildJsonObject {
            put(CDDL_LITERAL, JsonPrimitive("tstr"))
            put(VALUE_LITERAL, JsonPrimitive("test"))
            put(KEY_LITERAL, JsonPrimitive("mykey"))
        }
        assertTrue(CborItemJson.isCborItemJson(jsonObject))
    }

    @Test
    fun testIsCborItemJsonWithInvalidSize() {
        val jsonObject = buildJsonObject {
            put(CDDL_LITERAL, JsonPrimitive("tstr"))
            put(VALUE_LITERAL, JsonPrimitive("test"))
        }
        assertFalse(CborItemJson.isCborItemJson(jsonObject))
    }

    @Test
    fun testIsCborItemJsonWithMissingKey() {
        val jsonObject = buildJsonObject {
            put(CDDL_LITERAL, JsonPrimitive("tstr"))
            put(VALUE_LITERAL, JsonPrimitive("test"))
            put("other", JsonPrimitive("other"))
        }
        assertFalse(CborItemJson.isCborItemJson(jsonObject))
    }

    @Test
    fun testIsCborItemJsonWithNonObject() {
        val jsonPrimitive = JsonPrimitive("test")
        assertFalse(CborItemJson.isCborItemJson(jsonPrimitive))
    }

    // ========== fromJsonPrimitive tests ==========

    @Test
    fun testFromJsonPrimitiveWithKey() {
        val result = CborItemJson.fromJsonPrimitive(JsonPrimitive("value"), CDDL.tstr, "myKey")
        assertIs<CborItemJson>(result)
        assertEquals("myKey", (result as CborItemJson).key)
        assertEquals(JsonPrimitive("value"), result.value)
    }

    @Test
    fun testFromJsonPrimitiveWithoutKey() {
        val result = CborItemJson.fromJsonPrimitive(JsonPrimitive("value"), CDDL.tstr)
        assertIs<ICborItemValueJson>(result)
        assertEquals(CDDL.tstr, result.cddl)
        assertEquals(JsonPrimitive("value"), result.value)
    }

    @Test
    fun testFromJsonPrimitiveWithNullKey() {
        val result = CborItemJson.fromJsonPrimitive(JsonPrimitive(123), CDDL.uint, null)
        assertIs<ICborItemValueJson>(result)
        assertEquals(CDDL.uint, result.cddl)
    }

    // ========== fromJsonObjectAsCborItemJson tests ==========

    @Test
    fun testFromJsonObjectAsCborItemJsonValid() {
        val jsonObject = buildJsonObject {
            put(CDDL_LITERAL, JsonPrimitive("tstr"))
            put(VALUE_LITERAL, JsonPrimitive("testvalue"))
            put(KEY_LITERAL, JsonPrimitive("testkey"))
        }
        val result = CborItemJson.fromJsonObjectAsCborItemJson(jsonObject)
        assertIs<ICborItemJson>(result)
        assertEquals("testkey", result.key)
    }

    @Test
    fun testFromJsonObjectAsCborItemJsonInvalid() {
        val jsonObject = buildJsonObject {
            put("random", JsonPrimitive("value"))
        }
        assertFailsWith<IllegalStateException> {
            CborItemJson.fromJsonObjectAsCborItemJson(jsonObject)
        }
    }

    // ========== fromJsonObjectAsValueJson tests ==========

    @Test
    fun testFromJsonObjectAsValueJsonWithKey() {
        val jsonObject = buildJsonObject {
            put(CDDL_LITERAL, JsonPrimitive("tstr"))
            put(VALUE_LITERAL, JsonPrimitive("testvalue"))
            put(KEY_LITERAL, JsonPrimitive("testkey"))
        }
        val result = CborItemJson.fromJsonObjectAsValueJson(jsonObject)
        assertIs<ICborItemJson>(result)
        assertEquals("testkey", (result as ICborItemJson).key)
    }

    @Test
    fun testFromJsonObjectAsValueJsonWithoutKey() {
        val jsonObject = buildJsonObject {
            put(CDDL_LITERAL, JsonPrimitive("tstr"))
            put(VALUE_LITERAL, JsonPrimitive("testvalue"))
        }
        val result = CborItemJson.fromJsonObjectAsValueJson(jsonObject)
        assertIs<ICborItemValueJson>(result)
        assertEquals(JsonPrimitive("testvalue"), result.value)
    }

    @Test
    fun testFromJsonObjectAsValueJsonInvalid() {
        val jsonObject = buildJsonObject {
            put("random", JsonPrimitive("value"))
        }
        assertFailsWith<IllegalStateException> {
            CborItemJson.fromJsonObjectAsValueJson(jsonObject)
        }
    }

    // ========== fromDTO tests ==========

    @Test
    fun testFromDTO() {
        val original = CborItemJson("key", JsonPrimitive("value"), CDDL.tstr)
        val result = CborItemJson.fromDTO(original)
        assertEquals(original.key, result.key)
        assertEquals(original.value, result.value)
        assertEquals(original.cddl, result.cddl)
    }

    @Test
    fun testFromDTOWithCustomInterface() {
        val customImpl = object : ICborItemJson {
            override val key: String = "customKey"
            override val value = JsonPrimitive("customValue")
            override val cddl = CDDL.tstr
        }
        val result = CborItemJson.fromDTO(customImpl)
        assertEquals("customKey", result.key)
        assertEquals(JsonPrimitive("customValue"), result.value)
    }

    // ========== jsonObjectToCborJsonItem extension tests ==========

    @Test
    fun testJsonObjectToCborJsonItemExtension() {
        val jsonObject = buildJsonObject {
            put(CDDL_LITERAL, JsonPrimitive("uint"))
            put(VALUE_LITERAL, JsonPrimitive(42))
        }
        val result = jsonObject.jsonObjectToCborJsonItem()
        assertIs<ICborItemValueJson>(result)
        assertEquals(JsonPrimitive(42), result.value)
    }

    // ========== SimpleCborBuilder tests ==========

    @Test
    fun testSimpleCborBuilder() {
        val item = CborUInt(42)
        val builder = SimpleCborBuilder(item)
        val built = builder.build()
        assertEquals(item, built)
    }

    // ========== HasCborJsonRepresentation interface tests ==========

    @Test
    fun testCborUIntHasCborJsonRepresentation() {
        val uint = CborUInt(100)
        val jsonSimple = uint.toJsonSimple()
        assertIs<JsonPrimitive>(jsonSimple)
        assertEquals("100", jsonSimple.content)

        val jsonWithCddl = uint.toJsonWithCDDL()
        assertNotNull(jsonWithCddl)

        val jsonCborItem = uint.toJsonCborItem()
        assertNotNull(jsonCborItem)
    }

    @Test
    fun testCborStringHasCborJsonRepresentation() {
        val str = CborString("test")
        val jsonSimple = str.toJsonSimple()
        assertIs<JsonPrimitive>(jsonSimple)
        assertEquals("test", jsonSimple.content)
    }

    @Test
    fun testCborArrayToJsonWithCDDL() {
        val array = CborArray(mutableListOf(CborUInt(1), CborUInt(2)))
        val json = array.toJsonWithCDDL()
        assertIs<JsonArray>(json)
    }

    @Test
    fun testCborArrayToJsonCborItem() {
        val array = CborArray(mutableListOf(CborString("a"), CborString("b")))
        val result = array.toJsonCborItem()
        assertNotNull(result)
    }
}
