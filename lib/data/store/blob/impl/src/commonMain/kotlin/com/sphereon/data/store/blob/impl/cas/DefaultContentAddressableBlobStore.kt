package com.sphereon.data.store.blob.impl.cas

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobMetadata
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.ResolvedBlobInfo
import com.sphereon.data.store.blob.cas.ContentAddress
import com.sphereon.data.store.blob.cas.ContentAddressDescriptor
import com.sphereon.data.store.blob.cas.ContentAddressableBlobStore

/**
 * Default CAS implementation using git-style sharded paths.
 *
 * Content is stored at `cas/{alg}/{first2chars}/{rest}` for filesystem-friendly layout.
 * Paths are prefixed with [tenantPrefix] for tenant isolation.
 */
class DefaultContentAddressableBlobStore(
    private val blobStore: BlobStore,
    private val storeId: String,
    private val tenantPrefix: String? = null,
) : ContentAddressableBlobStore {

    override suspend fun store(
        data: ByteArray,
        algorithm: DigestAlg,
        metadata: BlobMetadata,
    ): IdkResult<ContentAddressDescriptor, IdkError> {
        val address = ContentAddress.compute(data, algorithm)
        val path = addressToPath(address)
        val info = BlobInfo(storeId = storeId, path = path)

        val existsResult = blobStore.exists(info)
        if (existsResult.isErr) return Err(existsResult.error)

        val hashString = address.toMultibaseString()

        if (existsResult.value) {
            val statResult = blobStore.stat(info)
            if (statResult.isErr) return Err(statResult.error)
            val descriptor = statResult.value.copy(contentHash = hashString)
            return Ok(ContentAddressDescriptor(contentAddress = address, descriptor = descriptor))
        }

        val putResult = blobStore.put(
            target = info.copy(contentType = metadata.contentType, metadata = metadata.custom),
            data = data,
            options = PutOptions(overwrite = false),
        )
        if (putResult.isErr) return Err(putResult.error)
        val descriptor = putResult.value.copy(contentHash = hashString)
        return Ok(ContentAddressDescriptor(contentAddress = address, descriptor = descriptor))
    }

    override suspend fun retrieve(address: ContentAddress): IdkResult<ResolvedBlobInfo, IdkError> {
        val path = addressToPath(address)
        val info = BlobInfo(storeId = storeId, path = path)
        return blobStore.get(info)
    }

    override suspend fun contains(address: ContentAddress): IdkResult<Boolean, IdkError> {
        val path = addressToPath(address)
        val info = BlobInfo(storeId = storeId, path = path)
        return blobStore.exists(info)
    }

    override suspend fun remove(address: ContentAddress): IdkResult<Boolean, IdkError> {
        val path = addressToPath(address)
        val info = BlobInfo(storeId = storeId, path = path)
        return blobStore.delete(info)
    }

    override suspend fun verify(address: ContentAddress): IdkResult<Boolean, IdkError> {
        val path = addressToPath(address)
        val info = BlobInfo(storeId = storeId, path = path)

        val getResult = blobStore.get(info)
        if (getResult.isErr) return Err(getResult.error)

        val storedData = getResult.value.data
        val recomputed = hash(storedData, address.algorithm)
        return Ok(recomputed.contentEquals(address.digest))
    }

    private fun addressToPath(address: ContentAddress): String {
        val hex = address.digest.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        val prefix = hex.take(2)
        val rest = hex.drop(2)
        val casPath = "cas/${address.algorithm.internalName.lowercase()}/$prefix/$rest"
        return if (tenantPrefix != null) "$tenantPrefix/$casPath" else casPath
    }
}
