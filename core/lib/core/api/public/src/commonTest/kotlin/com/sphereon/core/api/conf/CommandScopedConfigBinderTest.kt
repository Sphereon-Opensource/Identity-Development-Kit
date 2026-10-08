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

import com.sphereon.core.api.context.ContextConfig
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
data class TestHttpConfig(
    val timeout: TestTimeoutConfig? = null,
    val logging: TestLoggingConfig? = null,
    val baseUrl: String? = null,
)

@Serializable
data class TestTimeoutConfig(
    val connectMs: Long? = null,
    val requestMs: Long? = null,
)

@Serializable
data class TestLoggingConfig(
    val enabled: Boolean? = null,
    val tag: String? = null,
)

class CommandScopedConfigBinderGlobalOnlyTest {
    private fun createResolver(vararg properties: Pair<String, Any>): PropertyResolver {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        return PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
    }

    @Test
    fun resolvesFromGlobalWhenNoScopedProperties() {
        val resolver =
            createResolver(
                "http.client.timeout.connect.ms" to "30000",
                "http.client.timeout.request.ms" to "60000",
                "http.client.logging.enabled" to "true",
            )
        val binder =
            CommandScopedConfigBinder(
                resolver = resolver,
                scope = CommandConfigScope.fromCommandId("kms.keys.get"),
            )

        val config = binder.getConfig<TestHttpConfig>("http.client")
        assertNotNull(config)
        assertEquals(30000L, config.timeout?.connectMs)
        assertEquals(60000L, config.timeout?.requestMs)
        assertEquals(true, config.logging?.enabled)
    }

    @Test
    fun returnsNullWhenNoPropertiesFound() {
        val resolver = createResolver()
        val binder =
            CommandScopedConfigBinder(
                resolver = resolver,
                scope = CommandConfigScope.fromCommandId("kms.keys.get"),
            )

        val config = binder.getConfig<TestHttpConfig>("http.client")
        assertNull(config)
    }

    @Test
    fun getRequiredConfigThrowsWhenNotFound() {
        val resolver = createResolver()
        val binder =
            CommandScopedConfigBinder(
                resolver = resolver,
                scope = CommandConfigScope.GLOBAL,
            )

        assertFailsWith<IllegalStateException> {
            binder.getRequiredConfig<TestHttpConfig>("http.client")
        }
    }
}

class CommandScopedConfigBinderModuleOverrideTest {
    private fun createResolver(vararg properties: Pair<String, Any>): PropertyResolver {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        return PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
    }

    @Test
    fun moduleOverridesGlobalForSpecificFields() {
        val resolver =
            createResolver(
                "http.client.timeout.connect.ms" to "30000",
                "http.client.timeout.request.ms" to "60000",
                "http.client.logging.enabled" to "true",
                // Module override: KMS uses shorter connect timeout
                "cmd.kms.default.default.http.client.timeout.connect.ms" to "10000",
            )
        val binder =
            CommandScopedConfigBinder(
                resolver = resolver,
                scope = CommandConfigScope.fromCommandId("kms.keys.get"),
            )

        val config = binder.getConfig<TestHttpConfig>("http.client")
        assertNotNull(config)
        assertEquals(10000L, config.timeout?.connectMs, "Module override should win for connectMs")
        assertEquals(60000L, config.timeout?.requestMs, "Global should be kept for requestMs")
        assertEquals(true, config.logging?.enabled, "Global should be kept for logging")
    }

    @Test
    fun moduleOverrideDoesNotAffectOtherModules() {
        val resolver =
            createResolver(
                "http.client.timeout.connect.ms" to "30000",
                "cmd.kms.default.default.http.client.timeout.connect.ms" to "10000",
            )
        val binder =
            CommandScopedConfigBinder(
                resolver = resolver,
                scope = CommandConfigScope.fromCommandId("party.parties.create"),
            )

        val config = binder.getConfig<TestHttpConfig>("http.client")
        assertNotNull(config)
        assertEquals(30000L, config.timeout?.connectMs, "Other module should not see KMS override")
    }
}

class CommandScopedConfigBinderServiceOverrideTest {
    private fun createResolver(vararg properties: Pair<String, Any>): PropertyResolver {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        return PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
    }

    @Test
    fun serviceOverridesModuleAndGlobal() {
        val resolver =
            createResolver(
                "http.client.timeout.connect.ms" to "30000",
                "http.client.logging.tag" to "global",
                "cmd.kms.default.default.http.client.timeout.connect.ms" to "10000",
                "cmd.kms.default.default.http.client.logging.tag" to "kms",
                // Service override
                "cmd.kms.keys.default.http.client.timeout.connect.ms" to "5000",
            )
        val binder =
            CommandScopedConfigBinder(
                resolver = resolver,
                scope = CommandConfigScope.fromCommandId("kms.keys.get"),
            )

        val config = binder.getConfig<TestHttpConfig>("http.client")
        assertNotNull(config)
        assertEquals(5000L, config.timeout?.connectMs, "Service override should win")
        assertEquals("kms", config.logging?.tag, "Module override should be kept for tag")
    }
}

class CommandScopedConfigBinderCommandOverrideTest {
    private fun createResolver(vararg properties: Pair<String, Any>): PropertyResolver {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        return PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
    }

    @Test
    fun commandOverridesAllLevels() {
        val resolver =
            createResolver(
                "http.client.timeout.connect.ms" to "30000",
                "http.client.logging.enabled" to "true",
                "cmd.kms.default.default.http.client.timeout.connect.ms" to "10000",
                "cmd.kms.keys.default.http.client.timeout.connect.ms" to "5000",
                // Command-level override
                "cmd.kms.keys.get.http.client.timeout.connect.ms" to "2000",
                "cmd.kms.keys.get.http.client.logging.enabled" to "false",
            )
        val binder =
            CommandScopedConfigBinder(
                resolver = resolver,
                scope = CommandConfigScope.fromCommandId("kms.keys.get"),
            )

        val config = binder.getConfig<TestHttpConfig>("http.client")
        assertNotNull(config)
        assertEquals(2000L, config.timeout?.connectMs, "Command override should win")
        assertEquals(false, config.logging?.enabled, "Command override should win for logging")
    }

    @Test
    fun commandOverrideDoesNotAffectOtherCommandsInSameService() {
        val resolver =
            createResolver(
                "http.client.timeout.connect.ms" to "30000",
                "cmd.kms.keys.get.http.client.timeout.connect.ms" to "2000",
            )
        val binder =
            CommandScopedConfigBinder(
                resolver = resolver,
                scope = CommandConfigScope.fromCommandId("kms.keys.list"),
            )

        val config = binder.getConfig<TestHttpConfig>("http.client")
        assertNotNull(config)
        assertEquals(30000L, config.timeout?.connectMs, "Other command should not see 'get' override")
    }
}

class CommandScopedConfigBinderResultTest {
    private fun createResolver(vararg properties: Pair<String, Any>): PropertyResolver {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        return PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
    }

    @Test
    fun getConfigResultReturnsOkWhenFound() {
        val resolver =
            createResolver(
                "http.client.base.url" to "https://api.example.com",
            )
        val binder =
            CommandScopedConfigBinder(
                resolver = resolver,
                scope = CommandConfigScope.GLOBAL,
            )

        val result = binder.getConfigResult<TestHttpConfig>("http.client")
        assertTrue(result.isOk)
        assertEquals("https://api.example.com", result.value.baseUrl)
    }

    @Test
    fun getConfigResultReturnsErrWhenNotFound() {
        val resolver = createResolver()
        val binder =
            CommandScopedConfigBinder(
                resolver = resolver,
                scope = CommandConfigScope.GLOBAL,
            )

        val result = binder.getConfigResult<TestHttpConfig>("nonexistent")
        assertTrue(result.isErr)
    }
}

class CommandScopedConfigBinderAuthorizationTest {
    @Test
    fun interpolateFalseServiceBinderHidesProtectedAppAndDelegatedEnvironmentSources() {
        val app =
            ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                addProtectedProperty("http.client.base.url", "server-owned", PropertyProtection.PROTECTED)
            }
        val delegatedEnvironment =
            ScopedPropertySourceWrapper(
                MapPropertySource(
                    "environment",
                    mapOf("http.client.logging.tag" to "environment-owned"),
                ),
                ConfigLevel.APP,
            )
        val tenant =
            ProtectedMutableMapPropertySource("tenant", ConfigLevel.TENANT).apply {
                addProperty("http.client.timeout.connect.ms", "1500")
            }
        val environment =
            TestConfigEnvironment(
                level = ConfigLevel.TENANT,
                propertySources =
                    DefaultPropertySources(
                        mutableListOf(
                            delegatedEnvironment,
                            tenant,
                            app,
                        ),
                    ),
            )
        val service = TestConfigService(environment)

        val binder =
            service.toCommandScopedBinder(
                scope = CommandConfigScope.GLOBAL,
                interpolate = false,
            )
        val config = binder.getConfig<TestHttpConfig>("http.client")

        assertNotNull(config)
        assertEquals(1500L, config.timeout?.connectMs)
        assertNull(config.baseUrl)
        assertNull(config.logging?.tag)
    }
}

class CommandScopedConfigBinderPolicyPropagationTest {
    private fun environment(): TestConfigEnvironment {
        val source =
            ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                addProperty("feature.value", "\${env:PATH}")
            }
        return TestConfigEnvironment(
            level = ConfigLevel.APP,
            propertySources = DefaultPropertySources(mutableListOf(source)),
            interpolationPolicyProvider =
                DefaultInterpolationPolicyProvider(
                    mapOf("feature.value" to InterpolationPolicy.APP_ENVIRONMENT),
                ),
        )
    }

    @Test
    fun configServiceCommandBinderUsesEnvironmentPolicyProvider() {
        val service = TestConfigService(environment())

        val config =
            service
                .toCommandScopedBinder(CommandConfigScope.GLOBAL)
                .getConfig<SimpleConfig>("feature")

        assertNotNull(config)
        assertTrue(config.value.isNotBlank())
        assertTrue(!config.value.contains("\${env:"))
    }

    @Test
    fun contextCommandBinderUsesPrincipalEnvironmentPolicyProvider() {
        val delegate = TestConfigService(environment())
        val app = BinderPolicyTestAppConfigService(delegate)
        val tenant = BinderPolicyTestTenantConfigService(delegate, app)
        val principal = BinderPolicyTestPrincipalConfigService(delegate, tenant)
        val context =
            object : ContextConfig {
                override val app: AppConfigService = app
                override val tenant: TenantConfigService = tenant
                override val principal: PrincipalConfigService = principal

                override fun conf(level: ConfigLevel): ConfigService =
                    when (level) {
                        ConfigLevel.APP -> app
                        ConfigLevel.TENANT -> tenant
                        ConfigLevel.PRINCIPAL -> principal
                    }
            }

        val config =
            context
                .toCommandScopedBinder(CommandConfigScope.GLOBAL)
                .getConfig<SimpleConfig>("feature")

        assertNotNull(config)
        assertTrue(config.value.isNotBlank())
        assertTrue(!config.value.contains("\${env:"))
    }
}

private class BinderPolicyTestAppConfigService(
    delegate: ConfigService,
) : AppConfigService,
    ConfigService by delegate {
    override val parent: ConfigService?
        get() = null
    override val level: ConfigLevel
        get() = ConfigLevel.APP
    override val configLevel: ConfigLevel
        get() = ConfigLevel.APP
}

private class BinderPolicyTestTenantConfigService(
    delegate: ConfigService,
    override val parent: AppConfigService,
) : TenantConfigService,
    ConfigService by delegate {
    override val level: ConfigLevel
        get() = ConfigLevel.TENANT
    override val configLevel: ConfigLevel
        get() = ConfigLevel.TENANT
}

private class BinderPolicyTestPrincipalConfigService(
    delegate: ConfigService,
    override val parent: TenantConfigService,
) : PrincipalConfigService,
    ConfigService by delegate {
    override val level: ConfigLevel
        get() = ConfigLevel.PRINCIPAL
    override val configLevel: ConfigLevel
        get() = ConfigLevel.PRINCIPAL
}
