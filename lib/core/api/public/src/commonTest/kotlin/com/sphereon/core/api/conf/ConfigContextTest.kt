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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ConfigScopeTest {
    @Test
    fun globalScopeExists() {
        assertEquals("GLOBAL", ConfigScope.GLOBAL.name)
    }

    @Test
    fun serviceScopeExists() {
        assertEquals("SERVICE", ConfigScope.SERVICE.name)
    }

    @Test
    fun hasTwoScopes() {
        assertEquals(2, ConfigScope.entries.size)
    }
}

class ConfigSourceTest {
    @Test
    fun buildSourceExists() {
        assertEquals("BUILD", ConfigSource.BUILD.name)
    }

    @Test
    fun appSourceExists() {
        assertEquals("APP", ConfigSource.APP.name)
    }

    @Test
    fun profileSourceExists() {
        assertEquals("PROFILE", ConfigSource.PROFILE.name)
    }

    @Test
    fun tenantSourceExists() {
        assertEquals("TENANT", ConfigSource.TENANT.name)
    }

    @Test
    fun principalSourceExists() {
        assertEquals("PRINCIPAL", ConfigSource.PRINCIPAL.name)
    }

    @Test
    fun hasFiveSources() {
        assertEquals(5, ConfigSource.entries.size)
    }
}

class ConfigLevelTest {
    @Test
    fun appLevelExists() {
        assertEquals("APP", ConfigLevel.APP.name)
    }

    @Test
    fun tenantLevelExists() {
        assertEquals("TENANT", ConfigLevel.TENANT.name)
    }

    @Test
    fun principalLevelExists() {
        assertEquals("PRINCIPAL", ConfigLevel.PRINCIPAL.name)
    }

    @Test
    fun hasThreeLevels() {
        assertEquals(3, ConfigLevel.entries.size)
    }
}

class ConfigContextTest {
    @Test
    fun canCreateConfigContext() {
        val context = ConfigContext(ConfigScope.GLOBAL, ConfigSource.APP)
        assertEquals(ConfigScope.GLOBAL, context.scope)
        assertEquals(ConfigSource.APP, context.source)
    }

    @Test
    fun dataClassEquality() {
        val context1 = ConfigContext(ConfigScope.GLOBAL, ConfigSource.APP)
        val context2 = ConfigContext(ConfigScope.GLOBAL, ConfigSource.APP)
        val context3 = ConfigContext(ConfigScope.SERVICE, ConfigSource.APP)

        assertEquals(context1, context2)
        assertNotEquals(context1, context3)
    }

    @Test
    fun copyWorks() {
        val original = ConfigContext(ConfigScope.GLOBAL, ConfigSource.BUILD)
        val copied = original.copy(source = ConfigSource.TENANT)

        assertEquals(ConfigScope.GLOBAL, copied.scope)
        assertEquals(ConfigSource.TENANT, copied.source)
    }
}

class ConfigContextHierarchyTest {
    @Test
    fun canCreateHierarchy() {
        val hierarchy =
            ConfigContextHierarchy(
                ConfigScope.GLOBAL,
                listOf(ConfigSource.BUILD, ConfigSource.APP, ConfigSource.PROFILE),
            )

        assertEquals(ConfigScope.GLOBAL, hierarchy.scope)
        assertEquals(3, hierarchy.sources.size)
    }

    @Test
    fun dataClassEquality() {
        val hierarchy1 =
            ConfigContextHierarchy(
                ConfigScope.GLOBAL,
                listOf(ConfigSource.BUILD, ConfigSource.APP),
            )
        val hierarchy2 =
            ConfigContextHierarchy(
                ConfigScope.GLOBAL,
                listOf(ConfigSource.BUILD, ConfigSource.APP),
            )
        val hierarchy3 =
            ConfigContextHierarchy(
                ConfigScope.SERVICE,
                listOf(ConfigSource.BUILD),
            )

        assertEquals(hierarchy1, hierarchy2)
        assertNotEquals(hierarchy1, hierarchy3)
    }
}
