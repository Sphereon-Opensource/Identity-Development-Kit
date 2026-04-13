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

package com.sphereon.openid.oid4vp.auth.impl.store

import com.sphereon.core.defaults.log.AppLogManagerImpl
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSession
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSessionStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class InMemoryOid4vpAuthSessionStoreTest {
    private val store = InMemoryOid4vpAuthSessionStore(AppLogManagerImpl(emptySet()))

    private fun createTestSession(
        sessionId: String = "test-session-123",
        correlationId: String = "correlation-456",
        status: Oid4vpAuthSessionStatus = Oid4vpAuthSessionStatus.PENDING,
    ): Oid4vpAuthSession {
        val now = Clock.System.now()
        return Oid4vpAuthSession(
            sessionId = sessionId,
            correlationId = correlationId,
            oauthSessionId = null,
            queryId = "test-query",
            status = status,
            verifiedData = null,
            resolvedUserId = null,
            errorMessage = null,
            createdAt = now,
            updatedAt = now,
            expiresAt = now + 300.seconds,
        )
    }

    @Test
    fun `put and get should work`() =
        runTest {
            val session = createTestSession()

            val putResult = store.put(session.sessionId, session, 300.seconds)
            assertTrue(putResult.isOk)

            val getResult = store.get(session.sessionId)
            assertTrue(getResult.isOk)
            assertNotNull(getResult.value)
            assertEquals(session.sessionId, getResult.value!!.sessionId)
            assertEquals(session.correlationId, getResult.value!!.correlationId)
        }

    @Test
    fun `get should return null for non-existent session`() =
        runTest {
            val result = store.get("non-existent")
            assertTrue(result.isOk)
            assertNull(result.value)
        }

    @Test
    fun `delete should remove session`() =
        runTest {
            val session = createTestSession()
            store.put(session.sessionId, session, 300.seconds)

            val deleteResult = store.delete(session.sessionId)
            assertTrue(deleteResult.isOk)

            val getResult = store.get(session.sessionId)
            assertTrue(getResult.isOk)
            assertNull(getResult.value)
        }

    @Test
    fun `exists should return true for existing session`() =
        runTest {
            val session = createTestSession()
            store.put(session.sessionId, session, 300.seconds)

            val result = store.exists(session.sessionId)
            assertTrue(result.isOk)
            assertTrue(result.value!!)
        }

    @Test
    fun `exists should return false for non-existent session`() =
        runTest {
            val result = store.exists("non-existent")
            assertTrue(result.isOk)
            assertFalse(result.value!!)
        }

    @Test
    fun `expired sessions should not be returned`() =
        runTest {
            val session = createTestSession()

            // Store with very short TTL
            store.put(session.sessionId, session, 1.milliseconds)

            // Wait for expiration
            Thread.sleep(10)

            val getResult = store.get(session.sessionId)
            assertTrue(getResult.isOk)
            assertNull(getResult.value) // Should be null because expired
        }

    @Test
    fun `cleanupExpired should remove expired sessions`() =
        runTest {
            val session1 = createTestSession(sessionId = "session-1")
            val session2 = createTestSession(sessionId = "session-2")

            // Store session1 with very short TTL
            store.put(session1.sessionId, session1, 1.milliseconds)
            // Store session2 with long TTL
            store.put(session2.sessionId, session2, 300.seconds)

            // Wait for session1 to expire
            Thread.sleep(10)

            // Cleanup
            val removed = store.cleanupExpired()
            assertEquals(1, removed)

            // session1 should be gone
            val result1 = store.get(session1.sessionId)
            assertTrue(result1.isOk)
            assertNull(result1.value)

            // session2 should still exist
            val result2 = store.get(session2.sessionId)
            assertTrue(result2.isOk)
            assertNotNull(result2.value)
        }

    @Test
    fun `update session should replace existing`() =
        runTest {
            val session = createTestSession()
            store.put(session.sessionId, session, 300.seconds)

            val updatedSession =
                session.copy(
                    status = Oid4vpAuthSessionStatus.VERIFIED,
                    updatedAt = Clock.System.now(),
                )
            store.put(session.sessionId, updatedSession, 300.seconds)

            val result = store.get(session.sessionId)
            assertTrue(result.isOk)
            assertNotNull(result.value)
            assertEquals(Oid4vpAuthSessionStatus.VERIFIED, result.value!!.status)
        }
}
