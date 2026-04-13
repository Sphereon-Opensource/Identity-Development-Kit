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

import com.sphereon.data.store.credential.design.model.ClaimCardinality
import com.sphereon.data.store.credential.design.model.ClaimLabel
import com.sphereon.data.store.credential.design.model.ClaimPathSegment
import com.sphereon.data.store.credential.design.model.ClaimPresentation
import com.sphereon.data.store.credential.design.model.ClaimValueKind
import com.sphereon.data.store.credential.design.model.ClaimWidgetHint
import com.sphereon.data.store.credential.design.model.DerivedRenderHintsRecord
import com.sphereon.data.store.credential.design.model.DesignClaimPath
import com.sphereon.data.store.credential.design.model.FieldRenderHint
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.Uuid

class JsonSchemaDesignMapper {
    fun deriveHints(schema: JsonObject): DerivedRenderHintsRecord {
        val hints = mutableListOf<FieldRenderHint>()
        val properties = schema["properties"]?.jsonObject ?: return emptyHints()

        for ((name, propElement) in properties) {
            val prop = propElement.jsonObject
            val path: DesignClaimPath = listOf(ClaimPathSegment.Property(name))
            val type = prop["type"]?.jsonPrimitive?.contentOrNull
            val format = prop["format"]?.jsonPrimitive?.contentOrNull
            val contentMediaType = prop["contentMediaType"]?.jsonPrimitive?.contentOrNull
            val contentEncoding = prop["contentEncoding"]?.jsonPrimitive?.contentOrNull
            val minItems = prop["minItems"]?.jsonPrimitive?.intOrNull
            val maxItems = prop["maxItems"]?.jsonPrimitive?.intOrNull

            val valueKind = deriveValueKind(type, format, contentMediaType, contentEncoding)
            val widgetHint = deriveWidgetHint(valueKind, prop)

            hints.add(
                FieldRenderHint(
                    path = path,
                    valueKind = valueKind,
                    widgetHint = widgetHint,
                    formatHint = format,
                    contentMediaType = contentMediaType,
                    contentEncoding = contentEncoding,
                    cardinality =
                        if (minItems != null || maxItems != null) {
                            ClaimCardinality(min = minItems, max = maxItems)
                        } else {
                            null
                        },
                ),
            )
        }

        return DerivedRenderHintsRecord(
            id = Uuid.random(),
            tenantId = "",
            fieldHints = hints,
            defaultOrdering = hints.map { it.path },
        )
    }

    fun deriveClaims(schema: JsonObject): List<ClaimPresentation> {
        val properties = schema["properties"]?.jsonObject ?: return emptyList()
        val required = schema["required"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet() ?: emptySet()

        return properties.entries.mapIndexed { index, (name, propElement) ->
            val prop = propElement.jsonObject
            val path: DesignClaimPath = listOf(ClaimPathSegment.Property(name))
            val title = prop["title"]?.jsonPrimitive?.contentOrNull
            val description = prop["description"]?.jsonPrimitive?.contentOrNull
            val enumValues = prop["enum"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }

            val labels =
                if (title != null) {
                    listOf(ClaimLabel(locale = "", label = title, description = description))
                } else {
                    listOf(ClaimLabel(locale = "", label = name, description = description))
                }

            ClaimPresentation(
                path = path,
                labels = labels,
                mandatory = name in required,
                order = index,
                entryCodes = enumValues,
                widgetHint =
                    if (!enumValues.isNullOrEmpty()) {
                        ClaimWidgetHint.PICKLIST
                    } else {
                        null
                    },
            )
        }
    }

    private fun deriveValueKind(
        type: String?,
        format: String?,
        contentMediaType: String?,
        contentEncoding: String?,
    ): ClaimValueKind {
        if (contentEncoding == "base64") {
            return ClaimValueKind.BINARY
        }
        if (contentMediaType?.startsWith("image/") == true) {
            return ClaimValueKind.IMAGE
        }
        if (contentMediaType == "text/markdown") {
            return ClaimValueKind.MARKDOWN
        }

        return when (type) {
            "string" -> {
                when (format) {
                    "date" -> ClaimValueKind.DATE
                    "date-time" -> ClaimValueKind.DATE_TIME
                    "uri", "iri" -> ClaimValueKind.URI
                    else -> ClaimValueKind.STRING
                }
            }

            "boolean" -> {
                ClaimValueKind.BOOLEAN
            }

            "integer" -> {
                ClaimValueKind.INTEGER
            }

            "number" -> {
                ClaimValueKind.NUMBER
            }

            "array" -> {
                ClaimValueKind.ARRAY
            }

            "object" -> {
                ClaimValueKind.OBJECT
            }

            else -> {
                ClaimValueKind.UNKNOWN
            }
        }
    }

    private fun deriveWidgetHint(
        valueKind: ClaimValueKind,
        prop: JsonObject,
    ): ClaimWidgetHint {
        val hasEnum = prop.containsKey("enum")
        if (hasEnum) {
            return ClaimWidgetHint.PICKLIST
        }

        return when (valueKind) {
            ClaimValueKind.STRING -> ClaimWidgetHint.TEXT
            ClaimValueKind.BOOLEAN -> ClaimWidgetHint.CHECKBOX
            ClaimValueKind.INTEGER, ClaimValueKind.NUMBER -> ClaimWidgetHint.TEXT
            ClaimValueKind.ARRAY -> ClaimWidgetHint.LIST
            ClaimValueKind.OBJECT -> ClaimWidgetHint.GROUP
            ClaimValueKind.DATE -> ClaimWidgetHint.DATE
            ClaimValueKind.DATE_TIME -> ClaimWidgetHint.DATE_TIME
            ClaimValueKind.URI -> ClaimWidgetHint.URI
            ClaimValueKind.IMAGE -> ClaimWidgetHint.IMAGE
            ClaimValueKind.MARKDOWN -> ClaimWidgetHint.MARKDOWN
            ClaimValueKind.BINARY -> ClaimWidgetHint.FILE
            ClaimValueKind.REFERENCE -> ClaimWidgetHint.GROUP
            ClaimValueKind.UNKNOWN -> ClaimWidgetHint.TEXT
        }
    }

    private fun emptyHints() =
        DerivedRenderHintsRecord(
            id = Uuid.random(),
            tenantId = "",
            fieldHints = emptyList(),
        )
}
