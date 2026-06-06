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

package com.sphereon.core.api.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Serializes a `List<String>` as either a bare JSON string (single element) or a JSON
 * array of strings (zero or multiple elements), and accepts both forms on input.
 *
 * W3C DID 1.1 permits several fields — `controller`, service `type`, JSON-LD `@context` —
 * to appear in either shape in the wire document. Persistence always stores the normalized
 * list form; this serializer handles the on-the-wire collapse/expand so the emitted document
 * byte-matches what callers and resolvers produce in practice.
 *
 * JSON-only: the serializer relies on `JsonEncoder` / `JsonDecoder` and throws on other
 * formats. Empty lists are emitted as an empty JSON array.
 */
object StringOrStringListSerializer : KSerializer<List<String>> {
    private val listSerializer = ListSerializer(String.serializer())

    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor(
            serialName = "com.sphereon.core.api.json.StringOrStringList",
            kind = SerialKind.CONTEXTUAL,
        ) {
            element("string", String.serializer().descriptor)
            element("list", listSerializer.descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: List<String>
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: throw IllegalStateException("StringOrStringListSerializer is JSON-only")
        if (value.size == 1) {
            jsonEncoder.encodeString(value[0])
        } else {
            jsonEncoder.encodeSerializableValue(listSerializer, value)
        }
    }

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw IllegalStateException("StringOrStringListSerializer is JSON-only")
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> element.map { jsonDecoder.json.decodeFromJsonElement(String.serializer(), it) }
            is JsonPrimitive -> if (element.isString) listOf(element.content) else error("Expected string or array of strings, got non-string primitive: $element")
            else -> error("Expected string or array of strings, got: $element")
        }
    }
}
