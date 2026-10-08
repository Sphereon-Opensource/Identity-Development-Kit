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

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.compat.JsExportCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Interface for loading cache requirements from the config system.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheConfigLoader", exact = true)
interface CacheConfigLoader {
    /**
     * Load requirements for a namespace from config.
     * Falls back to global defaults, then to code-provided defaults.
     *
     * @param namespace The cache namespace
     * @param defaults Code-provided defaults from CacheDefaults
     * @return CacheRequirements with config values applied
     */
    fun loadRequirements(
        namespace: String,
        defaults: CacheDefaults<*, *>,
    ): CacheRequirements
}

/**
 * Default implementation of CacheConfigLoader.
 *
 * Loads cache configuration from the config system using these keys:
 * - cache.defaults.* - Global defaults for all caches
 * - cache.namespaces.{namespace}.* - Per-namespace overrides
 *
 * Example configuration:
 * ```yaml
 * cache:
 *   defaults:
 *     locality: LOCAL_PREFERRED
 *     distributed-fallback: true
 *     ttl:
 *       app: 10m
 *       tenant: 5m
 *       principal: 2m
 *
 *   namespaces:
 *     config:
 *       ttl:
 *         app: 30m
 *     did-resolver:
 *       locality: LOCAL_PREFERRED
 *       distributed-fallback: true
 *       ttl:
 *         app: 1h
 *     oauth-tokens:
 *       locality: DISTRIBUTED_ONLY
 *       ttl:
 *         principal: 15m
 * ```
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<CacheConfigLoader>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultCacheConfigLoader", exact = true)
class DefaultCacheConfigLoader(
    private val configService: AppConfigService,
) : CacheConfigLoader {
    override fun loadRequirements(
        namespace: String,
        defaults: CacheDefaults<*, *>,
    ): CacheRequirements {
        val nsPrefix = "$CONFIG_PREFIX.$namespace"
        val globalPrefix = GLOBAL_PREFIX

        // Locality - config can override default LOCAL_ONLY
        val locality =
            getProperty("$nsPrefix.locality")
                ?.let { parseLocality(it) }
                ?: getProperty("$globalPrefix.locality")
                    ?.let { parseLocality(it) }
                ?: CacheLocality.LOCAL_ONLY

        val distributedFallback =
            getBooleanProperty("$nsPrefix.distributed-fallback")
                ?: getBooleanProperty("$globalPrefix.distributed-fallback")
                ?: false

        val writeThrough =
            getBooleanProperty("$nsPrefix.write-through")
                ?: getBooleanProperty("$globalPrefix.write-through")
                ?: false

        // TTL - config overrides code defaults
        val ttlConfig = loadTtlConfig(nsPrefix, globalPrefix, defaults.defaultTtlConfig)

        // Max entries - config overrides code defaults
        val maxEntries =
            getIntProperty("$nsPrefix.max-entries")
                ?: getIntProperty("$globalPrefix.max-entries")
                ?: defaults.defaultMaxEntries

        val persistent =
            getBooleanProperty("$nsPrefix.persistent")
                ?: getBooleanProperty("$globalPrefix.persistent")
                ?: false

        return CacheRequirements(
            namespace = namespace,
            locality = locality,
            distributedFallback = distributedFallback,
            writeThrough = writeThrough,
            ttlConfig = ttlConfig,
            maxLocalEntries = maxEntries,
            persistent = persistent,
            tags = defaults.tags,
        )
    }

    private fun parseLocality(value: String): CacheLocality =
        when (value.uppercase().replace("-", "_")) {
            "LOCAL_ONLY" -> CacheLocality.LOCAL_ONLY
            "LOCAL_PREFERRED" -> CacheLocality.LOCAL_PREFERRED
            "DISTRIBUTED_PREFERRED" -> CacheLocality.DISTRIBUTED_PREFERRED
            "DISTRIBUTED_ONLY" -> CacheLocality.DISTRIBUTED_ONLY
            else -> CacheLocality.LOCAL_ONLY
        }

    private fun loadTtlConfig(
        nsPrefix: String,
        globalPrefix: String,
        defaults: CacheTtlConfig,
    ): CacheTtlConfig =
        CacheTtlConfig(
            app =
                parseDuration("$nsPrefix.ttl.app")
                    ?: parseDuration("$globalPrefix.ttl.app")
                    ?: defaults.app,
            tenant =
                parseDuration("$nsPrefix.ttl.tenant")
                    ?: parseDuration("$globalPrefix.ttl.tenant")
                    ?: defaults.tenant,
            principal =
                parseDuration("$nsPrefix.ttl.principal")
                    ?: parseDuration("$globalPrefix.ttl.principal")
                    ?: defaults.principal,
        )

    private fun getProperty(key: String): String? {
        // Use the config service to get the property value
        return try {
            configService.getPropertyAsString(key)
        } catch (_: Exception) {
            null
        }
    }

    private fun getBooleanProperty(key: String): Boolean? {
        val value = getProperty(key) ?: return null
        return value.lowercase() in listOf("true", "1", "yes", "on")
    }

    private fun getIntProperty(key: String): Int? {
        val value = getProperty(key) ?: return null
        return value.toIntOrNull()
    }

    private fun parseDuration(key: String): Duration? {
        val value = getProperty(key) ?: return null
        return parseDurationString(value)
    }

    private fun parseDurationString(value: String): Duration? {
        return try {
            // Parse formats like "10m", "1h", "30s", "5m30s"
            val trimmed = value.trim().lowercase()

            when {
                trimmed.endsWith("ms") -> {
                    val num = trimmed.dropLast(2).toLongOrNull() ?: return null
                    Duration.parse("${num}ms")
                }

                trimmed.endsWith("s") && !trimmed.endsWith("ms") -> {
                    val num = trimmed.dropLast(1).toLongOrNull() ?: return null
                    Duration.parse("${num}s")
                }

                trimmed.endsWith("m") && !trimmed.endsWith("ms") -> {
                    val num = trimmed.dropLast(1).toLongOrNull() ?: return null
                    num.minutes
                }

                trimmed.endsWith("h") -> {
                    val num = trimmed.dropLast(1).toLongOrNull() ?: return null
                    Duration.parse("${num}h")
                }

                trimmed.endsWith("d") -> {
                    val num = trimmed.dropLast(1).toLongOrNull() ?: return null
                    Duration.parse("${num}d")
                }

                else -> {
                    // Try ISO-8601 duration format
                    Duration.parseOrNull(value)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val CONFIG_PREFIX = "cache.namespaces"
        const val GLOBAL_PREFIX = "cache.defaults"
    }
}

/**
 * Extension for CacheManager that uses CacheConfigLoader.
 *
 * Creates a cache with requirements loaded from config.
 */
fun <K : Any, V : Any> CacheManager.cachedWithConfig(
    namespace: String,
    configLoader: CacheConfigLoader,
    block: CacheDefaults<K, V>.() -> Unit = {},
): ScopedCache<K, V> {
    val defaults = CacheDefaults<K, V>(namespace)
    defaults.block()

    // Load requirements from config, falling back to defaults
    val requirements = configLoader.loadRequirements(namespace, defaults)
    return createCache(requirements, defaults.keySerializer, defaults.valueSerializer)
}
