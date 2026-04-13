/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.identity.reconciliation.impl.store.kottage

import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.reconciliation.model.ReconciliationSession
import com.sphereon.identity.reconciliation.model.ReconciliationSessionStatus
import com.sphereon.data.store.kv.KottageKvStoreConfig
import com.sphereon.data.store.kv.kottage.KottageKvStoreFactory
import io.github.irgaly.kottage.Kottage
import io.github.irgaly.kottage.KottageEnvironment
import io.github.irgaly.kottage.platform.KottageContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class KottageReconciliationSessionStoreJvmTest {

    private val testDir = Files.createTempDirectory("kottage-recon-test").toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun createKottage(name: String = "test"): Kottage = Kottage(
        name = name,
        directoryPath = testDir,
        environment = KottageEnvironment(context = KottageContext()),
        scope = scope
    )

    private fun createStore(kottage: Kottage = createKottage()): KottageReconciliationSessionStore {
        val kvStoreFactory = KottageKvStoreFactory(kottage)
        val kvStore = kvStoreFactory.create(KottageKvStoreConfig(id = "reconciliation-sessions", scopeBinding = com.sphereon.data.store.kv.KvStoreScopeBinding.APP))
        return KottageReconciliationSessionStore(store = kvStore)
    }

    private fun createSession(
        id: String = "session-1",
        tenantId: String = "tenant-1",
        state: String? = "state-abc",
        expiresAt: kotlinx.datetime.Instant = Clock.System.now() + 30.minutes
    ) = ReconciliationSession(
        id = id,
        tenantId = tenantId,
        status = ReconciliationSessionStatus.CREATED,
        identifierHash = "hash-xyz",
        identifierType = IdentifierType.KEY,
        providerId = "surf",
        state = state,
        nonce = "nonce-123",
        createdAt = Clock.System.now(),
        expiresAt = expiresAt
    )

    @AfterTest
    fun cleanup() {
        java.io.File(testDir).deleteRecursively()
    }

    @Test
    fun createAndFindById() = runTest {
        val store = createStore()
        store.create(createSession())
        val found = store.findById("tenant-1", "session-1")
        assertNotNull(found)
        assertEquals(ReconciliationSessionStatus.CREATED, found.status)
        assertEquals("surf", found.providerId)
    }

    @Test
    fun findByState() = runTest {
        val store = createStore()
        store.create(createSession())
        val found = store.findByState("tenant-1", "state-abc")
        assertNotNull(found)
        assertEquals("session-1", found.id)
    }

    @Test
    fun findByStateWrongTenant() = runTest {
        val store = createStore()
        store.create(createSession())
        assertNull(store.findByState("tenant-2", "state-abc"))
    }

    @Test
    fun updateSession() = runTest {
        val store = createStore()
        store.create(createSession())
        val updated = createSession().copy(
            status = ReconciliationSessionStatus.COMPLETED,
            state = "state-new"
        )
        store.update(updated)
        val found = store.findById("tenant-1", "session-1")
        assertNotNull(found)
        assertEquals(ReconciliationSessionStatus.COMPLETED, found.status)

        // Old state index should be cleaned up
        assertNull(store.findByState("tenant-1", "state-abc"))
        // New state index should work
        assertNotNull(store.findByState("tenant-1", "state-new"))
    }

    @Test
    fun deleteSession() = runTest {
        val store = createStore()
        store.create(createSession())
        val deleted = store.delete("tenant-1", "session-1")
        assertTrue(deleted)
        assertNull(store.findById("tenant-1", "session-1"))
        assertNull(store.findByState("tenant-1", "state-abc"))
    }

    @Test
    fun findExpired() = runTest {
        val store = createStore()
        val past = Clock.System.now() - 1.hours
        store.create(createSession(id = "expired-session", expiresAt = past, state = "state-exp"))
        store.create(createSession(id = "valid-session", state = "state-valid"))

        val expired = store.findExpired("tenant-1", Clock.System.now())
        assertEquals(1, expired.size)
        assertEquals("expired-session", expired[0].id)
    }

    @Test
    fun persistenceAcrossRestart() = runTest {
        val kottage = createKottage("persist-test")
        val kvStoreFactory = KottageKvStoreFactory(kottage)
        val store1 = KottageReconciliationSessionStore(store = kvStoreFactory.create(KottageKvStoreConfig(id = "reconciliation-sessions", scopeBinding = com.sphereon.data.store.kv.KvStoreScopeBinding.APP)))
        store1.create(createSession())

        val store2 = KottageReconciliationSessionStore(store = kvStoreFactory.create(KottageKvStoreConfig(id = "reconciliation-sessions", scopeBinding = com.sphereon.data.store.kv.KvStoreScopeBinding.APP)))
        val found = store2.findById("tenant-1", "session-1")
        assertNotNull(found)
        assertEquals("session-1", found.id)
    }
}
