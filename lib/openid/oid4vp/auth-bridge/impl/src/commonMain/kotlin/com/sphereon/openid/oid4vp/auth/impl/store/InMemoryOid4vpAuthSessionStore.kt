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

package com.sphereon.openid.oid4vp.auth.impl.store

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.openid.oid4vp.auth.error.Oid4vpAuthErrors
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSession
import com.sphereon.openid.oid4vp.auth.store.Oid4vpAuthSessionStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * In-memory implementation of [Oid4vpAuthSessionStore].
 *
 * This implementation stores sessions in memory with TTL-based expiration.
 * It is suitable for development and testing. For production use, implement
 * a store backed by a persistent KV store or database.
 *
 * Features:
 * - Automatic expiration checking on read operations
 * - Manual cleanup of expired sessions via [cleanupExpired]
 * - Thread-safe for single-threaded coroutine contexts
 *
 * TODO (VDX-36): Replace this with a persistent session store (e.g., KvBackedOid4vpAuthSessionStore)
 *                for production deployments. The in-memory store does not persist sessions across
 *                restarts and does not support distributed/clustered deployments.
 *
 * Scope note:
 * - This store is AppScope on purpose. In the current Ktor setup, a new DI session ID is generated
 *   per HTTP request, so SessionScope would create a fresh store instance for each request and lose
 *   data needed by later /status and /complete calls.
 * - SessionScope can work only if requests in the same auth flow reuse a stable DI session ID
 *   (for example via cookie/header mapped to createOrGetFromId) and that session lifecycle is
 *   explicitly cleaned up (destroy/expiry/logout).
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<Oid4vpAuthSessionStore>())
class InMemoryOid4vpAuthSessionStore(
    logManager: AppLogManager,
) : Oid4vpAuthSessionStore {
    private val log = logManager.withTag("Oid4vpSessionStore")

    private data class StoredSession(
        val session: Oid4vpAuthSession,
        val expiresAt: Long,
    )

    private val sessions = mutableMapOf<String, StoredSession>()

    override suspend fun put(
        sessionId: String,
        session: Oid4vpAuthSession,
        ttl: Duration,
    ): IdkResult<Unit, IdkError> =
        try {
            val expiresAt = Clock.System.now().toEpochMilliseconds() + ttl.inWholeMilliseconds
            sessions[sessionId] = StoredSession(session, expiresAt)
            Ok(Unit)
        } catch (expected: Exception) {
            Err(Oid4vpAuthErrors.sessionStoreFailed("Failed to store session: ${expected.message}", expected))
        }

    override suspend fun get(sessionId: String): IdkResult<Oid4vpAuthSession?, IdkError> =
        try {
            val stored = sessions[sessionId]
            if (stored == null) {
                Ok(null)
            } else if (Clock.System.now().toEpochMilliseconds() > stored.expiresAt) {
                // Session expired, remove it
                sessions.remove(sessionId)
                Ok(null)
            } else {
                Ok(stored.session)
            }
        } catch (expected: Exception) {
            Err(Oid4vpAuthErrors.sessionStoreFailed("Failed to retrieve session: ${expected.message}", expected))
        }

    override suspend fun delete(sessionId: String): IdkResult<Unit, IdkError> =
        try {
            sessions.remove(sessionId)
            Ok(Unit)
        } catch (expected: Exception) {
            Err(Oid4vpAuthErrors.sessionStoreFailed("Failed to delete session: ${expected.message}", expected))
        }

    override suspend fun exists(sessionId: String): IdkResult<Boolean, IdkError> =
        try {
            val stored = sessions[sessionId]
            if (stored == null) {
                Ok(false)
            } else if (Clock.System.now().toEpochMilliseconds() > stored.expiresAt) {
                sessions.remove(sessionId)
                Ok(false)
            } else {
                Ok(true)
            }
        } catch (expected: Exception) {
            Err(Oid4vpAuthErrors.sessionStoreFailed("Failed to check session existence: ${expected.message}", expected))
        }

    override suspend fun findByReconciliationSessionId(reconciliationSessionId: String): IdkResult<Oid4vpAuthSession?, IdkError> =
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            log.debug("findByReconciliationSessionId: looking for reconciliationSessionId=$reconciliationSessionId")
            log.debug("Store has ${sessions.size} sessions")
            sessions.forEach { (id, stored) ->
                log.debug("  session=$id, reconciliationSessionId=${stored.session.reconciliationSessionId}, expired=${stored.expiresAt <= now}, status=${stored.session.status}")
            }
            val found =
                sessions.values.firstOrNull { stored ->
                    stored.expiresAt > now && stored.session.reconciliationSessionId == reconciliationSessionId
                }
            val resultDescription =
                if (found != null) {
                    "FOUND session ${found.session.sessionId}"
                } else {
                    "NOT FOUND"
                }
            log.debug("Result: $resultDescription")
            Ok(found?.session)
        } catch (expected: Exception) {
            Err(Oid4vpAuthErrors.sessionStoreFailed("Failed to find session by reconciliation ID: ${expected.message}", expected))
        }

    /**
     * Clean up expired sessions.
     *
     * This method removes all sessions that have exceeded their TTL.
     * Call this periodically to prevent memory buildup from expired sessions.
     *
     * @return The number of expired sessions that were removed.
     */
    fun cleanupExpired(): Int {
        val now = Clock.System.now().toEpochMilliseconds()
        val expiredKeys =
            sessions.entries
                .filter { it.value.expiresAt < now }
                .map { it.key }
        expiredKeys.forEach { sessions.remove(it) }
        return expiredKeys.size
    }

    /**
     * Get the current number of stored sessions.
     *
     * @return The number of sessions currently stored (including potentially expired ones).
     */
    fun sessionCount(): Int = sessions.size
}
