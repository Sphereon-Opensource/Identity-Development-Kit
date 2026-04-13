/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.identity.matching.impl.store

import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.matching.model.IdentityMatch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryIdentityMatchStoreTest {

    private fun createStore() = InMemoryIdentityMatchStore()

    private fun createMatch(
        id: String = "match-1",
        identifierHash: String = "hash-abc",
        identifierType: IdentifierType = IdentifierType.DID,
        internalIdentityId: String = "identity-1",
        tenantId: String = "tenant-1",
        metadata: Map<String, String> = emptyMap()
    ) = IdentityMatch(
        id = id,
        identifierHash = identifierHash,
        identifierType = identifierType,
        internalIdentityId = internalIdentityId,
        tenantId = tenantId,
        metadata = metadata,
        createdAt = Clock.System.now()
    )

    @Test
    fun createAndFindById() = runTest {
        val store = createStore()
        val match = createMatch()

        val created = store.create(match)
        assertEquals(match.id, created.id)

        val found = store.findById("tenant-1", "match-1")
        assertNotNull(found)
        assertEquals("hash-abc", found.identifierHash)
        assertEquals(IdentifierType.DID, found.identifierType)
        assertEquals("identity-1", found.internalIdentityId)
    }

    @Test
    fun findByIdReturnsNullWhenNotFound() = runTest {
        val store = createStore()
        val result = store.findById("tenant-1", "nonexistent")
        assertNull(result)
    }

    @Test
    fun findByIdentifierHash() = runTest {
        val store = createStore()
        store.create(createMatch())

        val found = store.findByIdentifierHash("tenant-1", "hash-abc", IdentifierType.DID)
        assertNotNull(found)
        assertEquals("match-1", found.id)
    }

    @Test
    fun findByIdentifierHashReturnsNullForWrongType() = runTest {
        val store = createStore()
        store.create(createMatch(identifierType = IdentifierType.DID))

        val found = store.findByIdentifierHash("tenant-1", "hash-abc", IdentifierType.EMAIL)
        assertNull(found)
    }

    @Test
    fun findByIdentifierHashReturnsNullForWrongTenant() = runTest {
        val store = createStore()
        store.create(createMatch(tenantId = "tenant-1"))

        val found = store.findByIdentifierHash("tenant-2", "hash-abc", IdentifierType.DID)
        assertNull(found)
    }

    @Test
    fun findByInternalIdentityId() = runTest {
        val store = createStore()
        store.create(createMatch(id = "m1", identifierHash = "hash-1", internalIdentityId = "identity-1"))
        store.create(createMatch(id = "m2", identifierHash = "hash-2", internalIdentityId = "identity-1"))
        store.create(createMatch(id = "m3", identifierHash = "hash-3", internalIdentityId = "identity-2"))

        val matches = store.findByInternalIdentityId("tenant-1", "identity-1")
        assertEquals(2, matches.size)
        assertTrue(matches.any { it.id == "m1" })
        assertTrue(matches.any { it.id == "m2" })
    }

    @Test
    fun findByInternalIdentityIdReturnsEmptyForWrongTenant() = runTest {
        val store = createStore()
        store.create(createMatch(tenantId = "tenant-1"))

        val matches = store.findByInternalIdentityId("tenant-2", "identity-1")
        assertTrue(matches.isEmpty())
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
    fun deleteReturnsFalseWhenNotFound() = runTest {
        val store = createStore()
        val deleted = store.delete("tenant-1", "nonexistent")
        assertTrue(!deleted)
    }

    @Test
    fun tenantIsolation() = runTest {
        val store = createStore()
        store.create(createMatch(id = "m1", tenantId = "tenant-1"))
        store.create(createMatch(id = "m2", tenantId = "tenant-2", identifierHash = "hash-other"))

        assertNotNull(store.findById("tenant-1", "m1"))
        assertNull(store.findById("tenant-2", "m1"))
        assertNull(store.findById("tenant-1", "m2"))
        assertNotNull(store.findById("tenant-2", "m2"))
    }

    @Test
    fun metadataIsPreserved() = runTest {
        val store = createStore()
        val metadata = mapOf("source" to "oid4vp", "key_type" to "ES256")
        store.create(createMatch(metadata = metadata))

        val found = store.findById("tenant-1", "match-1")
        assertNotNull(found)
        assertEquals("oid4vp", found.metadata["source"])
        assertEquals("ES256", found.metadata["key_type"])
    }

    @Test
    fun customIdentifierType() = runTest {
        val store = createStore()
        val customType = IdentifierType("CUSTOM_BIOMETRIC")
        store.create(createMatch(identifierType = customType))

        val found = store.findByIdentifierHash("tenant-1", "hash-abc", customType)
        assertNotNull(found)
        assertEquals(customType, found.identifierType)
    }
}
