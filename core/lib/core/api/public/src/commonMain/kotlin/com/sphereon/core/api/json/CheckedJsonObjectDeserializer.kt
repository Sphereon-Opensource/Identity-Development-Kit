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

package com.sphereon.core.api.json

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Decodes an object from the original JSON input while rejecting repeated decoded member names.
 * Values use the ordinary JSON element decoder unless a caller selects another deserializer for a key.
 * This does not change [JsonSupport.serializer] or recursively check unrelated object-valued members.
 */
class CheckedJsonObjectDeserializer(
    private val valueDeserializer: (String) -> DeserializationStrategy<out JsonElement> = { JsonElement.serializer() },
) : DeserializationStrategy<JsonObject> {
    override val descriptor: SerialDescriptor =
        MapSerializer(String.serializer(), JsonElement.serializer()).descriptor

    override fun deserialize(decoder: Decoder): JsonObject {
        val composite = decoder.beginStructure(descriptor)
        val members = linkedMapOf<String, JsonElement>()

        while (true) {
            val keyIndex = composite.decodeElementIndex(descriptor)
            if (keyIndex == CompositeDecoder.DECODE_DONE) break
            if (keyIndex % 2 != 0) {
                throw SerializationException("Expected a JSON object member name at map index $keyIndex")
            }
            val key = composite.decodeSerializableElement(descriptor, keyIndex, String.serializer())
            if (members.containsKey(key)) {
                throw SerializationException("Duplicate JSON object member name: $key")
            }

            val valueIndex = composite.decodeElementIndex(descriptor)
            if (valueIndex != keyIndex + 1) {
                throw SerializationException("Expected a JSON object member value after map index $keyIndex")
            }
            members[key] = composite.decodeSerializableElement(descriptor, valueIndex, valueDeserializer(key))
        }

        composite.endStructure(descriptor)
        return JsonObject(members)
    }
}
