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

package com.sphereon.data.store.blob.cas

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.data.store.blob.BlobMetadata
import com.sphereon.data.store.blob.ResolvedBlobInfo

/**
 * Content-addressable blob store interface.
 *
 * Blobs are stored by their content hash, enabling deduplication and integrity verification.
 */
@JsExportCompat
interface ContentAddressableBlobStore {
    /**
     * Store data by content address. Returns the content address descriptor.
     * If data with the same hash already exists, increments the reference count.
     */
    suspend fun store(
        data: ByteArray,
        algorithm: DigestAlg = DigestAlg.SHA256,
        metadata: BlobMetadata = BlobMetadata.EMPTY,
    ): IdkResult<ContentAddressDescriptor, IdkError>

    /**
     * Retrieve data by content address.
     */
    suspend fun retrieve(address: ContentAddress): IdkResult<ResolvedBlobInfo, IdkError>

    /**
     * Check if data exists for a content address.
     */
    suspend fun contains(address: ContentAddress): IdkResult<Boolean, IdkError>

    /**
     * Remove data by content address. Only removes if reference count drops to zero.
     */
    suspend fun remove(address: ContentAddress): IdkResult<Boolean, IdkError>

    /**
     * Verify that stored data matches its content address.
     */
    suspend fun verify(address: ContentAddress): IdkResult<Boolean, IdkError>
}
