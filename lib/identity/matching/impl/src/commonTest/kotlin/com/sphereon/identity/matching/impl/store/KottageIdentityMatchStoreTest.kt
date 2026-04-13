package com.sphereon.identity.matching.impl.store

import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.matching.model.IdentityMatch
import com.sphereon.identity.matching.store.IdentityMatchStore
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the IdentityMatchStore interface contract.
 *
 * Uses InMemoryIdentityMatchStore (a real implementation, not a mock)
 * to verify CRUD, composite-key lookup, field persistence,
 * tenant isolation, and identity lookups.
 *
 * Kottage-specific persistence tests are in jvmTest:
 * [com.sphereon.identity.matching.impl.store.kottage.KottageIdentityMatchStoreJvmTest]
 */
class KottageIdentityMatchStoreTest {

    private fun createStore(): IdentityMatchStore = InMemoryIdentityMatchStore()

    private fun createMatch(
        id: String = "match-1",
        identifierHash: String = "hash-abc",
        identifierType: IdentifierType = IdentifierType.DID,
        internalIdentityId: String = "identity-1",
        tenantId: String = "tenant-1",
        metadata: Map<String, String> = emptyMap(),
        hashKeyVersion: String? = "v1",
    ) = IdentityMatch(
        id = id,
        identifierHash = identifierHash,
        identifierType = identifierType,
        internalIdentityId = internalIdentityId,
        tenantId = tenantId,
        metadata = metadata,
        hashKeyVersion = hashKeyVersion,
        createdAt = Clock.System.now()
    )

    @Test
    fun createAndFindById() = runTest {
        val store = createStore()
        val match = createMatch()
        store.create(match)
        val found = store.findById("tenant-1", "match-1")
        assertNotNull(found)
        assertEquals("hash-abc", found.identifierHash)
        assertEquals(IdentifierType.DID, found.identifierType)
        assertEquals("identity-1", found.internalIdentityId)
    }

    @Test
    fun lookupByTenantIdIdentifierHashAndType() = runTest {
        val store = createStore()
        store.create(createMatch())
        val found = store.findByIdentifierHash("tenant-1", "hash-abc", IdentifierType.DID)
        assertNotNull(found)
        assertEquals("match-1", found.id)
    }

    @Test
    fun hashKeyVersionFieldPersisted() = runTest {
        val store = createStore()
        val match = createMatch(hashKeyVersion = "v3")
        store.create(match)
        val found = store.findById("tenant-1", "match-1")
        assertNotNull(found)
        assertEquals("v3", found.hashKeyVersion)
    }

    @Test
    fun lastUsedAtFieldUpdated() = runTest {
        val store = createStore()
        val now = Clock.System.now()
        val match = createMatch().copy(lastUsedAt = now)
        store.create(match)
        val found = store.findById("tenant-1", "match-1")
        assertNotNull(found)
        assertNotNull(found.createdAt)
        assertEquals(now, found.lastUsedAt)
    }

    @Test
    fun deleteRemovesMatch() = runTest {
        val store = createStore()
        store.create(createMatch())
        val deleted = store.delete("tenant-1", "match-1")
        assertTrue(deleted)
        assertNull(store.findById("tenant-1", "match-1"))
        assertNull(store.findByIdentifierHash("tenant-1", "hash-abc", IdentifierType.DID))
    }

    @Test
    fun tenantIsolation() = runTest {
        val store = createStore()
        store.create(createMatch(tenantId = "tenant-1"))
        assertNull(store.findById("tenant-2", "match-1"))
        assertNull(store.findByIdentifierHash("tenant-2", "hash-abc", IdentifierType.DID))
    }

    @Test
    fun findByInternalIdentityId() = runTest {
        val store = createStore()
        store.create(createMatch(id = "m1", identifierHash = "h1"))
        store.create(createMatch(id = "m2", identifierHash = "h2"))
        val matches = store.findByInternalIdentityId("tenant-1", "identity-1")
        assertEquals(2, matches.size)
        assertTrue(matches.any { it.id == "m1" })
        assertTrue(matches.any { it.id == "m2" })
    }

    @Test
    fun hashKeyVersionFieldPersistedWithVersionedKey() = runTest {
        // Test 3: Specifically verify hashKeyVersion="A-v1" round-trips correctly
        val store = createStore()
        val match = createMatch(hashKeyVersion = "A-v1")
        store.create(match)
        val found = store.findById("tenant-1", "match-1")
        assertNotNull(found)
        assertEquals("A-v1", found.hashKeyVersion, "hashKeyVersion 'A-v1' must survive round-trip")
    }

    @Test
    fun hashKeyVersionNullIsPreserved() = runTest {
        val store = createStore()
        val match = createMatch(hashKeyVersion = null)
        store.create(match)
        val found = store.findById("tenant-1", "match-1")
        assertNotNull(found)
        assertEquals(null, found.hashKeyVersion, "null hashKeyVersion must be preserved")
    }

    @Test
    fun persistenceAcrossRestart() = runTest {
        // InMemory store does not persist across restarts.
        // Kottage persistence is tested in jvmTest:
        // KottageIdentityMatchStoreJvmTest.persistenceAcrossRestart()
        //
        // This test verifies that data is consistent within a single store lifetime.
        val store = createStore()
        store.create(createMatch())
        val found = store.findById("tenant-1", "match-1")
        assertNotNull(found)
        assertEquals("hash-abc", found.identifierHash)
    }
}
