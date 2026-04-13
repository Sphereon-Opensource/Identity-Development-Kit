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
