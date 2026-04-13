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

class KottageIdentityMatchStoreJvmTest {

    private val testDir = Files.createTempDirectory("kottage-match-test").toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun createKottage(name: String = "test"): Kottage = Kottage(
        name = name,
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

    @AfterTest
    fun cleanup() {
        java.io.File(testDir).deleteRecursively()
    }

    @Test
    fun createAndFindById() = runTest {
        val store = createStore()
        val match = createMatch()
        store.create(match)
        val found = store.findById("tenant-1", "match-1")
        assertNotNull(found)
        assertEquals("hash-abc", found.identifierHash)
        assertEquals("v1", found.hashKeyVersion)
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
    fun lastUsedAtFieldPersisted() = runTest {
        val store = createStore()
        val now = Clock.System.now()
        val match = createMatch().copy(lastUsedAt = now)
        store.create(match)
        val found = store.findById("tenant-1", "match-1")
        assertNotNull(found)
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
    }

    @Test
    fun persistenceAcrossRestart() = runTest {
        val kottage = createKottage("persist-test")
        val kvStoreFactory = KottageKvStoreFactory(kottage)
        val store1 = KottageIdentityMatchStore(store = kvStoreFactory.create(KottageKvStoreConfig(id = "identity-matches", scopeBinding = com.sphereon.data.store.kv.KvStoreScopeBinding.APP)))
        store1.create(createMatch())

        // Create a new store instance pointing to the same Kottage DB
        val store2 = KottageIdentityMatchStore(store = kvStoreFactory.create(KottageKvStoreConfig(id = "identity-matches", scopeBinding = com.sphereon.data.store.kv.KvStoreScopeBinding.APP)))
        val found = store2.findById("tenant-1", "match-1")
        assertNotNull(found)
        assertEquals("hash-abc", found.identifierHash)
    }
}
