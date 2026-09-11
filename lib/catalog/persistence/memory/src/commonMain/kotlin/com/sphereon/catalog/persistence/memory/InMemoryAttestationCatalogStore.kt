/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.catalog.persistence.memory

import com.sphereon.catalog.model.AttestationCatalog
import com.sphereon.catalog.model.AttestationCatalogStatus
import com.sphereon.catalog.model.AttestationSchemaRecord
import com.sphereon.catalog.store.AttestationCatalogStore
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class InMemoryAttestationCatalogStore : AttestationCatalogStore {
    private val mutex = Mutex()
    private val catalogs = linkedMapOf<Pair<String, String>, AttestationCatalog>()
    private val schemas = linkedMapOf<Triple<String, String, String>, AttestationSchemaRecord>()

    override suspend fun saveCatalog(
        tenantId: String,
        catalog: AttestationCatalog,
    ): IdkResult<AttestationCatalog, IdkError> =
        mutex.withLock {
            catalogs[tenantId to catalog.id] = catalog
            Ok(catalog)
        }

    override suspend fun findCatalogById(
        tenantId: String,
        catalogId: String,
    ): IdkResult<AttestationCatalog?, IdkError> = mutex.withLock { Ok(catalogs[tenantId to catalogId]) }

    override suspend fun findCatalogBySlug(
        tenantId: String,
        slug: String,
    ): IdkResult<AttestationCatalog?, IdkError> =
        mutex.withLock {
            Ok(catalogs.entries.firstOrNull { it.key.first == tenantId && it.value.slug == slug }?.value)
        }

    override suspend fun listCatalogs(
        tenantId: String,
        status: AttestationCatalogStatus?,
        verificationEnabled: Boolean?,
    ): IdkResult<List<AttestationCatalog>, IdkError> =
        mutex.withLock {
            Ok(
                catalogs.entries
                    .filter { it.key.first == tenantId }
                    .map { it.value }
                    .filter { status == null || it.status == status }
                    .filter { verificationEnabled == null || it.verificationEnabled == verificationEnabled },
            )
        }

    override suspend fun saveSchema(
        tenantId: String,
        record: AttestationSchemaRecord,
    ): IdkResult<AttestationSchemaRecord, IdkError> =
        mutex.withLock {
            val id = record.schema.id ?: return@withLock Ok(record)
            schemas[Triple(tenantId, record.catalogId, id)] = record
            Ok(record)
        }

    override suspend fun findSchema(
        tenantId: String,
        catalogId: String,
        schemaId: String,
    ): IdkResult<AttestationSchemaRecord?, IdkError> = mutex.withLock { Ok(schemas[Triple(tenantId, catalogId, schemaId)]) }

    override suspend fun listSchemas(
        tenantId: String,
        catalogId: String,
    ): IdkResult<List<AttestationSchemaRecord>, IdkError> =
        mutex.withLock {
            Ok(
                schemas.entries
                    .filter { it.key.first == tenantId && it.key.second == catalogId }
                    .map { it.value },
            )
        }

    override suspend fun deleteSchema(
        tenantId: String,
        catalogId: String,
        schemaId: String,
    ): IdkResult<Boolean, IdkError> =
        mutex.withLock {
            Ok(schemas.remove(Triple(tenantId, catalogId, schemaId)) != null)
        }

    override suspend fun listPublishedVerificationSchemas(tenantId: String,): IdkResult<List<AttestationSchemaRecord>, IdkError> =
        mutex.withLock {
            val published =
                catalogs.entries
                    .filter {
                        it.key.first == tenantId &&
                            it.value.status == AttestationCatalogStatus.PUBLISHED &&
                            it.value.verificationEnabled
                    }.map { it.value.id }
                    .toSet()
            Ok(
                schemas.entries
                    .filter {
                        it.key.first == tenantId &&
                            it.key.second in published &&
                            it.value.listing.includes(
                                kotlin.time.Clock.System
                                    .now()
                            )
                    }.map { it.value },
            )
        }
}
