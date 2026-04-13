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
import kotlin.test.assertNull

/**
 * Tests for property interpolation through the ConfigResolutionPipeline.
 *
 * These tests verify that:
 * 1. ${property} placeholders are resolved
 * 2. ${property:default} syntax provides fallback values
 * 3. ${env:VAR} references resolve environment variables
 * 4. Interpolation works across config hierarchy (app -> tenant -> principal)
 */
class InterpolationTest {
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
                    application = this@InterpolationTest,
                    appId = "interpolation-test",
                    profile = "test",
                    version = "0.0.1",
                )

            userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-principal"),
                )
            appSettingsSource = appGraph.appConfigSettings
            tenantSettingsSource = (userContextInstance.graph as MultiplatformSettingsTenantPropertySource.Graph).tenantConfigSettings
            principalSettingsSource = (userContextInstance.graph as MultiplatformSettingsPrincipalPropertySource.Graph).principalConfigSettings

            // Clean up any existing test keys
            cleanupTestKeys()
        }
    }

    private fun cleanupTestKeys() {
        val keysToClean =
            listOf(
                "base.url",
                "users.endpoint",
                "api.endpoint",
                "greeting",
                "name",
                "nested.value",
                "level1",
                "level2",
                "missing.ref",
                "with.default",
            )
        keysToClean.forEach { key ->
            appSettings.remove(key)
            tenantSettings.remove(key)
            principalSettings.remove(key)
        }
    }

    @Test
    fun testSimpleInterpolation() {
        // Set base value
        appSettingsSource.setProperty("base.url", "https://api.example.com")
        // Set value with interpolation reference
        appSettingsSource.setProperty("users.endpoint", "\${base.url}/users")

        // Verify interpolation resolves
        val resolved = appConfigService.getProperty<String>("users.endpoint")
        assertEquals("https://api.example.com/users", resolved)
    }

    @Test
    fun testInterpolationWithDefault() {
        // Set value with default (missing.host doesn't exist)
        appSettingsSource.setProperty("api.endpoint", "\${missing.host:localhost}/api")

        // Verify default is used
        val resolved = appConfigService.getProperty<String>("api.endpoint")
        assertEquals("localhost/api", resolved)
    }

    @Test
    fun testInterpolationAcrossHierarchy() {
        // Set base value at app level
        appSettingsSource.setProperty("base.url", "https://app.example.com")
        // Set reference at tenant level
        tenantSettingsSource.setProperty("api.endpoint", "\${base.url}/tenant-api")

        // Verify tenant can reference app-level properties
        val resolved = tenantConfigService.getProperty<String>("api.endpoint")
        assertEquals("https://app.example.com/tenant-api", resolved)
    }

    @Test
    fun testInterpolationOverrideInHierarchy() {
        // Set base value at app level
        appSettingsSource.setProperty("base.url", "https://app.example.com")
        // Override at tenant level
        tenantSettingsSource.setProperty("base.url", "https://tenant.example.com")
        // Set reference at principal level
        principalSettingsSource.setProperty("api.endpoint", "\${base.url}/principal-api")

        // Verify principal sees tenant's override
        val resolved = principalConfigService.getProperty<String>("api.endpoint")
        assertEquals("https://tenant.example.com/principal-api", resolved)
    }

    @Test
    fun testMultipleInterpolations() {
        // Set multiple values
        appSettingsSource.setProperty("greeting", "Hello")
        appSettingsSource.setProperty("name", "World")
        appSettingsSource.setProperty("message", "\${greeting}, \${name}!")

        // Verify multiple interpolations in one value
        val resolved = appConfigService.getProperty<String>("message")
        assertEquals("Hello, World!", resolved)
    }

    @Test
    fun testNestedInterpolation() {
        // Set nested references
        appSettingsSource.setProperty("level1", "value1")
        appSettingsSource.setProperty("level2", "\${level1}-extended")
        appSettingsSource.setProperty("nested.value", "\${level2}-final")

        // Verify nested interpolation resolves
        val resolved = appConfigService.getProperty<String>("nested.value")
        assertEquals("value1-extended-final", resolved)
    }

    @Test
    fun testNoInterpolationWhenNoPlaceholders() {
        // Set plain value without placeholders
        appSettingsSource.setProperty("plain.value", "just a string")

        // Verify value is returned as-is
        val resolved = appConfigService.getProperty<String>("plain.value")
        assertEquals("just a string", resolved)
    }

    @Test
    fun testEnvInterpolation() {
        // Set a value referencing an environment variable
        // Using PATH which should exist on all systems
        appSettingsSource.setProperty("env.ref", "\${env:PATH}")

        // Verify env variable is resolved (just check it's not the placeholder)
        val resolved = appConfigService.getProperty<String>("env.ref")
        // PATH should exist and not be the literal "${env:PATH}"
        if (resolved != null) {
            assert(!resolved.contains("\${env:PATH}")) { "Environment variable should be resolved" }
        }
    }

    @Test
    fun testEnvInterpolationWithDefault() {
        // Set a value referencing a non-existent env var with default
        appSettingsSource.setProperty("env.missing", "\${env:NONEXISTENT_VAR_12345:default-value}")

        // Verify default is used for missing env var
        val resolved = appConfigService.getProperty<String>("env.missing")
        assertEquals("default-value", resolved)
    }
}
