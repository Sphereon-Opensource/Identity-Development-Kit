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

package com.sphereon.cbor.json

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class CborJsonSerializationTest {
    // Test the cborJsonSerializer
    @Test
    fun testCborJsonSerializerExists() {
        assertNotNull(cborJsonSerializer)
    }

    @Test
    fun testCborJsonSerializerEncodeAndDecode() {
        @Serializable
        data class TestData(
            val name: String,
            val value: Int,
        )

        val data = TestData("test", 42)
        val json = cborJsonSerializer.encodeToString(TestData.serializer(), data)
        val decoded = cborJsonSerializer.decodeFromString(TestData.serializer(), json)

        assertEquals(data, decoded)
    }

    // Test CborJsonSupport
    @Test
    fun testCborJsonSupportModule() {
        assertNotNull(CborJsonSupport.module)
    }

    @Test
    fun testCborJsonSupportSerializer() {
        assertNotNull(CborJsonSupport.serializer)
        assertEquals(cborJsonSerializer, CborJsonSupport.serializer)
    }

    // Test toJsonDTO function
    // Note: On JS, toJsonDTO uses native JSON.parse (returns JS object, not JsonElement).
    // On JVM/wasmJs/native, it returns a kotlinx.serialization JsonElement.
    // We use assertNotNull to keep tests platform-agnostic.
    @Test
    fun testToJsonDTO() {
        val jsonString = """{"key": "value"}"""
        val hasToJsonString =
            object : HasToJsonString {
                override fun toJsonString(): String = jsonString
            }

        val result = toJsonDTO<Any>(hasToJsonString)
        assertNotNull(result)
    }

    @Test
    fun testToJsonDTOWithPrimitive() {
        val hasToJsonString =
            object : HasToJsonString {
                override fun toJsonString(): String = "42"
            }

        val result = toJsonDTO<Any>(hasToJsonString)
        assertNotNull(result)
    }

    // Test JsonView (using a concrete implementation)
    @Test
    fun testJsonViewImplementation() {
        val jsonView =
            object : JsonView() {
                override fun toJsonString(): String = """{"test": true}"""

                override fun toCbor(): Any = byteArrayOf(0xF5.toByte()) // CBOR true
            }

        val dto = jsonView.toJsonDTO<Any>()
        assertNotNull(dto)

        val cbor = jsonView.toCbor()
        assertIs<ByteArray>(cbor)
    }

    @Test
    fun testHasToJsonStringInterface() {
        val impl =
            object : HasToJsonString {
                override fun toJsonString(): String = "test string"
            }
        assertEquals("test string", impl.toJsonString())
    }

    @Test
    fun testHasToJsonDTOInterface() {
        val impl =
            object : HasToJsonDTO {
                override fun <T> toJsonDTO(): T {
                    @Suppress("UNCHECKED_CAST")
                    return "test" as T
                }
            }
        assertEquals("test", impl.toJsonDTO<String>())
    }
}
