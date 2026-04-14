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
import com.sphereon.core.compat.JsExportCompat

/**
 * DSL marker for Universal HTTP Config builders.
 */
@DslMarker
annotation class UniversalHttpConfigDsl

/**
 * Builder for [UniversalHttpDefaults].
 */
@JsExportCompat
@UniversalHttpConfigDsl
class UniversalHttpDefaultsBuilder {
    /**
     * Default server prefix (e.g., "/api").
     */
    var serverPrefix: String = ""

    /**
     * Default tenant-in-path mode.
     */
    var tenantPathMode: TenantPathMode = TenantPathMode.OFF

    /**
     * Default tenant segment pattern.
     */
    var tenantSegmentPattern: String = HttpAdapterMount.DEFAULT_TENANT_SEGMENT_PATTERN

    /**
     * Default tenant resolution priority.
     */
    var tenantResolutionPriority: TenantResolutionPriority = TenantResolutionPriority.HEADER_THEN_PATH

    /**
     * Default content types accepted (consumes).
     */
    var defaultConsumes: Set<MediaType> = setOf(MediaType.ApplicationJson)

    /**
     * Default content types produced.
     */
    var defaultProduces: Set<MediaType> = setOf(MediaType.ApplicationJson)

    internal fun build(): UniversalHttpDefaults =
        UniversalHttpDefaults(
            serverPrefix = serverPrefix,
            tenantPathMode = tenantPathMode,
            tenantSegmentPattern = tenantSegmentPattern,
            tenantResolutionPriority = tenantResolutionPriority,
            defaultConsumes = defaultConsumes,
            defaultProduces = defaultProduces,
        )
}

/**
 * Builder for [UniversalHttpAdapterOverride].
 */
@JsExportCompat
@UniversalHttpConfigDsl
class UniversalHttpAdapterOverrideBuilder {
    /**
     * Override the server prefix for this adapter.
     */
    var serverPrefix: String? = null

    /**
     * Override the adapter base path.
     */
    var adapterBasePath: String? = null

    /**
     * Override the tenant-in-path mode for this adapter.
     */
    var tenantPathMode: TenantPathMode? = null

    /**
     * Override the tenant segment pattern for this adapter.
     */
    var tenantSegmentPattern: String? = null

    /**
     * Override the tenant resolution priority for this adapter.
     */
    var tenantResolutionPriority: TenantResolutionPriority? = null

    /**
     * Whether this adapter is enabled.
     */
    var enabled: Boolean = true

    internal fun build(): UniversalHttpAdapterOverride =
        UniversalHttpAdapterOverride(
            serverPrefix = serverPrefix,
            adapterBasePath = adapterBasePath,
            tenantPathMode = tenantPathMode,
            tenantSegmentPattern = tenantSegmentPattern,
            tenantResolutionPriority = tenantResolutionPriority,
            enabled = enabled,
        )
}

/**
 * Builder for [UniversalHttpConfig].
 */
@JsExportCompat
@UniversalHttpConfigDsl
class UniversalHttpConfigBuilder {
    private var defaultsBuilder: UniversalHttpDefaultsBuilder = UniversalHttpDefaultsBuilder()
    private val adapterOverrides: MutableMap<String, UniversalHttpAdapterOverride> = mutableMapOf()

    /**
     * Configure global defaults.
     *
     * ```kotlin
     * universalHttpConfig {
     *     defaults {
     *         serverPrefix = "/api"
     *         tenantPathMode = TenantPathMode.OFF
     *     }
     * }
     * ```
     */
    fun defaults(configure: UniversalHttpDefaultsBuilder.() -> Unit) {
        defaultsBuilder.apply(configure)
    }

    /**
     * Configure a specific adapter by ID.
     *
     * ```kotlin
     * universalHttpConfig {
     *     adapter("KMS_PROVIDERS") {
     *         serverPrefix = "/api/kms"
     *         tenantPathMode = TenantPathMode.BOTH
     *     }
     * }
     * ```
     */
    fun adapter(
        adapterId: String,
        configure: UniversalHttpAdapterOverrideBuilder.() -> Unit,
    ) {
        val builder = UniversalHttpAdapterOverrideBuilder().apply(configure)
        adapterOverrides[adapterId] = builder.build()
    }

    /**
     * Disable an adapter entirely.
     *
     * ```kotlin
     * universalHttpConfig {
     *     disableAdapter("LEGACY_ADAPTER")
     * }
     * ```
     */
    fun disableAdapter(adapterId: String) {
        adapterOverrides[adapterId] = UniversalHttpAdapterOverride(enabled = false)
    }

    internal fun build(): UniversalHttpConfig =
        UniversalHttpConfig(
            defaults = defaultsBuilder.build(),
            overrides = adapterOverrides.toMap(),
        )
}

/**
 * DSL entry point for building a [UniversalHttpConfig].
 *
 * **Example:**
 * ```kotlin
 * val config = universalHttpConfig {
 *     defaults {
 *         serverPrefix = "/api"
 *         tenantPathMode = TenantPathMode.OFF
 *         tenantSegmentPattern = "/t/{tenantId}"
 *     }
 *
 *     adapter("KMS_PROVIDERS") {
 *         serverPrefix = "/api/kms"
 *         tenantPathMode = TenantPathMode.BOTH
 *     }
 *
 *     adapter("KMS_KEYS") {
 *         serverPrefix = "/api/kms"
 *         adapterBasePath = "/keys"
 *     }
 *
 *     disableAdapter("LEGACY_ADAPTER")
 * }
 * ```
 */
fun universalHttpConfig(configure: UniversalHttpConfigBuilder.() -> Unit): UniversalHttpConfig = UniversalHttpConfigBuilder().apply(configure).build()
