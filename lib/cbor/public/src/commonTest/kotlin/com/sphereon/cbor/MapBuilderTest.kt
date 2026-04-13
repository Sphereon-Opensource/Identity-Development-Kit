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

/**
 * Tests for MapBuilder fluent API including put methods, nested structures, and tagged values
 */
@Suppress("DEPRECATION")
class MapBuilderTest {

    // ========== String key put methods ==========

    @Test
    fun testMapBuilderPutStringString() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", "value")
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringStringNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", null as String?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringByteArray() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", byteArrayOf(1, 2, 3))
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringByte() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", 42.toByte())
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringByteNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", null as Byte?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringShort() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", 1000.toShort())
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringShortNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", null as Short?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringInt() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", 42)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringIntNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", null as Int?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringLong() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", 1234567890L)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringLongNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", null as Long?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringBoolean() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", true)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringBooleanNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", null as Boolean?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringDouble() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", 3.14159)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringDoubleNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", null as Double?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringFloat() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", 3.14f)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringFloatNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", null as Float?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringMap() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", mapOf("nested" to "value"))
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringMapNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", null as Map<String, String>?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringList() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", listOf(1, 2, 3))
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringListNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", null as List<Int>?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringArray() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", arrayOf(1, 2, 3))
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutStringArrayNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", null as Array<Int>?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    // ========== Long key put methods ==========

    @Test
    fun testMapBuilderPutLongCborItem() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, CborString("value"))
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongCborItemNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, null as CborItem<*>?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongString() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, "value")
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongStringNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, null as String?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongByteArray() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, byteArrayOf(1, 2, 3))
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongByteArrayNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, null as ByteArray?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongByte() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, 42.toByte())
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongByteNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, null as Byte?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongShort() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, 1000.toShort())
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongShortNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, null as Short?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongInt() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, 42)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongIntNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, null as Int?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongLong() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, 1234567890L)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongLongNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, null as Long?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongBoolean() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, true)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongBooleanNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, null as Boolean?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongDouble() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, 3.14159)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongDoubleNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, null as Double?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongFloat() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, 3.14f)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongFloatNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, null as Float?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongHasToCbor() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        val hasToCbor = object : HasToCbor<CborString> {
            override fun toCborStructure(): CborString = CborString("test")
        }
        builder.put(1L, hasToCbor)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongHasToCborNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, null as HasToCbor<*>?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    // ========== CborItem key put methods ==========

    @Test
    fun testMapBuilderPutCborItemMap() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(CborString("key"), mapOf("nested" to "value"))
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutCborItemMapNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(CborString("key"), null as Map<String, String>?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutCborItemList() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(CborString("key"), listOf(1, 2, 3))
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutCborItemListNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(CborString("key"), null as List<Int>?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutCborItemArray() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(CborString("key"), arrayOf(1, 2, 3))
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutCborItemArrayNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(CborString("key"), null as Array<Int>?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    // ========== putArray methods ==========

    @Test
    fun testMapBuilderPutArrayString() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putArray("items")
            .add(CborUInt(1))
            .add(CborUInt(2))
            .end()
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutArrayLong() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putArray(1L)
            .add(CborString("a"))
            .add(CborString("b"))
            .end()
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutArrayCborItem() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putArray(CborString("items"))
            .add(CborUInt(1))
            .end()
        assertEquals(1, cborMap.value.size)
    }

    // ========== putMap methods ==========

    @Test
    fun testMapBuilderPutMapString() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putMap("nested")
            .put("innerKey", "innerValue")
            .end()
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutMapLong() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putMap(1L)
            .put("key", "value")
            .end()
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutMapCborItem() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putMap(CborString("nested"))
            .put("key", "value")
            .end()
        assertEquals(1, cborMap.value.size)
    }

    // ========== putTagged methods ==========

    @Test
    fun testMapBuilderPutTaggedCborItem() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putTagged(CborString("tagged"), 42, CborString("taggedValue"))
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutTaggedCborItemNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putTagged(CborString("tagged"), 42, null, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutTaggedString() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putTagged("tagged", 42, CborString("taggedValue") as CborItem<Any>)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutTaggedStringNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putTagged("tagged", 42, null as CborItem<Any>?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutTaggedLong() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putTagged(1L, 42, CborString("taggedValue") as CborItem<Any>)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutTaggedLongNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putTagged(1L, 42, null as CborItem<Any>?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    // ========== putTaggedEncodedCbor methods ==========

    @Test
    fun testMapBuilderPutTaggedEncodedCborCborItem() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        val encoded = Cbor.encode(CborString("test"))
        builder.putTaggedEncodedCbor<CborString>(CborString("encoded"), encoded)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutTaggedEncodedCborCborItemNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putTaggedEncodedCbor<CborString>(CborString("encoded"), null, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutTaggedEncodedCborString() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        val encoded = Cbor.encode(CborString("test"))
        builder.putTaggedEncodedCbor<CborString>("encoded", encoded)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutTaggedEncodedCborStringNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putTaggedEncodedCbor<CborString>("encoded", null, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutTaggedEncodedCborLong() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        val encoded = Cbor.encode(CborString("test"))
        builder.putTaggedEncodedCbor<CborString>(1L, encoded)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutTaggedEncodedCborLongNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putTaggedEncodedCbor<CborString>(1L, null, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    // ========== putCborMap and putCborArray ==========

    @Test
    fun testMapBuilderPutCborMapMethod() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        val innerMap: CborMap<CborItem<*>, CborItem<*>> = CborMap(mutableMapOf(CborString("a") to CborUInt(1)))
        builder.putCborMap(CborString("nested"), innerMap)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutCborMapMethodNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putCborMap(CborString("nested"), null, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutCborArrayMethod() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        val innerArray: CborArray<CborItem<*>> = CborArray(mutableListOf(CborUInt(1), CborUInt(2)))
        builder.putCborArray(CborString("items"), innerArray)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutCborArrayMethodNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putCborArray(CborString("items"), null, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    // ========== toCborMap method ==========

    @Test
    fun testMapBuilderToCborMap() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        val result = builder.toCborMap<CborString, CborUInt>(mapOf("key" to 42))
        assertNotNull(result)
        assertEquals(1, result.value.size)
    }

    @Test
    fun testMapBuilderToCborMapEmpty() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        val result = builder.toCborMap<CborString, CborUInt>(emptyMap())
        assertEquals(0, result.value.size)
    }

    // ========== end method ==========

    @Test
    fun testMapBuilderEnd() {
        val parent = "parent"
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(parent, cborMap)
        val result = builder.end()
        assertEquals(parent, result)
    }

    // ========== Error cases ==========

    @Test
    fun testMapBuilderPutNonOptionalNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        assertFailsWith<IllegalArgumentException> {
            builder.put(CborString("key"), null as CborItem<*>?, optional = false)
        }
    }

    @Test
    fun testMapBuilderPutStringNonOptionalNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        assertFailsWith<IllegalArgumentException> {
            builder.put("key", null as String?, optional = false)
        }
    }
}
