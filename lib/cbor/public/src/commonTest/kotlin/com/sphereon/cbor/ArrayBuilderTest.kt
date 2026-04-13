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

/**
 * Tests for ArrayBuilder fluent API including add, addRequired, and nested structures
 */
@Suppress("DEPRECATION")
class ArrayBuilderTest {

    private fun buildArray(action: ArrayBuilder<SimpleCborBuilder>.() -> Unit): CborArray<*> {
        return buildCborArray(action) as CborArray<*>
    }

    // ========== ArrayBuilder.addRequired branches ==========

    @Test
    fun testAddRequiredSingleItem() {
        val array = buildArray { addRequired(CborUInt(42)) }
        assertEquals(1, array.value.size)
    }

    @Test
    fun testAddRequiredMultipleItems() {
        val array = buildArray { addRequired(CborUInt(1), CborUInt(2), CborUInt(3)) }
        assertEquals(3, array.value.size)
    }

    @Test
    fun testAddRequiredEmptyThrows() {
        assertFailsWith<IllegalArgumentException> {
            buildArray { addRequired() }
        }
    }

    // ========== ArrayBuilder.add branches ==========

    @Test
    fun testAddNullItem() {
        val array = buildArray { add(null as CborItem<*>?) }
        assertEquals(0, array.value.size)
    }

    @Test
    fun testAddMultipleWithNulls() {
        val array = buildArray { add(CborUInt(1), null, CborUInt(2)) }
        assertEquals(2, array.value.size)
    }

    @Test
    fun testAddByteArray() {
        val array = buildArray { add(byteArrayOf(1, 2, 3)) }
        assertEquals(1, array.value.size)
        assertIs<CborByteString>(array.value[0])
    }

    @Test
    fun testAddByteArrayWithNull() {
        val array = buildArray { add(null as ByteArray?) }
        assertEquals(0, array.value.size)
    }

    @Test
    fun testAddHasToCbor() {
        val hasToCbor = object : HasToCbor<CborString> {
            override fun toCborStructure(): CborString = CborString("test")
        }
        val array = buildArray { add(hasToCbor) }
        assertEquals(1, array.value.size)
        assertIs<CborString>(array.value[0])
    }

    @Test
    fun testAddHasToCborNull() {
        val array = buildArray { add(null as HasToCbor<*>?) }
        assertEquals(0, array.value.size)
    }

    // ========== ArrayBuilder.addCborArray branches ==========

    @Test
    fun testAddCborArray() {
        val inner: CborArray<CborItem<*>> = CborArray(mutableListOf(CborUInt(1), CborUInt(2)))
        val array = buildArray { addCborArray(inner) }
        assertEquals(2, array.value.size)
    }

    @Test
    fun testAddCborArrayNull() {
        val array = buildArray { addCborArray(null) }
        assertEquals(0, array.value.size)
    }

    // ========== ArrayBuilder.addCborMap ==========

    @Test
    fun testAddCborMap() {
        val map: CborMap<CborItem<*>, CborItem<*>> = CborMap(mutableMapOf(CborString("key") to CborUInt(42)))
        val array = buildArray { addCborMap(map) }
        assertEquals(1, array.value.size)
        assertIs<CborMap<*, *>>(array.value[0])
    }

    // ========== ArrayBuilder.addTagged ==========

    @Test
    fun testAddTagged() {
        val array = buildArray { addTagged(42, CborString("test")) }
        assertEquals(1, array.value.size)
        assertIs<CborTagged<*>>(array.value[0])
        assertEquals(42, (array.value[0] as CborTagged<*>).tagNumber)
    }

    // ========== ArrayBuilder.addTaggedEncodedCbor ==========

    @Test
    fun testAddTaggedEncodedCbor() {
        val inner = Cbor.encode(CborString("test"))
        val array = buildArray { addTaggedEncodedCbor<CborItem<*>>(inner) }
        assertEquals(1, array.value.size)
        val tagged = array.value[0] as CborTagged<*>
        assertEquals(CborTagged.ENCODED_CBOR, tagged.tagNumber)
    }

    // ========== ArrayBuilder nested builders ==========

    @Test
    fun testAddMap() {
        val array = buildArray {
            addMap()
                .put(CborString("key"), CborUInt(42))
                .end()
        }
        assertEquals(1, array.value.size)
        assertIs<CborMap<*, *>>(array.value[0])
    }

    @Test
    fun testAddNestedArray() {
        val array = buildArray {
            addArray()
                .add(CborUInt(1))
                .add(CborUInt(2))
                .end()
        }
        assertEquals(1, array.value.size)
        assertIs<CborArray<*>>(array.value[0])
    }

    // ========== ArrayBuilder convenience methods ==========

    @Test
    fun testAddString() {
        val array = buildArray { addString("hello") }
        assertEquals(1, array.value.size)
        assertIs<CborString>(array.value[0])
    }

    @Test
    fun testAddByte() {
        val array = buildArray { addByte(42.toByte()) }
        assertEquals(1, array.value.size)
    }

    @Test
    fun testAddShort() {
        val array = buildArray { addShort(1000.toShort()) }
        assertEquals(1, array.value.size)
    }

    @Test
    fun testAddInt() {
        val array = buildArray { addInt(42) }
        assertEquals(1, array.value.size)
    }

    @Test
    fun testAddLong() {
        val array = buildArray { addLong(42L) }
        assertEquals(1, array.value.size)
    }

    @Test
    fun testAddBoolean() {
        val array = buildArray {
            addBoolean(true)
            addBoolean(false)
        }
        assertEquals(2, array.value.size)
    }

    @Test
    fun testAddDouble() {
        val array = buildArray { addDouble(3.14) }
        assertEquals(1, array.value.size)
        assertIs<CborDouble>(array.value[0])
    }

    @Test
    fun testAddFloat() {
        val array = buildArray { addFloat(3.14f) }
        assertEquals(1, array.value.size)
        assertIs<CborFloat32>(array.value[0])
    }

    @Test
    fun testAddNull() {
        val array = buildArray { addNull() }
        assertEquals(1, array.value.size)
        assertIs<CborNull>(array.value[0])
    }

    // ========== MapBuilder branches ==========

    @Test
    fun testMapBuilderPut() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(CborString("key"), CborUInt(42))
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutOptionalWithNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(CborString("key"), null as CborItem<*>?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutWithNullValueThrows() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        assertFailsWith<IllegalArgumentException> {
            builder.put(CborString("key"), null as CborItem<*>?, optional = false)
        }
    }

    @Test
    fun testMapBuilderPutStringValue() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", "value")
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutIntValue() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", 42)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutByteArray() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", byteArrayOf(1, 2, 3))
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutByteArrayNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", null as ByteArray?, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderNestedArray() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putArray("items")
            .add(CborUInt(1))
            .add(CborUInt(2))
            .end()
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderNestedMap() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putMap("nested")
            .put("inner", "value")
            .end()
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderTaggedEncodedCbor() {
        val bytes = Cbor.encode(CborString("test"))
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putTaggedEncodedCbor<CborItem<*>>("encoded", bytes)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderTaggedEncodedCborNull() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.putTaggedEncodedCbor<CborItem<*>>("encoded", null, optional = true)
        assertEquals(0, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongKey() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, CborUInt(42))
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongKeyStringValue() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put(1L, "value")
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutByteValue() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", 42.toByte())
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutShortValue() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", 1000.toShort())
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutLongValue() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", 42L)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutBooleanValue() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", true)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutDoubleValue() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", 3.14)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutFloatValue() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", 3.14f)
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutMapValue() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", mapOf("inner" to "value"))
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutListValue() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", listOf(1L, 2L, 3L))
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutArrayValue() {
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", arrayOf(1L, 2L, 3L))
        assertEquals(1, cborMap.value.size)
    }

    @Test
    fun testMapBuilderPutHasToCbor() {
        val hasToCbor = object : HasToCbor<CborString> {
            override fun toCborStructure(): CborString = CborString("test")
        }
        val cborMap = CborMap(mutableMapOf<CborItem<*>, CborItem<*>?>())
        val builder = MapBuilder(Unit, cborMap)
        builder.put("key", hasToCbor)
        assertEquals(1, cborMap.value.size)
    }

    // ========== CborBuilder ==========

    @Test
    fun testCborBuilder() {
        val array = buildArray { addInt(42) }
        val builder = CborBuilder(array, "subject")

        assertEquals(array, builder.build())
        assertEquals("subject", builder.subject())
        assertNotNull(builder.encodedBuild())
    }

    @Test
    fun testCborBuilderWithNullSubject() {
        val item = CborUInt(42)
        val builder = CborBuilder(item, null)

        assertEquals(item, builder.build())
        assertEquals(null, builder.subject())
    }
}
