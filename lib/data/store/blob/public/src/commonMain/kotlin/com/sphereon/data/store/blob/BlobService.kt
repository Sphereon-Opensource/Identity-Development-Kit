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

package com.sphereon.data.store.blob

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.data.store.blob.cas.ContentAddress
import com.sphereon.data.store.blob.cas.ContentAddressDescriptor
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * High-level blob service interface providing tenant-scoped CRUD, CAS operations, and metadata search.
 *
 * All methods accept [BlobInfo] or [BlobInfoType] instead of scattered parameters.
 * Read operations accept [BlobInfoType] so already-resolved blobs pass through without re-fetch.
 * Write operations accept [BlobInfo] (the target location).
 *
 * Store instances are resolved by store ID (configured in properties, e.g., `blob.stores.documents`).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlobService", exact = true)
interface BlobService {
    /**
     * The default store ID used when no explicit store ID is provided.
     */
    fun defaultStoreId(): String

    // -- Standard CRUD --

    suspend fun storeBlob(
        target: BlobInfo,
        data: ByteArray,
        options: PutOptions = PutOptions.DEFAULT,
    ): IdkResult<BlobDescriptor, IdkError>

    /**
     * Retrieve a blob. If [info] is already a [ResolvedBlobInfo], returns it directly (no re-fetch).
     */
    suspend fun getBlob(info: BlobInfoType): IdkResult<ResolvedBlobInfo, IdkError>

    suspend fun getBlobInfo(info: BlobInfoType): IdkResult<BlobDescriptor, IdkError>

    suspend fun deleteBlob(info: BlobInfoType): IdkResult<Boolean, IdkError>

    suspend fun listBlobs(
        info: BlobInfo,
        options: ListOptions = ListOptions.DEFAULT,
    ): IdkResult<ListResult, IdkError>

    /**
     * Copy a blob. Source and destination can reference different stores (cross-store copy).
     */
    suspend fun copyBlob(
        source: BlobInfoType,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError>

    /**
     * Move a blob. Source and destination can reference different stores (cross-store move).
     */
    suspend fun moveBlob(
        source: BlobInfoType,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError>

    // -- CAS operations --

    suspend fun casStore(
        info: BlobInfo,
        data: ByteArray,
        algorithm: DigestAlg = DigestAlg.SHA256,
    ): IdkResult<ContentAddressDescriptor, IdkError>

    suspend fun casGet(
        info: BlobInfo,
        address: ContentAddress,
    ): IdkResult<ResolvedBlobInfo, IdkError>

    suspend fun casVerify(
        info: BlobInfo,
        address: ContentAddress,
    ): IdkResult<Boolean, IdkError>

    // -- Metadata search --

    suspend fun findByMetadata(
        info: BlobInfo,
        query: MetadataSearchQuery,
    ): IdkResult<List<BlobDescriptor>, IdkError>

    // -- Temp URLs --

    suspend fun createTempUrl(
        info: BlobInfoType,
        options: TempUrlOptions = TempUrlOptions.DEFAULT,
    ): IdkResult<TempUrlResult, IdkError>
}
