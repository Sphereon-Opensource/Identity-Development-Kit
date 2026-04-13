package com.sphereon.data.store.blob

import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * Declares the capabilities of a blob store backend.
 */
@Serializable
data class BlobStoreCapabilities(
    val supportsEtag: Boolean = false,
    val supportsCopy: Boolean = false,
    val supportsMove: Boolean = false,
    val supportsBulkDelete: Boolean = false,
    val supportsTempUrls: Boolean = false,
    val supportsListing: Boolean = true,
    val supportsMetadata: Boolean = true,
    val maxBlobSizeBytes: Long = Long.MAX_VALUE,
    val maxTempUrlDuration: Duration? = null,
) {
    companion object {
        val MINIMAL = BlobStoreCapabilities()

        val SIMPLE = BlobStoreCapabilities(
            supportsCopy = true,
            supportsMove = true,
            supportsBulkDelete = true,
        )

        val CLOUD_OBJECT_STORE = BlobStoreCapabilities(
            supportsEtag = true,
            supportsCopy = true,
            supportsMove = true,
            supportsBulkDelete = true,
            supportsTempUrls = true,
        )
    }
}
