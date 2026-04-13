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

import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.AppConsoleLogServiceImpl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Service for bootstrapping property sources.
 *
 * Collects all [PropertySourceContribution] instances registered via multibinding
 * and adds enabled providers to the appropriate ConfigService.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertySourceBootstrap", exact = true)
interface PropertySourceBootstrap {
    /**
     * Register all enabled APP-level property sources.
     *
     * This is called during application startup to wire cloud providers,
     * database providers, etc. into the ConfigService.
     *
     * Sources are filtered by:
     * 1. configLevel == APP
     * 2. isEnabled() returns true
     *
     * Then sorted by order and added to the AppConfigService.
     */
    fun registerAppSources()

    /**
     * Initialize all async providers.
     *
     * Called after registration for providers that need async initialization
     * (e.g., cloud providers that need to fetch from remote).
     *
     * This runs initialization in parallel for better startup performance.
     */
    suspend fun initializeAsync()

    /**
     * Register tenant-level sources for a specific tenant context.
     *
     * @param tenantConfigService The tenant's ConfigService
     * @param tenantId The tenant identifier
     */
    fun registerTenantSources(tenantConfigService: ConfigService, tenantId: String)

    /**
     * Register principal-level sources for a specific principal context.
     *
     * @param principalConfigService The principal's ConfigService
     * @param tenantId The tenant identifier
     * @param principalId The principal identifier
     */
    fun registerPrincipalSources(principalConfigService: ConfigService, tenantId: String, principalId: String)

    /**
     * Get all registered contributions (for debugging/introspection).
     */
    val contributions: Set<PropertySourceContribution>

    /**
     * Get the count of registered APP-level sources.
     */
    val registeredAppSourceCount: Int

    @ContributesTo(AppScope::class)
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Component", exact = true)
    interface Component {
        val propertySourceBootstrap: PropertySourceBootstrap
    }
}

/**
 * Default implementation of PropertySourceBootstrap.
 *
 * Uses kotlin-inject multibinding to collect all PropertySourceContribution instances.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertySourceBootstrapImpl", exact = true)
class PropertySourceBootstrapImpl(
    private val appConfigService: AppConfigService,
    override val contributions: Set<PropertySourceContribution> = emptySet(),
    private val logService: LogService = AppConsoleLogServiceImpl()
) : PropertySourceBootstrap {

    private var _registeredAppSourceCount = 0
    private val registeredSources = mutableSetOf<String>()
    private val registeredTenantSources = mutableSetOf<String>()
    private val registeredPrincipalSources = mutableSetOf<String>()

    override val registeredAppSourceCount: Int
        get() = _registeredAppSourceCount

    private fun orderedContributions(level: ConfigLevel): List<PropertySourceContribution> =
        contributions
            .filter { it.configLevel == level }
            .sortedWith(
                compareBy<PropertySourceContribution> { it.getOrder() }
                    .thenBy { it.providerId }
            )

    override fun registerAppSources() {
        val appContributions = orderedContributions(ConfigLevel.APP)

        logService.debug("Found ${appContributions.size} APP-level property source contributions")

        for (contribution in appContributions) {
            if (contribution.providerId in registeredSources) {
                continue
            }
            try {
                if (contribution.isEnabled(appConfigService)) {
                    val source = contribution.getPropertySource().let {
                        if (it is ScopedPropertySource<*>) it else ScopedPropertySourceWrapper(it, contribution.configLevel)
                    }
                    appConfigService.addPropertySource(source)
                    registeredSources.add(contribution.providerId)
                    _registeredAppSourceCount = registeredSources.size
                    logService.info("Registered property source: ${contribution.providerId} (order=${contribution.getOrder()})")
                } else {
                    logService.debug("Property source disabled: ${contribution.providerId}")
                }
            } catch (e: Exception) {
                logService.error("Failed to register property source: ${contribution.providerId}", e)
            }
        }

        logService.info("Property source bootstrap complete: $_registeredAppSourceCount APP sources registered")
    }

    override suspend fun initializeAsync() {
        val asyncContributions = orderedContributions(ConfigLevel.APP)
            .filter { it.requiresAsyncInit }
            .filter { it.providerId in registeredSources }

        if (asyncContributions.isEmpty()) {
            logService.debug("No async initialization required")
            return
        }

        logService.info("Initializing ${asyncContributions.size} async property sources...")

        coroutineScope {
            asyncContributions.map { contribution ->
                async {
                    try {
                        logService.debug("Initializing: ${contribution.providerId}")
                        contribution.initialize()
                        logService.info("Initialized: ${contribution.providerId}")
                    } catch (e: Exception) {
                        logService.error("Failed to initialize: ${contribution.providerId}", e)
                    }
                }
            }.awaitAll()
        }

        logService.info("Async initialization complete")
    }

    override fun registerTenantSources(tenantConfigService: ConfigService, tenantId: String) {
        val tenantContributions = orderedContributions(ConfigLevel.TENANT)

        logService.debug("Registering ${tenantContributions.size} TENANT-level sources for tenant: $tenantId")

        for (contribution in tenantContributions) {
            val registrationKey = "$tenantId:${contribution.providerId}"
            if (registrationKey in registeredTenantSources) {
                continue
            }
            try {
                if (contribution.isEnabled(tenantConfigService)) {
                    val source = contribution.getPropertySource().let {
                        if (it is ScopedPropertySource<*>) it else ScopedPropertySourceWrapper(it, contribution.configLevel)
                    }
                    tenantConfigService.addPropertySource(source)
                    registeredTenantSources.add(registrationKey)
                    logService.debug("Registered tenant source: ${contribution.providerId} for tenant: $tenantId")
                }
            } catch (e: Exception) {
                logService.error("Failed to register tenant source: ${contribution.providerId}", e)
            }
        }
    }

    override fun registerPrincipalSources(principalConfigService: ConfigService, tenantId: String, principalId: String) {
        val principalContributions = orderedContributions(ConfigLevel.PRINCIPAL)

        logService.debug("Registering ${principalContributions.size} PRINCIPAL-level sources for: $tenantId/$principalId")

        for (contribution in principalContributions) {
            val registrationKey = "$tenantId:$principalId:${contribution.providerId}"
            if (registrationKey in registeredPrincipalSources) {
                continue
            }
            try {
                if (contribution.isEnabled(principalConfigService)) {
                    val source = contribution.getPropertySource().let {
                        if (it is ScopedPropertySource<*>) it else ScopedPropertySourceWrapper(it, contribution.configLevel)
                    }
                    principalConfigService.addPropertySource(source)
                    registeredPrincipalSources.add(registrationKey)
                    logService.debug("Registered principal source: ${contribution.providerId}")
                }
            } catch (e: Exception) {
                logService.error("Failed to register principal source: ${contribution.providerId}", e)
            }
        }
    }
}

/**
 * No-op implementation when no contributions are available.
 *
 * This is used as a fallback when no property source modules are on the classpath.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("NoOpPropertySourceBootstrap", exact = true)
class NoOpPropertySourceBootstrap : PropertySourceBootstrap {
    override fun registerAppSources() {}
    override suspend fun initializeAsync() {}
    override fun registerTenantSources(tenantConfigService: ConfigService, tenantId: String) {}
    override fun registerPrincipalSources(principalConfigService: ConfigService, tenantId: String, principalId: String) {}
    override val contributions: Set<PropertySourceContribution> = emptySet()
    override val registeredAppSourceCount: Int = 0
}
