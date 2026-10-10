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

package com.sphereon.core.api.cache

import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Kache-based in-memory cache backend.
 *
 * Features:
 * - Safe concurrent access with serialized protection of Kache's mutable LRU metadata
 * - LRU eviction strategy for memory management
 * - Manual TTL expiration checking
 * - Pattern-based key operations via iteration
 * - Kotlin Multiplatform support (JVM, Native, JS)
 *
 * This is the default local cache backend and is always available.
 * For multi-instance deployments, use in combination with a distributed
 * backend via LayeredCacheBackend.
 */
@Inject
@SingleIn(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("KacheCacheBackend", exact = true)
class KacheCacheBackend(
    private val maxSize: Long = DEFAULT_MAX_SIZE,
) : CacheBackend {
    companion object {
        const val DEFAULT_MAX_SIZE: Long = 10_000
    }

    override val id: String = "kache"

    override val capabilities = BackendCapabilities.IN_MEMORY

    /**
     * Cache entry with TTL tracking.
     */
    private data class CacheEntry(
        val value: ByteArray,
        val expiresAt: Instant?,
    ) {
        fun isExpired(): Boolean = expiresAt != null && Clock.System.now() >= expiresAt

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as CacheEntry
            return value.contentEquals(other.value) && expiresAt == other.expiresAt
        }

        override fun hashCode(): Int {
            var result = value.contentHashCode()
            result = 31 * result + (expiresAt?.hashCode() ?: 0)
            return result
        }
    }

    private val cache =
        InMemoryKache<String, CacheEntry>(maxSize) {
            strategy = KacheStrategy.LRU
        }

    /**
     * InMemoryKache's LRU strategy mutates its access-order chain even for reads. The chain is not
     * safe to access concurrently, so every operation that touches [cache] must hold this mutex.
     */
    private val cacheMutex = Mutex()

    override suspend fun get(key: String): ByteArray? = cacheMutex.withLock { getUnlocked(key) }

    private suspend fun getUnlocked(key: String): ByteArray? {
        val entry = cache.getIfAvailable(key) ?: return null

        if (entry.isExpired()) {
            cache.remove(key)
            return null
        }

        return entry.value
    }

    override suspend fun set(
        key: String,
        value: ByteArray,
        ttlMs: Long?,
    ) = cacheMutex.withLock {
        setUnlocked(key, value, ttlMs)
    }

    private suspend fun setUnlocked(
        key: String,
        value: ByteArray,
        ttlMs: Long?,
    ) {
        val expiresAt = ttlMs?.let { Clock.System.now().plus(kotlin.time.Duration.parse("${it}ms")) }
        cache.put(key, CacheEntry(value, expiresAt))
    }

    override suspend fun delete(key: String): Boolean = cacheMutex.withLock { cache.remove(key) != null }

    override suspend fun exists(key: String): Boolean = cacheMutex.withLock { existsUnlocked(key) }

    private suspend fun existsUnlocked(key: String): Boolean {
        val entry = cache.getIfAvailable(key) ?: return false
        if (entry.isExpired()) {
            cache.remove(key)
            return false
        }
        return true
    }

    override suspend fun getMany(keys: Collection<String>): Map<String, ByteArray> = cacheMutex.withLock {
        val result = mutableMapOf<String, ByteArray>()
        for (key in keys) {
            getUnlocked(key)?.let { result[key] = it }
        }
        result
    }

    override suspend fun setMany(
        entries: Map<String, ByteArray>,
        ttlMs: Long?,
    ) = cacheMutex.withLock {
        entries.forEach { (key, value) ->
            setUnlocked(key, value, ttlMs)
        }
    }

    override suspend fun deleteByPattern(pattern: String): Int = cacheMutex.withLock {
        val keysToDelete = keysMatchingPattern(pattern, cacheKeysUnlocked())
        keysToDelete.forEach { cache.remove(it) }
        keysToDelete.size
    }

    override suspend fun keys(pattern: String): List<String> =
        cacheMutex.withLock { keysMatchingPattern(pattern, cacheKeysUnlocked()) }

    override suspend fun clear() {
        cacheMutex.withLock { cache.clear() }
    }

    override suspend fun size(): Long = cacheMutex.withLock { cache.size }

    override suspend fun isHealthy(): Boolean = true

    /**
     * Cleanup expired entries.
     * Can be called periodically if needed.
     */
    suspend fun cleanupExpired(): Int = cacheMutex.withLock {
        val now = Clock.System.now()
        var cleaned = 0

        for (key in cacheKeysUnlocked()) {
            val entry = cache.getIfAvailable(key)
            if (entry != null && entry.expiresAt != null && now >= entry.expiresAt) {
                cache.remove(key)
                cleaned++
            }
        }

        cleaned
    }

    private suspend fun cacheKeysUnlocked(): List<String> {
        @Suppress("UNCHECKED_CAST")
        val keys = cache.getKeys() as Iterable<String?>
        return keys.filterNotNull()
    }
}

internal fun keysMatchingPattern(
    pattern: String,
    keys: Iterable<String?>,
): List<String> {
    val regex = patternToRegex(pattern)
    return keys.filterNotNull().filter { regex.matches(it) }
}

/**
 * Convert a glob-style pattern to a regex.
 * Supports * as wildcard.
 */
private fun patternToRegex(pattern: String): Regex {
    val regexPattern =
        buildString {
            append("^")
            for (char in pattern) {
                when (char) {
                    '*' -> append(".*")
                    '.' -> append("\\.")
                    '[' -> append("\\[")
                    ']' -> append("\\]")
                    '(' -> append("\\(")
                    ')' -> append("\\)")
                    '{' -> append("\\{")
                    '}' -> append("\\}")
                    '\\' -> append("\\\\")
                    '^' -> append("\\^")
                    '$' -> append("\\$")
                    '|' -> append("\\|")
                    '?' -> append("\\?")
                    '+' -> append("\\+")
                    else -> append(char)
                }
            }
            append("$")
        }
    return Regex(regexPattern)
}
