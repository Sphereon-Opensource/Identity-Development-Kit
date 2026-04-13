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

package com.sphereon.core.api.http.config

import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.describe.TenantPathMode
import com.sphereon.core.api.http.describe.TenantResolutionPriority

/**
 * Global defaults for Universal HTTP Adapter exposure.
 *
 * These defaults apply to all adapters unless overridden per-adapter.
 */
data class UniversalHttpDefaults(
    /**
     * Default server prefix (e.g., "/api").
     * If empty, adapters mount at root.
     */
    val serverPrefix: String = "",
    /**
     * Default tenant-in-path mode.
     * OFF means tenant is resolved from headers/JWT only.
     */
    val tenantPathMode: TenantPathMode = TenantPathMode.OFF,
    /**
     * Default tenant segment pattern (e.g., "/t/{tenantId}").
     */
    val tenantSegmentPattern: String = HttpAdapterMount.DEFAULT_TENANT_SEGMENT_PATTERN,
    /**
     * Default tenant resolution priority when both header and path provide tenant.
     */
    val tenantResolutionPriority: TenantResolutionPriority = TenantResolutionPriority.HEADER_THEN_PATH,
    /**
     * Default content types this server accepts (consumes).
     */
    val defaultConsumes: Set<MediaType> = setOf(MediaType.ApplicationJson),
    /**
     * Default content types this server produces.
     */
    val defaultProduces: Set<MediaType> = setOf(MediaType.ApplicationJson),
)

/**
 * Per-adapter configuration overrides.
 *
 * Any property set here overrides the corresponding default.
 * Properties left as null inherit from [UniversalHttpDefaults].
 */
data class UniversalHttpAdapterOverride(
    /**
     * Override the server prefix for this adapter.
     */
    val serverPrefix: String? = null,
    /**
     * Override the adapter base path.
     */
    val adapterBasePath: String? = null,
    /**
     * Override the tenant-in-path mode for this adapter.
     */
    val tenantPathMode: TenantPathMode? = null,
    /**
     * Override the tenant segment pattern for this adapter.
     */
    val tenantSegmentPattern: String? = null,
    /**
     * Override the tenant resolution priority for this adapter.
     */
    val tenantResolutionPriority: TenantResolutionPriority? = null,
    /**
     * Whether this adapter is enabled.
     * Disabled adapters are not exposed.
     */
    val enabled: Boolean = true,
)

/**
 * Complete configuration for Universal HTTP Adapter exposure.
 *
 * This model can be:
 * - Built programmatically using the [universalHttpConfig] DSL
 * - Bound from configuration files (Ktor HOCON, Spring application.yml/properties)
 *
 * **Usage (DSL):**
 * ```kotlin
 * val config = universalHttpConfig {
 *     defaults {
 *         serverPrefix = "/api"
 *         tenantPathMode = TenantPathMode.OFF
 *     }
 *     adapter("KMS_PROVIDERS") {
 *         serverPrefix = "/api/kms"
 *         tenantPathMode = TenantPathMode.BOTH
 *     }
 * }
 * ```
 */
data class UniversalHttpConfig(
    /**
     * Global defaults applying to all adapters.
     */
    val defaults: UniversalHttpDefaults = UniversalHttpDefaults(),
    /**
     * Per-adapter overrides, keyed by adapter ID.
     */
    val overrides: Map<String, UniversalHttpAdapterOverride> = emptyMap(),
) {
    /**
     * Resolves the effective mount configuration for an adapter.
     *
     * The resolution order is:
     * 1. Per-adapter override (if present)
     * 2. Global defaults
     * 3. Adapter's own declared mount (passed as parameter)
     *
     * @param adapterId The adapter ID to resolve config for
     * @param declaredMount The adapter's own declared mount configuration
     * @return The effective mount configuration with all overrides applied
     */
    fun resolveMount(
        adapterId: String,
        declaredMount: HttpAdapterMount,
    ): HttpAdapterMount {
        val override = overrides[adapterId]

        return HttpAdapterMount(
            serverPrefix =
                override?.serverPrefix
                    ?: declaredMount.serverPrefix.ifEmpty { defaults.serverPrefix },
            adapterBasePath =
                override?.adapterBasePath
                    ?: declaredMount.adapterBasePath,
            tenantPathMode =
                override?.tenantPathMode
                    ?: if (declaredMount.tenantPathMode != TenantPathMode.OFF) {
                        declaredMount.tenantPathMode
                    } else {
                        defaults.tenantPathMode
                    },
            tenantSegmentPattern =
                override?.tenantSegmentPattern
                    ?: if (declaredMount.tenantSegmentPattern != HttpAdapterMount.DEFAULT_TENANT_SEGMENT_PATTERN) {
                        declaredMount.tenantSegmentPattern
                    } else {
                        defaults.tenantSegmentPattern
                    },
            tenantResolutionPriority =
                override?.tenantResolutionPriority
                    ?: if (declaredMount.tenantResolutionPriority != TenantResolutionPriority.HEADER_THEN_PATH) {
                        declaredMount.tenantResolutionPriority
                    } else {
                        defaults.tenantResolutionPriority
                    },
        )
    }

    /**
     * Checks if an adapter is enabled based on configuration.
     *
     * @param adapterId The adapter ID to check
     * @return true if the adapter is enabled (or no override exists), false otherwise
     */
    fun isAdapterEnabled(adapterId: String): Boolean = overrides[adapterId]?.enabled ?: true

    companion object {
        /**
         * Default configuration with no overrides.
         */
        val DEFAULT = UniversalHttpConfig()
    }
}
