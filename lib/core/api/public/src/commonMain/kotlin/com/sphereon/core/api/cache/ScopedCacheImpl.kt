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
 * Implementation of ScopedCache that wraps a CacheBackend.
 *
 * Handles:
 * - Key serialization and scoping
 * - Value serialization
 * - TTL management based on scope
 * - Statistics tracking
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScopedCacheImpl", exact = true)
class ScopedCacheImpl<K : Any, V : Any>(
    override val namespace: String,
    private val backend: CacheBackend,
    private val keySerializer: CacheSerializer<K>,
    private val valueSerializer: CacheSerializer<V>,
    private val ttlConfig: CacheTtlConfig = CacheTtlConfig.DEFAULT
) : ScopedCache<K, V> {

    override val backendId: String = backend.id

    // Statistics tracking
    private var hits = 0L
    private var misses = 0L
    private var evictions = 0L

    // ========== Core Cache operations ==========

    override suspend fun get(key: ScopedKey<K>): V? {
        val stringKey = key.toStringKey(keySerializer)
        val bytes = backend.get(stringKey)
        return if (bytes != null) {
            hits++
            valueSerializer.deserialize(bytes)
        } else {
            misses++
            null
        }
    }

    override suspend fun put(key: ScopedKey<K>, value: V, ttl: Duration?) {
        val stringKey = key.toStringKey(keySerializer)
        val bytes = valueSerializer.serialize(value)
        val ttlMs = (ttl ?: ttlConfig.forScope(key.scope)).inWholeMilliseconds
        backend.set(stringKey, bytes, ttlMs)
    }

    override suspend fun getOrPut(key: ScopedKey<K>, ttl: Duration?, compute: suspend () -> V): V {
        get(key)?.let { return it }
        val value = compute()
        put(key, value, ttl)
        return value
    }

    override suspend fun remove(key: ScopedKey<K>): Boolean {
        val stringKey = key.toStringKey(keySerializer)
        return backend.delete(stringKey)
    }

    override suspend fun contains(key: ScopedKey<K>): Boolean {
        val stringKey = key.toStringKey(keySerializer)
        return backend.exists(stringKey)
    }

    override suspend fun clear() {
        val pattern = "$namespace::*"
        evictions += backend.deleteByPattern(pattern)
    }

    override suspend fun size(): Long {
        val pattern = "$namespace::*"
        return backend.keys(pattern).size.toLong()
    }

    override fun stats(): CacheStatistics = CacheStatistics(
        hits = hits,
        misses = misses,
        evictions = evictions,
        size = 0, // Would require async call
        maxSize = 0
    )

    // ========== Batch operations ==========

    override suspend fun getMany(keys: Collection<ScopedKey<K>>): Map<ScopedKey<K>, V> {
        if (keys.isEmpty()) return emptyMap()

        val keyMap = keys.associateBy { it.toStringKey(keySerializer) }
        val bytesMap = backend.getMany(keyMap.keys)

        return bytesMap.mapNotNull { (stringKey, bytes) ->
            val scopedKey = keyMap[stringKey] ?: return@mapNotNull null
            scopedKey to valueSerializer.deserialize(bytes)
        }.toMap()
    }

    override suspend fun putMany(entries: Map<ScopedKey<K>, V>, ttl: Duration?) {
        if (entries.isEmpty()) return

        val bytesMap = entries.map { (key, value) ->
            key.toStringKey(keySerializer) to valueSerializer.serialize(value)
        }.toMap()

        // Use the TTL for the first key's scope (all entries in a batch should have same scope)
        val effectiveTtl = ttl ?: entries.keys.firstOrNull()?.let { ttlConfig.forScope(it.scope) }
        backend.setMany(bytesMap, effectiveTtl?.inWholeMilliseconds)
    }

    override suspend fun removeMany(keys: Collection<ScopedKey<K>>): Int {
        if (keys.isEmpty()) return 0

        var removed = 0
        keys.forEach { key ->
            if (remove(key)) removed++
        }
        return removed
    }

    // ========== App-scoped operations ==========

    override suspend fun getApp(key: K): V? = get(ScopedKey.app(namespace, key))

    override suspend fun putApp(key: K, value: V, ttl: Duration?) =
        put(ScopedKey.app(namespace, key), value, ttl)

    override suspend fun removeApp(key: K): Boolean = remove(ScopedKey.app(namespace, key))

    override suspend fun containsApp(key: K): Boolean = contains(ScopedKey.app(namespace, key))

    // ========== Tenant-scoped operations ==========

    override suspend fun getTenant(tenantId: String, key: K): V? =
        get(ScopedKey.tenant(namespace, tenantId, key))

    override suspend fun putTenant(tenantId: String, key: K, value: V, ttl: Duration?) =
        put(ScopedKey.tenant(namespace, tenantId, key), value, ttl)

    override suspend fun removeTenant(tenantId: String, key: K): Boolean =
        remove(ScopedKey.tenant(namespace, tenantId, key))

    override suspend fun containsTenant(tenantId: String, key: K): Boolean =
        contains(ScopedKey.tenant(namespace, tenantId, key))

    // ========== Principal-scoped operations ==========

    override suspend fun getPrincipal(tenantId: String, principalId: String, key: K): V? =
        get(ScopedKey.principal(namespace, tenantId, principalId, key))

    override suspend fun putPrincipal(tenantId: String, principalId: String, key: K, value: V, ttl: Duration?) =
        put(ScopedKey.principal(namespace, tenantId, principalId, key), value, ttl)

    override suspend fun removePrincipal(tenantId: String, principalId: String, key: K): Boolean =
        remove(ScopedKey.principal(namespace, tenantId, principalId, key))

    override suspend fun containsPrincipal(tenantId: String, principalId: String, key: K): Boolean =
        contains(ScopedKey.principal(namespace, tenantId, principalId, key))

    // ========== Bulk invalidation ==========

    override suspend fun invalidateApp() {
        val pattern = "$namespace::${CacheScope.APP.name}::*"
        evictions += backend.deleteByPattern(pattern)
    }

    override suspend fun invalidateTenant(tenantId: String) {
        // Invalidate both TENANT and PRINCIPAL scoped entries for this tenant
        val tenantPattern = "$namespace::${CacheScope.TENANT.name}::$tenantId::*"
        val principalPattern = "$namespace::${CacheScope.PRINCIPAL.name}::$tenantId::*"
        evictions += backend.deleteByPattern(tenantPattern)
        evictions += backend.deleteByPattern(principalPattern)
    }

    override suspend fun invalidatePrincipal(tenantId: String, principalId: String) {
        val pattern = "$namespace::${CacheScope.PRINCIPAL.name}::$tenantId::$principalId::*"
        evictions += backend.deleteByPattern(pattern)
    }

    override suspend fun invalidateByKeyPattern(pattern: String) {
        val fullPattern = "$namespace::*::*::*::$pattern"
        evictions += backend.deleteByPattern(fullPattern)
    }
}
