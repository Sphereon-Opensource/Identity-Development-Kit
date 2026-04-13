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

package com.sphereon.identity.matching.impl.store.kottage

import com.sphereon.data.store.kv.KottageKvStoreConfig
import com.sphereon.data.store.kv.kottage.KottageKvStoreFactory
import com.sphereon.identity.matching.crypto.EncryptedPayload
import com.sphereon.identity.matching.model.AssuranceSummary
import com.sphereon.identity.matching.model.IdentityLinkBinding
import com.sphereon.identity.matching.model.PersistedAttributesEnvelope
import io.github.irgaly.kottage.Kottage
import io.github.irgaly.kottage.KottageEnvironment
import io.github.irgaly.kottage.platform.KottageContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Comprehensive CRUD and index tests for KottageIdentityLinkBindingStore (Test 5).
 *
 * Tests cover:
 * 1. Create + read-back with all fields verified
 * 2. Update with indexed field changes
 * 3. Delete and verify gone
 * 4. findByHolderHash lookup
 * 5. findByMatchId lookup
 */
class KottageIdentityLinkBindingStoreCrudTest {
    private val testDir = Files.createTempDirectory("kottage-binding-crud-test").toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun createKottage(): Kottage =
        Kottage(
            name = "binding-crud-test",
            directoryPath = testDir,
            environment = KottageEnvironment(context = KottageContext()),
            scope = scope,
        )

    private fun createStore(kottage: Kottage = createKottage()): KottageIdentityLinkBindingStore {
        val kvStoreFactory = KottageKvStoreFactory(kottage)
        val kvStore = kvStoreFactory.create(KottageKvStoreConfig(id = "identity-link-bindings", scopeBinding = com.sphereon.data.store.kv.KvStoreScopeBinding.APP))
        return KottageIdentityLinkBindingStore(store = kvStore)
    }

    private fun createBinding(
        id: String = "binding-1",
        tenantId: String = "tenant-1",
        matchId: String = "match-1",
        holderIdentifierHash: String = "holder-hash-abc",
        holderHashKeyVersion: String = "A-v1",
        institutionIdentifierHash: String? = "inst-hash-def",
        institutionHashKeyVersion: String? = "B-v1",
        providerId: String = "surf",
        institutionId: String? = "kw1c",
        persistedAttributesEnvelope: PersistedAttributesEnvelope =
            PersistedAttributesEnvelope(
                encrypted =
                    EncryptedPayload(
                        ciphertext = "ciphertext",
                        keyVersion = "C-v1",
                    ),
                canonicalSchemaVersion = "1",
                materialProfileVersion = "profile-v1",
                selectorRuleVersion = "rules-v1",
                attributeNames = setOf("sub"),
                updatedAt = Clock.System.now(),
            ),
    ) = IdentityLinkBinding(
        id = id,
        tenantId = tenantId,
        matchId = matchId,
        holderIdentifierHash = holderIdentifierHash,
        holderHashKeyVersion = holderHashKeyVersion,
        institutionIdentifierHash = institutionIdentifierHash,
        institutionHashKeyVersion = institutionHashKeyVersion,
        encryptedInstitutionId = null,
        persistedAttributesEnvelope = persistedAttributesEnvelope,
        providerId = providerId,
        institutionId = institutionId,
        assuranceSummary =
            AssuranceSummary(
                walletAssuranceLevel = "high",
                oidcAcr = "urn:mace:surfnet.nl:assurance:loa3",
            ),
        createdAt = Clock.System.now(),
        updatedAt = null,
        lastUsedAt = null,
    )

    @AfterTest
    fun cleanup() {
        java.io.File(testDir).deleteRecursively()
    }

    // ---- Test 5.1: Create and read back, verify all fields ----

    @Test
    fun createAndReadBack_allFieldsPreserved() =
        runTest {
            val store = createStore()
            val binding = createBinding()
            store.create(binding)

            val found = store.findByMatchId("tenant-1", "match-1")
            assertNotNull(found, "Binding should be found by matchId after create")
            assertEquals("binding-1", found.id)
            assertEquals("tenant-1", found.tenantId)
            assertEquals("match-1", found.matchId)
            assertEquals("holder-hash-abc", found.holderIdentifierHash)
            assertEquals("A-v1", found.holderHashKeyVersion)
            assertEquals("inst-hash-def", found.institutionIdentifierHash)
            assertEquals("B-v1", found.institutionHashKeyVersion)
            assertNull(found.encryptedInstitutionId)
            assertNotNull(found.persistedAttributesEnvelope)
            assertEquals("surf", found.providerId)
            assertEquals("kw1c", found.institutionId)
            assertNotNull(found.assuranceSummary)
            assertEquals("high", found.assuranceSummary?.walletAssuranceLevel)
            assertEquals("urn:mace:surfnet.nl:assurance:loa3", found.assuranceSummary?.oidcAcr)
            assertNotNull(found.createdAt)
            assertNull(found.updatedAt)
            assertNull(found.lastUsedAt)
        }

    @Test
    fun createAndReadBack_preservedPersistedAttributesEnvelope() =
        runTest {
            val store = createStore()
            val now = Clock.System.now()
            val binding =
                createBinding().copy(
                    persistedAttributesEnvelope =
                        PersistedAttributesEnvelope(
                            encrypted =
                                EncryptedPayload(
                                    ciphertext = "ciphertext",
                                    keyVersion = "C-v1",
                                ),
                            canonicalSchemaVersion = "1",
                            materialProfileVersion = "profile-v1",
                            selectorRuleVersion = "rules-v1",
                            attributeNames = setOf("sub", "email"),
                            materialFingerprints = setOf("fp-1"),
                            updatedAt = now,
                        ),
                )

            store.create(binding)

            val found = store.findByMatchId("tenant-1", "match-1")
            assertNotNull(found)
            assertNotNull(found.persistedAttributesEnvelope)
            assertEquals("ciphertext", found.persistedAttributesEnvelope?.encrypted?.ciphertext)
            assertEquals("profile-v1", found.persistedAttributesEnvelope?.materialProfileVersion)
            assertEquals(setOf("sub", "email"), found.persistedAttributesEnvelope?.attributeNames)
            assertEquals(setOf("fp-1"), found.persistedAttributesEnvelope?.materialFingerprints)
        }

    // ---- Test 5.2: Update indexed fields ----

    @Test
    fun updateHolderHash_indexRepaired() =
        runTest {
            val store = createStore()
            store.create(createBinding(holderIdentifierHash = "old-holder-hash"))

            // Update holder hash
            val binding = store.findByMatchId("tenant-1", "match-1")!!
            val updated =
                binding.copy(
                    holderIdentifierHash = "new-holder-hash",
                    holderHashKeyVersion = "A-v2",
                    updatedAt = Clock.System.now(),
                )
            store.update(updated)

            // Old holder hash index should be gone
            assertNull(
                store.findByHolderHash("tenant-1", "old-holder-hash"),
                "Old holder hash index should be removed after update",
            )

            // New holder hash index should work
            val found = store.findByHolderHash("tenant-1", "new-holder-hash")
            assertNotNull(found, "New holder hash index should be created after update")
            assertEquals("A-v2", found.holderHashKeyVersion)
        }

    @Test
    fun updateMatchId_indexRepaired() =
        runTest {
            val store = createStore()
            store.create(createBinding(matchId = "old-match"))

            val binding = store.findByMatchId("tenant-1", "old-match")!!
            val updated = binding.copy(matchId = "new-match", updatedAt = Clock.System.now())
            store.update(updated)

            // Old match index should be gone
            assertNull(
                store.findByMatchId("tenant-1", "old-match"),
                "Old match index should be removed after update",
            )

            // New match index should work
            val found = store.findByMatchId("tenant-1", "new-match")
            assertNotNull(found, "New match index should be created after update")
            assertEquals("binding-1", found.id)
        }

    @Test
    fun updateNonIndexedField_indexesStillWork() =
        runTest {
            val store = createStore()
            store.create(createBinding())

            val binding = store.findByMatchId("tenant-1", "match-1")!!
            val updated =
                binding.copy(
                    persistedAttributesEnvelope =
                        binding.persistedAttributesEnvelope.copy(
                            attributeNames = setOf("sub", "email"),
                            updatedAt = Clock.System.now(),
                        ),
                    updatedAt = Clock.System.now(),
                )
            store.update(updated)

            // Both indexes should still work
            assertNotNull(store.findByMatchId("tenant-1", "match-1"))
            assertNotNull(store.findByHolderHash("tenant-1", "holder-hash-abc"))

            // Updated field should be persisted
            val found = store.findByMatchId("tenant-1", "match-1")!!
            assertEquals(setOf("sub", "email"), found.persistedAttributesEnvelope?.attributeNames)
        }

    // ---- Test 5.3: Delete and verify gone ----

    @Test
    fun deleteBinding_removedFromAllIndexes() =
        runTest {
            val store = createStore()
            store.create(createBinding())

            // Verify it exists first
            assertNotNull(store.findByMatchId("tenant-1", "match-1"))
            assertNotNull(store.findByHolderHash("tenant-1", "holder-hash-abc"))

            // Delete
            val deleted = store.delete("tenant-1", "binding-1")
            assertTrue(deleted, "Delete should return true for existing binding")

            // Verify all lookups return null
            assertNull(store.findByMatchId("tenant-1", "match-1"), "matchId lookup should return null after delete")
            assertNull(store.findByHolderHash("tenant-1", "holder-hash-abc"), "holderHash lookup should return null after delete")
        }

    @Test
    fun deleteNonExistent_returnsFalse() =
        runTest {
            val store = createStore()
            val deleted = store.delete("tenant-1", "nonexistent")
            assertFalse(deleted, "Delete should return false for non-existent binding")
        }

    // ---- Test 5.4: findByHolderHash ----

    @Test
    fun findByHolderHash_returnsCorrectBinding() =
        runTest {
            val store = createStore()
            store.create(createBinding(id = "b1", matchId = "m1", holderIdentifierHash = "holder-1"))
            store.create(createBinding(id = "b2", matchId = "m2", holderIdentifierHash = "holder-2"))

            val found = store.findByHolderHash("tenant-1", "holder-1")
            assertNotNull(found)
            assertEquals("b1", found.id)
            assertEquals("m1", found.matchId)
        }

    @Test
    fun findByHolderHash_returnsNullForWrongTenant() =
        runTest {
            val store = createStore()
            store.create(createBinding(tenantId = "tenant-1"))

            assertNull(
                store.findByHolderHash("tenant-2", "holder-hash-abc"),
                "Should not find binding from different tenant",
            )
        }

    @Test
    fun findByHolderHash_returnsNullForNonExistentHash() =
        runTest {
            val store = createStore()
            store.create(createBinding())

            assertNull(store.findByHolderHash("tenant-1", "nonexistent-hash"))
        }

    // ---- Test 5.5: findByMatchId ----

    @Test
    fun findByMatchId_returnsCorrectBinding() =
        runTest {
            val store = createStore()
            store.create(createBinding(id = "b1", matchId = "m1", holderIdentifierHash = "h1"))
            store.create(createBinding(id = "b2", matchId = "m2", holderIdentifierHash = "h2"))

            val found = store.findByMatchId("tenant-1", "m2")
            assertNotNull(found)
            assertEquals("b2", found.id)
        }

    @Test
    fun findByMatchId_returnsNullForWrongTenant() =
        runTest {
            val store = createStore()
            store.create(createBinding(tenantId = "tenant-1"))

            assertNull(
                store.findByMatchId("tenant-2", "match-1"),
                "Should not find binding from different tenant",
            )
        }
}
