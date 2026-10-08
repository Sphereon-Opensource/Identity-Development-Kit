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
 */

package com.sphereon.core.api.http.codec

import com.sphereon.core.api.http.GenericHttpBody
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.di.Order
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpBodyCodecTest {
    // Test codec implementation - uses MEDIUM order (default)
    private class TestJsonCodec : HttpBodyCodec {
        override fun getOrder(): Int = Order.MEDIUM.orderValue

        override val supportedMediaTypes: Set<MediaType> = setOf(MediaType.ApplicationJson)

        override fun <T : Any> decode(
            body: GenericHttpBody,
            targetType: KClass<T>,
        ): T {
            @Suppress("UNCHECKED_CAST")
            return body.asTextOrNull() as T
        }

        override fun <T : Any> encode(value: T): GenericHttpBody = GenericHttpBody.Text(value.toString())
    }

    private class TestTextCodec : HttpBodyCodec {
        override fun getOrder(): Int = Order.MEDIUM.orderValue

        override val supportedMediaTypes: Set<MediaType> = setOf(MediaType.TextPlain)

        override fun <T : Any> decode(
            body: GenericHttpBody,
            targetType: KClass<T>,
        ): T {
            @Suppress("UNCHECKED_CAST")
            return body.asTextOrNull() as T
        }

        override fun <T : Any> encode(value: T): GenericHttpBody = GenericHttpBody.Text(value.toString())
    }

    private class TestBinaryCodec : HttpBodyCodec {
        override fun getOrder(): Int = Order.MEDIUM.orderValue

        override val supportedMediaTypes: Set<MediaType> = setOf(MediaType.ApplicationOctetStream)

        override fun <T : Any> decode(
            body: GenericHttpBody,
            targetType: KClass<T>,
        ): T {
            @Suppress("UNCHECKED_CAST")
            return body.asBytesOrNull() as T
        }

        override fun <T : Any> encode(value: T): GenericHttpBody {
            @Suppress("UNCHECKED_CAST")
            return GenericHttpBody.Bytes(value as ByteArray)
        }
    }

    // Higher priority JSON codec (simulating EDK replacement)
    // Uses HIGH order (lower value = higher priority)
    private class HighPriorityJsonCodec : HttpBodyCodec {
        override fun getOrder(): Int = Order.HIGH.orderValue

        override val supportedMediaTypes: Set<MediaType> = setOf(MediaType.ApplicationJson)

        override fun <T : Any> decode(
            body: GenericHttpBody,
            targetType: KClass<T>,
        ): T {
            @Suppress("UNCHECKED_CAST")
            return "high-priority-${body.asTextOrNull()}" as T
        }

        override fun <T : Any> encode(value: T): GenericHttpBody = GenericHttpBody.Text("high-priority-$value")
    }

    @Test
    fun codecSupportsReturnsTrueForMatchingMediaType() {
        val codec = TestJsonCodec()

        assertTrue(codec.supports(MediaType.ApplicationJson))
        assertFalse(codec.supports(MediaType.TextPlain))
    }

    @Test
    fun codecSupportsMatchesWithParametersIgnored() {
        val codec = TestJsonCodec()

        assertTrue(codec.supports(MediaType.Custom("application/json; charset=utf-8")))
        assertFalse(codec.supports(MediaType.Custom("text/plain; charset=utf-8")))
    }

    @Test
    fun mediaTypeMatchesIgnoresParameters() {
        val json = MediaType.ApplicationJson
        val jsonWithCharset = MediaType.Custom("application/json; charset=utf-8")

        assertTrue(json.matches(jsonWithCharset))
        assertTrue(jsonWithCharset.matches(json))
    }

    @Test
    fun mediaTypeParseReturnsCorrectTypeForKnownMediaTypes() {
        assertEquals(MediaType.ApplicationJson, MediaType.parse("application/json"))
        assertEquals(MediaType.ApplicationJson, MediaType.parse("APPLICATION/JSON"))
        assertEquals(MediaType.ApplicationJson, MediaType.parse("application/json; charset=utf-8"))
        assertEquals(MediaType.TextPlain, MediaType.parse("text/plain"))
        assertEquals(MediaType.ApplicationOctetStream, MediaType.parse("application/octet-stream"))
    }

    @Test
    fun mediaTypeParseReturnsCustomForUnknownTypes() {
        val custom = MediaType.parse("application/protobuf")

        assertTrue(custom is MediaType.Custom)
        assertEquals("application/protobuf", custom?.value)
    }

    @Test
    fun mediaTypeParseReturnsNullForNullOrBlankInput() {
        assertEquals(null, MediaType.parse(null))
        assertEquals(null, MediaType.parse(""))
        assertEquals(null, MediaType.parse("   "))
    }

    @Test
    fun codecEncodeAndDecodeRoundtrip() {
        val codec = TestJsonCodec()
        val original = "test content"

        val encoded = codec.encode(original)
        val decoded = codec.decode<String>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun encodeToStringReturnsTextContent() {
        val codec = TestJsonCodec()
        val value = "test"

        val result = codec.encodeToString(value)

        assertEquals("test", result)
    }

    // Test registry behavior

    private class TestCodecRegistry(
        override val codecs: Set<HttpBodyCodec>,
        override val defaultCodec: HttpBodyCodec,
    ) : HttpBodyCodecRegistry {
        override fun codecFor(mediaType: MediaType?): HttpBodyCodec {
            if (mediaType == null) return defaultCodec

            // Lower orderValue = higher priority, so sort ascending
            val matching =
                codecs
                    .filter { it.supports(mediaType) }
                    .sortedBy { it.getOrder() }

            return matching.firstOrNull() ?: defaultCodec
        }
    }

    @Test
    fun registryReturnsDefaultCodecForNullMediaType() {
        val jsonCodec = TestJsonCodec()
        val registry =
            TestCodecRegistry(
                codecs = setOf(jsonCodec, TestTextCodec()),
                defaultCodec = jsonCodec,
            )

        val codec = registry.codecFor(null)

        assertEquals(jsonCodec, codec)
    }

    @Test
    fun registryReturnsMatchingCodecForKnownMediaType() {
        val jsonCodec = TestJsonCodec()
        val textCodec = TestTextCodec()
        val registry =
            TestCodecRegistry(
                codecs = setOf(jsonCodec, textCodec),
                defaultCodec = jsonCodec,
            )

        val codec = registry.codecFor(MediaType.TextPlain)

        assertEquals(textCodec, codec)
    }

    @Test
    fun registryReturnsDefaultCodecForUnknownMediaType() {
        val jsonCodec = TestJsonCodec()
        val registry =
            TestCodecRegistry(
                codecs = setOf(jsonCodec),
                defaultCodec = jsonCodec,
            )

        val codec = registry.codecFor(MediaType.Custom("application/protobuf"))

        assertEquals(jsonCodec, codec)
    }

    @Test
    fun registrySelectsHighestPriorityCodecWhenMultipleMatch() {
        val defaultJsonCodec = TestJsonCodec()
        val highPriorityJsonCodec = HighPriorityJsonCodec()
        val registry =
            TestCodecRegistry(
                codecs = setOf(defaultJsonCodec, highPriorityJsonCodec),
                defaultCodec = defaultJsonCodec,
            )

        val codec = registry.codecFor(MediaType.ApplicationJson)

        assertEquals(highPriorityJsonCodec, codec)
    }

    @Test
    fun registryDecodeUsesCorrectCodec() {
        val jsonCodec = TestJsonCodec()
        val textCodec = TestTextCodec()
        val registry =
            TestCodecRegistry(
                codecs = setOf(jsonCodec, textCodec),
                defaultCodec = jsonCodec,
            )

        val body = GenericHttpBody.Text("hello")
        val result = registry.decode<String>(body, MediaType.TextPlain)

        assertEquals("hello", result)
    }

    @Test
    fun registryEncodeUsesCorrectCodec() {
        val jsonCodec = TestJsonCodec()
        val textCodec = TestTextCodec()
        val registry =
            TestCodecRegistry(
                codecs = setOf(jsonCodec, textCodec),
                defaultCodec = jsonCodec,
            )

        val result = registry.encode("value", MediaType.ApplicationJson)

        assertTrue(result is GenericHttpBody.Text)
        assertEquals("value", result.asTextOrNull())
    }

    @Test
    fun registryEncodeToStringUsesCorrectCodec() {
        val jsonCodec = TestJsonCodec()
        val textCodec = TestTextCodec()
        val registry =
            TestCodecRegistry(
                codecs = setOf(jsonCodec, textCodec),
                defaultCodec = jsonCodec,
            )

        val result = registry.encodeToString("myValue", MediaType.ApplicationJson)

        assertEquals("myValue", result)
    }
}

class CodecExceptionTest {
    @Test
    fun codecExceptionHasMessage() {
        val exception = CodecException("Test error")
        assertEquals("Test error", exception.message)
    }

    @Test
    fun codecExceptionHasCause() {
        val cause = RuntimeException("Root cause")
        val exception = CodecException("Test error", cause)
        assertEquals("Test error", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun encodeToStringThrowsCodecExceptionForEmptyBody() {
        // Codec that returns empty body (which has asTextOrNull() -> null)
        val emptyBodyCodec =
            object : HttpBodyCodec {
                override fun getOrder(): Int = Order.MEDIUM.orderValue

                override val supportedMediaTypes: Set<MediaType> = setOf(MediaType.ApplicationOctetStream)

                override fun <T : Any> decode(
                    body: GenericHttpBody,
                    targetType: KClass<T>,
                ): T {
                    @Suppress("UNCHECKED_CAST")
                    return body.asBytesOrNull() as T
                }

                override fun <T : Any> encode(value: T): GenericHttpBody {
                    // Return Empty body which returns null from asTextOrNull()
                    return GenericHttpBody.Empty
                }
            }

        val exception =
            kotlin
                .runCatching {
                    emptyBodyCodec.encodeToString("test")
                }.exceptionOrNull()

        assertTrue(exception is CodecException)
        assertEquals("Codec produced non-text body", exception?.message)
    }
}

class HttpBodyCodecRegistryDefaultMethodsTest {
    // Codec implementation for testing
    private class SimpleCodec(
        override val supportedMediaTypes: Set<MediaType>,
    ) : HttpBodyCodec {
        override fun getOrder(): Int = Order.MEDIUM.orderValue

        override fun <T : Any> decode(
            body: GenericHttpBody,
            targetType: KClass<T>,
        ): T {
            @Suppress("UNCHECKED_CAST")
            return body.asTextOrNull() as T
        }

        override fun <T : Any> encode(value: T): GenericHttpBody = GenericHttpBody.Text(value.toString())
    }

    // Registry that uses default interface implementations
    private class DefaultMethodRegistry(
        override val codecs: Set<HttpBodyCodec>,
        override val defaultCodec: HttpBodyCodec,
    ) : HttpBodyCodecRegistry {
        override fun getOrder(): Int = Order.MEDIUM.orderValue

        override fun codecFor(mediaType: MediaType?): HttpBodyCodec {
            if (mediaType == null) return defaultCodec
            return codecs.firstOrNull { it.supports(mediaType) } ?: defaultCodec
        }

        // Uses default implementations for decode, encode, encodeToString
    }

    @Test
    fun defaultDecodeMethodDelegatesToCodecFor() {
        val jsonCodec = SimpleCodec(setOf(MediaType.ApplicationJson))
        val registry = DefaultMethodRegistry(setOf(jsonCodec), jsonCodec)

        val body = GenericHttpBody.Text("hello")
        val result: String = registry.decode(body, MediaType.ApplicationJson, String::class)

        assertEquals("hello", result)
    }

    @Test
    fun defaultEncodeMethodDelegatesToCodecFor() {
        val jsonCodec = SimpleCodec(setOf(MediaType.ApplicationJson))
        val registry = DefaultMethodRegistry(setOf(jsonCodec), jsonCodec)

        val result = registry.encode("test", MediaType.ApplicationJson)

        assertTrue(result is GenericHttpBody.Text)
        assertEquals("test", result.asTextOrNull())
    }

    @Test
    fun defaultEncodeToStringMethodDelegatesToCodecFor() {
        val jsonCodec = SimpleCodec(setOf(MediaType.ApplicationJson))
        val registry = DefaultMethodRegistry(setOf(jsonCodec), jsonCodec)

        val result = registry.encodeToString("test", MediaType.ApplicationJson)

        assertEquals("test", result)
    }

    @Test
    fun defaultEncodeMethodWithNullMediaType() {
        val jsonCodec = SimpleCodec(setOf(MediaType.ApplicationJson))
        val registry = DefaultMethodRegistry(setOf(jsonCodec), jsonCodec)

        val result = registry.encode("test")

        assertTrue(result is GenericHttpBody.Text)
        assertEquals("test", result.asTextOrNull())
    }

    @Test
    fun defaultEncodeToStringMethodWithNullMediaType() {
        val jsonCodec = SimpleCodec(setOf(MediaType.ApplicationJson))
        val registry = DefaultMethodRegistry(setOf(jsonCodec), jsonCodec)

        val result = registry.encodeToString("test", null)

        assertEquals("test", result)
    }

    @Test
    fun defaultEncodeMethodWithDefaultParameter() {
        // This test uses the default parameter path (not passing mediaType at all)
        val jsonCodec = SimpleCodec(setOf(MediaType.ApplicationJson))
        val registry = DefaultMethodRegistry(setOf(jsonCodec), jsonCodec)

        // Call without mediaType to exercise default parameter synthetic method
        val result = registry.encode("default-test")

        assertTrue(result is GenericHttpBody.Text)
        assertEquals("default-test", result.asTextOrNull())
    }

    @Test
    fun defaultEncodeToStringMethodWithDefaultParameter() {
        // This test uses the default parameter path (not passing mediaType at all)
        val jsonCodec = SimpleCodec(setOf(MediaType.ApplicationJson))
        val registry = DefaultMethodRegistry(setOf(jsonCodec), jsonCodec)

        // Call without mediaType to exercise default parameter synthetic method
        val result = registry.encodeToString("default-test")

        assertEquals("default-test", result)
    }
}

class HttpBodyCodecExtensionsTest {
    private class SimpleCodec : HttpBodyCodec {
        override fun getOrder(): Int = Order.MEDIUM.orderValue

        override val supportedMediaTypes: Set<MediaType> = setOf(MediaType.ApplicationJson)

        override fun <T : Any> decode(
            body: GenericHttpBody,
            targetType: KClass<T>,
        ): T {
            @Suppress("UNCHECKED_CAST")
            return body.asTextOrNull() as T
        }

        override fun <T : Any> encode(value: T): GenericHttpBody = GenericHttpBody.Text(value.toString())
    }

    private class SimpleRegistry(
        override val codecs: Set<HttpBodyCodec>,
        override val defaultCodec: HttpBodyCodec,
    ) : HttpBodyCodecRegistry {
        override fun getOrder(): Int = Order.MEDIUM.orderValue

        override fun codecFor(mediaType: MediaType?): HttpBodyCodec = defaultCodec
    }

    @Test
    fun reifiedDecodeExtensionOnCodecWorks() {
        val codec = SimpleCodec()
        val body = GenericHttpBody.Text("hello world")

        // Use the reified inline extension
        val result: String = codec.decode(body)

        assertEquals("hello world", result)
    }

    @Test
    fun reifiedDecodeExtensionOnRegistryWorks() {
        val codec = SimpleCodec()
        val registry = SimpleRegistry(setOf(codec), codec)
        val body = GenericHttpBody.Text("registry decode")

        // Use the reified inline extension
        val result: String = registry.decode(body, MediaType.ApplicationJson)

        assertEquals("registry decode", result)
    }

    @Test
    fun reifiedDecodeExtensionOnRegistryWithNullMediaType() {
        val codec = SimpleCodec()
        val registry = SimpleRegistry(setOf(codec), codec)
        val body = GenericHttpBody.Text("null media type")

        // Use the reified inline extension with null mediaType
        val result: String = registry.decode(body, null)

        assertEquals("null media type", result)
    }

    @Test
    fun reifiedDecodeExtensionOnRegistryWithDefaultMediaType() {
        val codec = SimpleCodec()
        val registry = SimpleRegistry(setOf(codec), codec)
        val body = GenericHttpBody.Text("default media type")

        // Use the reified inline extension with default mediaType (which defaults to null)
        val result: String = registry.decode(body)

        assertEquals("default media type", result)
    }
}
