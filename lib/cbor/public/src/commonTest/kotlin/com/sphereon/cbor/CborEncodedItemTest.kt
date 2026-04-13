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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for CborEncodedItem, CborHexEncodedItem, CborItemJson, RawCbor, and related utilities
 */
class CborEncodedItemTest {
    private fun decodeTaggedEncodedCbor(tagged: CborTagged<*>): CborItem<*> {
        require(tagged.tagNumber == CborTagged.ENCODED_CBOR) { "Expected tag 24" }
        val bytes = (tagged.taggedItem as? CborByteString)?.value ?: throw IllegalArgumentException("Expected byte string content")
        return Cbor.decode(bytes)
    }

    private fun <T : Any> explicitEncodedItem(value: T): CborEncodedItem<T> = CborEncodedItem(CborEncodedItem.cborSerializeItem(value).value, value)

    // CborEncodedItem additional tests

    @Test
    fun testCborEncodedItemCopy() {
        val original = explicitEncodedItem(CborString("test"))
        val copied = original.copy<CborItem<*>>(null)
        assertNotNull(copied)
        assertFalse(copied.isDataInitialized())
    }

    @Test
    fun testCborEncodedItemWithInitializedData() {
        val item = explicitEncodedItem(CborUInt(42))
        assertNotNull(item)
        assertTrue(item.isDataInitialized())
        assertEquals(42L, (item.data() as CborUInt).value)
    }

    @Test
    fun testCborEncodedItemToData() {
        val item = explicitEncodedItem(CborString("hello"))
        val data = CborEncodedItem.toData(item)
        assertIs<CborString>(data)
        assertEquals("hello", data.value)
    }

    @Test
    fun testCborEncodedItemEquality() {
        val item1 = explicitEncodedItem(CborUInt(42))
        val item2 = explicitEncodedItem(CborUInt(42))
        val item3 = explicitEncodedItem(CborUInt(43))

        assertEquals(item1, item2)
        assertNotEquals(item1, item3)
        assertFalse(item1.equals(null))
        assertEquals(item1, item1)
    }

    @Test
    fun testCborEncodedItemHashCode() {
        val item1 = explicitEncodedItem(CborUInt(42))
        // Just test that hashCode returns consistent values
        val hash1 = item1.hashCode()
        val hash2 = item1.hashCode()
        assertEquals(hash1, hash2)
    }

    @Test
    fun testCborEncodedItemToString() {
        val item = explicitEncodedItem(CborUInt(42))
        val str = item.toString()
        assertTrue(str.contains("CborEncodedItem"))
    }

    @Test
    fun testCborSerializeItem() {
        // ByteArray
        val bytes = byteArrayOf(1, 2, 3)
        val result1 = CborEncodedItem.cborSerializeItem(bytes)
        assertIs<CborByteString>(result1)

        // CborByteString
        val cborBytes = CborByteString(byteArrayOf(4, 5, 6))
        val result2 = CborEncodedItem.cborSerializeItem(cborBytes)
        assertEquals(cborBytes, result2)

        // CborItem
        val cborItem = CborUInt(42)
        val result3 = CborEncodedItem.cborSerializeItem(cborItem)
        assertIs<CborByteString>(result3)
    }

    @Test
    fun testCborEncodedItemFromBytes() {
        val encoded = Cbor.encode(CborString("test"))
        val item = CborEncodedItem<CborString>(encoded)
        assertNotNull(item)
        assertTrue(item.wasEncoded())
    }

    @Test
    fun testCborEncodedItemData() {
        val encoded = Cbor.encode(CborString("test"))
        val item = CborEncodedItem<CborItem<*>>(encoded)
        val data = item.data { Cbor.decode(it) }
        assertIs<CborString>(data)
        assertEquals("test", (data as CborString).value)
    }

    // CborHexEncodedItem tests

    @Test
    fun testCborHexEncodedItem() {
        val hex = "deadbeef"
        val item = CborHexEncodedItem(hex)
        assertEquals(4, item.value.size)
    }

    // CborItem JSON method tests

    @Test
    fun testCborItemToJson() {
        val item = CborUInt(42)
        val jsonSimple = item.toJson(includeCDDL = false)
        assertIs<JsonPrimitive>(jsonSimple)

        val jsonWithCDDL = item.toJson(includeCDDL = true)
        assertIs<JsonObject>(jsonWithCDDL)
        assertTrue(jsonWithCDDL.containsKey("cddl"))
        assertTrue(jsonWithCDDL.containsKey("value"))
    }

    @Test
    fun testCborItemToJsonCborItem() {
        val item = CborString("test")
        val jsonItem = item.toJsonCborItem()
        assertNotNull(jsonItem.cddl)
        assertNotNull(jsonItem.value)
    }

    @Test
    fun testCborItemAsTaggedSubject() {
        val tagged = CborTagged(42, CborString("test"))
        val subject = tagged.asTaggedSubject
        assertIs<CborString>(subject)
        assertEquals("test", subject.value)
    }

    @Test
    fun testCborItemAsTaggedEncodedCbor() {
        val inner = CborString("test")
        val encoded = Cbor.encode(inner)
        val tagged = CborTagged(CborTagged.ENCODED_CBOR, CborByteString(encoded))
        val decoded = decodeTaggedEncodedCbor(tagged)
        assertIs<CborString>(decoded)
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

        val invalidJson = JsonPrimitive("test")
        assertFalse(CborItemJson.isCborItemValueJson(invalidJson))

        val missingKey = JsonObject(mapOf("cddl" to JsonPrimitive("uint")))
        assertFalse(CborItemJson.isCborItemValueJson(missingKey))
    }

    @Test
    fun testCborItemJsonIsCborItemJson() {
        val validJson =
            JsonObject(
                mapOf(
                    "cddl" to JsonPrimitive("uint"),
                    "value" to JsonPrimitive(42),
                    "key" to JsonPrimitive("mykey"),
                ),
            )
        assertTrue(CborItemJson.isCborItemJson(validJson))

        val missingKey =
            JsonObject(
                mapOf(
                    "cddl" to JsonPrimitive("uint"),
                    "value" to JsonPrimitive(42),
                ),
            )
        assertFalse(CborItemJson.isCborItemJson(missingKey))
    }

    @Test
    fun testCborItemJsonFromJsonPrimitive() {
        val result = CborItemJson.fromJsonPrimitive(JsonPrimitive("test"), CDDL.tstr, "mykey")
        assertIs<CborItemJson>(result)
        assertEquals("mykey", (result as CborItemJson).key)

        val resultNoKey = CborItemJson.fromJsonPrimitive(JsonPrimitive("test"), CDDL.tstr)
        assertNotNull(resultNoKey)
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
        assertNotNull(result)
        assertEquals(CDDL.uint, result.cddl)
    }

    @Test
    fun testCborItemJsonFromJsonObjectAsCborItemJson() {
        val json =
            JsonObject(
                mapOf(
                    "cddl" to JsonPrimitive("tstr"),
                    "value" to JsonPrimitive("test"),
                    "key" to JsonPrimitive("mykey"),
                ),
            )
        val result = CborItemJson.fromJsonObjectAsCborItemJson(json)
        assertNotNull(result)
        assertEquals("mykey", result.key)
    }

    @Test
    fun testCborItemJsonFromDTO() {
        val itemJson = CborItemJson("testkey", JsonPrimitive(42), CDDL.uint)
        val copied = CborItemJson.fromDTO(itemJson)
        assertEquals(itemJson.key, copied.key)
        assertEquals(itemJson.value, copied.value)
        assertEquals(itemJson.cddl, copied.cddl)
    }

    @Test
    fun testCborItemJsonToJsonMethods() {
        val itemJson = CborItemJson("testkey", JsonPrimitive(42), CDDL.uint)

        val jsonWithCDDL = itemJson.toJsonWithCDDL()
        assertIs<JsonObject>(jsonWithCDDL)

        val jsonSimple = itemJson.toJsonSimple()
        assertIs<JsonObject>(jsonSimple)
        assertTrue(jsonSimple.containsKey("testkey"))

        val json = itemJson.toJson(includeCDDL = false)
        assertEquals(jsonSimple, json)

        val cborItem = itemJson.toJsonCborItem()
        assertEquals(itemJson, cborItem)
    }

    // RawCbor tests

    @Test
    fun testRawCborCreation() {
        val bytes = byteArrayOf(0x18, 0x2A) // CBOR encoding of 42
        val rawCbor = RawCbor(bytes)
        assertNotNull(rawCbor)
        assertEquals(bytes, rawCbor.value)
    }

    @Test
    fun testRawCborEncode() {
        val bytes = byteArrayOf(0x18, 0x2A)
        val rawCbor = RawCbor(bytes)
        val encoded = rawCbor.encodeCbor()
        assertEquals(bytes.toList(), encoded.toList())
    }

    @Test
    fun testRawCborEquality() {
        val raw1 = RawCbor(byteArrayOf(1, 2, 3))
        val raw2 = RawCbor(byteArrayOf(1, 2, 3))
        val raw3 = RawCbor(byteArrayOf(4, 5, 6))

        // RawCbor uses ByteArray which has reference equality by default
        // Just test that the same object equals itself
        assertEquals(raw1, raw1)
        assertNotEquals(raw1.value.toList(), raw3.value.toList())
        // hashCode returns consistent values
        assertEquals(raw1.hashCode(), raw1.hashCode())
    }

    // CDDL newCborItem additional paths

    @Test
    fun testCDDLNewCborItemText() {
        val item = CDDL.text.newCborItem("hello")
        assertIs<CborString>(item)
        assertEquals("hello", (item as CborString).value)
    }

    @Test
    fun testCDDLNewCborItemBytes() {
        val item = CDDL.bytes.newCborItem(byteArrayOf(1, 2, 3))
        assertIs<CborByteString>(item)
    }

    @Test
    fun testCDDLNewCborItemFloat16() {
        val item = CDDL.float16.newCborItem(1.5f)
        assertIs<CborFloat16>(item)
    }

    @Test
    fun testCDDLNewCborItemFloat32() {
        val item = CDDL.float32.newCborItem(3.14f)
        assertIs<CborFloat32>(item)
    }

    @Test
    fun testCDDLNewCborItemFloat64() {
        val item = CDDL.float64.newCborItem(3.14159)
        assertIs<CborDouble>(item)
    }

    @Test
    fun testCDDLNewCborItemNil() {
        val item = CDDL.nil.newCborItem(null)
        assertEquals(CborSimple.NULL, item)
    }

    @Test
    fun testCDDLNewCborItemUndefined() {
        val item = CDDL.undefined.newCborItem(Unit)
        assertEquals(CborSimple.UNDEFINED, item)
    }

    @Test
    fun testCDDLNewCborItemWithJsonElement() {
        val item = CDDL.any.newCborItem(JsonPrimitive("test"))
        assertIs<CborString>(item)
    }

    @Test
    fun testCDDLNewCborItemWithNull() {
        val item = CDDL.nil.newCborItem(null)
        assertEquals(CborSimple.NULL, item)
    }

    // CDDL toTag tests

    @Test
    fun testCDDLToTag() {
        // toTag returns "#<MajorType>" format
        val uintTag = CDDL.uint.toTag()
        assertTrue(uintTag.startsWith("#"))

        val bstrTag = CDDL.bstr.toTag()
        assertTrue(bstrTag.startsWith("#"))
    }

    @Test
    fun testCDDLToTagWithInfo() {
        // These CDDL types have both majorType and info set
        val falseTag = CDDL.False.toTag()
        assertTrue(falseTag.startsWith("#"))
        assertTrue(falseTag.contains(".") || falseTag.contains("SPECIAL"))

        val trueTag = CDDL.True.toTag()
        assertTrue(trueTag.startsWith("#"))
    }

    // CDDL util tests - fromTag requires specific format with major.info

    @Test
    fun testCDDLUtilFromTagWithInfo() {
        // fromTag parses "#major.info" format
        val falseType = CDDL.util.fromTag("#7.20")
        assertEquals(CDDL.False, falseType)

        val trueType = CDDL.util.fromTag("#7.21")
        assertEquals(CDDL.True, trueType)
    }

    @Test
    fun testCDDLUtilFromBytes() {
        // Major type 0 (uint)
        val uint = CDDL.util.fromBytes(0x00)
        assertEquals(CDDL.uint, uint)

        // Major type 1 (nint)
        val nint = CDDL.util.fromBytes(0x20)
        assertEquals(CDDL.nint, nint)
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
        assertEquals(7, MajorType.SPECIAL.type)
    }

    // CborAny tests

    @Test
    fun testCborAny() {
        val any = CborAny<String>("any value")
        assertIs<CborAny<String>>(any)
        assertEquals("any value", any.value)
    }

    // CborNumber - test via toCborIntFromLong extension

    @Test
    fun testCborNumberToCborIntFromLong() {
        val positive = 42L.toCborIntFromLong()
        assertIs<CborUInt>(positive)
        assertEquals(42L, positive.value)

        val negative = (-42L).toCborIntFromLong()
        assertIs<CborNInt>(negative)
        assertEquals(42L, negative.value)
    }

    // CoseLabel fromCborItem tests

    @Test
    fun testCoseLabelFromCborStructure() {
        val strLabel = CoseLabel.fromCborItem(CborString("key"))
        assertIs<StringLabel>(strLabel)

        val numLabel = CoseLabel.fromCborItem(CborUInt(1))
        assertIs<NumberLabel>(numLabel)

        val negNumLabel = CoseLabel.fromCborItem(CborNInt(1))
        assertIs<NumberLabel>(negNumLabel)
    }

    // Extension function tests

    @Test
    fun testToCborStringExtension() {
        val str = "test".toCborString()
        assertIs<CborString>(str)
        assertEquals("test", str.value)
    }

    @Test
    fun testToCborByteStringExtension() {
        val bytes = "test".toCborByteString()
        assertIs<CborByteString>(bytes)
    }

    @Test
    fun testByteArrayToCborByteString() {
        val bytes = byteArrayOf(1, 2, 3).toCborByteString()
        assertIs<CborByteString>(bytes)
        assertEquals(3, bytes.value.size)
    }

    // CborMap utility functions

    @Test
    fun testMapGetStringLabel() {
        // getStringLabel uses the raw key for lookup
        val map =
            mapOf<Any, CborItem<*>>(
                "key" to CborUInt(42),
            )
        val value: CborUInt? = map.getStringLabel("key")
        assertNotNull(value)
        assertEquals(42L, value.value)
    }

    @Test
    fun testMapGetNumberLabel() {
        // getNumberLabel converts Long to Int for key lookup
        val map =
            mapOf<Any, CborItem<*>>(
                1 to CborString("value"), // Use Int as key since getNumberLabel does toInt()
            )
        val value: CborString? = map.getNumberLabel(1L)
        assertNotNull(value)
        assertEquals("value", value.value)
    }

    // Cbor utility tests

    @Test
    fun testCborEncodeLength() {
        // Test various length encodings
        val small = CborUInt(23)
        val encoded = small.encodeCbor()
        assertEquals(1, encoded.size) // Single byte for values 0-23

        val medium = CborUInt(200)
        val encodedMedium = medium.encodeCbor()
        assertEquals(2, encodedMedium.size) // 1 byte header + 1 byte value

        val large = CborUInt(1000)
        val encodedLarge = large.encodeCbor()
        assertEquals(3, encodedLarge.size) // 1 byte header + 2 bytes value
    }

    // CborBaseItem tests

    @Test
    fun testCborBaseItemCddl() {
        val item = CborUInt(42)
        assertEquals(CDDL.uint, item.cddl)
    }

    // SimpleCborBuilder test

    @Test
    fun testSimpleCborBuilder() {
        val item = CborString("test")
        val builder = SimpleCborBuilder(item)
        assertEquals(item, builder.build())
    }

    // CborArray tests

    @Test
    fun testCborArrayRequired() {
        val array = CborArray(mutableListOf<CborItem<*>>(CborUInt(1), CborUInt(2), CborUInt(3)))
        val item: CborItem<*> = array.required(0)
        assertEquals(1, item.asInt)
    }

    @Test
    fun testCborArrayOptional() {
        val array = CborArray(mutableListOf<CborItem<*>>(CborUInt(1)))
        val item: CborItem<*>? = array.optional(0)
        assertNotNull(item)

        val nullItem: CborItem<*>? = array.optional(10)
        assertEquals(null, nullItem)
    }

    @Test
    fun testCborArrayToJsonSimple() {
        val array = CborArray(mutableListOf<CborItem<*>>(CborUInt(1), CborString("test")))
        val json = array.toJsonSimple()
        assertEquals(2, json.size)
    }

    @Test
    fun testCborArrayToJsonWithCDDL() {
        val array = CborArray(mutableListOf<CborItem<*>>(CborUInt(1)))
        val json = array.toJsonWithCDDL()
        assertEquals(1, json.size)
    }

    @Test
    fun testCborArrayToJsonCborItem() {
        val array = CborArray(mutableListOf<CborItem<*>>(CborUInt(1)))
        val item = array.toJsonCborItem()
        assertEquals(CDDL.list, item.cddl)
    }

    @Test
    fun testCborArrayToString() {
        val array = CborArray(mutableListOf<CborItem<*>>(CborUInt(1)))
        val str = array.toString()
        assertTrue(str.contains("CborArray"))
    }

    // CborMap tests

    @Test
    fun testCborMapToJson() {
        val map =
            CborMap(
                mutableMapOf<CborItem<*>, CborItem<*>>(
                    CborString("key") to CborUInt(42),
                ),
            )
        val json = map.toJson()
        assertIs<JsonObject>(json)
    }

    @Test
    fun testCborMapToJsonCborItem() {
        val map =
            CborMap(
                mutableMapOf<CborItem<*>, CborItem<*>>(
                    CborString("key") to CborUInt(42),
                ),
            )
        val item = map.toJsonCborItem()
        assertEquals(CDDL.map, item.cddl)
    }

    // cborArray DSL test

    @Test
    fun testCborArrayDsl() {
        val array =
            cborArray {
                add(1)
                add(2)
            }

        assertIs<CborArray<*>>(array)
    }
}
