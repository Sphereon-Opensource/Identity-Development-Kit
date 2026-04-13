package com.sphereon.data.store.blob

/**
 * Registry that resolves [BlobStore] instances by scheme.
 *
 * Uses the set of [BlobStoreFactory] multibindings to find the right factory for a given scheme.
 */
interface BlobStoreRegistry {
    fun resolve(config: BlobStoreConfigBase): BlobStore
    fun availableBackends(): List<String>
}
