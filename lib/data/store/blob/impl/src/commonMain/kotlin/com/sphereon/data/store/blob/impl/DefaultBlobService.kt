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

package com.sphereon.data.store.blob.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.Logger
import com.sphereon.core.events.SessionEventService
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.data.store.blob.BlobByteSource
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobEventCategories
import com.sphereon.data.store.blob.BlobEventSubsystem
import com.sphereon.data.store.blob.BlobEventTypes
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobInfoType
import com.sphereon.data.store.blob.BlobMetadataIndex
import com.sphereon.data.store.blob.BlobReadRange
import com.sphereon.data.store.blob.BlobReadStream
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.BlobStoreCapabilities
import com.sphereon.data.store.blob.BlobStoreError
import com.sphereon.data.store.blob.DeleteOptions
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.ListResult
import com.sphereon.data.store.blob.MetadataSearchQuery
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.ResolvedBlobInfo
import com.sphereon.data.store.blob.RetentionPolicyService
import com.sphereon.data.store.blob.TempUrlOptions
import com.sphereon.data.store.blob.TempUrlPolicy
import com.sphereon.data.store.blob.TempUrlResult
import com.sphereon.data.store.blob.cas.ContentAddress
import com.sphereon.data.store.blob.cas.ContentAddressDescriptor
import com.sphereon.data.store.blob.cas.ContentAddressableBlobStore
import com.sphereon.data.store.blob.impl.cas.DefaultContentAddressableBlobStore
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Default implementation of [BlobService].
 *
 * Blob stores are resolved by **store ID** from [BlobStoreService], which reads
 * per-tenant/principal configuration (e.g., `blob.stores.documents.type=filesystem`).
 * This mirrors the KMS provider pattern.
 *
 * Implements two-tier metadata:
 * - Tier 1: Storage-native metadata from the backend (put/stat)
 * - Tier 2: Application metadata indexed in [BlobMetadataIndex] (KvStore-backed in IDK)
 *
 * All paths are tenant-scoped: `{tenantId}/{path}`
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<BlobService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultBlobService", exact = true)
class DefaultBlobService(
    private val blobStoreService: BlobStoreService,
    private val metadataIndex: BlobMetadataIndex,
    private val retentionPolicyService: RetentionPolicyService,
    private val tempUrlPolicy: TempUrlPolicy,
    private val eventService: SessionEventService,
    private val execution: SessionExecution,
) : BlobService {
    private val log: Logger = execution.log

    private val casCache = mutableMapOf<String, DefaultContentAddressableBlobStore>()

    private fun emitInfoString(info: BlobInfoType): String {
        val path = info.path ?: "<no-path>"
        val store = info.storeId ?: defaultStoreId()
        return "$store://$path"
    }

    private suspend fun emitBlobEvent(
        type: com.sphereon.core.api.events.EventType,
        info: BlobInfoType,
        sizeBytes: Long? = null,
        contentType: String? = null,
        destinationInfo: BlobInfo? = null,
    ) {
        try {
            val event =
                eventService
                    .eventBuilder()
                    .type(type)
                    .origin("blob.service")
                    .subsystem(BlobEventSubsystem.BLOB_STORE)
                    .category(BlobEventCategories.STORAGE)
                    .payload(
                        buildJsonObject {
                            put("ref", emitInfoString(info))
                            put("storeId", info.storeId ?: defaultStoreId())
                            put("path", info.path ?: "")
                            if (sizeBytes != null) {
                                put("sizeBytes", sizeBytes)
                            }
                            if (contentType != null) {
                                put("contentType", contentType)
                            }
                            if (destinationInfo != null) {
                                put("destinationRef", emitInfoString(destinationInfo))
                            }
                        },
                    ).build()
            eventService.emit(event)
        } catch (expected: Exception) {
            log.warn("Failed to emit blob event ${type.value} for ${emitInfoString(info)}: ${expected.message}")
        }
    }

    override fun defaultStoreId(): String {
        val ids = blobStoreService.getStoreIds()
        require(ids.isNotEmpty()) { "No blob stores configured. Add at least one blob store in properties (blob.stores.<id>.type=...)" }
        return ids.first()
    }

    override fun getCapabilities(storeId: String?): BlobStoreCapabilities = resolveStore(storeId).capabilities

    private fun resolveStoreId(storeId: String?): String = storeId ?: defaultStoreId()

    private fun resolveStore(storeId: String?): BlobStore {
        val id = resolveStoreId(storeId)
        return blobStoreService.getStore(id)
    }

    /** Build a tenant-namespaced BlobInfo for the underlying store.
     *
     * Idempotent w.r.t. the tenant prefix: if `info.path` is already
     * `<tenantId>/...` (e.g. it was returned by a previous round-trip
     * through this service via [getBlob] / [storeBlob]), the prefix is not
     * re-applied. This guards against the previously-existing
     * double-prefix bug where callers stored a descriptor whose `path`
     * already included the tenant segment, then handed it back to
     * [getBlob], which produced a `<tenantId>/<tenantId>/...` lookup.
     *
     * For new writes the typical path is the un-prefixed logical key the
     * caller chose (or a generated UUID); for reads the path is whatever
     * the caller cached from a previous `storeBlob` descriptor — and
     * since [storeBlob] now always returns the LOGICAL path, callers
     * round-trip correctly without ever seeing the storage layer's
     * tenant scoping.
     *
     * `storeId` is set to the CONFIGURED registry id (e.g. `"default"`),
     * never the backend scheme id ([BlobStore.schemeId], e.g.
     * `"filesystem"`). The scheme id is not round-trippable: a later
     * `getBlob` would call `resolveStoreId("filesystem")` →
     * `blobStoreService.getStore("filesystem")` → no such configured id →
     * "Blob store config not found".
     */
    private fun tenantScopedInfo(
        info: BlobInfo,
        tenantId: String,
        configuredStoreId: String,
    ): BlobInfo {
        val logicalPath =
            info.path ?: kotlin.uuid.Uuid
                .random()
                .toString()
        val scopedPath = BlobTenantScope.scopePath(logicalPath, tenantId)
        return info.copy(path = scopedPath, storeId = info.storeId ?: configuredStoreId)
    }

    /**
     * THE single boundary that turns a storage-layer descriptor into a
     * caller-facing one. Every value returned from this service flows
     * through here so no descriptor ever leaks a tenant-scoped path or the
     * backend scheme id:
     *
     *  - **Path**: backend stores write under `<tenantId>/<logical>`;
     *    callers must see only the logical path or a later `getBlob`
     *    will re-scope and produce `<tenantId>/<tenantId>/...` (the
     *    historical "double-prefix" bug).
     *
     *  - **storeId**: backend [BlobStore] impls stamp `storeId =
     *    [BlobStore.schemeId]` (e.g. `"filesystem"`), not the configured
     *    registry id. A descriptor carrying the scheme would fail the
     *    next `getBlob` lookup with "Blob store config not found for
     *    store ID: filesystem". Rewriting to the configured id keeps the
     *    round-trip stable.
     *
     * Idempotent on path.
     */
    private fun unscopeForCaller(
        descriptor: BlobDescriptor,
        tenantId: String,
        configuredStoreId: String,
    ): BlobDescriptor {
        val unscopedPath = BlobTenantScope.unscopePath(descriptor.path, tenantId)
        return descriptor.copy(path = unscopedPath, storeId = configuredStoreId)
    }

    /**
     * [unscopeForCaller] applied to a [ResolvedBlobInfo]: normalises both
     * the embedded descriptor and the embedded [BlobInfo] (path + storeId)
     * so the resolved value the caller sees is fully unscoped and carries
     * the configured store id.
     */
    private fun unscopeResolvedForCaller(
        resolved: ResolvedBlobInfo,
        tenantId: String,
        configuredStoreId: String,
    ): ResolvedBlobInfo {
        val unscopedInfoPath =
            resolved.info.path?.let { BlobTenantScope.unscopePath(it, tenantId) }
        return resolved.copy(
            info = resolved.info.copy(path = unscopedInfoPath, storeId = configuredStoreId),
            descriptor = unscopeForCaller(resolved.descriptor, tenantId, configuredStoreId),
        )
    }

    private fun getCas(
        storeId: String?,
        tenantId: String,
    ): ContentAddressableBlobStore {
        val id = resolveStoreId(storeId)
        val key = "$id/$tenantId"
        return casCache
            .getOrPut(key) {
                val store = blobStoreService.getStore(id)
                DefaultContentAddressableBlobStore(
                    blobStore = store,
                    storeId = id,
                    tenantPrefix = tenantId,
                )
            }.also {
                // Simple eviction: if cache grows too large, clear oldest entries
                if (casCache.size > MAX_CAS_CACHE_SIZE) {
                    val keysToRemove = casCache.keys.take(casCache.size - MAX_CAS_CACHE_SIZE)
                    keysToRemove.forEach { k -> casCache.remove(k) }
                }
            }
    }

    // -- Standard CRUD --

    override suspend fun storeBlob(
        target: BlobInfo,
        data: ByteArray,
        options: PutOptions,
    ): IdkResult<BlobDescriptor, IdkError> {
        val tenantId = target.tenantId ?: "default"
        // Configured registry id (e.g. "default"), NOT the backend scheme id ("memory",
        // "filesystem", ...) that the store impl stamps onto descriptors. Used for the metadata
        // index and the caller-facing descriptor so getBlob can resolve the store on round-trip.
        val configuredStoreId = resolveStoreId(target.storeId)
        val store = resolveStore(target.storeId)
        val scopedInfo = tenantScopedInfo(target, tenantId, configuredStoreId)
        log.debug("storeBlob: ${emitInfoString(scopedInfo)} (${data.size} bytes)")

        val digestAlg = options.digestAlgorithm
        val contentHash =
            if (digestAlg != null) {
                val digest = hash(data, digestAlg)
                ContentAddress(algorithm = digestAlg, digest = digest).toMultibaseString()
            } else {
                null
            }

        val putResult = store.put(scopedInfo, data, options)
        if (putResult.isErr) {
            return putResult
        }

        var descriptor = putResult.value
        if (contentHash != null) {
            descriptor = descriptor.copy(contentHash = contentHash)
        }
        val retentionResult = retentionPolicyService.applyRetention(descriptor)
        if (retentionResult.isOk) {
            descriptor = retentionResult.value
        }

        // Index under the CONFIGURED storeId (e.g. "default"), not the backend scheme id the
        // store impl stamps onto the descriptor (e.g. "memory"). The search path returns the
        // indexed descriptor verbatim, and callers round-trip it back through getBlob(storeId=...);
        // a scheme id there fails with "Blob store config not found for store ID: <scheme>".
        // The scoped path is kept so the tenant-scoped pathPrefix search still matches.
        val indexResult = metadataIndex.index(descriptor.copy(storeId = configuredStoreId))
        if (indexResult.isErr) {
            log.warn("Failed to index metadata for ${emitInfoString(scopedInfo)}: ${indexResult.error}")
        }
        emitBlobEvent(BlobEventTypes.BLOB_CREATED, scopedInfo, sizeBytes = descriptor.sizeBytes, contentType = descriptor.contentType)
        // Hand back the LOGICAL path (no tenant prefix) and the CONFIGURED
        // storeId so a later getBlob call can re-scope cleanly without
        // producing `<tenant>/<tenant>/…` and without "Blob store config
        // not found" lookups. Both are storage-layer implementation
        // details that should not leak to callers.
        return Ok(unscopeForCaller(descriptor, tenantId, configuredStoreId = configuredStoreId))
    }

    override suspend fun storeBlobStream(
        target: BlobInfo,
        source: BlobByteSource,
        options: PutOptions,
    ): IdkResult<BlobDescriptor, IdkError> {
        if (options.digestAlgorithm != null) {
            return Err(BlobStoreError.Unsupported("digestAlgorithm with storeBlobStream").toIdkError())
        }
        val tenantId = target.tenantId ?: "default"
        val configuredStoreId = resolveStoreId(target.storeId)
        val store = resolveStore(target.storeId)
        if (!store.capabilities.supportsStreamingWrite) {
            return Err(BlobStoreError.Unsupported("storeBlobStream").toIdkError())
        }
        val scopedInfo = tenantScopedInfo(target, tenantId, configuredStoreId)
        val putResult = store.putStream(scopedInfo, source, options)
        if (putResult.isErr) {
            return putResult
        }

        var descriptor = putResult.value
        val retentionResult = retentionPolicyService.applyRetention(descriptor)
        if (retentionResult.isOk) {
            descriptor = retentionResult.value
        }
        val indexResult = metadataIndex.index(descriptor.copy(storeId = configuredStoreId))
        if (indexResult.isErr) {
            log.warn("Failed to index metadata for ${emitInfoString(scopedInfo)}: ${indexResult.error}")
        }
        emitBlobEvent(BlobEventTypes.BLOB_CREATED, scopedInfo, sizeBytes = descriptor.sizeBytes, contentType = descriptor.contentType)
        return Ok(unscopeForCaller(descriptor, tenantId, configuredStoreId))
    }

    override suspend fun getBlob(info: BlobInfoType): IdkResult<ResolvedBlobInfo, IdkError> {
        if (info is ResolvedBlobInfo) {
            return Ok(info)
        }

        val blobInfo = info.toBlobInfo()
        val tenantId = blobInfo.tenantId ?: "default"
        val path =
            blobInfo.path
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "BlobInfo.path is required for getBlob"))
        val configuredStoreId = resolveStoreId(blobInfo.storeId)
        val store = resolveStore(blobInfo.storeId)
        val scopedInfo = tenantScopedInfo(blobInfo, tenantId, configuredStoreId)
        val getResult = store.get(scopedInfo)
        if (getResult.isErr) {
            return getResult
        }
        return Ok(unscopeResolvedForCaller(getResult.value, tenantId, configuredStoreId))
    }

    override suspend fun openBlobRead(info: BlobInfoType): IdkResult<BlobReadStream, IdkError> {
        val blobInfo = info.toBlobInfo()
        val tenantId = blobInfo.tenantId ?: "default"
        val configuredStoreId = resolveStoreId(blobInfo.storeId)
        val store = resolveStore(blobInfo.storeId)
        if (!store.capabilities.supportsStreamingRead) {
            return Err(BlobStoreError.Unsupported("openBlobRead").toIdkError())
        }
        if (blobInfo.path == null) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "BlobInfo.path is required for openBlobRead"))
        }
        val scopedInfo = tenantScopedInfo(blobInfo, tenantId, configuredStoreId)
        val streamResult = store.openRead(scopedInfo)
        if (streamResult.isErr) {
            return streamResult
        }
        return Ok(
            streamResult.value.copy(
                descriptor = unscopeForCaller(streamResult.value.descriptor, tenantId, configuredStoreId),
            ),
        )
    }

    override suspend fun openBlobReadRange(
        info: BlobInfoType,
        range: BlobReadRange,
    ): IdkResult<BlobReadStream, IdkError> {
        val blobInfo = info.toBlobInfo()
        val tenantId = blobInfo.tenantId ?: "default"
        val configuredStoreId = resolveStoreId(blobInfo.storeId)
        val store = resolveStore(blobInfo.storeId)
        if (!store.capabilities.supportsRangeReads) return Err(BlobStoreError.Unsupported("openBlobReadRange").toIdkError())
        if (blobInfo.path == null) return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "BlobInfo.path is required for openBlobReadRange"))
        val result = store.openReadRange(tenantScopedInfo(blobInfo, tenantId, configuredStoreId), range)
        return if (result.isErr) {
            result
        } else {
            Ok(result.value.copy(descriptor = unscopeForCaller(result.value.descriptor, tenantId, configuredStoreId)))
        }
    }

    override suspend fun verifyBlobIntegrity(
        info: BlobInfoType,
        expected: ContentAddress,
    ): IdkResult<Boolean, IdkError> {
        val blobInfo = info.toBlobInfo()
        val tenantId = blobInfo.tenantId ?: "default"
        val configuredStoreId = resolveStoreId(blobInfo.storeId)
        val store = resolveStore(blobInfo.storeId)
        if (!store.capabilities.supportsIntegrityVerification) return Err(BlobStoreError.Unsupported("verifyBlobIntegrity").toIdkError())
        if (blobInfo.path == null) return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "BlobInfo.path is required for verifyBlobIntegrity"))
        return store.verifyIntegrity(tenantScopedInfo(blobInfo, tenantId, configuredStoreId), expected)
    }

    override suspend fun getBlobInfo(info: BlobInfoType): IdkResult<BlobDescriptor, IdkError> {
        if (info is ResolvedBlobInfo) {
            return Ok(info.descriptor)
        }

        val blobInfo = info.toBlobInfo()
        val tenantId = blobInfo.tenantId ?: "default"
        val path =
            blobInfo.path
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "BlobInfo.path is required for getBlobInfo"))
        val configuredStoreId = resolveStoreId(blobInfo.storeId)
        val store = resolveStore(blobInfo.storeId)
        val scopedInfo = tenantScopedInfo(blobInfo, tenantId, configuredStoreId)

        val statResult = store.stat(scopedInfo)
        if (statResult.isErr) {
            return statResult
        }

        val storageDescriptor = statResult.value

        val indexedResult = metadataIndex.getIndexed(scopedInfo)
        if (indexedResult.isErr || indexedResult.value == null) {
            return Ok(unscopeForCaller(storageDescriptor, tenantId, configuredStoreId))
        }

        val indexed = indexedResult.value!!
        val merged =
            storageDescriptor.copy(
                metadata =
                    storageDescriptor.metadata.copy(
                        custom = indexed.metadata.custom,
                        contentHash = indexed.contentHash ?: storageDescriptor.contentHash,
                        retentionHint = indexed.metadata.retentionHint ?: storageDescriptor.metadata.retentionHint,
                    ),
                contentHash = indexed.contentHash ?: storageDescriptor.contentHash,
            )
        return Ok(unscopeForCaller(merged, tenantId, configuredStoreId))
    }

    override suspend fun deleteBlob(info: BlobInfoType): IdkResult<Boolean, IdkError> {
        val blobInfo = info.toBlobInfo()
        val tenantId = blobInfo.tenantId ?: "default"
        val path =
            blobInfo.path
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "BlobInfo.path is required for deleteBlob"))
        val configuredStoreId = resolveStoreId(blobInfo.storeId)
        val store = resolveStore(blobInfo.storeId)
        val scopedInfo = tenantScopedInfo(blobInfo, tenantId, configuredStoreId)
        log.debug("deleteBlob: ${emitInfoString(scopedInfo)}")

        // Check retention policy before deleting
        val statResult = store.stat(scopedInfo)
        if (statResult.isOk) {
            val descriptor = statResult.value
            val canDeleteResult = retentionPolicyService.canDelete(scopedInfo, descriptor.metadata)
            if (canDeleteResult.isErr) {
                return Err(canDeleteResult.error)
            }
            if (!canDeleteResult.value) {
                log.info("Retention policy denied deletion of ${emitInfoString(scopedInfo)}")
                return Err(BlobStoreError.PermissionDenied("Blob is under retention and cannot be deleted: ${scopedInfo.path}").toIdkError())
            }
        }

        val deleteResult = store.delete(scopedInfo)
        if (deleteResult.isErr) {
            return deleteResult
        }

        val deindexResult = metadataIndex.deindex(scopedInfo)
        if (deindexResult.isErr) {
            log.warn("Failed to deindex metadata for ${emitInfoString(scopedInfo)}: ${deindexResult.error}")
        }
        if (deleteResult.value) {
            emitBlobEvent(BlobEventTypes.BLOB_DELETED, scopedInfo)
        }
        return deleteResult
    }

    override suspend fun deleteBlobConditional(
        info: BlobInfoType,
        options: DeleteOptions,
    ): IdkResult<Boolean, IdkError> {
        val blobInfo = info.toBlobInfo()
        val tenantId = blobInfo.tenantId ?: "default"
        val configuredStoreId = resolveStoreId(blobInfo.storeId)
        val store = resolveStore(blobInfo.storeId)
        if (!store.capabilities.supportsConditionalDelete) {
            return Err(BlobStoreError.Unsupported("deleteBlobConditional").toIdkError())
        }
        if (blobInfo.path == null) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "BlobInfo.path is required for deleteBlobConditional"))
        }
        val scopedInfo = tenantScopedInfo(blobInfo, tenantId, configuredStoreId)
        val statResult = store.stat(scopedInfo)
        if (statResult.isOk) {
            val canDeleteResult = retentionPolicyService.canDelete(scopedInfo, statResult.value.metadata)
            if (canDeleteResult.isErr) {
                return Err(canDeleteResult.error)
            }
            if (!canDeleteResult.value) {
                return Err(BlobStoreError.PermissionDenied("Blob is under retention and cannot be deleted: ${scopedInfo.path}").toIdkError())
            }
        }
        val deleteResult = store.deleteConditional(scopedInfo, options)
        if (deleteResult.isErr) {
            return deleteResult
        }
        val deindexResult = metadataIndex.deindex(scopedInfo)
        if (deindexResult.isErr) {
            log.warn("Failed to deindex metadata for ${emitInfoString(scopedInfo)}: ${deindexResult.error}")
        }
        if (deleteResult.value) {
            emitBlobEvent(BlobEventTypes.BLOB_DELETED, scopedInfo)
        }
        return deleteResult
    }

    override suspend fun listBlobs(
        info: BlobInfo,
        options: ListOptions,
    ): IdkResult<ListResult, IdkError> {
        val tenantId = info.tenantId ?: "default"
        val configuredStoreId = resolveStoreId(info.storeId)
        val store = resolveStore(info.storeId)
        val scopedOptions = options.copy(prefix = BlobTenantScope.scopePrefix(options.prefix, tenantId))
        val scopedInfo = tenantScopedInfo(info, tenantId, configuredStoreId)
        val listResult = store.list(scopedInfo, scopedOptions)
        if (listResult.isErr) {
            return listResult
        }
        val result = listResult.value
        return Ok(
            result.copy(
                descriptors = result.descriptors.map { unscopeForCaller(it, tenantId, configuredStoreId) },
                // commonPrefixes carry the same tenant scope as descriptor paths; unscope them too
                // so the caller never sees the internal "<tenantId>/..." layout (a later getBlob on a
                // returned prefix would otherwise re-scope into "<tenantId>/<tenantId>/...").
                commonPrefixes = result.commonPrefixes.map { BlobTenantScope.unscopePath(it, tenantId) },
            ),
        )
    }

    override suspend fun copyBlob(
        source: BlobInfoType,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> {
        val sourceInfo = source.toBlobInfo()
        val sourceTenantId = sourceInfo.tenantId ?: "default"
        val destTenantId = destination.tenantId ?: "default"
        val sourceConfiguredStoreId = resolveStoreId(sourceInfo.storeId)
        val destConfiguredStoreId = resolveStoreId(destination.storeId)
        val sourceStore = resolveStore(sourceInfo.storeId)
        val destStore = resolveStore(destination.storeId)
        val scopedSource = tenantScopedInfo(sourceInfo, sourceTenantId, sourceConfiguredStoreId)
        val scopedDest = tenantScopedInfo(destination, destTenantId, destConfiguredStoreId)
        log.debug("copyBlob: ${emitInfoString(scopedSource)} -> ${emitInfoString(scopedDest)}")

        // Cross-store copy: get from source, put to destination
        if (sourceConfiguredStoreId != destConfiguredStoreId) {
            val getResult = sourceStore.get(scopedSource)
            if (getResult.isErr) {
                return Err(getResult.error)
            }
            val resolved = getResult.value
            val putResult = destStore.put(scopedDest, resolved.data)
            if (putResult.isErr) {
                return putResult
            }
            val indexResult = metadataIndex.index(putResult.value.copy(storeId = destConfiguredStoreId))
            if (indexResult.isErr) {
                log.warn("Failed to index metadata for ${emitInfoString(scopedDest)}: ${indexResult.error}")
            }
            emitBlobEvent(BlobEventTypes.BLOB_COPIED, scopedSource, sizeBytes = putResult.value.sizeBytes, destinationInfo = scopedDest)
            return Ok(unscopeForCaller(putResult.value, destTenantId, destConfiguredStoreId))
        }

        val copyResult = sourceStore.copy(scopedSource, scopedDest)
        if (copyResult.isErr) {
            return copyResult
        }

        val indexResult = metadataIndex.index(copyResult.value.copy(storeId = destConfiguredStoreId))
        if (indexResult.isErr) {
            log.warn("Failed to index metadata for ${emitInfoString(scopedDest)}: ${indexResult.error}")
        }
        emitBlobEvent(BlobEventTypes.BLOB_COPIED, scopedSource, sizeBytes = copyResult.value.sizeBytes, destinationInfo = scopedDest)
        return Ok(unscopeForCaller(copyResult.value, destTenantId, destConfiguredStoreId))
    }

    override suspend fun moveBlob(
        source: BlobInfoType,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> {
        val sourceInfo = source.toBlobInfo()
        val sourceTenantId = sourceInfo.tenantId ?: "default"
        val destTenantId = destination.tenantId ?: "default"
        val sourceConfiguredStoreId = resolveStoreId(sourceInfo.storeId)
        val destConfiguredStoreId = resolveStoreId(destination.storeId)
        val sourceStore = resolveStore(sourceInfo.storeId)
        val destStore = resolveStore(destination.storeId)
        val scopedSource = tenantScopedInfo(sourceInfo, sourceTenantId, sourceConfiguredStoreId)
        val scopedDest = tenantScopedInfo(destination, destTenantId, destConfiguredStoreId)
        log.debug("moveBlob: ${emitInfoString(scopedSource)} -> ${emitInfoString(scopedDest)}")

        // Cross-store move: copy to destination, then delete from source
        if (sourceConfiguredStoreId != destConfiguredStoreId) {
            val getResult = sourceStore.get(scopedSource)
            if (getResult.isErr) {
                return Err(getResult.error)
            }
            val resolved = getResult.value
            val putResult = destStore.put(scopedDest, resolved.data)
            if (putResult.isErr) {
                return putResult
            }
            val deleteResult = sourceStore.delete(scopedSource)
            if (deleteResult.isErr) {
                log.warn("Cross-store move: destination written but source delete failed: ${deleteResult.error}")
            }
            val deindexResult = metadataIndex.deindex(scopedSource)
            if (deindexResult.isErr) {
                log.warn("Failed to deindex metadata for ${emitInfoString(scopedSource)}: ${deindexResult.error}")
            }
            val indexResult = metadataIndex.index(putResult.value.copy(storeId = destConfiguredStoreId))
            if (indexResult.isErr) {
                log.warn("Failed to index metadata for ${emitInfoString(scopedDest)}: ${indexResult.error}")
            }
            emitBlobEvent(BlobEventTypes.BLOB_MOVED, scopedSource, sizeBytes = putResult.value.sizeBytes, destinationInfo = scopedDest)
            return Ok(unscopeForCaller(putResult.value, destTenantId, destConfiguredStoreId))
        }

        val moveResult = sourceStore.move(scopedSource, scopedDest)
        if (moveResult.isErr) {
            return moveResult
        }

        val deindexResult = metadataIndex.deindex(scopedSource)
        if (deindexResult.isErr) {
            log.warn("Failed to deindex metadata for ${emitInfoString(scopedSource)}: ${deindexResult.error}")
        }
        val indexResult = metadataIndex.index(moveResult.value.copy(storeId = destConfiguredStoreId))
        if (indexResult.isErr) {
            log.warn("Failed to index metadata for ${emitInfoString(scopedDest)}: ${indexResult.error}")
        }
        emitBlobEvent(BlobEventTypes.BLOB_MOVED, scopedSource, sizeBytes = moveResult.value.sizeBytes, destinationInfo = scopedDest)
        return Ok(unscopeForCaller(moveResult.value, destTenantId, destConfiguredStoreId))
    }

    // -- CAS operations --

    override suspend fun casStore(
        info: BlobInfo,
        data: ByteArray,
        algorithm: DigestAlg,
    ): IdkResult<ContentAddressDescriptor, IdkError> {
        val tenantId = info.tenantId ?: "default"
        val configuredStoreId = resolveStoreId(info.storeId)
        val cas = getCas(info.storeId, tenantId)
        val storeResult = cas.store(data, algorithm, info.toBlobMetadata())
        if (storeResult.isErr) {
            return storeResult
        }
        val casDescriptor = storeResult.value
        return Ok(
            casDescriptor.copy(
                descriptor = unscopeForCaller(casDescriptor.descriptor, tenantId, configuredStoreId),
            ),
        )
    }

    override suspend fun casGet(
        info: BlobInfo,
        address: ContentAddress,
    ): IdkResult<ResolvedBlobInfo, IdkError> {
        val tenantId = info.tenantId ?: "default"
        val configuredStoreId = resolveStoreId(info.storeId)
        val cas = getCas(info.storeId, tenantId)
        val retrieveResult = cas.retrieve(address)
        if (retrieveResult.isErr) {
            return retrieveResult
        }
        return Ok(unscopeResolvedForCaller(retrieveResult.value, tenantId, configuredStoreId))
    }

    override suspend fun casVerify(
        info: BlobInfo,
        address: ContentAddress,
    ): IdkResult<Boolean, IdkError> {
        val tenantId = info.tenantId ?: "default"
        val cas = getCas(info.storeId, tenantId)
        return cas.verify(address)
    }

    // -- Metadata search --

    override suspend fun findByMetadata(
        info: BlobInfo,
        query: MetadataSearchQuery,
    ): IdkResult<List<BlobDescriptor>, IdkError> {
        val tenantId = info.tenantId ?: "default"
        val scopedQuery = query.copy(pathPrefix = BlobTenantScope.scopePrefix(query.pathPrefix, tenantId))
        val searchResult = metadataIndex.search(scopedQuery)
        if (searchResult.isErr) return searchResult
        // Hand back caller-facing descriptors: strip the tenant prefix from the path so a later
        // getBlob doesn't re-scope into `<tenant>/<tenant>/...`. storeId was already normalised to
        // the configured id at index time, so descriptor.storeId IS the configured id here.
        return Ok(
            searchResult.value.map { descriptor ->
                unscopeForCaller(descriptor, tenantId, configuredStoreId = descriptor.storeId)
            },
        )
    }

    // -- Temp URLs --

    override suspend fun createTempUrl(
        info: BlobInfoType,
        options: TempUrlOptions,
    ): IdkResult<TempUrlResult, IdkError> {
        val blobInfo = info.toBlobInfo()
        val tenantId = blobInfo.tenantId ?: "default"
        val configuredStoreId = resolveStoreId(blobInfo.storeId)
        val store = resolveStore(blobInfo.storeId)
        val scopedInfo = tenantScopedInfo(blobInfo, tenantId, configuredStoreId)

        // Check policy before creating temp URL
        val policyResult = tempUrlPolicy.evaluate(scopedInfo, options)
        if (policyResult.isErr) {
            return Err(policyResult.error)
        }
        val approvedOptions = policyResult.value

        return store.createTempUrl(scopedInfo, approvedOptions)
    }

    @ContributesTo(SessionScope::class)
    interface Graph {
        val blobService: BlobService
    }

    companion object {
        private const val MAX_CAS_CACHE_SIZE = 100
    }
}
