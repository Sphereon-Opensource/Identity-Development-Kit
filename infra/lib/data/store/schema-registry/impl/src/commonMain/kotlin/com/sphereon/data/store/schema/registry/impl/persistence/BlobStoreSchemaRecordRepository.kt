/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.store.schema.registry.impl.persistence

import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.schema.registry.SchemaRecord
import com.sphereon.data.store.schema.registry.SchemaRecordFilter
import com.sphereon.data.store.schema.registry.persistence.SchemaRecordRepository
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Blob-store-backed implementation of [SchemaRecordRepository].
 *
 * Stores schema metadata as JSON documents in the blob store under a `_meta/` prefix.
 * This gives IDK a fully working service layer backed only by [BlobService].
 *
 * EDK's SQL persistence modules replace this via `@ContributesBinding(replaces=[...])`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SchemaRecordRepository>())
class BlobStoreSchemaRecordRepository(
    private val blobService: BlobService,
) : SchemaRecordRepository {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

    private fun metaPath(
        namespace: String,
        name: String,
    ): String = "schemas/_meta/${namespace.ifEmpty { "_default" }}/$name/record.json"

    private fun metaPathById(id: Uuid): String = "schemas/_meta/_by-id/$id/record.json"

    override suspend fun findById(
        tenantId: String,
        id: Uuid,
    ): SchemaRecord? {
        val result = blobService.getBlob(BlobInfo(path = metaPathById(id), tenantId = tenantId))
        if (result.isErr) {
            return null
        }
        val resolved = result.value
        return json.decodeFromString<SchemaRecord>(resolved.data.decodeToString())
    }

    override suspend fun findByNamespaceName(
        tenantId: String,
        namespace: String,
        name: String,
    ): SchemaRecord? {
        val result = blobService.getBlob(BlobInfo(path = metaPath(namespace, name), tenantId = tenantId))
        if (result.isErr) {
            return null
        }
        val resolved = result.value
        return json.decodeFromString<SchemaRecord>(resolved.data.decodeToString())
    }

    override suspend fun findAll(
        tenantId: String,
        filter: SchemaRecordFilter,
    ): List<SchemaRecord> {
        val listResult =
            blobService.listBlobs(
                BlobInfo(path = "schemas/_meta/", tenantId = tenantId),
                ListOptions(prefix = "schemas/_meta/", recursive = true, maxResults = 1000),
            )
        if (listResult.isErr) {
            return emptyList()
        }

        val records = mutableListOf<SchemaRecord>()
        for (descriptor in listResult.value.descriptors) {
            val path = descriptor.path
            if (!path.endsWith("/record.json") || path.contains("/_by-id/")) {
                continue
            }
            val blob = blobService.getBlob(BlobInfo(path = path, tenantId = tenantId))
            if (blob.isErr) {
                continue
            }
            val record = json.decodeFromString<SchemaRecord>(blob.value.data.decodeToString())
            if (matchesFilter(record, filter)) {
                records.add(record)
            }
        }
        return records
    }

    override suspend fun create(record: SchemaRecord): SchemaRecord {
        val data = json.encodeToString(record).encodeToByteArray()
        // Store by namespace/name path
        blobService.storeBlob(
            target = BlobInfo(path = metaPath(record.namespace, record.name), tenantId = record.tenantId),
            data = data,
        )
        // Store by-id index
        blobService.storeBlob(
            target = BlobInfo(path = metaPathById(record.id), tenantId = record.tenantId),
            data = data,
        )
        return record
    }

    override suspend fun update(record: SchemaRecord): SchemaRecord {
        return create(record) // overwrite
    }

    override suspend fun delete(
        tenantId: String,
        id: Uuid,
    ): Boolean {
        val existing = findById(tenantId, id) ?: return false
        blobService.deleteBlob(BlobInfo(path = metaPath(existing.namespace, existing.name), tenantId = tenantId))
        blobService.deleteBlob(BlobInfo(path = metaPathById(id), tenantId = tenantId))
        return true
    }

    private fun matchesFilter(
        record: SchemaRecord,
        filter: SchemaRecordFilter,
    ): Boolean {
        if (filter.schemaType != null && record.schemaType != filter.schemaType) {
            return false
        }
        if (filter.namespace != null && record.namespace != filter.namespace) {
            return false
        }
        if (filter.hostingMode != null && record.hostingMode != filter.hostingMode) {
            return false
        }
        val nameContains = filter.nameContains
        if (nameContains != null && !record.name.contains(nameContains, ignoreCase = true)) {
            return false
        }
        return true
    }
}
