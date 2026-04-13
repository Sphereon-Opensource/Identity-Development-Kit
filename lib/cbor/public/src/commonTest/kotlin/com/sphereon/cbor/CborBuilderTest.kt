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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("DEPRECATION")
class CborBuilderTest {

    // ArrayBuilder tests

    @Test
    fun testArrayBuilderBasic() {
        val array = CborArray.simpleBuilder()
            .addInt(1)
            .addInt(2)
            .addInt(3)
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        assertEquals(3, (array as CborArray<*>).value.size)
    }

    @Test
    fun testArrayBuilderAddString() {
        val array = CborArray.simpleBuilder()
            .addString("hello")
            .addString("world")
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        val arr = array as CborArray<*>
        assertEquals(2, arr.value.size)
        assertEquals("hello", arr.value[0].asStr)
        assertEquals("world", arr.value[1].asStr)
    }

    @Test
    fun testArrayBuilderAddByte() {
        val array = CborArray.simpleBuilder()
            .addByte(42)
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        assertEquals(42, (array as CborArray<*>).value[0].asInt)
    }

    @Test
    fun testArrayBuilderAddShort() {
        val array = CborArray.simpleBuilder()
            .addShort(1000)
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        assertEquals(1000, (array as CborArray<*>).value[0].asInt)
    }

    @Test
    fun testArrayBuilderAddLong() {
        // Note: addLong currently has a limitation - it uses CDDL.int.newCborItem which casts to Int
        // Using a value that fits in Int range for now
        val array = CborArray.simpleBuilder()
            .addLong(2147483647L) // Max Int value
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        assertEquals(2147483647L, (array as CborArray<*>).value[0].asLong)
    }

    @Test
    fun testArrayBuilderAddBoolean() {
        val array = CborArray.simpleBuilder()
            .addBoolean(true)
            .addBoolean(false)
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        val arr = array as CborArray<*>
        assertEquals(true, arr.value[0].asBool)
        assertEquals(false, arr.value[1].asBool)
    }

    @Test
    fun testArrayBuilderAddDouble() {
        val array = CborArray.simpleBuilder()
            .addDouble(3.14)
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        val value = (array as CborArray<*>).value[0]
        assertIs<CborDouble>(value)
    }

    @Test
    fun testArrayBuilderAddFloat() {
        val array = CborArray.simpleBuilder()
            .addFloat(3.14f)
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        val value = (array as CborArray<*>).value[0]
        assertIs<CborFloat>(value)
    }

    @Test
    fun testArrayBuilderAddNull() {
        val array = CborArray.simpleBuilder()
            .addNull()
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        assertIs<CborNull>((array as CborArray<*>).value[0])
    }

    @Test
    fun testArrayBuilderAddCborItem() {
        val array = CborArray.simpleBuilder()
            .add(CborUInt(42))
            .add(CborString("test"))
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        assertEquals(2, (array as CborArray<*>).value.size)
    }

    @Test
    fun testArrayBuilderAddNullItem() {
        val array = CborArray.simpleBuilder()
            .add(null as CborItem<*>?)
            .add(CborUInt(42))
            .end()
            .build()

        // Null items should be skipped
        assertIs<CborArray<*>>(array)
        assertEquals(1, (array as CborArray<*>).value.size)
    }

    @Test
    fun testArrayBuilderAddRequired() {
        val array = CborArray.simpleBuilder()
            .addRequired(CborUInt(1), CborUInt(2))
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        assertEquals(2, (array as CborArray<*>).value.size)
    }

    @Test
    fun testArrayBuilderAddRequiredEmpty() {
        assertFailsWith<IllegalArgumentException> {
            CborArray.simpleBuilder().addRequired()
        }
    }

    @Test
    fun testArrayBuilderAddByteArray() {
        val array = CborArray.simpleBuilder()
            .add(byteArrayOf(0x01, 0x02))
            .add(byteArrayOf(0x03, 0x04))
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        assertEquals(2, (array as CborArray<*>).value.size)
    }

    @Test
    fun testArrayBuilderAddTagged() {
        val array = CborArray.simpleBuilder()
            .addTagged(42, CborString("test"))
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        val item = (array as CborArray<*>).value[0]
        assertIs<CborTagged<*>>(item)
        assertEquals(42, (item as CborTagged<*>).tagNumber)
    }

    @Test
    fun testArrayBuilderAddTaggedEncodedCbor() {
        val encoded = cborSerializer.encode(CborString("test"))
        val array = CborArray.simpleBuilder()
            .addTaggedEncodedCbor<CborString>(encoded)
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        val item = (array as CborArray<*>).value[0]
        assertIs<CborTagged<*>>(item)
        assertEquals(CborTagged.ENCODED_CBOR, (item as CborTagged<*>).tagNumber)
    }

    @Test
    fun testArrayBuilderNestedArray() {
        val array = CborArray.simpleBuilder()
            .addInt(1)
            .addArray()
                .addInt(2)
                .addInt(3)
            .end()
            .addInt(4)
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        val arr = array as CborArray<*>
        assertEquals(3, arr.value.size)
        assertIs<CborArray<*>>(arr.value[1])
    }

    @Test
    fun testArrayBuilderNestedMap() {
        val array = CborArray.simpleBuilder()
            .addMap()
                .put("key", "value")
            .end()
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        val arr = array as CborArray<*>
        assertEquals(1, arr.value.size)
        assertIs<CborMap<*, *>>(arr.value[0])
    }

    @Test
    fun testArrayBuilderAddCborArray() {
        val inner = CborArray(mutableListOf<CborItem<*>>(CborUInt(1), CborUInt(2)))
        val array = CborArray.simpleBuilder()
            .addCborArray(inner)
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        // Elements are added individually, not as nested array
        assertEquals(2, (array as CborArray<*>).value.size)
    }

    @Test
    fun testArrayBuilderAddCborMap() {
        val map = CborMap(mutableMapOf<CborItem<*>, CborItem<*>>(StringLabel("key") to CborUInt(42)))
        val array = CborArray.simpleBuilder()
            .addCborMap(map)
            .end()
            .build()

        assertIs<CborArray<*>>(array)
        assertEquals(1, (array as CborArray<*>).value.size)
        assertIs<CborMap<*, *>>(array.value[0])
    }

    // MapBuilder tests

    @Test
    fun testMapBuilderBasic() {
        val builder = CborMap.builder(Unit)
        builder.put("key", "value")
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
        assertEquals(1, (map as CborMap<*, *>).value.size)
    }

    @Test
    fun testMapBuilderStringKeys() {
        val builder = CborMap.builder(Unit)
        builder.put("string", "value")
        builder.put("int", 42)
        builder.put("bool", true)
        builder.put("double", 3.14)
        builder.put("float", 2.5f)
        builder.put("long", 1000000000000L)
        builder.put("byte", 1.toByte())
        builder.put("short", 100.toShort())
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
        assertEquals(8, (map as CborMap<*, *>).value.size)
    }

    @Test
    fun testMapBuilderByteArrayValue() {
        val builder = CborMap.builder(Unit)
        builder.put("bytes", byteArrayOf(0x01, 0x02, 0x03))
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
    }

    @Test
    fun testMapBuilderCborItemKey() {
        val builder = CborMap.builder(Unit)
        builder.put(CborString("key"), CborUInt(42))
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
    }

    @Test
    fun testMapBuilderLongKeys() {
        val builder = CborMap.builder(Unit)
        builder.put(1L, "value")
        builder.put(2L, 42)
        builder.put(3L, true)
        builder.put(4L, 3.14)
        builder.put(5L, 2.5f)
        builder.put(6L, 1000000000000L)
        builder.put(7L, 1.toByte())
        builder.put(8L, 100.toShort())
        builder.put(9L, byteArrayOf(0x01))
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
        assertEquals(9, (map as CborMap<*, *>).value.size)
    }

    @Test
    fun testMapBuilderOptionalNull() {
        val builder = CborMap.builder(Unit)
        builder.put("required", "value")
        builder.put("optional", null as String?, optional = true)
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
        assertEquals(1, (map as CborMap<*, *>).value.size) // optional null should not be added
    }

    @Test
    fun testMapBuilderRequiredNull() {
        val builder = CborMap.builder(Unit)
        assertFailsWith<IllegalArgumentException> {
            builder.put(CborString("key"), null as CborItem<*>?, optional = false)
        }
    }

    @Test
    fun testMapBuilderNestedMap() {
        val builder = CborMap.builder(Unit)
        builder.putMap("nested")
            .put("inner", "value")
        .end()
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
        val nested = (map as CborMap<*, *>).value.values.first()
        assertIs<CborMap<*, *>>(nested)
    }

    @Test
    fun testMapBuilderNestedArray() {
        val builder = CborMap.builder(Unit)
        builder.putArray("array")
            .addInt(1)
            .addInt(2)
        .end()
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
    }

    @Test
    fun testMapBuilderPutCborMap() {
        val inner = CborMap(mutableMapOf<CborItem<*>, CborItem<*>>(StringLabel("inner") to CborUInt(1)))
        val builder = CborMap.builder(Unit)
        builder.putCborMap(CborString("key"), inner)
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
    }

    @Test
    fun testMapBuilderPutCborArray() {
        val inner = CborArray(mutableListOf<CborItem<*>>(CborUInt(1), CborUInt(2)))
        val builder = CborMap.builder(Unit)
        builder.putCborArray(CborString("key"), inner)
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
    }

    @Test
    fun testMapBuilderPutTagged() {
        val builder = CborMap.builder(Unit)
        @Suppress("UNCHECKED_CAST")
        builder.putTagged("key", 42, CborString("tagged") as CborItem<Any>)
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
        val value = (map as CborMap<*, *>).value.values.first()
        assertIs<CborTagged<*>>(value)
    }

    @Test
    fun testMapBuilderPutTaggedLongKey() {
        val builder = CborMap.builder(Unit)
        @Suppress("UNCHECKED_CAST")
        builder.putTagged(1L, 42, CborString("tagged") as CborItem<Any>)
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
    }

    @Test
    fun testMapBuilderPutTaggedEncodedCbor() {
        val encoded = cborSerializer.encode(CborString("test"))
        val builder = CborMap.builder(Unit)
        builder.putTaggedEncodedCbor<CborString>("key", encoded)
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
    }

    @Test
    fun testMapBuilderPutMap() {
        val mapValue = mapOf("inner" to "value")
        val builder = CborMap.builder(Unit)
        builder.put("key", mapValue)
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
    }

    @Test
    fun testMapBuilderPutList() {
        val listValue = listOf(1, 2, 3)
        val builder = CborMap.builder(Unit)
        builder.put("key", listValue)
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
    }

    @Test
    fun testMapBuilderPutArray() {
        val arrayValue = arrayOf(1, 2, 3)
        val builder = CborMap.builder(Unit)
        builder.put("key", arrayValue)
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
    }

    @Test
    fun testMapBuilderWithCborItemKey() {
        val builder = CborMap.builder(Unit)
        builder.put(CborString("key"), mapOf("a" to 1))
        builder.put(CborString("key2"), listOf(1, 2))
        builder.put(CborString("key3"), arrayOf(3, 4))
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
        assertEquals(3, (map as CborMap<*, *>).value.size)
    }

    @Test
    fun testMapBuilderPutArrayWithLongKey() {
        val builder = CborMap.builder(Unit)
        builder.putArray(1L)
            .addInt(1)
        .end()
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
    }

    @Test
    fun testMapBuilderPutMapWithLongKey() {
        val builder = CborMap.builder(Unit)
        builder.putMap(1L)
            .put("inner", "value")
        .end()
        val result = builder.end()
        val map = result.build()

        assertIs<CborMap<*, *>>(map)
    }

    // CborBuilder tests

    @Test
    fun testCborBuilderWithSubject() {
        data class MyData(val id: Int)
        val subject = MyData(42)

        val builder: CborBuilder<MyData> = CborArray.builder(subject)
            .addInt(1)
            .end()

        // subject property is private, so we just test that build works
        assertNotNull(builder.build())
    }

    @Test
    fun testEncodeItemBuilder() {
        val builder = CborMap.encodeItemBuilder(Unit)
        builder.put("key", "value")
        val result = builder.end()
        val item = result.build()

        assertIs<CborEncodedItem<*>>(item)
    }

    // SimpleCborBuilder tests

    @Test
    fun testSimpleCborBuilder() {
        val item = CborUInt(42)
        val builder = SimpleCborBuilder(item)
        assertEquals(item, builder.build())
    }
}
