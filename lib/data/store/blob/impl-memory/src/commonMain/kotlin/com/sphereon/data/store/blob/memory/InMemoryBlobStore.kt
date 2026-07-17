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

package com.sphereon.data.store.blob.memory

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
import com.sphereon.data.store.blob.BlobStoreSchemes
import com.sphereon.data.store.blob.ByteArrayBlobSource
import com.sphereon.data.store.blob.DEFAULT_BLOB_STREAM_CHUNK_SIZE
import com.sphereon.data.store.blob.DeleteOptions
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.ListResult
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.ResolvedBlobInfo
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal class InMemoryBlobStore(
    private val partition: InMemoryBlobPartition,
    private val maxEntries: Int = 0,
) : BlobStore {
    override val schemeId: String = BlobStoreSchemes.MEMORY

    override val capabilities: BlobStoreCapabilities =
        BlobStoreCapabilities.SIMPLE.copy(
            supportsEtag = true,
            supportsRevisions = true,
            supportsConditionalWrites = true,
            supportsConditionalDelete = true,
            supportsStreamingRead = true,
            supportsStreamingWrite = true,
            supportsRangeReads = true,
            supportsIntegrityVerification = true,
        )

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun put(
        target: BlobInfo,
        data: ByteArray,
        options: PutOptions,
    ): IdkResult<BlobDescriptor, IdkError> =
        partition.mutex.withLock {
            try {
                val path = target.path ?: Uuid.random().toString()
                if (maxEntries > 0 && partition.blobs.size >= maxEntries && !partition.blobs.containsKey(path)) {
                    return@withLock Err(BlobStoreError.QuotaExceeded("Max entries ($maxEntries) reached").toIdkError())
                }
                val existing = partition.blobs[path]
                if (!options.overwrite && existing != null) {
                    return@withLock Err(BlobStoreError.AlreadyExists(path).toIdkError())
                }
                if (options.ifNoneMatch != null && existing != null) {
                    val currentEtag = etag(existing.revision)
                    if (options.ifNoneMatch == "*" || options.ifNoneMatch == currentEtag) {
                        return@withLock Err(BlobStoreError.PreconditionFailed("ifNoneMatch condition failed for $path").toIdkError())
                    }
                }
                if (options.ifMatch != null) {
                    val matches = existing != null && (options.ifMatch == "*" || options.ifMatch == etag(existing.revision))
                    if (!matches) {
                        return@withLock Err(BlobStoreError.PreconditionFailed("ifMatch condition failed for $path").toIdkError())
                    }
                }
                if (options.expectedRevision != null && existing?.revision != options.expectedRevision) {
                    return@withLock Err(
                        BlobStoreError.PreconditionFailed("Expected revision ${options.expectedRevision} for $path").toIdkError(),
                    )
                }

                val metadata = target.toBlobMetadata()
                val now = Clock.System.now().toEpochMilliseconds()
                partition.blobs[path] =
                    InMemoryStoredBlob(
                        data = data.copyOf(),
                        metadata = metadata,
                        createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                        lastModifiedAtEpochMillis = now,
                        revision = (existing?.revision ?: 0) + 1,
                    )

                Ok(toDescriptor(path, partition.blobs.getValue(path)))
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to put blob '${target.path}': ${expected.message}", exception = expected, code = "BLOB_PUT_FAILED"))
            }
        }

    override suspend fun putStream(
        target: BlobInfo,
        source: BlobByteSource,
        options: PutOptions,
    ): IdkResult<BlobDescriptor, IdkError> {
        val chunks = mutableListOf<ByteArray>()
        var totalSize = 0L
        try {
            while (true) {
                val readResult = source.read(DEFAULT_BLOB_STREAM_CHUNK_SIZE)
                if (readResult.isErr) {
                    return Err(readResult.error)
                }
                val chunk = readResult.value ?: break
                if (chunk.isEmpty()) {
                    return Err(BlobStoreError.BackendError("Blob stream returned an empty chunk before EOF").toIdkError())
                }
                totalSize += chunk.size
                if (totalSize > Int.MAX_VALUE) {
                    return Err(BlobStoreError.QuotaExceeded("In-memory blob stream exceeds the platform byte-array limit").toIdkError())
                }
                chunks += chunk
            }
        } finally {
            source.close()
        }

        val data = ByteArray(totalSize.toInt())
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(data, destinationOffset = offset)
            offset += chunk.size
        }
        return put(target, data, options)
    }

    override suspend fun get(info: BlobInfo): IdkResult<ResolvedBlobInfo, IdkError> =
        partition.mutex.withLock {
            try {
                val path = info.path ?: return@withLock Err(BlobStoreError.NotFound("null").toIdkError())
                val stored =
                    partition.blobs[path]
                        ?: return@withLock Err(BlobStoreError.NotFound(path).toIdkError())
                val descriptor = toDescriptor(path, stored)
                Ok(ResolvedBlobInfo.fromContent(info, stored.data.copyOf(), descriptor))
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to get blob '${info.path}': ${expected.message}", exception = expected, code = "BLOB_GET_FAILED"))
            }
        }

    override suspend fun openRead(info: BlobInfo): IdkResult<BlobReadStream, IdkError> =
        partition.mutex.withLock {
            val path = info.path ?: return@withLock Err(BlobStoreError.NotFound("null").toIdkError())
            val stored = partition.blobs[path] ?: return@withLock Err(BlobStoreError.NotFound(path).toIdkError())
            Ok(
                BlobReadStream(
                    descriptor = toDescriptor(path, stored),
                    source = ByteArrayBlobSource(stored.data),
                ),
            )
        }

    override suspend fun openReadRange(
        info: BlobInfo,
        range: BlobReadRange,
    ): IdkResult<BlobReadStream, IdkError> =
        partition.mutex.withLock {
            val path = info.path ?: return@withLock Err(BlobStoreError.NotFound("null").toIdkError())
            val stored = partition.blobs[path] ?: return@withLock Err(BlobStoreError.NotFound(path).toIdkError())
            if (range.startInclusive > stored.data.size) {
                return@withLock Err(BlobStoreError.PreconditionFailed("Range starts beyond end of blob: $path").toIdkError())
            }
            Ok(BlobReadStream(toDescriptor(path, stored), ByteArrayBlobSource(stored.data, range)))
        }

    override suspend fun delete(info: BlobInfo): IdkResult<Boolean, IdkError> =
        partition.mutex.withLock {
            try {
                val path = info.path ?: return@withLock Err(BlobStoreError.NotFound("null").toIdkError())
                Ok(partition.blobs.remove(path) != null)
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to delete blob '${info.path}': ${expected.message}", exception = expected, code = "BLOB_DELETE_FAILED"))
            }
        }

    override suspend fun deleteConditional(
        info: BlobInfo,
        options: DeleteOptions,
    ): IdkResult<Boolean, IdkError> =
        partition.mutex.withLock {
            val path = info.path ?: return@withLock Err(BlobStoreError.NotFound("null").toIdkError())
            val existing =
                partition.blobs[path]
                    ?: return@withLock Err(BlobStoreError.PreconditionFailed("Blob does not exist: $path").toIdkError())
            val currentEtag = etag(existing.revision)
            if (options.ifMatch != null && options.ifMatch != "*" && options.ifMatch != currentEtag) {
                return@withLock Err(BlobStoreError.PreconditionFailed("ifMatch condition failed for $path").toIdkError())
            }
            if (options.expectedRevision != null && options.expectedRevision != existing.revision) {
                return@withLock Err(
                    BlobStoreError.PreconditionFailed("Expected revision ${options.expectedRevision} for $path").toIdkError(),
                )
            }
            partition.blobs.remove(path)
            Ok(true)
        }

    override suspend fun exists(info: BlobInfo): IdkResult<Boolean, IdkError> =
        partition.mutex.withLock {
            try {
                val path = info.path ?: return@withLock Ok(false)
                Ok(partition.blobs.containsKey(path))
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to check existence of blob '${info.path}': ${expected.message}", exception = expected, code = "BLOB_EXISTS_FAILED"))
            }
        }

    override suspend fun stat(info: BlobInfo): IdkResult<BlobDescriptor, IdkError> =
        partition.mutex.withLock {
            try {
                val path = info.path ?: return@withLock Err(BlobStoreError.NotFound("null").toIdkError())
                val stored =
                    partition.blobs[path]
                        ?: return@withLock Err(BlobStoreError.NotFound(path).toIdkError())
                Ok(toDescriptor(path, stored))
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to stat blob '${info.path}': ${expected.message}", exception = expected, code = "BLOB_STAT_FAILED"))
            }
        }

    override suspend fun list(
        info: BlobInfo,
        options: ListOptions,
    ): IdkResult<ListResult, IdkError> =
        partition.mutex.withLock {
            try {
                val prefix = options.prefix ?: info.path ?: ""
                val entries =
                    partition.blobs.entries
                        .filter { (path, _) -> path.startsWith(prefix) }
                        .sortedBy { it.key }

                val descriptors =
                    entries
                        .take(options.maxResults)
                        .map { (path, stored) ->
                            toDescriptor(path, stored)
                        }

                val nextPageToken =
                    if (entries.size > options.maxResults) {
                        entries[options.maxResults].key
                    } else {
                        null
                    }

                Ok(ListResult(descriptors = descriptors, nextPageToken = nextPageToken))
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to list blobs: ${expected.message}", exception = expected, code = "BLOB_LIST_FAILED"))
            }
        }

    override suspend fun deletePrefix(info: BlobInfo): IdkResult<Int, IdkError> =
        partition.mutex.withLock {
            try {
                val prefix =
                    info.path ?: return@withLock Err(
                        IdkError.fromString(message = "deletePrefix requires a path", code = "BLOB_DELETE_PREFIX_FAILED"),
                    )
                val keysToRemove = partition.blobs.keys.filter { it.startsWith(prefix) }
                keysToRemove.forEach { partition.blobs.remove(it) }
                Ok(keysToRemove.size)
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to delete prefix '${info.path}': ${expected.message}", exception = expected, code = "BLOB_DELETE_PREFIX_FAILED"))
            }
        }

    override suspend fun copy(
        source: BlobInfo,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> =
        partition.mutex.withLock {
            try {
                val sourcePath = source.path ?: return@withLock Err(BlobStoreError.NotFound("null").toIdkError())
                val destPath = destination.path ?: error("Destination path is required for copy")
                val stored =
                    partition.blobs[sourcePath]
                        ?: return@withLock Err(BlobStoreError.NotFound(sourcePath).toIdkError())
                val now = Clock.System.now().toEpochMilliseconds()
                partition.blobs[destPath] =
                    stored.copy(
                        data = stored.data.copyOf(),
                        createdAtEpochMillis = now,
                        lastModifiedAtEpochMillis = now,
                        revision = 1,
                    )
                Ok(toDescriptor(destPath, partition.blobs.getValue(destPath)))
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to copy blob: ${expected.message}", exception = expected, code = "BLOB_COPY_FAILED"))
            }
        }

    override suspend fun move(
        source: BlobInfo,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> =
        partition.mutex.withLock {
            try {
                val sourcePath = source.path ?: return@withLock Err(BlobStoreError.NotFound("null").toIdkError())
                val destPath = destination.path ?: error("Destination path is required for move")
                val stored =
                    partition.blobs.remove(sourcePath)
                        ?: return@withLock Err(BlobStoreError.NotFound(sourcePath).toIdkError())
                val now = Clock.System.now().toEpochMilliseconds()
                partition.blobs[destPath] = stored.copy(lastModifiedAtEpochMillis = now)
                Ok(toDescriptor(destPath, partition.blobs.getValue(destPath)))
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to move blob: ${expected.message}", exception = expected, code = "BLOB_MOVE_FAILED"))
            }
        }

    private fun toDescriptor(
        path: String,
        stored: InMemoryStoredBlob,
    ): BlobDescriptor =
        BlobDescriptor(
            path = path,
            storeId = schemeId,
            sizeBytes = stored.data.size.toLong(),
            contentType = stored.metadata.contentType,
            filename = path.substringAfterLast('/'),
            etag = etag(stored.revision),
            revision = stored.revision,
            createdAt = Instant.fromEpochMilliseconds(stored.createdAtEpochMillis),
            lastModified = Instant.fromEpochMilliseconds(stored.lastModifiedAtEpochMillis),
            metadata = stored.metadata,
            contentHash = stored.metadata.contentHash,
        )

    private fun etag(revision: Long): String = "\"$revision\""
}
