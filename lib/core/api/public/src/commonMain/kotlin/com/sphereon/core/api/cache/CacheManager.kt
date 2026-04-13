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

/**
 * Central cache management service.
 *
 * Responsibilities:
 * - Registers available cache backends
 * - Selects backend(s) based on CacheRequirements
 * - Creates caches with proper namespace partitioning
 * - Provides aggregate statistics and bulk invalidation
 *
 * IMPORTANT: Commands access caches via getCache(namespace). For this to work,
 * modules must register their cache requirements during initialization using
 * registerNamespace() or createCache(). The recommended pattern is:
 *
 * 1. Module registers requirements at app startup (via DI module)
 * 2. Commands inject CacheManager and use getCache(namespace)
 * 3. Services can use CacheService for convenience methods
 *
 * Example:
 * ```kotlin
 * // In module initialization
 * cacheManager.registerNamespace(CacheRequirements.localOnly("config"))
 *
 * // In command/service
 * val cache = cacheManager.getCache<String, ConfigValue>("config")
 * val value = cache?.getApp("db.pool.size")
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheManager", exact = true)
interface CacheManager {
    // ========== Backend Registration ==========

    /**
     * Register a cache backend.
     * Multiple backends can be registered (e.g., local + distributed).
     */
    fun registerBackend(backend: CacheBackend)

    /**
     * Get all registered backends.
     */
    fun getBackends(): List<CacheBackend>

    /**
     * Get a specific backend by ID.
     */
    fun getBackend(id: String): CacheBackend?

    /**
     * Check if a distributed backend is available.
     */
    fun hasDistributedBackend(): Boolean

    /**
     * Check if a local backend is available.
     */
    fun hasLocalBackend(): Boolean

    // ========== Namespace Registration ==========

    /**
     * Pre-register cache requirements for a namespace.
     * Useful when modules want to declare requirements at startup
     * but create caches lazily on first access.
     */
    fun registerNamespace(requirements: CacheRequirements)

    /**
     * Get registered requirements for a namespace.
     * Returns null if namespace not registered.
     */
    fun getRequirements(namespace: String): CacheRequirements?

    /**
     * List all registered namespaces.
     */
    fun getNamespaces(): Set<String>

    // ========== Cache Creation & Lookup ==========

    /**
     * Create a cache based on requirements.
     * The cache is registered and can be retrieved via getCache(namespace).
     * If cache already exists for namespace, returns existing cache.
     *
     * @param requirements Cache requirements including namespace
     * @param keySerializer Serializer for cache keys
     * @param valueSerializer Serializer for cache values
     * @return A ScopedCache for the namespace
     */
    fun <K : Any, V : Any> createCache(
        requirements: CacheRequirements,
        keySerializer: CacheSerializer<K>,
        valueSerializer: CacheSerializer<V>,
    ): ScopedCache<K, V>

    /**
     * Convenience method for string-keyed caches.
     */
    fun <V : Any> createStringCache(
        requirements: CacheRequirements,
        valueSerializer: CacheSerializer<V>,
    ): ScopedCache<String, V> = createCache(requirements, CacheSerializers.string, valueSerializer)

    /**
     * Get cache by namespace. Used by Commands.
     * Returns null if namespace not registered.
     * If requirements were registered but cache not yet created, creates it lazily.
     */
    fun <K : Any, V : Any> getCache(namespace: String): ScopedCache<K, V>?

    /**
     * Get all created caches. Used for bulk invalidation.
     */
    fun getAllCaches(): List<ScopedCache<*, *>>

    // ========== Statistics & Maintenance ==========

    /**
     * Get aggregate statistics across all caches.
     * @return Map of namespace to statistics
     */
    fun aggregateStats(): Map<String, CacheStatistics>

    /**
     * Invalidate all caches for a tenant.
     */
    suspend fun invalidateTenant(tenantId: String)

    /**
     * Invalidate all caches for a principal.
     */
    suspend fun invalidatePrincipal(
        tenantId: String,
        principalId: String,
    )

    /**
     * Clear all caches (use with caution in production).
     */
    suspend fun clearAll()

    /**
     * Check if all backends are healthy.
     */
    suspend fun isHealthy(): Boolean
}
