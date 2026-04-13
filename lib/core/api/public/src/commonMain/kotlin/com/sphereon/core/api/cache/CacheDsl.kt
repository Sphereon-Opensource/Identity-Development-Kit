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
import kotlin.time.Duration.Companion.minutes

/**
 * DSL entry point for creating caches.
 *
 * Code only specifies namespace - all caching behavior is controlled by config.
 *
 * Usage:
 * ```kotlin
 * // Module code - doesn't specify locality (that comes from config)
 * private val cache = cacheManager.cached<String, MyValue>("my-namespace")
 *
 * // Optional: provide defaults that config can override
 * private val cache = cacheManager.cached<String, MyValue>("my-namespace") {
 *     defaultTtl { app = 10.minutes }  // Config can override
 * }
 * ```
 *
 * Config determines backend selection:
 * ```yaml
 * cache:
 *   namespaces:
 *     my-namespace:
 *       locality: LOCAL_PREFERRED
 *       distributed-fallback: true
 *       ttl:
 *         app: 15m
 * ```
 */
fun <K : Any, V : Any> CacheManager.cached(
    namespace: String,
    block: CacheDefaults<K, V>.() -> Unit = {}
): CacheAccessor<K, V> {
    val defaults = CacheDefaults<K, V>(namespace)
    defaults.block()

    // Build requirements from defaults (config loader can override if available)
    val requirements = defaults.toRequirements()
    val cache = createCache(requirements, defaults.keySerializer, defaults.valueSerializer)
    return CacheAccessorImpl(cache)
}

/**
 * Convenience extension for string-keyed caches (most common case).
 */
inline fun <reified V : Any> CacheManager.stringCached(
    namespace: String,
    noinline block: CacheDefaults<String, V>.() -> Unit
): CacheAccessor<String, V> = cached<String, V>(namespace) {
    keySerializer = CacheSerializers.string
    valueSerializer = CacheSerializers.json<V>()
    block()
}

/**
 * Default settings that can be overridden by config.
 *
 * Modules provide sensible defaults; ops can override via config.
 * Note: NO locality methods here - locality is config-driven only.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheDefaults", exact = true)
class CacheDefaults<K : Any, V : Any>(val namespace: String) {
    /** Serializer for cache keys */
    @Suppress("UNCHECKED_CAST")
    var keySerializer: CacheSerializer<K> = CacheSerializers.string as CacheSerializer<K>

    /** Serializer for cache values */
    @Suppress("UNCHECKED_CAST")
    var valueSerializer: CacheSerializer<V> = CacheSerializers.string as CacheSerializer<V>

    // Defaults that config can override
    internal var defaultTtlConfig: CacheTtlConfig = CacheTtlConfig.DEFAULT
    internal var defaultMaxEntries: Int = 10_000
    internal var tags: Set<String> = emptySet()

    // Note: NO locality methods here - locality is config-driven only

    /**
     * Set default TTL if not specified in config.
     *
     * Example:
     * ```kotlin
     * defaultTtl {
     *     app = 10.minutes
     *     tenant = 5.minutes
     *     principal = 2.minutes
     * }
     * ```
     */
    fun defaultTtl(block: TtlBuilder.() -> Unit) {
        val builder = TtlBuilder()
        builder.block()
        defaultTtlConfig = builder.build()
    }

    /**
     * Set maximum entries for the local cache.
     */
    fun maxEntries(max: Int) {
        defaultMaxEntries = max
    }

    /**
     * Add tags for cache categorization.
     */
    fun tags(vararg t: String) {
        tags = t.toSet()
    }

    /**
     * Use JSON serializer for values.
     */
    inline fun <reified T : Any> jsonValue() {
        @Suppress("UNCHECKED_CAST")
        valueSerializer = CacheSerializers.json<T>() as CacheSerializer<V>
    }

    /**
     * Use JSON serializer for keys.
     */
    inline fun <reified T : Any> jsonKey() {
        @Suppress("UNCHECKED_CAST")
        keySerializer = CacheSerializers.json<T>() as CacheSerializer<K>
    }

    /**
     * Build CacheRequirements from defaults.
     * These can be overridden by the config system at runtime.
     */
    internal fun toRequirements() = CacheRequirements(
        namespace = namespace,
        locality = CacheLocality.LOCAL_ONLY, // Default, config can override
        distributedFallback = false,
        writeThrough = false,
        ttlConfig = defaultTtlConfig,
        maxLocalEntries = defaultMaxEntries,
        persistent = false,
        tags = tags
    )
}

/**
 * Builder for TTL configuration.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TtlBuilder", exact = true)
class TtlBuilder {
    var app: Duration = 10.minutes
    var tenant: Duration = 5.minutes
    var principal: Duration = 2.minutes

    fun build() = CacheTtlConfig(app, tenant, principal)
}
