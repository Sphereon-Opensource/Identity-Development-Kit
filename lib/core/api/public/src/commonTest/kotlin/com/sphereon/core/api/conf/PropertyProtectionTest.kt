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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ========== PropertyProtection Tests ==========

class PropertyProtectionTest {
    @Test
    fun noneHasNoRestrictions() {
        val protection = PropertyProtection.NONE
        assertFalse(protection.isFinal)
        assertFalse(protection.isInterpolationProtected)
        assertFalse(protection.hasRestrictions)
    }

    @Test
    fun finalHasFinalRestriction() {
        val protection = PropertyProtection.FINAL
        assertTrue(protection.isFinal)
        assertFalse(protection.isInterpolationProtected)
        assertTrue(protection.hasRestrictions)
    }

    @Test
    fun protectedHasInterpolationRestriction() {
        val protection = PropertyProtection.PROTECTED
        assertFalse(protection.isFinal)
        assertTrue(protection.isInterpolationProtected)
        assertTrue(protection.hasRestrictions)
    }

    @Test
    fun finalAndProtectedHasBothRestrictions() {
        val protection = PropertyProtection.FINAL_AND_PROTECTED
        assertTrue(protection.isFinal)
        assertTrue(protection.isInterpolationProtected)
        assertTrue(protection.hasRestrictions)
    }

    @Test
    fun withScopeSetsDefinedAt() {
        val protection = PropertyProtection.FINAL.withScope(ConfigLevel.TENANT)
        assertEquals(ConfigLevel.TENANT, protection.definedAt)
        assertTrue(protection.isFinal)
    }
}

// ========== DotPrefixProtectionParser Tests ==========

class DotPrefixProtectionParserTest {
    private val parser = DotPrefixProtectionParser()

    @Test
    fun parsesRegularKey() {
        val result = parser.parse("db.password")
        assertEquals("db.password", result.canonicalKey)
        assertFalse(result.protection.isFinal)
        assertFalse(result.protection.isInterpolationProtected)
    }

    @Test
    fun parsesFinalPrefix() {
        val result = parser.parse("final.db.host")
        assertEquals("db.host", result.canonicalKey)
        assertTrue(result.protection.isFinal)
        assertFalse(result.protection.isInterpolationProtected)
    }

    @Test
    fun parsesProtectedPrefix() {
        val result = parser.parse("protected.db.password")
        assertEquals("db.password", result.canonicalKey)
        assertFalse(result.protection.isFinal)
        assertTrue(result.protection.isInterpolationProtected)
    }

    @Test
    fun parsesFinalProtectedPrefix() {
        val result = parser.parse("final.protected.internal.api.key")
        assertEquals("internal.api.key", result.canonicalKey)
        assertTrue(result.protection.isFinal)
        assertTrue(result.protection.isInterpolationProtected)
    }

    @Test
    fun parsesProtectedFinalPrefix() {
        val result = parser.parse("protected.final.internal.api.key")
        assertEquals("internal.api.key", result.canonicalKey)
        assertTrue(result.protection.isFinal)
        assertTrue(result.protection.isInterpolationProtected)
    }

    @Test
    fun caseInsensitiveFinal() {
        val result = parser.parse("FINAL.db.host")
        assertEquals("db.host", result.canonicalKey)
        assertTrue(result.protection.isFinal)
    }

    @Test
    fun caseInsensitiveProtected() {
        val result = parser.parse("PROTECTED.api.key")
        assertEquals("api.key", result.canonicalKey)
        assertTrue(result.protection.isInterpolationProtected)
    }

    @Test
    fun caseInsensitiveFinalProtected() {
        val result = parser.parse("FINAL.PROTECTED.secret")
        assertEquals("secret", result.canonicalKey)
        assertTrue(result.protection.isFinal)
        assertTrue(result.protection.isInterpolationProtected)
    }

    @Test
    fun emptyKeyAfterPrefix() {
        val result = parser.parse("final.")
        assertEquals("", result.canonicalKey)
        assertTrue(result.protection.isFinal)
    }
}

// ========== EnvPrefixProtectionParser Tests ==========

class EnvPrefixProtectionParserTest {
    private val parser = EnvPrefixProtectionParser()

    @Test
    fun parsesRegularKey() {
        val result = parser.parse("DB_PASSWORD")
        assertEquals("DB_PASSWORD", result.canonicalKey)
        assertEquals(PropertyProtection.NONE, result.protection)
    }

    @Test
    fun parsesFinalPrefix() {
        val result = parser.parse("FINAL_DB_HOST")
        assertEquals("DB_HOST", result.canonicalKey)
        assertTrue(result.protection.isFinal)
        assertFalse(result.protection.isInterpolationProtected)
    }

    @Test
    fun parsesProtectedPrefix() {
        val result = parser.parse("PROTECTED_DB_PASSWORD")
        assertEquals("DB_PASSWORD", result.canonicalKey)
        assertFalse(result.protection.isFinal)
        assertTrue(result.protection.isInterpolationProtected)
    }

    @Test
    fun parsesFinalProtectedPrefix() {
        val result = parser.parse("FINAL_PROTECTED_API_KEY")
        assertEquals("API_KEY", result.canonicalKey)
        assertTrue(result.protection.isFinal)
        assertTrue(result.protection.isInterpolationProtected)
    }

    @Test
    fun parsesProtectedFinalPrefix() {
        val result = parser.parse("PROTECTED_FINAL_API_KEY")
        assertEquals("API_KEY", result.canonicalKey)
        assertTrue(result.protection.isFinal)
        assertTrue(result.protection.isInterpolationProtected)
    }

    @Test
    fun caseInsensitiveParsing() {
        val result = parser.parse("final_db_host")
        assertEquals("db_host", result.canonicalKey)
        assertTrue(result.protection.isFinal)
    }
}

// ========== ProtectedMutableMapPropertySource Tests ==========

class ProtectedMutableMapPropertySourceTest {
    @Test
    fun tenantAndPrincipalWritesRejectDirectEnvironmentReferences() {
        val tenant = ProtectedMutableMapPropertySource("tenant", ConfigLevel.TENANT)
        val principal = ProtectedMutableMapPropertySource("principal", ConfigLevel.PRINCIPAL)

        val tenantError =
            assertFailsWith<IllegalArgumentException> {
                tenant.addProperty("endpoint", "https://\${env:INTERNAL_HOST}/v1")
            }
        val principalError =
            assertFailsWith<IllegalArgumentException> {
                principal.addProtectedProperty(
                    "endpoint",
                    "\${env:INTERNAL_HOST:fallback}",
                    PropertyProtection.PROTECTED,
                )
            }

        assertEquals(tenantError.message, principalError.message)
        assertFalse(tenantError.message.orEmpty().contains("INTERNAL_HOST"))
        assertFalse(tenant.hasProperty("endpoint"))
        assertFalse(principal.hasProperty("endpoint"))
    }

    @Test
    fun lowerScopeWritesRejectNestedMapListAndArrayEnvironmentReferences() {
        val source = ProtectedMutableMapPropertySource("tenant", ConfigLevel.TENANT)
        val values =
            listOf(
                mapOf("nested" to listOf("\${env:INTERNAL_HOST}")),
                listOf(mapOf("nested" to "\${env:INTERNAL_HOST}")),
                arrayOf<Any>("safe", mapOf("nested" to "\${env:INTERNAL_HOST}")),
            )

        values.forEachIndexed { index, value ->
            val error =
                assertFailsWith<IllegalArgumentException> {
                    source.addProperty("nested.$index", value)
                }
            assertFalse(error.message.orEmpty().contains("INTERNAL_HOST"))
            assertFalse(source.hasProperty("nested.$index"))
        }
    }

    @Test
    fun everyScopeRejectsNestedExternalSecretReferencesBeforeWrite() {
        listOf(ConfigLevel.APP, ConfigLevel.TENANT, ConfigLevel.PRINCIPAL).forEach { scope ->
            val source = ProtectedMutableMapPropertySource("source-$scope", scope)
            val value = mapOf("nested" to listOf("\${secret:@env:INTERNAL_HOST}"))

            val error =
                assertFailsWith<IllegalArgumentException> {
                    source.addProperty("provider.reference", value)
                }

            assertEquals("Configuration value is not permitted", error.message)
            assertFalse(source.hasProperty("provider.reference"))
        }
    }

    @Test
    fun lowerScopeWriteValidationFailsClosedWhenTraversalBoundsAreExceeded() {
        val source = ProtectedMutableMapPropertySource("tenant", ConfigLevel.TENANT)
        var value: Any = "safe"
        repeat(34) {
            value = listOf(value)
        }

        assertFailsWith<IllegalArgumentException> {
            source.addProperty("nested.too-deep", value)
        }
        assertFalse(source.hasProperty("nested.too-deep"))
    }

    @Test
    fun exposedProtectedMapIsDefensiveAndCannotMutateRegisteredValues() {
        val source =
            ProtectedMutableMapPropertySource("tenant", ConfigLevel.TENANT).apply {
                addProperty("service.endpoint", "safe")
            }

        source.getSource()["service.endpoint"] = "\${env:PATH}"
        source.getSource()["service.late"] = "\${env:PATH}"

        assertEquals("safe", source.getPropertyAsString("service.endpoint"))
        assertFalse(source.hasProperty("service.late"))
    }

    @Test
    fun appWritePreservesDeclaredEnvironmentReference() {
        val source = ProtectedMutableMapPropertySource("app", ConfigLevel.APP)

        source.addProperty("endpoint", "\${env:EXTERNAL_BASE_URL:https://example.com}")

        assertEquals("\${env:EXTERNAL_BASE_URL:https://example.com}", source.getPropertyAsString("endpoint"))
    }

    @Test
    fun addPropertyWithoutPrefixHasNoProtection() {
        val source = ProtectedMutableMapPropertySource("test", ConfigLevel.APP)
        source.addProperty("db.host", "localhost")

        assertEquals("localhost", source.getPropertyAsString("db.host"))
        assertNull(source.getProtection("db.host"))
    }

    @Test
    fun addPropertyWithFinalPrefixHasFinalProtection() {
        val source = ProtectedMutableMapPropertySource("test", ConfigLevel.APP)
        source.addProperty("final.db.host", "localhost")

        assertEquals("localhost", source.getPropertyAsString("db.host"))
        val protection = source.getProtection("db.host")
        assertNotNull(protection)
        assertTrue(protection.isFinal)
        assertEquals(ConfigLevel.APP, protection.definedAt)
    }

    @Test
    fun addPropertyWithProtectedPrefixHasProtectedProtection() {
        val source = ProtectedMutableMapPropertySource("test", ConfigLevel.APP)
        source.addProperty("protected.api.key", "secret-key")

        assertEquals("secret-key", source.getPropertyAsString("api.key"))
        val protection = source.getProtection("api.key")
        assertNotNull(protection)
        assertTrue(protection.isInterpolationProtected)
    }

    @Test
    fun addPropertyWithFinalProtectedPrefixHasBothProtections() {
        val source = ProtectedMutableMapPropertySource("test", ConfigLevel.APP)
        source.addProperty("final.protected.db.password", "secret")

        assertEquals("secret", source.getPropertyAsString("db.password"))
        val protection = source.getProtection("db.password")
        assertNotNull(protection)
        assertTrue(protection.isFinal)
        assertTrue(protection.isInterpolationProtected)
    }

    @Test
    fun canSetReturnsTrueForNonProtectedProperty() {
        val source = ProtectedMutableMapPropertySource("test", ConfigLevel.APP)
        source.addProperty("db.host", "localhost")

        assertTrue(source.canSet("db.host", ConfigLevel.APP))
        assertTrue(source.canSet("db.host", ConfigLevel.TENANT))
        assertTrue(source.canSet("db.host", ConfigLevel.PRINCIPAL))
    }

    @Test
    fun canSetReturnsFalseForFinalPropertyFromLowerScope() {
        val source = ProtectedMutableMapPropertySource("test", ConfigLevel.APP)
        source.addProperty("final.db.host", "localhost")

        // APP can still set (same scope)
        assertTrue(source.canSet("db.host", ConfigLevel.APP))
        // TENANT and PRINCIPAL cannot override (lower scope)
        assertFalse(source.canSet("db.host", ConfigLevel.TENANT))
        assertFalse(source.canSet("db.host", ConfigLevel.PRINCIPAL))
    }

    @Test
    fun canInterpolateReturnsTrueForNonProtectedProperty() {
        val source = ProtectedMutableMapPropertySource("test", ConfigLevel.APP)
        source.addProperty("db.host", "localhost")

        assertTrue(source.canInterpolate("db.host", ConfigLevel.APP))
        assertTrue(source.canInterpolate("db.host", ConfigLevel.TENANT))
        assertTrue(source.canInterpolate("db.host", ConfigLevel.PRINCIPAL))
    }

    @Test
    fun canInterpolateReturnsFalseForProtectedPropertyFromLowerScope() {
        val source = ProtectedMutableMapPropertySource("test", ConfigLevel.APP)
        source.addProperty("protected.db.password", "secret")

        // APP can interpolate (same scope)
        assertTrue(source.canInterpolate("db.password", ConfigLevel.APP))
        // TENANT and PRINCIPAL cannot interpolate (lower scope)
        assertFalse(source.canInterpolate("db.password", ConfigLevel.TENANT))
        assertFalse(source.canInterpolate("db.password", ConfigLevel.PRINCIPAL))
    }

    @Test
    fun addProtectedPropertySetsExplicitProtection() {
        val source = ProtectedMutableMapPropertySource("test", ConfigLevel.TENANT)
        source.addProtectedProperty("api.key", "secret", PropertyProtection.FINAL_AND_PROTECTED)

        assertEquals("secret", source.getPropertyAsString("api.key"))
        val protection = source.getProtection("api.key")
        assertNotNull(protection)
        assertTrue(protection.isFinal)
        assertTrue(protection.isInterpolationProtected)
        assertEquals(ConfigLevel.TENANT, protection.definedAt)
    }

    @Test
    fun getAllProtectionsReturnsAllProtectedKeys() {
        val source = ProtectedMutableMapPropertySource("test", ConfigLevel.APP)
        source.addProperty("final.db.host", "localhost")
        source.addProperty("protected.api.key", "secret")
        source.addProperty("regular.prop", "value")

        val protections = source.getAllProtections()
        assertEquals(2, protections.size)
        assertTrue(protections.containsKey("db.host"))
        assertTrue(protections.containsKey("api.key"))
        assertFalse(protections.containsKey("regular.prop"))
    }
}

// ========== ProtectedPropertySourcesResolver Tests ==========

class ProtectedPropertySourcesResolverTest {
    @Test
    fun getProtectionReturnsNullForNonProtectedProperty() {
        val source = ProtectedMutableMapPropertySource("app", ConfigLevel.APP)
        source.addProperty("db.host", "localhost")

        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = ProtectedPropertySourcesResolver(sources, ConfigLevel.APP)

        assertNull(resolver.getProtection("db.host"))
    }

    @Test
    fun getProtectionReturnsProtectionForProtectedProperty() {
        val source = ProtectedMutableMapPropertySource("app", ConfigLevel.APP)
        source.addProperty("final.db.host", "localhost")

        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = ProtectedPropertySourcesResolver(sources, ConfigLevel.APP)

        val protection = resolver.getProtection("db.host")
        assertNotNull(protection)
        assertTrue(protection.isFinal)
    }

    @Test
    fun canSetPropertyReturnsOkForNonFinalProperty() {
        val source = ProtectedMutableMapPropertySource("app", ConfigLevel.APP)
        source.addProperty("db.host", "localhost")

        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = ProtectedPropertySourcesResolver(sources, ConfigLevel.TENANT)

        val result = resolver.canSetProperty("db.host")
        assertTrue(result.isOk)
    }

    @Test
    fun canSetPropertyReturnsErrorForFinalPropertyFromLowerScope() {
        val source = ProtectedMutableMapPropertySource("app", ConfigLevel.APP)
        source.addProperty("final.db.host", "localhost")

        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = ProtectedPropertySourcesResolver(sources, ConfigLevel.TENANT)

        val result = resolver.canSetProperty("db.host")
        assertTrue(result.isErr)
        assertTrue(
            result.error.message.defaultMessage
                .contains("FINAL"),
        )
    }

    @Test
    fun canInterpolatePropertyReturnsOkForNonProtectedProperty() {
        val source = ProtectedMutableMapPropertySource("app", ConfigLevel.APP)
        source.addProperty("db.host", "localhost")

        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = ProtectedPropertySourcesResolver(sources, ConfigLevel.APP)

        val result = resolver.canInterpolateProperty("db.host", ConfigLevel.TENANT)
        assertTrue(result.isOk)
    }

    @Test
    fun canInterpolatePropertyReturnsErrorForProtectedPropertyFromLowerScope() {
        val source = ProtectedMutableMapPropertySource("app", ConfigLevel.APP)
        source.addProperty("protected.db.password", "secret")

        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = ProtectedPropertySourcesResolver(sources, ConfigLevel.APP)

        // APP can interpolate its own protected property
        val appResult = resolver.canInterpolateProperty("db.password", ConfigLevel.APP)
        assertTrue(appResult.isOk)

        // TENANT cannot interpolate APP's protected property
        val tenantResult = resolver.canInterpolateProperty("db.password", ConfigLevel.TENANT)
        assertTrue(tenantResult.isErr)
        assertTrue(
            tenantResult.error.message.defaultMessage
                .contains("PROTECTED"),
        )
    }

    @Test
    fun nonProtectedPropertyCanBeAccessedFromAnyScope() {
        val source = ProtectedMutableMapPropertySource("app", ConfigLevel.APP)
        source.addProperty("app.theme", "light")

        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = ProtectedPropertySourcesResolver(sources, ConfigLevel.TENANT)

        assertTrue(resolver.canSetProperty("app.theme").isOk)
        assertTrue(resolver.canInterpolateProperty("app.theme", ConfigLevel.TENANT).isOk)
        assertTrue(resolver.canInterpolateProperty("app.theme", ConfigLevel.PRINCIPAL).isOk)
    }
}

// ========== ConfigLevel Protection Tests ==========

class ConfigLevelProtectionTest {
    @Test
    fun fromStringParsesKey() {
        assertEquals(ConfigLevel.APP, ConfigLevel.fromString("app"))
        assertEquals(ConfigLevel.TENANT, ConfigLevel.fromString("tenant"))
        assertEquals(ConfigLevel.PRINCIPAL, ConfigLevel.fromString("principal"))
    }

    @Test
    fun fromStringParsesName() {
        assertEquals(ConfigLevel.APP, ConfigLevel.fromString("APP"))
        assertEquals(ConfigLevel.TENANT, ConfigLevel.fromString("TENANT"))
        assertEquals(ConfigLevel.PRINCIPAL, ConfigLevel.fromString("PRINCIPAL"))
    }

    @Test
    fun fromStringIsCaseInsensitive() {
        assertEquals(ConfigLevel.APP, ConfigLevel.fromString("App"))
        assertEquals(ConfigLevel.TENANT, ConfigLevel.fromString("TeNaNt"))
    }

    @Test
    fun fromStringReturnsNullForInvalidValue() {
        assertNull(ConfigLevel.fromString("invalid"))
        assertNull(ConfigLevel.fromString(""))
    }

    @Test
    fun fromLevelReturnsCorrectLevel() {
        assertEquals(ConfigLevel.APP, ConfigLevel.fromLevel(10))
        assertEquals(ConfigLevel.TENANT, ConfigLevel.fromLevel(20))
        assertEquals(ConfigLevel.PRINCIPAL, ConfigLevel.fromLevel(30))
    }

    @Test
    fun fromLevelReturnsNullForInvalidLevel() {
        assertNull(ConfigLevel.fromLevel(15))
        assertNull(ConfigLevel.fromLevel(0))
    }

    @Test
    fun levelPropertyIsAccessible() {
        assertEquals(10, ConfigLevel.APP.level)
        assertEquals(20, ConfigLevel.TENANT.level)
        assertEquals(30, ConfigLevel.PRINCIPAL.level)
    }

    @Test
    fun keyPropertyIsAccessible() {
        assertEquals("app", ConfigLevel.APP.key)
        assertEquals("tenant", ConfigLevel.TENANT.key)
        assertEquals("principal", ConfigLevel.PRINCIPAL.key)
    }
}

// ========== PropertyInterpolator Protection Integration Tests ==========

class PropertyInterpolatorProtectionTest {
    @Test
    fun interpolateWithProtectedPropertyAtAppScope() =
        runTest {
            val source = ProtectedMutableMapPropertySource("app", ConfigLevel.APP)
            source.addProperty("final.protected.db.password", "super-secret")
            source.addProperty("connection.string", "jdbc:mysql://\${db.password}")

            val sources = DefaultPropertySources().apply { add(source) }
            val resolver = ProtectedPropertySourcesResolver(sources, ConfigLevel.APP)
            val interpolator = DefaultPropertyInterpolator()

            // APP can interpolate its own protected value
            val appResult = interpolator.interpolate("\${db.password}", resolver, ConfigLevel.APP)
            assertTrue(appResult.isOk)
            assertEquals("super-secret", appResult.value)
        }

    @Test
    fun interpolateDeniedForProtectedPropertyFromLowerScope() =
        runTest {
            val source = ProtectedMutableMapPropertySource("app", ConfigLevel.APP)
            source.addProperty("protected.db.password", "secret")

            val sources = DefaultPropertySources().apply { add(source) }
            val resolver = ProtectedPropertySourcesResolver(sources, ConfigLevel.APP)
            val interpolator = DefaultPropertyInterpolator()

            // TENANT cannot interpolate - denied BEFORE value is read
            val tenantResult = interpolator.interpolate("\${db.password}", resolver, ConfigLevel.TENANT)
            assertTrue(tenantResult.isErr)
            assertFalse(
                tenantResult.error.message.defaultMessage
                    .contains("db.password")
            )
            assertFalse(
                tenantResult.error.message.defaultMessage
                    .contains("secret")
            )
        }

    @Test
    fun interpolateScopePatternDeniedForProtectedProperty() =
        runTest {
            val source = ProtectedMutableMapPropertySource("app", ConfigLevel.APP)
            source.addProperty("protected.db.password", "secret")

            val sources = DefaultPropertySources().apply { add(source) }
            val resolver = ProtectedPropertySourcesResolver(sources, ConfigLevel.APP)
            val interpolator = DefaultPropertyInterpolator()

            // TENANT cannot use ${app:db.password} interpolation
            val result = interpolator.interpolate("\${app:db.password}", resolver, ConfigLevel.TENANT)
            assertTrue(result.isErr)
            assertFalse(
                result.error.message.defaultMessage
                    .contains("db.password")
            )
            assertFalse(
                result.error.message.defaultMessage
                    .contains("secret")
            )
        }

    @Test
    fun interpolateNonProtectedPropertyAllowedFromAnyScope() =
        runTest {
            val source = ProtectedMutableMapPropertySource("app", ConfigLevel.APP)
            source.addProperty("app.theme", "dark")

            val sources = DefaultPropertySources().apply { add(source) }
            val resolver = ProtectedPropertySourcesResolver(sources, ConfigLevel.APP)
            val interpolator = DefaultPropertyInterpolator()

            // All scopes can interpolate non-protected properties
            val appResult = interpolator.interpolate("\${app.theme}", resolver, ConfigLevel.APP)
            assertTrue(appResult.isOk)
            assertEquals("dark", appResult.value)

            val tenantResult = interpolator.interpolate("\${app.theme}", resolver, ConfigLevel.TENANT)
            assertTrue(tenantResult.isOk)
            assertEquals("dark", tenantResult.value)

            val principalResult = interpolator.interpolate("\${app.theme}", resolver, ConfigLevel.PRINCIPAL)
            assertTrue(principalResult.isOk)
            assertEquals("dark", principalResult.value)
        }

    @Test
    fun protectedAndAbsentValuesUseTheSameDefaultWithoutReadingProtectedValue() =
        runTest {
            val source = ProtectedMutableMapPropertySource("app", ConfigLevel.APP)
            source.addProperty("protected.db.password", "secret")

            val sources = DefaultPropertySources().apply { add(source) }
            val resolver = ProtectedPropertySourcesResolver(sources, ConfigLevel.APP)
            val interpolator = DefaultPropertyInterpolator()

            val protected = interpolator.interpolate("\${db.password:fallback}", resolver, ConfigLevel.TENANT)
            val absent = interpolator.interpolate("\${db.absent:fallback}", resolver, ConfigLevel.TENANT)

            assertTrue(protected.isOk)
            assertTrue(absent.isOk)
            assertEquals("fallback", protected.value)
            assertEquals(protected.value, absent.value)
        }

    @Test
    fun interpolateWithoutScopeCheckWorksAsUsual() =
        runTest {
            val source = ProtectedMutableMapPropertySource("app", ConfigLevel.APP)
            source.addProperty("protected.db.password", "secret")
            source.addProperty("app.name", "MyApp")

            val sources = DefaultPropertySources().apply { add(source) }
            val resolver = ProtectedPropertySourcesResolver(sources, ConfigLevel.APP)
            val interpolator = DefaultPropertyInterpolator()

            // Without scope parameter, interpolation works for all properties
            val result = interpolator.interpolate("\${app.name}", resolver)
            assertTrue(result.isOk)
            assertEquals("MyApp", result.value)
        }
}

// ========== ProtectionErrors Tests ==========

class ProtectionErrorsTest {
    @Test
    fun overrideNotAllowedErrorContainsRelevantInfo() {
        val error = ProtectionErrors.overrideNotAllowed("db.host", ConfigLevel.APP, ConfigLevel.TENANT)

        assertTrue(error.message.defaultMessage.contains("db.host"))
        assertTrue(error.message.defaultMessage.contains("FINAL"))
        assertTrue(error.message.defaultMessage.contains("APP"))
        assertTrue(error.message.defaultMessage.contains("TENANT"))
    }

    @Test
    fun interpolationNotAllowedErrorContainsRelevantInfo() {
        val error = ProtectionErrors.interpolationNotAllowed("db.password", ConfigLevel.APP, ConfigLevel.TENANT)

        assertTrue(error.message.defaultMessage.contains("db.password"))
        assertTrue(error.message.defaultMessage.contains("PROTECTED"))
        assertTrue(error.message.defaultMessage.contains("APP"))
        assertTrue(error.message.defaultMessage.contains("TENANT"))
    }
}

// ========== ProtectedPropertyValue Tests ==========

class ProtectedPropertyValueTest {
    @Test
    fun holdsValueAndProtection() {
        val protection = PropertyProtection.FINAL.withScope(ConfigLevel.APP)
        val protectedValue = ProtectedPropertyValue("secret-value", protection)

        assertEquals("secret-value", protectedValue.value)
        assertTrue(protectedValue.protection.isFinal)
        assertEquals(ConfigLevel.APP, protectedValue.protection.definedAt)
    }
}

// ========== ParsedProtectedKey Tests ==========

class ParsedProtectedKeyTest {
    @Test
    fun holdsCanonicalKeyAndProtection() {
        val protection = PropertyProtection(isFinal = true, isInterpolationProtected = false)
        val parsed = ParsedProtectedKey("db.host", protection)

        assertEquals("db.host", parsed.canonicalKey)
        assertTrue(parsed.protection.isFinal)
        assertFalse(parsed.protection.isInterpolationProtected)
    }
}

// ========== DefaultProtectionKeyParser Tests ==========

class DefaultProtectionKeyParserTest {
    @Test
    fun defaultParserIsDotPrefixParser() {
        val result = DefaultProtectionKeyParser.parse("final.db.host")
        assertEquals("db.host", result.canonicalKey)
        assertTrue(result.protection.isFinal)
    }
}

// ========== DefaultEnvProtectionKeyParser Tests ==========

class DefaultEnvProtectionKeyParserTest {
    @Test
    fun defaultEnvParserIsEnvPrefixParser() {
        val result = DefaultEnvProtectionKeyParser.parse("FINAL_DB_HOST")
        assertEquals("DB_HOST", result.canonicalKey)
        assertTrue(result.protection.isFinal)
    }
}

// ========== ProtectedPropertyResolverFactory Tests ==========

class ProtectedPropertyResolverFactoryTest {
    @Test
    fun forAppCreatesAppLevelResolver() {
        val sources = DefaultPropertySources()
        val resolver = ProtectedPropertyResolverFactory.forApp(sources)

        assertEquals(ConfigLevel.APP, resolver.resolverLevel)
    }

    @Test
    fun forTenantCreatesTenantLevelResolver() {
        val sources = DefaultPropertySources()
        val resolver = ProtectedPropertyResolverFactory.forTenant(sources)

        assertEquals(ConfigLevel.TENANT, resolver.resolverLevel)
    }

    @Test
    fun forPrincipalCreatesPrincipalLevelResolver() {
        val sources = DefaultPropertySources()
        val resolver = ProtectedPropertyResolverFactory.forPrincipal(sources)

        assertEquals(ConfigLevel.PRINCIPAL, resolver.resolverLevel)
    }
}

class ProtectedDirectVisibilityTest {
    private fun sourcesWithProtectedAppAndEnvironment(): DefaultPropertySources {
        val app =
            ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                addProtectedProperty("service.token", "server-owned", PropertyProtection.PROTECTED)
                addProperty("service.public", "visible")
            }
        return DefaultPropertySources(
            mutableListOf(
                StaticProtectedEnvPropertySourceObject,
                app,
            ),
        )
    }

    @Test
    fun tenantAndPrincipalDirectReadsHideProtectedAppAndEnvironmentSources() {
        val sources = sourcesWithProtectedAppAndEnvironment()

        listOf(ConfigLevel.TENANT, ConfigLevel.PRINCIPAL).forEach { level ->
            val resolver = ProtectedPropertySourcesResolver(sources, level)

            assertFalse(resolver.containsProperty("service.token"))
            assertNull(resolver.getPropertyAsString("service.token"))
            assertNull(resolver.getPropertyAtScope("service.token", String::class, ConfigLevel.APP))
            assertFalse(resolver.getAllProperties().containsKey("service.token"))
            assertFalse(resolver.getSubProperties(setOf("service"), stripPrefix = false).containsKey("service.token"))

            assertFalse(resolver.containsProperty("PATH"))
            assertNull(resolver.getPropertyAsString("PATH"))
            assertNull(resolver.getPropertyAtScope("PATH", String::class, ConfigLevel.APP))
            assertFalse(resolver.getAllProperties().containsKey("path"))
            assertFalse(resolver.getSubProperties(setOf("path"), stripPrefix = false).containsKey("path"))

            assertEquals("visible", resolver.getPropertyAsString("service.public"))
        }
    }

    @Test
    fun interpolateFalseFactoryStillHidesProtectedAndEnvironmentValues() {
        val resolver =
            PropertyResolverFactory.create(
                propertySources = sourcesWithProtectedAppAndEnvironment(),
                interpolator = null,
                resolverLevel = ConfigLevel.TENANT,
            )

        assertFalse(resolver.containsProperty("service.token"))
        assertNull(resolver.getPropertyAsString("service.token"))
        assertFalse(resolver.containsProperty("PATH"))
        assertNull(resolver.getPropertyAsString("PATH"))
        assertEquals("visible", resolver.getPropertyAsString("service.public"))
    }
}

class ScopedPropertySourceWrapperContainmentTest {
    @Test
    fun scopedCollectionIsValidatedAsAnyBeforeStringConversionAtRegistration() {
        val scopedCollection =
            object :
                MutableMapPropertySource("tenant-collection"),
                ScopedPropertySource<MutableMap<String, Any>> {
                override val configLevel: ConfigLevel = ConfigLevel.TENANT

                override fun getPropertyAsString(name: String): String? = "opaque"
            }.apply {
                addProperty(
                    "service.options",
                    mapOf("nested" to listOf("safe", "\${env:PATH}")),
                )
            }

        assertFailsWith<IllegalArgumentException> {
            DefaultPropertySources(mutableListOf(scopedCollection))
        }
    }

    @Test
    fun postRegistrationBackingMutationFailsClosedForLowerScopeReads() {
        val backing =
            MutableMapPropertySource("tenant-backing").apply {
                addProperty("service.endpoint", "safe")
            }
        val wrapped = ScopedPropertySourceWrapper(backing, ConfigLevel.TENANT)
        val resolver =
            ProtectedPropertySourcesResolver(
                DefaultPropertySources(mutableListOf(wrapped)),
                ConfigLevel.TENANT,
            )

        backing.getSource()["service.endpoint"] = mapOf("nested" to listOf("\${env:PATH}"))

        assertFalse(resolver.containsProperty("service.endpoint"))
        assertFailsWith<IllegalStateException> {
            resolver.getPropertyAsString("service.endpoint")
        }
        assertFailsWith<IllegalStateException> {
            resolver.getSubProperties(setOf("service"), stripPrefix = false)
        }
    }

    @Test
    fun wrapperPreservesProtectionAndRefreshMetadata() {
        val protected =
            ProtectedMutableMapPropertySource("protected", ConfigLevel.APP).apply {
                addProtectedProperty("service.token", "server-owned", PropertyProtection.PROTECTED)
            }
        val protectedWrapper = ScopedPropertySourceWrapper(protected, ConfigLevel.APP)
        val refreshable = WrapperRefreshablePropertySource()
        val refreshableWrapper = ScopedPropertySourceWrapper(refreshable, ConfigLevel.APP)

        assertNotNull(protectedWrapper.getProtection("service.token"))
        assertFalse(protectedWrapper.canInterpolate("service.token", ConfigLevel.TENANT))
        assertEquals(0L, refreshableWrapper.contentRevision)

        refreshable.publishOnRefresh()
        refreshableWrapper.refreshIfNeeded()

        assertEquals(1L, refreshableWrapper.contentRevision)
        assertEquals("updated", refreshableWrapper.getPropertyAsString("refresh.value"))
    }

    @Test
    fun wrappedEnvironmentSourceCannotBypassLowerScopeVisibility() {
        val wrappedEnvironment = ScopedPropertySourceWrapper(EnvPropertySource(), ConfigLevel.APP)
        val resolver =
            ProtectedPropertySourcesResolver(
                DefaultPropertySources(mutableListOf(wrappedEnvironment)),
                ConfigLevel.TENANT,
            )

        assertFalse(resolver.containsProperty("PATH"))
        assertNull(resolver.getPropertyAsString("PATH"))
        assertTrue(resolver.getAllProperties().isEmpty())
    }
}

private class WrapperRefreshablePropertySource :
    MutableMapPropertySource("refreshable-wrapper"),
    RefreshablePropertySource {
    override var contentRevision: Long = 0L
        private set

    private var publish = false

    init {
        addProperty("refresh.value", "initial")
    }

    fun publishOnRefresh() {
        publish = true
    }

    override fun refreshIfNeeded() {
        if (publish) {
            publish = false
            addProperty("refresh.value", "updated")
            contentRevision += 1L
        }
    }
}
