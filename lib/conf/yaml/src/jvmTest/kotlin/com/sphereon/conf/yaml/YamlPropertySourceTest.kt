/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.conf.yaml

import com.sphereon.core.api.conf.ConfigLevel
import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YamlPropertySourceTest {
    private val nonexistentPath = Path("/nonexistent")

    private fun testConfigDir(): Path {
        val url =
            this::class.java.classLoader.getResource("config/application.yml")
                ?: error("Test resource config/application.yml not found")
        return Path(
            java.io
                .File(url.toURI())
                .parentFile.absolutePath,
        )
    }

    @Test
    fun loadsFromClasspath() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.app",
                sourceLevel = ConfigLevel.APP,
                configLocation = nonexistentPath,
                filePrefix = "application",
            )

        assertEquals("localhost", source.getPropertyAsString("database.host"))
        assertEquals("5432", source.getPropertyAsString("database.port"))
        assertEquals("software", source.getPropertyAsString("kms.providers.default.type"))
    }

    @Test
    fun normalizesHyphenatedKeys() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.app",
                sourceLevel = ConfigLevel.APP,
                configLocation = nonexistentPath,
                filePrefix = "application",
            )

        // pool-size in YAML should be normalized to pool.size
        assertNotNull(
            source.getPropertyAsString("database.pool.size"),
            "Hyphenated YAML key pool-size should be normalized to pool.size",
        )
        assertEquals("10", source.getPropertyAsString("database.pool.size"))
    }

    @Test
    fun profileOverridesBase() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.app",
                sourceLevel = ConfigLevel.APP,
                configLocation = nonexistentPath,
                profile = "production",
                filePrefix = "application",
            )

        // Profile should override base values
        assertEquals("prod-db.example.com", source.getPropertyAsString("database.host"))
        assertEquals("50", source.getPropertyAsString("database.pool.size"))
        // Non-overridden values should still come from base
        assertEquals("5432", source.getPropertyAsString("database.port"))
    }

    @Test
    fun returnsNullForMissingKeys() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.app",
                sourceLevel = ConfigLevel.APP,
                configLocation = nonexistentPath,
                filePrefix = "application",
            )

        assertNull(source.getPropertyAsString("nonexistent.key"))
        assertFalse(source.hasProperty("nonexistent.key"))
    }

    @Test
    fun hasPropertyReturnsTrueForExistingKeys() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.app",
                sourceLevel = ConfigLevel.APP,
                configLocation = nonexistentPath,
                filePrefix = "application",
            )

        assertTrue(source.hasProperty("database.host"))
        assertTrue(source.hasProperty("kms.providers.default.type"))
    }

    @Test
    fun usesLowOrder() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.app",
                sourceLevel = ConfigLevel.APP,
                configLocation = nonexistentPath,
                filePrefix = "application",
            )

        assertEquals(70, source.getOrder())
    }

    @Test
    fun getAllPropertyNamesReturnsBareKeys() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.app",
                sourceLevel = ConfigLevel.APP,
                configLocation = nonexistentPath,
                filePrefix = "application",
            )

        val names = source.getAllPropertyNames()
        assertTrue(names.contains("database.host"))
        assertTrue(names.contains("database.port"))
        assertTrue(names.contains("kms.providers.default.type"))
        // No sphereon.app prefix
        assertFalse(names.any { it.startsWith("sphereon.") })
    }

    @Test
    fun silentlyIgnoresMissingFiles() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.app",
                sourceLevel = ConfigLevel.APP,
                configLocation = nonexistentPath,
                profile = "nonexistent",
                filePrefix = "nonexistent",
            )

        // Should return empty, not throw
        assertTrue(source.getAllPropertyNames().isEmpty())
    }

    @Test
    fun sourceNameIsCorrect() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.app",
                sourceLevel = ConfigLevel.APP,
                configLocation = nonexistentPath,
                filePrefix = "application",
            )

        assertEquals("yaml.app", source.getName())
    }

    // --- Scoped file-based YAML tests ---

    @Test
    fun loadsAppFromConfigDir() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.app",
                sourceLevel = ConfigLevel.APP,
                configLocation = testConfigDir(),
                filePrefix = "application",
            )

        assertEquals("localhost", source.getPropertyAsString("database.host"))
        assertEquals("software", source.getPropertyAsString("kms.providers.default.type"))
    }

    @Test
    fun appProfileOverrideFromConfigDir() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.app",
                sourceLevel = ConfigLevel.APP,
                configLocation = testConfigDir(),
                profile = "staging",
                filePrefix = "application",
            )

        assertEquals("staging-db.example.com", source.getPropertyAsString("database.host"))
        assertEquals("25", source.getPropertyAsString("database.pool.size"))
        // Non-overridden value from base
        assertEquals("5432", source.getPropertyAsString("database.port"))
    }

    @Test
    fun loadsTenantYaml() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.tenant",
                sourceLevel = ConfigLevel.TENANT,
                configLocation = testConfigDir(),
                filePrefix = "tenant",
                subPath = "tenant/test-tenant",
            )

        assertEquals("tenant-logo.png", source.getPropertyAsString("branding.logo"))
        assertEquals("100", source.getPropertyAsString("feature.max.users"))
    }

    @Test
    fun tenantProfileOverride() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.tenant",
                sourceLevel = ConfigLevel.TENANT,
                configLocation = testConfigDir(),
                profile = "staging",
                filePrefix = "tenant",
                subPath = "tenant/test-tenant",
            )

        // Profile override
        assertEquals("200", source.getPropertyAsString("feature.max.users"))
        // Base value still present
        assertEquals("tenant-logo.png", source.getPropertyAsString("branding.logo"))
    }

    @Test
    fun loadsPrincipalYaml() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.principal",
                sourceLevel = ConfigLevel.PRINCIPAL,
                configLocation = testConfigDir(),
                filePrefix = "principal",
                subPath = "tenant/test-tenant/principal/user-1",
            )

        assertEquals("dark", source.getPropertyAsString("preferences.theme"))
        assertEquals("en", source.getPropertyAsString("preferences.language"))
    }

    @Test
    fun tenantNormalizesHyphenatedKeys() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.tenant",
                sourceLevel = ConfigLevel.TENANT,
                configLocation = testConfigDir(),
                filePrefix = "tenant",
                subPath = "tenant/test-tenant",
            )

        // primary-color should be normalized to primary.color
        assertNotNull(
            source.getPropertyAsString("branding.primary.color"),
            "Hyphenated YAML key primary-color should be normalized to primary.color",
        )
    }

    @Test
    fun missingTenantDirReturnsEmpty() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.tenant",
                sourceLevel = ConfigLevel.TENANT,
                configLocation = testConfigDir(),
                filePrefix = "tenant",
                subPath = "tenant/nonexistent-tenant",
            )

        assertTrue(source.getAllPropertyNames().isEmpty())
    }

    // =========== Bracket-Quoted Literal Key Tests ===========

    @Test
    fun bracketQuotedYamlKeysPreserved() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.app",
                sourceLevel = ConfigLevel.APP,
                configLocation = testConfigDir(),
                filePrefix = "application",
            )

        assertEquals("dc+sd-jwt", source.getPropertyAsString("credentials.[TestCredential].format"))
        assertEquals("test_credential", source.getPropertyAsString("credentials.[TestCredential].scope"))
        assertEquals("dc+sd-jwt", source.getPropertyAsString("credentials.[PID].format"))
        assertEquals("eu_pid", source.getPropertyAsString("credentials.[PID].scope"))
    }

    @Test
    fun bracketKeysInGetAllPropertyNames() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.app",
                sourceLevel = ConfigLevel.APP,
                configLocation = testConfigDir(),
                filePrefix = "application",
            )

        val names = source.getAllPropertyNames()
        assertTrue(names.contains("credentials.[TestCredential].format"), "Should contain bracket key [TestCredential]")
        assertTrue(names.contains("credentials.[PID].format"), "Should contain bracket key [PID]")
    }

    @Test
    fun bracketKeysViaClasspath() {
        val source =
            YamlPropertySourceImpl(
                name = "yaml.app",
                sourceLevel = ConfigLevel.APP,
                configLocation = nonexistentPath,
                filePrefix = "application",
            )

        assertEquals("dc+sd-jwt", source.getPropertyAsString("credentials.[TestCredential].format"))
    }

    @Test
    fun configLevelIsCorrect() {
        val appSource =
            YamlPropertySourceImpl(
                name = "yaml.app",
                sourceLevel = ConfigLevel.APP,
                configLocation = nonexistentPath,
                filePrefix = "application",
            )
        assertEquals(ConfigLevel.APP, appSource.configLevel)

        val tenantSource =
            YamlPropertySourceImpl(
                name = "yaml.tenant",
                sourceLevel = ConfigLevel.TENANT,
                configLocation = testConfigDir(),
                filePrefix = "tenant",
                subPath = "tenant/test-tenant",
            )
        assertEquals(ConfigLevel.TENANT, tenantSource.configLevel)
    }
}
