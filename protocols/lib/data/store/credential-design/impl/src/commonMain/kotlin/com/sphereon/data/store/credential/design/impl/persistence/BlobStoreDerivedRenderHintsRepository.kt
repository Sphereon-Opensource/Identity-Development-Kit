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
import com.sphereon.data.store.credential.design.model.DerivedRenderHintsRecord
import com.sphereon.data.store.credential.design.persistence.DerivedRenderHintsRepository
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Blob-store-backed implementation of [DerivedRenderHintsRepository].
 *
 * Stores derived render hints as JSON documents in the blob store under a `_meta/` prefix.
 * EDK's SQL persistence modules replace this via `@ContributesBinding(replaces=[...])`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DerivedRenderHintsRepository>())
class BlobStoreDerivedRenderHintsRepository(
    private val blobService: BlobService,
) : DerivedRenderHintsRepository {
    private val json = BlobStoreJson

    private fun metaPath(id: Uuid): String = "vc-designs/_meta/derived-hints/$id/record.json"

    private fun metaPathById(id: Uuid): String = "vc-designs/_meta/derived-hints/_by-id/$id/record.json"

    override suspend fun findById(
        tenantId: String,
        id: Uuid,
    ): DerivedRenderHintsRecord? {
        val result = blobService.getBlob(BlobInfo(path = metaPathById(id), tenantId = tenantId))
        if (result.isErr) {
            return null
        }
        return json.decodeFromString<DerivedRenderHintsRecord>(result.value.data.decodeToString())
    }

    override suspend fun create(record: DerivedRenderHintsRecord): DerivedRenderHintsRecord {
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

    override suspend fun delete(
        tenantId: String,
        id: Uuid,
    ): Boolean {
        val existing = findById(tenantId, id) ?: return false
        blobService.deleteBlob(BlobInfo(path = metaPath(existing.id), tenantId = tenantId))
        blobService.deleteBlob(BlobInfo(path = metaPathById(id), tenantId = tenantId))
        return true
    }
}
