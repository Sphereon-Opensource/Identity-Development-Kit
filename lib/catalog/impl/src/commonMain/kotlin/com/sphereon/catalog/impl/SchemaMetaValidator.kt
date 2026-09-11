/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

import com.sphereon.catalog.model.AttestationBindingType
import com.sphereon.catalog.model.AttestationFormatIdentifier
import com.sphereon.catalog.model.AttestationLevelOfSurety
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.model.TrustFrameworkType
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

object SchemaMetaValidator {
    private val slugRegex = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
    private val uuidRegex =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val uriSchemeRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.+")
    private val semverRegex = Regex("""^\d+\.\d+(?:\.\d+)?(?:[-+][0-9A-Za-z.-]+)?$""")
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
        }

    fun validateSlug(slug: String): IdkResult<String, IdkError> {
        if (!slugRegex.matches(slug)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(arg = slug, message = "Invalid catalog slug"))
        }
        return Ok(slug)
    }

    fun validateJson(
        element: JsonObject,
        requireId: Boolean = false
    ): IdkResult<SchemaMeta, IdkError> {
        val extra = element.keys - Ts11SchemaMetaDocument.allowedFields
        if (extra.isNotEmpty()) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "SchemaMeta additionalProperties are not allowed: ${extra.sorted().joinToString()}",
                ),
            )
        }
        element["schemaURIs"]?.let { refs ->
            val array = refs as? JsonArray ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "schemaURIs must be an array"))
            array.forEach { item ->
                val obj = item as? JsonObject ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "schemaURIs entries must be objects"))
                val refExtra = obj.keys - Ts11SchemaMetaDocument.schemaRefAllowedFields
                if (refExtra.isNotEmpty()) {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "schemaURI additionalProperties are not allowed: ${refExtra.sorted().joinToString()}",
                        ),
                    )
                }
            }
        }
        element["trustedAuthorities"]?.let { authorities ->
            val array = authorities as? JsonArray ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "trustedAuthorities must be an array"))
            array.forEach { item ->
                val obj = item as? JsonObject ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "trustedAuthorities entries must be objects"))
                val extraAuth = obj.keys - Ts11SchemaMetaDocument.trustAuthorityAllowedFields
                if (extraAuth.isNotEmpty()) {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "trustedAuthority additionalProperties are not allowed: ${extraAuth.sorted().joinToString()}",
                        ),
                    )
                }
            }
        }
        val schema =
            runCatching { json.decodeFromJsonElement(SchemaMeta.serializer(), element) }
                .getOrElse {
                    return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid SchemaMeta JSON: ${it.message}"))
                }
        return validate(schema, requireId)
    }

    fun validateEncoded(
        raw: String,
        requireId: Boolean = false
    ): IdkResult<SchemaMeta, IdkError> {
        val element =
            runCatching { json.parseToJsonElement(raw).jsonObject }
                .getOrElse { return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "SchemaMeta is not a JSON object")) }
        return validateJson(element, requireId)
    }

    fun hasValidLoS(value: String): Boolean = AttestationLevelOfSurety.fromWire(value) != null

    fun hasValidBinding(value: String): Boolean = AttestationBindingType.entries.any { it.name == value }

    fun isUri(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
        if (trimmed.startsWith("/")) return true
        return uriSchemeRegex.matches(trimmed)
    }

    fun validate(
        schema: SchemaMeta,
        requireId: Boolean = false
    ): IdkResult<SchemaMeta, IdkError> {
        if (schema.version.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "SchemaMeta.version is required"))
        }
        if (!semverRegex.matches(schema.version.trim())) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(arg = schema.version, message = "SchemaMeta.version must follow SemVer practices"))
        }
        if (schema.rulebookURI.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "SchemaMeta.rulebookURI is required"))
        }
        if (!isUri(schema.rulebookURI)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(arg = schema.rulebookURI, message = "rulebookURI must be a URI"))
        }
        if (!hasValidLoS(schema.attestationLoS)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(arg = schema.attestationLoS, message = "Invalid attestationLoS"))
        }
        if (!hasValidBinding(schema.bindingType)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(arg = schema.bindingType, message = "Invalid bindingType"))
        }
        if (schema.supportedFormats.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "supportedFormats must contain at least one format"))
        }
        schema.supportedFormats.forEach { format ->
            if (AttestationFormatIdentifier.fromWire(format) == null) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(arg = format, message = "Invalid supportedFormat"))
            }
        }
        if (schema.schemaURIs.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "schemaURIs must contain at least one entry"))
        }
        schema.schemaURIs.forEach { ref ->
            if (AttestationFormatIdentifier.fromWire(ref.formatIdentifier) == null) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(arg = ref.formatIdentifier, message = "Invalid schemaURI formatIdentifier"))
            }
            if (ref.uri.isBlank()) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "schemaURI uri is required"))
            }
            if (!isUri(ref.uri)) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(arg = ref.uri, message = "schemaURI uri must be a URI"))
            }
            if (ref.formatIdentifier !in schema.supportedFormats) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "schemaURI format ${ref.formatIdentifier} is not listed in supportedFormats",
                    ),
                )
            }
        }
        schema.trustedAuthorities.forEach { authority ->
            if (authority.value.isBlank()) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "trustedAuthority.value is required"))
            }
            if (authority.isLOTE != null && authority.frameworkType != TrustFrameworkType.etsi_tl) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "isLOTE is only valid for etsi_tl"))
            }
        }
        if (requireId) {
            val id = schema.id
            if (id.isNullOrBlank() || !uuidRegex.matches(id)) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "SchemaMeta.id must be a UUID"))
            }
        }
        return Ok(schema)
    }
}
