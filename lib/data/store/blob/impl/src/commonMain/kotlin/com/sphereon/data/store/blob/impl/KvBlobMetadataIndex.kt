package com.sphereon.data.store.blob.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobMetadataIndex
import com.sphereon.data.store.blob.MetadataSearchQuery
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreListing
import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.impl.KvStoreService
import com.sphereon.di.session.SessionScope
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Duration

/**
 * KvStore-backed metadata index.
 *
 * This is a functional but limited implementation suitable for development and small deployments.
 * Search is O(n) list-and-filter. EDK replaces this with PostgreSQL (GIN JSONB) or Elasticsearch
 * via `@ContributesBinding(replaces = [KvBlobMetadataIndex::class])`.
 *
 * The KvStore is resolved from [KvStoreService] using the well-known store ID [STORE_ID].
 * Configure it in properties:
 * ```
 * kv.stores.blob.metadata.type=memory
 * kv.stores.blob.metadata.scopeBinding=TENANT
 * ```
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<BlobMetadataIndex>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("KvBlobMetadataIndex", exact = true)
class KvBlobMetadataIndex(
    kvStoreService: KvStoreService,
) : BlobMetadataIndex {

    private val kvStore: KvStore by lazy { kvStoreService.getStore(STORE_ID) }

    private val namespace = KvNamespace(
        name = NAMESPACE_NAME,
        codec = KotlinxSerializationJsonKvCodec(json, BlobDescriptor.serializer()),
    )

    private fun infoToKey(info: BlobInfo): String {
        val storeId = info.storeId ?: "default"
        val path = info.path ?: ""
        return "$storeId://$path"
    }

    override suspend fun index(descriptor: BlobDescriptor): IdkResult<Unit, IdkError> {
        val key = "${descriptor.storeId}://${descriptor.path}"
        val result = kvStore.put(namespace, key, descriptor, Duration.INFINITE)
        return result.map { }
    }

    override suspend fun deindex(info: BlobInfo): IdkResult<Boolean, IdkError> {
        val key = infoToKey(info)
        return kvStore.delete(namespace, key)
    }

    override suspend fun getIndexed(info: BlobInfo): IdkResult<BlobDescriptor?, IdkError> {
        val key = infoToKey(info)
        return kvStore.get(namespace, key)
    }

    override suspend fun search(query: MetadataSearchQuery): IdkResult<List<BlobDescriptor>, IdkError> {
        val listing = kvStore as? KvStoreListing
            ?: return Err(IdkError.fromString(message = "KvStore does not support listing; metadata search unavailable", code = "BLOB_METADATA_INDEX_UNSUPPORTED"))

        val allResult = listing.getAll(namespace)
        if (allResult.isErr) return Err(allResult.error)

        val all = allResult.value
        val filtered = all.values.filter { descriptor ->
            matchesQuery(descriptor, query)
        }.take(query.maxResults)

        return Ok(filtered)
    }

    override suspend fun findByContentHash(contentHash: String): IdkResult<List<BlobDescriptor>, IdkError> {
        val listing = kvStore as? KvStoreListing
            ?: return Err(IdkError.fromString(message = "KvStore does not support listing; findByContentHash unavailable", code = "BLOB_METADATA_INDEX_UNSUPPORTED"))

        val allResult = listing.getAll(namespace)
        if (allResult.isErr) return Err(allResult.error)

        val matches = allResult.value.values.filter { it.contentHash == contentHash }
        return Ok(matches)
    }

    private fun matchesQuery(descriptor: BlobDescriptor, query: MetadataSearchQuery): Boolean {
        if (query.contentType != null && descriptor.contentType != query.contentType) return false
        val prefix = query.pathPrefix
        if (prefix != null && !descriptor.path.startsWith(prefix)) return false
        if (query.customMetadata.isNotEmpty()) {
            for ((key, value) in query.customMetadata) {
                if (descriptor.metadata.custom[key] != value) return false
            }
        }
        return true
    }

    companion object {
        const val STORE_ID = "blob.metadata"
        const val NAMESPACE_NAME = "blob.metadata"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
