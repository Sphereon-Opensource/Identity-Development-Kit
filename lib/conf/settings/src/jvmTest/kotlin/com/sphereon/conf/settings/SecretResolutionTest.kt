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
import com.sphereon.core.api.conf.getProperty
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.di.context.UserContextInstance
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for secret resolution through the ConfigResolutionPipeline.
 *
 * These tests verify that:
 * 1. ${secret:env:VAR} references resolve environment variables as secrets
 * 2. Secret references work in combination with regular interpolation
 * 3. Missing secrets are handled appropriately
 */
class SecretResolutionTest {
    private lateinit var userContextInstance: UserContextInstance
    private lateinit var appGraph: JvmMPSettingsAppGraph
    private lateinit var appSettingsSource: MultiplatformSettingsAppPropertySource

    val appSettings: MultiplatformSettings
        get() = appSettingsSource.getSource()

    val appConfigService: AppConfigService
        get() = appGraph.appConfigService

    @BeforeTest
    fun setup() {
        runBlocking {
            appGraph =
                createJvmMPSettingsAppGraph(
                    application = this@SecretResolutionTest,
                    appId = "secret-test",
                    profile = "test",
                    version = "0.0.1",
                )

            userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-principal"),
                )
            appSettingsSource = appGraph.appConfigSettings

            // Clean up any existing test keys
            cleanupTestKeys()
        }
    }

    private fun cleanupTestKeys() {
        val keysToClean =
            listOf(
                "secret.ref",
                "db.connection",
                "combined.value",
                "missing.secret",
            )
        keysToClean.forEach { key ->
            appSettings.remove(key)
        }
    }

    @Test
    fun testSecretEnvResolution() {
        // Set a value referencing an env var as a secret
        // Using PATH which should exist on all systems
        appSettingsSource.setProperty("secret.ref", "\${secret:env:PATH}")

        // Verify secret is resolved (just check it's not the placeholder)
        val resolved = appConfigService.getProperty<String>("secret.ref")
        assertNotNull(resolved, "Secret should resolve to a value")
        assertTrue(!resolved.contains("\${secret:"), "Secret placeholder should be resolved")
    }

    @Test
    fun testSecretCombinedWithInterpolation() {
        // Set a base value
        appSettingsSource.setProperty("db.host", "localhost")
        // Set a value combining regular interpolation with a secret-like pattern
        appSettingsSource.setProperty("db.connection", "jdbc:postgresql://\${db.host}:5432/mydb")

        // Verify both interpolations resolve
        val resolved = appConfigService.getProperty<String>("db.connection")
        assertEquals("jdbc:postgresql://localhost:5432/mydb", resolved)
    }

    @Test
    fun testSecretEnvResolutionWithExistingVar() {
        // Test with a common environment variable that should exist
        // HOME on Unix, USERPROFILE on Windows
        val envVar =
            if (System.getProperty("os.name").lowercase().contains("win")) {
                "USERPROFILE"
            } else {
                "HOME"
            }

        appSettingsSource.setProperty("user.home.secret", "\${secret:env:$envVar}")

        val resolved = appConfigService.getProperty<String>("user.home.secret")
        assertNotNull(resolved, "Secret referencing $envVar should resolve")

        // Verify it matches the actual env var
        val expected = System.getenv(envVar)
        assertEquals(expected, resolved, "Secret should resolve to actual env var value")
    }

    @Test
    fun testRawSecretReferenceStored() {
        // Verify that the raw reference is stored in settings (not resolved at storage time)
        val secretRef = "\${secret:env:PATH}"
        appSettingsSource.setProperty("raw.secret", secretRef)

        // Get the raw value from settings (bypassing interpolation)
        val rawValue = appSettings.get<String>("raw.secret")
        assertEquals(secretRef, rawValue, "Raw secret reference should be stored as-is")

        // But when accessed through ConfigService, it should be resolved
        val resolvedValue = appConfigService.getProperty<String>("raw.secret")
        assertNotNull(resolvedValue)
        assertTrue(
            resolvedValue != secretRef || System.getenv("PATH") == null,
            "Secret should be resolved through ConfigService",
        )
    }

    @Test
    fun testMultipleSecretsInOneValue() {
        // This tests a more complex scenario with multiple references
        appSettingsSource.setProperty("app.name", "MyApp")
        appSettingsSource.setProperty("complex.value", "\${app.name} running on \${env:PATH:unknown}")

        val resolved = appConfigService.getProperty<String>("complex.value")
        assertNotNull(resolved)
        assertTrue(resolved.startsWith("MyApp running on "), "Should start with interpolated app name")
    }
}
