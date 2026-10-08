/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.catalog.impl.client

import com.sphereon.catalog.client.CatalogRemoteClient
import com.sphereon.catalog.model.AttestationCatalog
import com.sphereon.catalog.model.AttestationCatalogStatus
import com.sphereon.catalog.model.AttestationSchemaDocument
import com.sphereon.catalog.model.AttestationSchemaRecord
import com.sphereon.catalog.model.CatalogDocumentKind
import com.sphereon.catalog.model.CatalogListingWindow
import com.sphereon.catalog.model.CatalogSchemaProvenance
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.store.AttestationCatalogStore
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import kotlin.time.Clock
import kotlin.time.Instant

data class IndexedCatalogSource(
    val baseUrl: String,
    val slug: String,
    val displayName: String,
    val id: String? = null,
)

/**
 * Copies a trusted TS 11 catalog API into the local store. Remote list
 * failures leave the previous index in place.
 */
class CatalogRemoteIndexSync(
    private val remote: CatalogRemoteClient,
    private val now: () -> Instant = { Clock.System.now() },
) {
    suspend fun sync(
        store: AttestationCatalogStore,
        tenantId: String,
        source: IndexedCatalogSource,
    ): IdkResult<Unit, IdkError> {
        val listing = listAll(source.baseUrl).getOrElse { return Err(it) }
        val instant = now()
        val existing = store.findCatalogBySlug(tenantId, source.slug).getOrNull()
        val catalog =
            (
                existing ?: AttestationCatalog(
                    id = source.id?.takeIf { it.isNotBlank() } ?: "wallet-${source.slug}",
                    slug = source.slug,
                    displayName = source.displayName.ifBlank { source.slug },
                    status = AttestationCatalogStatus.PUBLISHED,
                    createdAt = instant,
                    updatedAt = instant,
                )
            ).copy(
                displayName = source.displayName.ifBlank { source.slug },
                status = AttestationCatalogStatus.PUBLISHED,
                updatedAt = instant,
            )
        store.saveCatalog(tenantId, catalog).getOrElse { return Err(it) }
        val remoteIds = listing.schemas.mapNotNull { it.id?.takeIf(String::isNotBlank) }.toSet()
        listing.schemas.forEach { schema ->
            persistServed(store, tenantId, catalog.id, schema, instant).getOrElse { return Err(it) }
        }
        if (listing.complete) {
            store
                .listSchemas(tenantId, catalog.id)
                .getOrNull()
                .orEmpty()
                .filter { it.provenance == CatalogSchemaProvenance.IMPORTED }
                .filter { record -> record.schema.id !in remoteIds }
                .forEach { record ->
                    store
                        .saveSchema(tenantId, record.copy(listing = CatalogListingWindow.never(instant), updatedAt = instant))
                        .getOrElse { return Err(it) }
                }
        }
        return Ok(Unit)
    }

    private data class RemoteListing(
        val schemas: List<SchemaMeta>,
        val complete: Boolean,
    )

    private suspend fun listAll(baseUrl: String): IdkResult<RemoteListing, IdkError> {
        val pageSize = 100
        val incoming = mutableListOf<SchemaMeta>()
        val seen = mutableSetOf<String>()
        var offset = 0
        var total = Int.MAX_VALUE
        do {
            val page = remote.listSchemas(baseUrl, pageSize, offset).getOrElse { return Err(it) }
            total = page.total
            val fresh =
                page.data.filter { schema ->
                    val id = schema.id?.takeIf(String::isNotBlank)
                    id == null || seen.add(id)
                }
            if (page.data.isEmpty() || fresh.isEmpty()) break
            incoming += page.data
            offset += page.data.size
        } while (offset < total)
        return Ok(RemoteListing(schemas = incoming, complete = incoming.size >= total || total == 0))
    }

    private suspend fun persistServed(
        store: AttestationCatalogStore,
        tenantId: String,
        catalogId: String,
        listed: SchemaMeta,
        instant: Instant,
    ): IdkResult<Unit, IdkError> {
        val schemaId = listed.id ?: return Ok(Unit)
        val existing = store.findSchema(tenantId, catalogId, schemaId).getOrNull()
        val documents =
            if (existing != null &&
                existing.schema.version == listed.version &&
                documentsComplete(listed, existing.documents)
            ) {
                existing.documents
            } else {
                fetchDocuments(listed)
            }
        store
            .saveSchema(
                tenantId,
                AttestationSchemaRecord(
                    catalogId = catalogId,
                    schema = listed,
                    provenance = CatalogSchemaProvenance.IMPORTED,
                    listing = CatalogListingWindow.open(instant),
                    documents = documents,
                    createdAt = existing?.createdAt ?: instant,
                    updatedAt = instant,
                ),
            ).getOrElse { return Err(it) }
        return Ok(Unit)
    }

    private fun documentsComplete(
        listed: SchemaMeta,
        documents: List<AttestationSchemaDocument>,
    ): Boolean {
        val formats = listed.supportedFormats.toSet()
        if (formats.isEmpty()) return false
        val haveFormats =
            documents
                .filter { it.kind == CatalogDocumentKind.FORMAT }
                .mapNotNull { it.formatIdentifier }
                .toSet()
        if (!formats.all { it in haveFormats }) return false
        if (listed.rulebookURI.isNotBlank() && documents.none { it.kind == CatalogDocumentKind.RULEBOOK }) {
            return false
        }
        return true
    }

    private suspend fun fetchDocuments(listed: SchemaMeta): List<AttestationSchemaDocument> {
        val documents = mutableListOf<AttestationSchemaDocument>()
        listed.schemaURIs.forEach { ref ->
            val doc = remote.fetchDocument(ref.uri).getOrNull() ?: return@forEach
            documents +=
                AttestationSchemaDocument(
                    kind = CatalogDocumentKind.FORMAT,
                    formatIdentifier = ref.formatIdentifier,
                    mediaType = doc.mediaType,
                    bytes = doc.bytes,
                    integrity = doc.integrity,
                )
        }
        if (listed.rulebookURI.isNotBlank()) {
            remote.fetchDocument(listed.rulebookURI).getOrNull()?.let { rulebook ->
                documents +=
                    AttestationSchemaDocument(
                        kind = CatalogDocumentKind.RULEBOOK,
                        mediaType = rulebook.mediaType,
                        bytes = rulebook.bytes,
                        integrity = rulebook.integrity,
                    )
            }
        }
        return documents
    }
}
