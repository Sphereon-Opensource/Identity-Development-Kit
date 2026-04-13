package com.sphereon.data.store.blob

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Descriptor for a stored blob, returned by put/stat/list operations.
 */
@Serializable
data class BlobDescriptor(
    val path: String,
    val storeId: String,
    val sizeBytes: Long,
    val contentType: String? = null,
    val filename: String? = null,
    val etag: String? = null,
    val createdAt: Instant? = null,
    val lastModified: Instant? = null,
    val metadata: BlobMetadata = BlobMetadata.EMPTY,
    val contentHash: String? = null,
)
