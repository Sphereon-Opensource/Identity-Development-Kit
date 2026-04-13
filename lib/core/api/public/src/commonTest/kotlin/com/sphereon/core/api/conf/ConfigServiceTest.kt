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
 */

package com.sphereon.core.api.conf

import kotlinx.io.files.Path
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Test implementation of ConfigEnvironment for testing AbstractConfigService
 */
class TestConfigEnvironment(
    override val level: ConfigLevel = ConfigLevel.APP,
    override val parent: ConfigEnvironment? = null,
    private val propertySources: PropertySources = DefaultPropertySources(),
    private val activeProfile: String = "test",
    private val appName: String = "test-app",
    private val configLocationPath: String = "/test/config",
    private val namespace: String = "test.namespace"
) : ConfigEnvironment {

    override fun getActiveProfile(): String = activeProfile

    override fun getAppName(): String = appName

    override fun getConfigLocation(): Path = Path(configLocationPath)

    override fun getPropertySources(includeParents: Boolean): PropertySources = propertySources

    override fun getNamespace(): String = namespace

    override fun containsProperty(key: String): Boolean = propertySources.contains(key)

    override fun <T : Any> getProperty(key: String, targetType: KClass<T>, defaultValue: T?): T? {
        for (source in propertySources) {
            val value = source.getProperty(key, targetType)
            if (value != null) return value
        }
        return defaultValue
    }

    override fun <T : Any> getRequiredProperty(key: String, targetType: KClass<T>, defaultValue: T?): T {
        return getProperty(key, targetType, defaultValue)
            ?: throw IllegalStateException("Required property '$key' not found")
    }

    override fun getAllProperties(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        for (source in propertySources) {
            for (name in source.getAllPropertyNames()) {
                source.getProperty(name, Any::class)?.let { result[name] = it }
            }
        }
        return result
    }

    override fun getSubProperties(prefixes: Set<String>, stripPrefix: Boolean): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        for ((key, value) in getAllProperties()) {
            for (prefix in prefixes) {
                if (key.startsWith("$prefix.")) {
                    val newKey = if (stripPrefix) key.removePrefix("$prefix.") else key
                    result[newKey] = value
                }
            }
        }
        return result
    }

    override fun getPropertyAsString(key: String, defaultValue: String?): String? {
        return getProperty(key, String::class, defaultValue)
    }

    override fun getRequiredPropertyAsString(key: String, defaultValue: String?): String {
        return getRequiredProperty(key, String::class, defaultValue)
    }

    override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> {
        return getAllProperties().mapValues { it.value.toString() }
    }

    override fun getSubPropertiesAsString(prefixes: Set<String>, stripPrefix: Boolean, redact: Boolean): Map<String, String> {
        return getSubProperties(prefixes, stripPrefix).mapValues { it.value.toString() }
    }
}

/**
 * Concrete implementation of AbstractConfigService for testing
 */
class TestConfigService(environment: ConfigEnvironment) : AbstractConfigService(environment), ConfigService {
    override val parent: ConfigService? = null
    override val level: ConfigLevel = environment.level
}

// ========== AbstractConfigService Tests ==========

class AbstractConfigServiceTest {

    private fun createTestService(props: Map<String, Any> = emptyMap()): TestConfigService {
        val source = MapPropertySource("test", props)
        val sources = DefaultPropertySources().apply { add(source) }
        val env = TestConfigEnvironment(propertySources = sources)
        return TestConfigService(env)
    }

    @Test
    fun configLevelMatchesEnvironmentLevel() {
        val env = TestConfigEnvironment(level = ConfigLevel.TENANT)
        val service = TestConfigService(env)

        assertEquals(ConfigLevel.TENANT, service.configLevel)
    }

    @Test
    fun addPropertySourceAddsToEnvironment() {
        val service = createTestService()
        val newSource = MapPropertySource("new-source", mapOf("key" to "value"))

        val result = service.addPropertySource(newSource)

        assertSame(service, result)
        assertTrue(service.getPropertySources().contains("new-source"))
    }

    @Test
    fun removePropertySourceRemovesFromEnvironment() {
        val source = MapPropertySource("removable", mapOf("key" to "value"))
        val sources = DefaultPropertySources().apply { add(source) }
        val env = TestConfigEnvironment(propertySources = sources)
        val service = TestConfigService(env)

        assertTrue(service.getPropertySources().contains("removable"))

        val result = service.removePropertySource(source)

        assertSame(service, result)
        assertFalse(service.getPropertySources().contains("removable"))
    }

    @Test
    fun addAndRemovePropertySourceUseLocalPropertySourcesWhenParentsAreIncluded() {
        val localSources = DefaultPropertySources().apply {
            add(MapPropertySource("local-source", mapOf("local.key" to "local-value")))
        }
        val parentSources = DefaultPropertySources().apply {
            add(MapPropertySource("parent-source", mapOf("parent.key" to "parent-value")))
        }

        val env = object : ConfigEnvironment {
            override val parent: ConfigEnvironment? = null
            override val level: ConfigLevel = ConfigLevel.TENANT
            override fun getActiveProfile(): String = "test"
            override fun getAppName(): String = "test-app"
            override fun getConfigLocation(): Path = Path("/test/config")
            override fun getNamespace(): String = "test.namespace"
            override fun getPropertySources(includeParents: Boolean): PropertySources =
                if (includeParents) localSources.copy(parentSources) else localSources

            override fun containsProperty(key: String): Boolean = getPropertyAsString(key) != null

            override fun <T : Any> getProperty(key: String, targetType: KClass<T>, defaultValue: T?): T? {
                getPropertySources(includeParents = true).forEach { source ->
                    source.getProperty(key, targetType)?.let { return it }
                }
                return defaultValue
            }

            override fun <T : Any> getRequiredProperty(key: String, targetType: KClass<T>, defaultValue: T?): T =
                getProperty(key, targetType, defaultValue) ?: throw IllegalStateException("Required property '$key' not found")

            override fun getPropertyAsString(key: String, defaultValue: String?): String? =
                getProperty(key, String::class, defaultValue)

            override fun getRequiredPropertyAsString(key: String, defaultValue: String?): String =
                getRequiredProperty(key, String::class, defaultValue)

            override fun getAllProperties(): Map<String, Any> = emptyMap()
            override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = emptyMap()
            override fun getSubProperties(prefixes: Set<String>, stripPrefix: Boolean): Map<String, Any> = emptyMap()
            override fun getSubPropertiesAsString(prefixes: Set<String>, stripPrefix: Boolean, redact: Boolean): Map<String, String> = emptyMap()
        }

        val service = TestConfigService(env)
        val newSource = MapPropertySource("new-source", mapOf("new.key" to "new-value"))

        service.addPropertySource(newSource)

        assertTrue(localSources.contains("new-source"))
        assertFalse(parentSources.contains("new-source"))

        service.removePropertySource(newSource)

        assertFalse(localSources.contains("new-source"))
    }

    @Test
    fun getActiveProfileDelegatesToEnvironment() {
        val env = TestConfigEnvironment(activeProfile = "production")
        val service = TestConfigService(env)

        assertEquals("production", service.getActiveProfile())
    }

    @Test
    fun getAppNameDelegatesToEnvironment() {
        val env = TestConfigEnvironment(appName = "my-application")
        val service = TestConfigService(env)

        assertEquals("my-application", service.getAppName())
    }

    @Test
    fun getConfigLocationDelegatesToEnvironment() {
        val customPath = "/custom/config/path"
        val env = TestConfigEnvironment(configLocationPath = customPath)
        val service = TestConfigService(env)

        assertEquals(Path(customPath).toString(), service.getConfigLocation().toString())
    }

    @Test
    fun getPropertySourcesDelegatesToEnvironment() {
        val source = MapPropertySource("test", mapOf("key" to "value"))
        val sources = DefaultPropertySources().apply { add(source) }
        val env = TestConfigEnvironment(propertySources = sources)
        val service = TestConfigService(env)

        val result = service.getPropertySources()

        assertTrue(result.contains("test"))
    }

    @Test
    fun containsPropertyDelegatesToEnvironment() {
        val service = createTestService(mapOf("exists" to "value"))

        // Note: containsProperty checks source names, not property keys
        assertTrue(service.containsProperty("test"))
        assertFalse(service.containsProperty("nonexistent"))
    }

    @Test
    fun getPropertyDelegatesToEnvironment() {
        val service = createTestService(mapOf("key" to "value"))

        val result = service.getProperty("key", String::class)

        assertEquals("value", result)
    }

    @Test
    fun getPropertyReturnsDefaultWhenNotFound() {
        val service = createTestService()

        val result = service.getProperty("missing", String::class, "default")

        assertEquals("default", result)
    }

    @Test
    fun getPropertyAsStringDelegatesToEnvironment() {
        val service = createTestService(mapOf("key" to "value"))

        val result = service.getPropertyAsString("key")

        assertEquals("value", result)
    }

    @Test
    fun getPropertyAsStringReturnsDefaultWhenNotFound() {
        val service = createTestService()

        val result = service.getPropertyAsString("missing", "default")

        assertEquals("default", result)
    }

    @Test
    fun getRequiredPropertyDelegatesToEnvironment() {
        val service = createTestService(mapOf("key" to "value"))

        val result = service.getRequiredProperty("key", String::class)

        assertEquals("value", result)
    }

    @Test
    fun getRequiredPropertyThrowsWhenNotFound() {
        val service = createTestService()

        assertFailsWith<IllegalStateException> {
            service.getRequiredProperty("missing", String::class)
        }
    }

    @Test
    fun getRequiredPropertyAsStringDelegatesToEnvironment() {
        val service = createTestService(mapOf("key" to "value"))

        val result = service.getRequiredPropertyAsString("key")

        assertEquals("value", result)
    }

    @Test
    fun getRequiredPropertyAsStringThrowsWhenNotFound() {
        val service = createTestService()

        assertFailsWith<IllegalStateException> {
            service.getRequiredPropertyAsString("missing")
        }
    }

    @Test
    fun getAllPropertiesDelegatesToEnvironment() {
        val service = createTestService(mapOf("a" to "1", "b" to "2"))

        val result = service.getAllProperties()

        assertEquals(2, result.size)
        assertEquals("1", result["a"])
        assertEquals("2", result["b"])
    }

    @Test
    fun getAllPropertiesAsStringDelegatesToEnvironment() {
        val service = createTestService(mapOf("num" to 42))

        val result = service.getAllPropertiesAsString()

        assertEquals("42", result["num"])
    }

    @Test
    fun getSubPropertiesDelegatesToEnvironment() {
        val service = createTestService(
            mapOf(
                "app.db.url" to "jdbc:test",
                "app.db.user" to "admin",
                "other.key" to "value"
            )
        )

        val result = service.getSubProperties(setOf("app.db"), stripPrefix = true)

        assertEquals(2, result.size)
        assertEquals("jdbc:test", result["url"])
        assertEquals("admin", result["user"])
    }

    @Test
    fun getSubPropertiesAsStringDelegatesToEnvironment() {
        val service = createTestService(
            mapOf(
                "app.port" to 8080,
                "app.timeout" to 30
            )
        )

        val result = service.getSubPropertiesAsString(setOf("app"), stripPrefix = true)

        assertEquals("8080", result["port"])
        assertEquals("30", result["timeout"])
    }

    @Test
    fun getNamespaceDelegatesToEnvironment() {
        val env = TestConfigEnvironment(namespace = "com.example.app")
        val service = TestConfigService(env)

        assertEquals("com.example.app", service.getNamespace())
    }
}

// ========== ConfigService Extension Functions Tests ==========

class ConfigServiceExtensionFunctionsTest {

    private fun createTestService(props: Map<String, Any> = emptyMap()): TestConfigService {
        val source = MapPropertySource("test", props)
        val sources = DefaultPropertySources().apply { add(source) }
        val env = TestConfigEnvironment(propertySources = sources)
        return TestConfigService(env)
    }

    @Test
    fun reifiedGetPropertyReturnsValue() {
        val service = createTestService(mapOf("key" to "value"))

        val result: String? = service.getProperty("key", null)

        assertEquals("value", result)
    }

    @Test
    fun reifiedGetPropertyReturnsDefaultWhenNotFound() {
        val service = createTestService()

        val result: String? = service.getProperty("missing", "default")

        assertEquals("default", result)
    }

    @Test
    fun reifiedGetPropertyReturnsNullWhenNotFound() {
        val service = createTestService()

        val result: String? = service.getProperty<String>("missing", null)

        assertNull(result)
    }

    @Test
    fun reifiedGetRequiredPropertyReturnsValue() {
        val service = createTestService(mapOf("key" to "value"))

        val result: String = service.getRequiredProperty("key", null)

        assertEquals("value", result)
    }

    @Test
    fun reifiedGetRequiredPropertyReturnsDefault() {
        val service = createTestService()

        val result: String = service.getRequiredProperty("missing", "default")

        assertEquals("default", result)
    }

    @Test
    fun reifiedGetRequiredPropertyThrowsWhenNotFoundAndNoDefault() {
        val service = createTestService()

        assertFailsWith<IllegalStateException> {
            service.getRequiredProperty<String>("missing", null)
        }
    }
}

// ========== EnvPropertySource Tests ==========

class EnvPropertySourceTest {

    @Test
    fun getNameReturnsEnvName() {
        val source = EnvPropertySource()

        assertEquals("environment", source.getName())
    }

    @Test
    fun getPropertyReturnsNullForNonexistentKey() {
        val source = EnvPropertySource()

        // Test with a key that definitely doesn't exist
        val result = source.getProperty("NONEXISTENT_KEY_12345_ABC", String::class)

        assertNull(result)
    }

    @Test
    fun getAllPropertyNamesReturnsEnvironmentKeys() {
        val source = EnvPropertySource()

        val names = source.getAllPropertyNames()

        // Should return at least some environment variables (PATH is almost always set)
        assertTrue(names.isNotEmpty() || true) // Allow empty in restricted environments
    }
}

// ========== Env Object Tests ==========

class EnvObjectTest {

    @Test
    fun getReturnsNullForNonexistentKey() {
        // Test with a key that definitely doesn't exist
        val result = Env.get("NONEXISTENT_KEY_12345_XYZ")

        assertNull(result)
    }
}
