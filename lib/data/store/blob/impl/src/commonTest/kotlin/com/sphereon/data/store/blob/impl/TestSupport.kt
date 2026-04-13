package com.sphereon.data.store.blob.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.events.Event
import com.sphereon.core.events.EventBuilder
import com.sphereon.core.events.EventHub
import com.sphereon.core.events.SessionEventService
import com.sphereon.core.events.UserEventService
import com.sphereon.core.events.impl.DefaultEventBuilder
import com.sphereon.core.events.impl.EventHubImpl
import com.sphereon.core.events.EventContext
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.BlobStoreConfigBase
import com.sphereon.data.store.blob.InMemoryBlobStoreConfig
import com.sphereon.data.store.blob.memory.InMemoryBlobBackingStorageImpl
import com.sphereon.data.store.blob.memory.InMemoryBlobStoreFactoryImpl
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.impl.KvStoreService
import com.sphereon.data.store.kv.memory.InMemoryKvBackingStorageImpl
import com.sphereon.data.store.kv.memory.InMemoryKvStoreFactoryImpl

/**
 * Test-only [BlobStoreService] that resolves all store IDs to the same in-memory blob store.
 */
class TestBlobStoreService(
    private val store: BlobStore,
    private val storeId: String = "memory",
) : BlobStoreService {
    override fun getStoreIds(): Array<String> = arrayOf(storeId)
    override fun getStoreConfig(storeId: String): BlobStoreConfigBase =
        InMemoryBlobStoreConfig(id = storeId)
    override fun getStore(storeId: String): BlobStore = store
}

/**
 * Test-only [KvStoreService] that resolves all store IDs to the same in-memory KV store.
 */
class TestKvStoreService(
    private val store: KvStore,
) : KvStoreService {
    override fun getStoreIds(): Array<String> = arrayOf(KvBlobMetadataIndex.STORE_ID)
    override fun getStoreConfig(storeId: String): KvStoreConfigBase =
        InMemoryKvStoreConfig(id = storeId, scopeBinding = KvStoreScopeBinding.APP)
    override fun getStore(storeId: String): KvStore = store
}

/**
 * No-op [SessionLogService] for unit tests. Same pattern as CommandBackedHttpAdapterTest.
 */
private class NoOpSessionLogService(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionLogService {
    override val id: String = "test-blob-log"
    override val isEnabled: Boolean = false
    override val scope = com.sphereon.core.api.context.IdkScope.SESSION
    override val logManager: SessionLogManager
        get() = throw NotImplementedError("Not needed for unit tests")
    override suspend fun setConfig(config: LoggerConfig): com.sphereon.core.api.log.LogService = this
    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)
    override fun toAsync(): AsyncLogService = throw NotImplementedError("Not needed for unit tests")
}

/**
 * Minimal [SessionExecution] for unit tests. Same pattern as CommandBackedHttpAdapterTest.
 */
class TestSessionExecution(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionExecution {
    override val sessionContextManager: SessionContextManager
        get() = throw NotImplementedError("Not needed for unit tests")
    override val log: SessionLogService = NoOpSessionLogService(sessionContext)
    override val conf: ContextConfig
        get() = throw NotImplementedError("Not needed for unit tests")
}

/**
 * No-op [SessionEventService] for unit tests.
 * Events are silently dropped (DefaultBlobService wraps emission in try/catch anyway).
 */
class TestSessionEventService : SessionEventService {
    private val hub = EventHubImpl()
    override val scope: IdkScope = IdkScope.SESSION
    override val eventHub: EventHub = hub
    override val parent: UserEventService get() = throw NotImplementedError("Not needed for unit tests")
    override val sessionContext: SessionContext = NoOpSessionContext

    override suspend fun emit(event: Event) {
        hub.publish(event)
    }

    override suspend fun emit(
        event: Event,
        sign: Boolean,
        encrypt: Boolean,
        keyAlias: String?,
        encryptionKeyAlias: String?,
        encryptParts: Set<com.sphereon.core.events.EncryptedPart>,
    ) {
        hub.publish(event)
    }

    override fun eventBuilder(): EventBuilder =
        DefaultEventBuilder(IdkScope.SESSION)
}

/**
 * Creates a test [DefaultBlobService] backed by in-memory stores.
 */
fun createTestBlobService(): DefaultBlobService {
    val blobBackingStorage = InMemoryBlobBackingStorageImpl()
    val blobFactory = InMemoryBlobStoreFactoryImpl(blobBackingStorage)
    val blobConfig = InMemoryBlobStoreConfig(id = "memory")
    val memoryStore = blobFactory.create(blobConfig)

    val kvBackingStorage = InMemoryKvBackingStorageImpl()
    val kvFactory = InMemoryKvStoreFactoryImpl(kvBackingStorage)
    val kvConfig = InMemoryKvStoreConfig(id = KvBlobMetadataIndex.STORE_ID, scopeBinding = KvStoreScopeBinding.APP)
    val kvStore = kvFactory.create(kvConfig)

    val kvStoreService = TestKvStoreService(kvStore)
    val metadataIndex = KvBlobMetadataIndex(kvStoreService)
    val blobStoreService = TestBlobStoreService(memoryStore)

    return DefaultBlobService(
        blobStoreService = blobStoreService,
        metadataIndex = metadataIndex,
        retentionPolicyService = DefaultRetentionPolicyService(),
        tempUrlPolicy = com.sphereon.data.store.blob.DefaultTempUrlPolicy(),
        eventService = TestSessionEventService(),
        execution = TestSessionExecution(),
    )
}
