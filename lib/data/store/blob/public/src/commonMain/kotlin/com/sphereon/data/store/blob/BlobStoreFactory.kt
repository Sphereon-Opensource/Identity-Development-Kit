package com.sphereon.data.store.blob

import com.sphereon.core.api.context.SessionExecution

/**
 * Factory for creating [BlobStore] instances from configuration.
 *
 * Implementations are registered via DI multibinding (`@ContributesBinding(AppScope, multibinding = true)`).
 * Each factory handles a specific backend type (memory, filesystem, etc.) and receives
 * a typed [BlobStoreConfigBase] deserialized by the polymorphic config binder.
 */
interface BlobStoreFactory {
    val backendId: String

    fun create(config: BlobStoreConfigBase, execution: SessionExecution? = null): BlobStore
}
