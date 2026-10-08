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

import com.sphereon.data.store.credential.design.model.RenderVariantKind
import com.sphereon.data.store.credential.design.model.RenderVariantRecord
import com.sphereon.data.store.credential.design.model.W3cRenderMethodReference
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.Uuid

class W3cRenderMethodDesignMapper {
    fun parseRenderMethods(renderMethodArray: JsonArray): List<RenderVariantRecord> {
        return renderMethodArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            parseRenderMethod(obj)
        }
    }

    fun parseRenderMethod(obj: JsonObject): RenderVariantRecord? {
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull
        val name = obj["name"]?.jsonPrimitive?.contentOrNull
        val description = obj["description"]?.jsonPrimitive?.contentOrNull
        val mediaType = obj["mediaType"]?.jsonPrimitive?.contentOrNull
        val digestMultibase = obj["digestMultibase"]?.jsonPrimitive?.contentOrNull

        val renderSuite =
            when {
                type.contains("SvgRenderingTemplate") || type.contains("svg-mustache") -> "svg-mustache"
                type.contains("PdfRenderingTemplate") || type.contains("pdf-mustache") -> "pdf-mustache"
                else -> null
            }

        val uri = id ?: obj["url"]?.jsonPrimitive?.contentOrNull ?: ""
        val renderProperties = obj["renderProperties"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }

        return RenderVariantRecord(
            id = Uuid.random(),
            tenantId = "", // Set by caller
            kind = RenderVariantKind.W3C_RENDER_METHOD,
            w3cRenderMethod =
                W3cRenderMethodReference(
                    type = type,
                    renderSuite = renderSuite,
                    uri = uri,
                    mediaType = mediaType,
                    name = name,
                    description = description,
                    digestMultibase = digestMultibase,
                    renderProperties = renderProperties,
                ),
        )
    }
}
