/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl.client

import com.sphereon.catalog.impl.SchemaMetaValidator
import com.sphereon.catalog.model.AttestationCatalog
import com.sphereon.catalog.model.AttestationCatalogList
import com.sphereon.catalog.model.CatalogDocument
import com.sphereon.catalog.model.PaginatedSchemaList
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

object PublicTs11RemoteBodies {
    val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun isJwt(
        contentType: String?,
        body: String
    ): Boolean {
        val type =
            contentType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                .orEmpty()
        if (type == "application/jwt" || type == "application/jose") return true
        return body.count { it == '.' } == 2 && !body.trimStart().startsWith('{')
    }

    fun decodePayload(
        contentType: String?,
        body: String
    ): IdkResult<JsonElement, IdkError> {
        if (isJwt(contentType, body)) {
            val payload = decodeCompactPayload(body).getOrElse { return Err(it) }
            return dataClaim(payload)
        }
        return runCatching { json.parseToJsonElement(body) }
            .fold(
                onSuccess = { Ok(it) },
                onFailure = { Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "Invalid remote JSON")) },
            )
    }

    fun decodeSchemaList(data: JsonElement): IdkResult<PaginatedSchemaList, IdkError> {
        val page = decodePaginatedObject(data)
        if (page != null) return Ok(page)
        val items = itemsArray(data)
        if (items != null) {
            val schemas =
                items.mapNotNull { element ->
                    runCatching { json.decodeFromJsonElement(SchemaMeta.serializer(), element) }.getOrNull()
                }
            return Ok(PaginatedSchemaList(total = schemas.size, limit = schemas.size, offset = 0, data = schemas))
        }
        return Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "Invalid remote schema list"))
    }

    fun decodeSchema(data: JsonElement): IdkResult<SchemaMeta, IdkError> {
        val obj =
            unwrapData(data) as? JsonObject
                ?: return Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "Invalid remote SchemaMeta"))
        return SchemaMetaValidator.validateJson(obj).fold(
            success = { Ok(it) },
            failure = { Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = it.message.defaultMessage)) },
        )
    }

    fun decodeCatalogList(data: JsonElement): IdkResult<AttestationCatalogList, IdkError> {
        val unwrapped = unwrapData(data)
        runCatching { json.decodeFromJsonElement(AttestationCatalogList.serializer(), unwrapped) }
            .onSuccess { return Ok(it) }
        val items =
            (unwrapped as? JsonArray)
                ?: (unwrapped as? JsonObject)?.get("items") as? JsonArray
                ?: (unwrapped as? JsonObject)?.get("data") as? JsonArray
        if (items != null) {
            val catalogs =
                items.mapNotNull { element ->
                    runCatching { json.decodeFromJsonElement(AttestationCatalog.serializer(), element) }.getOrNull()
                }
            return Ok(AttestationCatalogList(catalogs))
        }
        return Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "Invalid remote catalog list"))
    }

    fun document(
        mediaType: String?,
        body: String
    ): CatalogDocument =
        CatalogDocument(
            mediaType = mediaType?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() } ?: "application/json",
            bytes = body.encodeToByteArray(),
        )

    fun hostedPublicBase(
        origin: String,
        slug: String
    ): String = "${origin.trim().trimEnd('/')}/public/catalogs/${slug.trim().trim('/')}/api/v1"

    fun schemaListUrls(
        baseUrl: String,
        limit: Int,
        offset: Int
    ): List<String> {
        val base = baseUrl.trim().trimEnd('/')
        val query = "?limit=$limit&offset=$offset"
        return listOf("$base/schemas$query", "$base/schemas.json$query").distinct()
    }

    fun schemaUrls(
        baseUrl: String,
        schemaId: String
    ): List<String> {
        val base = baseUrl.trim().trimEnd('/')
        val id = schemaId.trim().trim('/')
        return listOf("$base/schemas/$id", "$base/schemas/$id.json").distinct()
    }

    private fun decodePaginatedObject(data: JsonElement): PaginatedSchemaList? {
        val obj = data as? JsonObject ?: return null
        if (obj.containsKey("total") && obj.containsKey("data")) {
            return runCatching { json.decodeFromJsonElement(PaginatedSchemaList.serializer(), obj) }.getOrNull()
        }
        val nested = obj["data"] as? JsonObject ?: return null
        if (nested.containsKey("total") && nested.containsKey("data")) {
            return runCatching { json.decodeFromJsonElement(PaginatedSchemaList.serializer(), nested) }.getOrNull()
        }
        return null
    }

    private fun itemsArray(data: JsonElement): JsonArray? {
        if (data is JsonArray) return data
        val obj = data as? JsonObject ?: return null
        return obj["data"] as? JsonArray
    }

    private fun unwrapData(data: JsonElement): JsonElement = (data as? JsonObject)?.get("data") ?: data

    private fun decodeCompactPayload(compact: String): IdkResult<JsonObject, IdkError> {
        val parts = compact.trim().split('.')
        if (parts.size < 2) {
            return Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "Remote catalog JWS is not compact"))
        }
        val payload =
            runCatching {
                val padded =
                    parts[1]
                        .replace('-', '+')
                        .replace('_', '/')
                        .let { block -> block + "=".repeat((4 - block.length % 4) % 4) }
                padded.decodeFromBase64().decodeToString()
            }.getOrElse {
                return Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "Remote catalog JWS payload is not decodable"))
            }
        return runCatching { json.parseToJsonElement(payload).jsonObject }
            .fold(
                onSuccess = { Ok(it) },
                onFailure = { Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "Remote catalog JWS payload is not JSON")) },
            )
    }

    private fun dataClaim(payload: JsonObject): IdkResult<JsonElement, IdkError> {
        val data =
            payload["data"]
                ?: return Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "Remote catalog JWS is missing data"))
        return Ok(data)
    }
}
