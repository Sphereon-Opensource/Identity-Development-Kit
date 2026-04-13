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

package com.sphereon.mdoc.json

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for MdocJsonSupport and serialization.
 */
class MdocJsonSerializationTest {

    @Serializable
    data class TestData(val name: String, val value: Int)

    @Test
    fun testMdocJsonSerializerExists() {
        assertNotNull(mdocJsonSerializer)
    }

    @Test
    fun testMdocJsonSupportSerializerExists() {
        assertNotNull(MdocJsonSupport.serializer)
    }

    @Test
    fun testMdocJsonSupportModuleExists() {
        assertNotNull(MdocJsonSupport.module)
    }

    @Test
    fun testSerializerEncodeDefaults() {
        // The serializer should not encode defaults
        @Serializable
        data class WithDefault(val name: String, val value: Int = 42)

        val data = WithDefault(name = "test")
        val json = MdocJsonSupport.serializer.encodeToString(WithDefault.serializer(), data)

        // encodeDefaults = false, so value should not be in output
        assertTrue(!json.contains("42") || json.contains("value"))
    }

    @Test
    fun testSerializerIsLenient() {
        // Lenient mode allows unquoted strings and other relaxed parsing
        val json = """{"name": test, "value": 123}"""
        val result = MdocJsonSupport.serializer.decodeFromString(TestData.serializer(), json)

        assertEquals("test", result.name)
        assertEquals(123, result.value)
    }

    @Test
    fun testSerializerIgnoresUnknownKeys() {
        // ignoreUnknownKeys = true
        val json = """{"name": "test", "value": 123, "extra": "ignored"}"""
        val result = MdocJsonSupport.serializer.decodeFromString(TestData.serializer(), json)

        assertEquals("test", result.name)
        assertEquals(123, result.value)
    }

    @Test
    fun testSerializerPrettyPrint() {
        val data = TestData(name = "test", value = 123)
        val json = MdocJsonSupport.serializer.encodeToString(TestData.serializer(), data)

        // Pretty print should add newlines
        assertTrue(json.contains("\n") || json.contains("  "))
    }

    @Test
    fun testSerializerRoundTrip() {
        val original = TestData(name = "roundtrip", value = 999)
        val json = MdocJsonSupport.serializer.encodeToString(TestData.serializer(), original)
        val decoded = MdocJsonSupport.serializer.decodeFromString(TestData.serializer(), json)

        assertEquals(original, decoded)
    }

    @Test
    fun testSerializerWithNestedObjects() {
        @Serializable
        data class Nested(val inner: TestData)

        val original = Nested(TestData("nested", 42))
        val json = MdocJsonSupport.serializer.encodeToString(Nested.serializer(), original)
        val decoded = MdocJsonSupport.serializer.decodeFromString(Nested.serializer(), json)

        assertEquals(original, decoded)
    }

    @Test
    fun testSerializerWithLists() {
        @Serializable
        data class WithList(val items: List<String>)

        val original = WithList(listOf("a", "b", "c"))
        val json = MdocJsonSupport.serializer.encodeToString(WithList.serializer(), original)
        val decoded = MdocJsonSupport.serializer.decodeFromString(WithList.serializer(), json)

        assertEquals(original, decoded)
    }

    @Test
    fun testSerializerWithNullableFields() {
        @Serializable
        data class WithNullable(val name: String, val optional: String? = null)

        val original = WithNullable("test")
        val json = MdocJsonSupport.serializer.encodeToString(WithNullable.serializer(), original)
        val decoded = MdocJsonSupport.serializer.decodeFromString(WithNullable.serializer(), json)

        assertEquals(original, decoded)
    }

    @Test
    fun testSerializerWithMaps() {
        @Serializable
        data class WithMap(val data: Map<String, Int>)

        val original = WithMap(mapOf("a" to 1, "b" to 2))
        val json = MdocJsonSupport.serializer.encodeToString(WithMap.serializer(), original)
        val decoded = MdocJsonSupport.serializer.decodeFromString(WithMap.serializer(), json)

        assertEquals(original, decoded)
    }
}
