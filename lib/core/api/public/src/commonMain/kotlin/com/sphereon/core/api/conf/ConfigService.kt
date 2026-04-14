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
import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.io.files.Path
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.reflect.KClass

/**
 * Interface for application-level configuration.
 * Provides access to application-wide settings and properties.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AppConfigService", exact = true)
interface AppConfigService : ConfigService {
    // future extensions
    override val level: ConfigLevel
        get() = ConfigLevel.APP

    override val parent: ConfigService?
        get() = null

    @SingleIn(AppScope::class)
    @ContributesTo(AppScope::class)
    interface Graph {
        val appConfigService: AppConfigService
    }
}

/**
 * Interface for tenant-level configuration.
 * Provides access to tenant-specific settings and properties.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TenantConfigService", exact = true)
interface TenantConfigService : ConfigService {
    override val level: ConfigLevel
        get() = ConfigLevel.TENANT

    override val parent: AppConfigService

    /* // Allow access to global as well
     val global: AppConfigService
     // future extensions*/

    @SingleIn(UserScope::class)
    @ContributesTo(UserScope::class)
    interface Graph {
        val tenantConfigService: TenantConfigService
    }
}

/**
 * Interface for principal-level configuration.
 * Provides access to principal-specific settings and properties.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PrincipalConfigService", exact = true)
interface PrincipalConfigService : ConfigService {
    override val parent: TenantConfigService

    override val level: ConfigLevel
        get() = ConfigLevel.PRINCIPAL

    /*// Allow access to global and tenant as well
    val global: AppConfigService
    val tenant: TenantConfigService
    // future extensions*/

    @SingleIn(UserScope::class)
    @ContributesTo(UserScope::class)
    interface Graph {
        val principalConfigService: PrincipalConfigService
    }
}

/**
 * Main interface for configuration service operations.
 * Provides methods for retrieving and managing configuration properties.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ConfigService", exact = true)
interface ConfigService : ConfigEnvironment {
    val configLevel: ConfigLevel

    fun addPropertySource(source: PropertySource<*>): ConfigService

    fun removePropertySource(source: PropertySource<*>): ConfigService
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<AppConfigService>())
@ContributesIntoSet(AppScope::class, binding = binding<ConfigService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("AppConfigServiceImpl", exact = true)
class AppConfigServiceImpl(
    environment: AppConfigEnvironment,
) : AbstractConfigService(environment),
    AppConfigService

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<TenantConfigService>())
@ContributesIntoSet(UserScope::class, binding = binding<ConfigService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("TenantConfigServiceImpl", exact = true)
class TenantConfigServiceImpl(
    environment: TenantConfigEnvironment,
    override val parent: AppConfigService,
) : AbstractConfigService(environment),
    TenantConfigService

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<PrincipalConfigService>())
@ContributesIntoSet(UserScope::class, binding = binding<ConfigService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("PrincipalConfigServiceImpl", exact = true)
class PrincipalConfigServiceImpl(
    environment: PrincipalConfigEnvironment,
    override val parent: TenantConfigService,
) : AbstractConfigService(environment),
    PrincipalConfigService

@JsExportCompat
abstract class AbstractConfigService(
    protected val configEnvironment: ConfigEnvironment,
) : ConfigService {
    override val configLevel = configEnvironment.level

    override fun addPropertySource(source: PropertySource<*>): ConfigService = apply { configEnvironment.getPropertySources(includeParents = false).add(source) }

    override fun removePropertySource(source: PropertySource<*>): ConfigService =
        apply {
            configEnvironment.getPropertySources(includeParents = false).remove(source)
        }

    override fun getActiveProfile(): String = configEnvironment.getActiveProfile()

    override fun getAppName(): String = configEnvironment.getAppName()

    override fun getConfigLocation(): Path = configEnvironment.getConfigLocation()

    override fun getPropertySources(includeParents: Boolean) = configEnvironment.getPropertySources(includeParents)

    override fun containsProperty(key: String): Boolean = configEnvironment.containsProperty(key)

    override fun <T : Any> getProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T? = configEnvironment.getProperty(key, targetType, defaultValue)

    override fun getPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String? = configEnvironment.getPropertyAsString(key, defaultValue)

    override fun <T : Any> getRequiredProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T = configEnvironment.getRequiredProperty(key, targetType, defaultValue)

    override fun getRequiredPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String = configEnvironment.getRequiredPropertyAsString(key, defaultValue)

    override fun getAllProperties(): Map<String, Any> = configEnvironment.getAllProperties()

    override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = configEnvironment.getAllPropertiesAsString(redact)

    override fun getSubProperties(
        prefixes: Set<String>,
        stripPrefix: Boolean,
    ): Map<String, Any> = configEnvironment.getSubProperties(prefixes, stripPrefix)

    override fun getSubPropertiesAsString(
        prefixes: Set<String>,
        stripPrefix: Boolean,
        redact: Boolean,
    ): Map<String, String> = configEnvironment.getSubPropertiesAsString(prefixes, stripPrefix, redact)

    override fun getNamespace(): String = configEnvironment.getNamespace()
}

inline fun <reified T : Any> ConfigService.getProperty(
    key: String,
    defaultValue: T?,
) = getProperty(key, T::class, defaultValue)

inline fun <reified T : Any> ConfigService.getRequiredProperty(
    key: String,
    defaultValue: T?,
) = getRequiredProperty(key, T::class, defaultValue)
