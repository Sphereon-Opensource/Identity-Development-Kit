package com.sphereon.data.store.blob

import kotlinx.serialization.Serializable

/**
 * Per-tenant indexing configuration. EDK enforces this; IDK defines the model.
 */
@Serializable
data class IndexingPolicy(
    val enabled: Boolean = true,
    val indexContentHash: Boolean = true,
    val indexCustomMetadata: Boolean = true,
)
