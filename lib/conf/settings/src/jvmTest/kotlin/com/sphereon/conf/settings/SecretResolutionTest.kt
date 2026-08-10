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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the greenfield configuration pipeline.
 *
 * These tests verify that:
 * 1. Caller-created ${secret:...} references fail closed
 * 2. Regular property and environment interpolation continue to work
 * 3. Raw provider references are never returned by configuration interpolation
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
                "db.host",
                "db.connection",
                "combined.value",
                "missing.secret",
                "user.home.secret",
                "raw.secret",
                "app.name",
                "complex.value",
            )
        keysToClean.forEach { key ->
            appSettings.remove(key)
        }
    }

    @Test
    fun testSecretReferenceFailsClosed() {
        val secretRef = "\${secret:@env:PATH}"

        val denial =
            assertFailsWith<IllegalArgumentException> {
                appSettingsSource.setProperty("secret.ref", secretRef)
            }

        assertEquals("Configuration value is not permitted", denial.message)
        assertNull(appSettings.get<String>("secret.ref"))
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
    fun testSecretReferenceDoesNotReadExistingEnvironmentVariable() {
        val envVar =
            if (System.getProperty("os.name").lowercase().contains("win")) {
                "USERPROFILE"
            } else {
                "HOME"
            }

        val secretRef = "\${secret:@env:$envVar}"

        val denial =
            assertFailsWith<IllegalArgumentException> {
                appSettingsSource.setProperty("user.home.secret", secretRef)
            }

        assertEquals("Configuration value is not permitted", denial.message)
        assertNull(appSettings.get<String>("user.home.secret"))
    }

    @Test
    fun testRawSecretReferenceIsRejectedBeforeStorage() {
        val secretRef = "\${secret:@env:PATH}"

        val denial =
            assertFailsWith<IllegalArgumentException> {
                appSettingsSource.setProperty("raw.secret", secretRef)
            }

        assertEquals("Configuration value is not permitted", denial.message)
        assertNull(appSettings.get<String>("raw.secret"))
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
