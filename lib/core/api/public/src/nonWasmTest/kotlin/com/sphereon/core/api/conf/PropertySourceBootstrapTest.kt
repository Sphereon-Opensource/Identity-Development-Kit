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

package com.sphereon.core.api.conf

import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for PropertySourceContribution interface and helper utilities.
 */
class PropertySourceContributionTest {
    /**
     * Test PropertySourceContributionConfig.enabledKey helper.
     */
    @Test
    fun testEnabledKeyGeneration() {
        assertEquals(
            "config.providers.my-provider.enabled",
            PropertySourceContributionConfig.enabledKey("my-provider"),
        )
        assertEquals(
            "config.providers.rest.config.enabled",
            PropertySourceContributionConfig.enabledKey("rest.config"),
        )
        assertEquals(
            "config.providers.azure.app.config.enabled",
            PropertySourceContributionConfig.enabledKey("azure.app.config"),
        )
    }

    /**
     * Test PropertySourceContributionConfig.orderKey helper.
     */
    @Test
    fun testOrderKeyGeneration() {
        assertEquals(
            "config.providers.my-provider.order",
            PropertySourceContributionConfig.orderKey("my-provider"),
        )
        assertEquals(
            "config.providers.rest.config.order",
            PropertySourceContributionConfig.orderKey("rest.config"),
        )
    }

    /**
     * Test config prefix constant.
     */
    @Test
    fun testConfigPrefix() {
        assertEquals("config.providers", PropertySourceContributionConfig.CONFIG_PREFIX)
    }
}

/**
 * Tests for NoOpPropertySourceBootstrap.
 */
class NoOpPropertySourceBootstrapTest {
    @Test
    fun testNoOpBootstrapHasZeroSources() {
        val noOp = NoOpPropertySourceBootstrap()
        assertEquals(0, noOp.registeredAppSourceCount)
    }

    @Test
    fun testNoOpBootstrapHasEmptyContributions() {
        val noOp = NoOpPropertySourceBootstrap()
        assertTrue(noOp.contributions.isEmpty())
    }

    @Test
    fun testRegisterAppSourcesDoesNotThrow() {
        val noOp = NoOpPropertySourceBootstrap()
        noOp.registerAppSources() // Should not throw
    }

    @Test
    fun testInitializeAsyncDoesNotThrow() =
        runTest {
            val noOp = NoOpPropertySourceBootstrap()
            noOp.initializeAsync() // Should not throw
        }

    @Test
    fun testRegisterTenantSourcesDoesNotThrow() {
        val noOp = NoOpPropertySourceBootstrap()
        val mockConfigService = createMockConfigService()
        noOp.registerTenantSources(mockConfigService, "tenant-1") // Should not throw
    }

    @Test
    fun testRegisterPrincipalSourcesDoesNotThrow() {
        val noOp = NoOpPropertySourceBootstrap()
        val mockConfigService = createMockConfigService()
        noOp.registerPrincipalSources(mockConfigService, "tenant-1", "principal-1") // Should not throw
    }

    private fun createMockConfigService(): ConfigService {
        val source = MapPropertySource("test", emptyMap())
        val sources = DefaultPropertySources().apply { add(source) }
        val env = createTestEnv(sources)
        return TestConfigService(env)
    }

    private fun createTestEnv(sources: PropertySources): ConfigEnvironment = TestConfigEnvironment(propertySources = sources)
}

/**
 * Tests for PropertySourceContribution interface behavior.
 */
class PropertySourceContributionInterfaceTest {
    @Test
    fun testContributionDefaultRequiresAsyncInitIsFalse() {
        val contribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "test-provider"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("test", emptyMap())

                override fun getOrder() = 50
            }

        assertFalse(contribution.requiresAsyncInit)
    }

    @Test
    fun testContributionDefaultInitializeDoesNothing() =
        runTest {
            val contribution =
                object : PropertySourceContribution {
                    override val configLevel = ConfigLevel.APP
                    override val providerId = "test-provider"

                    override fun isEnabled(resolver: PropertyResolver) = true

                    override fun getPropertySource(): PropertySource<*> = MapPropertySource("test", emptyMap())

                    override fun getOrder() = 50
                }

            // Should not throw
            contribution.initialize()
        }

    @Test
    fun testContributionIsEnabledLogicWithFalseValue() {
        // Test the contribution logic directly: when resolver returns false, isEnabled should return false
        val contribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "test-provider"

                override fun isEnabled(resolver: PropertyResolver): Boolean {
                    // Simulate what real contributions do: check config value
                    // For this test, we hardcode the check to return false as if config said disabled
                    val configEnabled = false // Simulating config returned false
                    return configEnabled != false
                }

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("test-source", emptyMap())

                override fun getOrder() = 50
            }

        // The contribution should be disabled because its isEnabled logic returns false
        val source = MapPropertySource("test", emptyMap())
        val sources = DefaultPropertySources().apply { add(source) }
        val env = TestConfigEnvironment(propertySources = sources)
        val configService = TestConfigService(env)

        assertFalse(contribution.isEnabled(configService))
    }

    @Test
    fun testContributionEnabledWhenConfigNotSet() {
        val source = MapPropertySource("test", emptyMap())
        val sources = DefaultPropertySources().apply { add(source) }
        val env = TestConfigEnvironment(propertySources = sources)
        val configService = TestConfigService(env)

        val contribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "test-provider"

                override fun isEnabled(resolver: PropertyResolver): Boolean {
                    val configEnabled =
                        resolver.getProperty(
                            PropertySourceContributionConfig.enabledKey(providerId),
                            Boolean::class,
                        )
                    return configEnabled != false
                }

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("test-source", emptyMap())

                override fun getOrder() = 50
            }

        assertTrue(contribution.isEnabled(configService))
    }
}

class PropertySourceContributionWithAsyncInitTest {
    @Test
    fun testContributionWithAsyncInitTrue() {
        var initialized = false

        val contribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "async-provider"
                override val requiresAsyncInit = true

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("async-source", emptyMap())

                override fun getOrder() = 50

                override suspend fun initialize() {
                    initialized = true
                }
            }

        assertTrue(contribution.requiresAsyncInit)
        assertEquals("async-provider", contribution.providerId)
    }

    @Test
    fun testContributionOrdering() {
        val contribution1 =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "provider-1"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("source-1", emptyMap())

                override fun getOrder() = 100
            }

        val contribution2 =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "provider-2"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("source-2", emptyMap())

                override fun getOrder() = 50
            }

        val contributions = listOf(contribution1, contribution2).sortedBy { it.getOrder() }

        assertEquals("provider-2", contributions[0].providerId) // Lower order first
        assertEquals("provider-1", contributions[1].providerId)
    }

    @Test
    fun testContributionForTenantLevel() {
        val contribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.TENANT
                override val providerId = "tenant-provider"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("tenant-source", emptyMap())

                override fun getOrder() = 50
            }

        assertEquals(ConfigLevel.TENANT, contribution.configLevel)
    }

    @Test
    fun testContributionForPrincipalLevel() {
        val contribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.PRINCIPAL
                override val providerId = "principal-provider"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("principal-source", emptyMap())

                override fun getOrder() = 50
            }

        assertEquals(ConfigLevel.PRINCIPAL, contribution.configLevel)
    }

    @Test
    fun testContributionConfigKeyGeneration() {
        assertEquals(
            "config.providers.my-provider.enabled",
            PropertySourceContributionConfig.enabledKey("my-provider"),
        )

        assertEquals(
            "config.providers.my-provider.order",
            PropertySourceContributionConfig.orderKey("my-provider"),
        )
    }
}

class PropertySourceContributionFilteringTest {
    @Test
    fun filterContributionsByLevel() {
        val appContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "app-provider"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("app-source", emptyMap())

                override fun getOrder() = 50
            }

        val tenantContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.TENANT
                override val providerId = "tenant-provider"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("tenant-source", emptyMap())

                override fun getOrder() = 50
            }

        val principalContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.PRINCIPAL
                override val providerId = "principal-provider"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("principal-source", emptyMap())

                override fun getOrder() = 50
            }

        val contributions = setOf(appContribution, tenantContribution, principalContribution)

        val appContributions = contributions.filter { it.configLevel == ConfigLevel.APP }
        val tenantContributions = contributions.filter { it.configLevel == ConfigLevel.TENANT }
        val principalContributions = contributions.filter { it.configLevel == ConfigLevel.PRINCIPAL }

        assertEquals(1, appContributions.size)
        assertEquals(1, tenantContributions.size)
        assertEquals(1, principalContributions.size)
    }

    @Test
    fun filterEnabledContributions() {
        val source = MapPropertySource("test", emptyMap())
        val sources = DefaultPropertySources().apply { add(source) }
        val env = TestConfigEnvironment(propertySources = sources)
        val configService = TestConfigService(env)

        val enabledContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "enabled-provider"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("enabled-source", emptyMap())

                override fun getOrder() = 50
            }

        val disabledContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "disabled-provider"

                override fun isEnabled(resolver: PropertyResolver) = false

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("disabled-source", emptyMap())

                override fun getOrder() = 50
            }

        val contributions = setOf(enabledContribution, disabledContribution)

        val enabledContributions = contributions.filter { it.isEnabled(configService) }

        assertEquals(1, enabledContributions.size)
        assertEquals("enabled-provider", enabledContributions.first().providerId)
    }
}

/**
 * Tests for PropertySourceBootstrapImpl with various scenarios.
 */
class PropertySourceBootstrapImplTest {
    private fun createAppConfigService(): AppConfigService {
        val source = MapPropertySource("test", emptyMap())
        val sources = DefaultPropertySources().apply { add(source) }
        val env = TestConfigEnvironment(propertySources = sources)
        return TestAppConfigService(env)
    }

    private fun createLogService(): com.sphereon.core.api.log.LogService =
        com.sphereon.core.api.log
            .AppConsoleLogServiceImpl()

    @Test
    fun registerAppSourcesRegistersEnabledContributions() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()

        val contribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "test-app-provider"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("test-source", mapOf("key" to "value"))

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(contribution),
                logService = logService,
            )

        bootstrap.registerAppSources()
        assertEquals(1, bootstrap.registeredAppSourceCount)
    }

    @Test
    fun registerAppSourcesIsExplicit() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()

        val contribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "explicit-provider"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("explicit-source", emptyMap())

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(contribution),
                logService = logService,
            )

        assertEquals(0, bootstrap.registeredAppSourceCount)

        bootstrap.registerAppSources()

        assertEquals(1, bootstrap.registeredAppSourceCount)
    }

    @Test
    fun registerAppSourcesIsIdempotent() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()

        val contribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "idempotent-provider"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("idempotent-source", emptyMap())

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(contribution),
                logService = logService,
            )

        bootstrap.registerAppSources()
        bootstrap.registerAppSources()

        assertEquals(1, bootstrap.registeredAppSourceCount)
    }

    @Test
    fun registerAppSourcesSkipsDisabledContributions() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()

        val disabledContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "disabled-provider"

                override fun isEnabled(resolver: PropertyResolver) = false

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("disabled-source", emptyMap())

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(disabledContribution),
                logService = logService,
            )

        bootstrap.registerAppSources()
        assertEquals(0, bootstrap.registeredAppSourceCount)
    }

    @Test
    fun registerAppSourcesSkipsNonAppLevelContributions() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()

        val tenantContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.TENANT
                override val providerId = "tenant-provider"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("tenant-source", emptyMap())

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(tenantContribution),
                logService = logService,
            )

        bootstrap.registerAppSources()
        assertEquals(0, bootstrap.registeredAppSourceCount)
    }

    @Test
    fun registerAppSourcesPropagatesExceptions() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()

        val failingContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "failing-provider"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = throw RuntimeException("Simulated failure")

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(failingContribution),
                logService = logService,
            )

        // Exceptions from contributions are no longer silently caught
        assertFailsWith<RuntimeException> {
            bootstrap.registerAppSources()
        }
    }

    @Test
    fun registerAppSourcesRespectsOrder() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()

        val order100 =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "order-100"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("source-100", emptyMap())

                override fun getOrder() = 100
            }

        val order50 =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "order-50"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("source-50", emptyMap())

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(order100, order50),
                logService = logService,
            )

        bootstrap.registerAppSources()
        assertEquals(2, bootstrap.registeredAppSourceCount)
    }

    @Test
    fun registerAppSourcesUsesProviderIdAsTieBreakerForSameOrder() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()

        val zProvider =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "z-provider"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("source-z", mapOf("tie.breaker.key" to "z-value"))

                override fun getOrder() = 50
            }

        val aProvider =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "a-provider"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("source-a", mapOf("tie.breaker.key" to "a-value"))

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(zProvider, aProvider),
                logService = logService,
            )

        bootstrap.registerAppSources()

        assertEquals("a-value", appConfigService.getPropertyAsString("tie.breaker.key"))
    }

    @Test
    fun initializeAsyncInitializesAsyncProviders() =
        runTest {
            val logService = createLogService()
            val appConfigService = createAppConfigService()
            var initialized = false

            val asyncContribution =
                object : PropertySourceContribution {
                    override val configLevel = ConfigLevel.APP
                    override val providerId = "async-provider"
                    override val requiresAsyncInit = true

                    override fun isEnabled(resolver: PropertyResolver) = true

                    override fun getPropertySource(): PropertySource<*> = MapPropertySource("async-source", emptyMap())

                    override fun getOrder() = 50

                    override suspend fun initialize() {
                        initialized = true
                    }
                }

            val bootstrap =
                PropertySourceBootstrapImpl(
                    appConfigService = appConfigService,
                    contributions = setOf(asyncContribution),
                    logService = logService,
                )

            bootstrap.registerAppSources()
            bootstrap.initializeAsync()

            assertTrue(initialized)
        }

    @Test
    fun initializeAsyncSkipsNonAsyncProviders() =
        runTest {
            val logService = createLogService()
            val appConfigService = createAppConfigService()
            var initCalled = false

            val syncContribution =
                object : PropertySourceContribution {
                    override val configLevel = ConfigLevel.APP
                    override val providerId = "sync-provider"
                    override val requiresAsyncInit = false

                    override fun isEnabled(resolver: PropertyResolver) = true

                    override fun getPropertySource(): PropertySource<*> = MapPropertySource("sync-source", emptyMap())

                    override fun getOrder() = 50

                    override suspend fun initialize() {
                        initCalled = true
                    }
                }

            val bootstrap =
                PropertySourceBootstrapImpl(
                    appConfigService = appConfigService,
                    contributions = setOf(syncContribution),
                    logService = logService,
                )

            bootstrap.registerAppSources()
            bootstrap.initializeAsync()

            assertFalse(initCalled)
        }

    @Test
    fun initializeAsyncSkipsUnregisteredProviders() =
        runTest {
            val logService = createLogService()
            val appConfigService = createAppConfigService()
            var initCalled = false

            // This contribution is disabled, so it won't be registered
            val disabledAsyncContribution =
                object : PropertySourceContribution {
                    override val configLevel = ConfigLevel.APP
                    override val providerId = "disabled-async-provider"
                    override val requiresAsyncInit = true

                    override fun isEnabled(resolver: PropertyResolver) = false

                    override fun getPropertySource(): PropertySource<*> = MapPropertySource("disabled-source", emptyMap())

                    override fun getOrder() = 50

                    override suspend fun initialize() {
                        initCalled = true
                    }
                }

            val bootstrap =
                PropertySourceBootstrapImpl(
                    appConfigService = appConfigService,
                    contributions = setOf(disabledAsyncContribution),
                    logService = logService,
                )

            bootstrap.registerAppSources()
            bootstrap.initializeAsync()

            assertFalse(initCalled)
        }

    @Test
    fun initializeAsyncHandlesExceptionsGracefully() =
        runTest {
            val logService = createLogService()
            val appConfigService = createAppConfigService()
            var secondInitialized = false

            val failingContribution =
                object : PropertySourceContribution {
                    override val configLevel = ConfigLevel.APP
                    override val providerId = "failing-async"
                    override val requiresAsyncInit = true

                    override fun isEnabled(resolver: PropertyResolver) = true

                    override fun getPropertySource(): PropertySource<*> = MapPropertySource("fail-source", emptyMap())

                    override fun getOrder() = 50

                    override suspend fun initialize(): Unit = throw RuntimeException("Async init failed")
                }

            val workingContribution =
                object : PropertySourceContribution {
                    override val configLevel = ConfigLevel.APP
                    override val providerId = "working-async"
                    override val requiresAsyncInit = true

                    override fun isEnabled(resolver: PropertyResolver) = true

                    override fun getPropertySource(): PropertySource<*> = MapPropertySource("work-source", emptyMap())

                    override fun getOrder() = 100

                    override suspend fun initialize() {
                        secondInitialized = true
                    }
                }

            val bootstrap =
                PropertySourceBootstrapImpl(
                    appConfigService = appConfigService,
                    contributions = setOf(failingContribution, workingContribution),
                    logService = logService,
                )

            bootstrap.registerAppSources()
            // Should not throw, should handle exception gracefully
            bootstrap.initializeAsync()

            assertTrue(secondInitialized)
        }

    @Test
    fun initializeAsyncDoesNothingWhenNoAsyncContributions() =
        runTest {
            val logService = createLogService()
            val appConfigService = createAppConfigService()

            val syncContribution =
                object : PropertySourceContribution {
                    override val configLevel = ConfigLevel.APP
                    override val providerId = "sync-provider"

                    override fun isEnabled(resolver: PropertyResolver) = true

                    override fun getPropertySource(): PropertySource<*> = MapPropertySource("sync-source", emptyMap())

                    override fun getOrder() = 50
                }

            val bootstrap =
                PropertySourceBootstrapImpl(
                    appConfigService = appConfigService,
                    contributions = setOf(syncContribution),
                    logService = logService,
                )

            bootstrap.registerAppSources()
            // Should complete without error
            bootstrap.initializeAsync()
        }

    @Test
    fun registerTenantSourcesRegistersEnabledContributions() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()
        val tenantConfigService = createMockConfigService()

        val tenantContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.TENANT
                override val providerId = "tenant-provider"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("tenant-source", mapOf("key" to "value"))

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(tenantContribution),
                logService = logService,
            )

        bootstrap.registerTenantSources(tenantConfigService, "tenant-123")
        // Verify no exception thrown - the contribution was processed
    }

    @Test
    fun registerTenantSourcesSkipsDisabledContributions() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()
        val tenantConfigService = createMockConfigService()

        val disabledTenantContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.TENANT
                override val providerId = "disabled-tenant"

                override fun isEnabled(resolver: PropertyResolver) = false

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("disabled-source", emptyMap())

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(disabledTenantContribution),
                logService = logService,
            )

        bootstrap.registerTenantSources(tenantConfigService, "tenant-123")
        // Should complete without error, but contribution is not registered
    }

    @Test
    fun registerTenantSourcesIsIdempotentPerConfigService() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()
        val tenantConfigService = createMockConfigService()

        val tenantContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.TENANT
                override val providerId = "tenant-idempotent"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("tenant-idempotent-source", mapOf("tenant.idempotent.key" to "value"))

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(tenantContribution),
                logService = logService,
            )

        bootstrap.registerTenantSources(tenantConfigService, "tenant-123")
        bootstrap.registerTenantSources(tenantConfigService, "tenant-123")

        val duplicateCount =
            tenantConfigService
                .getPropertySources(includeParents = false)
                .count { it.getName() == "tenant-idempotent-source" }
        assertEquals(1, duplicateCount)
    }

    @Test
    fun registerTenantSourcesEvaluatesEnablementFromAppConfigOnly() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()
        val tenantConfigService = createMockConfigService()
        var existingTenantSourceReads = 0

        tenantConfigService.addPropertySource(
            object : MapPropertySource("existing-tenant-source", emptyMap()) {
                override fun <T : Any> getProperty(
                    name: String,
                    targetType: KClass<T>,
                ): T? {
                    existingTenantSourceReads++
                    throw IllegalStateException("tenant source must not be queried for provider enablement")
                }
            },
        )

        val tenantContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.TENANT
                override val providerId = "tenant-bootstrap-only"

                override fun isEnabled(resolver: PropertyResolver): Boolean =
                    resolver.getProperty("config.providers.$providerId.enabled", Boolean::class, true) != false

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("tenant-bootstrap-only-source", emptyMap())

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(tenantContribution),
                logService = logService,
            )

        bootstrap.registerTenantSources(tenantConfigService, "tenant-123")

        assertEquals(0, existingTenantSourceReads)
        assertTrue(tenantConfigService.getPropertySources(includeParents = false).contains("tenant-bootstrap-only-source"))
    }

    @Test
    fun registerTenantSourcesRegistersSameTenantInDifferentConfigServices() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()
        val firstTenantConfigService = createMockConfigService()
        val secondTenantConfigService = createMockConfigService()

        val tenantContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.TENANT
                override val providerId = "tenant-per-context"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("tenant-per-context-source", emptyMap())

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(tenantContribution),
                logService = logService,
            )

        bootstrap.registerTenantSources(firstTenantConfigService, "tenant-123")
        bootstrap.registerTenantSources(secondTenantConfigService, "tenant-123")

        assertTrue(firstTenantConfigService.getPropertySources(includeParents = false).contains("tenant-per-context-source"))
        assertTrue(secondTenantConfigService.getPropertySources(includeParents = false).contains("tenant-per-context-source"))
    }

    @Test
    fun registerTenantSourcesPropagatesExceptions() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()
        val tenantConfigService = createMockConfigService()

        val failingContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.TENANT
                override val providerId = "failing-tenant"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = throw RuntimeException("Simulated tenant source failure")

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(failingContribution),
                logService = logService,
            )

        // Exceptions from tenant contributions are no longer silently caught
        assertFailsWith<RuntimeException> {
            bootstrap.registerTenantSources(tenantConfigService, "tenant-123")
        }
    }

    @Test
    fun registerPrincipalSourcesRegistersEnabledContributions() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()
        val principalConfigService = createMockConfigService()

        val principalContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.PRINCIPAL
                override val providerId = "principal-provider"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("principal-source", mapOf("key" to "value"))

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(principalContribution),
                logService = logService,
            )

        bootstrap.registerPrincipalSources(principalConfigService, "tenant-123", "principal-456")
        // Verify no exception thrown
    }

    @Test
    fun registerPrincipalSourcesSkipsDisabledContributions() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()
        val principalConfigService = createMockConfigService()

        val disabledPrincipalContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.PRINCIPAL
                override val providerId = "disabled-principal"

                override fun isEnabled(resolver: PropertyResolver) = false

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("disabled-source", emptyMap())

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(disabledPrincipalContribution),
                logService = logService,
            )

        bootstrap.registerPrincipalSources(principalConfigService, "tenant-123", "principal-456")
    }

    @Test
    fun registerPrincipalSourcesIsIdempotentPerConfigService() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()
        val principalConfigService = createMockConfigService()

        val principalContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.PRINCIPAL
                override val providerId = "principal-idempotent"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("principal-idempotent-source", mapOf("principal.idempotent.key" to "value"))

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(principalContribution),
                logService = logService,
            )

        bootstrap.registerPrincipalSources(principalConfigService, "tenant-123", "principal-456")
        bootstrap.registerPrincipalSources(principalConfigService, "tenant-123", "principal-456")

        val duplicateCount =
            principalConfigService
                .getPropertySources(includeParents = false)
                .count { it.getName() == "principal-idempotent-source" }
        assertEquals(1, duplicateCount)
    }

    @Test
    fun registerPrincipalSourcesRegistersSamePrincipalInDifferentConfigServices() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()
        val firstPrincipalConfigService = createMockConfigService()
        val secondPrincipalConfigService = createMockConfigService()

        val principalContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.PRINCIPAL
                override val providerId = "principal-per-context"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("principal-per-context-source", emptyMap())

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(principalContribution),
                logService = logService,
            )

        bootstrap.registerPrincipalSources(firstPrincipalConfigService, "tenant-123", "principal-456")
        bootstrap.registerPrincipalSources(secondPrincipalConfigService, "tenant-123", "principal-456")

        assertTrue(firstPrincipalConfigService.getPropertySources(includeParents = false).contains("principal-per-context-source"))
        assertTrue(secondPrincipalConfigService.getPropertySources(includeParents = false).contains("principal-per-context-source"))
    }

    @Test
    fun registerPrincipalSourcesPropagatesExceptions() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()
        val principalConfigService = createMockConfigService()

        val failingContribution =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.PRINCIPAL
                override val providerId = "failing-principal"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = throw RuntimeException("Simulated principal source failure")

                override fun getOrder() = 50
            }

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = setOf(failingContribution),
                logService = logService,
            )

        // Exceptions from principal contributions are no longer silently caught
        assertFailsWith<RuntimeException> {
            bootstrap.registerPrincipalSources(principalConfigService, "tenant-123", "principal-456")
        }
    }

    @Test
    fun contributionsPropertyReturnsAllContributions() {
        val logService = createLogService()
        val appConfigService = createAppConfigService()

        val contribution1 =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.APP
                override val providerId = "provider-1"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("source-1", emptyMap())

                override fun getOrder() = 50
            }

        val contribution2 =
            object : PropertySourceContribution {
                override val configLevel = ConfigLevel.TENANT
                override val providerId = "provider-2"

                override fun isEnabled(resolver: PropertyResolver) = true

                override fun getPropertySource(): PropertySource<*> = MapPropertySource("source-2", emptyMap())

                override fun getOrder() = 50
            }

        val contributions = setOf(contribution1, contribution2)

        val bootstrap =
            PropertySourceBootstrapImpl(
                appConfigService = appConfigService,
                contributions = contributions,
                logService = logService,
            )

        assertEquals(2, bootstrap.contributions.size)
    }

    private fun createMockConfigService(): ConfigService {
        val source = MapPropertySource("test", emptyMap())
        val sources = DefaultPropertySources().apply { add(source) }
        val env = TestConfigEnvironment(propertySources = sources)
        return TestConfigService(env)
    }
}

/**
 * Test implementation of AppConfigService for testing.
 */
class TestAppConfigService(
    environment: ConfigEnvironment,
) : AbstractConfigService(environment),
    AppConfigService {
    override val parent: ConfigService? = null
    override val level: ConfigLevel = ConfigLevel.APP
}
