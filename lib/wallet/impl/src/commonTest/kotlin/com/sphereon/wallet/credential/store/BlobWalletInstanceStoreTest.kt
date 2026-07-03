/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.wallet.credential.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.InMemoryBlobStoreConfig
import com.sphereon.data.store.blob.impl.BlobStoreService
import com.sphereon.data.store.blob.impl.DefaultBlobService
import com.sphereon.data.store.blob.impl.DefaultRetentionPolicyService
import com.sphereon.data.store.blob.impl.KvBlobMetadataIndex
import com.sphereon.data.store.blob.memory.InMemoryBlobBackingStorageImpl
import com.sphereon.data.store.blob.memory.InMemoryBlobStoreFactoryImpl
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.memory.InMemoryKvBackingStorageImpl
import com.sphereon.data.store.kv.memory.InMemoryKvStoreFactoryImpl
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.wallet.credential.IdentifierRef
import com.sphereon.wallet.credential.StorageProfile
import com.sphereon.wallet.credential.StoreRef
import com.sphereon.wallet.credential.WalletInstance
import com.sphereon.wallet.credential.WalletInstancePurpose
import com.sphereon.wallet.credential.WalletStorageMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val INSTANCE_WALLET_A = "wallet-instance-a"
private const val INSTANCE_WALLET_B = "wallet-instance-b"
private val INSTANCE_NOW = Instant.fromEpochSeconds(1_800_000_000)

private class WalletInstanceBlobStoreService(
    private val store: com.sphereon.data.store.blob.BlobStore,
) : BlobStoreService {
    override fun getStoreIds(): Array<String> = arrayOf("memory")

    override fun getStoreConfig(storeId: String) = InMemoryBlobStoreConfig(id = storeId)

    override fun getStore(storeId: String) = store
}

private class WalletInstanceKvStoreService(
    private val store: com.sphereon.data.store.kv.KvStore,
) : com.sphereon.data.store.kv.impl.KvStoreService {
    override fun getStoreIds(): Array<String> = arrayOf(KvBlobMetadataIndex.STORE_ID)

    override fun getStoreConfig(storeId: String) = InMemoryKvStoreConfig(id = storeId, scopeBinding = KvStoreScopeBinding.APP)

    override fun getStore(storeId: String) = store
}

private class WalletInstanceSessionExecution : com.sphereon.core.api.context.SessionExecution {
    override val sessionContext: com.sphereon.di.session.SessionContext = com.sphereon.di.context.NoOpSessionContext
    override val sessionContextManager: com.sphereon.di.session.SessionContextManager
        get() = throw NotImplementedError("Not needed for unit tests")
    override val log: com.sphereon.core.api.log.SessionLogService = WalletInstanceNoOpLogService(sessionContext)
    override val conf: com.sphereon.core.api.context.ContextConfig
        get() = throw NotImplementedError("Not needed for unit tests")
}

private class WalletInstanceNoOpLogService(
    override val sessionContext: com.sphereon.di.session.SessionContext,
) : com.sphereon.core.api.log.SessionLogService {
    override val id: String = "wallet-instance-store-test-log"
    override val isEnabled: Boolean = false
    override val scope = com.sphereon.core.api.context.IdkScope.SESSION
    override val logManager: com.sphereon.core.api.log.SessionLogManager
        get() = throw NotImplementedError("Not needed for unit tests")

    override suspend fun setConfig(config: com.sphereon.core.api.log.LoggerConfig): com.sphereon.core.api.log.LogService = this

    override fun executeAsync(message: com.sphereon.core.api.log.LogMessage): IdkResult<Unit, com.sphereon.core.api.error.IdkErrorType> = Ok(Unit)

    override fun toAsync(): com.sphereon.core.api.log.AsyncLogService = throw NotImplementedError("Not needed for unit tests")
}

private class WalletInstanceEventService : com.sphereon.core.events.SessionEventService {
    private val hub =
        com.sphereon.core.events.impl
            .EventHubImpl()
    override val scope: com.sphereon.core.api.context.IdkScope = com.sphereon.core.api.context.IdkScope.SESSION
    override val eventHub: com.sphereon.core.events.EventHub = hub
    override val parent: com.sphereon.core.events.UserEventService get() = throw NotImplementedError("Not needed for unit tests")
    override val sessionContext: com.sphereon.di.session.SessionContext = com.sphereon.di.context.NoOpSessionContext

    override suspend fun emit(event: com.sphereon.core.events.Event) {
        hub.publish(event)
    }

    override suspend fun emit(
        event: com.sphereon.core.events.Event,
        sign: Boolean,
        encrypt: Boolean,
        keyAlias: String?,
        encryptionKeyAlias: String?,
        encryptParts: Set<com.sphereon.core.events.EncryptedPart>,
    ) {
        hub.publish(event)
    }

    override fun eventBuilder(): com.sphereon.core.events.EventBuilder =
        com.sphereon.core.events.impl
            .DefaultEventBuilder(com.sphereon.core.api.context.IdkScope.SESSION)
}

private fun createWalletInstanceBlobService(): DefaultBlobService {
    val blobBackingStorage = InMemoryBlobBackingStorageImpl()
    val blobFactory = InMemoryBlobStoreFactoryImpl(blobBackingStorage)
    val memoryStore = blobFactory.create(InMemoryBlobStoreConfig(id = "memory"))

    val kvBackingStorage = InMemoryKvBackingStorageImpl()
    val kvFactory = InMemoryKvStoreFactoryImpl(kvBackingStorage)
    val kvStore = kvFactory.create(InMemoryKvStoreConfig(id = KvBlobMetadataIndex.STORE_ID, scopeBinding = KvStoreScopeBinding.APP))

    return DefaultBlobService(
        blobStoreService = WalletInstanceBlobStoreService(memoryStore),
        metadataIndex = KvBlobMetadataIndex(WalletInstanceKvStoreService(kvStore)),
        retentionPolicyService = DefaultRetentionPolicyService(),
        tempUrlPolicy =
            com.sphereon.data.store.blob
                .DefaultTempUrlPolicy(),
        eventService = WalletInstanceEventService(),
        execution = WalletInstanceSessionExecution(),
    )
}

class BlobWalletInstanceStoreTest {
    @Test
    fun putWalletInstancePersistsStorageProfileAndResolverUsesIt() =
        runTest {
            val store = BlobWalletInstanceStore(createWalletInstanceBlobService())
            val instance = walletInstance(INSTANCE_WALLET_A, WalletInstancePurpose.WORK, storageProfileId = "work-hybrid")
            val profile =
                StorageProfile(
                    id = "work-hybrid",
                    walletInstanceId = INSTANCE_WALLET_A,
                    mode = WalletStorageMode.HYBRID,
                    localStoreRef = StoreRef(id = "local-blob", type = "blob"),
                    remoteVaultRef = StoreRef(id = "work-vault", type = "vault"),
                    encryptionPolicyId = "work-managed-encryption",
                    syncPolicyId = "work-sync",
                )

            val put = store.putWalletInstance(instance, profile)

            assertTrue(put.isOk)
            assertEquals(instance, store.getWalletInstance(INSTANCE_WALLET_A).value)
            assertEquals(profile, store.getStorageProfile(INSTANCE_WALLET_A).value)
            assertEquals(profile, store.resolveStorageProfile(INSTANCE_WALLET_A).value)
        }

    @Test
    fun resolveStorageProfileCreatesStandaloneLocalBlobProfileWhenMissing() =
        runTest {
            val store = BlobWalletInstanceStore(createWalletInstanceBlobService())

            val profile = store.resolveStorageProfile(INSTANCE_WALLET_A).value

            assertEquals(INSTANCE_WALLET_A, profile.walletInstanceId)
            assertEquals(WalletStorageMode.LOCAL, profile.mode)
            assertEquals(StoreRef(id = "memory", type = "blob"), profile.localStoreRef)
            assertNotNull(store.getWalletInstance(INSTANCE_WALLET_A).value)
            assertEquals(profile, store.getStorageProfile(INSTANCE_WALLET_A).value)
        }

    @Test
    fun archiveWalletInstanceHidesItFromDefaultListButPreservesProfile() =
        runTest {
            val store = BlobWalletInstanceStore(createWalletInstanceBlobService())
            store.resolveStorageProfile(INSTANCE_WALLET_A)
            store.resolveStorageProfile(INSTANCE_WALLET_B)

            val archived = store.archiveWalletInstance(INSTANCE_WALLET_A, INSTANCE_NOW).value

            assertEquals(INSTANCE_NOW, archived?.archivedAt)
            assertEquals(listOf(INSTANCE_WALLET_B), store.listWalletInstances().value.map { it.id })
            assertEquals(
                setOf(INSTANCE_WALLET_A, INSTANCE_WALLET_B),
                store
                    .listWalletInstances(includeArchived = true)
                    .value
                    .map { it.id }
                    .toSet()
            )
            assertEquals(INSTANCE_WALLET_A, store.getStorageProfile(INSTANCE_WALLET_A).value?.walletInstanceId)
        }

    @Test
    fun putWalletInstanceRejectsMismatchedStorageProfile() =
        runTest {
            val store = BlobWalletInstanceStore(createWalletInstanceBlobService())
            val instance = walletInstance(INSTANCE_WALLET_A, WalletInstancePurpose.PERSONAL, storageProfileId = "profile-a")
            val profile =
                StorageProfile(
                    id = "profile-a",
                    walletInstanceId = INSTANCE_WALLET_B,
                    mode = WalletStorageMode.LOCAL,
                    localStoreRef = StoreRef(id = "memory", type = "blob"),
                    encryptionPolicyId = "wallet-local-default",
                )

            val result = store.putWalletInstance(instance, profile)

            assertTrue(result.isErr)
            assertNull(store.getWalletInstance(INSTANCE_WALLET_A).value)
        }

    private fun walletInstance(
        id: String,
        purpose: WalletInstancePurpose,
        storageProfileId: String,
    ): WalletInstance =
        WalletInstance(
            id = id,
            ownerSubjectRef = IdentifierRef(type = IdentifierType.DID, value = "did:example:owner:$id"),
            label = id,
            purpose = purpose,
            storageProfileId = storageProfileId,
            defaultHolderKeyPolicyId = "holder-key-default",
            createdAt = INSTANCE_NOW,
            updatedAt = INSTANCE_NOW,
        )
}
