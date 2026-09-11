/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

import com.sphereon.catalog.model.AttestationSchemaDocument
import com.sphereon.catalog.model.CatalogDocumentKind
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * TS 11 §4.3.4 format-document checks.
 *
 * `dc+sd-jwt` must be VCT type metadata (`vct` string). A bare OIDC credential
 * payload JSON Schema is not sufficient unless converted first.
 * `mso_mdoc` must identify a document type (`docType` or `properties.docType.const`).
 */
object FormatDocumentValidator {
    private val json = Json { ignoreUnknownKeys = true }

    fun isSdJwtVct(bytes: ByteArray): Boolean = vctValue(bytes) != null

    fun vctValue(bytes: ByteArray): String? {
        val obj = parseObject(bytes) ?: return null
        return obj["vct"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

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

    fun isMdocDocumentType(bytes: ByteArray): Boolean = docTypeValue(bytes) != null

    fun isJsonObject(bytes: ByteArray): Boolean = parseObject(bytes) != null

    fun validate(
        formatIdentifier: String,
        bytes: ByteArray,
    ): IdkResult<Unit, IdkError> =
        when (formatIdentifier) {
            "dc+sd-jwt" -> {
                if (isSdJwtVct(bytes)) {
                    Ok(Unit)
                } else {
                    Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "dc+sd-jwt format document must be SD-JWT VC type metadata with a vct string",
                        ),
                    )
                }
            }

            "mso_mdoc" -> {
                if (isMdocDocumentType(bytes)) {
                    Ok(Unit)
                } else {
                    Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "mso_mdoc format document must declare a docType",
                        ),
                    )
                }
            }

            "jwt_vc_json", "jwt_vc_json-ld", "ldp_vc" -> {
                if (isJsonObject(bytes)) {
                    Ok(Unit)
                } else {
                    Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "$formatIdentifier format document must be a JSON object",
                        ),
                    )
                }
            }

            else -> {
                Ok(Unit)
            }
        }

    fun validateListedFormats(
        supportedFormats: List<String>,
        documents: List<AttestationSchemaDocument>,
    ): IdkResult<Unit, IdkError> {
        supportedFormats.forEach { format ->
            val doc =
                documents.firstOrNull { it.kind == CatalogDocumentKind.FORMAT && it.formatIdentifier == format }
                    ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "A FORMAT document is required for supported format $format"))
            validate(format, doc.bytes).getOrElse { return Err(it) }
        }
        return Ok(Unit)
    }

    private fun parseObject(bytes: ByteArray): JsonObject? = runCatching { json.parseToJsonElement(bytes.decodeToString()).jsonObject }.getOrNull()
}
