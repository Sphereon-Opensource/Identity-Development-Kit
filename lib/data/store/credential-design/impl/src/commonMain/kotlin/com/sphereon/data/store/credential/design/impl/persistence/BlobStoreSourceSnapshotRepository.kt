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

package com.sphereon.data.store.credential.design.impl.persistence

import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.credential.design.model.SourceSnapshotRecord
import com.sphereon.data.store.credential.design.persistence.SourceSnapshotRepository
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Blob-store-backed implementation of [SourceSnapshotRepository].
 *
 * Stores source snapshot metadata as JSON documents in the blob store under a `_meta/` prefix.
 * EDK's SQL persistence modules replace this via `@ContributesBinding(replaces=[...])`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SourceSnapshotRepository>())
class BlobStoreSourceSnapshotRepository(
    private val blobService: BlobService,
) : SourceSnapshotRepository {
    private val json = BlobStoreJson

    private fun metaPath(id: Uuid): String = "vc-designs/_meta/snapshots/$id/record.json"

    private fun metaPathById(id: Uuid): String = "vc-designs/_meta/snapshots/_by-id/$id/record.json"

    override suspend fun findById(
        tenantId: String,
        id: Uuid,
    ): SourceSnapshotRecord? {
        val result = blobService.getBlob(BlobInfo(path = metaPathById(id), tenantId = tenantId))
        if (result.isErr) {
            return null
        }
        return json.decodeFromString<SourceSnapshotRecord>(result.value.data.decodeToString())
    }

    override suspend fun findBySaid(
        tenantId: String,
        said: String,
    ): SourceSnapshotRecord? = findAllRecords(tenantId).firstOrNull { it.said == said }

    override suspend fun create(record: SourceSnapshotRecord): SourceSnapshotRecord {
        val data = json.encodeToString(record).encodeToByteArray()
        blobService.storeBlob(
            target = BlobInfo(path = metaPath(record.id), tenantId = record.tenantId),
            data = data,
        )
        blobService.storeBlob(
            target = BlobInfo(path = metaPathById(record.id), tenantId = record.tenantId),
            data = data,
        )
        return record
    }

    override suspend fun listByDesignId(
        tenantId: String,
        designId: Uuid,
    ): List<SourceSnapshotRecord> {
        // In the blob-store implementation, snapshots are not indexed by designId.
        // The design record holds the snapshot IDs; callers should resolve individually.
        // For convenience, we return all snapshots and let the caller filter.
        return findAllRecords(tenantId)
    }

    private suspend fun findAllRecords(tenantId: String): List<SourceSnapshotRecord> {
        val listResult =
            blobService.listBlobs(
                BlobInfo(path = "vc-designs/_meta/snapshots/", tenantId = tenantId),
                ListOptions(prefix = "vc-designs/_meta/snapshots/", recursive = true, maxResults = 1000),
            )
        if (listResult.isErr) {
            return emptyList()
        }

        val records = mutableListOf<SourceSnapshotRecord>()
        for (descriptor in listResult.value.descriptors) {
            val path = descriptor.path
            if (!path.endsWith("/record.json") || path.contains("/_by-id/")) {
                continue
            }
            val blob = blobService.getBlob(BlobInfo(path = path, tenantId = tenantId))
            if (blob.isErr) {
                continue
            }
            records.add(json.decodeFromString<SourceSnapshotRecord>(blob.value.data.decodeToString()))
        }
        return records
    }
}
