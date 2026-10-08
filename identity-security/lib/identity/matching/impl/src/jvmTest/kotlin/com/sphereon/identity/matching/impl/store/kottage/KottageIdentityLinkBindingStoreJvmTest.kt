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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class KottageIdentityLinkBindingStoreJvmTest {
    private val testDir = Files.createTempDirectory("kottage-binding-test").toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun createKottage(name: String = "test"): Kottage =
        Kottage(
            name = name,
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

    @AfterTest
    fun cleanup() {
        java.io.File(testDir).deleteRecursively()
    }

    @Test
    fun createAndFindByMatchId() =
        runTest {
            val store = createStore()
            val binding = createBinding()
            store.create(binding)
            val found = store.findByMatchId("tenant-1", "match-1")
            assertNotNull(found)
            assertEquals("binding-1", found.id)
            assertEquals("surf", found.providerId)
            assertNotNull(found.assuranceSummary)
            assertEquals("high", found.assuranceSummary?.walletAssuranceLevel)
        }

    @Test
    fun findByHolderHash() =
        runTest {
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
            store.create(createBinding())
            val updated =
                createBinding().copy(
                    updatedAt = Clock.System.now(),
                    persistedAttributesEnvelope =
                        PersistedAttributesEnvelope(
                            encrypted = EncryptedPayload(ciphertext = "ciphertext", keyVersion = "C-v1"),
                            canonicalSchemaVersion = "1",
                            materialProfileVersion = "profile-v2",
                            selectorRuleVersion = "rules-v1",
                            attributeNames = setOf("sub", "email"),
                            updatedAt = Clock.System.now(),
                        ),
                )
            store.update(updated)
            val found = store.findByMatchId("tenant-1", "match-1")
            assertNotNull(found)
            assertEquals("profile-v2", found.persistedAttributesEnvelope?.materialProfileVersion)
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
    fun findExpired() =
        runTest {
            val store = createStore()
            val oldTime = Clock.System.now() - 48.hours
            store.create(createBinding(id = "old-binding", lastUsedAt = oldTime))
            store.create(createBinding(id = "new-binding", matchId = "match-2", holderIdentifierHash = "holder-hash-new"))

            val cutoff = Clock.System.now() - 24.hours
            val expired = store.findExpired("tenant-1", cutoff)
            assertEquals(1, expired.size)
            assertEquals("old-binding", expired[0].id)
        }

    @Test
    fun persistenceAcrossRestart() =
        runTest {
            val kottage = createKottage("persist-test")
            val kvStoreFactory = KottageKvStoreFactory(kottage)
            val store1 =
                KottageIdentityLinkBindingStore(store = kvStoreFactory.create(KottageKvStoreConfig(id = "identity-link-bindings", scopeBinding = com.sphereon.data.store.kv.KvStoreScopeBinding.APP)))
            store1.create(createBinding())

            val store2 =
                KottageIdentityLinkBindingStore(store = kvStoreFactory.create(KottageKvStoreConfig(id = "identity-link-bindings", scopeBinding = com.sphereon.data.store.kv.KvStoreScopeBinding.APP)))
            val found = store2.findByMatchId("tenant-1", "match-1")
            assertNotNull(found)
            assertEquals("binding-1", found.id)
        }
}
