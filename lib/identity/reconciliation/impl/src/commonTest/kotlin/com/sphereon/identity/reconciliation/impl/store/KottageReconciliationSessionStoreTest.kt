package com.sphereon.identity.reconciliation.impl.store

import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.reconciliation.model.ReconciliationSession
import com.sphereon.identity.reconciliation.model.ReconciliationSessionStatus
import com.sphereon.identity.reconciliation.store.ReconciliationSessionStore
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Tests for the ReconciliationSessionStore interface contract.
 *
 * Uses InMemoryReconciliationSessionStore (a real implementation, not a mock)
 * to verify CRUD, state index lookup, expiry queries,
 * tenant isolation, and status transitions.
 *
 * Kottage-specific persistence tests are in jvmTest:
 * [com.sphereon.identity.reconciliation.impl.store.kottage.KottageReconciliationSessionStoreJvmTest]
 */
class KottageReconciliationSessionStoreTest {

    private fun createStore(): ReconciliationSessionStore = InMemoryReconciliationSessionStore()

    private fun createSession(
        id: String = "session-1",
        tenantId: String = "tenant-1",
        status: ReconciliationSessionStatus = ReconciliationSessionStatus.CREATED,
        state: String? = "state-abc",
        expiresIn: kotlin.time.Duration = 10.minutes
    ): ReconciliationSession {
        val now = Clock.System.now()
        return ReconciliationSession(
            id = id,
            tenantId = tenantId,
            status = status,
            identifierHash = "hash-123",
            identifierType = IdentifierType.DID,
            providerId = "provider-1",
            authorizationUrl = "https://idp.example.com/authorize",
            state = state,
            nonce = "nonce-xyz",
            codeVerifier = "verifier-123",
            redirectUri = "https://app.example.com/callback",
            createdAt = now,
            expiresAt = now + expiresIn
        )
    }

    @Test
    fun createAndFindById() = runTest {
        val store = createStore()
        store.create(createSession())
        val found = store.findById("tenant-1", "session-1")
        assertNotNull(found)
        assertEquals(ReconciliationSessionStatus.CREATED, found.status)
        assertEquals("hash-123", found.identifierHash)
        assertEquals("provider-1", found.providerId)
    }

    @Test
    fun findByState() = runTest {
        val store = createStore()
        store.create(createSession(state = "unique-state"))
        val found = store.findByState("tenant-1", "unique-state")
        assertNotNull(found)
        assertEquals("session-1", found.id)
    }

    @Test
    fun updateSessionStatus() = runTest {
        val store = createStore()
        store.create(createSession())
        val session = store.findById("tenant-1", "session-1")!!
        store.update(session.copy(status = ReconciliationSessionStatus.COMPLETED))
        val updated = store.findById("tenant-1", "session-1")
        assertNotNull(updated)
        assertEquals(ReconciliationSessionStatus.COMPLETED, updated.status)
    }

    @Test
    fun sessionCleanupExpiry() = runTest {
        val store = createStore()
        store.create(createSession(id = "expired", expiresIn = (-5).minutes, state = "s1"))
        store.create(createSession(id = "valid", state = "s2", expiresIn = 10.minutes))
        val expired = store.findExpired("tenant-1", Clock.System.now())
        assertEquals(1, expired.size)
        assertEquals("expired", expired[0].id)
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
    fun tenantIsolation() = runTest {
        val store = createStore()
        store.create(createSession(tenantId = "tenant-1"))
        assertNull(store.findById("tenant-2", "session-1"))
        assertNull(store.findByState("tenant-2", "state-abc"))
    }

    @Test
    fun persistenceAcrossRestart() = runTest {
        // InMemory store does not persist across restarts.
        // Kottage persistence is tested in jvmTest:
        // KottageReconciliationSessionStoreJvmTest.persistenceAcrossRestart()
        //
        // This test verifies that data is consistent within a single store lifetime.
        val store = createStore()
        store.create(createSession())
        val found = store.findById("tenant-1", "session-1")
        assertNotNull(found)
        assertEquals("hash-123", found.identifierHash)
    }
}
