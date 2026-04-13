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

import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Low-level cache backend interface.
 *
 * Multiple implementations can coexist in the same deployment:
 * - In-memory (Kache) - always available
 * - REST/distributed - optional, for multi-instance deployments
 * - Redis, Memcached, etc. - pluggable via custom implementations
 *
 * Backends operate on string keys and byte array values.
 * Higher-level abstractions (Cache, ScopedCache) handle serialization.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheBackend", exact = true)
interface CacheBackend {
    /** Unique identifier for this backend (e.g., "kache", "rest", "redis") */
    val id: String

    /** Backend capabilities */
    val capabilities: BackendCapabilities

    /**
     * Get a value from the cache.
     *
     * @param key The cache key
     * @return The cached value as bytes, or null if not found
     */
    suspend fun get(key: String): ByteArray?

    /**
     * Set a value in the cache.
     *
     * @param key The cache key
     * @param value The value to cache as bytes
     * @param ttlMs Time-to-live in milliseconds, or null for no expiration
     */
    suspend fun set(key: String, value: ByteArray, ttlMs: Long? = null)

    /**
     * Delete a value from the cache.
     *
     * @param key The cache key
     * @return true if the key was deleted, false if it didn't exist
     */
    suspend fun delete(key: String): Boolean

    /**
     * Check if a key exists in the cache.
     *
     * @param key The cache key
     * @return true if the key exists and is not expired
     */
    suspend fun exists(key: String): Boolean

    /**
     * Get multiple values at once.
     *
     * @param keys The cache keys to retrieve
     * @return Map of keys to values (missing keys are omitted)
     */
    suspend fun getMany(keys: Collection<String>): Map<String, ByteArray>

    /**
     * Set multiple values at once.
     *
     * @param entries Map of keys to values
     * @param ttlMs Time-to-live in milliseconds, or null for no expiration
     */
    suspend fun setMany(entries: Map<String, ByteArray>, ttlMs: Long? = null)

    /**
     * Delete keys matching a pattern.
     *
     * @param pattern Glob pattern to match (e.g., "config::*::tenant-a::*")
     * @return Number of keys deleted
     */
    suspend fun deleteByPattern(pattern: String): Int

    /**
     * Get keys matching a pattern.
     *
     * @param pattern Glob pattern to match
     * @return List of matching keys
     */
    suspend fun keys(pattern: String): List<String>

    /**
     * Clear all entries from this backend.
     */
    suspend fun clear()

    /**
     * Get the current size (number of entries).
     */
    suspend fun size(): Long

    /**
     * Check if the backend is healthy and available.
     */
    suspend fun isHealthy(): Boolean

    /**
     * Close and release resources.
     */
    suspend fun close() {}
}

/**
 * Backend capabilities descriptor.
 *
 * Used by CacheManager to select appropriate backends based on requirements.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("BackendCapabilities", exact = true)
data class BackendCapabilities(
    /** Whether this is a local (in-process) backend */
    val isLocal: Boolean,
    /** Whether this backend is distributed (shared across instances) */
    val isDistributed: Boolean,
    /** Whether TTL expiration is supported */
    val supportsTtl: Boolean,
    /** Whether pattern-based deletion is supported */
    val supportsPatternDelete: Boolean,
    /** Whether batch operations are supported */
    val supportsBatchOps: Boolean,
    /** Whether data persists across restarts */
    val isPersistent: Boolean
) {
    companion object {
        /** Capabilities for in-memory backends (Kache, etc.) */
        val IN_MEMORY = BackendCapabilities(
            isLocal = true,
            isDistributed = false,
            supportsTtl = true,
            supportsPatternDelete = true,
            supportsBatchOps = true,
            isPersistent = false
        )

        /** Capabilities for distributed backends (REST, Redis, etc.) */
        val DISTRIBUTED = BackendCapabilities(
            isLocal = false,
            isDistributed = true,
            supportsTtl = true,
            supportsPatternDelete = true,
            supportsBatchOps = true,
            isPersistent = false
        )

        /** Capabilities for persistent distributed backends */
        val PERSISTENT_DISTRIBUTED = BackendCapabilities(
            isLocal = false,
            isDistributed = true,
            supportsTtl = true,
            supportsPatternDelete = true,
            supportsBatchOps = true,
            isPersistent = true
        )
    }
}

/**
 * No-op cache backend for testing or when caching is disabled.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("NoOpCacheBackend", exact = true)
object NoOpCacheBackend : CacheBackend {
    override val id: String = "noop"
    override val capabilities = BackendCapabilities(
        isLocal = true,
        isDistributed = false,
        supportsTtl = false,
        supportsPatternDelete = false,
        supportsBatchOps = false,
        isPersistent = false
    )

    override suspend fun get(key: String): ByteArray? = null
    override suspend fun set(key: String, value: ByteArray, ttlMs: Long?) {}
    override suspend fun delete(key: String): Boolean = false
    override suspend fun exists(key: String): Boolean = false
    override suspend fun getMany(keys: Collection<String>): Map<String, ByteArray> = emptyMap()
    override suspend fun setMany(entries: Map<String, ByteArray>, ttlMs: Long?) {}
    override suspend fun deleteByPattern(pattern: String): Int = 0
    override suspend fun keys(pattern: String): List<String> = emptyList()
    override suspend fun clear() {}
    override suspend fun size(): Long = 0
    override suspend fun isHealthy(): Boolean = true
}
