/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Maps an OIDC credential JSON Schema onto SD-JWT VC type metadata
 * (TS 11 §4.3.4 `dc+sd-jwt` SHALL be a VCT).
 */
object VctFormatMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun toSdJwtVctBytes(
        raw: String,
        fallbackVct: String,
        name: String,
    ): ByteArray {
        val parsed = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
        if (parsed != null && FormatDocumentValidator.isSdJwtVct(raw.encodeToByteArray())) {
            return raw.encodeToByteArray()
        }
        val vct = extractVct(parsed) ?: fallbackVct
        val title =
            parsed
                ?.get("title")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
                .orEmpty()
                .ifEmpty { name }
        val description = parsed?.get("description")?.jsonPrimitive?.contentOrNull
        val vctm =
            buildJsonObject {
                put("vct", vct)
                put("name", title)
                if (!description.isNullOrBlank()) put("description", description)
                if (parsed != null) put("schema", parsed)
            }
        return json.encodeToString(JsonObject.serializer(), vctm).encodeToByteArray()
    }

    fun extractVct(schema: JsonObject?): String? {
        schema
            ?.get("vct")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        val properties = schema?.get("properties")?.jsonObject ?: return null
        val vct = properties["vct"]?.jsonObject ?: return null
        vct["const"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        return vct["examples"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
