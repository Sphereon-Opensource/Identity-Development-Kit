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

package com.sphereon.data.store.blob.kv

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobMetadata
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.BlobStoreCapabilities
import com.sphereon.data.store.blob.BlobStoreError
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.ListResult
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.ResolvedBlobInfo
import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreListing
import com.sphereon.data.store.kv.KvStoreVersioning
import com.sphereon.data.store.kv.KvVersionAppendResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Serializable entry stored in KvStore for each blob.
 */
@Serializable
internal data class KvBlobEntry(
    val data: String, // Base64-encoded blob data
    val metadata: BlobMetadata,
    val sizeBytes: Long,
    val createdAtMillis: Long,
    val lastModifiedAtMillis: Long,
)

/** The revision of one blob, kept as the head of that blob's version chain. */
@Serializable
internal data class KvBlobRevision(
    val revision: Long,
)

/**
 * Blob store backed by IDK's KvStore abstraction.
 *
 * Each blob is stored as a KvStore entry with key = blob path and value = serialized [KvBlobEntry]
 * containing the data (Base64-encoded) and metadata.
 *
 * Best for small blobs (configs, certificates, small documents) where a separate storage backend
 * is overkill. The backing KvStore can be in-memory (testing) or Kottage (persistent via SQLite).
 *
 * When the KvStore offers optimistic version chains ([KvStoreVersioning]), every blob carries a
 * revision and a put may name the revision it expects ([PutOptions.expectedRevision]). The revision
 * is the head of the blob's version chain; a write first appends the next revision against the head
 * it read, so two writers holding the same revision cannot both succeed.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("KvBlobStore", exact = true)
class KvBlobStore(
    private val kvStore: KvStore,
    private val maxBlobSizeBytes: Long = 0,
) : BlobStore {
    override val schemeId: String = KvBlobStoreConfig.BACKEND_ID

    private val versioning: KvStoreVersioning? = kvStore as? KvStoreVersioning

    override val capabilities: BlobStoreCapabilities =
        BlobStoreCapabilities(
            supportsConditionalWrites = versioning != null,
            supportsRevisions = versioning != null,
            supportsCopy = true,
            supportsMove = true,
            supportsBulkDelete = true,
            supportsListing = true,
            maxBlobSizeBytes =
                if (maxBlobSizeBytes > 0) {
                    maxBlobSizeBytes
                } else {
                    Long.MAX_VALUE
                },
        )

    private val namespace =
        KvNamespace(
            name = NAMESPACE,
            codec = KotlinxSerializationJsonKvCodec(json, KvBlobEntry.serializer()),
        )

    private val revisionNamespace =
        KvNamespace(
            name = REVISION_NAMESPACE,
            codec = KotlinxSerializationJsonKvCodec(json, KvBlobRevision.serializer()),
        )

    /** The blob's current revision, 0 for a blob whose revision was never recorded. */
    private suspend fun currentRevision(path: String): IdkResult<Pair<String?, Long>?, IdkError> {
        val chain = versioning ?: return Ok(null)
        val head = chain.getHead(revisionNamespace, path).getOrElse { return Err(it) }
        return Ok(head?.versionId to (head?.value?.revision ?: 0L))
    }

    /**
     * Records the blob's next revision. With [expectedRevision] the write applies only on that
     * revision; without it the write follows whatever revision is current.
     */
    private suspend fun advanceRevision(
        path: String,
        expectedRevision: Long?,
    ): IdkResult<Long?, IdkError> {
        val chain = versioning ?: return Ok(null)
        repeat(REVISION_ATTEMPTS) {
            val (headId, current) = currentRevision(path).getOrElse { return Err(it) } ?: return Ok(null)
            if (expectedRevision != null && expectedRevision != current) {
                return Err(BlobStoreError.PreconditionFailed("expected revision $expectedRevision, found $current for $path").toIdkError())
            }
            when (chain.append(revisionNamespace, path, headId, KvBlobRevision(current + 1), Duration.INFINITE).getOrElse { return Err(it) }) {
                is KvVersionAppendResult.Applied -> return Ok(current + 1)
                is KvVersionAppendResult.Conflict ->
                    if (expectedRevision != null) {
                        return Err(BlobStoreError.PreconditionFailed("revision of $path changed concurrently").toIdkError())
                    }
            }
        }
        return Err(BlobStoreError.PreconditionFailed("revision of $path kept changing").toIdkError())
    }

    override suspend fun put(
        target: BlobInfo,
        data: ByteArray,
        options: PutOptions,
    ): IdkResult<BlobDescriptor, IdkError> {
        if (options.ifMatch != null || (options.expectedRevision != null && versioning == null)) {
            return Err(BlobStoreError.Unsupported("conditional KV write").toIdkError())
        }
        if (maxBlobSizeBytes > 0 && data.size > maxBlobSizeBytes) {
            return Err(BlobStoreError.QuotaExceeded("Blob size ${data.size} exceeds max $maxBlobSizeBytes bytes").toIdkError())
        }

        val path =
            target.path ?: kotlin.uuid.Uuid
                .random()
                .toString()

        val existingResult = kvStore.get(namespace, path)
        if (existingResult.isOk && existingResult.value != null) {
            if (!options.overwrite) {
                return Err(BlobStoreError.AlreadyExists(path).toIdkError())
            }
            if (options.ifNoneMatch != null) {
                return Err(BlobStoreError.PreconditionFailed("ifNoneMatch condition failed for $path").toIdkError())
            }
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val existing = existingResult.value
        val entry =
            KvBlobEntry(
                data = data.encodeToBase64(),
                metadata = target.toBlobMetadata(),
                sizeBytes = data.size.toLong(),
                createdAtMillis = existing?.createdAtMillis ?: now,
                lastModifiedAtMillis = now,
            )

        val revision = advanceRevision(path, options.expectedRevision).getOrElse { return Err(it) }
        val putResult = kvStore.put(namespace, path, entry, Duration.INFINITE)
        if (putResult.isErr) {
            return Err(putResult.error)
        }

        return Ok(toDescriptor(path, entry, revision))
    }

    override suspend fun get(info: BlobInfo): IdkResult<ResolvedBlobInfo, IdkError> {
        val path = info.path!!
        val getResult = kvStore.get(namespace, path)
        if (getResult.isErr) {
            return Err(getResult.error)
        }
        val entry =
            getResult.value
                ?: return Err(BlobStoreError.NotFound(path).toIdkError())

        val data =
            try {
                entry.data.decodeFromBase64()
            } catch (expected: Exception) {
                return Err(
                    IdkError.fromString(
                        message = "Failed to decode blob data for '$path': ${expected.message}",
                        exception = expected,
                        code = "BLOB_KV_DECODE_FAILED",
                    ),
                )
            }

        val revision = currentRevision(path).getOrElse { return Err(it) }?.second
        val descriptor = toDescriptor(path, entry, revision)
        return Ok(ResolvedBlobInfo.fromContent(info, data, descriptor))
    }

    override suspend fun delete(info: BlobInfo): IdkResult<Boolean, IdkError> {
        versioning?.deleteVersioned(revisionNamespace, info.path!!)?.getOrElse { return Err(it) }
        return kvStore.delete(namespace, info.path!!)
    }

    override suspend fun exists(info: BlobInfo): IdkResult<Boolean, IdkError> = kvStore.exists(namespace, info.path!!)

    override suspend fun stat(info: BlobInfo): IdkResult<BlobDescriptor, IdkError> {
        val path = info.path!!
        val getResult = kvStore.get(namespace, path)
        if (getResult.isErr) {
            return Err(getResult.error)
        }
        val entry =
            getResult.value
                ?: return Err(BlobStoreError.NotFound(path).toIdkError())

        val revision = currentRevision(path).getOrElse { return Err(it) }?.second
        return Ok(toDescriptor(path, entry, revision))
    }

    override suspend fun list(
        info: BlobInfo,
        options: ListOptions,
    ): IdkResult<ListResult, IdkError> {
        val listing =
            kvStore as? KvStoreListing
                ?: return Err(IdkError.fromString(message = "KvStore does not support listing", code = "BLOB_KV_LIST_UNSUPPORTED"))

        val allResult = listing.getAll(namespace)
        if (allResult.isErr) {
            return Err(allResult.error)
        }

        val prefix = options.prefix ?: ""
        val descriptors =
            allResult.value.entries
                .filter { (key, _) -> key.startsWith(prefix) }
                .sortedBy { it.key }
                .take(options.maxResults)
                .map { (key, entry) ->
                    toDescriptor(key, entry)
                }

        val allMatching =
            allResult.value.keys
                .filter { it.startsWith(prefix) }
                .sorted()
        val nextPageToken =
            if (allMatching.size > options.maxResults) {
                allMatching[options.maxResults]
            } else {
                null
            }

        return Ok(ListResult(descriptors = descriptors, nextPageToken = nextPageToken))
    }

    override suspend fun deletePrefix(info: BlobInfo): IdkResult<Int, IdkError> {
        val listing =
            kvStore as? KvStoreListing
                ?: return Err(IdkError.fromString(message = "KvStore does not support listing", code = "BLOB_KV_LIST_UNSUPPORTED"))

        val prefix = info.path ?: ""
        val allResult = listing.listKeys(namespace)
        if (allResult.isErr) {
            return Err(allResult.error)
        }

        var count = 0
        for (key in allResult.value) {
            if (key.startsWith(prefix)) {
                val delResult = kvStore.delete(namespace, key)
                if (delResult.isOk && delResult.value) {
                    count++
                }
            }
        }
        return Ok(count)
    }

    override suspend fun copy(
        source: BlobInfo,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> {
        val sourcePath = source.path!!
        val destPath =
            destination.path ?: kotlin.uuid.Uuid
                .random()
                .toString()

        val getResult = kvStore.get(namespace, sourcePath)
        if (getResult.isErr) {
            return Err(getResult.error)
        }
        val entry =
            getResult.value
                ?: return Err(BlobStoreError.NotFound(sourcePath).toIdkError())

        val now = Clock.System.now().toEpochMilliseconds()
        val newEntry = entry.copy(createdAtMillis = now, lastModifiedAtMillis = now)
        val putResult = kvStore.put(namespace, destPath, newEntry, Duration.INFINITE)
        if (putResult.isErr) {
            return Err(putResult.error)
        }

        return Ok(toDescriptor(destPath, newEntry))
    }

    override suspend fun move(
        source: BlobInfo,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> {
        val sourcePath = source.path!!
        val destPath =
            destination.path ?: kotlin.uuid.Uuid
                .random()
                .toString()

        val getResult = kvStore.get(namespace, sourcePath)
        if (getResult.isErr) {
            return Err(getResult.error)
        }
        val entry =
            getResult.value
                ?: return Err(BlobStoreError.NotFound(sourcePath).toIdkError())

        val now = Clock.System.now().toEpochMilliseconds()
        val movedEntry = entry.copy(lastModifiedAtMillis = now) // Preserve createdAtMillis
        val putResult = kvStore.put(namespace, destPath, movedEntry, Duration.INFINITE)
        if (putResult.isErr) {
            return Err(putResult.error)
        }

        val deleteResult = kvStore.delete(namespace, sourcePath)
        if (deleteResult.isErr) {
            // Log warning but don't fail — the blob was successfully moved
        }

        return Ok(toDescriptor(destPath, movedEntry))
    }

    private fun toDescriptor(
        path: String,
        entry: KvBlobEntry,
        revision: Long? = null,
    ): BlobDescriptor =
        BlobDescriptor(
            path = path,
            storeId = schemeId,
            sizeBytes = entry.sizeBytes,
            contentType = entry.metadata.contentType,
            filename = path.substringAfterLast('/'),
            createdAt = Instant.fromEpochMilliseconds(entry.createdAtMillis),
            lastModified = Instant.fromEpochMilliseconds(entry.lastModifiedAtMillis),
            metadata = entry.metadata,
            contentHash = entry.metadata.contentHash,
            revision = revision,
        )

    companion object {
        const val NAMESPACE = "blob.kv"
        const val REVISION_NAMESPACE = "blob.kv.revision"
        private const val REVISION_ATTEMPTS = 5
        private val json = Json { ignoreUnknownKeys = true }
    }
}
