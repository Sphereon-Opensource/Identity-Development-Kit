package com.sphereon.data.store.blob.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.log.Logger
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobInfoType
import com.sphereon.data.store.blob.BlobMetadataIndex
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.BlobStoreError
import com.sphereon.data.store.blob.BlobEventCategories
import com.sphereon.data.store.blob.BlobEventSubsystem
import com.sphereon.data.store.blob.BlobEventTypes
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
import com.sphereon.core.events.SessionEventService
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
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
            val event = eventService.eventBuilder()
                .type(type)
                .origin("blob.service")
                .subsystem(BlobEventSubsystem.BLOB_STORE)
                .category(BlobEventCategories.STORAGE)
                .payload(buildJsonObject {
                    put("ref", emitInfoString(info))
                    put("storeId", info.storeId ?: defaultStoreId())
                    put("path", info.path ?: "")
                    if (sizeBytes != null) put("sizeBytes", sizeBytes)
                    if (contentType != null) put("contentType", contentType)
                    if (destinationInfo != null) put("destinationRef", emitInfoString(destinationInfo))
                })
                .build()
            eventService.emit(event)
        } catch (e: Exception) {
            log.warn("Failed to emit blob event ${type.value} for ${emitInfoString(info)}: ${e.message}")
        }
    }

    override fun defaultStoreId(): String {
        val ids = blobStoreService.getStoreIds()
        require(ids.isNotEmpty()) { "No blob stores configured. Add at least one blob store in properties (blob.stores.<id>.type=...)" }
        return ids.first()
    }

    private fun resolveStoreId(storeId: String?): String = storeId ?: defaultStoreId()

    private fun resolveStore(storeId: String?): BlobStore {
        val id = resolveStoreId(storeId)
        return blobStoreService.getStore(id)
    }

    /** Build a tenant-namespaced BlobInfo for the underlying store. */
    private fun tenantScopedInfo(info: BlobInfo, tenantId: String, store: BlobStore): BlobInfo {
        val path = info.path ?: kotlin.uuid.Uuid.random().toString()
        return info.copy(path = "$tenantId/$path", storeId = store.storeId)
    }

    private fun getCas(storeId: String?, tenantId: String): ContentAddressableBlobStore {
        val id = resolveStoreId(storeId)
        val key = "$id/$tenantId"
        return casCache.getOrPut(key) {
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
        val store = resolveStore(target.storeId)
        val scopedInfo = tenantScopedInfo(target, tenantId, store)
        log.debug("storeBlob: ${emitInfoString(scopedInfo)} (${data.size} bytes)")

        val digestAlg = options.digestAlgorithm
        val contentHash = if (digestAlg != null) {
            val digest = hash(data, digestAlg)
            ContentAddress(algorithm = digestAlg, digest = digest).toMultibaseString()
        } else null

        val putResult = store.put(scopedInfo, data, options)
        if (putResult.isErr) return putResult

        var descriptor = putResult.value
        if (contentHash != null) {
            descriptor = descriptor.copy(contentHash = contentHash)
        }
        val retentionResult = retentionPolicyService.applyRetention(descriptor)
        if (retentionResult.isOk) {
            descriptor = retentionResult.value
        }

        val indexResult = metadataIndex.index(descriptor)
        if (indexResult.isErr) {
            log.warn("Failed to index metadata for ${emitInfoString(scopedInfo)}: ${indexResult.error}")
        }
        emitBlobEvent(BlobEventTypes.BLOB_CREATED, scopedInfo, sizeBytes = descriptor.sizeBytes, contentType = descriptor.contentType)
        return Ok(descriptor)
    }

    override suspend fun getBlob(
        info: BlobInfoType,
    ): IdkResult<ResolvedBlobInfo, IdkError> {
        if (info is ResolvedBlobInfo) return Ok(info)

        val blobInfo = info.toBlobInfo()
        val tenantId = blobInfo.tenantId ?: "default"
        val path = blobInfo.path
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "BlobInfo.path is required for getBlob"))
        val store = resolveStore(blobInfo.storeId)
        val scopedInfo = tenantScopedInfo(blobInfo, tenantId, store)
        return store.get(scopedInfo)
    }

    override suspend fun getBlobInfo(
        info: BlobInfoType,
    ): IdkResult<BlobDescriptor, IdkError> {
        if (info is ResolvedBlobInfo) return Ok(info.descriptor)

        val blobInfo = info.toBlobInfo()
        val tenantId = blobInfo.tenantId ?: "default"
        val path = blobInfo.path
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "BlobInfo.path is required for getBlobInfo"))
        val store = resolveStore(blobInfo.storeId)
        val scopedInfo = tenantScopedInfo(blobInfo, tenantId, store)

        val statResult = store.stat(scopedInfo)
        if (statResult.isErr) return statResult

        val storageDescriptor = statResult.value

        val indexedResult = metadataIndex.getIndexed(scopedInfo)
        if (indexedResult.isErr || indexedResult.value == null) return Ok(storageDescriptor)

        val indexed = indexedResult.value!!
        return Ok(
            storageDescriptor.copy(
                metadata = storageDescriptor.metadata.copy(
                    custom = indexed.metadata.custom,
                    contentHash = indexed.contentHash ?: storageDescriptor.contentHash,
                    retentionHint = indexed.metadata.retentionHint ?: storageDescriptor.metadata.retentionHint,
                ),
                contentHash = indexed.contentHash ?: storageDescriptor.contentHash,
            )
        )
    }

    override suspend fun deleteBlob(
        info: BlobInfoType,
    ): IdkResult<Boolean, IdkError> {
        val blobInfo = info.toBlobInfo()
        val tenantId = blobInfo.tenantId ?: "default"
        val path = blobInfo.path
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "BlobInfo.path is required for deleteBlob"))
        val store = resolveStore(blobInfo.storeId)
        val scopedInfo = tenantScopedInfo(blobInfo, tenantId, store)
        log.debug("deleteBlob: ${emitInfoString(scopedInfo)}")

        // Check retention policy before deleting
        val statResult = store.stat(scopedInfo)
        if (statResult.isOk) {
            val descriptor = statResult.value
            val canDeleteResult = retentionPolicyService.canDelete(scopedInfo, descriptor.metadata)
            if (canDeleteResult.isErr) return Err(canDeleteResult.error)
            if (!canDeleteResult.value) {
                log.info("Retention policy denied deletion of ${emitInfoString(scopedInfo)}")
                return Err(BlobStoreError.PermissionDenied("Blob is under retention and cannot be deleted: ${scopedInfo.path}").toIdkError())
            }
        }

        val deleteResult = store.delete(scopedInfo)
        if (deleteResult.isErr) return deleteResult

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
        val store = resolveStore(info.storeId)
        val scopedOptions = options.copy(prefix = "$tenantId/${options.prefix ?: ""}")
        val scopedInfo = tenantScopedInfo(info, tenantId, store)
        return store.list(scopedInfo, scopedOptions)
    }

    override suspend fun copyBlob(
        source: BlobInfoType,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> {
        val sourceInfo = source.toBlobInfo()
        val sourceTenantId = sourceInfo.tenantId ?: "default"
        val destTenantId = destination.tenantId ?: "default"
        val sourceStore = resolveStore(sourceInfo.storeId)
        val destStore = resolveStore(destination.storeId)
        val scopedSource = tenantScopedInfo(sourceInfo, sourceTenantId, sourceStore)
        val scopedDest = tenantScopedInfo(destination, destTenantId, destStore)
        log.debug("copyBlob: ${emitInfoString(scopedSource)} -> ${emitInfoString(scopedDest)}")

        // Cross-store copy: get from source, put to destination
        if (resolveStoreId(sourceInfo.storeId) != resolveStoreId(destination.storeId)) {
            val getResult = sourceStore.get(scopedSource)
            if (getResult.isErr) return Err(getResult.error)
            val resolved = getResult.value
            val putResult = destStore.put(scopedDest, resolved.data)
            if (putResult.isErr) return putResult
            val indexResult = metadataIndex.index(putResult.value)
            if (indexResult.isErr) {
                log.warn("Failed to index metadata for ${emitInfoString(scopedDest)}: ${indexResult.error}")
            }
            emitBlobEvent(BlobEventTypes.BLOB_COPIED, scopedSource, sizeBytes = putResult.value.sizeBytes, destinationInfo = scopedDest)
            return putResult
        }

        val copyResult = sourceStore.copy(scopedSource, scopedDest)
        if (copyResult.isErr) return copyResult

        val indexResult = metadataIndex.index(copyResult.value)
        if (indexResult.isErr) {
            log.warn("Failed to index metadata for ${emitInfoString(scopedDest)}: ${indexResult.error}")
        }
        emitBlobEvent(BlobEventTypes.BLOB_COPIED, scopedSource, sizeBytes = copyResult.value.sizeBytes, destinationInfo = scopedDest)
        return copyResult
    }

    override suspend fun moveBlob(
        source: BlobInfoType,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> {
        val sourceInfo = source.toBlobInfo()
        val sourceTenantId = sourceInfo.tenantId ?: "default"
        val destTenantId = destination.tenantId ?: "default"
        val sourceStore = resolveStore(sourceInfo.storeId)
        val destStore = resolveStore(destination.storeId)
        val scopedSource = tenantScopedInfo(sourceInfo, sourceTenantId, sourceStore)
        val scopedDest = tenantScopedInfo(destination, destTenantId, destStore)
        log.debug("moveBlob: ${emitInfoString(scopedSource)} -> ${emitInfoString(scopedDest)}")

        // Cross-store move: copy to destination, then delete from source
        if (resolveStoreId(sourceInfo.storeId) != resolveStoreId(destination.storeId)) {
            val getResult = sourceStore.get(scopedSource)
            if (getResult.isErr) return Err(getResult.error)
            val resolved = getResult.value
            val putResult = destStore.put(scopedDest, resolved.data)
            if (putResult.isErr) return putResult
            val deleteResult = sourceStore.delete(scopedSource)
            if (deleteResult.isErr) {
                log.warn("Cross-store move: destination written but source delete failed: ${deleteResult.error}")
            }
            val deindexResult = metadataIndex.deindex(scopedSource)
            if (deindexResult.isErr) {
                log.warn("Failed to deindex metadata for ${emitInfoString(scopedSource)}: ${deindexResult.error}")
            }
            val indexResult = metadataIndex.index(putResult.value)
            if (indexResult.isErr) {
                log.warn("Failed to index metadata for ${emitInfoString(scopedDest)}: ${indexResult.error}")
            }
            emitBlobEvent(BlobEventTypes.BLOB_MOVED, scopedSource, sizeBytes = putResult.value.sizeBytes, destinationInfo = scopedDest)
            return putResult
        }

        val moveResult = sourceStore.move(scopedSource, scopedDest)
        if (moveResult.isErr) return moveResult

        val deindexResult = metadataIndex.deindex(scopedSource)
        if (deindexResult.isErr) {
            log.warn("Failed to deindex metadata for ${emitInfoString(scopedSource)}: ${deindexResult.error}")
        }
        val indexResult = metadataIndex.index(moveResult.value)
        if (indexResult.isErr) {
            log.warn("Failed to index metadata for ${emitInfoString(scopedDest)}: ${indexResult.error}")
        }
        emitBlobEvent(BlobEventTypes.BLOB_MOVED, scopedSource, sizeBytes = moveResult.value.sizeBytes, destinationInfo = scopedDest)
        return moveResult
    }

    // -- CAS operations --

    override suspend fun casStore(
        info: BlobInfo,
        data: ByteArray,
        algorithm: DigestAlg,
    ): IdkResult<ContentAddressDescriptor, IdkError> {
        val tenantId = info.tenantId ?: "default"
        val cas = getCas(info.storeId, tenantId)
        return cas.store(data, algorithm, info.toBlobMetadata())
    }

    override suspend fun casGet(
        info: BlobInfo,
        address: ContentAddress,
    ): IdkResult<ResolvedBlobInfo, IdkError> {
        val tenantId = info.tenantId ?: "default"
        val cas = getCas(info.storeId, tenantId)
        return cas.retrieve(address)
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
        val scopedQuery = query.copy(pathPrefix = "$tenantId/${query.pathPrefix ?: ""}")
        return metadataIndex.search(scopedQuery)
    }

    // -- Temp URLs --

    override suspend fun createTempUrl(
        info: BlobInfoType,
        options: TempUrlOptions,
    ): IdkResult<TempUrlResult, IdkError> {
        val blobInfo = info.toBlobInfo()
        val tenantId = blobInfo.tenantId ?: "default"
        val store = resolveStore(blobInfo.storeId)
        val scopedInfo = tenantScopedInfo(blobInfo, tenantId, store)

        // Check policy before creating temp URL
        val policyResult = tempUrlPolicy.evaluate(scopedInfo, options)
        if (policyResult.isErr) return Err(policyResult.error)
        val approvedOptions = policyResult.value

        return store.createTempUrl(scopedInfo, approvedOptions)
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("BlobServiceComponent", exact = true)
    @ContributesTo(scope = SessionScope::class)
    interface Component {
        val blobService: BlobService
    }

    companion object {
        private const val MAX_CAS_CACHE_SIZE = 100
    }
}
