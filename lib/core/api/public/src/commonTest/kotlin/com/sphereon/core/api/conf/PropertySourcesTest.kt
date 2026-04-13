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

import com.sphereon.di.Order
import com.sphereon.di.SortOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MutableMapPropertySourceBasicTest {
    @Test
    fun getNameReturnsCorrectName() {
        val source = MutableMapPropertySource("test-source")
        assertEquals("test-source", source.getName())
    }

    @Test
    fun getSourceReturnsMutableMap() {
        val source = MutableMapPropertySource("test-source")
        assertNotNull(source.getSource())
        assertTrue(source.getSource() is MutableMap)
    }

    @Test
    fun getOrderReturnsDefaultMediumOrder() {
        val source = MutableMapPropertySource("test-source")
        assertEquals(Order.MEDIUM.orderValue, source.getOrder())
    }

    @Test
    fun getOrderReturnsCustomOrder() {
        val source = MutableMapPropertySource("test-source", order = Order.HIGH.orderValue)
        assertEquals(Order.HIGH.orderValue, source.getOrder())
    }

    @Test
    fun isPlatformSupportedReturnsTrue() {
        val source = MutableMapPropertySource("test-source")
        assertTrue(source.isPlatformSupported)
    }
}

class MutableMapPropertySourcePropertyTest {
    @Test
    fun addPropertyAddsToSource() {
        val source = MutableMapPropertySource("test-source")
        source.addProperty("myKey", "myValue")
        assertEquals("myValue", source.getProperty("myKey", String::class))
    }

    @Test
    fun addPropertyNormalizesKey() {
        val source = MutableMapPropertySource("test-source")
        source.addProperty("myKey", "myValue")
        assertEquals("myValue", source.getProperty("my.key", String::class))
    }

    @Test
    fun addPropertiesAddsMultipleProperties() {
        val source = MutableMapPropertySource("test-source")
        source.addProperties(mapOf("key1" to "value1", "key2" to "value2"))
        assertEquals("value1", source.getProperty("key1", String::class))
        assertEquals("value2", source.getProperty("key2", String::class))
    }

    @Test
    fun addPropertyReturnsSelf() {
        val source = MutableMapPropertySource("test-source")
        val result = source.addProperty("key", "value")
        assertEquals(source, result)
    }

    @Test
    fun addPropertiesReturnsSelf() {
        val source = MutableMapPropertySource("test-source")
        val result = source.addProperties(mapOf("key" to "value"))
        assertEquals(source, result)
    }

    @Test
    fun getPropertyReturnsNullForMissingKey() {
        val source = MutableMapPropertySource("test-source")
        assertNull(source.getProperty("missing", String::class))
    }

    @Test
    fun getPropertyThrowsForWrongType() {
        val source = MutableMapPropertySource("test-source")
        source.addProperty("key", "stringValue")
        assertFailsWith<IllegalArgumentException> {
            source.getProperty("key", Int::class)
        }
    }

    @Test
    fun getPropertyAsStringReturnsValue() {
        val source = MutableMapPropertySource("test-source")
        source.addProperty("key", "value")
        assertEquals("value", source.getPropertyAsString("key"))
    }

    @Test
    fun getPropertyAsStringConvertsNonString() {
        val source = MutableMapPropertySource("test-source")
        source.addProperty("key", 42)
        assertEquals("42", source.getPropertyAsString("key"))
    }

    @Test
    fun getPropertyAsStringReturnsNullForMissing() {
        val source = MutableMapPropertySource("test-source")
        assertNull(source.getPropertyAsString("missing"))
    }

    @Test
    fun hasPropertyReturnsTrueForExisting() {
        val source = MutableMapPropertySource("test-source")
        source.addProperty("key", "value")
        assertTrue(source.hasProperty("key"))
    }

    @Test
    fun hasPropertyReturnsFalseForMissing() {
        val source = MutableMapPropertySource("test-source")
        assertFalse(source.hasProperty("missing"))
    }

    @Test
    fun deletePropertyRemovesProperty() {
        val source = MutableMapPropertySource("test-source")
        source.addProperty("key", "value")
        source.deleteProperty("key")
        assertFalse(source.hasProperty("key"))
    }

    @Test
    fun removePropertyRemovesProperty() {
        val source = MutableMapPropertySource("test-source")
        source.addProperty("key", "value")
        source.removeProperty("key")
        assertFalse(source.hasProperty("key"))
    }

    @Test
    fun getAllPropertyNamesReturnsAllKeys() {
        val source = MutableMapPropertySource("test-source")
        source.addProperty("key1", "value1")
        source.addProperty("key2", "value2")
        val names = source.getAllPropertyNames()
        assertEquals(2, names.size)
        assertTrue(names.contains("key1"))
        assertTrue(names.contains("key2"))
    }
}

class MapPropertySourceBasicTest {
    @Test
    fun getNameReturnsCorrectName() {
        val source = MapPropertySource("test-source", mapOf("key" to "value"))
        assertEquals("test-source", source.getName())
    }

    @Test
    fun getSourceReturnsMap() {
        val map = mapOf("key" to "value")
        val source = MapPropertySource("test-source", map)
        assertEquals(map, source.getSource())
    }

    @Test
    fun getOrderReturnsDefaultMediumOrder() {
        val source = MapPropertySource("test-source", emptyMap())
        assertEquals(Order.MEDIUM.orderValue, source.getOrder())
    }

    @Test
    fun isPlatformSupportedReturnsTrue() {
        val source = MapPropertySource("test-source", emptyMap())
        assertTrue(source.isPlatformSupported)
    }
}

class MapPropertySourcePropertyTest {
    @Test
    fun getPropertyReturnsValue() {
        val source = MapPropertySource("test-source", mapOf("key" to "value"))
        assertEquals("value", source.getProperty("key", String::class))
    }

    @Test
    fun getPropertyNormalizesKey() {
        val source = MapPropertySource("test-source", mapOf("my.key" to "value"))
        assertEquals("value", source.getProperty("myKey", String::class))
    }

    @Test
    fun getPropertyReturnsNullForMissing() {
        val source = MapPropertySource("test-source", emptyMap())
        assertNull(source.getProperty("missing", String::class))
    }

    @Test
    fun getPropertyThrowsForWrongType() {
        val source = MapPropertySource("test-source", mapOf("key" to "value"))
        assertFailsWith<IllegalArgumentException> {
            source.getProperty("key", Int::class)
        }
    }

    @Test
    fun getPropertyAsStringReturnsValue() {
        val source = MapPropertySource("test-source", mapOf("key" to "value"))
        assertEquals("value", source.getPropertyAsString("key"))
    }

    @Test
    fun getPropertyAsStringConvertsNonString() {
        val source = MapPropertySource("test-source", mapOf("key" to 42))
        assertEquals("42", source.getPropertyAsString("key"))
    }

    @Test
    fun hasPropertyReturnsTrueForExisting() {
        val source = MapPropertySource("test-source", mapOf("key" to "value"))
        assertTrue(source.hasProperty("key"))
    }

    @Test
    fun hasPropertyReturnsFalseForMissing() {
        val source = MapPropertySource("test-source", emptyMap())
        assertFalse(source.hasProperty("missing"))
    }

    @Test
    fun removePropertyThrowsUnsupportedOperation() {
        val source = MapPropertySource("test-source", mapOf("key" to "value"))
        assertFailsWith<UnsupportedOperationException> {
            source.removeProperty("key")
        }
    }

    @Test
    fun getAllPropertyNamesReturnsAllKeys() {
        val source = MapPropertySource("test-source", mapOf("key1" to "v1", "key2" to "v2"))
        val names = source.getAllPropertyNames()
        assertEquals(2, names.size)
    }
}

class PropertySourceCompareTest {
    @Test
    fun compareToReturnsNegativeForLowerOrderValue() {
        // HIGH has orderValue 30, LOW has orderValue 70
        // 30 < 70, so HIGH.compareTo(LOW) returns negative
        val source1 = MutableMapPropertySource("s1", order = Order.HIGH.orderValue)
        val source2 = MutableMapPropertySource("s2", order = Order.LOW.orderValue)
        assertTrue(source1.compareTo(source2) < 0)
    }

    @Test
    fun compareToReturnsPositiveForHigherOrderValue() {
        // LOW has orderValue 70, HIGH has orderValue 30
        // 70 > 30, so LOW.compareTo(HIGH) returns positive
        val source1 = MutableMapPropertySource("s1", order = Order.LOW.orderValue)
        val source2 = MutableMapPropertySource("s2", order = Order.HIGH.orderValue)
        assertTrue(source1.compareTo(source2) > 0)
    }

    @Test
    fun compareToReturnsZeroForEqualOrder() {
        val source1 = MutableMapPropertySource("s1", order = Order.MEDIUM.orderValue)
        val source2 = MutableMapPropertySource("s2", order = Order.MEDIUM.orderValue)
        assertEquals(0, source1.compareTo(source2))
    }
}

class DefaultPropertySourcesBasicTest {
    @Test
    fun addSourceAddsToList() {
        val sources = DefaultPropertySources()
        val source = MutableMapPropertySource("test")
        sources.add(source)
        assertTrue(sources.contains("test"))
    }

    @Test
    fun addSourceReturnsSelf() {
        val sources = DefaultPropertySources()
        val source = MutableMapPropertySource("test")
        val result = sources.add(source)
        assertEquals(sources, result)
    }

    @Test
    fun removeSourceRemovesFromList() {
        val sources = DefaultPropertySources()
        val source = MutableMapPropertySource("test")
        sources.add(source)
        sources.remove(source)
        assertFalse(sources.contains("test"))
    }

    @Test
    fun removeSourceReturnsSelf() {
        val sources = DefaultPropertySources()
        val source = MutableMapPropertySource("test")
        sources.add(source)
        val result = sources.remove(source)
        assertEquals(sources, result)
    }

    @Test
    fun containsReturnsTrueForExisting() {
        val sources = DefaultPropertySources()
        val source = MutableMapPropertySource("test")
        sources.add(source)
        assertTrue(sources.contains("test"))
    }

    @Test
    fun containsReturnsFalseForMissing() {
        val sources = DefaultPropertySources()
        assertFalse(sources.contains("missing"))
    }

    @Test
    fun getReturnsSourceByName() {
        val sources = DefaultPropertySources()
        val source = MutableMapPropertySource("test")
        sources.add(source)
        assertEquals(source, sources.get("test"))
    }

    @Test
    fun getReturnsNullForMissing() {
        val sources = DefaultPropertySources()
        assertNull(sources.get("missing"))
    }

    @Test
    fun revisionIncrementsOnAddAndRemove() {
        val sources = DefaultPropertySources()
        val initialRevision = sources.revision
        val source = MutableMapPropertySource("test")

        sources.add(source)
        assertTrue(sources.revision > initialRevision)

        val revisionAfterAdd = sources.revision
        sources.remove(source)
        assertTrue(sources.revision > revisionAfterAdd)
    }

    @Test
    fun revisionDoesNotChangeWhenRemoveMisses() {
        val sources = DefaultPropertySources()
        val initialRevision = sources.revision

        sources.remove(MutableMapPropertySource("missing"))

        assertEquals(initialRevision, sources.revision)
    }
}

class DefaultPropertySourcesSortingTest {
    @Test
    fun iteratorReturnsSortedAscByDefault() {
        // ASC sorts by ascending orderValue: HIGH(30) < MEDIUM(50) < LOW(70)
        val sources = DefaultPropertySources()
        val high = MutableMapPropertySource("high", order = Order.HIGH.orderValue)
        val low = MutableMapPropertySource("low", order = Order.LOW.orderValue)
        val medium = MutableMapPropertySource("medium", order = Order.MEDIUM.orderValue)

        sources.add(high)
        sources.add(low)
        sources.add(medium)

        val list = sources.toList()
        assertEquals("high", list[0].getName())
        assertEquals("medium", list[1].getName())
        assertEquals("low", list[2].getName())
    }

    @Test
    fun iteratorReturnsSortedDescWhenConfigured() {
        // DESC sorts by descending orderValue: LOW(70) > MEDIUM(50) > HIGH(30)
        val sources = DefaultPropertySources(sorting = SortOrder.DESC)
        val high = MutableMapPropertySource("high", order = Order.HIGH.orderValue)
        val low = MutableMapPropertySource("low", order = Order.LOW.orderValue)
        val medium = MutableMapPropertySource("medium", order = Order.MEDIUM.orderValue)

        sources.add(high)
        sources.add(low)
        sources.add(medium)

        val list = sources.toList()
        assertEquals("low", list[0].getName())
        assertEquals("medium", list[1].getName())
        assertEquals("high", list[2].getName())
    }

    @Test
    fun iteratorReturnsUnsortedWhenNone() {
        val high = MutableMapPropertySource("high", order = Order.HIGH.orderValue)
        val low = MutableMapPropertySource("low", order = Order.LOW.orderValue)
        val sources = DefaultPropertySources(mutableListOf(high, low), sorting = SortOrder.NONE)

        val list = sources.toList()
        assertEquals("high", list[0].getName())
        assertEquals("low", list[1].getName())
    }
}

class DefaultPropertySourcesCopyTest {
    @Test
    fun copyCreatesNewInstance() {
        val sources = DefaultPropertySources()
        val source = MutableMapPropertySource("test")
        sources.add(source)

        val copy = sources.copy()
        assertTrue(copy.contains("test"))
    }

    @Test
    fun copyIncludesAdditionalSources() {
        val sources = DefaultPropertySources()
        val source1 = MutableMapPropertySource("test1")
        sources.add(source1)

        val additional = DefaultPropertySources()
        val source2 = MutableMapPropertySource("test2")
        additional.add(source2)

        val copy = sources.copy(additional)
        assertTrue(copy.contains("test1"))
        assertTrue(copy.contains("test2"))
    }

    @Test
    fun copyWithNullAdditionalSourcesWorks() {
        val sources = DefaultPropertySources()
        val source = MutableMapPropertySource("test")
        sources.add(source)

        val copy = sources.copy(null)
        assertTrue(copy.contains("test"))
    }
}

class DefaultPropertySourcesRemoveByNameTest {
    @Test
    fun removeByNameRemovesSource() {
        val sources = DefaultPropertySources()
        val source = MutableMapPropertySource("test")
        sources.add(source)

        val removed = sources.removeByName("test")
        assertTrue(removed)
        assertFalse(sources.contains("test"))
    }

    @Test
    fun removeByNameReturnsFalseForMissing() {
        val sources = DefaultPropertySources()
        val removed = sources.removeByName("missing")
        assertFalse(removed)
    }
}

class DefaultAppMapPropertySourceTest {
    @Test
    fun hasCorrectName() {
        assertEquals("default-app-map", DefaultAppMapPropertySource.getName())
    }

    @Test
    fun canAddAndGetProperty() {
        DefaultAppMapPropertySource.addProperty("testKey", "testValue")
        assertEquals("testValue", DefaultAppMapPropertySource.getProperty("test.key", String::class))
        DefaultAppMapPropertySource.deleteProperty("test.key")
    }
}

class DefaultTenantMapPropertySourceTest {
    @Test
    fun hasCorrectName() {
        assertEquals("default-tenant-map", DefaultTenantMapPropertySource.getName())
    }
}

class DefaultPrincipalMapPropertySourceTest {
    @Test
    fun hasCorrectName() {
        assertEquals("default-principal-map", DefaultPrincipalMapPropertySource.getName())
    }
}

class DefaultMapPropertySourceFactoryTest {
    @Test
    fun createsDistinctTenantSources() {
        val first = DefaultMapPropertySourceFactory.tenant()
        val second = DefaultMapPropertySourceFactory.tenant()

        assertTrue(first !== second)
    }

    @Test
    fun createsDistinctPrincipalSources() {
        val first = DefaultMapPropertySourceFactory.principal()
        val second = DefaultMapPropertySourceFactory.principal()

        assertTrue(first !== second)
    }

    @Test
    fun createsSourcesWithExpectedNamesAndScopes() {
        val app = DefaultMapPropertySourceFactory.app()
        val tenant = DefaultMapPropertySourceFactory.tenant()
        val principal = DefaultMapPropertySourceFactory.principal()

        assertEquals(DefaultAppMapPropertySource.NAME, app.getName())
        assertEquals(ConfigLevel.APP, app.configLevel)

        assertEquals(DefaultTenantMapPropertySource.NAME, tenant.getName())
        assertEquals(ConfigLevel.TENANT, tenant.configLevel)

        assertEquals(DefaultPrincipalMapPropertySource.NAME, principal.getName())
        assertEquals(ConfigLevel.PRINCIPAL, principal.configLevel)
    }
}

class PropertySourceExtensionTest {
    @Test
    fun reifiedGetPropertyWorks() {
        val source = MutableMapPropertySource("test")
        source.addProperty("key", "value")
        val value: String? = source.getProperty("key")
        assertEquals("value", value)
    }

    @Test
    fun reifiedGetPropertyReturnsNullForMissing() {
        val source = MutableMapPropertySource("test")
        val value: String? = source.getProperty("missing")
        assertNull(value)
    }
}
