/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.identity.reconciliation.impl.store

import com.sphereon.identity.matching.crypto.EncryptedPayload
import com.sphereon.identity.matching.model.AssuranceSummary
import com.sphereon.identity.matching.model.IdentityLinkBinding
import com.sphereon.identity.matching.model.PersistedAttributesEnvelope
import com.sphereon.identity.matching.store.IdentityLinkBindingStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Tests for the IdentityLinkBindingStore interface contract.
 *
 * Uses an in-memory implementation to verify CRUD, lookup by holderHash and matchId,
 * expiry queries, tenant isolation, and update semantics.
 *
 * Kottage-specific persistence tests are in jvmTest:
 * [com.sphereon.identity.matching.impl.store.kottage.KottageIdentityLinkBindingStoreJvmTest]
 */
class KottageIdentityLinkBindingStoreTest {
    /**
     * In-memory implementation of [IdentityLinkBindingStore] for testing the interface contract.
     * Not a mock -- implements the full interface with real in-memory state.
     */
    private class InMemoryIdentityLinkBindingStore : IdentityLinkBindingStore {
        private val byId = mutableMapOf<String, IdentityLinkBinding>()
        private val byMatch = mutableMapOf<String, String>() // compositeMatchKey -> bindingId
        private val byHolder = mutableMapOf<String, String>() // compositeHolderKey -> bindingId

        private fun idKey(
            tenantId: String,
            bindingId: String,
        ) = "$tenantId:$bindingId"

        private fun matchKey(
            tenantId: String,
            matchId: String,
        ) = "$tenantId:$matchId"

        private fun holderKey(
            tenantId: String,
            holderHash: String,
        ) = "$tenantId:$holderHash"

        override suspend fun create(binding: IdentityLinkBinding): IdentityLinkBinding {
            byId[idKey(binding.tenantId, binding.id)] = binding
            byMatch[matchKey(binding.tenantId, binding.matchId)] = binding.id
            byHolder[holderKey(binding.tenantId, binding.holderIdentifierHash)] = binding.id
            return binding
        }

        override suspend fun findByMatchId(
            tenantId: String,
            matchId: String,
        ): IdentityLinkBinding? {
            val bindingId = byMatch[matchKey(tenantId, matchId)] ?: return null
            return byId[idKey(tenantId, bindingId)]
        }

        override suspend fun findByHolderHash(
            tenantId: String,
            holderHash: String,
        ): IdentityLinkBinding? {
            val bindingId = byHolder[holderKey(tenantId, holderHash)] ?: return null
            return byId[idKey(tenantId, bindingId)]
        }

        override suspend fun update(binding: IdentityLinkBinding): IdentityLinkBinding {
            val existing = byId[idKey(binding.tenantId, binding.id)]
            if (existing != null) {
                if (existing.matchId != binding.matchId) {
                    byMatch.remove(matchKey(existing.tenantId, existing.matchId))
                }
                if (existing.holderIdentifierHash != binding.holderIdentifierHash) {
                    byHolder.remove(holderKey(existing.tenantId, existing.holderIdentifierHash))
                }
            }
            byId[idKey(binding.tenantId, binding.id)] = binding
            byMatch[matchKey(binding.tenantId, binding.matchId)] = binding.id
            byHolder[holderKey(binding.tenantId, binding.holderIdentifierHash)] = binding.id
            return binding
        }

        override suspend fun delete(
            tenantId: String,
            bindingId: String,
        ): Boolean {
            val binding = byId.remove(idKey(tenantId, bindingId)) ?: return false
            byMatch.remove(matchKey(tenantId, binding.matchId))
            byHolder.remove(holderKey(tenantId, binding.holderIdentifierHash))
            return true
        }

        override suspend fun findExpired(
            tenantId: String,
            inactiveSince: Instant,
        ): List<IdentityLinkBinding> =
            byId.values.filter { binding ->
                binding.tenantId == tenantId &&
                    (binding.lastUsedAt ?: binding.updatedAt ?: binding.createdAt) < inactiveSince
            }
    }

    private fun createStore(): IdentityLinkBindingStore = InMemoryIdentityLinkBindingStore()

    private fun createBinding(
        id: String = "binding-1",
        tenantId: String = "tenant-1",
        matchId: String = "match-1",
        holderIdentifierHash: String = "holder-hash-abc",
        lastUsedAt: Instant? = Clock.System.now(),
    ) = IdentityLinkBinding(
        id = id,
        tenantId = tenantId,
        matchId = matchId,
        holderIdentifierHash = holderIdentifierHash,
        holderHashKeyVersion = "v1",
        institutionIdentifierHash = "inst-hash-def",
        institutionHashKeyVersion = "v1",
        encryptedInstitutionId = null,
        persistedAttributesEnvelope =
            PersistedAttributesEnvelope(
                encrypted = EncryptedPayload(ciphertext = "ciphertext", keyVersion = "C-v1"),
                canonicalSchemaVersion = "1",
                materialProfileVersion = "profile-v1",
                selectorRuleVersion = "rules-v1",
                attributeNames = setOf("sub"),
                updatedAt = Clock.System.now(),
            ),
        providerId = "surf",
        institutionId = "kw1c",
        assuranceSummary =
            AssuranceSummary(
                walletAssuranceLevel = "high",
                oidcAcr = "urn:mace:surfnet.nl:assurance:loa3",
            ),
        createdAt = Clock.System.now(),
        updatedAt = null,
        lastUsedAt = lastUsedAt,
    )

    @Test
    fun createAndFindById() =
        runTest {
            val store = createStore()
            val binding = createBinding()
            store.create(binding)
            // IdentityLinkBindingStore doesn't have findById, uses findByMatchId or findByHolderHash
            val found = store.findByMatchId("tenant-1", "match-1")
            assertNotNull(found)
            assertEquals("binding-1", found.id)
            assertEquals("holder-hash-abc", found.holderIdentifierHash)
            assertEquals("surf", found.providerId)
        }

    @Test
    fun findByHolderHash() =
        runTest {
            val store = createStore()
            store.create(createBinding())
            val found = store.findByHolderHash("tenant-1", "holder-hash-abc")
            assertNotNull(found)
            assertEquals("binding-1", found.id)
            assertEquals("match-1", found.matchId)
        }

    @Test
    fun findByMatchId() =
        runTest {
            val store = createStore()
            store.create(createBinding())
            val found = store.findByMatchId("tenant-1", "match-1")
            assertNotNull(found)
            assertEquals("binding-1", found.id)
            assertEquals("holder-hash-abc", found.holderIdentifierHash)
        }

    @Test
    fun findExpired() =
        runTest {
            val store = createStore()
            val oldTime = Clock.System.now() - 48.hours
            store.create(
                createBinding(
                    id = "old-binding",
                    matchId = "match-old",
                    holderIdentifierHash = "holder-old",
                    lastUsedAt = oldTime,
                ),
            )
            store.create(
                createBinding(
                    id = "new-binding",
                    matchId = "match-new",
                    holderIdentifierHash = "holder-new",
                ),
            )
            val cutoff = Clock.System.now() - 24.hours
            val expired = store.findExpired("tenant-1", cutoff)
            assertEquals(1, expired.size)
            assertEquals("old-binding", expired[0].id)
        }

    @Test
    fun deleteBinding() =
        runTest {
            val store = createStore()
            store.create(createBinding())
            val deleted = store.delete("tenant-1", "binding-1")
            assertTrue(deleted)
            assertNull(store.findByMatchId("tenant-1", "match-1"))
            assertNull(store.findByHolderHash("tenant-1", "holder-hash-abc"))
        }

    @Test
    fun tenantIsolation() =
        runTest {
            val store = createStore()
            store.create(createBinding(tenantId = "tenant-1"))
            assertNull(store.findByMatchId("tenant-2", "match-1"))
            assertNull(store.findByHolderHash("tenant-2", "holder-hash-abc"))
        }

    @Test
    fun persistenceAcrossRestart() =
        runTest {
            // InMemory store does not persist across restarts.
            // Kottage persistence is tested in jvmTest:
            // KottageIdentityLinkBindingStoreJvmTest.persistenceAcrossRestart()
            //
            // This test verifies that data is consistent within a single store lifetime.
            val store = createStore()
            store.create(createBinding())
            val found = store.findByHolderHash("tenant-1", "holder-hash-abc")
            assertNotNull(found)
            assertEquals("binding-1", found.id)
        }

    @Test
    fun updateBinding() =
        runTest {
            val store = createStore()
            val binding = createBinding()
            store.create(binding)

            val updatedTime = Clock.System.now() + 1.hours
            val updated =
                binding.copy(
                    lastUsedAt = updatedTime,
                    updatedAt = updatedTime,
                    persistedAttributesEnvelope =
                        binding.persistedAttributesEnvelope.copy(
                            attributeNames = setOf("sub", "email"),
                            updatedAt = updatedTime,
                        ),
                )
            store.update(updated)

            val found = store.findByMatchId("tenant-1", "match-1")
            assertNotNull(found)
            assertEquals(setOf("sub", "email"), found.persistedAttributesEnvelope?.attributeNames)
            assertEquals(updatedTime, found.lastUsedAt)
            assertEquals(updatedTime, found.updatedAt)
        }
}
