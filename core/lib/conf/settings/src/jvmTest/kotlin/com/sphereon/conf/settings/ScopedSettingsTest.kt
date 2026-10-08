/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.conf.settings

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.conf.getProperty
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.di.context.UserContextInstance
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScopedSettingsTest {
    private lateinit var userContextInstance: UserContextInstance
    private lateinit var appGraph: JvmMPSettingsAppGraph
    private lateinit var appSettingsSource: MultiplatformSettingsAppPropertySource

    private lateinit var tenantSettingsSource: MultiplatformSettingsTenantPropertySource
    private lateinit var principalSettingsSource: MultiplatformSettingsPrincipalPropertySource

    val appSettings: MultiplatformSettings
        get() = appSettingsSource.getSource()

    val tenantSettings: MultiplatformSettings
        get() = tenantSettingsSource.getSource()

    val principalSettings: MultiplatformSettings
        get() = principalSettingsSource.getSource()

    val tenantConfigService: TenantConfigService
        get() = (userContextInstance.graph as TenantConfigService.Graph).tenantConfigService

    val principalConfigService: PrincipalConfigService
        get() = (userContextInstance.graph as PrincipalConfigService.Graph).principalConfigService

    val appConfigService: AppConfigService
        get() = appGraph.appConfigService

    @BeforeTest
    fun setup() {
        runBlocking {
            appGraph =
                createJvmMPSettingsAppGraph(
                    application = this@ScopedSettingsTest,
                    appId = "mp-settings-test",
                    profile = "test",
                    version = "0.0.1",
                )

            userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test@principal.com"),
                    DefaultPrincipalInputString("test@principal.com"),
                )
            appSettingsSource = appGraph.appConfigSettings
            tenantSettingsSource = (userContextInstance.graph as MultiplatformSettingsTenantPropertySource.Graph).tenantConfigSettings
            principalSettingsSource = (userContextInstance.graph as MultiplatformSettingsPrincipalPropertySource.Graph).principalConfigSettings
        }
    }

    @Test
    fun testPlatformSupported() {
        assertTrue(appSettingsSource.isPlatformSupported, "MultiplatformSettings should be supported")
    }

    @Test
    fun testSetDifferentValues() {
        val key = "test.int"
        val tenantValue = 42
        val principalValue = 43

        tenantSettings.set<Int>(key, tenantValue)
        assertEquals(tenantValue, tenantSettings.get<Int>(key))
        assertTrue(tenantSettings.getKeys().contains(key))

        principalSettings.set<Int>(key, principalValue)
        assertEquals(tenantValue, tenantSettings.get<Int>(key))
        assertEquals(principalValue, principalSettings.get<Int>(key))
        assertTrue(tenantSettings.getKeys().contains(key))
        assertTrue(principalSettings.getKeys().contains(key))

        tenantSettings.set<Int>(key, null)
        principalSettings.set<Int>(key, null)
        assertNull(tenantSettings.get<Int>(key))
        assertFalse(tenantSettings.getKeys().contains(key))
        assertNull(principalSettings.get<Int>(key))
        assertFalse(principalSettings.getKeys().contains(key))
    }

    @Test
    fun testInheritanceValues() {
        val key1 = "test_key1"
        val key2 = "test_key2"
        val key1Normalized = "test.key1"

        tenantSettingsSource.setProperty(key1, "tenant")
        // key1 is also normalized
        assertFalse(tenantSettings.getKeys().contains(key1))
        assertTrue(tenantSettings.getKeys().contains(key1Normalized))
        assertEquals("tenant", tenantSettingsSource.getProperty<String>(key1Normalized))

        // It was never set on the principal level, but should get it from the parent at the tenant level when using the config service
        assertNull(principalSettingsSource.getProperty<String>(key1))
        assertEquals("tenant", principalConfigService.getProperty<String>(key1))
        assertNull(appConfigService.getProperty<String>(key1))

        // Now let's set something at app level to see it propagate to tenant and principal
        appSettingsSource.setProperty(key2, "app")
        assertEquals("app", appConfigService.getProperty<String>(key2))
        assertEquals("app", tenantConfigService.getProperty<String>(key2))
        assertEquals("app", principalConfigService.getProperty<String>(key2))
    }

    @Test
    fun testInheritanceDifferentValues() {
        val key2 = "test_key_different"

        appSettingsSource.setProperty(key2, "app")
        tenantSettingsSource.setProperty(key2, "tenant")
        principalSettingsSource.setProperty(key2, "principal")
        assertEquals("app", appConfigService.getProperty<String>(key2))
        assertEquals("tenant", tenantConfigService.getProperty<String>(key2))
        assertEquals("principal", principalConfigService.getProperty<String>(key2))
    }
}
