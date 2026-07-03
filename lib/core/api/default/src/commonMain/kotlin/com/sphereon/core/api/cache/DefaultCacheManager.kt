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

import com.sphereon.core.api.log.Log
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Default implementation of CacheManager.
 *
 * Manages cache backends and creates caches based on requirements.
 * Selects appropriate backend(s) based on locality preferences and availability.
 *
 * Note: The CacheManager binding is provided by CacheManagerInitialization
 * which injects and initializes this class with all contributed backends.
 */
@Inject
@SingleIn(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultCacheManager", exact = true)
class DefaultCacheManager : CacheManager {
    private val backends = mutableMapOf<String, CacheBackend>()
    private val caches = mutableMapOf<String, ScopedCache<*, *>>()
    private val registeredRequirements = mutableMapOf<String, CacheRequirements>()
    private val defaultSerializers = mutableMapOf<String, Pair<CacheSerializer<*>, CacheSerializer<*>>>()
    private val log = Log.app().withTag("DefaultCacheManager")

    // ========== Backend Registration ==========

    override fun registerBackend(backend: CacheBackend) {
        backends[backend.id] = backend
    }

    override fun getBackends(): List<CacheBackend> = backends.values.toList()

    override fun getBackend(id: String): CacheBackend? = backends[id]

    override fun hasDistributedBackend(): Boolean = backends.values.any { it.capabilities.isDistributed }

    override fun hasLocalBackend(): Boolean = backends.values.any { it.capabilities.isLocal }

    // ========== Namespace Registration ==========

    override fun registerNamespace(requirements: CacheRequirements) {
        registeredRequirements[requirements.namespace] = requirements
    }

    override fun getRequirements(namespace: String): CacheRequirements? = registeredRequirements[namespace]

    override fun getNamespaces(): Set<String> = registeredRequirements.keys + caches.keys

    // ========== Cache Creation & Lookup ==========

    override fun <K : Any, V : Any> createCache(
        requirements: CacheRequirements,
        keySerializer: CacheSerializer<K>,
        valueSerializer: CacheSerializer<V>,
    ): ScopedCache<K, V> {
        // Register requirements for later lookup
        registeredRequirements[requirements.namespace] = requirements
        defaultSerializers[requirements.namespace] = keySerializer to valueSerializer

        @Suppress("UNCHECKED_CAST")
        return caches.getOrPut(requirements.namespace) {
            val backend = selectBackend(requirements)
            ScopedCacheImpl(
                namespace = requirements.namespace,
                backend = backend,
                keySerializer = keySerializer,
                valueSerializer = valueSerializer,
                ttlConfig = requirements.ttlConfig,
            )
        } as ScopedCache<K, V>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <K : Any, V : Any> getCache(namespace: String): ScopedCache<K, V>? {
        // Return existing cache if available
        caches[namespace]?.let { return it as ScopedCache<K, V> }

        // Lazy create if requirements were registered
        val requirements = registeredRequirements[namespace] ?: return null
        val serializers = defaultSerializers[namespace]

        // If no serializers registered, use string serializers as fallback
        val keySerializer = (serializers?.first ?: CacheSerializers.string) as CacheSerializer<K>
        val valueSerializer = (serializers?.second ?: CacheSerializers.string) as CacheSerializer<V>

        return createCache(requirements, keySerializer, valueSerializer)
    }

    override fun getAllCaches(): List<ScopedCache<*, *>> = caches.values.toList()

    // ========== Backend Selection ==========

    /**
     * Select the appropriate backend based on requirements and availability.
     *
     * Selection logic:
     * - LOCAL_ONLY: Use local backend (fail if none)
     * - DISTRIBUTED_ONLY: Use distributed backend (fail if none)
     * - LOCAL_PREFERRED: Use local, optionally layer with distributed for fallback
     * - DISTRIBUTED_PREFERRED: Use distributed if available, fall back to local
     */
    private fun selectBackend(requirements: CacheRequirements): CacheBackend {
        val localBackend = backends.values.find { it.capabilities.isLocal }
        val distributedBackend = backends.values.find { it.capabilities.isDistributed }

        return when (requirements.locality) {
            CacheLocality.LOCAL_ONLY -> {
                localBackend ?: NoOpCacheBackend.also {
                    // Log warning: no local backend registered
                }
            }

            CacheLocality.DISTRIBUTED_ONLY -> {
                checkNotNull(distributedBackend) {
                    "Cache namespace '${requirements.namespace}' requires distributed backend but none registered"
                }
            }

            CacheLocality.LOCAL_PREFERRED -> {
                val primary = localBackend ?: NoOpCacheBackend
                if (requirements.distributedFallback && distributedBackend != null) {
                    LayeredCacheBackend(
                        primary = primary,
                        secondary = distributedBackend,
                        writeThrough = requirements.writeThrough,
                    )
                } else {
                    primary
                }
            }

            CacheLocality.DISTRIBUTED_PREFERRED -> {
                val primary = distributedBackend
                if (primary != null) {
                    if (localBackend != null && requirements.distributedFallback) {
                        // Local as read-through cache in front of distributed
                        LayeredCacheBackend(
                            primary = localBackend,
                            secondary = primary,
                            writeThrough = requirements.writeThrough,
                        )
                    } else {
                        primary
                    }
                } else {
                    // Fall back to local if no distributed available
                    localBackend ?: NoOpCacheBackend
                }
            }
        }
    }

    // ========== Statistics & Maintenance ==========

    override fun aggregateStats(): Map<String, CacheStatistics> = caches.mapValues { (_, cache) -> cache.stats() }

    override suspend fun invalidateTenant(tenantId: String) {
        val before = retainedEntrySnapshot()
        val failures = mutableListOf<String>()
        caches.values.forEach { cache ->
            @Suppress("UNCHECKED_CAST")
            runCatching {
                (cache as ScopedCache<Any, Any>).invalidateTenant(tenantId)
            }.onFailure { error ->
                val failure = "${cache.namespace.sanitizeCacheLogToken()}:${error.cacheFailureToken()}"
                failures += failure
                log.warn(
                    "VDX_CACHE_MANAGER_TENANT_NAMESPACE_INVALIDATION_FAILED " +
                        "tenant=${tenantId.sanitizeCacheLogToken()} namespace=${cache.namespace.sanitizeCacheLogToken()} " +
                        "backend=${cache.backendId.sanitizeCacheLogToken()} error=${error.cacheFailureToken()}"
                )
            }
        }
        val after = retainedEntrySnapshot()
        log.debug(
            "VDX_CACHE_MANAGER_TENANT_INVALIDATED tenant=${tenantId.sanitizeCacheLogToken()} " +
                "cache.namespaces=${caches.size} entries.before=${before.total} entries.after=${after.total} " +
                "entries.evicted=${(before.total - after.total).coerceAtLeast(0)} " +
                "entries.retained=${after.total} entries.byNamespace=${after.byNamespace.toSizeLogToken()} " +
                "snapshot.failures.before=${before.failures.toFailureLogToken()} " +
                "snapshot.failures.after=${after.failures.toFailureLogToken()} " +
                "invalidation.failures=${failures.toListLogToken()}"
        )
        if (failures.isNotEmpty()) {
            error("Tenant cache invalidation failed for ${failures.joinToString(separator = ",")}")
        }
    }

    override suspend fun invalidatePrincipal(
        tenantId: String,
        principalId: String,
    ) {
        val before = retainedEntrySnapshot()
        val failures = mutableListOf<String>()
        caches.values.forEach { cache ->
            @Suppress("UNCHECKED_CAST")
            runCatching {
                (cache as ScopedCache<Any, Any>).invalidatePrincipal(tenantId, principalId)
            }.onFailure { error ->
                val failure = "${cache.namespace.sanitizeCacheLogToken()}:${error.cacheFailureToken()}"
                failures += failure
                log.warn(
                    "VDX_CACHE_MANAGER_PRINCIPAL_NAMESPACE_INVALIDATION_FAILED " +
                        "tenant=${tenantId.sanitizeCacheLogToken()} principal=${principalId.sanitizeCacheLogToken()} " +
                        "namespace=${cache.namespace.sanitizeCacheLogToken()} backend=${cache.backendId.sanitizeCacheLogToken()} " +
                        "error=${error.cacheFailureToken()}"
                )
            }
        }
        val after = retainedEntrySnapshot()
        log.debug(
            "VDX_CACHE_MANAGER_PRINCIPAL_INVALIDATED tenant=${tenantId.sanitizeCacheLogToken()} " +
                "principal=${principalId.sanitizeCacheLogToken()} cache.namespaces=${caches.size} " +
                "entries.before=${before.total} entries.after=${after.total} " +
                "entries.evicted=${(before.total - after.total).coerceAtLeast(0)} " +
                "entries.retained=${after.total} entries.byNamespace=${after.byNamespace.toSizeLogToken()} " +
                "snapshot.failures.before=${before.failures.toFailureLogToken()} " +
                "snapshot.failures.after=${after.failures.toFailureLogToken()} " +
                "invalidation.failures=${failures.toListLogToken()}"
        )
        if (failures.isNotEmpty()) {
            error("Principal cache invalidation failed for ${failures.joinToString(separator = ",")}")
        }
    }

    override suspend fun clearAll() {
        caches.values.forEach { it.clear() }
    }

    override suspend fun isHealthy(): Boolean = backends.values.all { it.isHealthy() }

    private suspend fun retainedEntrySnapshot(): RetainedEntrySnapshot {
        val byNamespace = mutableMapOf<String, Long>()
        val failures = mutableMapOf<String, String>()
        caches.entries.forEach { (namespace, cache) ->
            runCatching {
                cache.size()
            }.onSuccess { size ->
                byNamespace[namespace] = size
            }.onFailure { error ->
                failures[namespace] = error.cacheFailureToken()
            }
        }
        return RetainedEntrySnapshot(
            total = byNamespace.values.sum(),
            byNamespace = byNamespace.entries.sortedBy { it.key }.associate { it.key to it.value },
            failures = failures,
        )
    }

    private data class RetainedEntrySnapshot(
        val total: Long,
        val byNamespace: Map<String, Long>,
        val failures: Map<String, String>,
    )
}

private fun Map<String, Long>.toSizeLogToken(): String =
    if (isEmpty()) {
        "<empty>"
    } else {
        entries.joinToString(separator = ",") { (namespace, size) ->
            "${namespace.sanitizeCacheLogToken()}:$size"
        }
    }

private fun Map<String, String>.toFailureLogToken(): String =
    if (isEmpty()) {
        "<empty>"
    } else {
        entries.joinToString(separator = ",") { (namespace, error) ->
            "${namespace.sanitizeCacheLogToken()}:${error.sanitizeCacheLogToken()}"
        }
    }

private fun List<String>.toListLogToken(): String =
    if (isEmpty()) {
        "<empty>"
    } else {
        joinToString(separator = ",") { it.sanitizeCacheLogToken() }
    }

private fun Throwable.cacheFailureToken(): String = (message ?: this::class.simpleName ?: "unknown").sanitizeCacheLogToken()

private fun String?.sanitizeCacheLogToken(): String =
    (this?.trim()?.ifBlank { "<blank>" } ?: "<null>")
        .replace(Regex("[^A-Za-z0-9._:@-]"), "_")
        .take(160)
