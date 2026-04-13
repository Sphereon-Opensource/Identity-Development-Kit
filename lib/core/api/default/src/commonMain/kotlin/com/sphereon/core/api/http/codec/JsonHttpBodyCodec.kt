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

import com.sphereon.di.Order
import com.sphereon.core.api.http.GenericHttpBody
import com.sphereon.core.api.http.describe.MediaType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet
import kotlin.reflect.KClass

/**
 * Default JSON codec for HTTP body serialization using kotlinx.serialization.
 *
 * This codec supports:
 * - **JsonElement** (and subtypes): The universal JSON type for generic processing
 * - **String**: Passthrough for raw JSON strings
 *
 * **Usage in adapters:**
 * ```kotlin
 * // For generic JSON handling, use JsonElement:
 * val jsonElement = codecRegistry.decode<JsonElement>(body, MediaType.ApplicationJson)
 *
 * // For type-safe @Serializable types, use the serializer-based extensions:
 * val dto = codecRegistry.decode(body, MediaType.ApplicationJson, MyDto.serializer())
 *
 * // Or use your own Json instance directly (most common in adapters):
 * val dto = json.decodeFromString<MyDto>(request.body!!)
 * ```
 *
 * **Why KClass-based decode is limited:**
 * kotlinx.serialization requires compile-time serializers for @Serializable types.
 * Without reflection (which isn't available on JS/Native), we cannot obtain a
 * KSerializer from a KClass at runtime. Use the serializer-based methods instead.
 *
 * **Replacement:**
 * EDK can provide a higher-priority codec with additional capabilities.
 *
 * @property json The Json instance used for serialization. Can be customized via DI.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpBodyCodec>())
class JsonHttpBodyCodec(
    val json: Json = defaultJson
) : HttpBodyCodec {

    override fun getOrder(): Int = Order.MEDIUM.orderValue

    override val supportedMediaTypes: Set<MediaType> = setOf(
        MediaType.ApplicationJson,
        MediaType.Custom("application/json; charset=utf-8"),
        MediaType.Custom("text/json")
    )

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> decode(body: GenericHttpBody, targetType: KClass<T>): T {
        val text = body.asTextOrNull()
            ?: throw CodecException("Cannot decode empty body to ${targetType.simpleName}")

        return when (targetType) {
            // String passthrough
            String::class -> text as T

            // JsonElement and subtypes - these have known serializers
            JsonElement::class -> json.parseToJsonElement(text) as T
            JsonObject::class -> json.parseToJsonElement(text).let {
                if (it is JsonObject) it as T
                else throw CodecException("Expected JsonObject but got ${it::class.simpleName}")
            }
            JsonArray::class -> json.parseToJsonElement(text).let {
                if (it is JsonArray) it as T
                else throw CodecException("Expected JsonArray but got ${it::class.simpleName}")
            }
            JsonPrimitive::class -> json.parseToJsonElement(text).let {
                if (it is JsonPrimitive) it as T
                else throw CodecException("Expected JsonPrimitive but got ${it::class.simpleName}")
            }
            JsonNull::class -> json.parseToJsonElement(text).let {
                if (it is JsonNull) it as T
                else throw CodecException("Expected JsonNull but got ${it::class.simpleName}")
            }

            else -> throw CodecException(
                "Cannot decode to ${targetType.simpleName} using KClass. " +
                        "For @Serializable types, use decode(body, serializer) or decode<T>(body) with a reified type, " +
                        "or use JsonElement for generic JSON handling."
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> encode(value: T): GenericHttpBody {
        val text = when (value) {
            is String -> value
            is JsonElement -> json.encodeToString(JsonElement.serializer(), value)
            else -> throw CodecException(
                "Cannot encode ${value::class.simpleName} using KClass. " +
                        "For @Serializable types, use encode(value, serializer), " +
                        "or convert to JsonElement first."
            )
        }
        return GenericHttpBody.Text(text)
    }

    /**
     * Decode using an explicit KSerializer.
     *
     * This is the recommended way to decode @Serializable types without reflection.
     *
     * **Usage:**
     * ```kotlin
     * val myDto = codec.decode(body, MyDto.serializer())
     * ```
     */
    fun <T> decode(body: GenericHttpBody, serializer: KSerializer<T>): T {
        val text = body.asTextOrNull()
            ?: throw CodecException("Cannot decode empty body")
        return json.decodeFromString(serializer, text)
    }

    /**
     * Encode using an explicit KSerializer.
     *
     * This is the recommended way to encode @Serializable types without reflection.
     *
     * **Usage:**
     * ```kotlin
     * val body = codec.encode(myDto, MyDto.serializer())
     * ```
     */
    fun <T> encode(value: T, serializer: KSerializer<T>): GenericHttpBody {
        val text = json.encodeToString(serializer, value)
        return GenericHttpBody.Text(text)
    }

    /**
     * Encode to String using an explicit KSerializer.
     */
    fun <T> encodeToString(value: T, serializer: KSerializer<T>): String {
        return json.encodeToString(serializer, value)
    }

    companion object {
        /**
         * Default Json configuration:
         * - ignoreUnknownKeys: true - Tolerant of extra fields
         * - prettyPrint: false - Compact output for network efficiency
         * - encodeDefaults: false - Omit default values to reduce payload size
         */
        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            prettyPrint = false
            encodeDefaults = false
        }
    }
}

/**
 * Inline reified extension for decoding @Serializable types.
 *
 * This uses the compiler-generated serializer for the type.
 *
 * **Usage:**
 * ```kotlin
 * val myDto: MyDto = codec.decodeTyped<MyDto>(body)
 * ```
 */
inline fun <reified T> JsonHttpBodyCodec.decodeTyped(body: GenericHttpBody): T {
    val text = body.asTextOrNull()
        ?: throw CodecException("Cannot decode empty body to ${T::class.simpleName}")
    return json.decodeFromString<T>(text)
}

/**
 * Inline reified extension for encoding @Serializable types.
 *
 * **Usage:**
 * ```kotlin
 * val body = codec.encodeTyped(myDto)
 * ```
 */
inline fun <reified T> JsonHttpBodyCodec.encodeTyped(value: T): GenericHttpBody {
    val text = json.encodeToString<T>(value)
    return GenericHttpBody.Text(text)
}

/**
 * Inline reified extension for encoding @Serializable types to String.
 */
inline fun <reified T> JsonHttpBodyCodec.encodeTypedToString(value: T): String {
    return json.encodeToString<T>(value)
}
