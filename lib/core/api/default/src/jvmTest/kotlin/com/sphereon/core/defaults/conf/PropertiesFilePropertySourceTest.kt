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

package com.sphereon.core.defaults.conf

import com.sphereon.di.Order
import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [AbstractPropertiesFilePropertySource.Companion.loadProperties].
 */
class LoadPropertiesTest {
    private fun getTestResourcePath(name: String): Path {
        val resource =
            this::class.java.classLoader.getResource(name)
                ?: throw IllegalArgumentException("Resource not found: $name")
        // Use toURI() to properly handle Windows paths (URL.path has leading slash on Windows)
        return Path(
            java.nio.file.Paths
                .get(resource.toURI())
                .toString(),
        )
    }

    @Test
    fun loadPropertiesLoadsBaseProperties() {
        val configLocation = getTestResourcePath("config")
        val properties =
            AbstractPropertiesFilePropertySource.loadProperties(
                configLocation = configLocation,
                profile = "default",
                filePrefix = "application",
                subPath = null,
            )

        assertEquals("TestApp", properties["app.name"])
        assertEquals("1.0.0", properties["app.version"])
        assertEquals("jdbc:sqlite:mem:test", properties["database.url"])
        assertEquals("INFO", properties["log.level"])
    }

    @Test
    fun loadPropertiesLoadsProfileSpecificPropertiesOverrideBase() {
        val configLocation = getTestResourcePath("config")
        val properties =
            AbstractPropertiesFilePropertySource.loadProperties(
                configLocation = configLocation,
                profile = "production",
                filePrefix = "application",
                subPath = null,
            )

        // Base properties still present
        assertEquals("TestApp", properties["app.name"])
        assertEquals("1.0.0", properties["app.version"])

        // Profile overrides
        assertEquals("jdbc:postgresql://prod:5432/mydb", properties["database.url"])
        assertEquals("WARN", properties["log.level"])

        // Profile-specific additions
        assertEquals("false", properties["feature.debug"])
    }

    @Test
    fun loadPropertiesDefaultProfileSkipsProfileFile() {
        val configLocation = getTestResourcePath("config")
        val properties =
            AbstractPropertiesFilePropertySource.loadProperties(
                configLocation = configLocation,
                profile = "default",
                filePrefix = "application",
                subPath = null,
            )

        // Should not have profile-specific properties
        assertNull(properties["feature.debug"])
    }

    @Test
    fun loadPropertiesWithSubPathLoadsTenantProperties() {
        val configLocation = getTestResourcePath("config")
        val properties =
            AbstractPropertiesFilePropertySource.loadProperties(
                configLocation = configLocation,
                profile = "default",
                filePrefix = "tenant",
                subPath = "tenant/acme-corp",
            )

        assertEquals("Acme Corporation", properties["tenant.name"])
        assertEquals("100", properties["tenant.max.users"])
        assertEquals("acme-logo.png", properties["branding.logo"])
    }

    @Test
    fun loadPropertiesWithSubPathAndProfileLoadsTenantWithOverrides() {
        val configLocation = getTestResourcePath("config")
        val properties =
            AbstractPropertiesFilePropertySource.loadProperties(
                configLocation = configLocation,
                profile = "production",
                filePrefix = "tenant",
                subPath = "tenant/acme-corp",
            )

        // Base tenant properties
        assertEquals("Acme Corporation", properties["tenant.name"])
        assertEquals("acme-logo.png", properties["branding.logo"])

        // Profile overrides
        assertEquals("500", properties["tenant.max.users"])
        assertEquals("true", properties["feature.premium"])
    }

    @Test
    fun loadPropertiesWithPrincipalSubPathLoadsPrincipalProperties() {
        val configLocation = getTestResourcePath("config")
        val properties =
            AbstractPropertiesFilePropertySource.loadProperties(
                configLocation = configLocation,
                profile = "default",
                filePrefix = "principal",
                subPath = "tenant/acme-corp/principal/alice",
            )

        assertEquals("admin", properties["principal.role"])
        assertEquals("dark", properties["principal.theme"])
        assertEquals("true", properties["notifications.email"])
    }

    @Test
    fun loadPropertiesWithPrincipalSubPathAndProfileLoadsPrincipalWithOverrides() {
        val configLocation = getTestResourcePath("config")
        val properties =
            AbstractPropertiesFilePropertySource.loadProperties(
                configLocation = configLocation,
                profile = "production",
                filePrefix = "principal",
                subPath = "tenant/acme-corp/principal/alice",
            )

        // Base principal properties
        assertEquals("admin", properties["principal.role"])
        assertEquals("dark", properties["principal.theme"])
        assertEquals("true", properties["notifications.email"])

        // Profile overrides/additions
        assertEquals("true", properties["notifications.sms"])
        assertEquals("1000", properties["principal.api.limit"])
    }

    @Test
    fun loadPropertiesReturnsEmptyMapForMissingBaseFile() {
        val configLocation = getTestResourcePath("config")
        val properties =
            AbstractPropertiesFilePropertySource.loadProperties(
                configLocation = configLocation,
                profile = "default",
                filePrefix = "nonexistent",
                subPath = null,
            )

        assertTrue(properties.isEmpty())
    }

    @Test
    fun loadPropertiesReturnsEmptyMapForMissingSubPath() {
        val configLocation = getTestResourcePath("config")
        val properties =
            AbstractPropertiesFilePropertySource.loadProperties(
                configLocation = configLocation,
                profile = "default",
                filePrefix = "tenant",
                subPath = "tenant/nonexistent-tenant",
            )

        assertTrue(properties.isEmpty())
    }

    @Test
    fun loadPropertiesHandlesMissingProfileFileGracefully() {
        val configLocation = getTestResourcePath("config")
        val properties =
            AbstractPropertiesFilePropertySource.loadProperties(
                configLocation = configLocation,
                profile = "staging", // No staging profile file exists
                filePrefix = "application",
                subPath = null,
            )

        // Should have base properties but no staging overrides
        assertEquals("TestApp", properties["app.name"])
        assertEquals("jdbc:sqlite:mem:test", properties["database.url"])
    }

    @Test
    fun loadPropertiesReturnsEmptyMapForNonexistentConfigLocation() {
        val configLocation = Path("/nonexistent/config/path")
        val properties =
            AbstractPropertiesFilePropertySource.loadProperties(
                configLocation = configLocation,
                profile = "default",
                filePrefix = "application",
                subPath = null,
            )

        assertTrue(properties.isEmpty())
    }
}

/**
 * Tests for a concrete implementation of [AbstractPropertiesFilePropertySource].
 * Uses a test subclass to verify the inherited behavior.
 */
class AbstractPropertiesFilePropertySourceTest {
    private fun getTestResourcePath(name: String): Path {
        val resource =
            this::class.java.classLoader.getResource(name)
                ?: throw IllegalArgumentException("Resource not found: $name")
        // Use toURI() to properly handle Windows paths (URL.path has leading slash on Windows)
        return Path(
            java.nio.file.Paths
                .get(resource.toURI())
                .toString(),
        )
    }

    // Test subclass for testing abstract class behavior
    private class TestPropertiesFilePropertySource(
        configLocation: Path,
        profile: String,
        filePrefix: String,
        subPath: String? = null,
    ) : AbstractPropertiesFilePropertySource(
            name = "test-properties-file",
            configLocation = configLocation,
            profile = profile,
            filePrefix = filePrefix,
            subPath = subPath,
        )

    @Test
    fun propertySourceHasCorrectName() {
        val configLocation = getTestResourcePath("config")
        val source = TestPropertiesFilePropertySource(configLocation, "default", "application")

        assertEquals("test-properties-file", source.getName())
    }

    @Test
    fun propertySourceHasLowOrder() {
        val configLocation = getTestResourcePath("config")
        val source = TestPropertiesFilePropertySource(configLocation, "default", "application")

        assertEquals(Order.LOW.orderValue, source.getOrder())
    }

    @Test
    fun propertySourceGetPropertyReturnsValue() {
        val configLocation = getTestResourcePath("config")
        val source = TestPropertiesFilePropertySource(configLocation, "default", "application")

        assertEquals("TestApp", source.getProperty("app.name", String::class))
    }

    @Test
    fun propertySourceGetPropertyNormalizesKey() {
        val configLocation = getTestResourcePath("config")
        val source = TestPropertiesFilePropertySource(configLocation, "default", "application")

        // Property key normalization: "appName" -> "app.name"
        assertEquals("TestApp", source.getProperty("appName", String::class))
    }

    @Test
    fun propertySourceGetPropertyAsStringReturnsValue() {
        val configLocation = getTestResourcePath("config")
        val source = TestPropertiesFilePropertySource(configLocation, "default", "application")

        assertEquals("TestApp", source.getPropertyAsString("app.name"))
    }

    @Test
    fun propertySourceGetPropertyReturnsNullForMissingKey() {
        val configLocation = getTestResourcePath("config")
        val source = TestPropertiesFilePropertySource(configLocation, "default", "application")

        assertNull(source.getProperty("nonexistent.key", String::class))
    }

    @Test
    fun propertySourceHasPropertyReturnsTrueForExisting() {
        val configLocation = getTestResourcePath("config")
        val source = TestPropertiesFilePropertySource(configLocation, "default", "application")

        assertTrue(source.hasProperty("app.name"))
    }

    @Test
    fun propertySourceHasPropertyReturnsFalseForMissing() {
        val configLocation = getTestResourcePath("config")
        val source = TestPropertiesFilePropertySource(configLocation, "default", "application")

        assertFalse(source.hasProperty("nonexistent.key"))
    }

    @Test
    fun propertySourceGetAllPropertyNamesReturnsKeys() {
        val configLocation = getTestResourcePath("config")
        val source = TestPropertiesFilePropertySource(configLocation, "default", "application")

        val names = source.getAllPropertyNames()
        assertTrue(names.contains("app.name"))
        assertTrue(names.contains("app.version"))
        assertTrue(names.contains("database.url"))
        assertTrue(names.contains("log.level"))
        assertEquals(4, names.size)
    }

    @Test
    fun propertySourceIsPlatformSupportedReturnsTrue() {
        val configLocation = getTestResourcePath("config")
        val source = TestPropertiesFilePropertySource(configLocation, "default", "application")

        assertTrue(source.isPlatformSupported)
    }

    @Test
    fun propertySourceWithProfileLoadsOverrides() {
        val configLocation = getTestResourcePath("config")
        val source = TestPropertiesFilePropertySource(configLocation, "production", "application")

        // Verify profile overrides
        assertEquals("jdbc:postgresql://prod:5432/mydb", source.getPropertyAsString("database.url"))
        assertEquals("WARN", source.getPropertyAsString("log.level"))

        // Verify base properties still accessible
        assertEquals("TestApp", source.getPropertyAsString("app.name"))
    }

    @Test
    fun propertySourceWithSubPathLoadsTenantProperties() {
        val configLocation = getTestResourcePath("config")
        val source =
            TestPropertiesFilePropertySource(
                configLocation,
                "default",
                "tenant",
                "tenant/acme-corp",
            )

        assertEquals("Acme Corporation", source.getPropertyAsString("tenant.name"))
        assertEquals("100", source.getPropertyAsString("tenant.maxUsers"))
    }

    @Test
    fun propertySourceWithSubPathAndProfileLoadsOverrides() {
        val configLocation = getTestResourcePath("config")
        val source =
            TestPropertiesFilePropertySource(
                configLocation,
                "production",
                "tenant",
                "tenant/acme-corp",
            )

        // Base property
        assertEquals("Acme Corporation", source.getPropertyAsString("tenant.name"))
        // Profile override
        assertEquals("500", source.getPropertyAsString("tenant.maxUsers"))
        // Profile addition
        assertEquals("true", source.getPropertyAsString("feature.premium"))
    }

    @Test
    fun propertySourceComparesToOtherSourcesCorrectly() {
        val configLocation = getTestResourcePath("config")
        val lowSource = TestPropertiesFilePropertySource(configLocation, "default", "application")

        // Create a source with HIGHEST order
        val highSource =
            object : AbstractPropertiesFilePropertySource(
                name = "high-source",
                configLocation = configLocation,
                profile = "default",
                filePrefix = "application",
            ) {
                override fun getOrder(): Int = Order.HIGHEST.orderValue
            }

        // LOW (70) > HIGHEST (10), so compareTo should return positive
        assertTrue(lowSource.compareTo(highSource) > 0)
    }
}

/**
 * Tests for edge cases and error handling.
 */
class PropertiesFilePropertySourceEdgeCasesTest {
    private fun getTestResourcePath(name: String): Path {
        val resource =
            this::class.java.classLoader.getResource(name)
                ?: throw IllegalArgumentException("Resource not found: $name")
        return Path(resource.path)
    }

    private class TestPropertiesFilePropertySource(
        configLocation: Path,
        profile: String,
        filePrefix: String,
        subPath: String? = null,
    ) : AbstractPropertiesFilePropertySource(
            name = "test-properties-file",
            configLocation = configLocation,
            profile = profile,
            filePrefix = filePrefix,
            subPath = subPath,
        )

    @Test
    fun emptyConfigLocationReturnsEmptyProperties() {
        val source =
            TestPropertiesFilePropertySource(
                Path("/nonexistent"),
                "default",
                "application",
            )

        assertTrue(source.getSource().isEmpty())
    }

    @Test
    fun specialCharactersInSubPathHandledCorrectly() {
        val configLocation = getTestResourcePath("config")
        val source =
            TestPropertiesFilePropertySource(
                configLocation,
                "default",
                "principal",
                "tenant/acme-corp/principal/alice",
            )

        // Should load properties even with multiple path segments
        assertTrue(source.hasProperty("principal.role"))
    }

    @Test
    fun propertiesFromNonexistentProfileFileFallsBackToBase() {
        val configLocation = getTestResourcePath("config")
        val source =
            TestPropertiesFilePropertySource(
                configLocation,
                "nonexistent-profile",
                "application",
            )

        // Should have base properties
        assertEquals("TestApp", source.getPropertyAsString("app.name"))
        // But not profile-specific ones
        assertNull(source.getPropertyAsString("feature.debug"))
    }
}

/**
 * Tests for protection-aware properties file sources.
 */
class ProtectedPropertiesFilePropertySourceTest {
    private fun getTestResourcePath(name: String): Path {
        val resource =
            this::class.java.classLoader.getResource(name)
                ?: throw IllegalArgumentException("Resource not found: $name")
        return Path(
            java.nio.file.Paths
                .get(resource.toURI())
                .toString(),
        )
    }

    private class TestProtectedPropertiesFilePropertySource(
        configLocation: Path,
        profile: String,
        filePrefix: String,
        subPath: String? = null,
    ) : AbstractProtectedPropertiesFilePropertySource(
            name = "test-protected-properties-file",
            sourceLevel = com.sphereon.core.api.conf.ConfigLevel.APP,
            configLocation = configLocation,
            profile = profile,
            filePrefix = filePrefix,
            subPath = subPath,
        )

    @Test
    fun protectedPropertiesParsePrefixesAndRegisterProtection() {
        val configLocation = getTestResourcePath("config")
        val source = TestProtectedPropertiesFilePropertySource(configLocation, "default", "protected")

        assertEquals("prod.db.example.com", source.getPropertyAsString("db.host"))
        assertEquals("secret-key", source.getPropertyAsString("api.key"))
        assertEquals("super-secret", source.getPropertyAsString("db.password"))
        assertEquals("42", source.getPropertyAsString("regular.value"))

        val finalProtection = source.getProtection("db.host")
        assertTrue(finalProtection?.isFinal == true)
        assertEquals(com.sphereon.core.api.conf.ConfigLevel.APP, finalProtection?.definedAt)

        val protectedProtection = source.getProtection("api.key")
        assertTrue(protectedProtection?.isInterpolationProtected == true)

        val bothProtection = source.getProtection("db.password")
        assertTrue(bothProtection?.isFinal == true)
        assertTrue(bothProtection?.isInterpolationProtected == true)

        assertNull(source.getProtection("regular.value"))
    }
}
