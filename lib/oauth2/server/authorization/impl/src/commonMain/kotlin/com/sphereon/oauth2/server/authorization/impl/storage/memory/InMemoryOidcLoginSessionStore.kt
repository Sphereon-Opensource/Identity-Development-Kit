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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSession
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSessionStore
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSessionStoreError
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * In-memory backing for [OidcLoginSessionStore] suitable for development, OIDF conformance, and
 * single-node deployments. Bound at [AppScope] because the cookie addresses the same record
 * across many [com.sphereon.di.session.SessionScope] instances (one per HTTP request).
 *
 * [Clock] is injected so unit tests can pin time without touching the wall clock.
 *
 * Per `feedback_idk_persistence_drivers.md` IDK ships in-memory only; EDK / VDX overlays supply
 * durable replacements via `@ContributesBinding(replaces = [InMemoryOidcLoginSessionStore::class])`.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<OidcLoginSessionStore>())
class InMemoryOidcLoginSessionStore(
    internal val clock: Clock,
) : OidcLoginSessionStore {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, OidcLoginSession>()

    override suspend fun create(session: OidcLoginSession): IdkResult<OidcLoginSession, OidcLoginSessionStoreError> {
        mutex.withLock { sessions[session.sessionId] = session }
        return Ok(session)
    }

    override suspend fun findById(sessionId: String): IdkResult<OidcLoginSession?, OidcLoginSessionStoreError> {
        val now = clock.now()
        val active =
            mutex.withLock {
                val candidate = sessions[sessionId] ?: return@withLock null
                if (candidate.isExpired(now)) {
                    sessions.remove(sessionId)
                    null
                } else {
                    candidate
                }
            }
        return Ok(active)
    }

    override suspend fun touch(
        sessionId: String,
        now: Instant,
        idleTtlSeconds: Int,
    ): IdkResult<OidcLoginSession?, OidcLoginSessionStoreError> {
        val updated =
            mutex.withLock {
                val candidate = sessions[sessionId] ?: return@withLock null
                if (now >= candidate.absoluteExpiresAt) {
                    sessions.remove(sessionId)
                    return@withLock null
                }
                val nextIdle = now + idleTtlSeconds.seconds
                val cappedIdle = if (nextIdle > candidate.absoluteExpiresAt) candidate.absoluteExpiresAt else nextIdle
                val refreshed = candidate.copy(idleExpiresAt = cappedIdle)
                sessions[sessionId] = refreshed
                refreshed
            }
        return Ok(updated)
    }

    override suspend fun recordRpParticipation(
        sessionId: String,
        clientId: String,
        sid: String,
    ): IdkResult<OidcLoginSession?, OidcLoginSessionStoreError> {
        val now = clock.now()
        val updated =
            mutex.withLock {
                val candidate = sessions[sessionId] ?: return@withLock null
                if (candidate.isExpired(now)) {
                    sessions.remove(sessionId)
                    return@withLock null
                }
                val nextRpSessions = candidate.rpSessions.toMutableMap()
                nextRpSessions[clientId] = sid
                val next = candidate.copy(rpSessions = nextRpSessions)
                sessions[sessionId] = next
                next
            }
        return Ok(updated)
    }

    override suspend fun revoke(sessionId: String): IdkResult<Unit, OidcLoginSessionStoreError> {
        mutex.withLock { sessions.remove(sessionId) }
        return Ok(Unit)
    }

    override suspend fun revokeAllForUser(sub: String): IdkResult<Unit, OidcLoginSessionStoreError> {
        mutex.withLock {
            val victims = sessions.entries.filter { it.value.sub == sub }.map { it.key }
            victims.forEach(sessions::remove)
        }
        return Ok(Unit)
    }

    private fun OidcLoginSession.isExpired(now: Instant): Boolean = now >= absoluteExpiresAt || now >= idleExpiresAt
}
