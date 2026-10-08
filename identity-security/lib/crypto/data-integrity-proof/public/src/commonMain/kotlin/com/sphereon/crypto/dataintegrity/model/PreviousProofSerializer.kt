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

package com.sphereon.crypto.dataintegrity.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * W3C VC-DI 1.0 §2.1: `previousProof` MUST be a string value or an unordered
 * list of string values.
 *
 * Reads either form and exposes both as `List<String>` to Kotlin callers.
 * On write, emits a single string when the list has exactly one element and
 * an array otherwise, matching the wire form used by the W3C reference and
 * Digital Bazaar implementations for compactness.
 */
internal object PreviousProofSerializer : KSerializer<List<String>> {
    private val listSerializer = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = listSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: List<String>
    ) {
        require(encoder is JsonEncoder) { "PreviousProofSerializer requires a JSON encoder" }
        require(value.isNotEmpty()) { "previousProof must be either a single string or a non-empty list" }
        if (value.size == 1) {
            encoder.encodeJsonElement(JsonPrimitive(value.single()))
        } else {
            encoder.encodeJsonElement(JsonArray(value.map { JsonPrimitive(it) }))
        }
    }

    override fun deserialize(decoder: Decoder): List<String> {
        require(decoder is JsonDecoder) { "PreviousProofSerializer requires a JSON decoder" }
        return when (val element = decoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                require(element.isString) { "previousProof must be a string or array of strings" }
                listOf(element.content)
            }

            is JsonArray -> {
                element.jsonArray.map { it.jsonPrimitive.content }
            }

            else -> {
                error("previousProof must be a string or array of strings, got: $element")
            }
        }
    }
}
