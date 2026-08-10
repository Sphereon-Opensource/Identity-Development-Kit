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

package com.sphereon.core.defaults.conf

import com.sphereon.core.api.conf.CachingPropertySourcesPropertyResolver
import com.sphereon.core.api.conf.ConfigEnvironment
import com.sphereon.core.api.conf.DefaultInterpolationPolicyProvider
import com.sphereon.core.api.conf.DefaultPropertyInterpolator
import com.sphereon.core.api.conf.DefaultPropertySources
import com.sphereon.core.api.conf.Env
import com.sphereon.core.api.conf.InterpolationPolicyProvider
import com.sphereon.core.api.conf.PropertyInterpolator
import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.core.api.conf.PropertyResolverFactory
import com.sphereon.core.api.conf.PropertySources
import com.sphereon.core.api.conf.StaticProtectedEnvPropertySourceObject
import com.sphereon.core.api.conf.SyncConfigSnapshotCache
import com.sphereon.core.api.conf.TtlConfig
import kotlinx.io.files.Path
import kotlin.reflect.KClass

abstract class AbstractConfigEnvironment(
    protected val appId: String = RESERVED_DEFAULT_APP_NAME,
    protected val profile: String = RESERVED_DEFAULT_PROFILE_NAME,
    protected val propertySources: DefaultPropertySources = DefaultPropertySources(mutableListOf(StaticProtectedEnvPropertySourceObject)),
    propertyResolver: PropertyResolver? = null,
    protected val snapshotCache: SyncConfigSnapshotCache? = null,
    protected val tenantId: String? = null,
    protected val principalId: String? = null,
    protected val ttlConfig: TtlConfig = TtlConfig(),
    protected val interpolator: PropertyInterpolator? = null,
    interpolationPolicyProvider: InterpolationPolicyProvider = DefaultInterpolationPolicyProvider(),
) : ConfigEnvironment {
    private val interpolationPolicyProviderValue: InterpolationPolicyProvider = interpolationPolicyProvider
    final override val interpolationPolicyProvider: InterpolationPolicyProvider
        get() = interpolationPolicyProviderValue
    private var _propertyResolver: PropertyResolver? = null
    private var propertySourcesRevision: Long = 0L

    val propertyResolver: PropertyResolver
        get() {
            val currentRevision = structuralRevision()
            if (_propertyResolver == null || propertySourcesRevision != currentRevision) {
                _propertyResolver =
                    if (snapshotCache != null) {
                        CachingPropertySourcesPropertyResolver(
                            propertySources = getPropertySources(true),
                            snapshotCache = snapshotCache,
                            level = level,
                            tenantId = tenantId,
                            principalId = principalId,
                            ttlConfig = ttlConfig,
                            interpolator =
                                interpolator
                                    ?: DefaultPropertyInterpolator(),
                            interpolationPolicyProvider = interpolationPolicyProvider,
                        )
                    } else {
                        createResolver(
                            sources = getPropertySources(true),
                            interpolator = interpolator,
                            resolverLevel = level,
                            interpolationPolicyProvider = interpolationPolicyProvider,
                        )
                    }
                propertySourcesRevision = currentRevision
            }
            return _propertyResolver!!
        }

    override fun getNamespace(): String = "$appId.$profile"

    override fun getActiveProfile(): String = profile

    override fun getAppName(): String = appId

    override fun getConfigLocation(): Path {
        // Try to get from property resolver first (if available without causing recursion)
        // Then fall back to environment variable, then default
        val location =
            Env.get(ENV_CONFIG_LOCATION)
                ?: Env.get(ENV_CONFIG_LOCATION_ALT)
                ?: DEFAULT_CONFIG_LOCATION
        return Path(location)
    }

    override fun getPropertySources(includeParents: Boolean): PropertySources {
        if (!includeParents) {
            return propertySources
        } else {
            return propertySources.copy(parent?.getPropertySources(includeParents))
        }
    }

    private fun structuralRevision(environment: ConfigEnvironment? = this): Long {
        if (environment == null) {
            return 0L
        }
        val localRevision = environment.getPropertySources(includeParents = false).revision
        val parentRevision = structuralRevision(environment.parent)
        return (localRevision * 31L) + parentRevision
    }

    override fun <T : Any> getProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T? = propertyResolver.getProperty(key, targetType, defaultValue)

    override fun getPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String? = propertyResolver.getPropertyAsString(key, defaultValue)

    override fun <T : Any> getRequiredProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T = propertyResolver.getRequiredProperty(key, targetType, defaultValue)

    override fun getRequiredPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String = propertyResolver.getRequiredPropertyAsString(key, defaultValue)

    override fun getAllProperties(): Map<String, Any> = propertyResolver.getAllProperties()

    override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = propertyResolver.getAllPropertiesAsString(redact)

    override fun getSubProperties(
        prefixes: Set<String>,
        stripPrefix: Boolean,
    ): Map<String, Any> = propertyResolver.getSubProperties(prefixes, stripPrefix)

    override fun getSubPropertiesAsString(
        prefixes: Set<String>,
        stripPrefix: Boolean,
        redact: Boolean,
    ): Map<String, String> = propertyResolver.getSubPropertiesAsString(prefixes, stripPrefix, redact)

    override fun containsProperty(key: String): Boolean = propertyResolver.containsProperty(key)

    companion object {
        const val CONFIG_LOCATION_PROP_KEY = "com.sphereon.config.location"
        const val ENV_CONFIG_LOCATION = "SPHEREON_CONFIG_LOCATION"
        const val ENV_CONFIG_LOCATION_ALT = "SPHEREON_CONFIG_DIR"
        const val DEFAULT_CONFIG_LOCATION = "./config"
        const val ACTIVE_PROFILE_PROP_KEY: String = "com.sphereon.profile.active"
        const val DEFAULT_PROFILE_PROP_KEY: String = "com.sphereon.profile.default"

        const val RESERVED_DEFAULT_APP_NAME: String = "sphereon"
        const val RESERVED_DEFAULT_PROFILE_NAME: String = "default"

        /**
         * Create a PropertyResolver from property sources.
         *
         * When [interpolator] is provided, creates an interpolating resolver for:
         * - Property interpolation (${...} placeholders)
         * Provider-backed secret references are not part of regular property interpolation.
         *
         * @param sources The property sources to use
         * @param interpolator Optional interpolator for variable substitution
         * @return A PropertyResolver instance
         */
        fun createResolver(
            sources: PropertySources,
            interpolator: PropertyInterpolator? = null,
            resolverLevel: com.sphereon.core.api.conf.ConfigLevel,
            interpolationPolicyProvider: InterpolationPolicyProvider = DefaultInterpolationPolicyProvider(),
        ): PropertyResolver {
            val effectiveInterpolator =
                interpolator
                    ?: DefaultPropertyInterpolator()

            return PropertyResolverFactory.create(
                propertySources = sources,
                interpolator = effectiveInterpolator,
                resolverLevel = resolverLevel,
                interpolationPolicyProvider = interpolationPolicyProvider,
            )
        }
    }
}
