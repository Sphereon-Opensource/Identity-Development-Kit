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
import kotlinx.serialization.json.Json

/**
 * JSON serialization/deserialization for ETSI TS 119 602 LoTE.
 *
 * The domain objects use @SerialName annotations matching the 602 JSON schema directly,
 * so standard kotlinx.serialization.json handles the mapping.
 */
object LoTEJson {
    val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            prettyPrint = false
        }

    /**
     * Parses a LoTE from a JSON string.
     */
    fun parse(jsonString: String): LoTE = json.decodeFromString(jsonString)

    /**
     * Encodes a LoTE to a JSON string.
     */
    fun encode(lote: LoTE): String = json.encodeToString(LoTE.serializer(), lote)

    /**
     * Encodes a LoTE to a pretty-printed JSON string.
     */
    fun encodePretty(lote: LoTE): String {
        val prettyJson =
            Json(json) {
                prettyPrint = true
            }
        return prettyJson.encodeToString(LoTE.serializer(), lote)
    }
}
