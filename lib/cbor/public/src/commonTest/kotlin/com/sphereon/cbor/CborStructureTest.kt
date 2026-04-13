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

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for CborStructure.kt classes
 */
class CborStructureTest {

    /**
     * Concrete implementation of CborStructure for testing
     */
    private class TestCborStructure(
        private val testValue: String,
        original: ByteArray? = null
    ) : CborStructure<CborMap<CborString, CborString>, CborMap<CborString, CborString>>(CDDL.map, original) {
        override fun cborBuilder(): CborBuilder<CborMap<CborString, CborString>> {
            val map = CborMap(mutableMapOf(CborString("key") to CborString(testValue)))
            return CborBuilder(map, map)
        }
    }

    // ========== CborStructure tests ==========

    @Test
    fun testCborStructureUserConstructed() {
        val structure = TestCborStructure("test")
        assertTrue(structure.isUserConstructed())
        assertFalse(structure.isDecoded())
        assertTrue(structure.canBeModified())
    }

    @Test
    fun testCborStructureDecoded() {
        val original = byteArrayOf(0xA1.toByte(), 0x63, 0x6B, 0x65, 0x79, 0x64, 0x74, 0x65, 0x73, 0x74) // {"key":"test"}
        val structure = TestCborStructure("test", original)
        assertFalse(structure.isUserConstructed())
        assertTrue(structure.isDecoded())
        assertFalse(structure.canBeModified())
    }

    @Test
    fun testCborStructureEncodeCborUserConstructed() {
        val structure = TestCborStructure("value")
        val encoded = structure.encodeCbor()
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun testCborStructureEncodeCborDecoded() {
        val original = byteArrayOf(0xA1.toByte(), 0x63, 0x6B, 0x65, 0x79, 0x64, 0x74, 0x65, 0x73, 0x74)
        val structure = TestCborStructure("test", original)
        val encoded = structure.encodeCbor()
        // Should return original bytes
        assertTrue(original.contentEquals(encoded))
    }

    @Test
    fun testCborStructureToCborStructure() {
        val structure = TestCborStructure("myvalue")
        val cborMap = structure.toCborStructure()
        assertNotNull(cborMap)
        assertEquals("myvalue", cborMap.value[CborString("key")]?.value)
    }

    @Test
    fun testCborStructureOriginalProperty() {
        val structure1 = TestCborStructure("test")
        assertNull(structure1.original)

        val original = byteArrayOf(1, 2, 3)
        val structure2 = TestCborStructure("test", original)
        assertNotNull(structure2.original)
        assertTrue(original.contentEquals(structure2.original!!))
    }

    // ========== CborStructureSerializer tests ==========

    @Test
    fun testCborStructureSerializerDescriptor() {
        assertNotNull(CborStructureSerializer.descriptor)
        assertEquals("com.sphereon.cbor.CborStructure", CborStructureSerializer.descriptor.serialName)
    }

    @Test
    fun testCborStructureSerializerDeserializeThrows() {
        assertFailsWith<kotlinx.serialization.SerializationException> {
            val json = """{"cddl":"map"}"""
            Json.decodeFromString(CborStructureSerializer, json)
        }
    }

    // ========== CborJsonViewStructure tests ==========

    private class TestCborJsonViewStructure(
        private val testValue: String
    ) : CborJsonViewStructure<CborMap<CborString, CborString>, String, CborMap<CborString, CborString>>(CDDL.map) {
        override fun toJson(): String = """{"key":"$testValue"}"""

        override fun cborBuilder(): CborBuilder<CborMap<CborString, CborString>> {
            val map = CborMap(mutableMapOf(CborString("key") to CborString(testValue)))
            return CborBuilder(map, map)
        }
    }

    @Test
    fun testCborJsonViewStructureToJson() {
        val structure = TestCborJsonViewStructure("test")
        val json = structure.toJson()
        assertEquals("""{"key":"test"}""", json)
    }

    @Test
    fun testCborJsonViewStructureEncodeCbor() {
        val structure = TestCborJsonViewStructure("value")
        val encoded = structure.encodeCbor()
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun testCborJsonViewStructureToCborStructure() {
        val structure = TestCborJsonViewStructure("myvalue")
        val cborMap = structure.toCborStructure()
        assertNotNull(cborMap)
        assertEquals("myvalue", cborMap.value[CborString("key")]?.value)
    }

    // ========== Edge cases ==========

    @Test
    fun testCborStructureWithEmptyOriginal() {
        val original = byteArrayOf()
        val structure = TestCborStructure("test", original)
        assertTrue(structure.isDecoded())
        val encoded = structure.encodeCbor()
        assertTrue(original.contentEquals(encoded))
    }

    // ========== CborBuilder tests ==========

    @Test
    fun testCborBuilder() {
        val item = CborString("test")
        val subject = "mySubject"
        val builder = CborBuilder(item, subject)

        assertEquals(item, builder.build())
        assertEquals(subject, builder.subject())
    }

    @Test
    fun testCborBuilderEncodedBuild() {
        val item = CborUInt(42)
        val builder = CborBuilder(item, null)

        val encoded = builder.encodedBuild()
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())

        // Verify round-trip
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertEquals(42L, (decoded as CborUInt).value)
    }

    @Test
    fun testCborBuilderWithNullSubject() {
        val item = CborString("test")
        val builder = CborBuilder(item, null)

        assertEquals(item, builder.build())
        assertNull(builder.subject())
    }

    @Test
    fun testCborBuilderWithMap() {
        val key = CborString("key")
        val map = CborMap(mutableMapOf(key to CborUInt(42)))
        val builder = CborBuilder(map, map)

        val built = builder.build()
        assertEquals(map, built)
        assertEquals(map, builder.subject())

        val encoded = builder.encodedBuild()
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())

        // Verify the map is correctly encoded and decoded
        val decoded = Cbor.decode<CborItem<*>>(encoded)
        assertIs<CborMap<*, *>>(decoded)
        assertEquals(1, (decoded as CborMap<*, *>).value.size)
    }
}
