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

package com.sphereon.oauth2.server.authorization.impl.storage.memory

import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Behavioural coverage of [InMemoryPendingAuthorizationSessionStore]. Pins down the contract any
 * EDK overlay (Postgres, Redis) must satisfy: round-trip create/find, idempotent remove, and
 * absent-vs-failure differentiation on `findById`.
 */
class InMemoryPendingAuthorizationSessionStoreTest {
    @Test
    fun createAndRetrieveRoundTrip() =
        runTest {
            val store = InMemoryPendingAuthorizationSessionStore()
            val session = session("sess-1")

            val stored = store.create(session)
            assertTrue(stored.isOk)
            assertEquals(session, stored.value)

            val retrieved = store.findById("sess-1")
            assertTrue(retrieved.isOk)
            val value = retrieved.value
            assertNotNull(value)
            assertEquals(session, value)
        }

    @Test
    fun findByIdReturnsNullForUnknownId() =
        runTest {
            val store = InMemoryPendingAuthorizationSessionStore()

            val retrieved = store.findById("never-stored")
            assertTrue(retrieved.isOk)
            assertNull(retrieved.value)
        }

    @Test
    fun removeDeletesEntryAndIsIdempotent() =
        runTest {
            val store = InMemoryPendingAuthorizationSessionStore()
            assertTrue(store.create(session("sess-rm")).isOk)

            val firstRemove = store.remove("sess-rm")
            assertTrue(firstRemove.isOk)

            val afterRemove = store.findById("sess-rm")
            assertTrue(afterRemove.isOk)
            assertNull(afterRemove.value)

            val secondRemove = store.remove("sess-rm")
            assertTrue(secondRemove.isOk)
        }

    @Test
    fun createOverwritesExistingSessionForSameId() =
        runTest {
            val store = InMemoryPendingAuthorizationSessionStore()
            val original = session("dup", clientId = "client-a")
            val replacement = session("dup", clientId = "client-b")

            assertTrue(store.create(original).isOk)
            assertTrue(store.create(replacement).isOk)

            val retrieved = store.findById("dup")
            assertTrue(retrieved.isOk)
            val value = retrieved.value
            assertNotNull(value)
            assertEquals("client-b", value.clientId)
        }

    private fun session(
        id: String,
        clientId: String = "client-$id",
    ): AuthorizationSession {
        val now = Clock.System.now()
        return AuthorizationSession(
            sessionId = id,
            clientId = clientId,
            responseType = "code",
            redirectUri = "https://rp.example/callback",
            createdAt = now,
            expiresAt = now + 10.minutes,
        )
    }
}
