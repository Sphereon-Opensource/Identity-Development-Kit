/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.config

import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * App-scoped cache of the immutable descriptor for each tenant's current AS signing key.
 *
 * The durable [com.sphereon.oauth2.server.authorization.storage.SigningKeyStore] remains the
 * authority. A cache entry is addressed by the store's durable tenant-local monotonic revision.
 * The revision is read before every reuse, so replicas and out-of-band database mutations cannot
 * leave an entry valid indefinitely. A load is accepted only when a second authoritative revision
 * read still matches, which prevents a concurrent rotation from publishing the previous ACTIVE
 * key back into the cache.
 *
 * This cache has no arbitrary TTL. A future ACTIVE key's `notBefore` is a semantic lifecycle
 * boundary, so an entry records the earliest future activation and becomes stale exactly then.
 * Failures and the absence of an eligible ACTIVE key are never cached; callers therefore remain
 * fail closed and can recover immediately after provisioning succeeds.
 */
@Inject
@SingleIn(AppScope::class)
class ActiveSigningKeySnapshotCache(
    private val clock: Clock = Clock.System,
) : SynchronizedObject() {
    private data class Entry(
        val revision: Long,
        val active: OAuth2SigningKey,
        val reevaluateAt: Instant?,
    )

    private val entries: MutableMap<String, Entry> = mutableMapOf()
    private val loadLocks: MutableMap<String, Mutex> = mutableMapOf()

    /**
     * Resolve the current ACTIVE key from a revision-matched snapshot, loading the tenant's
     * immutable key descriptors only on a miss. [readRevision] and [loadAll] must read the same
     * authoritative store.
     */
    suspend fun resolve(
        tenantId: String,
        readRevision: suspend () -> Long,
        loadAll: suspend () -> List<OAuth2SigningKey>,
    ): OAuth2SigningKey? {
        val initialRevision = readRevision()
        cached(tenantId, initialRevision)?.let { return it }

        return loadLock(tenantId).withLock {
            while (true) {
                val expectedRevision = readRevision()
                cached(tenantId, expectedRevision)?.let { return@withLock it }

                val keys = loadAll()
                val now = clock.now()
                val active = selectActive(tenantId, keys, now)
                val reevaluateAt = nextActivation(tenantId, keys, now)
                val confirmedRevision = readRevision()

                val accepted =
                    synchronized(this) {
                        if (confirmedRevision != expectedRevision) {
                            false
                        } else {
                            if (active != null) {
                                entries[tenantId] =
                                    Entry(
                                        revision = expectedRevision,
                                        active = active,
                                        reevaluateAt = reevaluateAt,
                                    )
                            }
                            true
                        }
                    }
                if (accepted) {
                    return@withLock active
                }
            }

            @Suppress("UNREACHABLE_CODE")
            null
        }
    }

    private fun cached(
        tenantId: String,
        authoritativeRevision: Long,
    ): OAuth2SigningKey? =
        synchronized(this) {
            val entry = entries[tenantId] ?: return@synchronized null
            if (entry.revision != authoritativeRevision) {
                entries.remove(tenantId)
                return@synchronized null
            }

            val reevaluateAt = entry.reevaluateAt
            if (reevaluateAt != null && reevaluateAt <= clock.now()) {
                entries.remove(tenantId)
                return@synchronized null
            }
            entry.active
        }

    private fun loadLock(tenantId: String): Mutex =
        synchronized(this) {
            loadLocks.getOrPut(tenantId) { Mutex() }
        }

    private fun selectActive(
        tenantId: String,
        keys: List<OAuth2SigningKey>,
        now: Instant,
    ): OAuth2SigningKey? =
        keys
            .asSequence()
            .filter {
                it.tenantId == tenantId &&
                    it.state == OAuth2SigningKeyState.ACTIVE &&
                    it.notBefore <= now
            }.maxWithOrNull(activePriorityOrder)

    private fun nextActivation(
        tenantId: String,
        keys: List<OAuth2SigningKey>,
        now: Instant,
    ): Instant? =
        keys
            .asSequence()
            .filter {
                it.tenantId == tenantId &&
                    it.state == OAuth2SigningKeyState.ACTIVE &&
                    it.notBefore > now
            }.map { it.notBefore }
            .minOrNull()

    private companion object {
        val activePriorityOrder: Comparator<OAuth2SigningKey> =
            compareBy<OAuth2SigningKey> { it.priority }.thenBy { it.createdAt }
    }
}
