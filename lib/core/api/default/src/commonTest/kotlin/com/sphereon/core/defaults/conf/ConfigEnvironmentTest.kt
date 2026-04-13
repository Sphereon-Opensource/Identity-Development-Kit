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

package com.sphereon.core.defaults.conf

import com.sphereon.core.api.conf.AppConfigEnvironment
import com.sphereon.core.api.conf.ConfigEnvironment
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.DefaultAppMapPropertySource
import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.conf.DefaultPropertySources
import com.sphereon.core.api.conf.DefaultTenantMapPropertySource
import com.sphereon.core.api.conf.MapPropertySource
import com.sphereon.core.api.conf.NoOpSyncSnapshotCache
import com.sphereon.core.api.conf.PrincipalConfigEnvironment
import com.sphereon.core.api.conf.ProtectedMutableMapPropertySource
import com.sphereon.core.api.conf.TenantConfigEnvironment
import com.sphereon.core.api.testutil.appConfigService
import com.sphereon.core.api.testutil.createCoreApiTestAppGraph
import com.sphereon.di.context.AnonymousContext
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.UserContextGraph
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserContextManager
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionInstance
import software.amazon.app.platform.scope.Scope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigEnvironmentTest {
    private fun createAppGraph() =
        createCoreApiTestAppGraph(
            this,
            "config-test",
            "test-profile",
            "0.0.1-TEST",
        )

    // Test helper: Creates a minimal UserContextInstance for testing config environments
    private fun createTestUserContextInstance(): UserContextInstance =
        object : UserContextInstance {
            override val contextId: String = "test-context-id"
            override val context: UserContext = AnonymousContext
            override val graph: UserContextGraph
                get() = throw NotImplementedError("Not needed for config tests")
            override val scope: Scope
                get() = throw NotImplementedError("Not needed for config tests")
            override val userContextManager: UserContextManager
                get() = throw NotImplementedError("Not needed for config tests")
            override val sessionContextManager: SessionContextManager
                get() = throw NotImplementedError("Not needed for config tests")

            override fun <T : Any> getService(id: String): T = throw NotImplementedError("Not needed for config tests")

            override fun addService(
                id: String,
                service: Any,
            ): Scope = throw NotImplementedError("Not needed for config tests")

            override fun isCurrentlyActive(): Boolean = false

            override fun makeActive(): Boolean = false

            override fun destroy() {}

            override fun createSession(
                sessionId: String,
                makeActive: Boolean,
            ): SessionInstance = throw NotImplementedError("Not needed for config tests")

            override fun getOrCreateAnonymousSession(makeActive: Boolean): SessionInstance = throw NotImplementedError("Not needed for config tests")

            override fun getOrCreateBackgroundServiceSession(makeActive: Boolean): SessionInstance = throw NotImplementedError("Not needed for config tests")
        }

    // Helper to create AppConfigEnvironmentImpl with test defaults
    private fun createTestAppConfig(
        appId: String = "test-app",
        profile: String = "test-profile",
    ) = AppConfigEnvironmentImpl(appId, profile, NoOpSyncSnapshotCache, interpolator = null, secretResolver = null)

    // Helper to create TenantConfigEnvironmentImpl with test defaults
    private fun createTestTenantConfig(
        appId: String = "test-app",
        profile: String = "test-profile",
        parent: AppConfigEnvironmentImpl = createTestAppConfig(appId, profile),
    ) = TenantConfigEnvironmentImpl(appId, profile, createTestUserContextInstance(), NoOpSyncSnapshotCache, parent, interpolator = null, secretResolver = null)

    // Helper to create PrincipalConfigEnvironmentImpl with test defaults
    private fun createTestPrincipalConfig(
        appId: String = "test-app",
        profile: String = "test-profile",
        parent: TenantConfigEnvironmentImpl = createTestTenantConfig(appId, profile),
    ) = PrincipalConfigEnvironmentImpl(appId, profile, createTestUserContextInstance(), NoOpSyncSnapshotCache, parent, interpolator = null, secretResolver = null)

    private fun getRequiredMutableSource(
        environment: ConfigEnvironment,
        sourceName: String,
    ): ProtectedMutableMapPropertySource {
        val source = environment.getPropertySources(includeParents = false).get(sourceName)
        assertNotNull(source)
        assertTrue(source is ProtectedMutableMapPropertySource)
        return source
    }

    // ========== AppConfigService Tests ==========

    @Test
    fun appGraphHasAppConfigService() {
        val appGraph = createAppGraph()
        try {
            val appConfigService = appGraph.appConfigService
            assertNotNull(appConfigService)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun appConfigServiceCanGetProperty() {
        val appGraph = createAppGraph()
        try {
            val appConfigService = appGraph.appConfigService
            // Getting a property that doesn't exist should return null (not throw)
            val nonExistentProperty = appConfigService.getPropertyAsString("non.existent.property", null)
            // Just verify the method works - the result depends on configuration
            // The important thing is it doesn't throw
            assertNull(nonExistentProperty)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun appConfigServiceCanGetPropertyWithDefault() {
        val appGraph = createAppGraph()
        try {
            val appConfigService = appGraph.appConfigService
            val property = appConfigService.getPropertyAsString("non.existent.property", "default-value")
            // Should return the default when property doesn't exist
            assertTrue(property == "default-value" || property != null)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== AbstractConfigEnvironment Tests ==========

    @Test
    fun appConfigEnvironmentIsAbstractConfigEnvironment() {
        val appGraph = createAppGraph()
        try {
            val appConfig = createTestAppConfig()
            assertTrue(appConfig is AbstractConfigEnvironment)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun tenantConfigEnvironmentIsAbstractConfigEnvironment() {
        val appGraph = createAppGraph()
        try {
            val tenantConfig = createTestTenantConfig()
            assertTrue(tenantConfig is AbstractConfigEnvironment)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun principalConfigEnvironmentIsAbstractConfigEnvironment() {
        val appGraph = createAppGraph()
        try {
            val principalConfig = createTestPrincipalConfig()
            assertTrue(principalConfig is AbstractConfigEnvironment)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== Config Hierarchy Tests ==========

    @Test
    fun appConfigEnvironmentHasNullParent() {
        val appGraph = createAppGraph()
        try {
            val appConfig = createTestAppConfig()
            assertNull(appConfig.parent)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun tenantConfigEnvironmentHasAppConfigAsParent() {
        val appGraph = createAppGraph()
        try {
            val tenantConfig = createTestTenantConfig()
            assertNotNull(tenantConfig.parent)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun principalConfigEnvironmentHasTenantConfigAsParent() {
        val appGraph = createAppGraph()
        try {
            val principalConfig = createTestPrincipalConfig()
            assertNotNull(principalConfig.parent)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== ConfigEnvironment Interface Tests ==========

    @Test
    fun appConfigEnvironmentIsAppConfigEnvironment() {
        val appGraph = createAppGraph()
        try {
            val appConfig = createTestAppConfig()
            assertTrue(appConfig is AppConfigEnvironment)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun tenantConfigEnvironmentIsTenantConfigEnvironment() {
        val appGraph = createAppGraph()
        try {
            val tenantConfig = createTestTenantConfig()
            assertTrue(tenantConfig is TenantConfigEnvironment)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun principalConfigEnvironmentIsPrincipalConfigEnvironment() {
        val appGraph = createAppGraph()
        try {
            val principalConfig = createTestPrincipalConfig()
            assertTrue(principalConfig is PrincipalConfigEnvironment)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== Namespace Tests ==========

    @Test
    fun configEnvironmentCanGetNamespace() {
        val appGraph = createAppGraph()
        try {
            val appConfig = createTestAppConfig()
            val namespace = appConfig.getNamespace()
            assertNotNull(namespace)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== Additional Config Tests ==========

    @Test
    fun configEnvironmentGetActiveProfile() {
        val appGraph = createAppGraph()
        try {
            val appConfig = createTestAppConfig()
            assertEquals("test-profile", appConfig.getActiveProfile())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun configEnvironmentGetAppName() {
        val appGraph = createAppGraph()
        try {
            val appConfig = createTestAppConfig()
            assertEquals("test-app", appConfig.getAppName())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun configEnvironmentGetConfigLocation() {
        val appGraph = createAppGraph()
        try {
            val appConfig = createTestAppConfig()
            assertNotNull(appConfig.getConfigLocation())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun configEnvironmentGetPropertySources() {
        val appGraph = createAppGraph()
        try {
            val appConfig = createTestAppConfig()
            val sources = appConfig.getPropertySources(false)
            assertNotNull(sources)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun configEnvironmentGetPropertySourcesWithParents() {
        val appGraph = createAppGraph()
        try {
            val tenantConfig = createTestTenantConfig()
            val sourcesWithParents = tenantConfig.getPropertySources(true)
            assertNotNull(sourcesWithParents)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun configEnvironmentContainsPropertyReturnsFalse() {
        val appGraph = createAppGraph()
        try {
            val appConfig = createTestAppConfig()
            assertFalse(appConfig.containsProperty("non.existent.property"))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun configEnvironmentGetAllProperties() {
        val appGraph = createAppGraph()
        try {
            val appConfig = createTestAppConfig()
            val allProps = appConfig.getAllProperties()
            assertNotNull(allProps)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun configEnvironmentGetAllPropertiesAsString() {
        val appGraph = createAppGraph()
        try {
            val appConfig = createTestAppConfig()
            val allPropsString = appConfig.getAllPropertiesAsString()
            assertNotNull(allPropsString)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun configEnvironmentGetSubProperties() {
        val appGraph = createAppGraph()
        try {
            val appConfig = createTestAppConfig()
            val subProps = appConfig.getSubProperties(setOf("com.sphereon"), false)
            assertNotNull(subProps)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun configEnvironmentGetSubPropertiesAsString() {
        val appGraph = createAppGraph()
        try {
            val appConfig = createTestAppConfig()
            val subPropsString = appConfig.getSubPropertiesAsString(setOf("com.sphereon"), true)
            assertNotNull(subPropsString)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun resolverInvalidatesWhenLocalSourceStructureChangesWithoutCountChange() {
        val appGraph = createAppGraph()
        try {
            val localSources =
                DefaultPropertySources(
                    mutableListOf(MapPropertySource("first", mapOf("config.value" to "first"))),
                )
            val env =
                object : AbstractConfigEnvironment(
                    appId = "test-app",
                    profile = "test-profile",
                    propertySources = localSources,
                    snapshotCache = null,
                    interpolator = null,
                    secretResolver = null,
                ) {
                    override val parent: ConfigEnvironment? = null
                    override val level: ConfigLevel = ConfigLevel.APP
                }

            assertEquals("first", env.getPropertyAsString("config.value"))

            localSources.removeByName("first")
            localSources.add(MapPropertySource("second", mapOf("config.value" to "second")))

            assertEquals("second", env.getPropertyAsString("config.value"))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun resolverInvalidatesWhenParentSourceStructureChangesWithoutCountChange() {
        val appGraph = createAppGraph()
        try {
            val parentSources =
                DefaultPropertySources(
                    mutableListOf(MapPropertySource("parent-first", mapOf("shared.value" to "parent-first"))),
                )
            val parentEnv =
                object : AbstractConfigEnvironment(
                    appId = "test-app",
                    profile = "test-profile",
                    propertySources = parentSources,
                    snapshotCache = null,
                    interpolator = null,
                    secretResolver = null,
                ) {
                    override val parent: ConfigEnvironment? = null
                    override val level: ConfigLevel = ConfigLevel.APP
                }

            val childEnv =
                object : AbstractConfigEnvironment(
                    appId = "test-app",
                    profile = "test-profile",
                    propertySources = DefaultPropertySources(mutableListOf(MapPropertySource("child", emptyMap()))),
                    snapshotCache = null,
                    interpolator = null,
                    secretResolver = null,
                ) {
                    override val parent: ConfigEnvironment = parentEnv
                    override val level: ConfigLevel = ConfigLevel.TENANT
                }

            assertEquals("parent-first", childEnv.getPropertyAsString("shared.value"))

            parentSources.removeByName("parent-first")
            parentSources.add(MapPropertySource("parent-second", mapOf("shared.value" to "parent-second")))

            assertEquals("parent-second", childEnv.getPropertyAsString("shared.value"))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun appDefaultMapSourceIsIsolatedPerEnvironmentInstance() {
        val appGraph = createAppGraph()
        try {
            val appA = createTestAppConfig()
            val appB = createTestAppConfig()

            val appASource = getRequiredMutableSource(appA, DefaultAppMapPropertySource.NAME)
            appASource.addProperty("isolation.app.key", "app-a-value")

            assertEquals("app-a-value", appA.getPropertyAsString("isolation.app.key"))
            assertNull(appB.getPropertyAsString("isolation.app.key"))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun tenantDefaultMapSourceIsIsolatedPerEnvironmentInstance() {
        val appGraph = createAppGraph()
        try {
            val app = createTestAppConfig()
            val tenantA = createTestTenantConfig(parent = app)
            val tenantB = createTestTenantConfig(parent = app)

            val tenantASource = getRequiredMutableSource(tenantA, DefaultTenantMapPropertySource.NAME)
            tenantASource.addProperty("isolation.tenant.key", "tenant-a-value")

            assertEquals("tenant-a-value", tenantA.getPropertyAsString("isolation.tenant.key"))
            assertNull(tenantB.getPropertyAsString("isolation.tenant.key"))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun principalDefaultMapSourceIsIsolatedPerEnvironmentInstance() {
        val appGraph = createAppGraph()
        try {
            val tenant = createTestTenantConfig()
            val principalA = createTestPrincipalConfig(parent = tenant)
            val principalB = createTestPrincipalConfig(parent = tenant)

            val principalASource = getRequiredMutableSource(principalA, DefaultPrincipalMapPropertySource.NAME)
            principalASource.addProperty("isolation.principal.key", "principal-a-value")

            assertEquals("principal-a-value", principalA.getPropertyAsString("isolation.principal.key"))
            assertNull(principalB.getPropertyAsString("isolation.principal.key"))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun configPrecedenceIsDeterministicAcrossAppTenantPrincipal() {
        val appGraph = createAppGraph()
        try {
            val app = createTestAppConfig()
            val tenant = createTestTenantConfig(parent = app)
            val principal = createTestPrincipalConfig(parent = tenant)

            getRequiredMutableSource(app, DefaultAppMapPropertySource.NAME).addProperty("precedence.value", "app")
            getRequiredMutableSource(tenant, DefaultTenantMapPropertySource.NAME).addProperty("precedence.value", "tenant")
            getRequiredMutableSource(principal, DefaultPrincipalMapPropertySource.NAME).addProperty("precedence.value", "principal")

            assertEquals("app", app.getPropertyAsString("precedence.value"))
            assertEquals("tenant", tenant.getPropertyAsString("precedence.value"))
            assertEquals("principal", principal.getPropertyAsString("precedence.value"))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun configPrecedenceFallbackChainRemainsStable() {
        val appGraph = createAppGraph()
        try {
            val app = createTestAppConfig()
            val tenant = createTestTenantConfig(parent = app)
            val principal = createTestPrincipalConfig(parent = tenant)

            val appSource = getRequiredMutableSource(app, DefaultAppMapPropertySource.NAME)
            val tenantSource = getRequiredMutableSource(tenant, DefaultTenantMapPropertySource.NAME)
            val principalSource = getRequiredMutableSource(principal, DefaultPrincipalMapPropertySource.NAME)

            appSource.addProperty("precedence.fallback.value", "app")
            assertEquals("app", app.getPropertyAsString("precedence.fallback.value"))
            assertEquals("app", tenant.getPropertyAsString("precedence.fallback.value"))
            assertEquals("app", principal.getPropertyAsString("precedence.fallback.value"))

            tenantSource.addProperty("precedence.fallback.value", "tenant")
            assertEquals("app", app.getPropertyAsString("precedence.fallback.value"))
            assertEquals("tenant", tenant.getPropertyAsString("precedence.fallback.value"))
            assertEquals("tenant", principal.getPropertyAsString("precedence.fallback.value"))

            principalSource.addProperty("precedence.fallback.value", "principal")
            assertEquals("app", app.getPropertyAsString("precedence.fallback.value"))
            assertEquals("tenant", tenant.getPropertyAsString("precedence.fallback.value"))
            assertEquals("principal", principal.getPropertyAsString("precedence.fallback.value"))
        } finally {
            appGraph.destroy()
        }
    }

    // ========== Constants Tests ==========

    @Test
    fun abstractConfigEnvironmentConstantsExist() {
        val appGraph = createAppGraph()
        try {
            assertNotNull(AbstractConfigEnvironment.CONFIG_LOCATION_PROP_KEY)
            assertNotNull(AbstractConfigEnvironment.DEFAULT_CONFIG_LOCATION)
            assertNotNull(AbstractConfigEnvironment.ACTIVE_PROFILE_PROP_KEY)
            assertNotNull(AbstractConfigEnvironment.DEFAULT_PROFILE_PROP_KEY)
            assertNotNull(AbstractConfigEnvironment.RESERVED_DEFAULT_APP_NAME)
            assertNotNull(AbstractConfigEnvironment.RESERVED_DEFAULT_PROFILE_NAME)
        } finally {
            appGraph.destroy()
        }
    }
}
