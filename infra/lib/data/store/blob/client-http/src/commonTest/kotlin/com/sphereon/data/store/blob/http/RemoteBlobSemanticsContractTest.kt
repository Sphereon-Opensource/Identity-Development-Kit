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
import com.sphereon.data.store.blob.BlobByteSource
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobReadRange
import com.sphereon.data.store.blob.BlobReadStream
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.BlobStoreCapabilities
import com.sphereon.data.store.blob.BlobStoreError
import com.sphereon.data.store.blob.ByteArrayBlobSource
import com.sphereon.data.store.blob.DeleteOptions
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.ListResult
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.ResolvedBlobInfo
import com.sphereon.data.store.blob.testing.BlobStoreContract
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HttpBlobSemanticsContractTest : BlobStoreContract() {
    override fun createStore(): BlobStore =
        SemanticMapBlobStore(
            "http-semantic",
            BlobStoreCapabilities(supportsCopy = true, supportsMove = true, supportsListing = true, supportsMetadata = true),
        )
}

class ObjectStoreSemanticsContractTest : BlobStoreContract() {
    override fun createStore(): BlobStore =
        SemanticMapBlobStore(
            "object-semantic",
            BlobStoreCapabilities.CLOUD_OBJECT_STORE.copy(supportsRevisions = true, supportsTempUrls = false),
        )
}

private class SemanticMapBlobStore(
    override val schemeId: String,
    override val capabilities: BlobStoreCapabilities,
) : BlobStore {
    private data class Stored(
        val bytes: ByteArray,
        val descriptor: BlobDescriptor,
    )

    private val values = mutableMapOf<String, Stored>()
    private val mutationMutex = Mutex()

    override suspend fun put(
        target: BlobInfo,
        data: ByteArray,
        options: PutOptions,
    ): IdkResult<BlobDescriptor, IdkError> =
        mutationMutex.withLock {
            putUnlocked(target, data, options)
        }

    private fun putUnlocked(
        target: BlobInfo,
        data: ByteArray,
        options: PutOptions,
    ): IdkResult<BlobDescriptor, IdkError> {
        val path = requireNotNull(target.path)
        val current = values[path]
        if ((options.ifMatch != null || options.expectedRevision != null || options.ifNoneMatch != null) && !capabilities.supportsConditionalWrites) {
            return unsupported("conditional write")
        }
        if (!options.overwrite && current != null) return Err(BlobStoreError.AlreadyExists(path).toIdkError())
        if (options.ifNoneMatch == "*" && current != null) return precondition(path)
        if (options.ifMatch != null && options.ifMatch != "*" && current?.descriptor?.etag != options.ifMatch) return precondition(path)
        if (options.expectedRevision != null && current?.descriptor?.revision != options.expectedRevision) return precondition(path)
        val revision = (current?.descriptor?.revision ?: 0) + 1
        val descriptor =
            BlobDescriptor(
                path = path,
                storeId = schemeId,
                sizeBytes = data.size.toLong(),
                contentType = target.contentType,
                etag = "etag-$revision",
                revision = revision,
                metadata = target.toBlobMetadata(),
            )
        values[path] = Stored(data.copyOf(), descriptor)
        return Ok(descriptor)
    }

    override suspend fun get(info: BlobInfo): IdkResult<ResolvedBlobInfo, IdkError> {
        val stored = values[requireNotNull(info.path)] ?: return Err(BlobStoreError.NotFound(info.path!!).toIdkError())
        return Ok(ResolvedBlobInfo.fromContent(info, stored.bytes, stored.descriptor))
    }

    override suspend fun openRead(info: BlobInfo): IdkResult<BlobReadStream, IdkError> {
        if (!capabilities.supportsStreamingRead) return unsupported("openRead")
        val stored = values[requireNotNull(info.path)] ?: return Err(BlobStoreError.NotFound(info.path!!).toIdkError())
        return Ok(BlobReadStream(stored.descriptor, ByteArrayBlobSource(stored.bytes)))
    }

    override suspend fun openReadRange(
        info: BlobInfo,
        range: BlobReadRange,
    ): IdkResult<BlobReadStream, IdkError> {
        if (!capabilities.supportsRangeReads) return unsupported("openReadRange")
        val stored = values[requireNotNull(info.path)] ?: return Err(BlobStoreError.NotFound(info.path!!).toIdkError())
        return Ok(BlobReadStream(stored.descriptor, ByteArrayBlobSource(stored.bytes, range)))
    }

    override suspend fun putStream(
        target: BlobInfo,
        source: BlobByteSource,
        options: PutOptions,
    ): IdkResult<BlobDescriptor, IdkError> {
        if (!capabilities.supportsStreamingWrite) return unsupported("putStream")
        val chunks = mutableListOf<ByteArray>()
        var size = 0
        try {
            while (true) {
                val chunk = source.read(64 * 1024).value ?: break
                chunks += chunk
                size += chunk.size
            }
        } finally {
            source.close()
        }
        val bytes = ByteArray(size)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(bytes, offset)
            offset += chunk.size
        }
        return put(target, bytes, options)
    }

    override suspend fun delete(info: BlobInfo): IdkResult<Boolean, IdkError> = Ok(values.remove(requireNotNull(info.path)) != null)

    override suspend fun deleteConditional(
        info: BlobInfo,
        options: DeleteOptions,
    ): IdkResult<Boolean, IdkError> =
        mutationMutex.withLock {
            if (!capabilities.supportsConditionalDelete) return@withLock unsupported("deleteConditional")
            val path = requireNotNull(info.path)
            val current = values[path] ?: return@withLock precondition(path)
            if (options.ifMatch != null && options.ifMatch != "*" && options.ifMatch != current.descriptor.etag) {
                return@withLock precondition(path)
            }
            if (options.expectedRevision != null && options.expectedRevision != current.descriptor.revision) {
                return@withLock precondition(path)
            }
            values.remove(path)
            Ok(true)
        }

    override suspend fun stat(info: BlobInfo): IdkResult<BlobDescriptor, IdkError> {
        val stored = values[requireNotNull(info.path)] ?: return Err(BlobStoreError.NotFound(info.path!!).toIdkError())
        return Ok(stored.descriptor)
    }

    override suspend fun list(
        info: BlobInfo,
        options: ListOptions,
    ): IdkResult<ListResult, IdkError> {
        val descriptors =
            values.values
                .map { it.descriptor }
                .filter { it.path.startsWith(options.prefix ?: "") }
                .take(options.maxResults)
        return Ok(ListResult(descriptors))
    }

    override suspend fun copy(
        source: BlobInfo,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> {
        val stored = values[requireNotNull(source.path)] ?: return Err(BlobStoreError.NotFound(source.path!!).toIdkError())
        return put(destination, stored.bytes)
    }

    override suspend fun move(
        source: BlobInfo,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> {
        val result = copy(source, destination)
        if (result.isOk) values.remove(source.path)
        return result
    }

    private fun <T> unsupported(operation: String): IdkResult<T, IdkError> = Err(BlobStoreError.Unsupported(operation).toIdkError())

    private fun <T> precondition(path: String): IdkResult<T, IdkError> {
        val error = BlobStoreError.PreconditionFailed("Condition failed for $path").toIdkError()
        return Err(error)
    }
}
