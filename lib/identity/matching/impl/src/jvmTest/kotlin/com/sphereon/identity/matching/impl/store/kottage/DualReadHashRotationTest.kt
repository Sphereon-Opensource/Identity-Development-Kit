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
import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.matching.model.IdentityLinkBinding
import com.sphereon.identity.matching.model.IdentityMatch
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

/**
 * Tests for dual-read hash rotation at the store layer.
 *
 * Simulates key rotation scenario:
 * 1. A binding is stored under hash computed with "current" key (version 1)
 * 2. After key rotation, the "current" key (version 2) produces a different hash
 * 3. The "previous" key (version 1) still produces the original hash
 * 4. Lookup by new hash fails, but lookup by previous-key hash succeeds
 *
 * This validates the store-layer contract that the orchestrator depends on
 * for dual-read fallback during key rotation windows.
 */
class DualReadHashRotationTest {
    private val testDir = Files.createTempDirectory("kottage-rotation-test").toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun createKottage(): Kottage =
        Kottage(
            name = "rotation-test",
            directoryPath = testDir,
            environment = KottageEnvironment(context = KottageContext()),
            scope = scope,
        )

    @AfterTest
    fun cleanup() {
        java.io.File(testDir).deleteRecursively()
    }

    @Test
    fun dualReadMatchStoreFallbackViaPreviousHash() =
        runTest {
            val kottage = createKottage()
            val kvStoreFactory = KottageKvStoreFactory(kottage)
            val matchStore = KottageIdentityMatchStore(store = kvStoreFactory.create(KottageKvStoreConfig(id = "identity-matches", scopeBinding = com.sphereon.data.store.kv.KvStoreScopeBinding.APP)))

            // Simulate: holder key "holder-jwk-123" hashed with key version 1 => "hash-v1"
            val hashWithKeyV1 = "hash-v1"
            val match =
                IdentityMatch(
                    id = "match-1",
                    identifierHash = hashWithKeyV1,
                    identifierType = IdentifierType.KEY,
                    internalIdentityId = "identity-1",
                    tenantId = "tenant-1",
                    hashKeyVersion = "A-v1",
                    createdAt = Clock.System.now(),
                )
            matchStore.create(match)

            // After key rotation, same holder key hashed with key version 2 => "hash-v2"
            val hashWithKeyV2 = "hash-v2"

            // Step 1: Lookup with new hash (v2) FAILS - binding was stored under v1 hash
            val currentLookup = matchStore.findByIdentifierHash("tenant-1", hashWithKeyV2, IdentifierType.KEY)
            assertNull(currentLookup, "Lookup with new key hash should NOT find the old binding")

            // Step 2: Lookup with previous hash (v1) SUCCEEDS - this is the dual-read fallback
            val fallbackLookup = matchStore.findByIdentifierHash("tenant-1", hashWithKeyV1, IdentifierType.KEY)
            assertNotNull(fallbackLookup, "Lookup with previous key hash should find the binding (dual-read fallback)")
            assertEquals("match-1", fallbackLookup.id)
            assertEquals("A-v1", fallbackLookup.hashKeyVersion)
        }

    @Test
    fun dualReadBindingStoreFallbackViaPreviousHolderHash() =
        runTest {
            val kottage = createKottage()
            val kvStoreFactory = KottageKvStoreFactory(kottage)
            val bindingStore =
                KottageIdentityLinkBindingStore(store = kvStoreFactory.create(KottageKvStoreConfig(id = "identity-link-bindings", scopeBinding = com.sphereon.data.store.kv.KvStoreScopeBinding.APP)))

            // Simulate: holder key hashed with key version 1 => "holder-hash-v1"
            val holderHashV1 = "holder-hash-v1"
            val binding =
                IdentityLinkBinding(
                    id = "binding-1",
                    tenantId = "tenant-1",
                    matchId = "match-1",
                    holderIdentifierHash = holderHashV1,
                    holderHashKeyVersion = "A-v1",
                    institutionIdentifierHash = "inst-hash",
                    institutionHashKeyVersion = "B-v1",
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
                    assuranceSummary = null,
                    createdAt = Clock.System.now(),
                    updatedAt = null,
                    lastUsedAt = null,
                )
            bindingStore.create(binding)

            // After key rotation, same holder key hashed with key version 2 => "holder-hash-v2"
            val holderHashV2 = "holder-hash-v2"

            // Step 1: Lookup with new hash FAILS
            val currentLookup = bindingStore.findByHolderHash("tenant-1", holderHashV2)
            assertNull(currentLookup, "Lookup with new key hash should NOT find the old binding")

            // Step 2: Lookup with previous hash SUCCEEDS (dual-read fallback)
            val fallbackLookup = bindingStore.findByHolderHash("tenant-1", holderHashV1)
            assertNotNull(fallbackLookup, "Lookup with previous key hash should find the binding")
            assertEquals("binding-1", fallbackLookup.id)
            assertEquals("A-v1", fallbackLookup.holderHashKeyVersion)
        }

    @Test
    fun afterReHashUpdateCurrentHashWorks() =
        runTest {
            val kottage = createKottage()
            val kvStoreFactory = KottageKvStoreFactory(kottage)
            val matchStore = KottageIdentityMatchStore(store = kvStoreFactory.create(KottageKvStoreConfig(id = "identity-matches", scopeBinding = com.sphereon.data.store.kv.KvStoreScopeBinding.APP)))

            // Original stored with v1 hash
            val hashV1 = "hash-v1"
            val match =
                IdentityMatch(
                    id = "match-1",
                    identifierHash = hashV1,
                    identifierType = IdentifierType.KEY,
                    internalIdentityId = "identity-1",
                    tenantId = "tenant-1",
                    hashKeyVersion = "A-v1",
                    createdAt = Clock.System.now(),
                )
            matchStore.create(match)

            // Simulate re-hash migration: update to v2 hash
            val hashV2 = "hash-v2"
            val rehashed =
                match.copy(
                    identifierHash = hashV2,
                    hashKeyVersion = "A-v2",
                    updatedAt = Clock.System.now(),
                )
            matchStore.update(rehashed)

            // After migration, current hash lookup should work
            val found = matchStore.findByIdentifierHash("tenant-1", hashV2, IdentifierType.KEY)
            assertNotNull(found, "After re-hash migration, current hash should find the match")
            assertEquals("A-v2", found.hashKeyVersion)

            // Old hash should no longer work (index repaired by update)
            val stale = matchStore.findByIdentifierHash("tenant-1", hashV1, IdentifierType.KEY)
            assertNull(stale, "Old hash should be removed after re-hash migration")
        }
}
