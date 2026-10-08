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
 *
 */

package com.sphereon.core.api.conf

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.HasOrder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Interface for auto-registration of property sources.
 *
 * Modules that provide configuration sources (cloud providers, database providers, etc.)
 * should implement this interface and annotate with `@ContributesIntoSet(...)`
 * to automatically register themselves when on the classpath.
 *
 * Example:
 * ```kotlin
 * @Inject
 * @SingleIn(AppScope::class)
 * @ContributesIntoSet(AppScope::class, binding = binding<PropertySourceContribution>())
 * class MyProviderContribution(
 *     private val provider: MyProvider
 * ) : PropertySourceContribution {
 *     override val configLevel = ConfigLevel.APP
 *     override val providerId = "my-provider"
 *
 *     override fun isEnabled(resolver: PropertyResolver): Boolean {
 *         val disabled = resolver.getProperty("config.providers.my-provider.enabled", Boolean::class)
 *         return disabled != false
 *     }
 *
 *     override fun getPropertySource() = provider
 * }
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertySourceContribution", exact = true)
interface PropertySourceContribution : HasOrder {
    /**
     * The configuration level this provider serves.
     * Determines which ConfigService scope this source is added to.
     */
    val configLevel: ConfigLevel

    /**
     * Unique identifier for this provider.
     * Used in configuration keys like `config.providers.{providerId}.enabled`
     */
    val providerId: String

    /**
     * Check if this provider is enabled.
     *
     * Called during bootstrap to determine if this provider should be registered.
     * Implementations should check configuration properties to allow disabling:
     * - `config.providers.{providerId}.enabled=false` disables the provider
     *
     * @param resolver PropertyResolver to check configuration (env vars, property files)
     * @return true if the provider should be registered, false to skip
     */
    fun isEnabled(resolver: PropertyResolver): Boolean

    /**
     * Get the property source to register.
     *
     * Used by the bootstrap for APP-level contributions. For scoped contributions
     * (TENANT/PRINCIPAL), prefer [createScopedSource] which receives the scope identifiers.
     *
     * @return The PropertySource implementation to add to ConfigService
     */
    fun getPropertySource(): PropertySource<*>

    /**
     * Create a scoped property source for a specific tenant or principal context.
     *
     * Called by the bootstrap for TENANT and PRINCIPAL contributions. Receives the
     * scope identifiers needed to construct the source (e.g., file path, DB filter).
     *
     * Default: delegates to [getPropertySource] for backward compatibility.
     * Contributions that need scope IDs (DB, YAML file-based) should override this.
     *
     * @param tenantId the tenant identifier
     * @param principalId the principal identifier (null for TENANT scope)
     * @return a PropertySource scoped to the given tenant/principal, or null if
     *         this contribution only signals enablement (actual source is DI-injected)
     */
    fun createScopedSource(
        tenantId: String,
        principalId: String? = null,
    ): PropertySource<*>? = getPropertySource()

    /**
     * Whether this provider requires async initialization.
     *
     * If true, [initialize] will be called during bootstrap.
     * Cloud providers typically return true, local providers return false.
     */
    val requiresAsyncInit: Boolean
        get() = false

    /**
     * Initialize the provider asynchronously.
     *
     * Called after registration for providers that need async initialization
     * (e.g., cloud providers that need to refresh from remote).
     *
     * Default implementation does nothing.
     */
    suspend fun initialize() {
        // Default: no async initialization needed
    }
}

/**
 * Configuration keys for provider management.
 */
object PropertySourceContributionConfig {
    /**
     * Base prefix for provider configuration.
     */
    const val CONFIG_PREFIX = "config.providers"

    /**
     * Get the enabled key for a provider.
     * @param providerId The provider identifier
     * @return Configuration key like "config.providers.rest.enabled"
     */
    fun enabledKey(providerId: String): String = "$CONFIG_PREFIX.$providerId.enabled"

    /**
     * Get the priority/order key for a provider.
     * @param providerId The provider identifier
     * @return Configuration key like "config.providers.rest.order"
     */
    fun orderKey(providerId: String): String = "$CONFIG_PREFIX.$providerId.order"
}

/**
 * Fallback no-op contribution so DI graphs without explicit provider contributions still resolve
 * `Set<PropertySourceContribution>`.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<PropertySourceContribution>())
class NoOpPropertySourceContribution : PropertySourceContribution {
    override val configLevel: ConfigLevel = ConfigLevel.APP
    override val providerId: String = "noop"

    override fun isEnabled(resolver: PropertyResolver): Boolean = false

    override fun getPropertySource(): PropertySource<*> = MapPropertySource("noop", emptyMap())

    override fun getOrder(): Int = Int.MAX_VALUE
}
