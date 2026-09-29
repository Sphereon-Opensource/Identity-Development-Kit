/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl.command

import com.sphereon.catalog.impl.FormatDocumentValidator
import com.sphereon.catalog.model.AttestationCatalog
import com.sphereon.catalog.model.AttestationCatalogStatus
import com.sphereon.catalog.model.AttestationSchemaDocument
import com.sphereon.catalog.model.AttestationSchemaRecord
import com.sphereon.catalog.model.CatalogDocumentKind
import com.sphereon.catalog.model.CatalogListingWindow
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.model.SchemaUriRef
import com.sphereon.catalog.store.AttestationCatalogStore
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal suspend fun AttestationCatalogStore.requireCatalog(
    tenantId: String,
    catalogId: String?,
    slug: String?,
    publishedOnly: Boolean,
): IdkResult<AttestationCatalog, IdkError> {
    val found =
        when {
            !catalogId.isNullOrBlank() -> findCatalogById(tenantId, catalogId).getOrElse { return Err(it) }
            !slug.isNullOrBlank() -> findCatalogBySlug(tenantId, slug).getOrElse { return Err(it) }
            else -> return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "catalogId or slug is required"))
        } ?: return Err(IdkError.NOT_FOUND_ERROR(resource = catalogId ?: slug))
    if (publishedOnly && found.status != AttestationCatalogStatus.PUBLISHED) {
        return Err(IdkError.NOT_FOUND_ERROR(resource = found.slug))
    }
    return Ok(found)
}

internal suspend fun AttestationCatalogStore.requireSchema(
    tenantId: String,
    catalog: AttestationCatalog,
    schemaId: String,
): IdkResult<AttestationSchemaRecord, IdkError> {
    val record =
        findSchema(tenantId, catalog.id, schemaId).getOrElse { return Err(it) }
            ?: return Err(IdkError.NOT_FOUND_ERROR(resource = schemaId))
    return Ok(record)
}

internal fun illegalState(message: String): IdkError =
    IdkError(
        code = "ILLEGAL_STATE_ERROR",
        message =
            IdkError.Message(
                i18nKey = "com.sphereon.core.error.illegal-state-error",
                defaultMessage = message,
            ),
        category = ErrorCategory.CONFLICT,
    )

internal fun requireListedDocuments(
    schema: SchemaMeta,
    documents: List<AttestationSchemaDocument>,
    listing: CatalogListingWindow,
): IdkResult<Unit, IdkError> {
    if (listing.isEmpty()) return Ok(Unit)
    if (documents.none { it.kind == CatalogDocumentKind.RULEBOOK }) {
        return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "A RULEBOOK document is required for listed schemas"))
    }
    return FormatDocumentValidator.validateListedFormats(schema.supportedFormats, documents)
}

/**
 * Adds a minimal FORMAT document for every SD-JWT VC or mdoc type named in [schemaURIs] that has
 * none yet, so the type identity (`vct` or `docType`) is persisted with the record. Publishing
 * rewrites a non-URL schema URI to the hosted format URL; the format document keeps the identity.
 */
internal fun withTypeIdentityFormats(
    schemaURIs: List<SchemaUriRef>,
    documents: List<AttestationSchemaDocument>,
): List<AttestationSchemaDocument> {
    val added =
        schemaURIs.mapNotNull { ref ->
            val value = ref.uri.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val field =
                when (ref.formatIdentifier) {
                    "dc+sd-jwt" -> "vct"
                    "mso_mdoc" -> "docType"
                    else -> return@mapNotNull null
                }
            if (documents.any { it.kind == CatalogDocumentKind.FORMAT && it.formatIdentifier == ref.formatIdentifier }) {
                return@mapNotNull null
            }
            AttestationSchemaDocument(
                kind = CatalogDocumentKind.FORMAT,
                formatIdentifier = ref.formatIdentifier,
                mediaType = "application/json",
                bytes = JsonObject(mapOf(field to JsonPrimitive(value))).toString().encodeToByteArray(),
            )
        }.distinctBy { it.formatIdentifier }
    return documents + added
}

/**
 * SD-JWT VC schema URIs as the absolute URIs a vct must be. A relative hosted path such as
 * `/public/schema/vct/{id}` is resolved against [baseUrl] (the tenant public base URL). Other
 * formats and absolute values are kept; without a base URL nothing changes.
 */
internal fun absoluteVctSchemaUris(
    schemaURIs: List<SchemaUriRef>,
    baseUrl: String,
): List<SchemaUriRef> {
    val base = baseUrl.trim().trimEnd('/')
    if (base.isEmpty()) return schemaURIs
    return schemaURIs.map { ref ->
        val uri = ref.uri.trim()
        if (ref.formatIdentifier == "dc+sd-jwt" && uri.startsWith("/") && !uri.startsWith("//")) {
            SchemaUriRef(ref.formatIdentifier, "$base$uri")
        } else {
            ref
        }
    }
}

/** [supplied] documents replace the stored document of the same kind and format; the rest are kept. */
internal fun mergeDocuments(
    current: List<AttestationSchemaDocument>,
    supplied: List<AttestationSchemaDocument>,
): List<AttestationSchemaDocument> {
    fun key(document: AttestationSchemaDocument) = document.kind to document.formatIdentifier
    val replaced = supplied.map(::key).toSet()
    return current.filterNot { key(it) in replaced } + supplied
}

internal fun matchesLinkedType(
    record: com.sphereon.catalog.model.AttestationSchemaRecord,
    linkedDesignId: String?,
    linkedVctId: String?,
): Boolean {
    if (!linkedDesignId.isNullOrBlank() && record.linkedDesignId != linkedDesignId) return false
    if (!linkedVctId.isNullOrBlank() && record.linkedVctId != linkedVctId) return false
    return true
}

internal fun existingLinkMatches(
    record: com.sphereon.catalog.model.AttestationSchemaRecord,
    input: com.sphereon.catalog.command.LinkSchemaArgs,
): Boolean {
    val designId = input.designId?.trim()?.takeIf { it.isNotEmpty() }
    val vctId = input.vctId?.trim()?.takeIf { it.isNotEmpty() }
    val vct = input.vct?.trim()?.takeIf { it.isNotEmpty() }
    val doctype = input.doctype?.trim()?.takeIf { it.isNotEmpty() }
    if (designId != null && record.linkedDesignId == designId) return true
    if (vctId != null && record.linkedVctId == vctId) return true
    if (vct != null && record.schema.schemaURIs.any { it.uri == vct }) return true
    if (doctype != null && record.schema.schemaURIs.any { it.uri == doctype }) return true
    return false
}

internal fun matchesSchemaFilters(
    schema: com.sphereon.catalog.model.SchemaMeta,
    id: String?,
    supportedFormats: List<String>,
    attestationLoS: String?,
    bindingType: String?,
    trustedAuthoritiesFrameworkType: String?,
    trustedAuthoritiesValue: String?,
    schemaUri: String?,
    rulebookUri: String?,
): Boolean {
    if (!id.isNullOrBlank() && schema.id != id) return false
    if (supportedFormats.isNotEmpty() && schema.supportedFormats.none { it in supportedFormats }) return false
    if (!attestationLoS.isNullOrBlank() && schema.attestationLoS != attestationLoS) return false
    if (!bindingType.isNullOrBlank() && schema.bindingType != bindingType) return false
    if (!trustedAuthoritiesFrameworkType.isNullOrBlank() &&
        schema.trustedAuthorities.none { it.frameworkType.name == trustedAuthoritiesFrameworkType }
    ) {
        return false
    }
    if (!trustedAuthoritiesValue.isNullOrBlank() &&
        schema.trustedAuthorities.none { it.value == trustedAuthoritiesValue }
    ) {
        return false
    }
    if (!schemaUri.isNullOrBlank() && schema.schemaURIs.none { it.uri == schemaUri }) return false
    if (!rulebookUri.isNullOrBlank() && schema.rulebookURI != rulebookUri) return false
    return true
}
