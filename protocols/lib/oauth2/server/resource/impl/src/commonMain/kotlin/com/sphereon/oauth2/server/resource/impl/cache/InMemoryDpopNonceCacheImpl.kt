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

package com.sphereon.oauth2.server.resource.impl.cache

import com.sphereon.oauth2.server.resource.cache.DpopNonceCache
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * In-memory implementation of DpopNonceCache
 *
 * **DPoP Replay Protection**:
 * - Tracks used DPoP proof JTI (unique identifiers)
 * - Prevents replay attacks within time window
 * - Automatically considers expired entries as "not used"
 *
 * **Thread Safety**: Basic implementation using concurrent map.
 * For production with high concurrency, consider more sophisticated locking.
 *
 * **Security Note**: This single-instance cache creates a replay window
 * in multi-instance deployments. For production with multiple instances,
 * use a distributed cache (Redis, Memcached).
 *
 * **Time Window**:
 * - DPoP proofs are valid for ~60 seconds (configurable in verification)
 * - JTI entries should be kept for window + clock skew (e.g., 120 seconds)
 * - Automatic cleanup on each check reduces memory usage
 *
 * **Suitable for**:
 * - Single-instance deployments
 * - Development/testing
 * - Low-traffic production
 *
 * **NOT suitable for**:
 * - Multi-instance deployments without distributed cache
 * - High-security scenarios requiring guaranteed replay protection
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DpopNonceCache>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryDpopNonceCacheImpl", exact = true)
class InMemoryDpopNonceCacheImpl : DpopNonceCache {
    // Map of jti -> expiresAt
    // Not fully thread-safe - for production with high concurrency, use platform-specific concurrent collections
    private val usedNonces = mutableMapOf<String, Instant>()

    override suspend fun hasBeenUsed(jti: String): Boolean {
        val now = Clock.System.now()

        val expiresAt = usedNonces[jti] ?: return false

        // If expired, remove and return false (can be reused)
        if (now >= expiresAt) {
            usedNonces.remove(jti)
            return false
        }

        // Still within time window - has been used
        return true
    }

    override suspend fun markAsUsed(
        jti: String,
        expiresAt: Instant,
    ) {
        usedNonces[jti] = expiresAt
    }

    override suspend fun clear() {
        usedNonces.clear()
    }

    /**
     * Removes expired entries from cache
     *
     * Should be called periodically for memory management.
     * Returns number of entries removed.
     */
    suspend fun cleanup(): Int {
        val now = Clock.System.now()
        var removed = 0

        val iterator = usedNonces.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now >= entry.value) {
                iterator.remove()
                removed++
            }
        }

        return removed
    }

    /**
     * Returns current cache size
     */
    fun size(): Int = usedNonces.size

    @ContributesTo(AppScope::class)
    interface Graph {
        val dpopNonceCache: DpopNonceCache
    }
}
