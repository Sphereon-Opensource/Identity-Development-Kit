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

package com.sphereon.core.api.context

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.PropertySource
import com.sphereon.core.api.conf.PropertySources
import com.sphereon.core.api.conf.TenantConfigService
import kotlinx.io.files.Path
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class IdkScopeTest {
    @Test
    fun enumHasThreeScopes() {
        assertEquals(3, IdkScope.entries.size)
    }

    @Test
    fun appScopeExists() {
        assertEquals("APP", IdkScope.APP.name)
    }

    @Test
    fun userScopeExists() {
        assertEquals("USER", IdkScope.USER.name)
    }

    @Test
    fun sessionScopeExists() {
        assertEquals("SESSION", IdkScope.SESSION.name)
    }
}

class ContextConfigImplTest {
    // Minimal test implementations
    private class TestAppConfigService : AppConfigService {
        override val configLevel: ConfigLevel = ConfigLevel.APP

        override fun addPropertySource(source: PropertySource<*>): ConfigService = this

        override fun removePropertySource(source: PropertySource<*>): ConfigService = this

        override val level: ConfigLevel = ConfigLevel.APP
        override val parent: ConfigService? = null

        override fun getActiveProfile(): String = "test"

        override fun getAppName(): String = "test-app"

        override fun getConfigLocation(): Path = Path(".")

        override fun getPropertySources(includeParents: Boolean): PropertySources = throw NotImplementedError()

        override fun containsProperty(key: String): Boolean = false

        override fun <T : Any> getProperty(
            key: String,
            targetType: KClass<T>,
            defaultValue: T?,
        ): T? = defaultValue

        override fun getPropertyAsString(
            key: String,
            defaultValue: String?,
        ): String? = defaultValue

        override fun <T : Any> getRequiredProperty(
            key: String,
            targetType: KClass<T>,
            defaultValue: T?,
        ): T = defaultValue ?: throw IllegalStateException("No value for $key")

        override fun getRequiredPropertyAsString(
            key: String,
            defaultValue: String?,
        ): String = defaultValue ?: throw IllegalStateException("No value for $key")

        override fun getAllProperties(): Map<String, Any> = emptyMap()

        override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = emptyMap()

        override fun getSubProperties(
            prefixes: Set<String>,
            stripPrefix: Boolean,
        ): Map<String, Any> = emptyMap()

        override fun getSubPropertiesAsString(
            prefixes: Set<String>,
            stripPrefix: Boolean,
            redact: Boolean,
        ): Map<String, String> = emptyMap()

        override fun getNamespace(): String = "test"
    }

    private class TestTenantConfigService(
        override val parent: AppConfigService,
    ) : TenantConfigService {
        override val configLevel: ConfigLevel = ConfigLevel.TENANT

        override fun addPropertySource(source: PropertySource<*>): ConfigService = this

        override fun removePropertySource(source: PropertySource<*>): ConfigService = this

        override val level: ConfigLevel = ConfigLevel.TENANT

        override fun getActiveProfile(): String = "test"

        override fun getAppName(): String = "test-app"

        override fun getConfigLocation(): Path = Path(".")

        override fun getPropertySources(includeParents: Boolean): PropertySources = throw NotImplementedError()

        override fun containsProperty(key: String): Boolean = false

        override fun <T : Any> getProperty(
            key: String,
            targetType: KClass<T>,
            defaultValue: T?,
        ): T? = defaultValue

        override fun getPropertyAsString(
            key: String,
            defaultValue: String?,
        ): String? = defaultValue

        override fun <T : Any> getRequiredProperty(
            key: String,
            targetType: KClass<T>,
            defaultValue: T?,
        ): T = defaultValue ?: throw IllegalStateException("No value for $key")

        override fun getRequiredPropertyAsString(
            key: String,
            defaultValue: String?,
        ): String = defaultValue ?: throw IllegalStateException("No value for $key")

        override fun getAllProperties(): Map<String, Any> = emptyMap()

        override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = emptyMap()

        override fun getSubProperties(
            prefixes: Set<String>,
            stripPrefix: Boolean,
        ): Map<String, Any> = emptyMap()

        override fun getSubPropertiesAsString(
            prefixes: Set<String>,
            stripPrefix: Boolean,
            redact: Boolean,
        ): Map<String, String> = emptyMap()

        override fun getNamespace(): String = "test"
    }

    private class TestPrincipalConfigService(
        override val parent: TenantConfigService,
    ) : PrincipalConfigService {
        override val configLevel: ConfigLevel = ConfigLevel.PRINCIPAL

        override fun addPropertySource(source: PropertySource<*>): ConfigService = this

        override fun removePropertySource(source: PropertySource<*>): ConfigService = this

        override val level: ConfigLevel = ConfigLevel.PRINCIPAL

        override fun getActiveProfile(): String = "test"

        override fun getAppName(): String = "test-app"

        override fun getConfigLocation(): Path = Path(".")

        override fun getPropertySources(includeParents: Boolean): PropertySources = throw NotImplementedError()

        override fun containsProperty(key: String): Boolean = false

        override fun <T : Any> getProperty(
            key: String,
            targetType: KClass<T>,
            defaultValue: T?,
        ): T? = defaultValue

        override fun getPropertyAsString(
            key: String,
            defaultValue: String?,
        ): String? = defaultValue

        override fun <T : Any> getRequiredProperty(
            key: String,
            targetType: KClass<T>,
            defaultValue: T?,
        ): T = defaultValue ?: throw IllegalStateException("No value for $key")

        override fun getRequiredPropertyAsString(
            key: String,
            defaultValue: String?,
        ): String = defaultValue ?: throw IllegalStateException("No value for $key")

        override fun getAllProperties(): Map<String, Any> = emptyMap()

        override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = emptyMap()

        override fun getSubProperties(
            prefixes: Set<String>,
            stripPrefix: Boolean,
        ): Map<String, Any> = emptyMap()

        override fun getSubPropertiesAsString(
            prefixes: Set<String>,
            stripPrefix: Boolean,
            redact: Boolean,
        ): Map<String, String> = emptyMap()

        override fun getNamespace(): String = "test"
    }

    @Test
    fun appPropertyReturnsAppConfigService() {
        val appConfig = TestAppConfigService()
        val tenantConfig = TestTenantConfigService(appConfig)
        val principalConfig = TestPrincipalConfigService(tenantConfig)
        val contextConfig = ContextConfigImpl(appConfig, tenantConfig, principalConfig)

        assertSame(appConfig, contextConfig.app)
    }

    @Test
    fun tenantPropertyReturnsTenantConfigService() {
        val appConfig = TestAppConfigService()
        val tenantConfig = TestTenantConfigService(appConfig)
        val principalConfig = TestPrincipalConfigService(tenantConfig)
        val contextConfig = ContextConfigImpl(appConfig, tenantConfig, principalConfig)

        assertSame(tenantConfig, contextConfig.tenant)
    }

    @Test
    fun principalPropertyReturnsPrincipalConfigService() {
        val appConfig = TestAppConfigService()
        val tenantConfig = TestTenantConfigService(appConfig)
        val principalConfig = TestPrincipalConfigService(tenantConfig)
        val contextConfig = ContextConfigImpl(appConfig, tenantConfig, principalConfig)

        assertSame(principalConfig, contextConfig.principal)
    }

    @Test
    fun confReturnsAppServiceForAppLevel() {
        val appConfig = TestAppConfigService()
        val tenantConfig = TestTenantConfigService(appConfig)
        val principalConfig = TestPrincipalConfigService(tenantConfig)
        val contextConfig = ContextConfigImpl(appConfig, tenantConfig, principalConfig)

        assertSame(appConfig, contextConfig.conf(ConfigLevel.APP))
    }

    @Test
    fun confReturnsTenantServiceForTenantLevel() {
        val appConfig = TestAppConfigService()
        val tenantConfig = TestTenantConfigService(appConfig)
        val principalConfig = TestPrincipalConfigService(tenantConfig)
        val contextConfig = ContextConfigImpl(appConfig, tenantConfig, principalConfig)

        assertSame(tenantConfig, contextConfig.conf(ConfigLevel.TENANT))
    }

    @Test
    fun confReturnsPrincipalServiceForPrincipalLevel() {
        val appConfig = TestAppConfigService()
        val tenantConfig = TestTenantConfigService(appConfig)
        val principalConfig = TestPrincipalConfigService(tenantConfig)
        val contextConfig = ContextConfigImpl(appConfig, tenantConfig, principalConfig)

        assertSame(principalConfig, contextConfig.conf(ConfigLevel.PRINCIPAL))
    }
}

class SessionExecutionTest {
    // Test implementation of SessionExecution to exercise the default isAnonymous() method
    private class TestSessionExecution(
        private val testSessionContext: com.sphereon.di.session.SessionContext,
    ) : SessionExecution {
        override val sessionContextManager: com.sphereon.di.session.SessionContextManager
            get() = throw NotImplementedError("Not needed for this test")
        override val sessionContext: com.sphereon.di.session.SessionContext = testSessionContext
        override val log: com.sphereon.core.api.log.SessionLogService
            get() = throw NotImplementedError("Not needed for this test")
        override val conf: ContextConfig
            get() = throw NotImplementedError("Not needed for this test")
    }

    // Custom UserContext for non-anonymous testing
    private object TestUserContext : com.sphereon.di.context.UserContext {
        override val id: String = "test-tenant:test-principal:test"
        override val tenant: com.sphereon.di.context.TenantContextData =
            object : com.sphereon.di.context.TenantContextData {
                override val tenantId = "test-tenant"
            }
        override val principal: Any? = "test-principal"
        override val secureDetails: com.sphereon.di.context.SecuredTenantContextDetails? = null
    }

    // Custom SessionContext for non-anonymous testing
    private class TestSessionContext(
        override val sessionId: String,
        override val context: com.sphereon.di.context.UserContext,
    ) : com.sphereon.di.session.SessionContext

    @Test
    fun isAnonymousReturnsFalseForNonAnonymousSession() {
        val sessionContext =
            TestSessionContext(
                sessionId = "test-session",
                context = TestUserContext,
            )
        val sessionExecution = TestSessionExecution(sessionContext)

        // Non-anonymous session should return false
        assertEquals(false, sessionExecution.isAnonymous())
    }

    @Test
    fun isAnonymousReturnsTrueForNoOpSessionContext() {
        // NoOpSessionContext is always anonymous
        val sessionExecution = TestSessionExecution(com.sphereon.di.context.NoOpSessionContext)

        assertEquals(true, sessionExecution.isAnonymous())
    }

    @Test
    fun isAnonymousReturnsTrueForAnonymousSession() {
        // Create anonymous session using createAnonymousSessionContext
        val anonymousSession =
            com.sphereon.di.context
                .createAnonymousSessionContext("<anonymous>")
        val sessionExecution = TestSessionExecution(anonymousSession)

        assertEquals(true, sessionExecution.isAnonymous())
    }
}

class CoreApiContextExtensionGraphTest {
    // Test implementation that implements both interfaces
    private class TestCoreApiContextGraph :
        CoreApiContextExtensionGraph,
        com.sphereon.di.context.UserContextGraph {
        // CoreApiContextExtensionGraph properties
        override val logManager: com.sphereon.core.api.log.UserContextLogManager
            get() = throw NotImplementedError("Not needed for this test")
        override val conf: ContextConfig
            get() = throw NotImplementedError("Not needed for this test")
        override val commandExecutor: com.sphereon.core.api.session.CommandExecutor
            get() = throw NotImplementedError("Not needed for this test")

        // UserContextGraph properties (sessionContextManager satisfies both interfaces)
        override val sessionContextManager: com.sphereon.di.session.SessionContextManager
            get() = throw NotImplementedError("Not needed for this test")
        override val instance: com.sphereon.di.context.UserContextInstance
            get() = throw NotImplementedError("Not needed for this test")
        override val userContext: com.sphereon.di.context.UserContext
            get() = throw NotImplementedError("Not needed for this test")
        override val contextScopedInstances: Set<software.amazon.app.platform.scope.Scoped>
            get() = throw NotImplementedError("Not needed for this test")
        override val contextScopeCoroutineScopeScoped: software.amazon.app.platform.scope.coroutine.CoroutineScopeScoped
            get() = throw NotImplementedError("Not needed for this test")
    }

    @Test
    fun componentImplementsBothInterfaces() {
        val graph = TestCoreApiContextGraph()

        // Verifies the merged graph is accessible as both types
        val asContext: CoreApiContextExtensionGraph = graph
        val asUser: com.sphereon.di.context.UserContextGraph = graph
        assertSame(asContext as Any, asUser as Any)
    }
}

class CoreApiContextExtensionFunctionsTest {
    // Test implementation for extension function tests
    private class TestUserContextGraph :
        com.sphereon.di.context.UserContextGraph,
        CoreApiContextExtensionGraph {
        // CoreApiContextExtensionGraph properties
        override val logManager: com.sphereon.core.api.log.UserContextLogManager
            get() = throw NotImplementedError("Not needed for this test")
        override val conf: ContextConfig
            get() = throw NotImplementedError("Not needed for this test")
        override val commandExecutor: com.sphereon.core.api.session.CommandExecutor
            get() = throw NotImplementedError("Not needed for this test")

        // UserContextGraph properties (sessionContextManager satisfies both interfaces)
        override val sessionContextManager: com.sphereon.di.session.SessionContextManager
            get() = throw NotImplementedError("Not needed for this test")
        override val instance: com.sphereon.di.context.UserContextInstance
            get() = throw NotImplementedError("Not needed for this test")
        override val userContext: com.sphereon.di.context.UserContext
            get() = throw NotImplementedError("Not needed for this test")
        override val contextScopedInstances: Set<software.amazon.app.platform.scope.Scoped>
            get() = throw NotImplementedError("Not needed for this test")
        override val contextScopeCoroutineScopeScoped: software.amazon.app.platform.scope.coroutine.CoroutineScopeScoped
            get() = throw NotImplementedError("Not needed for this test")
    }

    private class TestUserContextInstance(
        override val graph: com.sphereon.di.context.UserContextGraph,
    ) : com.sphereon.di.context.UserContextInstance {
        override val contextId: String = "test-context"
        override val context: com.sphereon.di.context.UserContext
            get() = throw NotImplementedError("Not needed for this test")
        override val scope: software.amazon.app.platform.scope.Scope
            get() = throw NotImplementedError("Not needed for this test")
        override val userContextManager: com.sphereon.di.context.UserContextManager
            get() = throw NotImplementedError("Not needed for this test")
        override val sessionContextManager: com.sphereon.di.session.SessionContextManager
            get() = throw NotImplementedError("Not needed for this test")

        override fun <T : Any> getService(id: String): T = throw NotImplementedError("Not needed for this test")

        override fun addService(
            id: String,
            service: Any,
        ): software.amazon.app.platform.scope.Scope = throw NotImplementedError("Not needed for this test")

        override fun isCurrentlyActive(): Boolean = false

        override fun makeActive(): Boolean = false

        override fun destroy() {}

        override fun createSession(
            sessionId: String,
            makeActive: Boolean,
        ): com.sphereon.di.session.SessionInstance = throw NotImplementedError("Not needed for this test")

        override fun getOrCreateAnonymousSession(makeActive: Boolean): com.sphereon.di.session.SessionInstance = throw NotImplementedError("Not needed for this test")

        override fun getOrCreateBackgroundServiceSession(makeActive: Boolean): com.sphereon.di.session.SessionInstance = throw NotImplementedError("Not needed for this test")
    }

    @Test
    fun userContextComponentAsCoreApiContextGraphReturnsSelf() {
        val graph = TestUserContextGraph()

        val result = graph.asCoreApiContextGraph()

        assertSame(graph, result)
    }

    @Test
    fun userContextInstanceAsCoreApiContextComponentReturnsGraphCast() {
        val graph = TestUserContextGraph()
        val instance = TestUserContextInstance(graph)

        val result = instance.asCoreApiContextGraph()

        assertSame(graph, result)
    }
}
