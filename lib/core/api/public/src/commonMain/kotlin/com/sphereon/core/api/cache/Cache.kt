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

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Duration

/**
 * Core cache interface for typed key-value storage.
 *
 * Provides basic get/put/remove operations with optional TTL support.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("Cache", exact = true)
interface Cache<K : Any, V : Any> {
    /**
     * Get a value from the cache.
     *
     * @param key The cache key
     * @return The cached value, or null if not found or expired
     */
    suspend fun get(key: K): V?

    /**
     * Put a value in the cache.
     *
     * @param key The cache key
     * @param value The value to cache
     * @param ttl Time-to-live, or null to use default TTL
     */
    suspend fun put(
        key: K,
        value: V,
        ttl: Duration? = null,
    )

    /**
     * Get a value from the cache, or compute and store it if not found.
     *
     * @param key The cache key
     * @param ttl Time-to-live for the computed value
     * @param compute Function to compute the value if not cached
     * @return The cached or computed value
     */
    suspend fun getOrPut(
        key: K,
        ttl: Duration? = null,
        compute: suspend () -> V,
    ): V

    /**
     * Remove a value from the cache.
     *
     * @param key The cache key
     * @return true if the key was removed, false if it didn't exist
     */
    suspend fun remove(key: K): Boolean

    /**
     * Check if a key exists in the cache.
     *
     * @param key The cache key
     * @return true if the key exists and is not expired
     */
    suspend fun contains(key: K): Boolean

    /**
     * Clear all entries from this cache.
     */
    suspend fun clear()

    /**
     * Get the current size (number of entries).
     */
    suspend fun size(): Long

    /**
     * Get cache statistics.
     */
    fun stats(): CacheStatistics
}

/**
 * Cache with batch operations for efficiency.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BatchCache", exact = true)
interface BatchCache<K : Any, V : Any> : Cache<K, V> {
    /**
     * Get multiple values at once.
     *
     * @param keys The cache keys to retrieve
     * @return Map of keys to values (missing keys are omitted)
     */
    suspend fun getMany(keys: Collection<K>): Map<K, V>

    /**
     * Put multiple values at once.
     *
     * @param entries Map of keys to values
     * @param ttl Time-to-live for all entries
     */
    suspend fun putMany(
        entries: Map<K, V>,
        ttl: Duration? = null,
    )

    /**
     * Remove multiple keys at once.
     *
     * @param keys The keys to remove
     * @return Number of keys actually removed
     */
    suspend fun removeMany(keys: Collection<K>): Int
}

/**
 * Cache with scope partitioning for multi-tenancy.
 *
 * All operations are scoped by namespace, tenant, and principal.
 * Provides convenience methods for each scope level.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScopedCache", exact = true)
interface ScopedCache<K : Any, V : Any> : BatchCache<ScopedKey<K>, V> {
    /** The namespace for this cache */
    val namespace: String

    /** The backend ID being used */
    val backendId: String

    // ========== App-scoped operations ==========

    suspend fun getApp(key: K): V?

    suspend fun putApp(
        key: K,
        value: V,
        ttl: Duration? = null,
    )

    suspend fun removeApp(key: K): Boolean

    suspend fun containsApp(key: K): Boolean

    // ========== Tenant-scoped operations ==========

    suspend fun getTenant(
        tenantId: String,
        key: K,
    ): V?

    suspend fun putTenant(
        tenantId: String,
        key: K,
        value: V,
        ttl: Duration? = null,
    )

    suspend fun removeTenant(
        tenantId: String,
        key: K,
    ): Boolean

    suspend fun containsTenant(
        tenantId: String,
        key: K,
    ): Boolean

    // ========== Principal-scoped operations ==========

    suspend fun getPrincipal(
        tenantId: String,
        principalId: String,
        key: K,
    ): V?

    suspend fun putPrincipal(
        tenantId: String,
        principalId: String,
        key: K,
        value: V,
        ttl: Duration? = null,
    )

    suspend fun removePrincipal(
        tenantId: String,
        principalId: String,
        key: K,
    ): Boolean

    suspend fun containsPrincipal(
        tenantId: String,
        principalId: String,
        key: K,
    ): Boolean

    // ========== Bulk invalidation ==========

    /** Invalidate all app-scoped entries */
    suspend fun invalidateApp()

    /** Invalidate all entries for a tenant (tenant and principal scopes) */
    suspend fun invalidateTenant(tenantId: String)

    /** Invalidate all entries for a principal */
    suspend fun invalidatePrincipal(
        tenantId: String,
        principalId: String,
    )

    /** Invalidate entries matching a key pattern (within this namespace) */
    suspend fun invalidateByKeyPattern(pattern: String)
}
