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
import com.sphereon.catalog.store.AttestationCatalogStore
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError

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
