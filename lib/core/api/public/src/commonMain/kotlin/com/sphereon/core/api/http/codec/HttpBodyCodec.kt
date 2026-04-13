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

import com.sphereon.di.HasOrder
import com.sphereon.di.Order
import com.sphereon.core.api.http.GenericHttpBody
import com.sphereon.core.api.http.describe.MediaType
import kotlin.reflect.KClass

/**
 * Interface for encoding and decoding HTTP request/response bodies.
 *
 * This is the codec SPI for HTTP body serialization. The design allows:
 * - OSS to provide JSON codec as default
 * - EDK to provide Protobuf, CBOR, or other codecs
 * - Codecs are selected based on media type
 *
 * **Usage (adapter implementation):**
 * ```kotlin
 * class MyAdapter(
 *     private val codecRegistry: HttpBodyCodecRegistry
 * ) : HttpAdapter {
 *     suspend fun handleRequest(request: GenericHttpRequest): GenericHttpResponse {
 *         val requestDto = codecRegistry.decode(
 *             body = request.bodyContent,
 *             mediaType = MediaType.parse(request.contentType),
 *             targetType = MyRequest::class
 *         )
 *         // ... process
 *         return GenericHttpResponse(
 *             statusCode = 200,
 *             body = codecRegistry.encodeToString(responseDto, MediaType.ApplicationJson)
 *         )
 *     }
 * }
 * ```
 *
 * **Implementation:**
 * ```kotlin
 * @Inject
 * @SingleIn(AppScope::class)
 * @ContributesMultibinding(AppScope::class, boundType = HttpBodyCodec::class)
 * class JsonHttpBodyCodec : HttpBodyCodec {
 *     override fun getOrder(): Int = Order.MEDIUM.orderValue
 *     override val supportedMediaTypes = setOf(MediaType.ApplicationJson)
 *     // ...
 * }
 * ```
 */
interface HttpBodyCodec : HasOrder {

    /**
     * Media types this codec can handle.
     */
    val supportedMediaTypes: Set<MediaType>

    /**
     * Returns true if this codec can handle the given media type.
     */
    fun supports(mediaType: MediaType): Boolean = supportedMediaTypes.any { it.matches(mediaType) }

    /**
     * Decode a body to a typed object.
     *
     * @param T The target type
     * @param body The body content to decode
     * @param targetType The target class
     * @return The decoded object
     * @throws CodecException if decoding fails
     */
    fun <T : Any> decode(body: GenericHttpBody, targetType: KClass<T>): T

    /**
     * Encode a typed object to body content.
     *
     * @param T The source type
     * @param value The object to encode
     * @return The encoded body content
     * @throws CodecException if encoding fails
     */
    fun <T : Any> encode(value: T): GenericHttpBody

    /**
     * Encode a typed object to a String (convenience for JSON-like codecs).
     *
     * @param T The source type
     * @param value The object to encode
     * @return The encoded string
     * @throws CodecException if encoding fails
     */
    fun <T : Any> encodeToString(value: T): String = encode(value).asTextOrNull()
        ?: throw CodecException("Codec produced non-text body")
}

/**
 * Exception thrown when codec operations fail.
 */
class CodecException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Registry for looking up codecs by media type.
 *
 * This interface is the entry point for adapters to serialize/deserialize request and response bodies.
 * The registry aggregates all contributed codecs and selects the appropriate one based on media type.
 *
 * **Design:**
 * - Multiple codecs can support the same media type (e.g., OSS JSON vs EDK optimized JSON)
 * - Selection uses [HasOrder] to pick the highest-priority codec (lowest orderValue wins)
 * - Falls back to default codec (JSON) if no specific codec matches
 *
 * **Replacement via DI:**
 * This interface extends [HasOrder] so multiple implementations can be contributed,
 * and the highest-priority one (lowest [getOrder] value) wins. Use [com.sphereon.di.selectByOrder]
 * to select the winning implementation from a `Set<HttpBodyCodecRegistry>`.
 */
interface HttpBodyCodecRegistry : HasOrder {

    /**
     * All registered codecs.
     */
    val codecs: Set<HttpBodyCodec>

    /**
     * The default codec (typically JSON) used when no specific codec matches.
     */
    val defaultCodec: HttpBodyCodec

    /**
     * Find a codec that supports the given media type.
     *
     * If multiple codecs support the media type, the one with highest priority wins.
     *
     * @param mediaType The media type to find a codec for
     * @return The codec, or [defaultCodec] if no specific match
     */
    fun codecFor(mediaType: MediaType?): HttpBodyCodec

    /**
     * Decode a body using the appropriate codec for the given media type.
     *
     * @param T The target type
     * @param body The body content
     * @param mediaType The content type (used to select codec)
     * @param targetType The target class
     * @return The decoded object
     */
    fun <T : Any> decode(body: GenericHttpBody, mediaType: MediaType?, targetType: KClass<T>): T =
        codecFor(mediaType).decode(body, targetType)

    /**
     * Encode a value using the appropriate codec for the given media type.
     *
     * @param T The source type
     * @param value The object to encode
     * @param mediaType The desired output media type
     * @return The encoded body
     */
    fun <T : Any> encode(value: T, mediaType: MediaType? = null): GenericHttpBody =
        codecFor(mediaType).encode(value)

    /**
     * Encode a value to String using the appropriate codec for the given media type.
     *
     * @param T The source type
     * @param value The object to encode
     * @param mediaType The desired output media type
     * @return The encoded string
     */
    fun <T : Any> encodeToString(value: T, mediaType: MediaType? = null): String =
        codecFor(mediaType).encodeToString(value)
}

/**
 * Inline reified extension for decoding.
 */
inline fun <reified T : Any> HttpBodyCodecRegistry.decode(body: GenericHttpBody, mediaType: MediaType? = null): T =
    decode(body, mediaType, T::class)

/**
 * Inline reified extension for codec's decode.
 */
inline fun <reified T : Any> HttpBodyCodec.decode(body: GenericHttpBody): T =
    decode(body, T::class)
