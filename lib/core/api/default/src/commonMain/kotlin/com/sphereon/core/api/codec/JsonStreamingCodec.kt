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

package com.sphereon.core.api.codec

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.StreamingBody
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.json.JsonSupport
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass

/**
 * JSON implementation of [StreamingCodec] using kotlinx.serialization.
 *
 * This codec handles serialization and deserialization of JSON content
 * to/from [StreamingBody]. It uses [TypeToken] to preserve generic type
 * information at runtime for proper deserialization.
 *
 * **Supported Content Types:**
 * - `application/json` (primary)
 * - `text/json`
 * - `application/json; charset=utf-8`
 *
 * **Usage:**
 * ```kotlin
 * val codec = JsonStreamingCodec()
 *
 * // Encode
 * val body = codec.encode(myObject).getOrElse { return Err(it) }
 *
 * // Decode with TypeToken
 * val result = codec.decode(body, typeToken<MyClass>())
 * ```
 *
 * **Thread Safety:**
 * This class is thread-safe. The underlying [Json] instance is immutable
 * and can be safely shared across threads.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<StreamingCodec>())
class JsonStreamingCodec(
    private val json: Json? = null,
    /**
     * Compile-time-resolved serializer entries aggregated via a Metro multibinding.
     * Defaults to empty so graphs that contribute nothing still construct the codec;
     * on a miss the codec falls back to the existing reflective serializer lookup.
     */
    serializerEntries: Set<CommandSerializerEntry> = emptySet(),
) : StreamingCodec {
    private val effectiveJson: Json get() = json ?: defaultJson

    /**
     * Compile-time serializer registry, keyed by the serializable runtime class.
     * Built once from the injected entries. A hit means we resolve the serializer
     * without any reflection (required for GraalVM native-image command dispatch).
     */
    private val serializerByClass: Map<KClass<*>, KSerializer<*>> =
        serializerEntries.associate { it.kClass to it.serializer }

    override val contentType: String = ContentTypes.JSON

    override val additionalContentTypes: Set<String> =
        setOf(
            "text/json",
            "application/json; charset=utf-8",
        )

    /**
     * Encodes a value to a [StreamingBody].
     *
     * Uses kotlinx.serialization to convert the value to a JSON string,
     * then wraps it in a [StreamingBody.Text].
     *
     * @param value The value to encode (must be @Serializable or a supported primitive)
     * @return Success with the encoded body, or an error if encoding fails
     */
    override fun <T : Any> encode(value: T): IdkResult<StreamingBody, IdkError> =
        try {
            val text =
                when (value) {
                    is String -> {
                        value
                    }

                    is JsonElement -> {
                        effectiveJson.encodeToString(JsonElement.serializer(), value)
                    }

                    is Unit -> {
                        "{}"
                    }

                    else -> {
                        // Resolve the serializer from the compile-time registry only (zero
                        // reflection, native-safe). A miss is a clean error — there is NO
                        // reflective fallback (`value::class.serializer()`) because kotlin-reflect
                        // is excluded from the runtime classpath for GraalVM native-image support.
                        val registered =
                            serializerByClass[value::class]
                                ?: return Err(
                                    CodecErrors.encodingError(
                                        "No registered serializer for ${value::class.simpleName} — " +
                                            "register it in the command serializer registry (CommandSerializerEntry).",
                                    ),
                                )
                        @Suppress("UNCHECKED_CAST")
                        effectiveJson.encodeToString(registered as KSerializer<T>, value)
                    }
                }
            Ok(StreamingBody.Text(text))
        } catch (expected: Exception) {
            Err(CodecErrors.encodingError("Failed to encode to JSON: ${expected.message}", expected))
        }

    /**
     * Decodes a [StreamingBody] to a typed value.
     *
     * Uses the [TypeToken] to obtain the correct [KSerializer] at runtime,
     * then deserializes the JSON content.
     *
     * @param body The body to decode
     * @param typeToken Type information for deserialization
     * @return Success with the decoded value, or an error if decoding fails
     */
    override fun <T : Any> decode(
        body: StreamingBody,
        typeToken: TypeToken<T>,
    ): IdkResult<T, IdkError> {
        // Handle empty body for Unit type
        if (body.isEmpty) {
            return if (typeToken.kType == TypeToken.UNIT.kType) {
                @Suppress("UNCHECKED_CAST")
                Ok(Unit as T)
            } else {
                Err(CodecErrors.decodingError("Cannot decode empty body to ${typeToken.simpleName}"))
            }
        }

        val text =
            body.asTextOrNull()
                ?: return Err(CodecErrors.decodingError("Cannot decode binary body as JSON"))

        // Handle empty JSON for Unit
        if (text.isBlank() || text == "{}" || text == "null") {
            if (typeToken.kType == TypeToken.UNIT.kType) {
                @Suppress("UNCHECKED_CAST")
                return Ok(Unit as T)
            }
        }

        return try {
            // Resolve the serializer from the compile-time registry only (zero reflection,
            // native-safe). A miss is a clean error — there is NO reflective fallback
            // (`serializer(typeToken.kType)`) because kotlin-reflect is excluded from the
            // runtime classpath for GraalVM native-image support.
            val kClass = typeToken.kType.classifier as? KClass<*>
            val serializer =
                kClass?.let { serializerByClass[it] }
                    ?: return Err(
                        CodecErrors.decodingError(
                            "No registered serializer for ${typeToken.simpleName} — " +
                                "register it in the command serializer registry (CommandSerializerEntry).",
                        ),
                    )

            @Suppress("UNCHECKED_CAST")
            val result = effectiveJson.decodeFromString(serializer as KSerializer<T>, text)
            Ok(result)
        } catch (expected: Exception) {
            Err(CodecErrors.decodingError("Failed to decode JSON to ${typeToken.simpleName}: ${expected.message}", expected))
        }
    }

    /**
     * Decode using an explicit KSerializer.
     *
     * This is useful when you have the serializer available at compile time.
     *
     * @param body The body to decode
     * @param serializer The serializer to use
     * @return The decoded value
     */
    fun <T> decode(
        body: StreamingBody,
        serializer: KSerializer<T>,
    ): IdkResult<T, IdkError> {
        val text =
            body.asTextOrNull()
                ?: return Err(CodecErrors.decodingError("Cannot decode empty or binary body"))

        return try {
            Ok(effectiveJson.decodeFromString(serializer, text))
        } catch (expected: Exception) {
            Err(CodecErrors.decodingError("Failed to decode JSON: ${expected.message}", expected))
        }
    }

    /**
     * Encode using an explicit KSerializer.
     *
     * @param value The value to encode
     * @param serializer The serializer to use
     * @return The encoded body
     */
    fun <T> encode(
        value: T,
        serializer: KSerializer<T>,
    ): IdkResult<StreamingBody, IdkError> =
        try {
            val text = effectiveJson.encodeToString(serializer, value)
            Ok(StreamingBody.Text(text))
        } catch (expected: Exception) {
            Err(CodecErrors.encodingError("Failed to encode to JSON: ${expected.message}", expected))
        }

    companion object {
        /**
         * Default Json configuration:
         * - ignoreUnknownKeys: true - Tolerant of extra fields (forward compatibility)
         * - isLenient: true - Accept unquoted strings, trailing commas
         * - encodeDefaults: false - Omit default values to reduce payload size
         * - prettyPrint: false - Compact output for network efficiency
         */
        val defaultJson: Json
            get() =
                Json {
                    serializersModule = JsonSupport.module
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = false
                    prettyPrint = false
                }
    }
}

/**
 * Inline reified extension for decoding with compile-time type capture.
 *
 * **Usage:**
 * ```kotlin
 * val result: MyDto = codec.decodeTyped<MyDto>(body).getOrElse { return Err(it) }
 * ```
 */
inline fun <reified T : Any> JsonStreamingCodec.decodeTyped(body: StreamingBody): IdkResult<T, IdkError> =
    decode(
        body,
        com.sphereon.core.api.binary
            .typeToken<T>(),
    )

/**
 * Inline reified extension for encoding with compile-time type capture.
 *
 * **Usage:**
 * ```kotlin
 * val body = codec.encodeTyped(myDto).getOrElse { return Err(it) }
 * ```
 */
inline fun <reified T : Any> JsonStreamingCodec.encodeTyped(value: T): IdkResult<StreamingBody, IdkError> = encode(value)
