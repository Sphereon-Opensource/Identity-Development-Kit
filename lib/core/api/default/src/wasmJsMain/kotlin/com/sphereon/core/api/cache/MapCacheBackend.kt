/*
 * Copyright 2025 Sphereon International B.V.
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

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn

/**
 * Simple Map-based in-memory cache backend for wasmJs.
 *
 * Uses a plain MutableMap since Kache doesn't support wasmJs yet.
 * Provides the same CacheBackend contract with LRU-style eviction
 * and manual TTL expiration.
 */
@Inject
@SingleIn(AppScope::class)
class MapCacheBackend(
    private val maxSize: Long = DEFAULT_MAX_SIZE
) : CacheBackend {

    companion object {
        const val DEFAULT_MAX_SIZE: Long = 10_000
    }

    override val id: String = "map"

    override val capabilities = BackendCapabilities.IN_MEMORY

    private data class CacheEntry(
        val value: ByteArray,
        val expiresAt: Instant?
    ) {
        fun isExpired(): Boolean =
            expiresAt != null && Clock.System.now() >= expiresAt

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

    private val cache = mutableMapOf<String, CacheEntry>()

    override suspend fun get(key: String): ByteArray? {
        val entry = cache[key] ?: return null
        if (entry.isExpired()) {
            cache.remove(key)
            return null
        }
        return entry.value
    }

    override suspend fun set(key: String, value: ByteArray, ttlMs: Long?) {
        val expiresAt = ttlMs?.let { Clock.System.now().plus(kotlin.time.Duration.parse("${it}ms")) }
        cache[key] = CacheEntry(value, expiresAt)
        evictIfNeeded()
    }

    override suspend fun delete(key: String): Boolean {
        return cache.remove(key) != null
    }

    override suspend fun exists(key: String): Boolean {
        val entry = cache[key] ?: return false
        if (entry.isExpired()) {
            cache.remove(key)
            return false
        }
        return true
    }

    override suspend fun getMany(keys: Collection<String>): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        for (key in keys) {
            get(key)?.let { result[key] = it }
        }
        return result
    }

    override suspend fun setMany(entries: Map<String, ByteArray>, ttlMs: Long?) {
        entries.forEach { (key, value) ->
            set(key, value, ttlMs)
        }
    }

    override suspend fun deleteByPattern(pattern: String): Int {
        val regex = patternToRegex(pattern)
        val keysToDelete = cache.keys.filter { regex.matches(it) }
        keysToDelete.forEach { cache.remove(it) }
        return keysToDelete.size
    }

    override suspend fun keys(pattern: String): List<String> {
        val regex = patternToRegex(pattern)
        return cache.keys.filter { regex.matches(it) }
    }

    override suspend fun clear() {
        cache.clear()
    }

    override suspend fun size(): Long = cache.size.toLong()

    override suspend fun isHealthy(): Boolean = true

    private fun evictIfNeeded() {
        while (cache.size > maxSize) {
            val oldest = cache.keys.firstOrNull() ?: break
            cache.remove(oldest)
        }
    }

    private fun patternToRegex(pattern: String): Regex {
        val regexPattern = buildString {
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
}
