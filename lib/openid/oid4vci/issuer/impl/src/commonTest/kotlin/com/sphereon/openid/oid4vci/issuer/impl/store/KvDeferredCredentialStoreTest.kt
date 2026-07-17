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

package com.sphereon.openid.oid4vci.issuer.impl.store

import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialEntry
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialStatus
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class KvDeferredCredentialStoreTest {
    private val instanceId = "issuer-instance-deferred-credential-store"

    private fun createStore(): DeferredCredentialStore {
        val kvStoreManager = InMemoryTestKvStoreManager()
        return KvDeferredCredentialStore(
            kvStoreManager = kvStoreManager,
            execution = NoOpSessionExecution(),
        )
    }

    @Test
    fun createAndRetrieveDeferredEntry() =
        runTest {
            val store = createStore()
            val now = Clock.System.now().epochSeconds

            val entry =
                DeferredCredentialEntry(
                    transactionId = "txn-001",
                    issuanceSessionId = "session-abc",
                    instanceId = instanceId,
                    credentialConfigurationId = "IdentityCredential",
                    status = DeferredCredentialStatus.PENDING,
                    retryAfterSeconds = 10,
                    createdAt = now,
                    expiresAt = now + 3600,
                )

            val createResult = store.create(entry)
            assertTrue(createResult.isOk, "create should succeed")
            assertEquals(entry, createResult.getOrThrow())

            val getResult = store.get("txn-001")
            assertTrue(getResult.isOk, "get should succeed")
            val retrieved = getResult.getOrThrow()
            assertNotNull(retrieved)
            assertEquals("txn-001", retrieved.transactionId)
            assertEquals("session-abc", retrieved.issuanceSessionId)
            assertEquals(instanceId, retrieved.instanceId)
            assertEquals("IdentityCredential", retrieved.credentialConfigurationId)
            assertEquals(DeferredCredentialStatus.PENDING, retrieved.status)
            assertNull(retrieved.credentialResponse)
            assertEquals(10, retrieved.retryAfterSeconds)
            assertEquals(now, retrieved.createdAt)
            assertEquals(now + 3600, retrieved.expiresAt)
        }

    @Test
    fun getReturnsNullForUnknownTransactionId() =
        runTest {
            val store = createStore()

            val result = store.get("txn-unknown")
            assertTrue(result.isOk, "get should succeed")
            assertNull(result.getOrThrow())
        }

    @Test
    fun updateTransitionsStatusFromPendingToReady() =
        runTest {
            val store = createStore()
            val now = Clock.System.now().epochSeconds

            val entry =
                DeferredCredentialEntry(
                    transactionId = "txn-002",
                    issuanceSessionId = "session-def",
                    instanceId = instanceId,
                    credentialConfigurationId = "IdentityCredential",
                    status = DeferredCredentialStatus.PENDING,
                    createdAt = now,
                    expiresAt = now + 3600,
                )
            store.create(entry)

            val updated =
                entry.copy(
                    status = DeferredCredentialStatus.READY,
                    credentialResponse = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.credential.sig"),
                )
            val updateResult = store.update(updated)
            assertTrue(updateResult.isOk, "update should succeed")
            assertEquals(DeferredCredentialStatus.READY, updateResult.getOrThrow().status)

            val getResult = store.get("txn-002")
            assertTrue(getResult.isOk, "get should succeed")
            val retrieved = getResult.getOrThrow()
            assertNotNull(retrieved)
            assertEquals(DeferredCredentialStatus.READY, retrieved.status)
            assertNotNull(retrieved.credentialResponse)
            assertEquals(JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.credential.sig"), retrieved.credentialResponse)
        }

    @Test
    fun updateTransitionsReadyToDelivered() =
        runTest {
            val store = createStore()
            val now = Clock.System.now().epochSeconds

            val entry =
                DeferredCredentialEntry(
                    transactionId = "txn-003",
                    issuanceSessionId = "session-ghi",
                    instanceId = instanceId,
                    credentialConfigurationId = "IdentityCredential",
                    status = DeferredCredentialStatus.READY,
                    credentialResponse = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.credential.sig"),
                    createdAt = now,
                    expiresAt = now + 3600,
                )
            store.create(entry)

            val delivered = entry.copy(status = DeferredCredentialStatus.DELIVERED)
            val updateResult = store.update(delivered)
            assertTrue(updateResult.isOk, "update should succeed")
            assertEquals(DeferredCredentialStatus.DELIVERED, updateResult.getOrThrow().status)

            val getResult = store.get("txn-003")
            assertTrue(getResult.isOk, "get should succeed")
            val retrieved = getResult.getOrThrow()
            assertNotNull(retrieved)
            assertEquals(DeferredCredentialStatus.DELIVERED, retrieved.status)
        }
}
