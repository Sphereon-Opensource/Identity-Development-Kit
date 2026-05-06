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

import com.sphereon.oauth2.server.authorization.storage.ClientAssertionJtiStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * In-memory, TTL-backed implementation of [ClientAssertionJtiStore].
 *
 * Suitable for single-node OIDF conformance testing. Production deployments should substitute a
 * distributed-store implementation (Redis/Postgres) — see the WP5 follow-up listed in
 * `docs/reviews/2026-04-24-oidf-readiness/wp2-client-auth-hardening.md`.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ClientAssertionJtiStore>())
public class InMemoryClientAssertionJtiStore : ClientAssertionJtiStore {
    // Keyed on (clientId, jti); value is the expiry of the assertion. Expired entries are purged
    // lazily on each access so the map stays bounded by the active assertion-lifetime window.
    // Matches the concurrency posture of peer stores (InMemoryNonceStorageImpl etc.) — production
    // replaces this with Redis/Postgres where atomicity is native.
    private val entries = HashMap<Key, Instant>()

    override suspend fun recordIfNew(
        clientId: String,
        jti: String,
        expiresAt: Instant,
    ): Boolean {
        val now = Clock.System.now()
        entries.entries.removeAll { it.value <= now }
        val key = Key(clientId, jti)
        return if (entries.containsKey(key)) {
            false
        } else {
            entries[key] = expiresAt
            true
        }
    }

    private data class Key(
        val clientId: String,
        val jti: String,
    )
}
