/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.credential.design.impl.mapper

import com.sphereon.data.store.credential.design.model.ClaimPathSegment
import com.sphereon.data.store.credential.design.model.DerivedRenderHintsRecord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class JsonLdContextDesignMapper {
    fun deriveContextTerms(context: JsonObject): Map<String, List<String>> {
        val contextNode = context["@context"]
        val terms = mutableMapOf<String, List<String>>()

        when (contextNode) {
            is JsonObject -> {
                extractTerms(contextNode, terms)
            }

            is JsonArray -> {
                contextNode.forEach { element ->
                    if (element is JsonObject) {
                        extractTerms(element, terms)
                    }
                }
            }

            else -> {}
        }

        return terms
    }

    fun enrichHints(
        existingHints: DerivedRenderHintsRecord,
        contextTerms: Map<String, List<String>>,
    ): DerivedRenderHintsRecord {
        val enrichedHints =
            existingHints.fieldHints.map { hint ->
                val propertyName = hint.path.firstOrNull()
                if (propertyName is ClaimPathSegment.Property) {
                    val terms = contextTerms[propertyName.name]
                    if (terms != null) {
                        hint.copy(contextTerms = hint.contextTerms + terms)
                    } else {
                        hint
                    }
                } else {
                    hint
                }
            }
        return existingHints.copy(fieldHints = enrichedHints)
    }

    private fun extractTerms(
        obj: JsonObject,
        terms: MutableMap<String, List<String>>,
    ) {
        for ((key, value) in obj) {
            if (key.startsWith("@")) {
                continue
            }
            when (value) {
                is JsonPrimitive -> {
                    val iri = value.contentOrNull
                    if (iri != null) {
                        terms[key] = listOf(iri)
                    }
                }

                is JsonObject -> {
                    val id = value["@id"]?.jsonPrimitive?.contentOrNull
                    val type = value["@type"]?.jsonPrimitive?.contentOrNull
                    val iriList = listOfNotNull(id, type)
                    if (iriList.isNotEmpty()) {
                        terms[key] = iriList
                    }
                }

                else -> {}
            }
        }
    }
}
