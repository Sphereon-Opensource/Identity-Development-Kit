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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobInfoType
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.InMemoryBlobStoreConfig
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.ResolvedBlobInfo
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
import com.sphereon.wallet.credential.BodyStorageKind
import com.sphereon.wallet.credential.BodyStorageRef
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.credential.CredentialInstance
import com.sphereon.wallet.credential.CredentialLifecycleState
import com.sphereon.wallet.credential.CredentialMetadata
import com.sphereon.wallet.credential.CredentialMetadataFilter
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.CredentialRefreshMethod
import com.sphereon.wallet.credential.CredentialTypeRef
import com.sphereon.wallet.credential.CredentialTypeRefKind
import com.sphereon.wallet.credential.CredentialTypeRefSource
import com.sphereon.wallet.credential.CredentialValidityWindow
import com.sphereon.wallet.credential.HybridWalletCredentialStoreDelegate
import com.sphereon.wallet.credential.HybridWalletIssuanceSessionStoreDelegate
import com.sphereon.wallet.credential.IdentifierRef
import com.sphereon.wallet.credential.IssuanceProvenance
import com.sphereon.wallet.credential.IssuanceSession
import com.sphereon.wallet.credential.IssuanceSessionStatus
import com.sphereon.wallet.credential.KeyRef
import com.sphereon.wallet.credential.LocalWalletCredentialStore
import com.sphereon.wallet.credential.LocalWalletIssuanceSessionStore
import com.sphereon.wallet.credential.RecordSyncState
import com.sphereon.wallet.credential.RefreshPolicy
import com.sphereon.wallet.credential.RefreshState
import com.sphereon.wallet.credential.RemoteWalletCredentialStore
import com.sphereon.wallet.credential.RemoteWalletIssuanceSessionStore
import com.sphereon.wallet.credential.SecretRef
import com.sphereon.wallet.credential.StorageProfile
import com.sphereon.wallet.credential.StoreRef
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.credential.WalletDeferredAccessTokenRemoteMirrorOperation
import com.sphereon.wallet.credential.WalletDeferredAccessTokenRemoteMirrorPolicy
import com.sphereon.wallet.credential.WalletDeferredAccessTokenRemoteMirrorRequest
import com.sphereon.wallet.credential.WalletInstance
import com.sphereon.wallet.credential.WalletInstancePurpose
import com.sphereon.wallet.credential.WalletOperation
import com.sphereon.wallet.credential.WalletOperationType
import com.sphereon.wallet.credential.WalletStorageMode
import com.sphereon.wallet.credential.WalletStorageProfileResolver
import com.sphereon.wallet.credential.walletCredentialInstanceBodyPath
import com.sphereon.wallet.credential.walletCredentialMetadataPath
import com.sphereon.wallet.credential.walletCredentialRecordEnvelopePath
import com.sphereon.wallet.credential.walletDeferredAccessTokenPath
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val WALLET_A = "wallet-a"
private const val WALLET_B = "wallet-b"
private val NOW = Instant.fromEpochSeconds(1_800_000_000)
private val testJson = Json { ignoreUnknownKeys = true }

private class TestBlobStoreService(
    private val store: com.sphereon.data.store.blob.BlobStore,
    private val storeId: String = "memory",
) : BlobStoreService {
    override fun getStoreIds(): Array<String> = arrayOf(storeId)

    override fun getStoreConfig(storeId: String) = InMemoryBlobStoreConfig(id = storeId)

    override fun getStore(storeId: String) = store
}

private class TestKvStoreService(
    private val store: com.sphereon.data.store.kv.KvStore,
) : com.sphereon.data.store.kv.impl.KvStoreService {
    override fun getStoreIds(): Array<String> = arrayOf(KvBlobMetadataIndex.STORE_ID)

    override fun getStoreConfig(storeId: String) = InMemoryKvStoreConfig(id = storeId, scopeBinding = KvStoreScopeBinding.APP)

    override fun getStore(storeId: String) = store
}

private class TestSessionExecution : com.sphereon.core.api.context.SessionExecution {
    override val sessionContext: com.sphereon.di.session.SessionContext = com.sphereon.di.context.NoOpSessionContext
    override val sessionContextManager: com.sphereon.di.session.SessionContextManager
        get() = throw NotImplementedError("Not needed for unit tests")
    override val log: com.sphereon.core.api.log.SessionLogService = NoOpSessionLogService(sessionContext)
    override val conf: com.sphereon.core.api.context.ContextConfig
        get() = throw NotImplementedError("Not needed for unit tests")
}

private class NoOpSessionLogService(
    override val sessionContext: com.sphereon.di.session.SessionContext,
) : com.sphereon.core.api.log.SessionLogService {
    override val id: String = "test-wallet-log"
    override val isEnabled: Boolean = false
    override val scope = com.sphereon.core.api.context.IdkScope.SESSION
    override val logManager: com.sphereon.core.api.log.SessionLogManager
        get() = throw NotImplementedError("Not needed for unit tests")

    override suspend fun setConfig(config: com.sphereon.core.api.log.LoggerConfig): com.sphereon.core.api.log.LogService = this

    override fun executeAsync(message: com.sphereon.core.api.log.LogMessage): com.sphereon.core.api.IdkResult<Unit, com.sphereon.core.api.error.IdkErrorType> =
        com.sphereon.core.api
            .Ok(Unit)

    override fun toAsync(): com.sphereon.core.api.log.AsyncLogService = throw NotImplementedError("Not needed for unit tests")
}

private class TestSessionEventService : com.sphereon.core.events.SessionEventService {
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

private fun createTestBlobService(
    blobBackingStorage: InMemoryBlobBackingStorageImpl = InMemoryBlobBackingStorageImpl(),
    kvBackingStorage: InMemoryKvBackingStorageImpl = InMemoryKvBackingStorageImpl(),
    storeId: String = "memory",
): DefaultBlobService {
    val blobFactory = InMemoryBlobStoreFactoryImpl(blobBackingStorage)
    val memoryStore = blobFactory.create(InMemoryBlobStoreConfig(id = storeId))

    val kvFactory = InMemoryKvStoreFactoryImpl(kvBackingStorage)
    val kvStore = kvFactory.create(InMemoryKvStoreConfig(id = KvBlobMetadataIndex.STORE_ID, scopeBinding = KvStoreScopeBinding.APP))

    return DefaultBlobService(
        blobStoreService = TestBlobStoreService(memoryStore, storeId),
        metadataIndex = KvBlobMetadataIndex(TestKvStoreService(kvStore)),
        retentionPolicyService = DefaultRetentionPolicyService(),
        tempUrlPolicy =
            com.sphereon.data.store.blob
                .DefaultTempUrlPolicy(),
        eventService = TestSessionEventService(),
        execution = TestSessionExecution(),
    )
}

private class BodyReadTrackingBlobService(
    private val delegate: DefaultBlobService,
) : BlobService by delegate {
    var bodyReadCount: Int = 0
        private set

    fun resetBodyReadCount() {
        bodyReadCount = 0
    }

    override suspend fun getBlob(info: BlobInfoType): IdkResult<ResolvedBlobInfo, IdkError> {
        val path = info.path
        if (path != null && path.startsWith("wallet-instances/") && path.endsWith("/body")) {
            bodyReadCount++
        }
        return delegate.getBlob(info)
    }
}

private class FailingGetBlobService(
    private val delegate: DefaultBlobService,
    private val failingPaths: Set<String>,
    private val error: IdkError,
) : BlobService by delegate {
    override suspend fun getBlob(info: BlobInfoType): IdkResult<ResolvedBlobInfo, IdkError> {
        val path = info.path
        if (path != null && path in failingPaths) return Err(error)
        return delegate.getBlob(info)
    }
}

private fun createTrackingStore(): Pair<BodyReadTrackingBlobService, BlobWalletCredentialStore> {
    val tracking = BodyReadTrackingBlobService(createTestBlobService())
    return tracking to BlobWalletCredentialStore(tracking, TestWalletCredentialBodyProtector)
}

private fun typeRef(value: String = "https://credentials.example.com/employee") =
    CredentialTypeRef(
        format = CredentialFormat.SD_JWT_DC,
        kind = CredentialTypeRefKind.SD_JWT_VCT,
        value = value,
        source = CredentialTypeRefSource.CREDENTIAL_PAYLOAD,
        primary = true,
    )

private fun makeRecord(
    id: String,
    walletInstanceId: String = WALLET_A,
    ref: CredentialTypeRef = typeRef(),
) = CredentialRecord(
    id = id,
    walletInstanceId = walletInstanceId,
    issuerRef = IdentifierRef(type = IdentifierType.DID, value = "did:example:issuer"),
    format = CredentialFormat.SD_JWT_DC,
    credentialTypeRefs = setOf(ref),
    instances =
        listOf(
            CredentialInstance(
                id = "$id-instance",
                walletInstanceId = walletInstanceId,
                credentialRecordId = id,
                format = CredentialFormat.SD_JWT_DC,
                raw = "raw-$id",
                bodyStorageRef =
                    BodyStorageRef(
                        kind = BodyStorageKind.BLOB,
                        path = walletCredentialInstanceBodyPath(walletInstanceId, id, "$id-instance"),
                        storeRef = StoreRef(id = "memory", type = "blob"),
                    ),
                holderKeyRef = KeyRef(alias = "holder-key"),
                lifecycleState = CredentialLifecycleState.ACTIVE,
                validity = CredentialValidityWindow(),
                issuedAt = NOW,
                storedAt = NOW,
                updatedAt = NOW,
            ),
        ),
    issuanceProvenance =
        IssuanceProvenance(
            credentialIssuerUrl = "did:example:issuer",
            credentialConfigurationId = "EmployeeCredential",
            issuedAt = NOW,
        ),
    createdAt = NOW,
    updatedAt = NOW,
)

class BlobWalletCredentialStoreTest {
    private fun buildStore(): BlobWalletCredentialStore = BlobWalletCredentialStore(createTestBlobService(), TestWalletCredentialBodyProtector)

    @Test
    fun putAndGetCredentialRoundTrip() =
        runTest {
            val store = buildStore()
            val record = makeRecord("record-1")

            val putResult = store.putCredential(WALLET_A, record)
            assertTrue(putResult.isOk)

            val getResult = store.getCredential(WALLET_A, "record-1")
            assertTrue(getResult.isOk)
            assertNotNull(getResult.value)
            assertEquals(record, getResult.value)
        }

    @Test
    fun putCredentialRejectsMismatchedWalletInstanceId() =
        runTest {
            val store = buildStore()

            val result = store.putCredential(WALLET_A, makeRecord("record-mismatch", WALLET_B))

            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    ?.contains("record.walletInstanceId") == true
            )
        }

    @Test
    fun sameCredentialRecordIdIsIsolatedPerWalletInstance() =
        runTest {
            val store = buildStore()
            val baseRecordA = makeRecord("shared-record", WALLET_A)
            val baseRecordB = makeRecord("shared-record", WALLET_B)
            val recordA = baseRecordA.copy(instances = baseRecordA.instances.map { it.copy(raw = "raw-wallet-a") })
            val recordB = baseRecordB.copy(instances = baseRecordB.instances.map { it.copy(raw = "raw-wallet-b") })

            assertTrue(store.putCredential(WALLET_A, recordA).isOk)
            assertTrue(store.putCredential(WALLET_B, recordB).isOk)

            assertEquals(
                "raw-wallet-a",
                store
                    .getCredential(WALLET_A, "shared-record")
                    .value
                    ?.instances
                    ?.single()
                    ?.raw
            )
            assertEquals(
                "raw-wallet-b",
                store
                    .getCredential(WALLET_B, "shared-record")
                    .value
                    ?.instances
                    ?.single()
                    ?.raw
            )
            assertEquals(listOf("shared-record"), store.listMetadata(WALLET_A).value.map { it.credentialRecordId })
            assertEquals(listOf("shared-record"), store.listMetadata(WALLET_B).value.map { it.credentialRecordId })
        }

    @Test
    fun putStoresProtectedCredentialInPerInstanceBodyAndEnvelopeWithoutRaw() =
        runTest {
            val blobService = createTestBlobService()
            val store = BlobWalletCredentialStore(blobService, TestWalletCredentialBodyProtector)
            val record = makeRecord("record-body")

            val putResult = store.putCredential(WALLET_A, record)
            assertTrue(putResult.isOk)

            val envelopeBlob = blobService.getBlob(BlobInfo(path = walletCredentialRecordEnvelopePath(WALLET_A, "record-body")))
            assertTrue(envelopeBlob.isOk)
            val envelope = testJson.decodeFromString<CredentialRecord>(envelopeBlob.value.data.decodeToString())
            assertNull(envelope.instances.single().raw)
            assertEquals(
                walletCredentialInstanceBodyPath(WALLET_A, "record-body", "record-body-instance"),
                envelope.instances
                    .single()
                    .bodyStorageRef.path,
            )

            val bodyBlob =
                blobService.getBlob(BlobInfo(path = walletCredentialInstanceBodyPath(WALLET_A, "record-body", "record-body-instance")))
            assertTrue(bodyBlob.isOk)
            val protectedBody = bodyBlob.value.data.decodeToString()
            assertTrue(protectedBody.startsWith("test-protected:"))
            assertTrue("raw-record-body" !in protectedBody)

            val hydrated = store.getCredential(WALLET_A, "record-body").value
            assertEquals("raw-record-body", hydrated?.instances?.single()?.raw)
        }

    @Test
    fun refreshPersistsNewInstanceBodyAndUpdatesMetadataWithoutLosingOldBody() =
        runTest {
            val blobService = createTestBlobService()
            val store = BlobWalletCredentialStore(blobService, TestWalletCredentialBodyProtector)
            val record =
                makeRecord("record-refresh")
                    .copy(
                        refreshState =
                            RefreshState(
                                refreshMethod = CredentialRefreshMethod.OID4VCI_REISSUANCE,
                                policy = RefreshPolicy(supersedePreviousActiveInstance = true),
                            ),
                    )
            assertTrue(store.putCredential(WALLET_A, record).isOk)
            val oldInstance = record.instances.single()
            val newInstance =
                oldInstance.copy(
                    id = "record-refresh-instance-2",
                    raw = "raw-record-refresh-v2",
                    replacesInstanceId = oldInstance.id,
                    bodyStorageRef =
                        BodyStorageRef(
                            kind = BodyStorageKind.WALLET_STORE,
                            path = walletCredentialInstanceBodyPath(WALLET_A, "record-refresh", "record-refresh-instance-2"),
                        ),
                    updatedAt = NOW,
                )
            val refreshed = record.withRefreshedInstance(newInstance)

            assertTrue(store.putCredential(WALLET_A, refreshed).isOk)

            val oldBody = blobService.getBlob(BlobInfo(path = walletCredentialInstanceBodyPath(WALLET_A, "record-refresh", oldInstance.id)))
            val newBody = blobService.getBlob(BlobInfo(path = walletCredentialInstanceBodyPath(WALLET_A, "record-refresh", newInstance.id)))
            assertTrue(oldBody.isOk)
            assertTrue(newBody.isOk)
            val protectedOldBody = oldBody.value.data.decodeToString()
            val protectedNewBody = newBody.value.data.decodeToString()
            assertTrue(protectedOldBody.startsWith("test-protected:"))
            assertTrue(protectedNewBody.startsWith("test-protected:"))
            assertTrue("raw-record-refresh" !in protectedOldBody)
            assertTrue("raw-record-refresh-v2" !in protectedNewBody)

            val hydrated = store.getCredential(WALLET_A, "record-refresh").value
            assertEquals(2, hydrated?.instances?.size)
            assertEquals(CredentialLifecycleState.SUPERSEDED, hydrated?.instances?.first { it.id == oldInstance.id }?.lifecycleState)
            assertEquals(CredentialLifecycleState.ACTIVE, hydrated?.instances?.first { it.id == newInstance.id }?.lifecycleState)
            assertEquals("raw-record-refresh", hydrated?.instances?.first { it.id == oldInstance.id }?.raw)
            assertEquals("raw-record-refresh-v2", hydrated?.instances?.first { it.id == newInstance.id }?.raw)
            val metadata = store.getMetadata(WALLET_A, "record-refresh").value
            assertEquals(2, metadata?.instanceCount)
            assertEquals(1, metadata?.activeInstanceCount)
        }

    @Test
    fun metadataListIsWalletInstanceIsolatedAndDoesNotReadBodies() =
        runTest {
            val (tracking, store) = createTrackingStore()
            store.putCredential(WALLET_A, makeRecord("record-a", WALLET_A))
            store.putCredential(WALLET_B, makeRecord("record-b", WALLET_B))
            tracking.resetBodyReadCount()

            val listResult = store.listMetadata(WALLET_A)

            assertTrue(listResult.isOk)
            assertEquals(listOf("record-a"), listResult.value.map { it.credentialRecordId })
            assertEquals(0, tracking.bodyReadCount)
        }

    @Test
    fun findByCredentialTypeRefUsesSidecarsOnly() =
        runTest {
            val (tracking, store) = createTrackingStore()
            val employeeRef = typeRef("https://credentials.example.com/employee")
            val pidRef = typeRef("https://credentials.example.com/pid")
            store.putCredential(WALLET_A, makeRecord("record-employee", ref = employeeRef))
            store.putCredential(WALLET_A, makeRecord("record-pid", ref = pidRef))
            tracking.resetBodyReadCount()

            val findResult = store.findByCredentialTypeRef(WALLET_A, employeeRef)

            assertTrue(findResult.isOk)
            assertEquals(listOf("record-employee"), findResult.value.map { it.credentialRecordId })
            assertEquals(0, tracking.bodyReadCount)
        }

    @Test
    fun getMetadataDoesNotReadBodyButGetCredentialDoes() =
        runTest {
            val (tracking, store) = createTrackingStore()
            store.putCredential(WALLET_A, makeRecord("record-meta"))
            tracking.resetBodyReadCount()

            val metadataResult = store.getMetadata(WALLET_A, "record-meta")
            assertTrue(metadataResult.isOk)
            assertNotNull(metadataResult.value)
            assertEquals(0, tracking.bodyReadCount)

            val credentialResult = store.getCredential(WALLET_A, "record-meta")
            assertTrue(credentialResult.isOk)
            assertNotNull(credentialResult.value)
            assertEquals(1, tracking.bodyReadCount)
        }

    @Test
    fun getMetadataPropagatesNonNotFoundBlobReadFailure() =
        runTest {
            val blobService = createTestBlobService()
            val store = BlobWalletCredentialStore(blobService, TestWalletCredentialBodyProtector)
            assertTrue(store.putCredential(WALLET_A, makeRecord("record-metadata-error")).isOk)
            val failure = IdkError.fromString(code = "BLOB_PERMISSION_DENIED", message = "denied")
            val failingStore =
                BlobWalletCredentialStore(
                    FailingGetBlobService(
                        delegate = blobService,
                        failingPaths = setOf(walletCredentialMetadataPath(WALLET_A, "record-metadata-error")),
                        error = failure,
                    ),
                    TestWalletCredentialBodyProtector,
                )

            val result = failingStore.getMetadata(WALLET_A, "record-metadata-error")

            assertTrue(result.isErr)
            assertEquals("BLOB_PERMISSION_DENIED", result.error.code)
        }

    @Test
    fun getCredentialPropagatesNonNotFoundEnvelopeReadFailure() =
        runTest {
            val blobService = createTestBlobService()
            val store = BlobWalletCredentialStore(blobService, TestWalletCredentialBodyProtector)
            assertTrue(store.putCredential(WALLET_A, makeRecord("record-envelope-error")).isOk)
            val failure = IdkError.fromString(code = "BLOB_BACKEND_ERROR", message = "blob backend unavailable")
            val failingStore =
                BlobWalletCredentialStore(
                    FailingGetBlobService(
                        delegate = blobService,
                        failingPaths = setOf(walletCredentialRecordEnvelopePath(WALLET_A, "record-envelope-error")),
                        error = failure,
                    ),
                    TestWalletCredentialBodyProtector,
                )

            val result = failingStore.getCredential(WALLET_A, "record-envelope-error")

            assertTrue(result.isErr)
            assertEquals("BLOB_BACKEND_ERROR", result.error.code)
        }

    @Test
    fun deleteWritesTombstoneSidecarAndHidesDefaultMetadataList() =
        runTest {
            val store = buildStore()
            store.putCredential(WALLET_A, makeRecord("record-del"))

            val deleteResult = store.deleteCredential(WALLET_A, "record-del")
            assertTrue(deleteResult.isOk)
            assertEquals(true, deleteResult.value)

            assertNull(store.getCredential(WALLET_A, "record-del").value)
            assertEquals(emptyList(), store.listMetadata(WALLET_A).value)
            val tombstones = store.listMetadata(WALLET_A, CredentialMetadataFilter(includeDeleted = true)).value
            assertEquals(CredentialLifecycleState.DELETED, tombstones.single().lifecycleSummary.lifecycleState)
        }

    @Test
    fun issuanceSessionStorePersistsSessionsSecretsAndFiltersByWallet() =
        runTest {
            val store = BlobWalletIssuanceSessionStore(createTestBlobService())
            val sessionA = makeSession("session-a", WALLET_A, IssuanceSessionStatus.DEFERRED)
            val sessionB = makeSession("session-b", WALLET_B, IssuanceSessionStatus.CREATED)

            assertTrue(store.putSession(WALLET_A, sessionA).isOk)
            assertTrue(store.putSession(WALLET_B, sessionB).isOk)
            val secretRef = store.storeDeferredAccessToken(WALLET_A, sessionA.id, "access-token-a")
            assertTrue(secretRef.isOk)
            assertEquals(walletDeferredAccessTokenPath(WALLET_A, sessionA.id), secretRef.value.id)

            assertEquals(sessionA, store.getSession(WALLET_A, sessionA.id).value)
            assertNull(store.getSession(WALLET_B, sessionA.id).value)
            assertEquals(listOf(sessionA.id), store.listSessions(WALLET_A, setOf(IssuanceSessionStatus.DEFERRED)).value.map { it.id })
            assertEquals("access-token-a", store.getDeferredAccessToken(WALLET_A, sessionA.id).value)
            assertNull(store.getDeferredAccessToken(WALLET_B, sessionA.id).value)
        }

    @Test
    fun operationQueueIsWalletScopedSortedAndRemovable() =
        runTest {
            val queue = BlobWalletOperationQueue(createTestBlobService())
            val opLate = makeOperation("op-late", WALLET_A, Instant.fromEpochSeconds(1_800_000_100))
            val opOtherWallet = makeOperation("op-other", WALLET_B, Instant.fromEpochSeconds(1_800_000_050))
            val opEarly = makeOperation("op-early", WALLET_A, Instant.fromEpochSeconds(1_800_000_010))

            assertTrue(queue.enqueue(WALLET_A, opLate).isOk)
            assertTrue(queue.enqueue(WALLET_B, opOtherWallet).isOk)
            assertTrue(queue.enqueue(WALLET_A, opEarly).isOk)

            assertEquals(listOf("op-early", "op-late"), queue.listPending(WALLET_A).value.map { it.id })
            assertEquals(listOf("op-other"), queue.listPending(WALLET_B).value.map { it.id })

            assertEquals(true, queue.remove(WALLET_A, "op-early").value)
            assertEquals(listOf("op-late"), queue.listPending(WALLET_A).value.map { it.id })
            assertEquals(listOf("op-other"), queue.listPending(WALLET_B).value.map { it.id })
        }

    @Test
    fun blobWalletDeviceIdProviderPersistsStableDeviceId() =
        runTest {
            val blobService = createTestBlobService()
            val firstProvider = BlobWalletDeviceIdProvider(blobService)
            val first = firstProvider.deviceId()
            assertTrue(first.isOk)

            val second = BlobWalletDeviceIdProvider(blobService).deviceId()
            assertTrue(second.isOk)

            assertEquals(first.value, second.value)
            assertEquals(
                first.value,
                blobService
                    .getBlob(BlobInfo(path = walletDeviceIdPath()))
                    .value.data
                    .decodeToString()
            )
        }

    @Test
    fun hybridPutUsesPersistedDeviceIdForPendingOperation() =
        runTest {
            val localBlobService = createTestBlobService()
            val localStore = BlobWalletCredentialStore(localBlobService, TestWalletCredentialBodyProtector)
            val queue = BlobWalletOperationQueue(localBlobService)
            val deviceIdProvider = BlobWalletDeviceIdProvider(localBlobService)
            val expectedDeviceId = deviceIdProvider.deviceId().value
            val hybridStore =
                HybridWalletCredentialStore(
                    localStore = localStore,
                    remoteStore = UnsupportedRemoteWalletCredentialStore(),
                    operationQueue = queue,
                    deviceIdProvider = deviceIdProvider,
                )

            val result = hybridStore.putCredential(WALLET_A, makeRecord("record-persisted-device"))

            assertTrue(result.isOk)
            assertEquals(expectedDeviceId, result.value.syncState.deviceId)
            assertEquals(
                expectedDeviceId,
                queue
                    .listPending(WALLET_A)
                    .value
                    .single()
                    .createdByDeviceId
            )
        }

    @Test
    fun hybridGetCredentialFetchesAndRepairsMissingLocalBodyFromRemote() =
        runTest {
            val localBlobService = createTestBlobService()
            val remoteBlobService = createTestBlobService()
            val localStore = BlobWalletCredentialStore(localBlobService, TestWalletCredentialBodyProtector)
            val remoteStore = BlobWalletCredentialStore(remoteBlobService, TestWalletCredentialBodyProtector)
            val hybridStore =
                HybridWalletCredentialStore(
                    localStore = localStore,
                    remoteStore = remoteStore,
                    operationQueue = BlobWalletOperationQueue(localBlobService),
                    deviceId = "device-1",
                )
            val record = makeRecord("record-hybrid-repair")

            assertTrue(remoteStore.putCredential(WALLET_A, record).isOk)
            assertTrue(localStore.putCredential(WALLET_A, record).isOk)
            val localBodyDelete =
                localBlobService.deleteBlob(
                    BlobInfo(path = walletCredentialInstanceBodyPath(WALLET_A, "record-hybrid-repair", "record-hybrid-repair-instance")),
                )
            assertTrue(localBodyDelete.isOk)
            assertEquals(true, localBodyDelete.value)

            val repaired = hybridStore.getCredential(WALLET_A, "record-hybrid-repair")

            assertTrue(repaired.isOk)
            assertEquals(
                "raw-record-hybrid-repair",
                repaired.value
                    ?.instances
                    ?.single()
                    ?.raw
            )
            assertEquals(
                "raw-record-hybrid-repair",
                localStore
                    .getCredential(WALLET_A, "record-hybrid-repair")
                    .value
                    ?.instances
                    ?.single()
                    ?.raw
            )
        }

    @Test
    fun hybridSyncServiceReplaysQueuedPutAfterRemoteRecovers() =
        runTest {
            val localBlobService = createTestBlobService()
            val remoteBlobService = createTestBlobService()
            val localStore = BlobWalletCredentialStore(localBlobService, TestWalletCredentialBodyProtector)
            val remoteStore = BlobWalletCredentialStore(remoteBlobService, TestWalletCredentialBodyProtector)
            val queue = BlobWalletOperationQueue(localBlobService)
            val offlineHybridStore =
                HybridWalletCredentialStore(
                    localStore = localStore,
                    remoteStore = UnsupportedRemoteWalletCredentialStore(),
                    operationQueue = queue,
                    deviceId = "device-1",
                )
            assertTrue(offlineHybridStore.putCredential(WALLET_A, makeRecord("record-replay")).isOk)
            assertEquals(1, queue.listPending(WALLET_A).value.size)

            val syncService = HybridWalletOperationSyncService(localStore, remoteStore, queue)
            val replay = syncService.replayPending(WALLET_A)

            assertTrue(replay.isOk)
            assertEquals(1, replay.value.attempted)
            assertEquals(1, replay.value.applied)
            assertEquals(emptyList(), replay.value.conflicts)
            assertEquals(emptyList(), replay.value.failures)
            assertEquals(emptyList(), queue.listPending(WALLET_A).value)
            assertEquals(
                "raw-record-replay",
                remoteStore
                    .getCredential(WALLET_A, "record-replay")
                    .value
                    ?.instances
                    ?.single()
                    ?.raw
            )
            val localRecord = localStore.getCredential(WALLET_A, "record-replay").value
            assertEquals(emptyList(), localRecord?.syncState?.pendingOperationIds)
            assertNotNull(localRecord?.syncState?.remoteRevision)
        }

    @Test
    fun hybridInlinePutPreservesEarlierQueuedOperationIds() =
        runTest {
            val localBlobService = createTestBlobService()
            val remoteBlobService = createTestBlobService()
            val localStore = BlobWalletCredentialStore(localBlobService, TestWalletCredentialBodyProtector)
            val remoteStore = BlobWalletCredentialStore(remoteBlobService, TestWalletCredentialBodyProtector)
            val queue = BlobWalletOperationQueue(localBlobService)
            val hybridStore =
                HybridWalletCredentialStore(
                    localStore = localStore,
                    remoteStore = remoteStore,
                    operationQueue = queue,
                    deviceId = "device-1",
                )
            val earlierOperationId = "op-earlier"
            val baseRecord =
                makeRecord("record-inline-preserve")
                    .copy(
                        syncState =
                            RecordSyncState(
                                remoteRevision = "remote-rev-1",
                                pendingOperationIds = listOf(earlierOperationId),
                            ),
                    )
            val remoteRecord = baseRecord.copy(syncState = RecordSyncState(remoteRevision = "remote-rev-1"))
            val earlierOperation =
                WalletOperation(
                    id = earlierOperationId,
                    walletInstanceId = WALLET_A,
                    credentialRecordId = baseRecord.id,
                    operationType = WalletOperationType.PUT_CREDENTIAL,
                    baseRemoteRevision = "remote-rev-1",
                    createdByDeviceId = "device-1",
                    createdAt = NOW,
                )
            assertTrue(localStore.putCredential(WALLET_A, baseRecord).isOk)
            assertTrue(remoteStore.putCredential(WALLET_A, remoteRecord).isOk)
            assertTrue(queue.enqueue(WALLET_A, earlierOperation).isOk)

            val result = hybridStore.putCredential(WALLET_A, baseRecord)

            assertTrue(result.isOk)
            assertEquals(
                listOf(earlierOperationId),
                localStore
                    .getCredential(WALLET_A, baseRecord.id)
                    .value
                    ?.syncState
                    ?.pendingOperationIds
            )
            assertEquals(listOf(earlierOperationId), queue.listPending(WALLET_A).value.map { it.id })
        }

    @Test
    fun hybridSyncServiceLeavesConflictingOperationQueued() =
        runTest {
            val localBlobService = createTestBlobService()
            val remoteBlobService = createTestBlobService()
            val localStore = BlobWalletCredentialStore(localBlobService, TestWalletCredentialBodyProtector)
            val remoteStore = BlobWalletCredentialStore(remoteBlobService, TestWalletCredentialBodyProtector)
            val queue = BlobWalletOperationQueue(localBlobService)
            val hybridStore =
                HybridWalletCredentialStore(
                    localStore = localStore,
                    remoteStore = remoteStore,
                    operationQueue = queue,
                    deviceId = "device-1",
                )
            val localRecord =
                makeRecord("record-replay-conflict")
                    .copy(syncState = RecordSyncState(remoteRevision = "remote-rev-1"))
            val remoteRecord = localRecord.copy(syncState = RecordSyncState(remoteRevision = "remote-rev-2"))
            assertTrue(remoteStore.putCredential(WALLET_A, remoteRecord).isOk)

            val putResult = hybridStore.putCredential(WALLET_A, localRecord)
            assertTrue(putResult.isErr)

            val syncService = HybridWalletOperationSyncService(localStore, remoteStore, queue)
            val replay = syncService.replayPending(WALLET_A)

            assertTrue(replay.isOk)
            assertEquals(1, replay.value.attempted)
            assertEquals(0, replay.value.applied)
            assertEquals(1, replay.value.conflicts.size)
            assertEquals(
                "remote-rev-1",
                replay.value.conflicts
                    .single()
                    .baseRemoteRevision
            )
            assertEquals(
                "remote-rev-2",
                replay.value.conflicts
                    .single()
                    .remoteRevision
            )
            assertEquals(1, queue.listPending(WALLET_A).value.size)
        }

    @Test
    fun hybridMetadataQueriesIncludeRemoteOnlySidecarsWithoutReadingBodies() =
        runTest {
            val (localBlobService, localStore) = createTrackingStore()
            val (remoteBlobService, remoteStore) = createTrackingStore()
            val hybridStore =
                HybridWalletCredentialStore(
                    localStore = localStore,
                    remoteStore = remoteStore,
                    operationQueue = BlobWalletOperationQueue(localBlobService),
                    deviceId = "device-1",
                )
            val ref = typeRef("https://credentials.example.com/remote-only")
            val remoteRecord = makeRecord("record-remote-only", ref = ref)
            assertTrue(remoteStore.putCredential(WALLET_A, remoteRecord).isOk)
            localBlobService.resetBodyReadCount()
            remoteBlobService.resetBodyReadCount()

            val listed = hybridStore.listMetadata(WALLET_A)
            val found = hybridStore.findByCredentialTypeRef(WALLET_A, ref)

            assertTrue(listed.isOk)
            assertEquals(listOf("record-remote-only"), listed.value.map { it.credentialRecordId })
            assertTrue(found.isOk)
            assertEquals(listOf("record-remote-only"), found.value.map { it.credentialRecordId })
            assertEquals(0, localBlobService.bodyReadCount)
            assertEquals(0, remoteBlobService.bodyReadCount)
        }

    @Test
    fun hybridDeleteChecksRemoteRevisionBeforeLocalTombstone() =
        runTest {
            val localBlobService = createTestBlobService()
            val remoteBlobService = createTestBlobService()
            val localStore = BlobWalletCredentialStore(localBlobService, TestWalletCredentialBodyProtector)
            val remoteStore = BlobWalletCredentialStore(remoteBlobService, TestWalletCredentialBodyProtector)
            val queue = BlobWalletOperationQueue(localBlobService)
            val hybridStore =
                HybridWalletCredentialStore(
                    localStore = localStore,
                    remoteStore = remoteStore,
                    operationQueue = queue,
                    deviceId = "device-1",
                )
            val localRecord =
                makeRecord("record-conflict-delete")
                    .copy(syncState = RecordSyncState(remoteRevision = "remote-rev-1"))
            val remoteRecord = localRecord.copy(syncState = RecordSyncState(remoteRevision = "remote-rev-2"))
            assertTrue(localStore.putCredential(WALLET_A, localRecord).isOk)
            assertTrue(remoteStore.putCredential(WALLET_A, remoteRecord).isOk)

            val deleteResult = hybridStore.deleteCredential(WALLET_A, "record-conflict-delete")

            assertTrue(deleteResult.isErr, "delete should fail on remote revision conflict")
            assertEquals("SYNC_CONFLICT", deleteResult.error.code)
            assertEquals(
                false,
                localStore
                    .getMetadata(WALLET_A, "record-conflict-delete")
                    .value
                    ?.lifecycleSummary
                    ?.tombstone
            )
            assertEquals(
                "raw-record-conflict-delete",
                localStore
                    .getCredential(WALLET_A, "record-conflict-delete")
                    .value
                    ?.instances
                    ?.single()
                    ?.raw
            )
            assertEquals(emptyList(), queue.listPending(WALLET_A).value)
        }

    @Test
    fun storageProfileRouterDispatchesCredentialOperationsByWalletMode() =
        runTest {
            val local = RecordingWalletCredentialStore()
            val remote = RecordingWalletCredentialStore()
            val hybrid = RecordingWalletCredentialStore()
            val router =
                StorageProfileRoutingWalletCredentialStore(
                    storageProfileResolver =
                        TestStorageProfileResolver(
                            mapOf(
                                "wallet-local" to storageProfile("wallet-local", WalletStorageMode.LOCAL),
                                "wallet-remote" to storageProfile("wallet-remote", WalletStorageMode.REMOTE),
                                "wallet-hybrid" to storageProfile("wallet-hybrid", WalletStorageMode.HYBRID),
                            ),
                        ),
                    localStore = local,
                    remoteStore = remote,
                    hybridStore = hybrid,
                )

            assertTrue(router.putCredential("wallet-local", makeRecord("record-local", "wallet-local")).isOk)
            assertTrue(router.putCredential("wallet-remote", makeRecord("record-remote", "wallet-remote")).isOk)
            assertTrue(router.putCredential("wallet-hybrid", makeRecord("record-hybrid", "wallet-hybrid")).isOk)

            assertEquals(listOf("put:wallet-local:record-local"), local.calls)
            assertEquals(listOf("put:wallet-remote:record-remote"), remote.calls)
            assertEquals(listOf("put:wallet-hybrid:record-hybrid"), hybrid.calls)
        }

    @Test
    fun managedHybridProfileAndCredentialSurviveStoreRecreation() =
        runTest {
            val walletInstanceId = "wallet-profile-employee"
            val localBlobBacking = InMemoryBlobBackingStorageImpl()
            val localKvBacking = InMemoryKvBackingStorageImpl()
            val remoteBlobBacking = InMemoryBlobBackingStorageImpl()
            val remoteKvBacking = InMemoryKvBackingStorageImpl()

            fun localBlobService() = createTestBlobService(localBlobBacking, localKvBacking, "local")

            fun remoteBlobService() = createTestBlobService(remoteBlobBacking, remoteKvBacking, "remote")

            val instanceStore = BlobWalletInstanceStore(localBlobService())
            val profile =
                StorageProfile(
                    id = "$walletInstanceId:managed-hybrid",
                    walletInstanceId = walletInstanceId,
                    mode = WalletStorageMode.HYBRID,
                    localStoreRef = StoreRef(id = "local", type = "blob"),
                    remoteVaultRef = StoreRef(id = "remote", type = "vault"),
                    encryptionPolicyId = "wallet-managed-hybrid",
                    syncPolicyId = "wallet-managed-hybrid-sync",
                )
            val instance =
                WalletInstance(
                    id = walletInstanceId,
                    ownerSubjectRef = IdentifierRef(type = IdentifierType("party"), value = "party-employee"),
                    label = "Employee",
                    purpose = WalletInstancePurpose.WORK,
                    storageProfileId = profile.id,
                    defaultHolderKeyPolicyId = "wallet-holder-key-managed",
                    trustDomainId = "tenant-a",
                    createdAt = NOW,
                    updatedAt = NOW,
                )
            assertTrue(instanceStore.putWalletInstance(instance, profile).isOk)

            val localStore = BlobWalletCredentialStore(localBlobService(), TestWalletCredentialBodyProtector)
            val remoteStore = RemoteBlobWalletCredentialStore(BlobWalletCredentialStore(remoteBlobService(), TestWalletCredentialBodyProtector))
            val queue = BlobWalletOperationQueue(localBlobService())
            val router =
                StorageProfileRoutingWalletCredentialStore(
                    storageProfileResolver = instanceStore,
                    localStore = localStore,
                    remoteStore = remoteStore,
                    hybridStore = HybridWalletCredentialStore(localStore, remoteStore, queue, "device-1"),
                )
            assertTrue(router.putCredential(walletInstanceId, makeRecord("record-managed", walletInstanceId)).isOk)

            val recreatedInstanceStore = BlobWalletInstanceStore(localBlobService())
            val recreatedLocalStore = BlobWalletCredentialStore(localBlobService(), TestWalletCredentialBodyProtector)
            val recreatedRemoteStore = RemoteBlobWalletCredentialStore(BlobWalletCredentialStore(remoteBlobService(), TestWalletCredentialBodyProtector))
            val recreatedRouter =
                StorageProfileRoutingWalletCredentialStore(
                    storageProfileResolver = recreatedInstanceStore,
                    localStore = recreatedLocalStore,
                    remoteStore = recreatedRemoteStore,
                    hybridStore =
                        HybridWalletCredentialStore(
                            recreatedLocalStore,
                            recreatedRemoteStore,
                            BlobWalletOperationQueue(localBlobService()),
                            "device-1",
                        ),
                )

            assertEquals(profile, recreatedInstanceStore.resolveStorageProfile(walletInstanceId).value)
            val restored = recreatedRouter.getCredential(walletInstanceId, "record-managed")
            assertTrue(restored.isOk)
            assertEquals(
                "raw-record-managed",
                restored.value
                    ?.instances
                    ?.single()
                    ?.raw
            )
            assertEquals(WalletStorageMode.HYBRID, recreatedInstanceStore.getStorageProfile(walletInstanceId).value?.mode)
        }

    @Test
    fun storageProfileRouterRejectsInvalidProfilesBeforeCallingDelegates() =
        runTest {
            val local = RecordingWalletCredentialStore()
            val remote = RecordingWalletCredentialStore()
            val hybrid = RecordingWalletCredentialStore()
            val router =
                StorageProfileRoutingWalletCredentialStore(
                    storageProfileResolver =
                        TestStorageProfileResolver(
                            mapOf(
                                "wallet-bad" to
                                    StorageProfile(
                                        id = "wallet-bad-profile",
                                        walletInstanceId = "wallet-bad",
                                        mode = WalletStorageMode.HYBRID,
                                        localStoreRef = StoreRef(id = "local", type = "blob"),
                                        remoteVaultRef = null,
                                        encryptionPolicyId = "enc-default",
                                    ),
                            ),
                        ),
                    localStore = local,
                    remoteStore = remote,
                    hybridStore = hybrid,
                )

            val result = router.listMetadata("wallet-bad")

            assertTrue(result.isErr)
            assertEquals(emptyList(), local.calls + remote.calls + hybrid.calls)
        }

    @Test
    fun storageProfileRouterDispatchesIssuanceSessionOperationsByWalletMode() =
        runTest {
            val local = RecordingWalletIssuanceSessionStore()
            val remote = RecordingWalletIssuanceSessionStore()
            val hybrid = RecordingWalletIssuanceSessionStore()
            val router =
                StorageProfileRoutingWalletIssuanceSessionStore(
                    storageProfileResolver =
                        TestStorageProfileResolver(
                            mapOf(
                                "wallet-local" to storageProfile("wallet-local", WalletStorageMode.LOCAL),
                                "wallet-remote" to storageProfile("wallet-remote", WalletStorageMode.REMOTE),
                                "wallet-hybrid" to storageProfile("wallet-hybrid", WalletStorageMode.HYBRID),
                            ),
                        ),
                    localStore = local,
                    remoteStore = remote,
                    hybridStore = hybrid,
                )

            assertTrue(router.putSession("wallet-local", makeSession("session-local", "wallet-local", IssuanceSessionStatus.CREATED)).isOk)
            assertTrue(router.putSession("wallet-remote", makeSession("session-remote", "wallet-remote", IssuanceSessionStatus.CREATED)).isOk)
            assertTrue(router.putSession("wallet-hybrid", makeSession("session-hybrid", "wallet-hybrid", IssuanceSessionStatus.CREATED)).isOk)

            assertEquals(listOf("put:wallet-local:session-local"), local.calls)
            assertEquals(listOf("put:wallet-remote:session-remote"), remote.calls)
            assertEquals(listOf("put:wallet-hybrid:session-hybrid"), hybrid.calls)
        }

    @Test
    fun storageProfileRouterRejectsInvalidIssuanceSessionProfilesBeforeCallingDelegates() =
        runTest {
            val local = RecordingWalletIssuanceSessionStore()
            val remote = RecordingWalletIssuanceSessionStore()
            val hybrid = RecordingWalletIssuanceSessionStore()
            val router =
                StorageProfileRoutingWalletIssuanceSessionStore(
                    storageProfileResolver =
                        TestStorageProfileResolver(
                            mapOf(
                                "wallet-bad" to
                                    StorageProfile(
                                        id = "wallet-bad-profile",
                                        walletInstanceId = "wallet-bad",
                                        mode = WalletStorageMode.HYBRID,
                                        localStoreRef = StoreRef(id = "local", type = "blob"),
                                        remoteVaultRef = null,
                                        encryptionPolicyId = "enc-default",
                                    ),
                            ),
                        ),
                    localStore = local,
                    remoteStore = remote,
                    hybridStore = hybrid,
                )

            val result = router.listSessions("wallet-bad")

            assertTrue(result.isErr)
            assertEquals(emptyList(), local.calls + remote.calls + hybrid.calls)
        }

    @Test
    fun hybridIssuanceSessionStoreCachesRemoteSessionButDefaultDeniesRemoteSecretFallback() =
        runTest {
            val local = RecordingWalletIssuanceSessionStore()
            val remote = RecordingWalletIssuanceSessionStore()
            val hybrid = HybridWalletIssuanceSessionStore(localStore = local, remoteStore = remote)
            val session = makeSession("session-remote", WALLET_A, IssuanceSessionStatus.DEFERRED)
            remote.putSession(WALLET_A, session)
            remote.storeDeferredAccessToken(WALLET_A, session.id, "remote-token")
            local.calls.clear()
            remote.calls.clear()

            val fetchedSession = hybrid.getSession(WALLET_A, session.id)
            val fetchedToken = hybrid.getDeferredAccessToken(WALLET_A, session.id)

            assertTrue(fetchedSession.isOk)
            assertEquals(session, fetchedSession.value)
            assertTrue(fetchedToken.isOk)
            assertNull(fetchedToken.value)
            assertEquals(session, local.sessionFor(WALLET_A, session.id))
            assertNull(local.secretFor(WALLET_A, session.id))
            assertTrue("secret-get:$WALLET_A:${session.id}" !in remote.calls)
        }

    @Test
    fun hybridIssuanceSessionStoreDefaultDeniesRemoteSecretWrite() =
        runTest {
            val local = RecordingWalletIssuanceSessionStore()
            val remote = RecordingWalletIssuanceSessionStore()
            val hybrid = HybridWalletIssuanceSessionStore(localStore = local, remoteStore = remote)
            val session = makeSession("session-local", WALLET_A, IssuanceSessionStatus.DEFERRED)

            val result = hybrid.storeDeferredAccessToken(WALLET_A, session.id, "local-token")

            assertTrue(result.isOk)
            assertEquals("local-token", local.secretFor(WALLET_A, session.id))
            assertNull(remote.secretFor(WALLET_A, session.id))
            assertTrue("secret-put:$WALLET_A:${session.id}" !in remote.calls)
        }

    @Test
    fun hybridIssuanceSessionStoreMirrorsRemoteSecretsWhenPolicyAllows() =
        runTest {
            val local = RecordingWalletIssuanceSessionStore()
            val remote = RecordingWalletIssuanceSessionStore()
            val policy =
                RecordingDeferredAccessTokenRemoteMirrorPolicy(
                    allowedOperations =
                        setOf(
                            WalletDeferredAccessTokenRemoteMirrorOperation.STORE_REMOTE_COPY,
                            WalletDeferredAccessTokenRemoteMirrorOperation.READ_REMOTE_COPY,
                        ),
                )
            val hybrid =
                HybridWalletIssuanceSessionStore(
                    localStore = local,
                    remoteStore = remote,
                    deferredAccessTokenRemoteMirrorPolicy = policy,
                )
            val storedSession = makeSession("session-stored", WALLET_A, IssuanceSessionStatus.DEFERRED)
            val remoteSession = makeSession("session-remote-secret", WALLET_A, IssuanceSessionStatus.DEFERRED)
            remote.storeDeferredAccessToken(WALLET_A, remoteSession.id, "remote-token")
            remote.calls.clear()

            val stored = hybrid.storeDeferredAccessToken(WALLET_A, storedSession.id, "local-token")
            val fetched = hybrid.getDeferredAccessToken(WALLET_A, remoteSession.id)

            assertTrue(stored.isOk)
            assertEquals("local-token", local.secretFor(WALLET_A, storedSession.id))
            assertEquals("local-token", remote.secretFor(WALLET_A, storedSession.id))
            assertTrue(fetched.isOk)
            assertEquals("remote-token", fetched.value)
            assertEquals("remote-token", local.secretFor(WALLET_A, remoteSession.id))
            assertEquals(
                listOf(
                    WalletDeferredAccessTokenRemoteMirrorOperation.STORE_REMOTE_COPY,
                    WalletDeferredAccessTokenRemoteMirrorOperation.READ_REMOTE_COPY,
                ),
                policy.requests.map { it.operation },
            )
        }

    private fun makeSession(
        id: String,
        walletInstanceId: String,
        status: IssuanceSessionStatus,
    ) = IssuanceSession(
        id = id,
        walletInstanceId = walletInstanceId,
        issuerRef = IdentifierRef(type = IdentifierType.DID, value = "did:example:issuer"),
        credentialIssuerUrl = "did:example:issuer",
        credentialConfigurationId = "EmployeeCredential",
        status = status,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun makeOperation(
        id: String,
        walletInstanceId: String,
        createdAt: Instant,
    ) = WalletOperation(
        id = id,
        walletInstanceId = walletInstanceId,
        credentialRecordId = "record-1",
        operationType = WalletOperationType.PUT_CREDENTIAL,
        baseRemoteRevision = null,
        createdByDeviceId = "device-1",
        createdAt = createdAt,
    )

    private fun storageProfile(
        walletInstanceId: String,
        mode: WalletStorageMode,
    ): StorageProfile =
        StorageProfile(
            id = "$walletInstanceId-profile",
            walletInstanceId = walletInstanceId,
            mode = mode,
            localStoreRef =
                when (mode) {
                    WalletStorageMode.LOCAL, WalletStorageMode.HYBRID -> StoreRef(id = "local", type = "blob")
                    WalletStorageMode.REMOTE -> null
                },
            remoteVaultRef =
                when (mode) {
                    WalletStorageMode.REMOTE, WalletStorageMode.HYBRID -> StoreRef(id = "remote", type = "vault")
                    WalletStorageMode.LOCAL -> null
                },
            encryptionPolicyId = "enc-default",
        )
}

private class TestStorageProfileResolver(
    private val profiles: Map<String, StorageProfile>,
) : WalletStorageProfileResolver {
    override suspend fun resolveStorageProfile(walletInstanceId: String): IdkResult<StorageProfile, IdkError> =
        profiles[walletInstanceId]?.let { Ok(it) }
            ?: Err(IdkError.NOT_FOUND_ERROR(message = "Storage profile for wallet '$walletInstanceId' was not found"))
}

private class RemoteBlobWalletCredentialStore(
    private val delegate: BlobWalletCredentialStore,
) : RemoteWalletCredentialStore,
    WalletCredentialStore by delegate

private class RecordingWalletIssuanceSessionStore :
    LocalWalletIssuanceSessionStore,
    RemoteWalletIssuanceSessionStore,
    HybridWalletIssuanceSessionStoreDelegate {
    val calls: MutableList<String> = mutableListOf()
    private val sessions: MutableMap<String, IssuanceSession> = linkedMapOf()
    private val secrets: MutableMap<String, String> = linkedMapOf()

    fun sessionFor(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): IssuanceSession? = sessions[key(walletInstanceId, issuanceSessionId)]

    fun secretFor(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): String? = secrets[key(walletInstanceId, issuanceSessionId)]

    override suspend fun putSession(
        walletInstanceId: String,
        session: IssuanceSession,
    ): IdkResult<IssuanceSession, IdkError> {
        calls += "put:$walletInstanceId:${session.id}"
        sessions[key(walletInstanceId, session.id)] = session
        return Ok(session)
    }

    override suspend fun getSession(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): IdkResult<IssuanceSession?, IdkError> {
        calls += "get:$walletInstanceId:$issuanceSessionId"
        return Ok(sessions[key(walletInstanceId, issuanceSessionId)]?.takeIf { it.walletInstanceId == walletInstanceId })
    }

    override suspend fun listSessions(
        walletInstanceId: String,
        statuses: Set<IssuanceSessionStatus>,
    ): IdkResult<List<IssuanceSession>, IdkError> {
        calls += "list:$walletInstanceId"
        return Ok(
            sessions.values.filter {
                it.walletInstanceId == walletInstanceId && (statuses.isEmpty() || it.status in statuses)
            },
        )
    }

    override suspend fun storeDeferredAccessToken(
        walletInstanceId: String,
        issuanceSessionId: String,
        accessToken: String,
    ): IdkResult<SecretRef, IdkError> {
        calls += "secret-put:$walletInstanceId:$issuanceSessionId"
        secrets[key(walletInstanceId, issuanceSessionId)] = accessToken
        return Ok(SecretRef(id = "secret:$walletInstanceId:$issuanceSessionId", storeRef = StoreRef(id = "memory", type = "test")))
    }

    override suspend fun getDeferredAccessToken(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): IdkResult<String?, IdkError> {
        calls += "secret-get:$walletInstanceId:$issuanceSessionId"
        return Ok(secrets[key(walletInstanceId, issuanceSessionId)])
    }

    override suspend fun deleteSession(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): IdkResult<Boolean, IdkError> {
        calls += "delete:$walletInstanceId:$issuanceSessionId"
        val sessionRemoved = sessions.remove(key(walletInstanceId, issuanceSessionId)) != null
        val secretRemoved = secrets.remove(key(walletInstanceId, issuanceSessionId)) != null
        return Ok(sessionRemoved || secretRemoved)
    }

    private fun key(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): String = "$walletInstanceId:$issuanceSessionId"
}

private class RecordingDeferredAccessTokenRemoteMirrorPolicy(
    private val allowedOperations: Set<WalletDeferredAccessTokenRemoteMirrorOperation>,
) : WalletDeferredAccessTokenRemoteMirrorPolicy {
    val requests: MutableList<WalletDeferredAccessTokenRemoteMirrorRequest> = mutableListOf()

    override suspend fun allowRemoteMirror(request: WalletDeferredAccessTokenRemoteMirrorRequest): Boolean {
        requests += request
        return request.operation in allowedOperations
    }
}

private class RecordingWalletCredentialStore :
    LocalWalletCredentialStore,
    RemoteWalletCredentialStore,
    HybridWalletCredentialStoreDelegate {
    val calls: MutableList<String> = mutableListOf()
    private val records: MutableMap<String, CredentialRecord> = linkedMapOf()

    override suspend fun putCredential(
        walletInstanceId: String,
        record: CredentialRecord,
    ): IdkResult<CredentialRecord, IdkError> {
        calls += "put:$walletInstanceId:${record.id}"
        records[record.id] = record
        return Ok(record)
    }

    override suspend fun getCredential(
        walletInstanceId: String,
        credentialRecordId: String,
    ): IdkResult<CredentialRecord?, IdkError> {
        calls += "get:$walletInstanceId:$credentialRecordId"
        return Ok(records[credentialRecordId]?.takeIf { it.walletInstanceId == walletInstanceId })
    }

    override suspend fun getMetadata(
        walletInstanceId: String,
        credentialRecordId: String,
    ): IdkResult<CredentialMetadata?, IdkError> {
        calls += "metadata:$walletInstanceId:$credentialRecordId"
        return Ok(records[credentialRecordId]?.takeIf { it.walletInstanceId == walletInstanceId }?.metadata(NOW))
    }

    override suspend fun listMetadata(
        walletInstanceId: String,
        filter: CredentialMetadataFilter,
    ): IdkResult<List<CredentialMetadata>, IdkError> {
        calls += "list:$walletInstanceId"
        return Ok(
            records.values
                .filter { it.walletInstanceId == walletInstanceId }
                .map { it.metadata(NOW) }
                .filter { it.matches(filter) }
        )
    }

    override suspend fun findByCredentialTypeRef(
        walletInstanceId: String,
        ref: CredentialTypeRef,
    ): IdkResult<List<CredentialMetadata>, IdkError> {
        calls += "find:$walletInstanceId:${ref.value}"
        return Ok(
            records.values
                .filter { it.walletInstanceId == walletInstanceId }
                .map { it.metadata(NOW) }
                .filter { it.hasTypeRef(ref) }
        )
    }

    override suspend fun deleteCredential(
        walletInstanceId: String,
        credentialRecordId: String,
    ): IdkResult<Boolean, IdkError> {
        calls += "delete:$walletInstanceId:$credentialRecordId"
        return Ok(records.remove(credentialRecordId) != null)
    }
}
