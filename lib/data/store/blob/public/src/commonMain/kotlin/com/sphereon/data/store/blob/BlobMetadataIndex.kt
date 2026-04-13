package com.sphereon.data.store.blob

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Interface for indexing and searching blob metadata.
 *
 * IDK provides a KvStore-backed implementation (O(n) list-and-filter).
 * EDK replaces it with PostgreSQL (GIN JSONB, proper SQL queries) or Elasticsearch.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlobMetadataIndex", exact = true)
interface BlobMetadataIndex {

    /**
     * Index a blob descriptor for later search.
     */
    suspend fun index(descriptor: BlobDescriptor): IdkResult<Unit, IdkError>

    /**
     * Remove a blob descriptor from the index.
     */
    suspend fun deindex(info: BlobInfo): IdkResult<Boolean, IdkError>

    /**
     * Get an indexed descriptor by its reference.
     */
    suspend fun getIndexed(info: BlobInfo): IdkResult<BlobDescriptor?, IdkError>

    /**
     * Search indexed descriptors by criteria.
     */
    suspend fun search(query: MetadataSearchQuery): IdkResult<List<BlobDescriptor>, IdkError>

    /**
     * Find descriptors by content hash.
     */
    suspend fun findByContentHash(contentHash: String): IdkResult<List<BlobDescriptor>, IdkError>
}

/**
 * Query criteria for metadata search.
 */
@kotlinx.serialization.Serializable
data class MetadataSearchQuery(
    val contentType: String? = null,
    val customMetadata: Map<String, String> = emptyMap(),
    val pathPrefix: String? = null,
    val maxResults: Int = 100,
)
