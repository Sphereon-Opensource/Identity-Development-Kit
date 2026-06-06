/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.openid.wallet.impl.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobInfoType
import com.sphereon.data.store.blob.InMemoryBlobStoreConfig
import com.sphereon.data.store.blob.MetadataSearchQuery
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
import com.sphereon.openid.wallet.CredentialInstanceState
import com.sphereon.openid.wallet.IdentifierRef
import com.sphereon.openid.wallet.WalletCredentialInstance
import com.sphereon.openid.wallet.WalletDocument
import com.sphereon.openid.wallet.WalletRefreshState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Test doubles — mirrors DefaultBlobServiceTest / TestSupport.kt pattern
// ---------------------------------------------------------------------------

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

private fun createTestBlobService(): DefaultBlobService {
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
        tempUrlPolicy =
            com.sphereon.data.store.blob
                .DefaultTempUrlPolicy(),
        eventService = TestSessionEventService(),
        execution = TestSessionExecution(),
    )
}

/**
 * Wraps a [DefaultBlobService] and counts every `getBlob` call whose path starts with
 * the body prefix "wallet-documents/". Used to assert that metadata-only operations
 * never load body blobs.
 */
private class BodyReadTrackingBlobService(
    private val delegate: DefaultBlobService,
    private val bodyPathPrefix: String = "wallet-documents/",
) : com.sphereon.data.store.blob.BlobService by delegate {
    var bodyReadCount: Int = 0
        private set

    fun resetBodyReadCount() {
        bodyReadCount = 0
    }

    override suspend fun getBlob(info: BlobInfoType): IdkResult<ResolvedBlobInfo, IdkError> {
        val path = info.path
        if (path != null && path.startsWith(bodyPathPrefix)) {
            bodyReadCount++
        }
        return delegate.getBlob(info)
    }
}

private fun createTrackingStore(): Pair<BodyReadTrackingBlobService, BlobWalletDocumentStore> {
    val realService = createTestBlobService()
    val tracking = BodyReadTrackingBlobService(realService)
    return tracking to BlobWalletDocumentStore(tracking)
}

// ---------------------------------------------------------------------------
// Test data helpers
// ---------------------------------------------------------------------------

private fun makeDoc(
    id: String,
    credentialTypeId: String,
    issuerValue: String = "issuer:test",
): WalletDocument =
    WalletDocument(
        id = id,
        issuer = IdentifierRef(type = IdentifierType("url"), value = issuerValue),
        credentialTypeId = credentialTypeId,
        refresh = WalletRefreshState(),
        credentials =
            listOf(
                WalletCredentialInstance(
                    credentialId = "$id-cred-1",
                    format = "jwt_vc",
                    raw = "eyJ...",
                    holderKeyAlias = "holder-key",
                    state = CredentialInstanceState.ACTIVE,
                ),
            ),
    )

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class BlobWalletDocumentStoreTest {
    private fun buildStore(): BlobWalletDocumentStore {
        val blobService = createTestBlobService()
        return BlobWalletDocumentStore(blobService)
    }

    @Test
    fun upsertAndGetRoundtrip() =
        runTest {
            val store = buildStore()
            val doc = makeDoc("doc-1", "vct:emp")

            val upsertResult = store.upsert(doc)
            assertTrue(upsertResult.isOk, "upsert should succeed: ${if (upsertResult.isErr) upsertResult.error else ""}")

            val getResult = store.get("doc-1")
            assertTrue(getResult.isOk, "get should succeed")
            assertNotNull(getResult.value)
            assertEquals("doc-1", getResult.value!!.id)
            assertEquals("vct:emp", getResult.value!!.credentialTypeId)
            assertEquals(1, getResult.value!!.credentials.size)
            assertEquals(
                "doc-1-cred-1",
                getResult.value!!
                    .credentials
                    .first()
                    .credentialId
            )
            assertEquals(
                CredentialInstanceState.ACTIVE,
                getResult.value!!
                    .credentials
                    .first()
                    .state
            )
        }

    @Test
    fun listMetadataReturnsBothDocuments() =
        runTest {
            val store = buildStore()
            val doc1 = makeDoc("doc-a", "vct:emp")
            val doc2 = makeDoc("doc-b", "vct:id")
            store.upsert(doc1)
            store.upsert(doc2)

            val listResult = store.listMetadata()
            assertTrue(listResult.isOk, "listMetadata should succeed")
            assertEquals(2, listResult.value.size)
            val ids = listResult.value.map { it.documentId }.toSet()
            assertTrue(ids.contains("doc-a"), "doc-a should be in metadata list")
            assertTrue(ids.contains("doc-b"), "doc-b should be in metadata list")
        }

    @Test
    fun getMetadataMatchesDocumentMetadata() =
        runTest {
            val store = buildStore()
            val doc = makeDoc("doc-meta", "vct:emp")
            store.upsert(doc)

            val metaResult = store.getMetadata("doc-meta")
            assertTrue(metaResult.isOk, "getMetadata should succeed")
            assertNotNull(metaResult.value)
            val meta = metaResult.value!!
            assertEquals("doc-meta", meta.documentId)
            assertEquals("vct:emp", meta.credentialType)
            assertEquals(doc.issuer, meta.issuer)
        }

    @Test
    fun getMetadataReturnsNullForMissingDocument() =
        runTest {
            val store = buildStore()
            val metaResult = store.getMetadata("nonexistent-doc")
            assertTrue(metaResult.isOk, "getMetadata for missing doc should return Ok(null)")
            assertNull(metaResult.value)
        }

    @Test
    fun findMetadataByCredentialTypeFilters() =
        runTest {
            val store = buildStore()
            store.upsert(makeDoc("doc-emp", "vct:emp"))
            store.upsert(makeDoc("doc-id", "vct:id"))

            val findResult = store.findMetadataByCredentialType("vct:emp")
            assertTrue(findResult.isOk, "findMetadataByCredentialType should succeed")
            assertEquals(1, findResult.value.size)
            assertEquals("doc-emp", findResult.value.first().documentId)
            assertEquals("vct:emp", findResult.value.first().credentialType)
        }

    @Test
    fun deleteRemovesDocument() =
        runTest {
            val store = buildStore()
            store.upsert(makeDoc("doc-del", "vct:emp"))

            val deleteResult = store.delete("doc-del")
            assertTrue(deleteResult.isOk, "delete should succeed")
            assertTrue(deleteResult.value, "delete should return true")

            val getResult = store.get("doc-del")
            assertTrue(getResult.isOk, "get after delete should succeed (returning null)")
            assertNull(getResult.value, "deleted document should not be found")
        }

    @Test
    fun getReturnsNullForMissingDocument() =
        runTest {
            val store = buildStore()
            val getResult = store.get("nonexistent-doc")
            assertTrue(getResult.isOk, "get for missing doc should return Ok(null)")
            assertNull(getResult.value)
        }

    @Test
    fun getStillReturnsFullDocument() =
        runTest {
            val store = buildStore()
            store.upsert(makeDoc("doc-full", "vct:emp"))

            val getResult = store.get("doc-full")
            assertTrue(getResult.isOk, "get should succeed")
            assertNotNull(getResult.value)
            assertEquals("doc-full", getResult.value!!.id)
            assertEquals(1, getResult.value!!.credentials.size, "full document should include credential instances")
        }

    @Test
    fun upsertPreservesInstanceCollection() =
        runTest {
            val store = buildStore()
            val doc =
                WalletDocument(
                    id = "multi-inst",
                    issuer = IdentifierRef(type = IdentifierType("url"), value = "issuer:test"),
                    credentialTypeId = "vct:emp",
                    credentials =
                        listOf(
                            WalletCredentialInstance(
                                credentialId = "cred-1",
                                format = "jwt_vc",
                                raw = "eyA...",
                                holderKeyAlias = "key1",
                                state = CredentialInstanceState.ACTIVE,
                            ),
                            WalletCredentialInstance(
                                credentialId = "cred-2",
                                format = "jwt_vc",
                                raw = "eyB...",
                                holderKeyAlias = "key2",
                                state = CredentialInstanceState.USED,
                            ),
                        ),
                )

            store.upsert(doc)
            val retrieved = store.get("multi-inst")
            assertTrue(retrieved.isOk)
            assertEquals(2, retrieved.value!!.credentials.size)
            assertEquals(CredentialInstanceState.USED, retrieved.value!!.credentials[1].state)
        }

    // -----------------------------------------------------------------------
    // Sidecar tests (Task 3)
    // -----------------------------------------------------------------------

    @Test
    fun upsertWritesSidecarBlob() =
        runTest {
            val (tracking, store) = createTrackingStore()
            val doc = makeDoc("sc-1", "vct:emp")
            val upsertResult = store.upsert(doc)
            assertTrue(upsertResult.isOk, "upsert should succeed")

            // The sidecar at wallet-document-meta/sc-1 must be readable directly from the delegate.
            val sidecarInfo = BlobInfo(path = "wallet-document-meta/sc-1")
            val sidecarResult = tracking.getBlob(sidecarInfo)
            assertTrue(sidecarResult.isOk, "sidecar blob must exist after upsert")
            val sidecarJson = sidecarResult.value.data.decodeToString()
            assertTrue(sidecarJson.contains("\"sc-1\""), "sidecar JSON must contain documentId")
            assertTrue(sidecarJson.contains("vct:emp"), "sidecar JSON must contain credentialType")
        }

    @Test
    fun listMetadataDoesNotReadBodyBlobs() =
        runTest {
            val (tracking, store) = createTrackingStore()
            store.upsert(makeDoc("sc-a", "vct:emp"))
            store.upsert(makeDoc("sc-b", "vct:id"))

            // Reset counter after upserts (which do write body blobs via storeBlob, not getBlob).
            tracking.resetBodyReadCount()

            val listResult = store.listMetadata()
            assertTrue(listResult.isOk, "listMetadata should succeed")
            assertEquals(2, listResult.value.size, "must return both documents")
            assertEquals(
                0,
                tracking.bodyReadCount,
                "listMetadata must not read any body blob (wallet-documents/*) — got ${tracking.bodyReadCount} body read(s)",
            )
        }

    @Test
    fun getMetadataDoesNotReadBodyBlob() =
        runTest {
            val (tracking, store) = createTrackingStore()
            store.upsert(makeDoc("sc-meta", "vct:emp"))
            tracking.resetBodyReadCount()

            val metaResult = store.getMetadata("sc-meta")
            assertTrue(metaResult.isOk, "getMetadata should succeed")
            assertNotNull(metaResult.value)
            assertEquals("sc-meta", metaResult.value!!.documentId)
            assertEquals(
                0,
                tracking.bodyReadCount,
                "getMetadata must not read any body blob — got ${tracking.bodyReadCount} body read(s)",
            )
        }

    @Test
    fun findMetadataByCredentialTypeDoesNotReadBodyBlobs() =
        runTest {
            val (tracking, store) = createTrackingStore()
            store.upsert(makeDoc("sc-emp", "vct:emp"))
            store.upsert(makeDoc("sc-id", "vct:id"))
            tracking.resetBodyReadCount()

            val findResult = store.findMetadataByCredentialType("vct:emp")
            assertTrue(findResult.isOk, "findMetadataByCredentialType should succeed")
            assertEquals(1, findResult.value.size)
            assertEquals("sc-emp", findResult.value.first().documentId)
            assertEquals(
                0,
                tracking.bodyReadCount,
                "findMetadataByCredentialType must not read any body blob — got ${tracking.bodyReadCount} body read(s)",
            )
        }

    @Test
    fun sidecarMetadataRoundtrip() =
        runTest {
            val store = buildStore()
            val doc = makeDoc("sc-rt", "vct:emp", issuerValue = "did:example:issuer")
            store.upsert(doc)

            val getResult = store.getMetadata("sc-rt")
            assertTrue(getResult.isOk)
            val meta = getResult.value!!
            assertEquals("sc-rt", meta.documentId)
            assertEquals("vct:emp", meta.credentialType)
            assertEquals("did:example:issuer", meta.issuer.value)
        }

    @Test
    fun deleteSidecarAlongWithBody() =
        runTest {
            val (tracking, store) = createTrackingStore()
            store.upsert(makeDoc("sc-del", "vct:emp"))

            val deleteResult = store.delete("sc-del")
            assertTrue(deleteResult.isOk, "delete should succeed")
            assertTrue(deleteResult.value, "delete should return true")

            // Body must be gone.
            val getResult = store.get("sc-del")
            assertTrue(getResult.isOk)
            assertNull(getResult.value, "body should be gone after delete")

            // Sidecar must be gone: getMetadata must return null.
            val metaResult = store.getMetadata("sc-del")
            assertTrue(metaResult.isOk)
            assertNull(metaResult.value, "sidecar should be gone after delete")

            // listMetadata should be empty.
            val listResult = store.listMetadata()
            assertTrue(listResult.isOk)
            assertFalse(listResult.value.any { it.documentId == "sc-del" }, "deleted doc must not appear in list")
        }

    @Test
    fun getStillReadsBodyNotSidecar() =
        runTest {
            val (tracking, store) = createTrackingStore()
            val doc = makeDoc("sc-body", "vct:emp")
            store.upsert(doc)
            tracking.resetBodyReadCount()

            val getResult = store.get("sc-body")
            assertTrue(getResult.isOk)
            assertNotNull(getResult.value)
            assertEquals(1, tracking.bodyReadCount, "get() must read exactly the body blob")
            assertEquals(1, getResult.value!!.credentials.size, "full credential instances must be present")
        }
}
