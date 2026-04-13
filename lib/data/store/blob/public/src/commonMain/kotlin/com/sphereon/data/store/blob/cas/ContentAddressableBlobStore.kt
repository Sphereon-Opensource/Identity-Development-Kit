package com.sphereon.data.store.blob.cas

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.data.store.blob.BlobMetadata
import com.sphereon.data.store.blob.ResolvedBlobInfo

/**
 * Content-addressable blob store interface.
 *
 * Blobs are stored by their content hash, enabling deduplication and integrity verification.
 */
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
