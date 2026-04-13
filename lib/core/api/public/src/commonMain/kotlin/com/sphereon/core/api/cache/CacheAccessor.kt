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

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Duration

/**
 * Clean accessor interface for cache operations.
 *
 * Provides a developer-friendly API that hides the complexity of
 * scoped keys and backend selection.
 *
 * Example usage:
 * ```kotlin
 * // Get from app scope
 * val config = cache.get("db.pool.size")
 *
 * // Get from tenant scope
 * val tenantConfig = cache.tenant(tenantId).get("feature.enabled")
 *
 * // Get or compute
 * val value = cache.getOrPut("expensive.key") {
 *     computeExpensiveValue()
 * }
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheAccessor", exact = true)
interface CacheAccessor<K : Any, V : Any> {
    /** The namespace for this cache */
    val namespace: String

    // ========== Scope accessors ==========

    /** Access to app-scoped operations */
    val app: ScopeAccessor<K, V>

    /** Access to tenant-scoped operations */
    fun tenant(tenantId: String): ScopeAccessor<K, V>

    /** Access to principal-scoped operations */
    fun principal(tenantId: String, principalId: String): ScopeAccessor<K, V>

    // ========== Convenience methods (default to app scope) ==========

    /** Get from app scope */
    suspend fun get(key: K): V? = app.get(key)

    /** Put to app scope */
    suspend fun put(key: K, value: V, ttl: Duration? = null) = app.put(key, value, ttl)

    /** Remove from app scope */
    suspend fun remove(key: K): Boolean = app.remove(key)

    /** Get or compute for app scope */
    suspend fun getOrPut(key: K, ttl: Duration? = null, compute: suspend () -> V): V =
        app.getOrPut(key, ttl, compute)

    // ========== Bulk operations ==========

    /** Invalidate all entries in this cache */
    suspend fun invalidateAll()

    /** Get cache statistics */
    fun stats(): CacheStatistics
}

/**
 * Scoped accessor for a specific scope level (APP, TENANT, PRINCIPAL).
 *
 * Provides all cache operations within a specific scope context.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScopeAccessor", exact = true)
interface ScopeAccessor<K : Any, V : Any> {
    /** Get a value from the cache */
    suspend fun get(key: K): V?

    /** Put a value in the cache */
    suspend fun put(key: K, value: V, ttl: Duration? = null)

    /** Remove a value from the cache */
    suspend fun remove(key: K): Boolean

    /** Check if a key exists */
    suspend fun contains(key: K): Boolean

    /** Get or compute a value */
    suspend fun getOrPut(key: K, ttl: Duration? = null, compute: suspend () -> V): V

    /** Invalidate all entries in this scope */
    suspend fun invalidate()
}

/**
 * Implementation of CacheAccessor wrapping a ScopedCache.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheAccessorImpl", exact = true)
class CacheAccessorImpl<K : Any, V : Any>(
    private val cache: ScopedCache<K, V>
) : CacheAccessor<K, V> {

    override val namespace: String = cache.namespace

    override val app: ScopeAccessor<K, V> = AppScopeAccessor(cache)

    override fun tenant(tenantId: String): ScopeAccessor<K, V> =
        TenantScopeAccessor(cache, tenantId)

    override fun principal(tenantId: String, principalId: String): ScopeAccessor<K, V> =
        PrincipalScopeAccessor(cache, tenantId, principalId)

    override suspend fun invalidateAll() = cache.clear()

    override fun stats() = cache.stats()
}

/**
 * App-scoped accessor implementation.
 */
private class AppScopeAccessor<K : Any, V : Any>(
    private val cache: ScopedCache<K, V>
) : ScopeAccessor<K, V> {

    override suspend fun get(key: K): V? = cache.getApp(key)

    override suspend fun put(key: K, value: V, ttl: Duration?) = cache.putApp(key, value, ttl)

    override suspend fun remove(key: K): Boolean = cache.removeApp(key)

    override suspend fun contains(key: K): Boolean = cache.containsApp(key)

    override suspend fun getOrPut(key: K, ttl: Duration?, compute: suspend () -> V): V =
        cache.getApp(key) ?: compute().also { cache.putApp(key, it, ttl) }

    override suspend fun invalidate() = cache.invalidateApp()
}

/**
 * Tenant-scoped accessor implementation.
 */
private class TenantScopeAccessor<K : Any, V : Any>(
    private val cache: ScopedCache<K, V>,
    private val tenantId: String
) : ScopeAccessor<K, V> {

    override suspend fun get(key: K): V? = cache.getTenant(tenantId, key)

    override suspend fun put(key: K, value: V, ttl: Duration?) = cache.putTenant(tenantId, key, value, ttl)

    override suspend fun remove(key: K): Boolean = cache.removeTenant(tenantId, key)

    override suspend fun contains(key: K): Boolean = cache.containsTenant(tenantId, key)

    override suspend fun getOrPut(key: K, ttl: Duration?, compute: suspend () -> V): V =
        cache.getTenant(tenantId, key) ?: compute().also { cache.putTenant(tenantId, key, it, ttl) }

    override suspend fun invalidate() = cache.invalidateTenant(tenantId)
}

/**
 * Principal-scoped accessor implementation.
 */
private class PrincipalScopeAccessor<K : Any, V : Any>(
    private val cache: ScopedCache<K, V>,
    private val tenantId: String,
    private val principalId: String
) : ScopeAccessor<K, V> {

    override suspend fun get(key: K): V? = cache.getPrincipal(tenantId, principalId, key)

    override suspend fun put(key: K, value: V, ttl: Duration?) =
        cache.putPrincipal(tenantId, principalId, key, value, ttl)

    override suspend fun remove(key: K): Boolean = cache.removePrincipal(tenantId, principalId, key)

    override suspend fun contains(key: K): Boolean = cache.containsPrincipal(tenantId, principalId, key)

    override suspend fun getOrPut(key: K, ttl: Duration?, compute: suspend () -> V): V =
        cache.getPrincipal(tenantId, principalId, key) ?: compute().also {
            cache.putPrincipal(tenantId, principalId, key, it, ttl)
        }

    override suspend fun invalidate() = cache.invalidatePrincipal(tenantId, principalId)
}
