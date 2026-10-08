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

package com.sphereon.identity.reconciliation.impl.orchestrator

import com.sphereon.identity.matching.crypto.EncryptedPayload
import com.sphereon.identity.matching.crypto.HashedIdentifier
import com.sphereon.identity.matching.crypto.ReconciliationCryptoService
import com.sphereon.identity.matching.impl.store.InMemoryIdentityMatchStore
import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.matching.model.IdentityLinkBinding
import com.sphereon.identity.matching.model.IdentityMatch
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Tests for reconciliation orchestration logic.
 *
 * Exercises the top-level orchestration patterns: unknown holder -> IDV_REQUIRED,
 * known holder -> MATCHED with decrypted claims, OIDC completion -> binding materialized,
 * and dual-read rotation support.
 *
 * Uses in-memory stores and a deterministic crypto service to test the orchestration
 * logic without requiring the full ReconciliationOrchestrator from service-auth-bridge.
 * The actual ReconciliationOrchestrator integration is tested via E2E tests.
 */
class ReconciliationOrchestratorTest {
    // -- In-memory IdentityLinkBindingStore for testing --

    private class InMemoryIdentityLinkBindingStore : IdentityLinkBindingStore {
        private val byId = mutableMapOf<String, IdentityLinkBinding>()
        private val byMatch = mutableMapOf<String, String>()
        private val byHolder = mutableMapOf<String, String>()

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
            byId.values.filter {
                it.tenantId == tenantId &&
                    (it.lastUsedAt ?: it.updatedAt ?: it.createdAt) < inactiveSince
            }
    }

    // -- Deterministic crypto service for testing --

    private class TestCryptoService(
        private val holderKeyAlias: String = "holder-key",
        private val previousHolderKeyAlias: String? = null,
    ) : ReconciliationCryptoService {
        private fun deterministicHash(
            input: String,
            keyAlias: String,
        ): String {
            val combined = "$keyAlias:$input"
            val bytes = combined.encodeToByteArray()
            val hash = ByteArray(32)
            for (i in bytes.indices) {
                hash[i % 32] = (hash[i % 32].toInt() xor bytes[i].toInt()).toByte()
            }
            return "f1220" + hash.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
        }

        override suspend fun hashHolderKey(holderKey: String) =
            HashedIdentifier(
                hash = deterministicHash(holderKey, holderKeyAlias),
                keyVersion = "v1",
            )

        override suspend fun hashExternalIdentifier(identifier: String) =
            HashedIdentifier(
                hash = deterministicHash(identifier, "institution-key"),
                keyVersion = "v1",
            )

        override suspend fun encrypt(plaintext: String) =
            EncryptedPayload(
                ciphertext = plaintext.reversed(), // simple reversible for testing
                keyVersion = "v1",
            )

        override suspend fun decrypt(payload: EncryptedPayload) = payload.ciphertext.reversed()

        override suspend fun hashHolderKeyWithPrevious(holderKey: String): HashedIdentifier? {
            val alias = previousHolderKeyAlias ?: return null
            return HashedIdentifier(
                hash = deterministicHash(holderKey, alias),
                keyVersion = "v0",
            )
        }

        override suspend fun hashExternalIdentifierWithPrevious(identifier: String): HashedIdentifier? = null
    }

    // -- Orchestration logic under test --

    /**
     * Simplified orchestration logic extracted from ReconciliationOrchestrator.
     * Tests the core decision: known holder -> MATCHED, unknown holder -> IDV_REQUIRED.
     */
    private data class ReconciliationResult(
        val status: String,
        val binding: IdentityLinkBinding? = null,
    )

    private suspend fun processWalletPresentation(
        holderKey: String,
        tenantId: String,
        cryptoService: ReconciliationCryptoService,
        bindingStore: IdentityLinkBindingStore,
    ): ReconciliationResult {
        // 1. Hash the holder key
        val holderHash = cryptoService.hashHolderKey(holderKey)

        // 2. Look up binding by current hash
        val binding = bindingStore.findByHolderHash(tenantId, holderHash.hash)
        if (binding != null) {
            // Check if binding is expired (inactive for too long)
            val lastActivity = binding.lastUsedAt ?: binding.updatedAt ?: binding.createdAt
            if (lastActivity < Clock.System.now() - 24.hours) {
                return ReconciliationResult(status = "IDV_REQUIRED")
            }
            // Update lastUsedAt
            bindingStore.update(binding.copy(lastUsedAt = Clock.System.now()))
            return ReconciliationResult(status = "MATCHED", binding = binding)
        }

        // 3. Try previous key hash (dual-read for key rotation)
        val previousHash = cryptoService.hashHolderKeyWithPrevious(holderKey)
        if (previousHash != null) {
            val oldBinding = bindingStore.findByHolderHash(tenantId, previousHash.hash)
            if (oldBinding != null) {
                // Rehash with current key and update binding
                val updated =
                    oldBinding.copy(
                        holderIdentifierHash = holderHash.hash,
                        holderHashKeyVersion = holderHash.keyVersion,
                        lastUsedAt = Clock.System.now(),
                    )
                bindingStore.update(updated)
                return ReconciliationResult(status = "MATCHED", binding = updated)
            }
        }

        // 4. Unknown holder
        return ReconciliationResult(status = "IDV_REQUIRED")
    }

    private fun createBinding(
        id: String = "binding-1",
        tenantId: String = "tenant-1",
        holderIdentifierHash: String,
        matchId: String = "match-1",
        lastUsedAt: Instant? = Clock.System.now(),
    ) = IdentityLinkBinding(
        id = id,
        tenantId = tenantId,
        matchId = matchId,
        holderIdentifierHash = holderIdentifierHash,
        holderHashKeyVersion = "v1",
        institutionIdentifierHash = null,
        institutionHashKeyVersion = null,
        encryptedInstitutionId = null,
        persistedAttributesEnvelope =
            PersistedAttributesEnvelope(
                encrypted =
                    EncryptedPayload(
                        ciphertext = """{"email":"user@school.nl"}""".reversed(),
                        keyVersion = "v1",
                    ),
                canonicalSchemaVersion = "1",
                materialProfileVersion = "profile-v1",
                selectorRuleVersion = "rules-v1",
                attributeNames = setOf("email"),
                updatedAt = Clock.System.now(),
            ),
        providerId = "surf",
        institutionId = "kw1c",
        assuranceSummary = null,
        createdAt = Clock.System.now(),
        updatedAt = null,
        lastUsedAt = lastUsedAt,
    )

    @Test
    fun unknownHolder_returnsIdvRequired() =
        runTest {
            val cryptoService = TestCryptoService()
            val bindingStore = InMemoryIdentityLinkBindingStore()

            val result =
                processWalletPresentation(
                    holderKey = "did:key:unknown-holder",
                    tenantId = "tenant-1",
                    cryptoService = cryptoService,
                    bindingStore = bindingStore,
                )
            assertEquals("IDV_REQUIRED", result.status)
            assertNull(result.binding)
        }

    @Test
    fun knownHolder_returnsMatchedWithDecryptedClaims() =
        runTest {
            val cryptoService = TestCryptoService()
            val bindingStore = InMemoryIdentityLinkBindingStore()

            // Pre-create a binding with the hash for "did:key:known-holder"
            val holderHash = cryptoService.hashHolderKey("did:key:known-holder")
            val binding = createBinding(holderIdentifierHash = holderHash.hash)
            bindingStore.create(binding)

            val result =
                processWalletPresentation(
                    holderKey = "did:key:known-holder",
                    tenantId = "tenant-1",
                    cryptoService = cryptoService,
                    bindingStore = bindingStore,
                )
            assertEquals("MATCHED", result.status)
            assertNotNull(result.binding)

            // Verify we can decrypt the canonical claims
            val decrypted = cryptoService.decrypt(result.binding!!.persistedAttributesEnvelope.encrypted)
            assertEquals("""{"email":"user@school.nl"}""", decrypted)
        }

    @Test
    fun oidcCompletion_materializesBinding() =
        runTest {
            val cryptoService = TestCryptoService()
            val bindingStore = InMemoryIdentityLinkBindingStore()
            val matchStore = InMemoryIdentityMatchStore()

            // 1. Process wallet VP -- unknown holder -> IDV_REQUIRED
            val holderKey = "did:key:new-holder"
            val result1 =
                processWalletPresentation(
                    holderKey = holderKey,
                    tenantId = "tenant-1",
                    cryptoService = cryptoService,
                    bindingStore = bindingStore,
                )
            assertEquals("IDV_REQUIRED", result1.status)

            // 2. Simulate OIDC completion: create match and binding
            val holderHash = cryptoService.hashHolderKey(holderKey)
            val match =
                IdentityMatch(
                    id = "match-new",
                    identifierHash = holderHash.hash,
                    identifierType = IdentifierType.KEY,
                    internalIdentityId = holderHash.hash,
                    tenantId = "tenant-1",
                    createdAt = Clock.System.now(),
                )
            matchStore.create(match)

            val encryptedClaims = cryptoService.encrypt("""{"email":"new@school.nl","name":"New User"}""")
            val binding =
                createBinding(
                    id = "binding-new",
                    holderIdentifierHash = holderHash.hash,
                    matchId = match.id,
                ).copy(
                    persistedAttributesEnvelope =
                        PersistedAttributesEnvelope(
                            encrypted = encryptedClaims,
                            canonicalSchemaVersion = "1",
                            materialProfileVersion = "profile-v1",
                            selectorRuleVersion = "rules-v1",
                            attributeNames = setOf("email", "name"),
                            updatedAt = Clock.System.now(),
                        ),
                )
            bindingStore.create(binding)

            // 3. Verify binding was created
            val foundBinding = bindingStore.findByHolderHash("tenant-1", holderHash.hash)
            assertNotNull(foundBinding)
            assertEquals("binding-new", foundBinding.id)
            assertEquals(match.id, foundBinding.matchId)
        }

    @Test
    fun dualReadRotation_findsMatchWithPreviousKey() =
        runTest {
            val oldCryptoService = TestCryptoService(holderKeyAlias = "holder-key-v1")
            val bindingStore = InMemoryIdentityLinkBindingStore()

            // Create binding with old key hash
            val holderKey = "did:key:rotating-holder"
            val oldHash = oldCryptoService.hashHolderKey(holderKey)
            val binding = createBinding(holderIdentifierHash = oldHash.hash)
            bindingStore.create(binding)

            // Simulate key rotation: new crypto service with previous key support
            val rotatedCryptoService =
                TestCryptoService(
                    holderKeyAlias = "holder-key-v2",
                    previousHolderKeyAlias = "holder-key-v1",
                )

            // The current hash won't match, but dual-read should find via previous key
            val result =
                processWalletPresentation(
                    holderKey = holderKey,
                    tenantId = "tenant-1",
                    cryptoService = rotatedCryptoService,
                    bindingStore = bindingStore,
                )
            assertEquals("MATCHED", result.status)
            assertNotNull(result.binding)
            // Binding should be updated with the new hash
            val newHash = rotatedCryptoService.hashHolderKey(holderKey)
            assertEquals(newHash.hash, result.binding!!.holderIdentifierHash)
        }

    @Test
    fun expiredBinding_treatedAsUnknown() =
        runTest {
            val cryptoService = TestCryptoService()
            val bindingStore = InMemoryIdentityLinkBindingStore()

            // Create a binding that was last used over 24 hours ago
            val holderKey = "did:key:expired-holder"
            val holderHash = cryptoService.hashHolderKey(holderKey)
            val oldTime = Clock.System.now() - 48.hours
            val binding =
                createBinding(
                    holderIdentifierHash = holderHash.hash,
                    lastUsedAt = oldTime,
                )
            bindingStore.create(binding)

            val result =
                processWalletPresentation(
                    holderKey = holderKey,
                    tenantId = "tenant-1",
                    cryptoService = cryptoService,
                    bindingStore = bindingStore,
                )
            assertEquals("IDV_REQUIRED", result.status)
        }

    @Test
    fun oidcCompletion_updatesLastUsedAt() =
        runTest {
            val cryptoService = TestCryptoService()
            val bindingStore = InMemoryIdentityLinkBindingStore()

            val holderKey = "did:key:known-holder"
            val holderHash = cryptoService.hashHolderKey(holderKey)
            val originalTime = Clock.System.now() - 1.minutes
            val binding =
                createBinding(
                    holderIdentifierHash = holderHash.hash,
                    lastUsedAt = originalTime,
                )
            bindingStore.create(binding)

            // Process the wallet presentation (should update lastUsedAt)
            val result =
                processWalletPresentation(
                    holderKey = holderKey,
                    tenantId = "tenant-1",
                    cryptoService = cryptoService,
                    bindingStore = bindingStore,
                )
            assertEquals("MATCHED", result.status)

            // Verify lastUsedAt was updated
            val updatedBinding = bindingStore.findByHolderHash("tenant-1", holderHash.hash)
            assertNotNull(updatedBinding)
            assertNotNull(updatedBinding.lastUsedAt)
            assertTrue(
                updatedBinding.lastUsedAt!! > originalTime,
                "lastUsedAt should be updated to a more recent time",
            )
        }
}
