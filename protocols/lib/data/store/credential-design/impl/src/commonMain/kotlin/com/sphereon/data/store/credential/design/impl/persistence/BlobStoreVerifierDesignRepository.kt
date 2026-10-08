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
import com.sphereon.data.store.credential.design.model.DesignBinding
import com.sphereon.data.store.credential.design.model.DesignBindingKey
import com.sphereon.data.store.credential.design.model.DesignFilter
import com.sphereon.data.store.credential.design.model.VerifierDesignRecord
import com.sphereon.data.store.credential.design.persistence.VerifierDesignRepository
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Blob-store-backed implementation of [VerifierDesignRepository].
 *
 * Stores verifier design metadata as JSON documents in the blob store under a `_meta/` prefix.
 * EDK's SQL persistence modules replace this via `@ContributesBinding(replaces=[...])`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<VerifierDesignRepository>())
class BlobStoreVerifierDesignRepository(
    private val blobService: BlobService,
) : VerifierDesignRepository {
    private val json = BlobStoreJson

    private fun metaPath(id: Uuid): String = "vc-designs/_meta/verifiers/$id/record.json"

    private fun metaPathById(id: Uuid): String = "vc-designs/_meta/verifiers/_by-id/$id/record.json"

    override suspend fun findById(
        tenantId: String,
        id: Uuid,
    ): VerifierDesignRecord? {
        val result = blobService.getBlob(BlobInfo(path = metaPathById(id), tenantId = tenantId))
        if (result.isErr) {
            return null
        }
        return json.decodeFromString<VerifierDesignRecord>(result.value.data.decodeToString())
    }

    override suspend fun findByBinding(
        tenantId: String,
        binding: DesignBinding,
    ): List<VerifierDesignRecord> =
        findAllRecords(tenantId).filter { record ->
            record.bindings.any { it == binding }
        }

    override suspend fun findByBindingKey(
        tenantId: String,
        bindingKey: DesignBindingKey,
        bindingValue: String,
    ): List<VerifierDesignRecord> =
        findAllRecords(tenantId).filter { record ->
            record.bindings.anyMatchesKey(bindingKey, bindingValue)
        }

    override suspend fun findAll(
        tenantId: String,
        filter: DesignFilter,
    ): List<VerifierDesignRecord> = findAllRecords(tenantId).filter { matchesFilter(it, filter) }

    override suspend fun create(record: VerifierDesignRecord): VerifierDesignRecord {
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

    override suspend fun update(record: VerifierDesignRecord): VerifierDesignRecord {
        return create(record) // overwrite
    }

    override suspend fun delete(
        tenantId: String,
        id: Uuid,
    ): Boolean {
        val existing = findById(tenantId, id) ?: return false
        blobService.deleteBlob(BlobInfo(path = metaPath(existing.id), tenantId = tenantId))
        blobService.deleteBlob(BlobInfo(path = metaPathById(id), tenantId = tenantId))
        return true
    }

    private suspend fun findAllRecords(tenantId: String): List<VerifierDesignRecord> {
        val listResult =
            blobService.listBlobs(
                BlobInfo(path = "vc-designs/_meta/verifiers/", tenantId = tenantId),
                ListOptions(prefix = "vc-designs/_meta/verifiers/", recursive = true, maxResults = 1000),
            )
        if (listResult.isErr) {
            return emptyList()
        }

        val records = mutableListOf<VerifierDesignRecord>()
        for (descriptor in listResult.value.descriptors) {
            val path = descriptor.path
            if (!path.endsWith("/record.json") || path.contains("/_by-id/")) {
                continue
            }
            val blob = blobService.getBlob(BlobInfo(path = path, tenantId = tenantId))
            if (blob.isErr) {
                continue
            }
            records.add(json.decodeFromString<VerifierDesignRecord>(blob.value.data.decodeToString()))
        }
        return records
    }

    private fun matchesFilter(
        record: VerifierDesignRecord,
        filter: DesignFilter,
    ): Boolean {
        if (filter.hostingMode != null && record.hostingMode != filter.hostingMode) {
            return false
        }
        val aliasContains = filter.aliasContains
        val recordAlias = record.alias
        if (aliasContains != null && (recordAlias == null || !recordAlias.contains(aliasContains, ignoreCase = true))) {
            return false
        }
        if (filter.binding != null && record.bindings.none { it == filter.binding }) {
            return false
        }
        if (filter.bindingKey != null && filter.bindingValue != null) {
            if (!record.bindings.anyMatchesKey(filter.bindingKey!!, filter.bindingValue!!)) {
                return false
            }
        }
        return true
    }
}
