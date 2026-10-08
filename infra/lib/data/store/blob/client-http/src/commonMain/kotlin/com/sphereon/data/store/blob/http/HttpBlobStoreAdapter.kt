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

package com.sphereon.data.store.blob.http

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.BlobStoreCapabilities
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.ListResult
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.ResolvedBlobInfo
import com.sphereon.data.store.blob.TempUrlOptions
import com.sphereon.data.store.blob.TempUrlResult

/**
 * Adapts [HttpBlobServiceClient] (which implements [BlobService]) to the [BlobStore] interface
 * for integration with [BlobStoreFactory] and [BlobStoreRegistry].
 *
 * Binds a fixed [tenantId] so callers use the single-tenant [BlobStore] API.
 */
class HttpBlobStoreAdapter(
    private val client: HttpBlobServiceClient,
    private val tenantId: String,
) : BlobStore {
    override val schemeId: String = HttpBlobServiceClientConfig.BACKEND_ID

    override val capabilities: BlobStoreCapabilities =
        BlobStoreCapabilities(
            supportsCopy = true,
            supportsMove = true,
            supportsListing = true,
            supportsMetadata = true,
        )

    private fun withTenant(info: BlobInfo): BlobInfo =
        if (info.tenantId != null) {
            info
        } else {
            info.copy(tenantId = tenantId)
        }

    override suspend fun put(
        target: BlobInfo,
        data: ByteArray,
        options: PutOptions,
    ): IdkResult<BlobDescriptor, IdkError> = client.storeBlob(withTenant(target), data, options)

    override suspend fun get(info: BlobInfo): IdkResult<ResolvedBlobInfo, IdkError> = client.getBlob(withTenant(info))

    override suspend fun delete(info: BlobInfo): IdkResult<Boolean, IdkError> = client.deleteBlob(withTenant(info))

    override suspend fun exists(info: BlobInfo): IdkResult<Boolean, IdkError> {
        val statResult = stat(info)
        if (statResult.isErr) {
            if (statResult.error.code == "BLOB_NOT_FOUND") {
                return Ok(false)
            }
            return Err(statResult.error)
        }
        return Ok(true)
    }

    override suspend fun stat(info: BlobInfo): IdkResult<BlobDescriptor, IdkError> = client.getBlobInfo(withTenant(info))

    override suspend fun list(
        info: BlobInfo,
        options: ListOptions,
    ): IdkResult<ListResult, IdkError> = client.listBlobs(withTenant(info), options = options)

    override suspend fun copy(
        source: BlobInfo,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> = client.copyBlob(withTenant(source), withTenant(destination))

    override suspend fun move(
        source: BlobInfo,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> = client.moveBlob(withTenant(source), withTenant(destination))

    override suspend fun createTempUrl(
        info: BlobInfo,
        options: TempUrlOptions,
    ): IdkResult<TempUrlResult, IdkError> = client.createTempUrl(withTenant(info), options = options)
}
