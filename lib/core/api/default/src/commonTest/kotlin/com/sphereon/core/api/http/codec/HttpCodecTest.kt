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
 */

package com.sphereon.core.api.http.codec

import com.sphereon.core.api.testutil.createCoreApiTestAppComponent
import com.sphereon.core.api.http.GenericHttpBody
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.di.Order
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HttpCodecTest {

    private fun createAppComponent() = createCoreApiTestAppComponent(
        this, "codec-test", "test-profile", "0.0.1-TEST"
    )

    // ========== JsonHttpBodyCodec Tests ==========

    @Test
    fun jsonCodecDecodeStringReturnsText() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val body = GenericHttpBody.Text("""{"key": "value"}""")
            val result = codec.decode(body, String::class)
            assertEquals("""{"key": "value"}""", result)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecDecodeJsonElementReturnsJsonElement() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val body = GenericHttpBody.Text("""{"key": "value"}""")
            val result = codec.decode(body, JsonElement::class)
            assertTrue(result is JsonObject)
            assertEquals("value", (result as JsonObject)["key"]?.let { (it as JsonPrimitive).content })
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecDecodeJsonObjectReturnsJsonObject() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val body = GenericHttpBody.Text("""{"name": "test"}""")
            val result = codec.decode(body, JsonObject::class)
            assertEquals("test", result["name"]?.let { (it as JsonPrimitive).content })
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecDecodeJsonArrayReturnsJsonArray() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val body = GenericHttpBody.Text("""[1, 2, 3]""")
            val result = codec.decode(body, JsonArray::class)
            assertEquals(3, result.size)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecDecodeJsonPrimitiveReturnsJsonPrimitive() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val body = GenericHttpBody.Text(""""hello"""")
            val result = codec.decode(body, JsonPrimitive::class)
            assertEquals("hello", result.content)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecDecodeJsonNullReturnsJsonNull() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val body = GenericHttpBody.Text("null")
            val result = codec.decode(body, JsonNull::class)
            assertTrue(result is JsonNull)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecThrowsWhenExpectingWrongType() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val body = GenericHttpBody.Text("""[1, 2, 3]""")
            assertFailsWith<CodecException> {
                codec.decode(body, JsonObject::class)
            }
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecThrowsForUnsupportedType() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val body = GenericHttpBody.Text("""{"key": "value"}""")
            assertFailsWith<CodecException> {
                codec.decode(body, Int::class)
            }
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecThrowsForEmptyBody() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val body = GenericHttpBody.Empty
            assertFailsWith<CodecException> {
                codec.decode(body, JsonElement::class)
            }
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecEncodeStringReturnsTextBody() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val result = codec.encode("hello")
            assertTrue(result is GenericHttpBody.Text)
            assertEquals("hello", (result as GenericHttpBody.Text).value)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecEncodeJsonElementReturnsTextBody() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val jsonObject = JsonObject(mapOf("key" to JsonPrimitive("value")))
            val result = codec.encode(jsonObject)
            assertTrue(result is GenericHttpBody.Text)
            assertEquals("""{"key":"value"}""", (result as GenericHttpBody.Text).value)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecEncodeThrowsForUnsupportedType() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            assertFailsWith<CodecException> {
                codec.encode(123)
            }
        } finally {
            appComponent.destroy()
        }
    }

    @Serializable
    data class TestDto(val name: String, val value: Int)

    @Test
    fun jsonCodecDecodeWithSerializerDecodesDto() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val body = GenericHttpBody.Text("""{"name":"test","value":42}""")
            val result = codec.decode(body, TestDto.serializer())
            assertEquals(TestDto("test", 42), result)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecEncodeWithSerializerEncodesDto() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val dto = TestDto("test", 42)
            val result = codec.encode(dto, TestDto.serializer())
            assertTrue(result is GenericHttpBody.Text)
            assertEquals("""{"name":"test","value":42}""", (result as GenericHttpBody.Text).value)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecEncodeToStringWithSerializerReturnsString() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val dto = TestDto("test", 42)
            val result = codec.encodeToString(dto, TestDto.serializer())
            assertEquals("""{"name":"test","value":42}""", result)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecGetOrderReturnsMedium() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            assertEquals(Order.MEDIUM.orderValue, codec.getOrder())
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecSupportedMediaTypesContainsApplicationJson() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            assertTrue(codec.supportedMediaTypes.contains(MediaType.ApplicationJson))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecSupportedMediaTypesContainsTextJson() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            assertTrue(codec.supportedMediaTypes.contains(MediaType.Custom("text/json")))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecDecodeTypedDecodesDto() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val body = GenericHttpBody.Text("""{"name":"test","value":42}""")
            val result: TestDto = codec.decodeTyped(body)
            assertEquals(TestDto("test", 42), result)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecEncodeTypedEncodesDto() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val dto = TestDto("test", 42)
            val result = codec.encodeTyped(dto)
            assertTrue(result is GenericHttpBody.Text)
            assertEquals("""{"name":"test","value":42}""", (result as GenericHttpBody.Text).value)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun jsonCodecEncodeTypedToStringEncodesDto() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val dto = TestDto("test", 42)
            val result = codec.encodeTypedToString(dto)
            assertEquals("""{"name":"test","value":42}""", result)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== DefaultHttpBodyCodecRegistry Tests ==========

    @Test
    fun codecRegistryCodecForReturnsCodecForMediaType() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val registry = DefaultHttpBodyCodecRegistry(setOf(codec))
            val foundCodec = registry.codecFor(MediaType.ApplicationJson)
            assertNotNull(foundCodec)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun codecRegistryReturnsDefaultForUnknownMediaType() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val registry = DefaultHttpBodyCodecRegistry(setOf(codec))
            val foundCodec = registry.codecFor(MediaType.TextPlain)
            // Should return the default codec
            assertNotNull(foundCodec)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun codecRegistryDecodeWorks() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val registry = DefaultHttpBodyCodecRegistry(setOf(codec))
            val body = GenericHttpBody.Text("""{"key":"value"}""")
            val result = registry.decode<JsonObject>(body, MediaType.ApplicationJson)
            assertEquals("value", result["key"]?.let { (it as JsonPrimitive).content })
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun codecRegistryEncodeWorks() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val registry = DefaultHttpBodyCodecRegistry(setOf(codec))
            val jsonObject = JsonObject(mapOf("key" to JsonPrimitive("value")))
            val result = registry.encode(jsonObject, MediaType.ApplicationJson)
            assertTrue(result is GenericHttpBody.Text)
            assertEquals("""{"key":"value"}""", (result as GenericHttpBody.Text).value)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun codecRegistryHasDefaultCodec() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val registry = DefaultHttpBodyCodecRegistry(setOf(codec))
            assertNotNull(registry.defaultCodec)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun codecRegistryHasCodecs() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val registry = DefaultHttpBodyCodecRegistry(setOf(codec))
            assertTrue(registry.codecs.isNotEmpty())
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun codecRegistryGetOrderReturnsMedium() {
        val appComponent = createAppComponent()
        try {
            val codec = JsonHttpBodyCodec()
            val registry = DefaultHttpBodyCodecRegistry(setOf(codec))
            assertEquals(Order.MEDIUM.orderValue, registry.getOrder())
        } finally {
            appComponent.destroy()
        }
    }

    // ========== GenericHttpBody Tests ==========

    @Test
    fun genericHttpBodyTextHasValue() {
        val appComponent = createAppComponent()
        try {
            val body = GenericHttpBody.Text("hello")
            assertEquals("hello", body.value)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun genericHttpBodyTextAsTextReturnsValue() {
        val appComponent = createAppComponent()
        try {
            val body = GenericHttpBody.Text("hello")
            assertEquals("hello", body.asTextOrNull())
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun genericHttpBodyEmptyIsEmpty() {
        val appComponent = createAppComponent()
        try {
            val body = GenericHttpBody.Empty
            assertTrue(body.isEmpty)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun genericHttpBodyEmptyAsTextReturnsNull() {
        val appComponent = createAppComponent()
        try {
            val body = GenericHttpBody.Empty
            assertEquals(null, body.asTextOrNull())
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun genericHttpBodyBytesCanBeCreated() {
        val appComponent = createAppComponent()
        try {
            val bytes = byteArrayOf(1, 2, 3)
            val body = GenericHttpBody.Bytes(bytes)
            assertEquals(3, body.value.size)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== CodecException Tests ==========

    @Test
    fun codecExceptionCanBeCreated() {
        val appComponent = createAppComponent()
        try {
            val exception = CodecException("Test error")
            assertEquals("Test error", exception.message)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun codecExceptionCanHaveCause() {
        val appComponent = createAppComponent()
        try {
            val cause = RuntimeException("Cause")
            val exception = CodecException("Test error", cause)
            assertEquals(cause, exception.cause)
        } finally {
            appComponent.destroy()
        }
    }
}
