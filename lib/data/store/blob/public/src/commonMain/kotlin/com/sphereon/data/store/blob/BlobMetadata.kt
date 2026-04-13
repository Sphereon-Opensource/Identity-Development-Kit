package com.sphereon.data.store.blob

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Hint for retention policy enforcement. IDK defines the model; EDK provides jurisdiction-aware enforcement.
 */
@Serializable
data class RetentionHint(
    val retainUntil: Instant? = null,
    val legalHold: Boolean = false,
    val policy: String? = null,
)

/**
 * Metadata associated with a blob.
 *
 * Tier 1 (storage-native): contentType, contentEncoding, contentDisposition — stored by the backend.
 * Tier 2 (application): custom map, contentHash, retentionHint — indexed in BlobMetadataIndex.
 */
@Serializable
data class BlobMetadata(
    val contentType: String? = null,
    val contentEncoding: String? = null,
    val contentDisposition: String? = null,
    val custom: Map<String, String> = emptyMap(),
    val contentHash: String? = null,
    val retentionHint: RetentionHint? = null,
) {
    companion object {
        val EMPTY = BlobMetadata()

        fun ofContentType(contentType: String) = BlobMetadata(contentType = contentType)
    }
}
