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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Duration

/**
 * High-level cache service for traditional access style.
 *
 * Provides a simpler API than working directly with CacheManager,
 * wrapping operations in IdkResult for consistent error handling.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheService", exact = true)
interface CacheService {
    /**
     * Get or create a scoped cache for a namespace.
     *
     * Caches are always String-keyed and String-valued since the underlying
     * CacheManager operates on string serialization. Use CacheSerializers
     * for custom value types at the CacheManager level.
     */
    fun getCache(requirements: CacheRequirements): ScopedCache<String, String>

    /**
     * Get a value from any registered cache.
     */
    suspend fun get(args: CacheGetArgs): IdkResult<CacheGetResult, IdkError>

    /**
     * Put a value into a cache.
     */
    suspend fun put(args: CachePutArgs): IdkResult<CachePutResult, IdkError>

    /**
     * Remove a value from a cache.
     */
    suspend fun remove(args: CacheRemoveArgs): IdkResult<CacheRemoveResult, IdkError>

    /**
     * Invalidate cache entries.
     */
    suspend fun invalidate(args: CacheInvalidateArgs): IdkResult<CacheInvalidateResult, IdkError>

    /**
     * Invalidate all caches for a tenant.
     */
    suspend fun invalidateTenant(tenantId: String): IdkResult<CacheInvalidateResult, IdkError>

    /**
     * Invalidate all caches for a principal.
     */
    suspend fun invalidatePrincipal(
        tenantId: String,
        principalId: String,
    ): IdkResult<CacheInvalidateResult, IdkError>

    /**
     * Get aggregate statistics.
     */
    fun stats(): Map<String, CacheStatistics>

    /**
     * Check if caches are healthy.
     */
    suspend fun isHealthy(): Boolean
}

/**
 * Default implementation of CacheService.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<CacheService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultCacheService", exact = true)
class DefaultCacheService(
    private val cacheManager: CacheManager,
) : CacheService {
    override fun getCache(requirements: CacheRequirements): ScopedCache<String, String> = cacheManager.createStringCache(requirements, CacheSerializers.string)

    override suspend fun get(args: CacheGetArgs): IdkResult<CacheGetResult, IdkError> {
        val cache =
            cacheManager.getCache<String, String>(args.namespace)
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "Cache namespace: ${args.namespace}"))

        val scopedKey =
            when (args.scope) {
                CacheScope.APP -> ScopedKey.app(args.namespace, args.key)
                CacheScope.TENANT -> ScopedKey.tenant(args.namespace, args.tenantId!!, args.key)
                CacheScope.PRINCIPAL -> ScopedKey.principal(args.namespace, args.tenantId!!, args.principalId!!, args.key)
            }

        val value = cache.get(scopedKey)
        return Ok(
            if (value != null) {
                CacheGetResult.hit(value, cache.backendId)
            } else {
                CacheGetResult.miss(cache.backendId)
            },
        )
    }

    override suspend fun put(args: CachePutArgs): IdkResult<CachePutResult, IdkError> {
        val cache =
            cacheManager.getCache<String, String>(args.namespace)
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "Cache namespace: ${args.namespace}"))

        val scopedKey =
            when (args.scope) {
                CacheScope.APP -> ScopedKey.app(args.namespace, args.key)
                CacheScope.TENANT -> ScopedKey.tenant(args.namespace, args.tenantId!!, args.key)
                CacheScope.PRINCIPAL -> ScopedKey.principal(args.namespace, args.tenantId!!, args.principalId!!, args.key)
            }

        val ttl = args.ttlMs?.let { Duration.parse("${it}ms") }
        cache.put(scopedKey, args.value, ttl)
        return Ok(CachePutResult(stored = true, toBackend = cache.backendId))
    }

    override suspend fun remove(args: CacheRemoveArgs): IdkResult<CacheRemoveResult, IdkError> {
        val cache =
            cacheManager.getCache<String, String>(args.namespace)
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "Cache namespace: ${args.namespace}"))

        val scopedKey =
            when (args.scope) {
                CacheScope.APP -> ScopedKey.app(args.namespace, args.key)
                CacheScope.TENANT -> ScopedKey.tenant(args.namespace, args.tenantId!!, args.key)
                CacheScope.PRINCIPAL -> ScopedKey.principal(args.namespace, args.tenantId!!, args.principalId!!, args.key)
            }

        val removed = cache.remove(scopedKey)
        return Ok(CacheRemoveResult(removed = removed, fromBackend = cache.backendId))
    }

    override suspend fun invalidate(args: CacheInvalidateArgs): IdkResult<CacheInvalidateResult, IdkError> {
        var totalRemoved = 0L
        val affected = mutableListOf<String>()

        val caches =
            if (args.namespace != null) {
                listOfNotNull(cacheManager.getCache<Any, Any>(args.namespace))
            } else {
                cacheManager.getAllCaches()
            }

        for (cache in caches) {
            @Suppress("UNCHECKED_CAST")
            val scopedCache = cache as ScopedCache<Any, Any>
            val beforeSize = scopedCache.size()

            when {
                args.tenantId != null && args.principalId != null -> {
                    scopedCache.invalidatePrincipal(args.tenantId, args.principalId)
                }

                args.tenantId != null -> {
                    scopedCache.invalidateTenant(args.tenantId)
                }

                args.keyPattern != null -> {
                    scopedCache.invalidateByKeyPattern(args.keyPattern)
                }

                else -> {
                    scopedCache.clear()
                }
            }

            val afterSize = scopedCache.size()
            totalRemoved += (beforeSize - afterSize).coerceAtLeast(0)
            affected.add(scopedCache.namespace)
        }

        return Ok(CacheInvalidateResult(totalRemoved, affected))
    }

    override suspend fun invalidateTenant(tenantId: String): IdkResult<CacheInvalidateResult, IdkError> {
        cacheManager.invalidateTenant(tenantId)
        return Ok(
            CacheInvalidateResult(
                entriesRemoved = -1, // Unknown without tracking
                namespacesAffected = cacheManager.getNamespaces().toList(),
            ),
        )
    }

    override suspend fun invalidatePrincipal(
        tenantId: String,
        principalId: String,
    ): IdkResult<CacheInvalidateResult, IdkError> {
        cacheManager.invalidatePrincipal(tenantId, principalId)
        return Ok(
            CacheInvalidateResult(
                entriesRemoved = -1, // Unknown without tracking
                namespacesAffected = cacheManager.getNamespaces().toList(),
            ),
        )
    }

    override fun stats(): Map<String, CacheStatistics> = cacheManager.aggregateStats()

    override suspend fun isHealthy(): Boolean = cacheManager.isHealthy()
}
