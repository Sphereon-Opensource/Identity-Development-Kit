package com.sphereon.data.store.blob.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.BlobStoreCapabilities
import com.sphereon.data.store.blob.BlobStoreError
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.ListResult
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.ResolvedBlobInfo
import com.sphereon.data.store.blob.TempUrlOptions
import com.sphereon.data.store.blob.TempUrlResult
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Decorator that rejects all write operations, making a store effectively read-only.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ReadOnlyBlobStore", exact = true)
class ReadOnlyBlobStore(
    private val delegate: BlobStore,
) : BlobStore {

    override val storeId: String get() = delegate.storeId
    override val capabilities: BlobStoreCapabilities get() = delegate.capabilities

    private fun readOnlyError(): IdkError =
        BlobStoreError.PermissionDenied("Blob store is read-only").toIdkError()

    override suspend fun put(target: BlobInfo, data: ByteArray, options: PutOptions): IdkResult<BlobDescriptor, IdkError> =
        Err(readOnlyError())

    override suspend fun get(info: BlobInfo): IdkResult<ResolvedBlobInfo, IdkError> =
        delegate.get(info)

    override suspend fun delete(info: BlobInfo): IdkResult<Boolean, IdkError> =
        Err(readOnlyError())

    override suspend fun exists(info: BlobInfo): IdkResult<Boolean, IdkError> =
        delegate.exists(info)

    override suspend fun stat(info: BlobInfo): IdkResult<BlobDescriptor, IdkError> =
        delegate.stat(info)

    override suspend fun list(info: BlobInfo, options: ListOptions): IdkResult<ListResult, IdkError> =
        delegate.list(info, options)

    override suspend fun putIfAbsent(target: BlobInfo, data: ByteArray): IdkResult<BlobDescriptor, IdkError> =
        Err(readOnlyError())

    override suspend fun deletePrefix(info: BlobInfo): IdkResult<Int, IdkError> =
        Err(readOnlyError())

    override suspend fun copy(source: BlobInfo, destination: BlobInfo): IdkResult<BlobDescriptor, IdkError> =
        Err(readOnlyError())

    override suspend fun move(source: BlobInfo, destination: BlobInfo): IdkResult<BlobDescriptor, IdkError> =
        Err(readOnlyError())

    override suspend fun createTempUrl(info: BlobInfo, options: TempUrlOptions): IdkResult<TempUrlResult, IdkError> =
        delegate.createTempUrl(info, options)
}
