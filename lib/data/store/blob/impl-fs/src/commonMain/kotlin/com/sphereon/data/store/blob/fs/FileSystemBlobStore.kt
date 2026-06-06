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

package com.sphereon.data.store.blob.fs

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobMetadata
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.BlobStoreCapabilities
import com.sphereon.data.store.blob.BlobStoreError
import com.sphereon.data.store.blob.BlobStoreSchemes
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.ListResult
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.ResolvedBlobInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Filesystem-backed blob store using okio for KMP filesystem access.
 *
 * Stores tier 1 metadata (contentType) as a `.meta.json` sidecar file.
 * Tier 2 application metadata is handled by [BlobMetadataIndex] (KvStore), not by the filesystem.
 */
class FileSystemBlobStore(
    private val rootDir: String,
    private val autoCreateDirs: Boolean = true,
    private val fileSystem: FileSystem = defaultFileSystem(),
) : BlobStore {
    private val mutex = Mutex()

    override val schemeId: String = BlobStoreSchemes.FILESYSTEM

    override val capabilities: BlobStoreCapabilities = BlobStoreCapabilities.SIMPLE

    private fun resolvePath(info: BlobInfo): Path = rootDir.toPath() / requireNotNull(info.path) { "BlobInfo.path must not be null" }

    private fun metaPath(blobPath: Path): Path = (blobPath.toString() + ".meta.json").toPath()

    override suspend fun putIfAbsent(
        target: BlobInfo,
        data: ByteArray,
    ): IdkResult<BlobDescriptor, IdkError> = put(target, data, PutOptions.NO_OVERWRITE)

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun put(
        target: BlobInfo,
        data: ByteArray,
        options: PutOptions,
    ): IdkResult<BlobDescriptor, IdkError> =
        mutex.withLock {
            try {
                val effectiveInfo =
                    if (target.path == null) {
                        target.copy(path = Uuid.random().toString())
                    } else {
                        target
                    }
                val path = resolvePath(effectiveInfo)

                if (!options.overwrite && fileSystem.exists(path)) {
                    return@withLock Err(BlobStoreError.AlreadyExists(effectiveInfo.path!!).toIdkError())
                }

                if (autoCreateDirs) {
                    path.parent?.let { parent ->
                        fileSystem.createDirectories(parent)
                    }
                }

                fileSystem.sink(path).buffer().use { sink ->
                    sink.write(data)
                }

                val metadata = effectiveInfo.toBlobMetadata()
                if (metadata.contentType != null) {
                    val meta = SidecarMetadata(contentType = metadata.contentType)
                    val metaJson = json.encodeToString(SidecarMetadata.serializer(), meta)
                    fileSystem.sink(metaPath(path)).buffer().use { sink ->
                        sink.writeUtf8(metaJson)
                    }
                }

                val fsMetadata = fileSystem.metadata(path)
                Ok(toDescriptor(effectiveInfo.path!!, fsMetadata, readSidecarMetadata(path), metadata))
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to put blob '${target.path}': ${expected.message}", exception = expected, code = "BLOB_PUT_FAILED"))
            }
        }

    override suspend fun get(info: BlobInfo): IdkResult<ResolvedBlobInfo, IdkError> =
        mutex.withLock {
            try {
                val path = resolvePath(info)
                if (!fileSystem.exists(path)) {
                    return@withLock Err(BlobStoreError.NotFound(info.path!!).toIdkError())
                }

                val data =
                    fileSystem.source(path).buffer().use { source ->
                        source.readByteArray()
                    }

                val sidecar = readSidecarMetadata(path)
                val fsMetadata = fileSystem.metadata(path)
                val descriptor = toDescriptor(info.path!!, fsMetadata, sidecar)

                Ok(ResolvedBlobInfo.fromContent(info, data, descriptor))
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to get blob '${info.path}': ${expected.message}", exception = expected, code = "BLOB_GET_FAILED"))
            }
        }

    override suspend fun delete(info: BlobInfo): IdkResult<Boolean, IdkError> =
        mutex.withLock {
            try {
                val path = resolvePath(info)
                if (!fileSystem.exists(path)) {
                    return@withLock Ok(false)
                }
                fileSystem.delete(path)
                val meta = metaPath(path)
                if (fileSystem.exists(meta)) {
                    fileSystem.delete(meta)
                }
                Ok(true)
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to delete blob '${info.path}': ${expected.message}", exception = expected, code = "BLOB_DELETE_FAILED"))
            }
        }

    override suspend fun exists(info: BlobInfo): IdkResult<Boolean, IdkError> =
        mutex.withLock {
            try {
                Ok(fileSystem.exists(resolvePath(info)))
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to check existence of blob '${info.path}': ${expected.message}", exception = expected, code = "BLOB_EXISTS_FAILED"))
            }
        }

    override suspend fun stat(info: BlobInfo): IdkResult<BlobDescriptor, IdkError> =
        mutex.withLock {
            try {
                val path = resolvePath(info)
                if (!fileSystem.exists(path)) {
                    return@withLock Err(BlobStoreError.NotFound(info.path!!).toIdkError())
                }

                val fsMetadata = fileSystem.metadata(path)
                val sidecar = readSidecarMetadata(path)

                Ok(toDescriptor(info.path!!, fsMetadata, sidecar))
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to stat blob '${info.path}': ${expected.message}", exception = expected, code = "BLOB_STAT_FAILED"))
            }
        }

    override suspend fun list(
        info: BlobInfo,
        options: ListOptions,
    ): IdkResult<ListResult, IdkError> =
        mutex.withLock {
            try {
                val basePath = rootDir.toPath()
                val optPrefix = options.prefix ?: info.path ?: ""
                val prefixPath =
                    if (optPrefix.isNotEmpty()) {
                        basePath / optPrefix
                    } else {
                        basePath
                    }

                if (!fileSystem.exists(prefixPath) && optPrefix.isNotEmpty()) {
                    return@withLock Ok(ListResult(descriptors = emptyList()))
                }

                val searchDir =
                    if (fileSystem.metadata(prefixPath).isDirectory) {
                        prefixPath
                    } else {
                        prefixPath.parent ?: basePath
                    }

                val entries =
                    if (options.recursive) {
                        fileSystem.listRecursively(searchDir).toList()
                    } else {
                        fileSystem.list(searchDir)
                    }

                val descriptors =
                    entries
                        .filter { path ->
                            !path.name.endsWith(".meta.json") &&
                                fileSystem.metadata(path).isRegularFile &&
                                (optPrefix.isEmpty() || relativePathString(path, basePath).startsWith(optPrefix))
                        }.take(options.maxResults)
                        .map { path ->
                            val relativePath = relativePathString(path, basePath)
                            toDescriptor(relativePath, fileSystem.metadata(path), readSidecarMetadata(path))
                        }

                Ok(ListResult(descriptors = descriptors))
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to list blobs: ${expected.message}", exception = expected, code = "BLOB_LIST_FAILED"))
            }
        }

    override suspend fun copy(
        source: BlobInfo,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> =
        mutex.withLock {
            try {
                val srcPath = resolvePath(source)
                val dstPath = resolvePath(destination)

                if (!fileSystem.exists(srcPath)) {
                    return@withLock Err(BlobStoreError.NotFound(source.path!!).toIdkError())
                }

                if (autoCreateDirs) {
                    dstPath.parent?.let { fileSystem.createDirectories(it) }
                }

                fileSystem.copy(srcPath, dstPath)

                val srcMeta = metaPath(srcPath)
                if (fileSystem.exists(srcMeta)) {
                    fileSystem.copy(srcMeta, metaPath(dstPath))
                }

                Ok(toDescriptor(destination.path!!, fileSystem.metadata(dstPath), readSidecarMetadata(dstPath)))
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to copy blob: ${expected.message}", exception = expected, code = "BLOB_COPY_FAILED"))
            }
        }

    override suspend fun move(
        source: BlobInfo,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> =
        mutex.withLock {
            try {
                val srcPath = resolvePath(source)
                val dstPath = resolvePath(destination)

                if (!fileSystem.exists(srcPath)) {
                    return@withLock Err(BlobStoreError.NotFound(source.path!!).toIdkError())
                }

                if (autoCreateDirs) {
                    dstPath.parent?.let { fileSystem.createDirectories(it) }
                }

                fileSystem.atomicMove(srcPath, dstPath)

                val srcMeta = metaPath(srcPath)
                if (fileSystem.exists(srcMeta)) {
                    fileSystem.atomicMove(srcMeta, metaPath(dstPath))
                }

                Ok(toDescriptor(destination.path!!, fileSystem.metadata(dstPath), readSidecarMetadata(dstPath)))
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to move blob: ${expected.message}", exception = expected, code = "BLOB_MOVE_FAILED"))
            }
        }

    override suspend fun deletePrefix(info: BlobInfo): IdkResult<Int, IdkError> =
        mutex.withLock {
            try {
                val prefix =
                    info.path ?: return@withLock Err(
                        IdkError.fromString(message = "deletePrefix requires a path", code = "BLOB_DELETE_PREFIX_FAILED"),
                    )
                val basePath = rootDir.toPath()
                val prefixPath = basePath / prefix

                if (!fileSystem.exists(prefixPath)) {
                    return@withLock Ok(0)
                }

                var count = 0
                fileSystem
                    .listRecursively(prefixPath)
                    .toList()
                    .sortedByDescending { it.toString().length }
                    .forEach { path ->
                        if (fileSystem.metadata(path).isRegularFile) {
                            fileSystem.delete(path)
                            count++
                        }
                    }

                Ok(count)
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to delete prefix '${info.path}': ${expected.message}", exception = expected, code = "BLOB_DELETE_PREFIX_FAILED"))
            }
        }

    private fun readSidecarMetadata(blobPath: Path): SidecarMetadata? {
        val meta = metaPath(blobPath)
        if (!fileSystem.exists(meta)) {
            return null
        }
        return try {
            val content = fileSystem.source(meta).buffer().use { it.readUtf8() }
            json.decodeFromString(SidecarMetadata.serializer(), content)
        } catch (_: Exception) {
            // Ignored: sidecar metadata file could not be parsed
            null
        }
    }

    private fun toDescriptor(
        path: String,
        fsMetadata: okio.FileMetadata,
        sidecar: SidecarMetadata?,
        metadata: BlobMetadata? = null,
    ): BlobDescriptor {
        val effectiveMetadata = metadata ?: BlobMetadata(contentType = sidecar?.contentType)
        return BlobDescriptor(
            path = path,
            storeId = schemeId,
            sizeBytes = fsMetadata.size ?: 0L,
            contentType = sidecar?.contentType ?: effectiveMetadata.contentType,
            filename = path.substringAfterLast('/'),
            createdAt = fsMetadata.createdAtMillis?.let { Instant.fromEpochMilliseconds(it) },
            lastModified = fsMetadata.lastModifiedAtMillis?.let { Instant.fromEpochMilliseconds(it) },
            metadata = effectiveMetadata,
        )
    }

    private fun relativePathString(
        path: Path,
        base: Path,
    ): String {
        val baseStr = base.toString().replace('\\', '/')
        val pathStr = path.toString().replace('\\', '/')
        return pathStr.removePrefix(baseStr).removePrefix("/")
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
internal data class SidecarMetadata(
    val contentType: String? = null,
)
