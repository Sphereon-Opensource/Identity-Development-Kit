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

package com.sphereon.identity.matching.impl.store.kottage

import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.matching.model.IdentityMatch
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

/**
 * Tests for KottageIdentityMatchStore.update() index repair (Finding #6 fix).
 *
 * When an identity match is updated with a changed identifierHash or internalIdentityId,
 * the old index entries must be removed and new ones created. Without this fix,
 * stale index entries would remain, causing ghost lookups or missed lookups.
 */
class KottageIdentityMatchStoreIndexRepairTest {

    private val testDir = Files.createTempDirectory("kottage-index-repair-test").toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun createKottage(): Kottage = Kottage(
        name = "index-repair-test",
        directoryPath = testDir,
        environment = KottageEnvironment(context = KottageContext()),
        scope = scope
    )

    private fun createStore(kottage: Kottage = createKottage()): KottageIdentityMatchStore {
        val kvStoreFactory = KottageKvStoreFactory(kottage)
        val kvStore = kvStoreFactory.create(KottageKvStoreConfig(id = "identity-matches", scopeBinding = com.sphereon.data.store.kv.KvStoreScopeBinding.APP))
        return KottageIdentityMatchStore(store = kvStore)
    }

    private fun createMatch(
        id: String = "match-1",
        identifierHash: String = "hash1",
        identifierType: IdentifierType = IdentifierType.KEY,
        internalIdentityId: String = "identity1",
        tenantId: String = "tenant-1",
        hashKeyVersion: String? = "v1",
    ) = IdentityMatch(
        id = id,
        identifierHash = identifierHash,
        identifierType = identifierType,
        internalIdentityId = internalIdentityId,
        tenantId = tenantId,
        hashKeyVersion = hashKeyVersion,
        createdAt = Clock.System.now()
    )

    @AfterTest
    fun cleanup() {
        java.io.File(testDir).deleteRecursively()
    }

    @Test
    fun updateIdentifierHashRemovesOldHashIndex() = runTest {
        val store = createStore()

        // Step 1: Create a match with hash1
        val original = createMatch(identifierHash = "hash1")
        store.create(original)

        // Verify the original hash index works
        val foundByHash1 = store.findByIdentifierHash("tenant-1", "hash1", IdentifierType.KEY)
        assertNotNull(foundByHash1, "Should find match by original hash1")
        assertEquals("match-1", foundByHash1.id)

        // Step 2: Update with hash2
        val updated = original.copy(identifierHash = "hash2", updatedAt = Clock.System.now())
        store.update(updated)

        // Step 3: Old hash index (hash1) should be gone
        val staleResult = store.findByIdentifierHash("tenant-1", "hash1", IdentifierType.KEY)
        assertNull(staleResult, "Old hash index for 'hash1' should be removed after update")

        // Step 4: New hash index (hash2) should work
        val newResult = store.findByIdentifierHash("tenant-1", "hash2", IdentifierType.KEY)
        assertNotNull(newResult, "New hash index for 'hash2' should be created after update")
        assertEquals("match-1", newResult.id)
        assertEquals("hash2", newResult.identifierHash)
    }

    @Test
    fun updateInternalIdentityIdRepairsIdentityIndex() = runTest {
        val store = createStore()

        // Step 1: Create a match linked to identity1
        val original = createMatch(internalIdentityId = "identity1")
        store.create(original)

        // Verify identity1 lookup works
        val byIdentity1 = store.findByInternalIdentityId("tenant-1", "identity1")
        assertEquals(1, byIdentity1.size, "Should find 1 match for identity1")

        // Step 2: Update to link to identity2
        val updated = original.copy(internalIdentityId = "identity2", updatedAt = Clock.System.now())
        store.update(updated)

        // Step 3: Old identity index (identity1) should be empty
        val staleIdentity = store.findByInternalIdentityId("tenant-1", "identity1")
        assertTrue(staleIdentity.isEmpty(), "Old identity index for 'identity1' should be empty after update")

        // Step 4: New identity index (identity2) should contain the match
        val newIdentity = store.findByInternalIdentityId("tenant-1", "identity2")
        assertEquals(1, newIdentity.size, "New identity index for 'identity2' should contain the match")
        assertEquals("match-1", newIdentity[0].id)
    }

    @Test
    fun updateBothHashAndIdentityIdRepairsBothIndexes() = runTest {
        val store = createStore()

        // Create original
        val original = createMatch(identifierHash = "hash1", internalIdentityId = "identity1")
        store.create(original)

        // Update both fields
        val updated = original.copy(
            identifierHash = "hash2",
            internalIdentityId = "identity2",
            updatedAt = Clock.System.now()
        )
        store.update(updated)

        // Old indexes should be gone
        assertNull(
            store.findByIdentifierHash("tenant-1", "hash1", IdentifierType.KEY),
            "Old hash index should be removed"
        )
        assertTrue(
            store.findByInternalIdentityId("tenant-1", "identity1").isEmpty(),
            "Old identity index should be empty"
        )

        // New indexes should work
        assertNotNull(
            store.findByIdentifierHash("tenant-1", "hash2", IdentifierType.KEY),
            "New hash index should exist"
        )
        assertEquals(
            1,
            store.findByInternalIdentityId("tenant-1", "identity2").size,
            "New identity index should contain the match"
        )
    }

    @Test
    fun updateWithUnchangedHashDoesNotBreakIndex() = runTest {
        val store = createStore()

        val original = createMatch(identifierHash = "hash1")
        store.create(original)

        // Update metadata only, hash stays the same
        val updated = original.copy(
            metadata = mapOf("foo" to "bar"),
            updatedAt = Clock.System.now()
        )
        store.update(updated)

        // Hash index should still work
        val found = store.findByIdentifierHash("tenant-1", "hash1", IdentifierType.KEY)
        assertNotNull(found, "Hash index should survive an update that doesn't change the hash")
        assertEquals("bar", found.metadata["foo"])
    }

    @Test
    fun updateIdentityIdWithMultipleMatchesOnlyMovesUpdatedMatch() = runTest {
        val store = createStore()

        // Create two matches both linked to identity1
        store.create(createMatch(id = "m1", identifierHash = "h1", internalIdentityId = "identity1"))
        store.create(createMatch(id = "m2", identifierHash = "h2", internalIdentityId = "identity1"))

        val byIdentity1Before = store.findByInternalIdentityId("tenant-1", "identity1")
        assertEquals(2, byIdentity1Before.size, "Should have 2 matches for identity1")

        // Move m1 to identity2
        val m1 = store.findById("tenant-1", "m1")!!
        store.update(m1.copy(internalIdentityId = "identity2", updatedAt = Clock.System.now()))

        // identity1 should now have only m2
        val byIdentity1After = store.findByInternalIdentityId("tenant-1", "identity1")
        assertEquals(1, byIdentity1After.size, "identity1 should have only m2 remaining")
        assertEquals("m2", byIdentity1After[0].id)

        // identity2 should have m1
        val byIdentity2 = store.findByInternalIdentityId("tenant-1", "identity2")
        assertEquals(1, byIdentity2.size, "identity2 should have m1")
        assertEquals("m1", byIdentity2[0].id)
    }
}
