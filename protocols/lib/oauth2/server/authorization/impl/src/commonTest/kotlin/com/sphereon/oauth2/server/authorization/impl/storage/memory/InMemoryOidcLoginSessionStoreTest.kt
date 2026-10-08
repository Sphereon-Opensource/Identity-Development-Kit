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

import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Behavioural coverage for [InMemoryOidcLoginSessionStore]. Pins down the contract any EDK
 * overlay (Postgres, Redis) must satisfy: round-trip CRUD, idle vs absolute expiry, idempotent
 * revoke, and bulk revoke-by-sub.
 */
class InMemoryOidcLoginSessionStoreTest {
    @Test
    fun createAndFindRoundTrip() =
        runTest {
            val clock = MutableClock(Instant.fromEpochSeconds(1_700_000_000))
            val store = InMemoryOidcLoginSessionStore(clock)
            val session = newSession("sid-1", clock.now())

            val created = store.create(session)
            assertTrue(created.isOk)
            assertEquals(session, created.value)

            val found = store.findById("sid-1")
            assertTrue(found.isOk)
            val value = found.value
            assertNotNull(value)
            assertEquals(session, value)
        }

    @Test
    fun findByIdReturnsNullForUnknownId() =
        runTest {
            val store = InMemoryOidcLoginSessionStore(MutableClock(Instant.fromEpochSeconds(1_700_000_000)))

            val found = store.findById("never-stored")
            assertTrue(found.isOk)
            assertNull(found.value)
        }

    @Test
    fun findByIdReturnsNullAfterIdleExpiry() =
        runTest {
            val clock = MutableClock(Instant.fromEpochSeconds(1_700_000_000))
            val store = InMemoryOidcLoginSessionStore(clock)
            val session = newSession("sid-idle", clock.now(), idleTtl = 60.seconds, absoluteTtl = 24.minutes)
            assertTrue(store.create(session).isOk)

            clock.advance(61.seconds)
            val found = store.findById("sid-idle")
            assertTrue(found.isOk)
            assertNull(found.value)
        }

    @Test
    fun findByIdReturnsNullAfterAbsoluteExpiry() =
        runTest {
            val clock = MutableClock(Instant.fromEpochSeconds(1_700_000_000))
            val store = InMemoryOidcLoginSessionStore(clock)
            val session = newSession("sid-abs", clock.now(), idleTtl = 30.minutes, absoluteTtl = 5.minutes)
            assertTrue(store.create(session).isOk)

            clock.advance(6.minutes)
            val found = store.findById("sid-abs")
            assertTrue(found.isOk)
            assertNull(found.value)
        }

    @Test
    fun touchExtendsIdleButNotAbsolute() =
        runTest {
            val clock = MutableClock(Instant.fromEpochSeconds(1_700_000_000))
            val store = InMemoryOidcLoginSessionStore(clock)
            val absolute = clock.now() + 10.minutes
            val session =
                newSession("sid-touch", clock.now(), idleTtl = 60.seconds).copy(absoluteExpiresAt = absolute)
            assertTrue(store.create(session).isOk)

            clock.advance(30.seconds)
            val touched = store.touch("sid-touch", clock.now(), idleTtlSeconds = 120)
            assertTrue(touched.isOk)
            val refreshed = touched.value
            assertNotNull(refreshed)
            assertEquals(clock.now() + 120.seconds, refreshed.idleExpiresAt)
            assertEquals(absolute, refreshed.absoluteExpiresAt)
        }

    @Test
    fun touchCapsIdleAtAbsoluteExpiry() =
        runTest {
            val clock = MutableClock(Instant.fromEpochSeconds(1_700_000_000))
            val store = InMemoryOidcLoginSessionStore(clock)
            val absolute = clock.now() + 90.seconds
            val session = newSession("sid-cap", clock.now(), idleTtl = 60.seconds).copy(absoluteExpiresAt = absolute)
            assertTrue(store.create(session).isOk)

            clock.advance(30.seconds)
            val touched = store.touch("sid-cap", clock.now(), idleTtlSeconds = 600)
            assertTrue(touched.isOk)
            assertEquals(absolute, touched.value!!.idleExpiresAt)
        }

    @Test
    fun touchReturnsNullForExpiredSession() =
        runTest {
            val clock = MutableClock(Instant.fromEpochSeconds(1_700_000_000))
            val store = InMemoryOidcLoginSessionStore(clock)
            val session = newSession("sid-gone", clock.now(), absoluteTtl = 60.seconds)
            assertTrue(store.create(session).isOk)

            clock.advance(120.seconds)
            val touched = store.touch("sid-gone", clock.now(), idleTtlSeconds = 600)
            assertTrue(touched.isOk)
            assertNull(touched.value)
        }

    @Test
    fun revokeIsIdempotent() =
        runTest {
            val clock = MutableClock(Instant.fromEpochSeconds(1_700_000_000))
            val store = InMemoryOidcLoginSessionStore(clock)
            assertTrue(store.create(newSession("sid-rm", clock.now())).isOk)

            assertTrue(store.revoke("sid-rm").isOk)
            assertNull(store.findById("sid-rm").value)
            assertTrue(store.revoke("sid-rm").isOk)
        }

    @Test
    fun revokeAllForUserRemovesEverySessionForSub() =
        runTest {
            val clock = MutableClock(Instant.fromEpochSeconds(1_700_000_000))
            val store = InMemoryOidcLoginSessionStore(clock)
            assertTrue(store.create(newSession("a", clock.now(), sub = "alice")).isOk)
            assertTrue(store.create(newSession("b", clock.now(), sub = "alice")).isOk)
            assertTrue(store.create(newSession("c", clock.now(), sub = "bob")).isOk)

            assertTrue(store.revokeAllForUser("alice").isOk)

            assertNull(store.findById("a").value)
            assertNull(store.findById("b").value)
            val survivor = store.findById("c").value
            assertNotNull(survivor)
            assertEquals("bob", survivor.sub)
        }

    private fun newSession(
        id: String,
        now: Instant,
        sub: String = "user-$id",
        idleTtl: kotlin.time.Duration = 30.minutes,
        absoluteTtl: kotlin.time.Duration = 8.minutes * 60,
    ): OidcLoginSession =
        OidcLoginSession(
            sessionId = id,
            sub = sub,
            authTime = now,
            authMethod = AuthenticationMethod.PASSWORD,
            createdAt = now,
            absoluteExpiresAt = now + absoluteTtl,
            idleExpiresAt = now + idleTtl,
        )

    private class MutableClock(
        private var current: Instant
    ) : Clock {
        override fun now(): Instant = current

        fun advance(duration: kotlin.time.Duration) {
            current += duration
        }
    }
}
