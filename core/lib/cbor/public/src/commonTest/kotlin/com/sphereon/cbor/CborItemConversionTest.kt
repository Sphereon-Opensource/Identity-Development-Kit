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
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CborItemConversionTest {
    private fun decodeTaggedEncodedCbor(tagged: CborTagged<*>): CborItem<*> {
        require(tagged.tagNumber == CborTagged.ENCODED_CBOR) { "Expected tag 24" }
        val bytes = (tagged.taggedItem as? CborByteString)?.value ?: throw IllegalArgumentException("Expected byte string content")
        return Cbor.decode(bytes)
    }

    // toCborItem() extension function tests

    @Test
    fun testToCborItemFromString() {
        val result = "hello".toCborItem()
        assertIs<CborString>(result)
        assertEquals("hello", result.value)
    }

    @Test
    fun testToCborItemFromByteArray() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        val result = bytes.toCborItem()
        assertIs<CborByteString>(result)
        assertEquals(3, result.value.size)
    }

    @Test
    fun testToCborItemFromByte() {
        val result = 42.toByte().toCborItem()
        assertIs<CborUInt>(result)
        assertEquals(42L, result.value)
    }

    @Test
    fun testToCborItemFromNegativeByte() {
        val negativeByte: Byte = -5
        val result = negativeByte.toCborItem()
        assertIs<CborNInt>(result)
    }

    @Test
    fun testToCborItemFromShort() {
        val result = 1000.toShort().toCborItem()
        assertIs<CborUInt>(result)
        assertEquals(1000L, result.value)
    }

    @Test
    fun testToCborItemFromInt() {
        val result = 42.toCborItem()
        assertIs<CborUInt>(result)
        assertEquals(42L, result.value)
    }

    @Test
    fun testToCborItemFromNegativeInt() {
        val result = (-42).toCborItem()
        assertIs<CborNInt>(result)
        assertEquals(-42L, result.value)
    }

    @Test
    fun testToCborItemFromLong() {
        val result = 1000000000000L.toCborItem()
        assertIs<CborUInt>(result)
        assertEquals(1000000000000L, result.value)
    }

    @Test
    fun testToCborItemFromNegativeLong() {
        val result = (-1000000000000L).toCborItem()
        assertIs<CborNInt>(result)
    }

    @Test
    fun testToCborItemFromUInt() {
        val result = 42u.toCborItem()
        assertIs<CborUInt>(result)
        assertEquals(42L, result.value)
    }

    @Test
    fun testToCborItemFromBoolean() {
        val trueResult = true.toCborItem()
        assertEquals(CborSimple.TRUE, trueResult)

        val falseResult = false.toCborItem()
        assertEquals(CborSimple.FALSE, falseResult)
    }

    @Test
    fun testToCborItemFromDouble() {
        val result = 3.14.toCborItem()
        assertIsCborDouble(result)
    }

    @Test
    fun testToCborItemFromFloat() {
        val result = 3.14f.toCborItem()
        assertIsCborFloat(result)
    }

    @Test
    fun testToCborItemFromList() {
        val result = listOf(1, 2, 3).toCborItem()
        assertIs<CborArray<*>>(result)
        assertEquals(3, (result as CborArray<*>).value.size)
    }

    @Test
    fun testToCborItemFromArray() {
        val result = arrayOf(1, 2, 3).toCborItem()
        assertIs<CborArray<*>>(result)
        assertEquals(3, (result as CborArray<*>).value.size)
    }

    @Test
    fun testToCborItemFromMap() {
        val result = mapOf("key" to "value").toCborItem()
        assertIs<CborMap<*, *>>(result)
        assertEquals(1, (result as CborMap<*, *>).value.size)
    }

    @Test
    fun testToCborItemFromNull() {
        val result = null.toCborItem()
        assertIs<CborNull>(result)
    }

    @Test
    fun testToCborItemFromCborItem() {
        val original = CborUInt(42)
        val result = original.toCborItem()
        assertEquals(original, result)
    }

    @Test
    fun testTDateToCborStructure() {
        val tdate = TDate("2024-01-15T10:30:00Z")
        val result = tdate.toCborItem()
        assertIs<CborTDate>(result)
    }

    @Test
    fun testToCborItemUnsupportedType() {
        class UnsupportedType
        assertFailsWith<IllegalArgumentException> {
            UnsupportedType().toCborItem()
        }
    }

    // Accessor property tests

    @Test
    fun testAsStr() {
        val item = CborString("hello")
        assertEquals("hello", item.asStr)
    }

    @Test
    fun testAsStrInvalid() {
        val item = CborUInt(42)
        assertFailsWith<IllegalArgumentException> {
            item.asStr
        }
    }

    @Test
    fun testAsBool() {
        val trueItem = CborSimple.TRUE
        assertEquals(true, trueItem.asBool)

        val falseItem = CborSimple.FALSE
        assertEquals(false, falseItem.asBool)
    }

    @Test
    fun testAsBoolInvalid() {
        val item = CborString("true")
        assertFailsWith<IllegalArgumentException> {
            item.asBool
        }
    }

    @Test
    fun testAsBstr() {
        val bytes = byteArrayOf(0x01, 0x02)
        val item = CborByteString(bytes)
        assertEquals(2, item.asBstr.size)
    }

    @Test
    fun testAsBstrInvalid() {
        val item = CborString("test")
        assertFailsWith<IllegalArgumentException> {
            item.asBstr
        }
    }

    @Test
    fun testAsLongFromUInt() {
        val item = CborUInt(42)
        assertEquals(42L, item.asLong)
    }

    @Test
    fun testAsLongFromNInt() {
        val item = CborNInt(42)
        assertEquals(-42L, item.asLong)
    }

    @Test
    fun testAsLongInvalid() {
        val item = CborString("42")
        assertFailsWith<IllegalArgumentException> {
            item.asLong
        }
    }

    @Test
    fun testAsIntFromUInt() {
        val item = CborUInt(42)
        assertEquals(42, item.asInt)
    }

    @Test
    fun testAsIntFromNInt() {
        val item = CborNInt(42)
        assertEquals(-42, item.asInt)
    }

    @Test
    fun testAsIntOverflow() {
        val item = CborUInt(Long.MAX_VALUE)
        assertFailsWith<IllegalArgumentException> {
            item.asInt
        }
    }

    @Test
    fun testAsIntInvalid() {
        val item = CborString("42")
        assertFailsWith<IllegalArgumentException> {
            item.asInt
        }
    }

    @Test
    fun testAsMap() {
        val map = CborMap(mutableMapOf(StringLabel("key") to CborUInt(1)))
        assertNotNull(map.asMap)
        assertEquals(1, map.asMap.size)
    }

    @Test
    fun testAsMapInvalid() {
        val item = CborArray(mutableListOf<CborItem<*>>())
        assertFailsWith<IllegalArgumentException> {
            item.asMap
        }
    }

    @Test
    fun testAsList() {
        val array = CborArray(mutableListOf(CborUInt(1), CborUInt(2)))
        assertNotNull(array.asList)
        assertEquals(2, array.asList.size)
    }

    @Test
    fun testAsListInvalid() {
        val item = CborMap(mutableMapOf<CoseLabel<*>, CborItem<*>>())
        assertFailsWith<IllegalArgumentException> {
            item.asList
        }
    }

    @Test
    fun testAsTaggedSubject() {
        val tagged = CborTagged(42, CborString("test"))
        val subject = tagged.asTaggedSubject
        assertIs<CborString>(subject)
        assertEquals("test", (subject as CborString).value)
    }

    @Test
    fun testAsTaggedSubjectInvalid() {
        val item = CborString("test")
        assertFailsWith<IllegalArgumentException> {
            item.asTaggedSubject
        }
    }

    @Test
    fun testAsTaggedEncodedCbor() {
        val inner = CborString("test")
        val encoded = Cbor.encode(inner)
        val tagged = CborTagged(CborTagged.ENCODED_CBOR.toInt(), CborByteString(encoded))
        val decoded = decodeTaggedEncodedCbor(tagged)
        assertIs<CborString>(decoded)
    }

    @Test
    fun testAsTaggedEncodedCborWrongTag() {
        val tagged = CborTagged(42, CborByteString(byteArrayOf(0x01)))
        assertFailsWith<IllegalArgumentException> {
            decodeTaggedEncodedCbor(tagged)
        }
    }

    // encodeCbor() and toBstr() tests

    @Test
    fun testEncodeCbor() {
        val item = CborUInt(42)
        val encoded = item.encodeCbor()
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun testToBstr() {
        val item = CborUInt(42)
        val bstr = item.toBstr()
        assertIs<CborByteString>(bstr)
        assertTrue(bstr.value.isNotEmpty())
    }

    // JSON conversion tests

    @Test
    fun testToJsonSimple() {
        val item = CborUInt(42)
        val json = item.toJsonSimple()
        assertIs<JsonPrimitive>(json)
        assertEquals("42", json.content)
    }

    @Test
    fun testToJsonWithCDDL() {
        val item = CborUInt(42)
        val json = item.toJsonWithCDDL()
        assertIs<JsonObject>(json)
        assertTrue(json.containsKey("cddl"))
        assertTrue(json.containsKey("value"))
    }

    @Test
    fun testToJsonWithIncludeCDDL() {
        val item = CborString("test")
        val withCddl = item.toJson(includeCDDL = true)
        val withoutCddl = item.toJson(includeCDDL = false)
        assertIs<JsonObject>(withCddl)
        assertIs<JsonPrimitive>(withoutCddl)
    }

    @Test
    fun testToJsonCborItem() {
        val item = CborUInt(42)
        val cborItem = item.toJsonCborItem()
        assertEquals(CDDL.uint, cborItem.cddl)
    }

    // Explicit Cbor/CborItem usage tests

    @Test
    fun testCborSupportSerializer() {
        val serializer = Cbor
        assertNotNull(serializer)
    }

    @Test
    fun testCborSupportItemToValue() {
        val item = CborUInt(42)
        val value = item.toValue()
        assertEquals(42L, value)
    }

    @Test
    fun testCborSupportItemToByteArray() {
        val item = CborUInt(42)
        val bytes = item.encodeCbor()
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun testCborSupportItemFromByteArray() {
        val original = CborUInt(42)
        val bytes = original.encodeCbor()
        val decoded = Cbor.decode<CborItem<*>>(bytes)
        assertIs<CborUInt>(decoded)
        assertEquals(42L, (decoded as CborUInt).value)
    }

    @Test
    fun testCborSupportDataItemFromValue() {
        val bytes = Cbor.encode(CborString("test"))
        val dataItem = CborEncodedItem<ByteArray>(bytes, bytes)
        assertIs<CborEncodedItem<*>>(dataItem)
    }

    @Test
    fun testCborSupportDataItemToByteArray() {
        val bytes = Cbor.encode(CborString("test"))
        val dataItem = CborEncodedItem<ByteArray>(bytes, bytes)
        val encoded = Cbor.encode(dataItem)
        assertNotNull(encoded)
    }

    // RawCbor tests

    @Test
    fun testRawCborCreation() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        val raw = RawCbor(bytes)
        assertEquals(3, raw.value.size)
    }

    @Test
    fun testRawCborToJsonSimple() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        val raw = RawCbor(bytes)
        val json = raw.toJsonSimple()
        assertIs<JsonPrimitive>(json)
    }

    @Test
    fun testRawCborEncode() {
        val inner = CborUInt(42)
        val encoded = Cbor.encode(inner)
        val raw = RawCbor(encoded)
        val reEncoded = Cbor.encode(raw)
        assertNotNull(reEncoded)
    }

    // CborItem equality and hashCode tests

    @Test
    fun testCborItemEquality() {
        val item1 = CborUInt(42)
        val item2 = CborUInt(42)
        val item3 = CborUInt(43)

        assertEquals(item1, item2)
        assertTrue(item1 != item3)
    }

    @Test
    fun testCborItemHashCode() {
        val item1 = CborUInt(42)
        val item2 = CborUInt(42)
        assertEquals(item1.hashCode(), item2.hashCode())
    }

    @Test
    fun testCborItemToString() {
        val item = CborUInt(42)
        val str = item.toString()
        assertTrue(str.contains("42"))
        assertTrue(str.contains("CborItem"))
    }

    // CborItem majorType and info tests

    @Test
    fun testCborItemMajorType() {
        assertEquals(MajorType.UNSIGNED_INTEGER, CborUInt(42).majorType)
        assertEquals(MajorType.NEGATIVE_INTEGER, CborNInt(42).majorType)
        assertEquals(MajorType.BYTE_STRING, CborByteString(byteArrayOf()).majorType)
        assertEquals(MajorType.UNICODE_STRING, CborString("test").majorType)
        assertEquals(MajorType.ARRAY, CborArray(mutableListOf<CborItem<*>>()).majorType)
        assertEquals(MajorType.MAP, CborMap(mutableMapOf<CoseLabel<*>, CborItem<*>>()).majorType)
    }

    // CborConst tests

    @Test
    fun testCborConst() {
        assertEquals("cddl", CborConst.CDDL_LITERAL)
        assertEquals("value", CborConst.VALUE_LITERAL)
        assertEquals("key", CborConst.KEY_LITERAL)
    }

    // Cbor global tests

    @Test
    fun testCborSerializerGlobal() {
        assertNotNull(Cbor)
        assertSame(Cbor, Cbor)
    }

    // Nested conversion tests

    @Test
    fun testNestedListConversion() {
        val nested = listOf(listOf(1, 2), listOf(3, 4))
        val result = nested.toCborItem()
        assertIs<CborArray<*>>(result)
        val arr = result as CborArray<*>
        assertEquals(2, arr.value.size)
        assertIs<CborArray<*>>(arr.value[0])
    }

    @Test
    fun testNestedMapConversion() {
        val nested = mapOf("outer" to mapOf("inner" to 42))
        val result = nested.toCborItem()
        assertIs<CborMap<*, *>>(result)
    }

    @Test
    fun testMixedListConversion() {
        val mixed = listOf(1, "hello", true, null, 3.14)
        val result = mixed.toCborItem()
        assertIs<CborArray<*>>(result)
        val arr = result as CborArray<*>
        assertEquals(5, arr.value.size)
    }

    // toValue() test

    @Test
    fun testToValue() {
        val item = CborString("test")
        assertEquals("test", item.toValue())

        val intItem = CborUInt(42)
        assertEquals(42L, intItem.toValue())
    }

    // CborBaseItem tests

    @Test
    fun testCborBaseItemCddl() {
        val item = CborUInt(42)
        assertEquals(CDDL.uint, item.cddl)
    }

    @Test
    fun testNumberLabelToCborStructure() {
        val label = NumberLabel(42)
        val cbor = label.toCborItem()
        assertIs<CborUInt>(cbor)
    }
}
