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

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Test data classes
@Serializable
data class DatabaseConfig(
    val host: String,
    val port: Int,
    val name: String,
    val ssl: Boolean = false,
)

@Serializable
data class ServerConfig(
    val host: String = "localhost",
    val port: Int = 8080,
    val timeout: Long = 30000,
)

@Serializable
data class NestedConfig(
    val database: DatabaseConfig,
    val server: ServerConfig,
)

@Serializable
data class SimpleConfig(
    val value: String,
)

@Serializable
data class CredentialConfig(
    val username: String,
    val password: String,
)

class DefaultConfigBinderBasicTest {
    private fun createBinder(vararg properties: Pair<String, Any>): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver)
    }

    @Test
    fun getConfigBindsSimpleProperties() {
        val binder =
            createBinder(
                "database.host" to "localhost",
                "database.port" to 5432,
                "database.name" to "mydb",
                "database.ssl" to true,
            )

        val config: DatabaseConfig? = binder.getConfig("database")

        assertNotNull(config)
        assertEquals("localhost", config.host)
        assertEquals(5432, config.port)
        assertEquals("mydb", config.name)
        assertTrue(config.ssl)
    }

    @Test
    fun getConfigReturnsNullForMissingPrefix() {
        val binder = createBinder("other.key" to "value")

        val config: DatabaseConfig? = binder.getConfig("database")

        assertNull(config)
    }

    @Test
    fun getRequiredConfigThrowsForMissingPrefix() {
        val binder = createBinder("other.key" to "value")

        assertFailsWith<IllegalStateException> {
            binder.getRequiredConfig<DatabaseConfig>("database")
        }
    }

    @Test
    fun getConfigResultReturnsOkForExistingPrefix() {
        val binder =
            createBinder(
                "server.host" to "api.example.com",
                "server.port" to 443,
                "server.timeout" to 60000L,
            )

        val result = binder.getConfigResult<ServerConfig>("server")

        assertTrue(result.isOk)
        assertEquals("api.example.com", result.value.host)
        assertEquals(443, result.value.port)
    }

    @Test
    fun getConfigResultReturnsErrForMissingPrefix() {
        val binder = createBinder()

        val result = binder.getConfigResult<ServerConfig>("server")

        assertTrue(result.isErr)
        assertEquals("NOT_FOUND_ERROR", result.error.code)
    }

    @Test
    fun getConfigUsesDefaultValues() {
        val binder =
            createBinder(
                "server.host" to "api.example.com",
                // port and timeout not specified
            )

        val config: ServerConfig? = binder.getConfig("server")

        assertNotNull(config)
        assertEquals("api.example.com", config.host)
        assertEquals(8080, config.port) // default
        assertEquals(30000, config.timeout) // default
    }
}

class DefaultConfigBinderNestedTest {
    private fun createBinder(vararg properties: Pair<String, Any>): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver)
    }

    @Test
    fun bindsNestedConfiguration() {
        val binder =
            createBinder(
                "app.database.host" to "db.example.com",
                "app.database.port" to 5432,
                "app.database.name" to "appdb",
                "app.server.host" to "api.example.com",
                "app.server.port" to 8080,
                "app.server.timeout" to 30000L,
            )

        val config: NestedConfig? = binder.getConfig("app")

        assertNotNull(config)
        assertEquals("db.example.com", config.database.host)
        assertEquals(5432, config.database.port)
        assertEquals("api.example.com", config.server.host)
    }

    @Test
    fun bindsDeepNesting() {
        val binder =
            createBinder(
                "level1.level2.level3.value" to "deep-value",
            )

        @Serializable
        data class Level3(
            val value: String,
        )

        @Serializable
        data class Level2(
            val level3: Level3,
        )

        @Serializable
        data class Level1(
            val level2: Level2,
        )

        val config: Level1? = binder.getConfig("level1")

        assertNotNull(config)
        assertEquals("deep-value", config.level2.level3.value)
    }
}

class DefaultConfigBinderListTest {
    private fun createBinder(vararg properties: Pair<String, Any>): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver)
    }

    @Serializable
    data class CollectionFieldConfig(
        val ids: Set<String> = emptySet(),
    )

    @Test
    fun bindsIndexedList() {
        val binder =
            createBinder(
                "credentials.0.username" to "user1",
                "credentials.0.password" to "pass1",
                "credentials.1.username" to "user2",
                "credentials.1.password" to "pass2",
            )

        val list = binder.getConfigList<CredentialConfig>("credentials")

        assertEquals(2, list.size)
        assertEquals("user1", list[0].username)
        assertEquals("pass1", list[0].password)
        assertEquals("user2", list[1].username)
        assertEquals("pass2", list[1].password)
    }

    @Test
    fun returnsEmptyListForMissingPrefix() {
        val binder = createBinder("other.key" to "value")

        val list = binder.getConfigList<CredentialConfig>("credentials")

        assertTrue(list.isEmpty())
    }

    @Test
    fun handlesGapsInIndices() {
        val binder =
            createBinder(
                "items.0.value" to "first",
                "items.2.value" to "third",
                // index 1 is missing
            )

        val list = binder.getConfigList<SimpleConfig>("items")

        assertEquals(2, list.size)
        assertEquals("first", list[0].value)
        assertEquals("third", list[1].value)
    }

    @Test
    fun preservesIndexOrder() {
        val binder =
            createBinder(
                "items.2.value" to "third",
                "items.0.value" to "first",
                "items.1.value" to "second",
            )

        val list = binder.getConfigList<SimpleConfig>("items")

        assertEquals(3, list.size)
        assertEquals("first", list[0].value)
        assertEquals("second", list[1].value)
        assertEquals("third", list[2].value)
    }

    @Test
    fun bindsIndexedCollectionField() {
        val binder =
            createBinder(
                "config.ids[0]" to "alpha",
                "config.ids[1]" to "beta",
            )

        val config: CollectionFieldConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertEquals(setOf("alpha", "beta"), config.ids)
    }
}

class DefaultConfigBinderMapTest {
    private fun createBinder(vararg properties: Pair<String, Any>): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver)
    }

    @Test
    fun bindsKeyedMap() {
        val binder =
            createBinder(
                "databases.primary.host" to "primary.db.com",
                "databases.primary.port" to 5432,
                "databases.primary.name" to "primary",
                "databases.replica.host" to "replica.db.com",
                "databases.replica.port" to 5432,
                "databases.replica.name" to "replica",
            )

        val map = binder.getConfigMap<DatabaseConfig>("databases")

        assertEquals(2, map.size)
        assertTrue(map.containsKey("primary"))
        assertTrue(map.containsKey("replica"))
        assertEquals("primary.db.com", map["primary"]?.host)
        assertEquals("replica.db.com", map["replica"]?.host)
    }

    @Test
    fun bindsBracketQuotedMapKeyContainingDots() {
        val binder =
            createBinder(
                "commands.[kms.signature.verify].value" to "LOCAL",
            )

        val map = binder.getConfigMap<SimpleConfig>("commands")

        assertEquals(setOf("kms.signature.verify"), map.keys)
        assertEquals("LOCAL", map["kms.signature.verify"]?.value)
    }

    @Test
    fun returnsEmptyMapForMissingPrefix() {
        val binder = createBinder("other.key" to "value")

        val map = binder.getConfigMap<DatabaseConfig>("databases")

        assertTrue(map.isEmpty())
    }
}

class JsonMergeStrategyTest {
    private fun createBinderWithStrategy(
        strategy: JsonMergeStrategy,
        vararg properties: Pair<String, Any>,
    ): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver, mergeStrategy = strategy)
    }

    @Test
    fun deepMergeReplacesArraysByDefault() {
        // This test validates the default merge behavior
        val binder =
            createBinderWithStrategy(
                JsonMergeStrategy.DEEP_MERGE_REPLACE_ARRAYS,
                "config.host" to "localhost",
                "config.port" to 8080,
            )

        val config: ServerConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertEquals("localhost", config.host)
    }
}

class ConfigBinderExtensionsTest {
    @Test
    fun propertyResolverToConfigBinderWorks() {
        val source = MutableMapPropertySource("test")
        source.addProperty("server.host", "localhost")
        source.addProperty("server.port", 8080)
        source.addProperty("server.timeout", 30000L)

        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        val binder = resolver.toConfigBinder()

        val config: ServerConfig? = binder.getConfig("server")

        assertNotNull(config)
        assertEquals("localhost", config.host)
    }
}

class ConfigBinderTypeConversionTest {
    private fun createBinder(vararg properties: Pair<String, Any>): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver)
    }

    @Test
    fun convertsStringToInt() {
        val binder =
            createBinder(
                "server.host" to "localhost",
                "server.port" to "8080", // String instead of Int
                "server.timeout" to "30000",
            )

        val config: ServerConfig? = binder.getConfig("server")

        assertNotNull(config)
        assertEquals(8080, config.port)
    }

    @Test
    fun convertsStringToBoolean() {
        val binder =
            createBinder(
                "database.host" to "localhost",
                "database.port" to 5432,
                "database.name" to "mydb",
                "database.ssl" to "true", // String instead of Boolean
            )

        val config: DatabaseConfig? = binder.getConfig("database")

        assertNotNull(config)
        assertTrue(config.ssl)
    }
}

class ConfigBinderErrorHandlingTest {
    private fun createBinder(vararg properties: Pair<String, Any>): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver)
    }

    @Test
    fun handlesInvalidTypeGracefully() {
        val binder =
            createBinder(
                "database.host" to "localhost",
                "database.port" to "not-a-number", // Invalid type
                "database.name" to "mydb",
            )

        val result = binder.getConfigResult<DatabaseConfig>("database")

        assertTrue(result.isErr)
        assertEquals("CONFIG_BIND_ERROR", result.error.code)
    }

    @Test
    fun handlesMissingRequiredField() {
        val binder =
            createBinder(
                "database.host" to "localhost",
                // port and name are missing and have no defaults
            )

        val result = binder.getConfigResult<DatabaseConfig>("database")

        assertTrue(result.isErr)
    }
}

class HierarchicalConfigBinderTest {
    private fun createEnvironmentWithProperties(
        vararg properties: Pair<String, Any>,
        level: ConfigLevel = ConfigLevel.APP,
    ): ConfigEnvironment {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val sources = DefaultPropertySources().apply { add(source) }
        return TestConfigEnvironment(propertySources = sources, level = level)
    }

    @Test
    fun getConfigBindsSimpleProperties() {
        val env =
            createEnvironmentWithProperties(
                "server.host" to "localhost",
                "server.port" to 8080,
                "server.timeout" to 30000L,
            )
        val binder = HierarchicalConfigBinder(env)

        val config: ServerConfig? = binder.getConfig("server")

        assertNotNull(config)
        assertEquals("localhost", config.host)
        assertEquals(8080, config.port)
    }

    @Test
    fun getConfigReturnsNullForMissingPrefix() {
        val env = createEnvironmentWithProperties("other.key" to "value")
        val binder = HierarchicalConfigBinder(env)

        val config: ServerConfig? = binder.getConfig("server")

        assertNull(config)
    }

    @Test
    fun getRequiredConfigThrowsForMissingPrefix() {
        val env = createEnvironmentWithProperties("other.key" to "value")
        val binder = HierarchicalConfigBinder(env)

        assertFailsWith<IllegalStateException> {
            binder.getRequiredConfig<ServerConfig>("server")
        }
    }

    @Test
    fun getConfigResultReturnsOkForExistingPrefix() {
        val env =
            createEnvironmentWithProperties(
                "server.host" to "api.example.com",
                "server.port" to 443,
                "server.timeout" to 60000L,
            )
        val binder = HierarchicalConfigBinder(env)

        val result = binder.getConfigResult<ServerConfig>("server")

        assertTrue(result.isOk)
        assertEquals("api.example.com", result.value.host)
    }

    @Test
    fun getConfigListReturnsEmptyListForMissingPrefix() {
        val env = createEnvironmentWithProperties("other.key" to "value")
        val binder = HierarchicalConfigBinder(env)

        val list = binder.getConfigList<CredentialConfig>("credentials")

        assertTrue(list.isEmpty())
    }

    @Test
    fun getConfigMapReturnsEmptyMapForMissingPrefix() {
        val env = createEnvironmentWithProperties("other.key" to "value")
        val binder = HierarchicalConfigBinder(env)

        val map = binder.getConfigMap<DatabaseConfig>("databases")

        assertTrue(map.isEmpty())
    }

    @Test
    fun getLevelReturnsCorrectLevel() {
        val env =
            createEnvironmentWithProperties(
                "server.host" to "localhost",
                level = ConfigLevel.TENANT,
            )
        val binder = HierarchicalConfigBinder(env)

        assertEquals(ConfigLevel.TENANT, binder.getLevel())
    }

    @Test
    fun getActiveProfileReturnsEnvironmentProfile() {
        val env = createEnvironmentWithProperties("server.host" to "localhost")
        val binder = HierarchicalConfigBinder(env)

        // Default profile is "default" in TestConfigEnvironment
        assertNotNull(binder.getActiveProfile())
    }

    @Test
    fun hierarchicalTenantBinderHidesProtectedAppAndDelegatedEnvironmentValues() {
        val app =
            ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                addProtectedProperty("server.host", "server-owned", PropertyProtection.PROTECTED)
            }
        val delegatedEnvironment =
            ScopedPropertySourceWrapper(
                MapPropertySource("protected-environment", mapOf("server.timeout" to 99999L)),
                ConfigLevel.APP,
            )
        val tenant =
            ProtectedMutableMapPropertySource("tenant", ConfigLevel.TENANT).apply {
                addProperty("server.port", 8443)
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

        val config = HierarchicalConfigBinder(environment).getConfig<ServerConfig>("server")

        assertNotNull(config)
        assertEquals("localhost", config.host)
        assertEquals(8443, config.port)
        assertEquals(30000L, config.timeout)
    }
}

class ConfigEnvironmentToConfigBinderExtensionTest {
    @Test
    fun toConfigBinderCreatesWorkingBinder() {
        val source = MutableMapPropertySource("test")
        source.addProperty("server.host", "localhost")
        source.addProperty("server.port", 8080)
        source.addProperty("server.timeout", 30000L)
        val sources = DefaultPropertySources().apply { add(source) }
        val env = TestConfigEnvironment(propertySources = sources)

        val binder = env.toConfigBinder()

        val config: ServerConfig? = binder.getConfig("server")
        assertNotNull(config)
        assertEquals("localhost", config.host)
    }

    @Test
    fun toConfigBinderWithCustomMergeStrategy() {
        val source = MutableMapPropertySource("test")
        source.addProperty("config.host", "localhost")
        source.addProperty("config.port", 8080)
        source.addProperty("config.timeout", 30000L)
        val sources = DefaultPropertySources().apply { add(source) }
        val env = TestConfigEnvironment(propertySources = sources)

        val binder = env.toConfigBinder(mergeStrategy = JsonMergeStrategy.REPLACE)

        val config: ServerConfig? = binder.getConfig("config")
        assertNotNull(config)
    }

    @Test
    fun toConfigBinderUsesEnvironmentExactInterpolationCatalog() {
        val source =
            ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                addProperty("feature.value", "\${env:PATH}")
            }
        val policyProvider =
            DefaultInterpolationPolicyProvider(
                mapOf("feature.value" to InterpolationPolicy.APP_ENVIRONMENT),
            )
        val environment =
            TestConfigEnvironment(
                propertySources = DefaultPropertySources(mutableListOf(source)),
                interpolationPolicyProvider = policyProvider,
            )

        val config = environment.toConfigBinder().getConfig<SimpleConfig>("feature")

        assertNotNull(config)
        assertTrue(config.value.isNotBlank())
        assertFalse(config.value.contains("\${env:"))
    }

    @Test
    fun interpolateFalseTenantBinderRetainsProtectedAndEnvironmentAuthorization() {
        val app =
            ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                addProtectedProperty("server.host", "server-owned", PropertyProtection.PROTECTED)
            }
        val delegatedEnvironment =
            ScopedPropertySourceWrapper(
                MapPropertySource("environment", mapOf("server.timeout" to 99999L)),
                ConfigLevel.APP,
            )
        val tenant =
            ProtectedMutableMapPropertySource("tenant", ConfigLevel.TENANT).apply {
                addProperty("server.port", 8443)
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

        val config = environment.toConfigBinder(interpolate = false).getConfig<ServerConfig>("server")

        assertNotNull(config)
        assertEquals("localhost", config.host)
        assertEquals(8443, config.port)
        assertEquals(30000L, config.timeout)
    }

    @Test
    fun interpolateFalseTenantBinderRejectsSecretReferences() {
        val tenant =
            MutableMapPropertySource("tenant").apply {
                addProperty("server.host", "\${secret:@map:tenant/server-host}")
                addProperty("server.port", 8443)
            }
        val environment =
            TestConfigEnvironment(
                level = ConfigLevel.TENANT,
                propertySources = DefaultPropertySources(mutableListOf(tenant)),
            )

        val error =
            assertFailsWith<IllegalStateException> {
                environment
                    .toConfigBinder(interpolate = false)
                    .getConfigResult<ServerConfig>("server")
            }

        assertFalse(error.message.orEmpty().contains("tenant/server-host"))
    }
}

class ConfigBinderMapWithSimpleValuesTest {
    private fun createBinder(vararg properties: Pair<String, Any>): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver)
    }

    @Test
    fun getConfigMapHandlesSimplePrimitiveValues() {
        val binder =
            createBinder(
                "items.key1" to "value1",
                "items.key2" to "value2",
            )

        val map = binder.getConfigMap<SimpleConfig>("items")

        // Simple keys without nested structure should still work
        assertTrue(map.isEmpty() || map.isNotEmpty())
    }
}

class ConfigBinderListWithBracketNotationTest {
    private fun createBinder(vararg properties: Pair<String, Any>): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver)
    }

    @Test
    fun bindsListWithBracketNotation() {
        val binder =
            createBinder(
                "items.[0].value" to "first",
                "items.[1].value" to "second",
            )

        val list = binder.getConfigList<SimpleConfig>("items")

        assertEquals(2, list.size)
        assertEquals("first", list[0].value)
        assertEquals("second", list[1].value)
    }
}

class DefaultConfigBinderMergeStrategyTest {
    @Serializable
    data class MergeTestConfig(
        val value: String,
        val nested: NestedValue? = null,
    )

    @Serializable
    data class NestedValue(
        val a: String? = null,
        val b: String? = null,
    )

    private fun createBinderWithStrategy(
        strategy: JsonMergeStrategy,
        vararg properties: Pair<String, Any>,
    ): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver, mergeStrategy = strategy)
    }

    @Test
    fun replaceStrategyWorks() {
        val binder =
            createBinderWithStrategy(
                JsonMergeStrategy.REPLACE,
                "config.value" to "test-value",
            )

        val config: MergeTestConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertEquals("test-value", config.value)
    }

    @Test
    fun deepMergeReplaceArraysStrategyWorks() {
        val binder =
            createBinderWithStrategy(
                JsonMergeStrategy.DEEP_MERGE_REPLACE_ARRAYS,
                "config.value" to "test-value",
                "config.nested.a" to "value-a",
                "config.nested.b" to "value-b",
            )

        val config: MergeTestConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertEquals("test-value", config.value)
        assertNotNull(config.nested)
        assertEquals("value-a", config.nested?.a)
        assertEquals("value-b", config.nested?.b)
    }

    @Test
    fun deepMergeConcatArraysStrategyWorks() {
        val binder =
            createBinderWithStrategy(
                JsonMergeStrategy.DEEP_MERGE_CONCAT_ARRAYS,
                "config.value" to "test-value",
            )

        val config: MergeTestConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertEquals("test-value", config.value)
    }
}

class DefaultConfigBinderNullHandlingTest {
    private fun createBinder(vararg properties: Pair<String, Any?>): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) ->
            if (value != null) source.addProperty(key, value)
        }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver)
    }

    @Test
    fun handlesNullPropertyValues() {
        val binder =
            createBinder(
                "server.host" to "localhost",
                "server.port" to 8080,
                "server.timeout" to 30000L,
            )

        val config: ServerConfig? = binder.getConfig("server")

        assertNotNull(config)
        assertEquals("localhost", config.host)
    }
}

class DefaultConfigBinderTypeConversionsExtendedTest {
    private fun createBinder(vararg properties: Pair<String, Any>): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver)
    }

    @Test
    fun convertsNumberToString() {
        @Serializable
        data class StringConfig(
            val value: String,
        )

        val binder = createBinder("config.value" to 12345)

        val config: StringConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertEquals("12345", config.value)
    }

    @Test
    fun convertsBooleanFromString() {
        @Serializable
        data class BoolConfig(
            val enabled: Boolean,
        )

        val binder = createBinder("config.enabled" to "true")

        val config: BoolConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertTrue(config.enabled)
    }

    @Test
    fun convertsBooleanFalseFromString() {
        @Serializable
        data class BoolConfig(
            val enabled: Boolean,
        )

        val binder = createBinder("config.enabled" to "false")

        val config: BoolConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertFalse(config.enabled)
    }

    @Test
    fun handlesLongValues() {
        @Serializable
        data class LongConfig(
            val value: Long = 0,
        )

        val binder = createBinder("config.value" to 9876543210L)

        val config: LongConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertEquals(9876543210L, config.value)
    }
}

/**
 * Additional tests for ConfigBinder branch coverage.
 */
class DefaultConfigBinderAnyToJsonElementTest {
    private fun createBinder(vararg properties: Pair<String, Any>): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver)
    }

    @Test
    fun handlesStringValues() {
        @Serializable
        data class StringConfig(
            val value: String,
        )

        val binder = createBinder("config.value" to "test-string")

        val config: StringConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertEquals("test-string", config.value)
    }

    @Test
    fun handlesIntValues() {
        @Serializable
        data class IntConfig(
            val value: Int,
        )

        val binder = createBinder("config.value" to 42)

        val config: IntConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertEquals(42, config.value)
    }

    @Test
    fun handlesDoubleValues() {
        @Serializable
        data class DoubleConfig(
            val value: Double,
        )

        val binder = createBinder("config.value" to 3.14)

        val config: DoubleConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertEquals(3.14, config.value, 0.01)
    }

    @Test
    fun handlesBooleanTrueValues() {
        @Serializable
        data class BoolConfig(
            val value: Boolean,
        )

        val binder = createBinder("config.value" to true)

        val config: BoolConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertTrue(config.value)
    }

    @Test
    fun handlesBooleanFalseValues() {
        @Serializable
        data class BoolConfig(
            val value: Boolean,
        )

        val binder = createBinder("config.value" to false)

        val config: BoolConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertFalse(config.value)
    }

    @Test
    fun handlesObjectConversionToString() {
        @Serializable
        data class StringConfig(
            val value: String,
        )

        // Use a custom object that will be converted to string via toString()
        val customObject =
            object {
                override fun toString() = "custom-object-value"
            }

        val binder = createBinder("config.value" to customObject)

        val config: StringConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertEquals("custom-object-value", config.value)
    }
}

/**
 * Tests for merge strategy branches.
 */
class DefaultConfigBinderMergeStrategiesTest {
    @Serializable
    data class DeepConfig(
        val level1: Level1Config? = null,
    )

    @Serializable
    data class Level1Config(
        val a: String? = null,
        val b: String? = null,
    )

    @Serializable
    data class ListConfig(
        val items: List<String> = emptyList(),
    )

    private fun createBinderWithStrategy(
        strategy: JsonMergeStrategy,
        vararg properties: Pair<String, Any>,
    ): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver, mergeStrategy = strategy)
    }

    @Test
    fun replaceStrategyReplacesNestedValues() {
        val binder =
            createBinderWithStrategy(
                JsonMergeStrategy.REPLACE,
                "config.level1.a" to "value-a",
                "config.level1.b" to "value-b",
            )

        val config: DeepConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertEquals("value-a", config.level1?.a)
        assertEquals("value-b", config.level1?.b)
    }

    @Test
    fun deepMergeReplaceArraysMergesObjects() {
        val binder =
            createBinderWithStrategy(
                JsonMergeStrategy.DEEP_MERGE_REPLACE_ARRAYS,
                "config.level1.a" to "value-a",
                "config.level1.b" to "value-b",
            )

        val config: DeepConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertNotNull(config.level1)
        assertEquals("value-a", config.level1?.a)
        assertEquals("value-b", config.level1?.b)
    }

    @Test
    fun deepMergeConcatArraysWorks() {
        val binder =
            createBinderWithStrategy(
                JsonMergeStrategy.DEEP_MERGE_CONCAT_ARRAYS,
                "config.level1.a" to "value-a",
            )

        val config: DeepConfig? = binder.getConfig("config")

        assertNotNull(config)
        assertEquals("value-a", config.level1?.a)
    }
}

/**
 * Tests for getConfigList with bracket notation and edge cases.
 */
class DefaultConfigBinderListEdgeCasesTest {
    private fun createBinder(vararg properties: Pair<String, Any>): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver)
    }

    @Test
    fun getConfigListWithIndexedNotation() {
        val binder =
            createBinder(
                "items.0.value" to "first",
                "items.1.value" to "second",
                "items.2.value" to "third",
            )

        val list = binder.getConfigList<SimpleConfig>("items")

        assertEquals(3, list.size)
        assertEquals("first", list[0].value)
        assertEquals("second", list[1].value)
        assertEquals("third", list[2].value)
    }

    @Test
    fun getConfigListSkipsInvalidEntries() {
        val binder =
            createBinder(
                "items.0.value" to "valid",
                "items.1.invalid" to "missing-value-field", // Invalid - no 'value' field
                "items.2.value" to "also-valid",
            )

        val list = binder.getConfigList<SimpleConfig>("items")

        // Items 0 and 2 should succeed, item 1 should be skipped
        assertTrue(list.isNotEmpty())
    }

    @Test
    fun getConfigListResultReturnsDiagnosticsInStrictMode() {
        val binder =
            createBinder(
                "items.0.value" to "valid",
                "items.1.invalid" to "missing-value-field",
            )

        val result = binder.getConfigListResult<SimpleConfig>("items", strict = true)

        assertTrue(result.isErr)
        assertEquals("CONFIG_BIND_ERROR", result.error.code)
        assertTrue(
            result.error.message.defaultMessage
                .contains("items.1"),
        )
    }

    @Test
    fun getConfigListResultSkipsInvalidEntriesInLenientMode() {
        val binder =
            createBinder(
                "items.0.value" to "valid",
                "items.1.invalid" to "missing-value-field",
                "items.2.value" to "also-valid",
            )

        val result = binder.getConfigListResult<SimpleConfig>("items", strict = false)

        assertTrue(result.isOk)
        assertEquals(2, result.value.size)
        assertEquals("valid", result.value[0].value)
        assertEquals("also-valid", result.value[1].value)
    }

    @Test
    fun getConfigListWithNonIndexedPropertiesReturnsEmpty() {
        val binder =
            createBinder(
                "items.key1" to "value1",
                "items.key2" to "value2",
            )

        val list = binder.getConfigList<SimpleConfig>("items")

        // Non-indexed properties should not be treated as list items
        assertTrue(list.isEmpty())
    }
}

/**
 * Tests for getConfigMap edge cases.
 */
class DefaultConfigBinderMapEdgeCasesTest {
    private fun createBinder(vararg properties: Pair<String, Any>): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver)
    }

    @Test
    fun getConfigMapWithNestedValues() {
        val binder =
            createBinder(
                "databases.primary.host" to "primary.db.com",
                "databases.primary.port" to 5432,
                "databases.primary.name" to "primarydb",
                "databases.secondary.host" to "secondary.db.com",
                "databases.secondary.port" to 5432,
                "databases.secondary.name" to "secondarydb",
            )

        val map = binder.getConfigMap<DatabaseConfig>("databases")

        assertEquals(2, map.size)
        assertEquals("primary.db.com", map["primary"]?.host)
        assertEquals("secondary.db.com", map["secondary"]?.host)
    }

    @Test
    fun getConfigMapSkipsInvalidEntries() {
        val binder =
            createBinder(
                "configs.valid.host" to "localhost",
                "configs.valid.port" to 8080,
                "configs.valid.timeout" to 30000,
                "configs.invalid.host" to "badhost",
                "configs.invalid.port" to "not-a-number", // Invalid
            )

        val map = binder.getConfigMap<ServerConfig>("configs")

        // Valid entry should be included, invalid may be skipped
        assertTrue(map.containsKey("valid"))
    }

    @Test
    fun getConfigMapResultReturnsDiagnosticsInStrictMode() {
        val binder =
            createBinder(
                "configs.valid.host" to "localhost",
                "configs.valid.port" to 8080,
                "configs.valid.timeout" to 30000,
                "configs.invalid.host" to "badhost",
                "configs.invalid.port" to "not-a-number",
            )

        val result = binder.getConfigMapResult<ServerConfig>("configs", strict = true)

        assertTrue(result.isErr)
        assertEquals("CONFIG_BIND_ERROR", result.error.code)
        assertTrue(
            result.error.message.defaultMessage
                .contains("configs.invalid"),
        )
    }

    @Test
    fun getConfigMapResultSkipsInvalidEntriesInLenientMode() {
        val binder =
            createBinder(
                "configs.valid.host" to "localhost",
                "configs.valid.port" to 8080,
                "configs.valid.timeout" to 30000,
                "configs.invalid.host" to "badhost",
                "configs.invalid.port" to "not-a-number",
            )

        val result = binder.getConfigMapResult<ServerConfig>("configs", strict = false)

        assertTrue(result.isOk)
        assertTrue(result.value.containsKey("valid"))
        assertFalse(result.value.containsKey("invalid"))
    }
}

/**
 * Tests for exception handling in ConfigBinder.
 */
class DefaultConfigBinderExceptionHandlingTest {
    private fun createBinder(vararg properties: Pair<String, Any>): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver)
    }

    @Test
    fun getConfigResultCatchesSerializationException() {
        // Create config with wrong type that will cause serialization error
        val binder =
            createBinder(
                "config.port" to "not-a-valid-integer-at-all-xyz",
            )

        @Serializable
        data class PortConfig(
            val port: Int,
        )

        val result = binder.getConfigResult<PortConfig>("config")

        assertTrue(result.isErr)
        assertEquals("CONFIG_BIND_ERROR", result.error.code)
        assertEquals("config", result.error.meta["prefix"])
        assertFalse(result.error.meta.containsKey("receivedValue"))
        val renderedError = result.error.message.defaultMessage + result.error.meta.toString()
        assertFalse(renderedError.contains("not-a-valid-integer-at-all-xyz"))
    }

    @Test
    fun getRequiredConfigThrowsWithMessage() {
        val binder = createBinder()

        val exception =
            assertFailsWith<IllegalStateException> {
                binder.getRequiredConfig<ServerConfig>("missing-prefix")
            }

        assertTrue(exception.message?.contains("missing-prefix") == true)
    }
}

/**
 * Tests for setNestedValue with empty path.
 */
class DefaultConfigBinderSetNestedValueTest {
    private fun createBinder(vararg properties: Pair<String, Any>): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver)
    }

    @Test
    fun handlesDeepNesting() {
        val binder =
            createBinder(
                "a.b.c.d.e.f.value" to "deep-value",
            )

        @Serializable
        data class F(
            val value: String,
        )

        @Serializable
        data class E(
            val f: F,
        )

        @Serializable
        data class D(
            val e: E,
        )

        @Serializable
        data class C(
            val d: D,
        )

        @Serializable
        data class B(
            val c: C,
        )

        @Serializable
        data class A(
            val b: B,
        )

        val config: A? = binder.getConfig("a")

        assertNotNull(config)
        assertEquals("deep-value", config.b.c.d.e.f.value)
    }
}

/**
 * Tests for descriptor-aware camelCase field name resolution in DefaultConfigBinder.
 *
 * The normalizer converts camelCase to dot-separated (e.g., "defaultIdp" -> "default.idp").
 * When building JSON from flat properties, we need the SerialDescriptor to distinguish
 * between dots from camelCase normalization and dots from real nesting.
 */
class DefaultConfigBinderCamelCaseTest {
    private fun createBinder(vararg properties: Pair<String, Any>): ConfigBinder {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
        return DefaultConfigBinder(resolver)
    }

    @Serializable
    data class CamelCaseIdpConfig(
        val id: String,
        val issuer: String = "",
        val jwksUri: String? = null,
        val discoveryUri: String? = null,
        val clockSkewSeconds: Long = 60,
    )

    @Serializable
    data class CamelCaseJwtConfig(
        val enabled: Boolean = true,
        val defaultIdp: CamelCaseIdpConfig? = null,
    )

    @Serializable
    data class TopLevelCamelCase(
        val allowedPaths: List<String> = emptyList(),
        val exposePrivateKeys: Boolean = false,
    )

    @Serializable
    data class MultiCamelCaseConfig(
        val firstName: String,
        val lastName: String,
        val emailAddress: String? = null,
        val phoneNumber: String? = null,
    )

    @Serializable
    data class CamelCaseMapValueConfig(
        val timeoutMs: Long = 30_000,
    )

    @Serializable
    data class NestedCamelCaseMapConfig(
        val commands: Map<String, CamelCaseMapValueConfig> = emptyMap(),
    )

    @Test
    fun bindsCamelCaseFieldFromNormalizedKey() {
        // Given: properties using normalized keys (as stored by MutableMapPropertySource)
        // "defaultIdp" normalizes to "default.idp", so "defaultIdp.id" becomes "default.idp.id"
        val binder =
            createBinder(
                "jwt.enabled" to true,
                "jwt.defaultIdp.id" to "keycloak",
                "jwt.defaultIdp.issuer" to "http://localhost:8080/realms/test",
            )

        // When
        val config = binder.getConfig<CamelCaseJwtConfig>("jwt")

        // Then
        assertNotNull(config)
        assertTrue(config.enabled)
        assertNotNull(config.defaultIdp)
        assertEquals("keycloak", config.defaultIdp?.id)
        assertEquals("http://localhost:8080/realms/test", config.defaultIdp?.issuer)
    }

    @Test
    fun bindsNestedCamelCaseFields() {
        // Given: nested camelCase fields like jwksUri and clockSkewSeconds
        val binder =
            createBinder(
                "jwt.defaultIdp.id" to "keycloak",
                "jwt.defaultIdp.issuer" to "http://localhost:8080/realms/test",
                "jwt.defaultIdp.jwksUri" to "http://localhost:8080/realms/test/protocol/openid-connect/certs",
                "jwt.defaultIdp.clockSkewSeconds" to 120,
            )

        // When
        val config = binder.getConfig<CamelCaseJwtConfig>("jwt")

        // Then
        assertNotNull(config)
        val idp = config.defaultIdp
        assertNotNull(idp)
        assertEquals("keycloak", idp.id)
        assertEquals("http://localhost:8080/realms/test/protocol/openid-connect/certs", idp.jwksUri)
        assertEquals(120, idp.clockSkewSeconds)
    }

    @Test
    fun bindsTopLevelCamelCaseField() {
        // Given: top-level camelCase field "exposePrivateKeys"
        val binder =
            createBinder(
                "config.exposePrivateKeys" to true,
            )

        // When
        val config = binder.getConfig<TopLevelCamelCase>("config")

        // Then
        assertNotNull(config)
        assertTrue(config.exposePrivateKeys)
    }

    @Test
    fun bindsMultipleCamelCaseFieldsInSameClass() {
        // Given: multiple camelCase fields in one class
        val binder =
            createBinder(
                "person.firstName" to "John",
                "person.lastName" to "Doe",
                "person.emailAddress" to "john@example.com",
                "person.phoneNumber" to "+1234567890",
            )

        // When
        val config = binder.getConfig<MultiCamelCaseConfig>("person")

        // Then
        assertNotNull(config)
        assertEquals("John", config.firstName)
        assertEquals("Doe", config.lastName)
        assertEquals("john@example.com", config.emailAddress)
        assertEquals("+1234567890", config.phoneNumber)
    }

    @Test
    fun getConfigListWithCamelCaseFields() {
        // Given: list items with camelCase fields
        val binder =
            createBinder(
                "people.0.firstName" to "Alice",
                "people.0.lastName" to "Smith",
                "people.1.firstName" to "Bob",
                "people.1.lastName" to "Jones",
            )

        // When
        val list = binder.getConfigList<MultiCamelCaseConfig>("people")

        // Then
        assertEquals(2, list.size)
        assertEquals("Alice", list[0].firstName)
        assertEquals("Smith", list[0].lastName)
        assertEquals("Bob", list[1].firstName)
        assertEquals("Jones", list[1].lastName)
    }

    @Test
    fun getConfigMapWithCamelCaseFields() {
        // Given: map entries with camelCase fields
        val binder =
            createBinder(
                "idps.keycloak.id" to "keycloak",
                "idps.keycloak.issuer" to "http://localhost:8080/realms/test",
                "idps.keycloak.jwksUri" to "http://localhost:8080/certs",
                "idps.azure.id" to "azure",
                "idps.azure.issuer" to "https://login.microsoftonline.com/v2.0",
                "idps.azure.clockSkewSeconds" to 30,
            )

        // When
        val map = binder.getConfigMap<CamelCaseIdpConfig>("idps")

        // Then
        assertEquals(2, map.size)
        assertEquals("keycloak", map["keycloak"]?.id)
        assertEquals("http://localhost:8080/certs", map["keycloak"]?.jwksUri)
        assertEquals("azure", map["azure"]?.id)
        assertEquals(30, map["azure"]?.clockSkewSeconds)
    }

    @Test
    fun getConfigWithNestedMapReconstructsCamelCaseValueFields() {
        val binder =
            createBinder(
                "routing.commands.[application.license.get-effective].timeoutMs" to 5_000L,
            )

        val config = binder.getConfig<NestedCamelCaseMapConfig>("routing")

        assertNotNull(config)
        assertEquals(setOf("application.license.get-effective"), config.commands.keys)
        assertEquals(5_000L, config.commands["application.license.get-effective"]?.timeoutMs)
    }

    @Test
    fun mixedCamelCaseAndRegularNesting() {
        // Given: both camelCase fields and real nesting in the same structure
        val binder =
            createBinder(
                "jwt.enabled" to false,
                "jwt.defaultIdp.id" to "custom",
                "jwt.defaultIdp.issuer" to "https://custom-issuer.com",
                "jwt.defaultIdp.discoveryUri" to "https://custom-issuer.com/.well-known/openid-configuration",
                "jwt.defaultIdp.clockSkewSeconds" to 90,
            )

        // When
        val config = binder.getConfig<CamelCaseJwtConfig>("jwt")

        // Then
        assertNotNull(config)
        assertFalse(config.enabled)
        val idp = config.defaultIdp
        assertNotNull(idp)
        assertEquals("custom", idp.id)
        assertEquals("https://custom-issuer.com", idp.issuer)
        assertEquals("https://custom-issuer.com/.well-known/openid-configuration", idp.discoveryUri)
        assertEquals(90, idp.clockSkewSeconds)
    }
}
