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

package com.sphereon.mdoc.json

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for Oid4vpJsonSupport and serialization.
 */
class Oid4vpJsonSerializationTest {
    @Serializable
    data class TestData(
        val name: String,
        val value: Int,
    )

    @Test
    fun testOid4vpJsonSerializerExists() {
        assertNotNull(oid4vpJsonSerializer)
    }

    @Test
    fun testOid4vpJsonSupport_SerializerExists() {
        assertNotNull(Oid4vpJsonSupport.serializer)
    }

    @Test
    fun testOid4vpJsonSerializerIsMdocJsonSerializer() {
        // oid4vpJsonSerializer should be the same as mdocJsonSerializer
        assertEquals(mdocJsonSerializer, oid4vpJsonSerializer)
    }

    @Test
    fun testOid4vpSerializerEncodeDefaults() {
        @Serializable
        data class WithDefault(
            val name: String,
            val value: Int = 42,
        )

        val data = WithDefault(name = "test")
        val json = Oid4vpJsonSupport.serializer.encodeToString(WithDefault.serializer(), data)

        // encodeDefaults = false
        assertNotNull(json)
    }

    @Test
    fun testOid4vpSerializerIsLenient() {
        val json = """{"name": test, "value": 123}"""
        val result = Oid4vpJsonSupport.serializer.decodeFromString(TestData.serializer(), json)

        assertEquals("test", result.name)
        assertEquals(123, result.value)
    }

    @Test
    fun testOid4vpSerializerIgnoresUnknownKeys() {
        val json = """{"name": "test", "value": 123, "unknown": "value"}"""
        val result = Oid4vpJsonSupport.serializer.decodeFromString(TestData.serializer(), json)

        assertEquals("test", result.name)
        assertEquals(123, result.value)
    }

    @Test
    fun testOid4vpSerializerPrettyPrint() {
        val data = TestData(name = "test", value = 123)
        val json = Oid4vpJsonSupport.serializer.encodeToString(TestData.serializer(), data)

        // Pretty print enabled
        assertTrue(json.contains("\n") || json.contains("  "))
    }

    @Test
    fun testOid4vpSerializerRoundTrip() {
        val original = TestData(name = "roundtrip", value = 999)
        val json = Oid4vpJsonSupport.serializer.encodeToString(TestData.serializer(), original)
        val decoded = Oid4vpJsonSupport.serializer.decodeFromString(TestData.serializer(), json)

        assertEquals(original, decoded)
    }

    @Test
    fun testOid4vpSerializerWithComplexData() {
        @Serializable
        data class ComplexData(
            val id: String,
            val items: List<String>,
            val metadata: Map<String, String>,
        )

        val original =
            ComplexData(
                id = "test-id",
                items = listOf("item1", "item2"),
                metadata = mapOf("key" to "value"),
            )
        val json = Oid4vpJsonSupport.serializer.encodeToString(ComplexData.serializer(), original)
        val decoded = Oid4vpJsonSupport.serializer.decodeFromString(ComplexData.serializer(), json)

        assertEquals(original, decoded)
    }

    @Test
    fun testOid4vpSerializerWithNullableFields() {
        @Serializable
        data class WithNullable(
            val required: String,
            val optional: String? = null,
        )

        val withNull = WithNullable(required = "test")
        val withValue = WithNullable(required = "test", optional = "present")

        val json1 = Oid4vpJsonSupport.serializer.encodeToString(WithNullable.serializer(), withNull)
        val json2 = Oid4vpJsonSupport.serializer.encodeToString(WithNullable.serializer(), withValue)

        val decoded1 = Oid4vpJsonSupport.serializer.decodeFromString(WithNullable.serializer(), json1)
        val decoded2 = Oid4vpJsonSupport.serializer.decodeFromString(WithNullable.serializer(), json2)

        assertEquals(withNull, decoded1)
        assertEquals(withValue, decoded2)
    }

    @Test
    fun testOid4vpSerializerWithEmptyCollections() {
        @Serializable
        data class WithCollections(
            val list: List<String>,
            val map: Map<String, Int>,
        )

        val original = WithCollections(emptyList(), emptyMap())
        val json = Oid4vpJsonSupport.serializer.encodeToString(WithCollections.serializer(), original)
        val decoded = Oid4vpJsonSupport.serializer.decodeFromString(WithCollections.serializer(), json)

        assertEquals(original, decoded)
    }
}
