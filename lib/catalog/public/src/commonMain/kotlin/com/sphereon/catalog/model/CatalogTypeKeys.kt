/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Type keys used by [CatalogTypeView] and instance-console GET-default joins.
 * Always prefer format-document `vct` / `docType` over reminted hosted `schemaURIs`.
 */
object CatalogTypeKeys {
    private val json = Json { ignoreUnknownKeys = true }

    fun of(record: AttestationSchemaRecord): AttestationTypeKey {
        val vct = formatBytes(record, "dc+sd-jwt")?.let(::vctValue)
        if (!vct.isNullOrBlank()) return AttestationTypeKey(AttestationTypeKeyKind.VCT, vct)
        val docType = formatBytes(record, "mso_mdoc")?.let(::docTypeValue)
        if (!docType.isNullOrBlank()) return AttestationTypeKey(AttestationTypeKeyKind.DOCTYPE, docType)
        val uri =
            record.schema.schemaURIs
                .firstOrNull()
                ?.uri ?: record.schema.id.orEmpty()
        return AttestationTypeKey(AttestationTypeKeyKind.SCHEMA_URI, uri)
    }

    fun matches(
        record: AttestationSchemaRecord,
        boundTypeKeys: Set<String>,
        boundDesignIds: Set<String> = emptySet(),
    ): Boolean {
        if (record.linkedDesignId?.let(boundDesignIds::contains) == true) return true
        val key = of(record).value.trim()
        return key.isNotEmpty() && key in boundTypeKeys
    }

    fun vctValue(bytes: ByteArray): String? =
        parseObject(bytes)
            ?.get("vct")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun docTypeValue(bytes: ByteArray): String? {
        val obj = parseObject(bytes) ?: return null
        obj["docType"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        return obj["properties"]
            ?.jsonObject
            ?.get("docType")
            ?.jsonObject
            ?.get("const")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun formatBytes(
        record: AttestationSchemaRecord,
        formatIdentifier: String,
    ): ByteArray? =
        record.documents
            .firstOrNull { it.kind == CatalogDocumentKind.FORMAT && it.formatIdentifier == formatIdentifier }
            ?.bytes

    private fun parseObject(bytes: ByteArray): JsonObject? = runCatching { json.parseToJsonElement(bytes.decodeToString()).jsonObject }.getOrNull()
}
