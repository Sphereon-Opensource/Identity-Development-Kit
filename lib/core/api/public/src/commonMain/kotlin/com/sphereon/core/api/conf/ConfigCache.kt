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
 *
 */

package com.sphereon.core.api.conf

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Cache key for scope-aware configuration caching.
 * Uniquely identifies a cached configuration value based on its scope context.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ConfigCacheKey", exact = true)
@CoverageExcludedDataClass
data class ConfigCacheKey(
    val scope: ConfigLevel,
    val tenantId: String?,
    val principalId: String?,
    val sessionId: String?,
    val key: String
) {
    companion object {
        fun app(key: String) = ConfigCacheKey(
            scope = ConfigLevel.APP,
            tenantId = null,
            principalId = null,
            sessionId = null,
            key = key
        )

        fun tenant(tenantId: String, key: String) = ConfigCacheKey(
            scope = ConfigLevel.TENANT,
            tenantId = tenantId,
            principalId = null,
            sessionId = null,
            key = key
        )

        fun principal(tenantId: String, principalId: String, key: String) = ConfigCacheKey(
            scope = ConfigLevel.PRINCIPAL,
            tenantId = tenantId,
            principalId = principalId,
            sessionId = null,
            key = key
        )

        fun fromContext(context: ResolutionContext, key: String) = ConfigCacheKey(
            scope = context.level,
            tenantId = context.tenantId,
            principalId = context.principalId,
            sessionId = context.sessionId,
            key = key
        )
    }

    /**
     * Convert to a string key for use in simple cache implementations.
     */
    fun toStringKey(): String {
        val parts = mutableListOf(scope.name, key)
        tenantId?.let { parts.add(1, "t:$it") }
        principalId?.let { parts.add(2, "p:$it") }
        sessionId?.let { parts.add(3, "s:$it") }
        return parts.joinToString("::")
    }
}

private fun matchesStructuredPrefix(stringKey: String, prefix: String): Boolean {
    val payload = stringKey.substringAfterLast("::", "")
    return payload == prefix || payload.startsWith("$prefix.")
}

private fun selectEvictionKey(
    keys: Set<String>,
    evictionPolicy: EvictionPolicy,
    insertionOrder: Map<String, Long>,
    lastAccessOrder: Map<String, Long>,
    accessFrequency: Map<String, Long>
): String? {
    if (keys.isEmpty()) return null

    return when (evictionPolicy) {
        EvictionPolicy.FIFO -> keys.minWithOrNull(
            compareBy<String>(
                { insertionOrder[it] ?: Long.MAX_VALUE },
                { it }
            )
        )

        EvictionPolicy.LRU -> keys.minWithOrNull(
            compareBy<String>(
                { lastAccessOrder[it] ?: insertionOrder[it] ?: Long.MAX_VALUE },
                { insertionOrder[it] ?: Long.MAX_VALUE },
                { it }
            )
        )

        EvictionPolicy.LFU -> keys.minWithOrNull(
            compareBy<String>(
                { accessFrequency[it] ?: 0L },
                { insertionOrder[it] ?: Long.MAX_VALUE },
                { it }
            )
        )
    }
}

/**
 * Cached configuration value with metadata.
 * Note: value is stored as String for serialization; callers should convert as needed.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CachedConfigValue", exact = true)
@CoverageExcludedDataClass
data class CachedConfigValue(
    val stringValue: String?,
    val metadata: ResolutionMetadata,
    val cachedAt: Instant,
    val expiresAt: Instant?,
    val isNegativeCache: Boolean = false
) {
    /**
     * Get the value, supporting runtime type casting for non-serialized use cases.
     */
    @Transient
    var value: Any? = stringValue
        private set

    /**
     * Create with a non-string value (will be stored as toString() for serialization).
     */
    constructor(
        value: Any?,
        metadata: ResolutionMetadata,
        cachedAt: Instant,
        expiresAt: Instant?,
        isNegativeCache: Boolean = false,
        @Suppress("UNUSED_PARAMETER") preserveType: Boolean = false
    ) : this(
        stringValue = value?.toString(),
        metadata = metadata,
        cachedAt = cachedAt,
        expiresAt = expiresAt,
        isNegativeCache = isNegativeCache
    ) {
        this.value = value
    }

    companion object {
        fun of(
            resolved: ResolvedValue<*>,
            ttl: Duration? = null
        ): CachedConfigValue {
            val now = Clock.System.now()
            return CachedConfigValue(
                value = resolved.value,
                metadata = resolved.metadata,
                cachedAt = now,
                expiresAt = ttl?.let { now + it }
            )
        }

        fun negative(
            key: String,
            scope: ConfigLevel,
            source: String,
            ttl: Duration? = null
        ): CachedConfigValue {
            val now = Clock.System.now()
            return CachedConfigValue(
                value = null,
                metadata = ResolutionMetadata(
                    source = source,
                    scope = scope,
                    originalKey = key,
                    normalizedKey = key,
                    order = 0,
                    isSecret = false,
                    isInterpolated = false,
                    resolvedAt = now,
                    ttl = ttl
                ),
                cachedAt = now,
                expiresAt = ttl?.let { now + it },
                isNegativeCache = true
            )
        }
    }

    /**
     * Check if this cache entry has expired.
     */
    fun isExpired(): Boolean {
        return expiresAt != null && Clock.System.now() > expiresAt
    }

    /**
     * Convert to ResolvedValue if not expired and not negative cache.
     */
    fun <T> toResolvedValue(): ResolvedValue<T>? {
        if (isExpired() || isNegativeCache || value == null) return null
        @Suppress("UNCHECKED_CAST")
        return ResolvedValue(value as T, metadata)
    }
}

/**
 * Scope-aware configuration cache interface.
 * Lives at AppScope but stores values for all scope levels.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScopedConfigCache", exact = true)
interface ScopedConfigCache {
    /**
     * Get a cached configuration value.
     */
    suspend fun get(key: ConfigCacheKey): CachedConfigValue?

    /**
     * Put a configuration value in the cache.
     */
    suspend fun put(key: ConfigCacheKey, value: CachedConfigValue, ttl: Duration)

    /**
     * Get multiple cached values at once.
     */
    suspend fun getMany(keys: List<ConfigCacheKey>): Map<ConfigCacheKey, CachedConfigValue?>

    /**
     * Put multiple values at once.
     */
    suspend fun putMany(entries: Map<ConfigCacheKey, CachedConfigValue>)

    /**
     * Invalidate all cached values for a tenant.
     */
    suspend fun invalidateTenant(tenantId: String)

    /**
     * Invalidate all cached values for a principal.
     */
    suspend fun invalidatePrincipal(tenantId: String, principalId: String)

    /**
     * Invalidate all cached values matching a key prefix.
     */
    suspend fun invalidateByPrefix(prefix: String)

    /**
     * Prefetch configuration values matching a pattern.
     */
    suspend fun prefetch(
        scope: ConfigLevel,
        tenantId: String?,
        principalId: String?,
        pattern: String?
    )

    /**
     * Get cache statistics.
     */
    fun getStats(): CacheStats

    /**
     * Clear all cached values.
     */
    suspend fun clear()
}

/**
 * Cache statistics.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheStats", exact = true)
@CoverageExcludedDataClass
data class CacheStats(
    val hits: Long,
    val misses: Long,
    val evictions: Long,
    val size: Long,
    val maxSize: Long = 0,
    val expired: Long = 0,
    val invalidations: Long = 0
) {
    val hitRate: Double
        get() {
            val total = hits + misses
            return if (total == 0L) 0.0 else hits.toDouble() / total
        }
}

/**
 * Snapshot cache for prefix-based queries.
 * Caches entire prefix query results for efficient bulk reads.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ConfigSnapshotCache", exact = true)
interface ConfigSnapshotCache {
    /**
     * Get a cached snapshot.
     */
    suspend fun getSnapshot(key: SnapshotKey): ConfigSnapshot?

    /**
     * Put a snapshot in the cache.
     */
    suspend fun putSnapshot(key: SnapshotKey, snapshot: ConfigSnapshot)

    /**
     * Invalidate snapshots matching a prefix.
     */
    suspend fun invalidateByPrefix(prefix: String)

    /**
     * Get cache statistics.
     */
    fun getStats(): CacheStats

    /**
     * Clear all snapshots.
     */
    suspend fun clear()
}

/**
 * Key for snapshot cache entries.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("SnapshotKey", exact = true)
@CoverageExcludedDataClass
data class SnapshotKey(
    val scope: ConfigLevel,
    val tenantId: String?,
    val principalId: String?,
    val prefix: String
) {
    fun toStringKey(): String {
        val parts = mutableListOf(scope.name, prefix)
        tenantId?.let { parts.add(1, "t:$it") }
        principalId?.let { parts.add(2, "p:$it") }
        return parts.joinToString("::")
    }
}

/**
 * A cached snapshot of configuration values.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ConfigSnapshot", exact = true)
@CoverageExcludedDataClass
data class ConfigSnapshot(
    val values: Map<String, CachedConfigValue>,
    val createdAt: Instant,
    val expiresAt: Instant?
) {
    fun isExpired(): Boolean {
        return expiresAt != null && Clock.System.now() > expiresAt
    }
}

/**
 * No-op cache implementation for testing or when caching is disabled.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("NoOpConfigCache", exact = true)
object NoOpConfigCache : ScopedConfigCache {
    override suspend fun get(key: ConfigCacheKey): CachedConfigValue? = null
    override suspend fun put(key: ConfigCacheKey, value: CachedConfigValue, ttl: Duration) {}
    override suspend fun getMany(keys: List<ConfigCacheKey>): Map<ConfigCacheKey, CachedConfigValue?> =
        keys.associateWith { null }
    override suspend fun putMany(entries: Map<ConfigCacheKey, CachedConfigValue>) {}
    override suspend fun invalidateTenant(tenantId: String) {}
    override suspend fun invalidatePrincipal(tenantId: String, principalId: String) {}
    override suspend fun invalidateByPrefix(prefix: String) {}
    override suspend fun prefetch(scope: ConfigLevel, tenantId: String?, principalId: String?, pattern: String?) {}
    override fun getStats(): CacheStats = CacheStats(0, 0, 0, 0)
    override suspend fun clear() {}
}

/**
 * No-op snapshot cache implementation.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("NoOpSnapshotCache", exact = true)
object NoOpSnapshotCache : ConfigSnapshotCache {
    override suspend fun getSnapshot(key: SnapshotKey): ConfigSnapshot? = null
    override suspend fun putSnapshot(key: SnapshotKey, snapshot: ConfigSnapshot) {}
    override suspend fun invalidateByPrefix(prefix: String) {}
    override fun getStats(): CacheStats = CacheStats(0, 0, 0, 0)
    override suspend fun clear() {}
}

/**
 * In-memory cache implementation using a simple map with TTL.
 * Suitable for single-instance deployments or testing.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryConfigCache", exact = true)
class InMemoryConfigCache(
    private val maxEntries: Int = 10000,
    private val defaultTtl: Duration = 5.minutes,
    private val evictionPolicy: EvictionPolicy = EvictionPolicy.LRU
) : ScopedConfigCache {

    private val cache = mutableMapOf<String, CachedConfigValue>()
    private val insertionOrder = mutableMapOf<String, Long>()
    private val lastAccessOrder = mutableMapOf<String, Long>()
    private val accessFrequency = mutableMapOf<String, Long>()
    private var orderCounter = 0L
    private var hits = 0L
    private var misses = 0L
    private var evictions = 0L
    private var expired = 0L
    private var invalidations = 0L

    override suspend fun get(key: ConfigCacheKey): CachedConfigValue? {
        val stringKey = key.toStringKey()
        val cached = cache[stringKey]

        if (cached == null) {
            misses++
            return null
        }

        if (cached.isExpired()) {
            removeEntry(stringKey)
            expired++
            misses++
            return null
        }

        recordAccess(stringKey, isNewEntry = false)
        hits++
        return cached
    }

    override suspend fun put(key: ConfigCacheKey, value: CachedConfigValue, ttl: Duration) {
        val stringKey = key.toStringKey()
        val isNewEntry = !cache.containsKey(stringKey)

        // Evict if at capacity
        if (cache.size >= maxEntries && isNewEntry) {
            evictOne()
        }

        cache[stringKey] = if (value.expiresAt == null) {
            value.copy(expiresAt = Clock.System.now() + ttl)
        } else {
            value
        }
        recordAccess(stringKey, isNewEntry)
    }

    override suspend fun getMany(keys: List<ConfigCacheKey>): Map<ConfigCacheKey, CachedConfigValue?> {
        return keys.associateWith { get(it) }
    }

    override suspend fun putMany(entries: Map<ConfigCacheKey, CachedConfigValue>) {
        entries.forEach { (key, value) ->
            put(key, value, defaultTtl)
        }
    }

    override suspend fun invalidateTenant(tenantId: String) {
        val keysToRemove = cache.keys.filter { it.contains("::t:$tenantId::") }
        invalidations += keysToRemove.size
        keysToRemove.forEach { removeEntry(it) }
    }

    override suspend fun invalidatePrincipal(tenantId: String, principalId: String) {
        val keysToRemove = cache.keys.filter {
            it.contains("::t:$tenantId::") && it.contains("::p:$principalId::")
        }
        invalidations += keysToRemove.size
        keysToRemove.forEach { removeEntry(it) }
    }

    override suspend fun invalidateByPrefix(prefix: String) {
        val keysToRemove = cache.keys.filter { matchesStructuredPrefix(it, prefix) }
        invalidations += keysToRemove.size
        keysToRemove.forEach { removeEntry(it) }
    }

    override suspend fun prefetch(
        scope: ConfigLevel,
        tenantId: String?,
        principalId: String?,
        pattern: String?
    ) {
        // In-memory cache doesn't support prefetch - no external data source
    }

    override fun getStats(): CacheStats = CacheStats(
        hits = hits,
        misses = misses,
        evictions = evictions,
        size = cache.size.toLong(),
        maxSize = maxEntries.toLong(),
        expired = expired,
        invalidations = invalidations
    )

    override suspend fun clear() {
        invalidations += cache.size
        cache.clear()
        insertionOrder.clear()
        lastAccessOrder.clear()
        accessFrequency.clear()
    }

    private fun evictOne() {
        val keyToEvict = selectEvictionKey(
            keys = cache.keys,
            evictionPolicy = evictionPolicy,
            insertionOrder = insertionOrder,
            lastAccessOrder = lastAccessOrder,
            accessFrequency = accessFrequency
        )
        if (keyToEvict != null) {
            removeEntry(keyToEvict)
            evictions++
        }
    }

    private fun removeEntry(stringKey: String) {
        cache.remove(stringKey)
        insertionOrder.remove(stringKey)
        lastAccessOrder.remove(stringKey)
        accessFrequency.remove(stringKey)
    }

    private fun recordAccess(stringKey: String, isNewEntry: Boolean) {
        val order = nextOrder()
        if (isNewEntry) {
            insertionOrder[stringKey] = order
        }
        lastAccessOrder[stringKey] = order
        accessFrequency[stringKey] = (accessFrequency[stringKey] ?: 0L) + 1L
    }

    private fun nextOrder(): Long {
        orderCounter += 1
        return orderCounter
    }
}

/**
 * In-memory snapshot cache implementation.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemorySnapshotCache", exact = true)
class InMemorySnapshotCache(
    private val maxEntries: Int = 1000,
    private val defaultTtl: Duration = 30.minutes,
    private val evictionPolicy: EvictionPolicy = EvictionPolicy.LRU
) : ConfigSnapshotCache {

    private val cache = mutableMapOf<String, ConfigSnapshot>()
    private val insertionOrder = mutableMapOf<String, Long>()
    private val lastAccessOrder = mutableMapOf<String, Long>()
    private val accessFrequency = mutableMapOf<String, Long>()
    private var orderCounter = 0L
    private var hits = 0L
    private var misses = 0L
    private var evictions = 0L
    private var expired = 0L
    private var invalidations = 0L

    override suspend fun getSnapshot(key: SnapshotKey): ConfigSnapshot? {
        val stringKey = key.toStringKey()
        val cached = cache[stringKey]

        if (cached == null) {
            misses++
            return null
        }

        if (cached.isExpired()) {
            removeEntry(stringKey)
            expired++
            misses++
            return null
        }

        recordAccess(stringKey, isNewEntry = false)
        hits++
        return cached
    }

    override suspend fun putSnapshot(key: SnapshotKey, snapshot: ConfigSnapshot) {
        val stringKey = key.toStringKey()
        val isNewEntry = !cache.containsKey(stringKey)

        // Evict if at capacity
        if (cache.size >= maxEntries && isNewEntry) {
            evictOne()
        }

        cache[stringKey] = if (snapshot.expiresAt == null) {
            snapshot.copy(expiresAt = Clock.System.now() + defaultTtl)
        } else {
            snapshot
        }
        recordAccess(stringKey, isNewEntry)
    }

    override suspend fun invalidateByPrefix(prefix: String) {
        val keysToRemove = cache.keys.filter { matchesStructuredPrefix(it, prefix) }
        invalidations += keysToRemove.size
        keysToRemove.forEach { removeEntry(it) }
    }

    override fun getStats(): CacheStats = CacheStats(
        hits = hits,
        misses = misses,
        evictions = evictions,
        size = cache.size.toLong(),
        maxSize = maxEntries.toLong(),
        expired = expired,
        invalidations = invalidations
    )

    override suspend fun clear() {
        invalidations += cache.size
        cache.clear()
        insertionOrder.clear()
        lastAccessOrder.clear()
        accessFrequency.clear()
    }

    private fun evictOne() {
        val keyToEvict = selectEvictionKey(
            keys = cache.keys,
            evictionPolicy = evictionPolicy,
            insertionOrder = insertionOrder,
            lastAccessOrder = lastAccessOrder,
            accessFrequency = accessFrequency
        )
        if (keyToEvict != null) {
            removeEntry(keyToEvict)
            evictions++
        }
    }

    private fun removeEntry(stringKey: String) {
        cache.remove(stringKey)
        insertionOrder.remove(stringKey)
        lastAccessOrder.remove(stringKey)
        accessFrequency.remove(stringKey)
    }

    private fun recordAccess(stringKey: String, isNewEntry: Boolean) {
        val order = nextOrder()
        if (isNewEntry) {
            insertionOrder[stringKey] = order
        }
        lastAccessOrder[stringKey] = order
        accessFrequency[stringKey] = (accessFrequency[stringKey] ?: 0L) + 1L
    }

    private fun nextOrder(): Long {
        orderCounter += 1
        return orderCounter
    }
}

/**
 * Synchronous snapshot cache interface for use in non-suspend contexts.
 *
 * This interface is designed for property resolution where blocking or suspend
 * functions cannot be used (e.g., PropertyResolver interface methods).
 * Uses atomicfu for thread-safe access on all platforms including JS.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SyncConfigSnapshotCache", exact = true)
interface SyncConfigSnapshotCache {
    /**
     * Get a cached snapshot synchronously.
     */
    fun getSnapshot(key: SnapshotKey): ConfigSnapshot?

    /**
     * Put a snapshot in the cache synchronously.
     */
    fun putSnapshot(key: SnapshotKey, snapshot: ConfigSnapshot)

    /**
     * Invalidate snapshots matching a prefix.
     */
    fun invalidateByPrefix(prefix: String)

    /**
     * Get cache statistics.
     */
    fun getStats(): CacheStats

    /**
     * Clear all snapshots.
     */
    fun clear()
}

/**
 * No-op synchronous snapshot cache implementation.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("NoOpSyncSnapshotCache", exact = true)
object NoOpSyncSnapshotCache : SyncConfigSnapshotCache {
    override fun getSnapshot(key: SnapshotKey): ConfigSnapshot? = null
    override fun putSnapshot(key: SnapshotKey, snapshot: ConfigSnapshot) {}
    override fun invalidateByPrefix(prefix: String) {}
    override fun getStats(): CacheStats = CacheStats(0, 0, 0, 0)
    override fun clear() {}
}

/**
 * In-memory synchronous snapshot cache implementation using atomicfu.
 *
 * Thread-safe and works on all platforms including JS without blocking.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemorySyncSnapshotCache", exact = true)
class InMemorySyncSnapshotCache(
    private val maxEntries: Int = 1000,
    private val defaultTtl: Duration = 30.minutes,
    private val evictionPolicy: EvictionPolicy = EvictionPolicy.LRU
) : SyncConfigSnapshotCache {

    private val cacheRef: kotlinx.atomicfu.AtomicRef<Map<String, ConfigSnapshot>> =
        kotlinx.atomicfu.atomic(emptyMap())
    private val insertionOrderRef: kotlinx.atomicfu.AtomicRef<Map<String, Long>> =
        kotlinx.atomicfu.atomic(emptyMap())
    private val lastAccessOrderRef: kotlinx.atomicfu.AtomicRef<Map<String, Long>> =
        kotlinx.atomicfu.atomic(emptyMap())
    private val accessFrequencyRef: kotlinx.atomicfu.AtomicRef<Map<String, Long>> =
        kotlinx.atomicfu.atomic(emptyMap())
    private val orderCounterRef: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(0L)
    private val hitsRef: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(0L)
    private val missesRef: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(0L)
    private val evictionsRef: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(0L)
    private val expiredRef: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(0L)
    private val invalidationsRef: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(0L)

    override fun getSnapshot(key: SnapshotKey): ConfigSnapshot? {
        val stringKey = key.toStringKey()
        val currentCache = cacheRef.value
        val cached = currentCache[stringKey]

        if (cached == null) {
            missesRef.value = missesRef.value + 1L
            return null
        }

        if (cached.isExpired()) {
            // Atomically remove expired entry
            removeEntry(stringKey, currentCache)
            expiredRef.value = expiredRef.value + 1L
            missesRef.value = missesRef.value + 1L
            return null
        }

        recordAccess(stringKey, isNewEntry = false)
        hitsRef.value = hitsRef.value + 1L
        return cached
    }

    override fun putSnapshot(key: SnapshotKey, snapshot: ConfigSnapshot) {
        val stringKey = key.toStringKey()
        val snapshotWithTtl = if (snapshot.expiresAt == null) {
            snapshot.copy(expiresAt = Clock.System.now() + defaultTtl)
        } else {
            snapshot
        }
        val isNewEntry = !cacheRef.value.containsKey(stringKey)

        if (cacheRef.value.size >= maxEntries && isNewEntry) {
            evictOne(cacheRef.value)
        }

        val updatedCache = cacheRef.value + (stringKey to snapshotWithTtl)
        cacheRef.value = updatedCache
        recordAccess(stringKey, isNewEntry)
    }

    override fun invalidateByPrefix(prefix: String) {
        val currentCache = cacheRef.value
        val keysToRemove = currentCache.keys.filter {
            matchesStructuredPrefix(it, prefix)
        }.toSet()
        if (keysToRemove.isNotEmpty()) {
            invalidationsRef.value = invalidationsRef.value + keysToRemove.size.toLong()
            var updatedCache = currentCache
            keysToRemove.forEach { keyToRemove ->
                removeTracking(keyToRemove)
                updatedCache = updatedCache - keyToRemove
            }
            cacheRef.value = updatedCache
        }
    }

    override fun getStats(): CacheStats = CacheStats(
        hits = hitsRef.value,
        misses = missesRef.value,
        evictions = evictionsRef.value,
        size = cacheRef.value.size.toLong(),
        maxSize = maxEntries.toLong(),
        expired = expiredRef.value,
        invalidations = invalidationsRef.value
    )

    override fun clear() {
        val currentSize = cacheRef.value.size.toLong()
        invalidationsRef.value = invalidationsRef.value + currentSize
        cacheRef.value = emptyMap()
        insertionOrderRef.value = emptyMap()
        lastAccessOrderRef.value = emptyMap()
        accessFrequencyRef.value = emptyMap()
        orderCounterRef.value = 0L
    }

    private fun evictOne(currentCache: Map<String, ConfigSnapshot>) {
        val keyToEvict = selectEvictionKey(
            keys = currentCache.keys,
            evictionPolicy = evictionPolicy,
            insertionOrder = insertionOrderRef.value,
            lastAccessOrder = lastAccessOrderRef.value,
            accessFrequency = accessFrequencyRef.value
        )
        if (keyToEvict != null) {
            removeEntry(keyToEvict, cacheRef.value)
            evictionsRef.value = evictionsRef.value + 1L
        }
    }

    private fun removeEntry(stringKey: String, currentCache: Map<String, ConfigSnapshot>) {
        removeTracking(stringKey)
        cacheRef.value = currentCache - stringKey
    }

    private fun removeTracking(stringKey: String) {
        insertionOrderRef.value = insertionOrderRef.value - stringKey
        lastAccessOrderRef.value = lastAccessOrderRef.value - stringKey
        accessFrequencyRef.value = accessFrequencyRef.value - stringKey
    }

    private fun recordAccess(stringKey: String, isNewEntry: Boolean) {
        val order = nextOrder()
        if (isNewEntry) {
            insertionOrderRef.value = insertionOrderRef.value + (stringKey to order)
        }
        lastAccessOrderRef.value = lastAccessOrderRef.value + (stringKey to order)
        val nextFrequency = (accessFrequencyRef.value[stringKey] ?: 0L) + 1L
        accessFrequencyRef.value = accessFrequencyRef.value + (stringKey to nextFrequency)
    }

    private fun nextOrder(): Long {
        val next = orderCounterRef.value + 1L
        orderCounterRef.value = next
        return next
    }
}

/**
 * Configuration for caching behavior.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheConfig", exact = true)
@CoverageExcludedDataClass
data class CacheConfig(
    val enabled: Boolean = true,
    val maxEntries: Int = 10000,
    val ttl: TtlConfig = TtlConfig(),
    val evictionPolicy: EvictionPolicy = EvictionPolicy.LRU,
    val snapshotEnabled: Boolean = true,
    val snapshotMaxEntries: Int = 1000,
    val snapshotTtl: Duration = 30.minutes
)

/**
 * TTL configuration per scope level.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("TtlConfig", exact = true)
@CoverageExcludedDataClass
data class TtlConfig(
    val app: Duration = 10.minutes,
    val tenant: Duration = 5.minutes,
    val principal: Duration = 2.minutes
) {
    fun forScope(scope: ConfigLevel): Duration = when (scope) {
        ConfigLevel.APP -> app
        ConfigLevel.TENANT -> tenant
        ConfigLevel.PRINCIPAL -> principal
    }
}

/**
 * Cache eviction policy.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("EvictionPolicy", exact = true)
enum class EvictionPolicy {
    LRU,
    LFU,
    FIFO
}

/**
 * Runtime cache configuration loader for environments where full config services
 * are not yet available during cache wiring.
 *
 * Uses environment variables to avoid circular dependencies between config services
 * and snapshot cache construction.
 */
object CacheConfigRuntimeLoader {
    const val ENV_CACHE_ENABLED = "SPHEREON_CONFIG_CACHE_ENABLED"
    const val ENV_CACHE_MAX_ENTRIES = "SPHEREON_CONFIG_CACHE_MAX_ENTRIES"
    const val ENV_CACHE_EVICTION_POLICY = "SPHEREON_CONFIG_CACHE_EVICTION_POLICY"
    const val ENV_CACHE_SNAPSHOT_ENABLED = "SPHEREON_CONFIG_CACHE_SNAPSHOT_ENABLED"
    const val ENV_CACHE_SNAPSHOT_MAX_ENTRIES = "SPHEREON_CONFIG_CACHE_SNAPSHOT_MAX_ENTRIES"
    const val ENV_CACHE_SNAPSHOT_TTL = "SPHEREON_CONFIG_CACHE_SNAPSHOT_TTL"

    fun loadFromEnvironment(
        getEnv: (String) -> String? = Env::get,
        defaults: CacheConfig = CacheConfig()
    ): CacheConfig {
        val enabled = parseBoolean(getEnv(ENV_CACHE_ENABLED)) ?: defaults.enabled
        val maxEntries = parsePositiveInt(getEnv(ENV_CACHE_MAX_ENTRIES)) ?: defaults.maxEntries
        val evictionPolicy = parseEvictionPolicy(getEnv(ENV_CACHE_EVICTION_POLICY)) ?: defaults.evictionPolicy
        val snapshotEnabled = parseBoolean(getEnv(ENV_CACHE_SNAPSHOT_ENABLED)) ?: defaults.snapshotEnabled
        val snapshotMaxEntries = parsePositiveInt(getEnv(ENV_CACHE_SNAPSHOT_MAX_ENTRIES)) ?: defaults.snapshotMaxEntries
        val snapshotTtl = parseDuration(getEnv(ENV_CACHE_SNAPSHOT_TTL)) ?: defaults.snapshotTtl

        return defaults.copy(
            enabled = enabled,
            maxEntries = maxEntries,
            evictionPolicy = evictionPolicy,
            snapshotEnabled = snapshotEnabled,
            snapshotMaxEntries = snapshotMaxEntries,
            snapshotTtl = snapshotTtl
        )
    }

    private fun parseBoolean(value: String?): Boolean? {
        val normalized = value?.trim()?.lowercase() ?: return null
        return when (normalized) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> null
        }
    }

    private fun parsePositiveInt(value: String?): Int? {
        val parsed = value?.trim()?.toIntOrNull() ?: return null
        return if (parsed > 0) parsed else null
    }

    private fun parseEvictionPolicy(value: String?): EvictionPolicy? {
        val normalized = value?.trim()?.uppercase()?.replace("-", "_") ?: return null
        return when (normalized) {
            "LRU" -> EvictionPolicy.LRU
            "LFU" -> EvictionPolicy.LFU
            "FIFO" -> EvictionPolicy.FIFO
            else -> null
        }
    }

    private fun parseDuration(value: String?): Duration? {
        val trimmed = value?.trim()?.lowercase() ?: return null
        return when {
            trimmed.endsWith("ms") -> trimmed.dropLast(2).toLongOrNull()?.milliseconds
            trimmed.endsWith("s") -> trimmed.dropLast(1).toLongOrNull()?.seconds
            trimmed.endsWith("m") -> trimmed.dropLast(1).toLongOrNull()?.minutes
            trimmed.endsWith("h") -> trimmed.dropLast(1).toLongOrNull()?.hours
            trimmed.endsWith("d") -> trimmed.dropLast(1).toLongOrNull()?.days
            else -> Duration.parseOrNull(trimmed)
        }
    }
}

fun createConfigSnapshotCache(cacheConfig: CacheConfig = CacheConfigRuntimeLoader.loadFromEnvironment()): ConfigSnapshotCache =
    if (cacheConfig.snapshotEnabled) {
        InMemorySnapshotCache(
            maxEntries = cacheConfig.snapshotMaxEntries,
            defaultTtl = cacheConfig.snapshotTtl,
            evictionPolicy = cacheConfig.evictionPolicy
        )
    } else {
        NoOpSnapshotCache
    }

fun createSyncConfigSnapshotCache(cacheConfig: CacheConfig = CacheConfigRuntimeLoader.loadFromEnvironment()): SyncConfigSnapshotCache =
    if (cacheConfig.snapshotEnabled) {
        InMemorySyncSnapshotCache(
            maxEntries = cacheConfig.snapshotMaxEntries,
            defaultTtl = cacheConfig.snapshotTtl,
            evictionPolicy = cacheConfig.evictionPolicy
        )
    } else {
        NoOpSyncSnapshotCache
    }

/**
 * Default DI binding for ConfigSnapshotCache.
 * Lives at AppScope as a singleton but stores values partitioned by tenant/principal.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ConfigSnapshotCache>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultConfigSnapshotCache", exact = true)
class DefaultConfigSnapshotCache : ConfigSnapshotCache by createConfigSnapshotCache()

/**
 * Default DI binding for SyncConfigSnapshotCache.
 * Lives at AppScope as a singleton but stores values partitioned by tenant/principal.
 * This synchronous cache is used by PropertyResolver implementations that cannot use suspend functions.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<SyncConfigSnapshotCache>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultSyncConfigSnapshotCache", exact = true)
class DefaultSyncConfigSnapshotCache : SyncConfigSnapshotCache by createSyncConfigSnapshotCache() {
    /**
     * Component interface for DI access to the sync cache.
     * Useful for tests that need to clear the cache when dynamically adding properties.
     */
    @ContributesTo(AppScope::class)
    interface Component {
        val syncConfigSnapshotCache: SyncConfigSnapshotCache
    }
}

/**
 * Interface for warming up the sync cache from an async source.
 *
 * This interface enables external cache support (Redis, DB) in the config system by providing
 * an async warmup phase that populates the sync cache before it's accessed. Must be called
 * before sync cache access if external cache is configured.
 *
 * Usage pattern:
 * 1. Session creation calls warmupAsync() with known prefixes
 * 2. Sync cache is now populated from external source
 * 3. PropertyResolver can use sync cache without blocking
 *
 * This follows the two-phase initialization pattern already used by PropertySourceBootstrap.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ConfigCacheWarmup", exact = true)
interface ConfigCacheWarmup {
    /**
     * Warms up the sync cache by fetching snapshots from an async source for the given prefixes.
     *
     * @param prefixes Set of config key prefixes to warm up (e.g., "kms.providers", "kms.keystores")
     * @param level The config level to warm up for
     * @param tenantId Optional tenant ID for tenant/principal level warmup
     * @param principalId Optional principal ID for principal level warmup
     */
    suspend fun warmupAsync(
        prefixes: Set<String>,
        level: ConfigLevel,
        tenantId: String?,
        principalId: String?
    )

    /**
     * Checks if the cache has been warmed up for a specific key.
     *
     * @param key The snapshot key to check
     * @return true if the key has been warmed up, false otherwise
     */
    fun isWarmedUp(key: SnapshotKey): Boolean

    /**
     * Checks if the warmup has completed (at least once) for any prefix.
     *
     * @return true if warmup has been called at least once
     */
    fun hasWarmedUp(): Boolean
}

/**
 * Bridges an async [ConfigSnapshotCache] to a [SyncConfigSnapshotCache] via warmup.
 *
 * This adapter enables external cache support by:
 * 1. Providing a sync cache interface for PropertyResolver (which can't use suspend)
 * 2. Allowing async warmup from external sources (Redis, DB) before sync access
 * 3. Tracking which keys have been warmed up
 *
 * Example usage:
 * ```kotlin
 * // Redis cache (async)
 * class RedisConfigSnapshotCache(redis: RedisClient) : ConfigSnapshotCache {
 *     override suspend fun getSnapshot(key: SnapshotKey): ConfigSnapshot? =
 *         redis.get(key.toStringKey())?.let { Json.decodeFromString(it) }
 * }
 *
 * // Bridge for sync access
 * val adapter = AsyncToSyncCacheAdapter(redisCache, InMemorySyncSnapshotCache())
 *
 * // Warmup during session creation
 * adapter.warmupAsync(setOf("kms.providers"), ConfigLevel.PRINCIPAL, tenantId, principalId)
 *
 * // Now sync access works
 * val snapshot = adapter.getSnapshot(key)  // Returns warmed-up data
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AsyncToSyncCacheAdapter", exact = true)
class AsyncToSyncCacheAdapter(
    private val asyncCache: ConfigSnapshotCache,
    private val syncCache: SyncConfigSnapshotCache = InMemorySyncSnapshotCache()
) : SyncConfigSnapshotCache by syncCache, ConfigCacheWarmup {

    private val warmedKeysRef: kotlinx.atomicfu.AtomicRef<Set<String>> = kotlinx.atomicfu.atomic(emptySet())
    private val hasWarmedUpRef: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)

    override suspend fun warmupAsync(
        prefixes: Set<String>,
        level: ConfigLevel,
        tenantId: String?,
        principalId: String?
    ) {
        for (prefix in prefixes) {
            val key = SnapshotKey(level, tenantId, principalId, prefix)
            asyncCache.getSnapshot(key)?.let { snapshot ->
                syncCache.putSnapshot(key, snapshot)
                // Atomically add to warmed keys
                val currentKeys = warmedKeysRef.value
                warmedKeysRef.value = currentKeys + key.toStringKey()
            }
        }
        hasWarmedUpRef.value = true
    }

    override fun isWarmedUp(key: SnapshotKey): Boolean = key.toStringKey() in warmedKeysRef.value

    override fun hasWarmedUp(): Boolean = hasWarmedUpRef.value
}

/**
 * Default DI binding for ConfigCacheWarmup.
 *
 * This default implementation wraps the existing sync cache without an external async source.
 * When no external cache is configured, warmup is a no-op and isWarmedUp always returns true.
 *
 * To enable external cache support, replace this binding with an AsyncToSyncCacheAdapter
 * that wraps your external ConfigSnapshotCache implementation.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ConfigCacheWarmup>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultConfigCacheWarmup", exact = true)
class DefaultConfigCacheWarmup(
    private val syncCache: SyncConfigSnapshotCache
) : ConfigCacheWarmup {

    // Default: no async source, tracks if warmup was called but always considers warmed
    private val hasWarmedUpRef: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)

    override suspend fun warmupAsync(
        prefixes: Set<String>,
        level: ConfigLevel,
        tenantId: String?,
        principalId: String?
    ) {
        // No-op: no external async source in default implementation
        // The sync cache is already directly populated by property resolution
        hasWarmedUpRef.value = true
    }

    override fun isWarmedUp(key: SnapshotKey): Boolean {
        // Default: always "warmed" since we use in-memory sync cache directly
        return true
    }

    // Tracks whether explicit warmup was requested by the caller.
    override fun hasWarmedUp(): Boolean = hasWarmedUpRef.value
}
