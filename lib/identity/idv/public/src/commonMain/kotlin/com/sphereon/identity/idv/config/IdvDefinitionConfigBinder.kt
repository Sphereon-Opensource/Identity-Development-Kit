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

package com.sphereon.identity.idv.config

import com.sphereon.identity.idv.model.IdvMethodDefinition
import com.sphereon.identity.idv.model.IdvUseCaseDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Container for IDV definitions loaded from a JSON resource file.
 *
 * The JSON format is:
 * ```json
 * {
 *   "methods": [ ... ],
 *   "useCases": [ ... ]
 * }
 * ```
 */
@Serializable
data class IdvDefinitions(
    val methods: List<IdvMethodDefinition> = emptyList(),
    val useCases: List<IdvUseCaseDefinition> = emptyList(),
)

/**
 * Reads IDV definitions from a JSON string and deserializes them using
 * kotlinx.serialization. All IDV sealed hierarchies use `@Serializable`
 * sealed interfaces, so polymorphic subclass registration is automatic.
 *
 * The JSON must use `_type` as the class discriminator for polymorphic types.
 *
 * Usage:
 * ```kotlin
 * val json = loadResource("idv-definitions.json")
 * val definitions = IdvDefinitionConfigBinder.bind(json)
 * ```
 */
object IdvDefinitionConfigBinder {
    val json: Json =
        Json {
            classDiscriminator = "_type"
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
        }

    /**
     * Deserializes IDV definitions from a JSON string.
     */
    fun bind(jsonString: String): IdvDefinitions = json.decodeFromString(IdvDefinitions.serializer(), jsonString)
}
