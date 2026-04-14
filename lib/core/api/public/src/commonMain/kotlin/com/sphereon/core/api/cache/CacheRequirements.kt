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

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Cache locality preference.
 *
 * Determines which backend(s) should be used for caching.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheLocality", exact = true)
enum class CacheLocality {
    /** Only use local (in-memory) cache */
    LOCAL_ONLY,

    /** Prefer local, fall back to distributed if configured */
    LOCAL_PREFERRED,

    /** Prefer distributed, fall back to local if unavailable */
    DISTRIBUTED_PREFERRED,

    /** Only use distributed cache (fail if unavailable) */
    DISTRIBUTED_ONLY,
}

/**
 * TTL configuration per scope level.
 *
 * Allows different expiration times based on the data's scope.
 * Typically, app-level data can be cached longer than user-specific data.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheTtlConfig", exact = true)
data class CacheTtlConfig(
    /** TTL for app-scoped entries */
    val app: Duration = 10.minutes,
    /** TTL for tenant-scoped entries */
    val tenant: Duration = 5.minutes,
    /** TTL for principal-scoped entries */
    val principal: Duration = 2.minutes,
) {
    /**
     * Get TTL for a specific scope.
     */
    fun forScope(scope: CacheScope): Duration =
        when (scope) {
            CacheScope.APP -> app
            CacheScope.TENANT -> tenant
            CacheScope.PRINCIPAL -> principal
        }

    companion object {
        val DEFAULT = CacheTtlConfig()

        /** Short-lived cache (1m/30s/15s) */
        val SHORT =
            CacheTtlConfig(
                app = 1.minutes,
                tenant = Duration.parse("30s"),
                principal = Duration.parse("15s"),
            )

        /** Long-lived cache (1h/30m/10m) */
        val LONG =
            CacheTtlConfig(
                app = Duration.parse("1h"),
                tenant = Duration.parse("30m"),
                principal = 10.minutes,
            )
    }
}

/**
 * Declares what a module/subsystem needs from caching.
 *
 * Used by CacheManager to select appropriate backend(s) based on:
 * - Locality requirements (local-only, distributed, hybrid)
 * - TTL configuration per scope
 * - Size constraints
 * - Persistence needs
 *
 * Code SHOULD NOT specify locality directly - that's a deployment concern
 * configured via the config/settings system. Instead, code provides defaults
 * that config can override.
 *
 * Example:
 * ```kotlin
 * // Module declares namespace and defaults - config determines actual behavior
 * val requirements = CacheRequirements(
 *     namespace = "did-resolver",
 *     ttlConfig = CacheTtlConfig(app = 30.minutes)
 * )
 * ```
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheRequirements", exact = true)
data class CacheRequirements(
    /** Unique namespace for this cache (e.g., "config", "did-resolver", "oauth-tokens") */
    val namespace: String,
    /** Preferred caching locality - typically set by config, not code */
    val locality: CacheLocality = CacheLocality.LOCAL_ONLY,
    /** Whether to use distributed cache as fallback when local misses */
    val distributedFallback: Boolean = false,
    /** Whether to write-through to distributed cache on puts */
    val writeThrough: Boolean = false,
    /** TTL configuration per scope */
    val ttlConfig: CacheTtlConfig = CacheTtlConfig.DEFAULT,
    /** Maximum entries for local cache sizing */
    val maxLocalEntries: Int = 10_000,
    /** Whether this cache needs to survive restarts (persistent storage) */
    val persistent: Boolean = false,
    /** Tags for filtering/grouping caches */
    val tags: Set<String> = emptySet(),
) {
    init {
        require(namespace.isNotBlank()) { "Cache namespace must not be blank" }
        require(maxLocalEntries > 0) { "maxLocalEntries must be positive" }
    }

    companion object {
        /**
         * Simple local-only cache (fastest, no sharing).
         * Use for data that doesn't need to be shared across instances.
         */
        @JvmStatic
        fun localOnly(namespace: String) =
            CacheRequirements(
                namespace = namespace,
                locality = CacheLocality.LOCAL_ONLY,
            )

        /**
         * Distributed-only cache (shared state required).
         * Use for data that MUST be consistent across instances (e.g., OAuth tokens).
         */
        @JvmStatic
        fun distributedOnly(namespace: String) =
            CacheRequirements(
                namespace = namespace,
                locality = CacheLocality.DISTRIBUTED_ONLY,
            )

        /**
         * Local with distributed fallback (read-through).
         * Local cache is checked first, distributed on miss.
         */
        @JvmStatic
        fun localWithFallback(namespace: String) =
            CacheRequirements(
                namespace = namespace,
                locality = CacheLocality.LOCAL_PREFERRED,
                distributedFallback = true,
            )

        /**
         * Hybrid with write-through (eventual consistency).
         * Writes go to both local and distributed.
         */
        @JvmStatic
        fun hybridWriteThrough(namespace: String) =
            CacheRequirements(
                namespace = namespace,
                locality = CacheLocality.LOCAL_PREFERRED,
                distributedFallback = true,
                writeThrough = true,
            )
    }
}
