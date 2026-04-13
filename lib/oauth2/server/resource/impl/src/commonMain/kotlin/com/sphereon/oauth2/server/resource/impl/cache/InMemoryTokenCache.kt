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

package com.sphereon.oauth2.server.resource.impl.cache

import com.sphereon.oauth2.server.resource.cache.TokenCache
import com.sphereon.oauth2.server.resource.model.TokenPayload
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * In-memory implementation of TokenCache
 *
 * **Thread Safety**: Basic implementation using concurrent map.
 * For production with high concurrency, consider more sophisticated locking.
 *
 * **Memory Management**: No automatic cleanup - relies on manual cleanup calls.
 * Production implementations should implement background cleanup thread.
 *
 * **Cache Strategy**:
 * - Stores validated token payloads with expiration
 * - Automatically checks expiration on get()
 * - Does NOT cache invalid tokens
 *
 * **Suitable for**:
 * - Single-instance deployments
 * - Development/testing
 * - Low-traffic production (< 1000 req/s)
 *
 * **NOT suitable for**:
 * - Multi-instance deployments (use Redis/Memcached)
 * - High-traffic production (needs distributed cache)
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<TokenCache>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryTokenCacheImpl", exact = true)
class InMemoryTokenCacheImpl : TokenCache {

    @ContributesTo(AppScope::class)
    interface Component {
        val tokenCache: TokenCache
    }

    private data class CacheEntry(
        val payload: TokenPayload,
        val expiresAt: Instant
    )

    // Using a mutable map - not fully thread-safe
    // For production with high concurrency, use platform-specific concurrent collections
    private val cache = mutableMapOf<String, CacheEntry>()

    override suspend fun get(token: String): TokenPayload? {
        val entry = cache[token] ?: return null

        // Check if expired
        if (Clock.System.now() >= entry.expiresAt) {
            cache.remove(token)
            return null
        }

        return entry.payload
    }

    override suspend fun put(token: String, payload: TokenPayload, expiresAt: Instant) {
        cache[token] = CacheEntry(payload, expiresAt)
    }

    override suspend fun remove(token: String) {
        cache.remove(token)
    }

    override suspend fun clear() {
        cache.clear()
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

        val iterator = cache.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now >= entry.value.expiresAt) {
                iterator.remove()
                removed++
            }
        }

        return removed
    }

    /**
     * Returns current cache size
     */
    fun size(): Int = cache.size
}
