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

import com.sphereon.di.Order
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PropertySourcesPropertyResolverTest {
    @Test
    fun containsPropertyReturnsTrueWhenPropertyExists() {
        val source = MapPropertySource("test", mapOf("prop" to "value"))
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        assertTrue(resolver.containsProperty("prop"))
    }

    @Test
    fun containsPropertyReturnsFalseWhenPropertyMissing() {
        val source = MapPropertySource("test", mapOf("key1" to "value1"))
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        assertFalse(resolver.containsProperty("missing.key"))
    }

    @Test
    fun getPropertyReturnsValue() {
        val source = MapPropertySource("test", mapOf("key" to "value"))
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        assertEquals("value", resolver.getProperty("key", String::class))
    }

    @Test
    fun getPropertyReturnsDefaultWhenNotFound() {
        val source = MapPropertySource("test", emptyMap())
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        assertEquals("default", resolver.getProperty("missing", String::class, "default"))
    }

    @Test
    fun getPropertyReturnsNullWhenNotFoundAndNoDefault() {
        val source = MapPropertySource("test", emptyMap())
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        assertNull(resolver.getProperty("missing", String::class))
    }

    @Test
    fun getRequiredPropertyThrowsWhenMissing() {
        val source = MapPropertySource("test", emptyMap())
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        assertFailsWith<IllegalStateException> {
            resolver.getRequiredProperty("missing", String::class)
        }
    }

    @Test
    fun getRequiredPropertyReturnsValue() {
        val source = MapPropertySource("test", mapOf("key" to "value"))
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        assertEquals("value", resolver.getRequiredProperty("key", String::class))
    }

    @Test
    fun getAllPropertiesReturnsAllProperties() {
        val source = MapPropertySource("test", mapOf("a" to "1", "b" to "2"))
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val allProps = resolver.getAllProperties()

        assertEquals(2, allProps.size)
        assertEquals("1", allProps["a"])
        assertEquals("2", allProps["b"])
    }

    @Test
    fun getAllPropertiesAsStringConvertsValues() {
        val source = MapPropertySource("test", mapOf("num" to 42))
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val allProps = resolver.getAllPropertiesAsString()

        assertEquals("42", allProps["num"])
    }

    @Test
    fun getSubPropertiesFiltersAndStripsPrefix() {
        val source =
            MapPropertySource(
                "test",
                mapOf(
                    "app.database.url" to "jdbc:h2",
                    "app.database.user" to "admin",
                    "server.port" to "8080",
                ),
            )
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val subProps = resolver.getSubProperties(setOf("app.database"), stripPrefix = true)

        assertEquals(2, subProps.size)
        assertEquals("jdbc:h2", subProps["url"])
        assertEquals("admin", subProps["user"])
    }

    @Test
    fun getSubPropertiesWithoutStripping() {
        val source =
            MapPropertySource(
                "test",
                mapOf(
                    "app.database.url" to "jdbc:h2",
                    "server.port" to "8080",
                ),
            )
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val subProps = resolver.getSubProperties(setOf("app.database"), stripPrefix = false)

        assertEquals(1, subProps.size)
        assertTrue(subProps.containsKey("app.database.url"))
    }

    @Test
    fun scopeOrderingPrefersChildScopesOverParentOrder() {
        val appSource =
            ProtectedMutableMapPropertySource(
                "app",
                ConfigLevel.APP,
                order = Order.HIGHEST.orderValue,
            ).apply { addProperty("shared.key", "app-value") }

        val tenantSource =
            ProtectedMutableMapPropertySource(
                "tenant",
                ConfigLevel.TENANT,
                order = Order.LOWEST.orderValue,
            ).apply { addProperty("shared.key", "tenant-value") }

        val principalSource =
            ProtectedMutableMapPropertySource(
                "principal",
                ConfigLevel.PRINCIPAL,
                order = Order.MEDIUM.orderValue,
            ).apply { addProperty("shared.key", "principal-value") }

        val sources =
            DefaultPropertySources().apply {
                add(appSource)
                add(tenantSource)
                add(principalSource)
            }
        val resolver = PropertySourcesPropertyResolver(sources)

        // PRINCIPAL should win even though APP has highest order
        assertEquals("principal-value", resolver.getProperty("shared.key", String::class))
    }

    @Test
    fun scopeOrderingRespectsOrderWithinSameScope() {
        val tenantHigh =
            ProtectedMutableMapPropertySource(
                "tenant-high",
                ConfigLevel.TENANT,
                order = Order.HIGHEST.orderValue,
            ).apply { addProperty("tenant.key", "high") }

        val tenantLow =
            ProtectedMutableMapPropertySource(
                "tenant-low",
                ConfigLevel.TENANT,
                order = Order.LOWEST.orderValue,
            ).apply { addProperty("tenant.key", "low") }

        val sources =
            DefaultPropertySources().apply {
                add(tenantLow)
                add(tenantHigh)
            }
        val resolver = PropertySourcesPropertyResolver(sources)

        assertEquals("high", resolver.getProperty("tenant.key", String::class))
    }
}

class StringPropertyValueConverterImplTest {
    @Test
    fun supportsBooleanValues() {
        val converter = StringPropertyValueConverterImpl()
        assertTrue(converter.supports(true))
        assertTrue(converter.supports(false))
    }

    @Test
    fun supportsNumberValues() {
        val converter = StringPropertyValueConverterImpl()
        assertTrue(converter.supports(42))
        assertTrue(converter.supports(3.14))
        assertTrue(converter.supports(100L))
    }

    @Test
    fun supportsStringValues() {
        val converter = StringPropertyValueConverterImpl()
        assertTrue(converter.supports("hello"))
    }

    @Test
    fun doesNotSupportOtherTypes() {
        val converter = StringPropertyValueConverterImpl()
        assertFalse(converter.supports(listOf(1, 2, 3)))
        assertFalse(converter.supports(mapOf("a" to 1)))
    }

    @Test
    fun convertsStringToString() {
        val converter = StringPropertyValueConverterImpl()
        assertEquals("hello", converter.convert("hello"))
    }

    @Test
    fun convertsBooleanToString() {
        val converter = StringPropertyValueConverterImpl()
        assertEquals("true", converter.convert(true))
        assertEquals("false", converter.convert(false))
    }

    @Test
    fun convertsNumberToString() {
        val converter = StringPropertyValueConverterImpl()
        assertEquals("42", converter.convert(42))
    }
}

class NumberPropertyValueConverterImplTest {
    @Test
    fun supportsNumberValues() {
        val converter = NumberPropertyValueConverterImpl()
        assertTrue(converter.supports(42))
        assertTrue(converter.supports(3.14))
    }

    @Test
    fun supportsBooleanValues() {
        val converter = NumberPropertyValueConverterImpl()
        assertTrue(converter.supports(true))
    }

    @Test
    fun supportsStringValues() {
        val converter = NumberPropertyValueConverterImpl()
        assertTrue(converter.supports("123"))
    }

    @Test
    fun convertsNumberToNumber() {
        val converter = NumberPropertyValueConverterImpl()
        assertEquals(42, converter.convert(42))
        assertEquals(3.14, converter.convert(3.14))
    }

    @Test
    fun convertsBooleanToNumber() {
        val converter = NumberPropertyValueConverterImpl()
        assertEquals(1, converter.convert(true))
        assertEquals(0, converter.convert(false))
    }

    @Test
    fun convertsStringToNumber() {
        val converter = NumberPropertyValueConverterImpl()
        assertEquals(123L, converter.convert("123"))
    }

    @Test
    fun throwsForInvalidString() {
        val converter = NumberPropertyValueConverterImpl()
        assertFailsWith<IllegalArgumentException> {
            converter.convert("not-a-number")
        }
    }

    @Test
    fun throwsForUnsupportedType() {
        val converter = NumberPropertyValueConverterImpl()
        assertFailsWith<IllegalArgumentException> {
            converter.convert(listOf(1, 2, 3))
        }
    }
}

class ConstantsTest {
    @Test
    fun propertyKeyDelimiterIsDot() {
        assertEquals(".", PROPERTY_KEY_DELIMITER)
    }
}

// ========== PropertyResolver Extension Functions Tests ==========

class PropertyResolverExtensionFunctionsTest {
    @Test
    fun reifiedGetPropertyReturnsValue() {
        val source = MapPropertySource("test", mapOf("key" to "value"))
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val result: String? = resolver.getProperty("key")
        assertEquals("value", result)
    }

    @Test
    fun reifiedGetPropertyReturnsDefaultWhenNotFound() {
        val source = MapPropertySource("test", emptyMap<String, Any>())
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val result: String? = resolver.getProperty("missing", "default")
        assertEquals("default", result)
    }

    @Test
    fun reifiedGetPropertyReturnsNullWhenNotFound() {
        val source = MapPropertySource("test", emptyMap<String, Any>())
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val result: String? = resolver.getProperty("missing")
        assertNull(result)
    }

    @Test
    fun reifiedGetRequiredPropertyReturnsValue() {
        val source = MapPropertySource("test", mapOf("key" to "value"))
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val result: String = resolver.getRequiredProperty("key")
        assertEquals("value", result)
    }

    @Test
    fun reifiedGetRequiredPropertyThrowsWhenNotFound() {
        val source = MapPropertySource("test", emptyMap<String, Any>())
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        assertFailsWith<IllegalStateException> {
            resolver.getRequiredProperty<String>("missing")
        }
    }

    @Test
    fun reifiedGetRequiredPropertyReturnsDefault() {
        val source = MapPropertySource("test", emptyMap<String, Any>())
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val result: String = resolver.getRequiredProperty("missing", "default-value")
        assertEquals("default-value", result)
    }
}

// ========== AbstractPropertyResolver Default Methods Tests ==========

class AbstractPropertyResolverDefaultMethodsTest {
    @Test
    fun getPropertyAsStringDelegatesToGetProperty() {
        val source = MapPropertySource("test", mapOf("key" to "value"))
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val result = resolver.getPropertyAsString("key")
        assertEquals("value", result)
    }

    @Test
    fun getPropertyAsStringReturnsDefaultWhenNotFound() {
        val source = MapPropertySource("test", emptyMap<String, Any>())
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val result = resolver.getPropertyAsString("missing", "default")
        assertEquals("default", result)
    }

    @Test
    fun getRequiredPropertyAsStringReturnsValue() {
        val source = MapPropertySource("test", mapOf("key" to "value"))
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val result = resolver.getRequiredPropertyAsString("key")
        assertEquals("value", result)
    }

    @Test
    fun getRequiredPropertyAsStringThrowsWhenNotFound() {
        val source = MapPropertySource("test", emptyMap<String, Any>())
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        assertFailsWith<IllegalStateException> {
            resolver.getRequiredPropertyAsString("missing")
        }
    }

    @Test
    fun getSubPropertiesAsStringConvertsValues() {
        val source =
            MapPropertySource(
                "test",
                mapOf(
                    "app.database.port" to 5432,
                    "app.database.pool.size" to 10,
                ),
            )
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val subProps = resolver.getSubPropertiesAsString(setOf("app.database"), stripPrefix = true)

        assertEquals("5432", subProps["port"])
        assertEquals("10", subProps["pool.size"])
    }
}
