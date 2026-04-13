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

package com.sphereon.core.api.codec

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.StreamingBody
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.error.IdkError

/**
 * A streaming-capable codec for encoding/decoding binary and text content.
 *
 * This interface provides the contract for serialization and deserialization
 * of objects to/from [StreamingBody]. Implementations exist for:
 * - JSON (application/json)
 * - Protobuf (application/protobuf)
 * - CBOR (application/cbor)
 *
 * **Thread Safety:**
 * Implementations should be thread-safe and stateless where possible.
 *
 * **Usage:**
 * ```kotlin
 * val codec = jsonCodec()
 *
 * // Encode an object
 * val body = codec.encode(myObject).getOrElse { return Err(it) }
 *
 * // Decode to a specific type
 * val result = codec.decode<MyClass>(body, typeToken<MyClass>())
 * ```
 */
interface StreamingCodec {
    /**
     * The primary content type this codec handles.
     * Examples: "application/json", "application/protobuf", "application/cbor"
     */
    val contentType: String

    /**
     * Additional content types this codec can handle.
     * For example, a JSON codec might also handle "text/json".
     */
    val additionalContentTypes: Set<String>
        get() = emptySet()

    /**
     * All content types this codec can handle.
     */
    val allContentTypes: Set<String>
        get() = setOf(contentType) + additionalContentTypes

    /**
     * Encodes an object to a StreamingBody.
     *
     * @param value The object to encode
     * @return The encoded body or an error
     */
    fun <T : Any> encode(value: T): IdkResult<StreamingBody, IdkError>

    /**
     * Decodes a StreamingBody to an object of the specified type.
     *
     * @param body The body to decode
     * @param typeToken The type information for deserialization
     * @return The decoded object or an error
     */
    fun <T : Any> decode(body: StreamingBody, typeToken: TypeToken<T>): IdkResult<T, IdkError>

    /**
     * Checks if this codec can handle the given content type.
     */
    fun canHandle(contentType: String): Boolean {
        val normalized = contentType.substringBefore(';').trim().lowercase()
        return normalized in allContentTypes.map { it.lowercase() }
    }
}

/**
 * Registry for streaming codecs.
 *
 * Provides lookup capabilities for finding the appropriate codec
 * based on content type.
 */
interface StreamingCodecRegistry {
    /**
     * The default codec to use when content type is unknown.
     * Typically JSON.
     */
    val defaultCodec: StreamingCodec

    /**
     * All registered codecs.
     */
    val codecs: List<StreamingCodec>

    /**
     * Finds a codec for the given content type.
     *
     * @param contentType The content type (e.g., "application/json")
     * @return The matching codec or null if not found
     */
    fun codecFor(contentType: String): StreamingCodec?

    /**
     * Finds a codec for the given content type, falling back to default.
     */
    fun codecForOrDefault(contentType: String?): StreamingCodec =
        contentType?.let { codecFor(it) } ?: defaultCodec

    /**
     * Registers a new codec.
     */
    fun register(codec: StreamingCodec)
}

/**
 * Default implementation of StreamingCodecRegistry.
 */
class DefaultStreamingCodecRegistry(
    override val defaultCodec: StreamingCodec
) : StreamingCodecRegistry {
    private val _codecs = mutableListOf(defaultCodec)

    override val codecs: List<StreamingCodec>
        get() = _codecs.toList()

    override fun codecFor(contentType: String): StreamingCodec? {
        val normalized = contentType.substringBefore(';').trim().lowercase()
        return _codecs.find { it.canHandle(normalized) }
    }

    override fun register(codec: StreamingCodec) {
        _codecs.add(codec)
    }
}

/**
 * Common content types for binary protocols.
 */
object ContentTypes {
    const val JSON = "application/json"
    const val PROTOBUF = "application/protobuf"
    const val CBOR = "application/cbor"
    const val OCTET_STREAM = "application/octet-stream"
    const val TEXT_PLAIN = "text/plain"
    const val FORM_URLENCODED = "application/x-www-form-urlencoded"

    /**
     * Checks if the content type is a binary format.
     */
    fun isBinary(contentType: String): Boolean {
        val normalized = contentType.substringBefore(';').trim().lowercase()
        return normalized in setOf(PROTOBUF, CBOR, OCTET_STREAM)
    }

    /**
     * Checks if the content type is text-based.
     */
    fun isText(contentType: String): Boolean {
        val normalized = contentType.substringBefore(';').trim().lowercase()
        return normalized.startsWith("text/") || normalized == JSON || normalized == FORM_URLENCODED
    }
}

/**
 * Codec error helper functions.
 */
object CodecErrors {
    fun encodingError(message: String, cause: Throwable? = null): IdkError =
        IdkError.UNKNOWN_ERROR(message = "Encoding error: $message", exception = cause)

    fun decodingError(message: String, cause: Throwable? = null): IdkError =
        IdkError.UNKNOWN_ERROR(message = "Decoding error: $message", exception = cause)

    fun unsupportedType(type: String): IdkError =
        IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported type for codec: $type")

    fun unsupportedContentType(contentType: String): IdkError =
        IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported content type: $contentType")
}
