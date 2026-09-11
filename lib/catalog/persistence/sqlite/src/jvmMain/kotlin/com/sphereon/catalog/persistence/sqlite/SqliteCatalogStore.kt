/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.catalog.persistence.sqlite

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
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * SQLDelight SQLite store. [CatalogDatabaseSqlite] is provided by [SqliteCatalogDatabaseGraph]
 * or by a wallet/edge assembly that owns the driver.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class SqliteCatalogStore(
    private val database: CatalogDatabaseSqlite,
) : AttestationCatalogStore {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override suspend fun saveCatalog(
        tenantId: String,
        catalog: AttestationCatalog,
    ): IdkResult<AttestationCatalog, IdkError> =
        runQuery("save catalog") {
            database.catalogQueries.upsertCatalog(
                tenant_id = tenantId,
                id = catalog.id,
                slug = catalog.slug,
                display_name = catalog.displayName,
                description = catalog.description,
                status = catalog.status.name,
                verification_enabled = catalog.verificationEnabled,
                version = catalog.version,
                created_at = catalog.createdAt?.toString(),
                updated_at = catalog.updatedAt?.toString(),
            )
            catalog
        }

    override suspend fun findCatalogById(
        tenantId: String,
        catalogId: String,
    ): IdkResult<AttestationCatalog?, IdkError> =
        runQuery("find catalog by id") {
            database.catalogQueries
                .selectCatalog(tenantId, catalogId)
                .executeAsOneOrNull()
                ?.toModel()
        }

    override suspend fun findCatalogBySlug(
        tenantId: String,
        slug: String,
    ): IdkResult<AttestationCatalog?, IdkError> =
        runQuery("find catalog by slug") {
            database.catalogQueries
                .selectCatalogBySlug(tenantId, slug)
                .executeAsOneOrNull()
                ?.toModel()
        }

    override suspend fun listCatalogs(
        tenantId: String,
        status: AttestationCatalogStatus?,
        verificationEnabled: Boolean?,
    ): IdkResult<List<AttestationCatalog>, IdkError> =
        runQuery("list catalogs") {
            database.catalogQueries
                .listCatalogs(tenantId)
                .executeAsList()
                .map { it.toModel() }
                .filter { status == null || it.status == status }
                .filter { verificationEnabled == null || it.verificationEnabled == verificationEnabled }
        }

    override suspend fun saveSchema(
        tenantId: String,
        record: AttestationSchemaRecord,
    ): IdkResult<AttestationSchemaRecord, IdkError> {
        val schemaId = record.schema.id ?: return Ok(record)
        return runQuery("save schema") {
            database.catalogQueries.upsertSchema(
                tenant_id = tenantId,
                catalog_id = record.catalogId,
                schema_id = schemaId,
                schema_json = json.encodeToString(SchemaMeta.serializer(), record.schema),
                provenance = record.provenance.name,
                listed_from = record.listing.startEpochMillis(),
                listed_until = record.listing.endEpochMillis(),
                linked_design_id = record.linkedDesignId,
                linked_vct_id = record.linkedVctId,
                created_at = record.createdAt?.toString(),
                updated_at = record.updatedAt?.toString(),
            )
            database.catalogQueries.deleteSchemaDocuments(tenantId, record.catalogId, schemaId)
            record.documents.forEach { doc ->
                database.catalogQueries.upsertDocument(
                    tenant_id = tenantId,
                    catalog_id = record.catalogId,
                    schema_id = schemaId,
                    kind = doc.kind.name,
                    format_identifier = doc.formatIdentifier.orEmpty(),
                    media_type = doc.mediaType,
                    bytes = doc.bytes,
                    integrity = doc.integrity,
                )
            }
            record
        }
    }

    override suspend fun findSchema(
        tenantId: String,
        catalogId: String,
        schemaId: String,
    ): IdkResult<AttestationSchemaRecord?, IdkError> =
        runQuery("find schema") {
            val row =
                database.catalogQueries.selectSchema(tenantId, catalogId, schemaId).executeAsOneOrNull()
                    ?: return@runQuery null
            val docs =
                database.catalogQueries
                    .listDocuments(tenantId, catalogId, schemaId)
                    .executeAsList()
                    .map { it.toDoc() }
            row.toRecord(docs)
        }

    override suspend fun listSchemas(
        tenantId: String,
        catalogId: String,
    ): IdkResult<List<AttestationSchemaRecord>, IdkError> =
        runQuery("list schemas") {
            database.catalogQueries.listSchemas(tenantId, catalogId).executeAsList().map { row ->
                val docs =
                    database.catalogQueries
                        .listDocuments(tenantId, catalogId, row.schema_id)
                        .executeAsList()
                        .map { it.toDoc() }
                row.toRecord(docs)
            }
        }

    override suspend fun deleteSchema(
        tenantId: String,
        catalogId: String,
        schemaId: String,
    ): IdkResult<Boolean, IdkError> =
        runQuery("delete schema") {
            val exists = database.catalogQueries.selectSchema(tenantId, catalogId, schemaId).executeAsOneOrNull() != null
            database.catalogQueries.deleteSchemaDocuments(tenantId, catalogId, schemaId)
            database.catalogQueries.deleteSchema(tenantId, catalogId, schemaId)
            exists
        }

    override suspend fun listPublishedVerificationSchemas(tenantId: String,): IdkResult<List<AttestationSchemaRecord>, IdkError> =
        runQuery("list published verification schemas") {
            database.catalogQueries.listPublishedVerificationSchemas(tenantId, Clock.System.now().toEpochMilliseconds()).executeAsList().map { row ->
                val docs =
                    database.catalogQueries
                        .listDocuments(tenantId, row.catalog_id, row.schema_id)
                        .executeAsList()
                        .map { it.toDoc() }
                row.toRecord(docs)
            }
        }

    private suspend fun <T> runQuery(
        operation: String,
        block: () -> T,
    ): IdkResult<T, IdkError> =
        withContext(IO) {
            try {
                Ok(block())
            } catch (expected: Exception) {
                Err(IdkError.UNKNOWN_ERROR(message = "Failed to $operation: ${expected.message}", exception = expected))
            }
        }

    private fun Attestation_catalog.toModel() =
        AttestationCatalog(
            id = id,
            slug = slug,
            displayName = display_name,
            description = description,
            status = AttestationCatalogStatus.valueOf(status),
            verificationEnabled = verification_enabled,
            version = version,
            createdAt = created_at?.let(Instant::parse),
            updatedAt = updated_at?.let(Instant::parse),
        )

    private fun Attestation_schema.toRecord(docs: List<AttestationSchemaDocument>) =
        AttestationSchemaRecord(
            catalogId = catalog_id,
            schema = json.decodeFromString(SchemaMeta.serializer(), schema_json),
            provenance = CatalogSchemaProvenance.valueOf(provenance),
            listing = CatalogListingWindow.fromEpochMillis(listed_from, listed_until),
            linkedDesignId = linked_design_id,
            linkedVctId = linked_vct_id,
            documents = docs,
            createdAt = created_at?.let(Instant::parse),
            updatedAt = updated_at?.let(Instant::parse),
        )

    private fun Attestation_schema_document.toDoc() =
        AttestationSchemaDocument(
            kind = CatalogDocumentKind.valueOf(kind),
            formatIdentifier = format_identifier.ifBlank { null },
            mediaType = media_type,
            bytes = bytes,
            integrity = integrity,
        )
}
