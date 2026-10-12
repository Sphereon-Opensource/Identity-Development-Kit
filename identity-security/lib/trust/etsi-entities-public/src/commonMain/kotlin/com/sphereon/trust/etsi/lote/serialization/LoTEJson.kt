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

package com.sphereon.trust.etsi.lote.serialization

import com.sphereon.trust.etsi.lote.model.LoTE
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * JSON serialization/deserialization for ETSI TS 119 602 LoTE.
 *
 * The domain objects use @SerialName annotations matching the 602 JSON schema directly,
 * so standard kotlinx.serialization.json handles the mapping.
 */
object LoTEJson {
    val json =
        Json {
            ignoreUnknownKeys = false
            encodeDefaults = false
            prettyPrint = false
        }

    /**
     * Parses a LoTE from a JSON string.
     */
    fun parse(jsonString: String): LoTE {
        val document = json.parseToJsonElement(jsonString)
        LoTEJsonStructure.validate(document)
        return json.decodeFromJsonElement<LoTEDocument>(document).lote
    }

    /**
     * Encodes a LoTE to a JSON string.
     */
    fun encode(lote: LoTE): String = encodeWith(json, lote)

    private fun encodeWith(format: Json, lote: LoTE): String {
        val document = format.encodeToJsonElement(LoTEDocument(lote))
        LoTEJsonStructure.validate(document)
        return format.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), document)
    }

    /**
     * Encodes a LoTE to a pretty-printed JSON string.
     */
    fun encodePretty(lote: LoTE): String {
        val prettyJson =
            Json(json) {
                prettyPrint = true
            }
        return encodeWith(prettyJson, lote)
    }
}

@Serializable
private data class LoTEDocument(@SerialName("LoTE") val lote: LoTE)
