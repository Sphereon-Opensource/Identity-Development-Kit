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

package com.sphereon.identity.reconciliation.impl.store

import com.sphereon.core.defaults.log.AppLogManagerImpl
import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.reconciliation.model.ReconciliationSession
import com.sphereon.identity.reconciliation.model.ReconciliationSessionStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class InMemoryReconciliationSessionStoreTest {
    private fun createStore() = InMemoryReconciliationSessionStore(AppLogManagerImpl(emptySet()))

    private fun createSession(
        id: String = "session-1",
        tenantId: String = "tenant-1",
        status: ReconciliationSessionStatus = ReconciliationSessionStatus.CREATED,
        state: String? = "state-abc",
        expiresIn: kotlin.time.Duration = 10.minutes,
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
            expiresAt = now + expiresIn,
        )
    }

    @Test
    fun createAndFindById() =
        runTest {
            val store = createStore()
            val session = createSession()

            store.create(session)

            val found = store.findById("tenant-1", "session-1")
            assertNotNull(found)
            assertEquals("session-1", found.id)
            assertEquals(ReconciliationSessionStatus.CREATED, found.status)
            assertEquals("hash-123", found.identifierHash)
        }

    @Test
    fun findByIdReturnsNullWhenNotFound() =
        runTest {
            val store = createStore()
            assertNull(store.findById("tenant-1", "nonexistent"))
        }

    @Test
    fun findByState() =
        runTest {
            val store = createStore()
            store.create(createSession(state = "unique-state-abc"))

            val found = store.findByState("tenant-1", "unique-state-abc")
            assertNotNull(found)
            assertEquals("session-1", found.id)
        }

    @Test
    fun findByStateReturnsNullWhenNotFound() =
        runTest {
            val store = createStore()
            assertNull(store.findByState("tenant-1", "nonexistent-state"))
        }

    @Test
    fun updateSession() =
        runTest {
            val store = createStore()
            val session = createSession()
            store.create(session)

            val updated = session.copy(status = ReconciliationSessionStatus.COMPLETED)
            store.update(updated)

            val found = store.findById("tenant-1", "session-1")
            assertNotNull(found)
            assertEquals(ReconciliationSessionStatus.COMPLETED, found.status)
        }

    @Test
    fun updateRemovesOldStateMapping() =
        runTest {
            val store = createStore()
            store.create(createSession(state = "old-state"))

            val session = store.findById("tenant-1", "session-1")!!
            store.update(session.copy(state = "new-state"))

            assertNull(store.findByState("tenant-1", "old-state"))
            assertNotNull(store.findByState("tenant-1", "new-state"))
        }

    @Test
    fun deleteSession() =
        runTest {
            val store = createStore()
            store.create(createSession())

            val deleted = store.delete("tenant-1", "session-1")
            assertTrue(deleted)
            assertNull(store.findById("tenant-1", "session-1"))
            assertNull(store.findByState("tenant-1", "state-abc"))
        }

    @Test
    fun deleteReturnsFalseWhenNotFound() =
        runTest {
            val store = createStore()
            assertTrue(!store.delete("tenant-1", "nonexistent"))
        }

    @Test
    fun findExpiredSessions() =
        runTest {
            val store = createStore()
            val now = Clock.System.now()

            // Create expired session
            store.create(createSession(id = "expired", expiresIn = (-5).minutes))
            // Create valid session
            store.create(createSession(id = "valid", state = "state-2", expiresIn = 10.minutes))

            val expired = store.findExpired("tenant-1", now)
            assertEquals(1, expired.size)
            assertEquals("expired", expired[0].id)
        }

    @Test
    fun tenantIsolation() =
        runTest {
            val store = createStore()
            store.create(createSession(id = "s1", tenantId = "tenant-1"))
            store.create(createSession(id = "s2", tenantId = "tenant-2", state = "state-2"))

            assertNotNull(store.findById("tenant-1", "s1"))
            assertNull(store.findById("tenant-2", "s1"))
            assertNull(store.findById("tenant-1", "s2"))
            assertNotNull(store.findById("tenant-2", "s2"))
        }
}
