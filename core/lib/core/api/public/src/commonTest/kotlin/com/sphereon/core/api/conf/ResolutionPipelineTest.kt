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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class ResolutionContextTest {
    @Test
    fun appContextHasCorrectDefaults() {
        val context = ResolutionContext.app()
        assertEquals(ConfigLevel.APP, context.level)
        assertEquals(null, context.tenantId)
        assertEquals(null, context.principalId)
        assertEquals(listOf("default"), context.activeProfiles)
    }

    @Test
    fun tenantContextIncludesTenantId() {
        val context = ResolutionContext.tenant("tenant-123")
        assertEquals(ConfigLevel.TENANT, context.level)
        assertEquals("tenant-123", context.tenantId)
        assertEquals(null, context.principalId)
    }

    @Test
    fun principalContextIncludesBothIds() {
        val context = ResolutionContext.principal("tenant-123", "user-456")
        assertEquals(ConfigLevel.PRINCIPAL, context.level)
        assertEquals("tenant-123", context.tenantId)
        assertEquals("user-456", context.principalId)
    }

    @Test
    fun customProfilesArePreserved() {
        val context = ResolutionContext.app(profiles = listOf("dev", "local"))
        assertEquals(listOf("dev", "local"), context.activeProfiles)
    }
}

class ResolutionOptionsTest {
    @Test
    fun defaultOptionsHaveExpectedValues() {
        val options = ResolutionOptions()
        assertTrue(options.useCache)
        assertTrue(options.interpolate)
        assertTrue(options.includeMetadata)
        assertEquals(10, options.maxInterpolationDepth)
    }

    @Test
    fun customOptionsArePreserved() {
        val options =
            ResolutionOptions(
                useCache = false,
                interpolate = false,
                includeMetadata = false,
                maxInterpolationDepth = 5,
            )
        assertFalse(options.useCache)
        assertFalse(options.interpolate)
        assertFalse(options.includeMetadata)
        assertEquals(5, options.maxInterpolationDepth)
    }
}

class ResolvedValueTest {
    @Test
    fun ofCreatesValueWithMetadata() {
        val resolved =
            ResolvedValue.of(
                value = "test-value",
                source = "test-source",
                scope = ConfigLevel.APP,
                originalKey = "test.key",
            )

        assertEquals("test-value", resolved.value)
        assertEquals("test-source", resolved.metadata.source)
        assertEquals(ConfigLevel.APP, resolved.metadata.scope)
        assertEquals("test.key", resolved.metadata.originalKey)
        assertEquals("test.key", resolved.metadata.normalizedKey)
        assertEquals(50, resolved.metadata.order)
        assertFalse(resolved.metadata.isSecret)
        assertFalse(resolved.metadata.isInterpolated)
        assertNotNull(resolved.metadata.resolvedAt)
    }

    @Test
    fun customMetadataIsPreserved() {
        val resolved =
            ResolvedValue.of(
                value = "secret-value",
                source = "vault",
                scope = ConfigLevel.TENANT,
                originalKey = "db.password",
                normalizedKey = "db.password",
                order = 30,
                isSecret = true,
                isInterpolated = true,
            )

        assertTrue(resolved.metadata.isSecret)
        assertTrue(resolved.metadata.isInterpolated)
        assertEquals(30, resolved.metadata.order)
    }
}

class DefaultConfigResolutionPipelineTest {
    private fun createPipeline(vararg sources: PropertySource<*>): DefaultConfigResolutionPipeline {
        val propertySources = DefaultPropertySources()
        sources.forEach { source ->
            propertySources.add(
                if (source is ScopedPropertySource<*>) {
                    source
                } else {
                    ScopedPropertySourceWrapper(source, ConfigLevel.APP)
                },
            )
        }
        return DefaultConfigResolutionPipeline(propertySources)
    }

    @Test
    fun resolveFindsPropertyInSource() =
        runTest {
            val source = MutableMapPropertySource("test-source")
            source.addProperty("my.key", "my-value")

            val pipeline = createPipeline(source)
            val context = ResolutionContext.app()

            val result = pipeline.resolve("my.key", String::class, context)

            assertTrue(result.isOk)
            assertEquals("my-value", result.value.value)
            assertEquals("test-source", result.value.metadata.source)
        }

    @Test
    fun resolveReturnsErrorForMissingProperty() =
        runTest {
            val source = MutableMapPropertySource("test-source")
            val pipeline = createPipeline(source)
            val context = ResolutionContext.app()

            val result = pipeline.resolve("missing.key", String::class, context)

            assertTrue(result.isErr)
            assertEquals("NOT_FOUND_ERROR", result.error.code)
        }

    @Test
    fun resolveUsesPropertySourcePriority() =
        runTest {
            val highPriority = MutableMapPropertySource("high", order = Order.HIGH.orderValue)
            highPriority.addProperty("shared.key", "high-value")

            val lowPriority = MutableMapPropertySource("low", order = Order.LOW.orderValue)
            lowPriority.addProperty("shared.key", "low-value")

            val pipeline = createPipeline(highPriority, lowPriority)
            val context = ResolutionContext.app()

            val result = pipeline.resolve("shared.key", String::class, context)

            assertTrue(result.isOk)
            assertEquals("high-value", result.value.value)
            assertEquals("high", result.value.metadata.source)
        }

    @Test
    fun resolveNormalizesKeys() =
        runTest {
            val source = MutableMapPropertySource("test-source")
            source.addProperty("my.key", "value")

            val pipeline = createPipeline(source)
            val context = ResolutionContext.app()

            val result = pipeline.resolve("myKey", String::class, context)

            assertTrue(result.isOk)
            assertEquals("value", result.value.value)
        }

    @Test
    fun containsPropertyReturnsTrueForExisting() =
        runTest {
            val source = MutableMapPropertySource("test-source")
            source.addProperty("my.key", "value")

            val pipeline = createPipeline(source)
            val context = ResolutionContext.app()

            assertTrue(pipeline.containsProperty("my.key", context))
        }

    @Test
    fun containsPropertyReturnsFalseForMissing() =
        runTest {
            val source = MutableMapPropertySource("test-source")
            val pipeline = createPipeline(source)
            val context = ResolutionContext.app()

            assertFalse(pipeline.containsProperty("missing", context))
        }

    @Test
    fun resolveAllFindsPropertiesWithPrefix() =
        runTest {
            val source = MutableMapPropertySource("test-source")
            source.addProperty("db.host", "localhost")
            source.addProperty("db.port", 5432)
            source.addProperty("app.name", "test-app")

            val pipeline = createPipeline(source)
            val context = ResolutionContext.app()

            val result = pipeline.resolveAll("db", context)

            assertTrue(result.isOk)
            assertEquals(2, result.value.size)
            assertTrue(result.value.containsKey("db.host"))
            assertTrue(result.value.containsKey("db.port"))
            assertFalse(result.value.containsKey("app.name"))
        }

    @Test
    fun resolveAllReturnsEmptyMapForNoMatches() =
        runTest {
            val source = MutableMapPropertySource("test-source")
            source.addProperty("app.name", "test-app")

            val pipeline = createPipeline(source)
            val context = ResolutionContext.app()

            val result = pipeline.resolveAll("db", context)

            assertTrue(result.isOk)
            assertTrue(result.value.isEmpty())
        }

    @Test
    fun resolveRecordsMetadataCorrectly() =
        runTest {
            val source = MutableMapPropertySource("test-source", order = Order.HIGH.orderValue)
            source.addProperty("test.key", "test-value")

            val pipeline = createPipeline(ScopedPropertySourceWrapper(source, ConfigLevel.TENANT))
            val context = ResolutionContext.tenant("tenant-123")

            val result = pipeline.resolve("test.key", String::class, context)

            assertTrue(result.isOk)
            val metadata = result.value.metadata
            assertEquals("test-source", metadata.source)
            assertEquals(ConfigLevel.TENANT, metadata.scope)
            assertEquals("test.key", metadata.originalKey)
            assertEquals("test.key", metadata.normalizedKey)
            assertEquals(Order.HIGH.orderValue, metadata.order)
        }

    @Test
    fun fixedTenantPipelineRejectsAppContextAuthorityEscalationBeforeAnyRead() =
        runTest {
            val app =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("protected.service.token", "server-owned")
                    addProperty("service.public", "visible")
                }
            val unscopedTenant =
                MutableMapPropertySource("persisted-tenant").apply {
                    addProperty("tenant.path", "\${env:PATH:tenant-fallback}")
                }
            val pipeline =
                DefaultConfigResolutionPipeline(
                    propertySources =
                        DefaultPropertySources(
                            mutableListOf(
                                StaticProtectedEnvPropertySourceObject,
                                app,
                                unscopedTenant,
                            ),
                        ),
                    interpolator = DefaultPropertyInterpolator(),
                    resolverLevel = ConfigLevel.TENANT,
                )
            val escalated = ResolutionContext.app()

            val directEnvironment = pipeline.resolve("PATH", String::class, escalated)
            val protectedApp = pipeline.resolve("service.token", String::class, escalated)
            val unscopedEnvironment = pipeline.resolve("tenant.path", String::class, escalated)
            val serviceBulk = pipeline.resolveAll("service", escalated)
            val tenantBulk = pipeline.resolveAll("tenant", escalated)
            val stringBulk = pipeline.resolveAllAsString("", escalated)

            assertTrue(directEnvironment.isErr)
            assertTrue(protectedApp.isErr)
            assertTrue(unscopedEnvironment.isErr)
            assertTrue(serviceBulk.isErr)
            assertTrue(tenantBulk.isErr)
            assertTrue(stringBulk.isErr)
            assertFalse(pipeline.containsProperty("PATH", escalated))
            assertFalse(pipeline.containsProperty("service.token", escalated))
            assertFalse(pipeline.containsProperty("tenant.path", escalated))

            val denialMessages =
                listOf(
                    directEnvironment.error.message.defaultMessage,
                    protectedApp.error.message.defaultMessage,
                    unscopedEnvironment.error.message.defaultMessage,
                    serviceBulk.error.message.defaultMessage,
                    tenantBulk.error.message.defaultMessage,
                    stringBulk.error.message.defaultMessage,
                )
            assertEquals(1, denialMessages.distinct().size)
            val combinedDenial = denialMessages.joinToString()
            listOf("PATH", "service.token", "server-owned", "tenant.path", "tenant-fallback").forEach {
                assertFalse(combinedDenial.contains(it))
            }

            assertTrue(pipeline.resolve("service.public", String::class, ResolutionContext.tenant("tenant-a")).isOk)
            assertTrue(pipeline.resolve("service.public", String::class, ResolutionContext.principal("tenant-a", "principal-a")).isOk)
        }

    @Test
    fun unscopedRootCannotInterpolateProtectedAppValueDirectlyOrInBulk() =
        runTest {
            val unscoped =
                MutableMapPropertySource("unscoped").apply {
                    addProperty("leak.value", "\${app:internal.credential}")
                }
            val app =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("protected.internal.credential", "server-owned")
                }
            val pipeline =
                DefaultConfigResolutionPipeline(
                    propertySources = DefaultPropertySources(mutableListOf(unscoped, app)),
                    interpolator = DefaultPropertyInterpolator(),
                )
            val context = ResolutionContext.app()

            val direct = pipeline.resolve("leak.value", String::class, context)
            val bulk = pipeline.resolveAll("leak", context)

            assertTrue(direct.isErr)
            assertTrue(bulk.isErr)
            assertFalse(pipeline.containsProperty("leak.value", context))
            val denialMessages =
                listOf(
                    direct.error.message.defaultMessage,
                    bulk.error.message.defaultMessage,
                )
            listOf("internal.credential", "server-owned").forEach { forbidden ->
                assertFalse(denialMessages.any { it.contains(forbidden) })
            }
        }
}

class ConfigResolutionPipelineWithInterpolationTest {
    private fun createPipelineWithInterpolation(vararg sources: PropertySource<*>): DefaultConfigResolutionPipeline {
        val propertySources = DefaultPropertySources()
        sources.forEach { propertySources.add(it) }

        val interpolator = DefaultPropertyInterpolator()

        return DefaultConfigResolutionPipeline(
            propertySources = propertySources,
            interpolator = interpolator,
            interpolationPolicyProvider =
                FixedInterpolationPolicyProvider(InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
        )
    }

    @Test
    fun interpolatesSimplePlaceholder() =
        runTest {
            val source = ProtectedMutableMapPropertySource("test-source", ConfigLevel.APP)
            source.addProperty("base.url", "https://api.example.com")
            source.addProperty("endpoint", "\${base.url}/v1")

            val pipeline = createPipelineWithInterpolation(source)
            val context = ResolutionContext.app()

            val result = pipeline.resolve("endpoint", String::class, context)

            assertTrue(result.isOk)
            assertEquals("https://api.example.com/v1", result.value.value)
            assertTrue(result.value.metadata.isInterpolated)
        }

    @Test
    fun interpolatesPlaceholderWithDefault() =
        runTest {
            val source = ProtectedMutableMapPropertySource("test-source", ConfigLevel.APP)
            source.addProperty("app.port", "\${PORT:8080}")

            val pipeline = createPipelineWithInterpolation(source)
            val context = ResolutionContext.app()

            val result = pipeline.resolve("app.port", String::class, context)

            assertTrue(result.isOk)
            assertEquals("8080", result.value.value)
        }

    @Test
    fun interpolatesNestedPlaceholders() =
        runTest {
            val source = ProtectedMutableMapPropertySource("test-source", ConfigLevel.APP)
            source.addProperty("env", "prod")
            source.addProperty("db.prod", "prod-db.example.com")
            source.addProperty("db.host", "\${db.\${env}}")

            val pipeline = createPipelineWithInterpolation(source)
            val context = ResolutionContext.app()

            val result = pipeline.resolve("db.host", String::class, context)

            assertTrue(result.isOk)
            assertEquals("prod-db.example.com", result.value.value)
        }

    @Test
    fun appEnvironmentInterpolationRequiresAndUsesAppDeclaration() =
        runTest {
            val source =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("deployment.path", "\${env:PATH}")
                }
            val pipeline =
                DefaultConfigResolutionPipeline(
                    propertySources = DefaultPropertySources(mutableListOf(source)),
                    interpolator = DefaultPropertyInterpolator(),
                    interpolationPolicyProvider =
                        DefaultInterpolationPolicyProvider(
                            mapOf("deployment.path" to InterpolationPolicy.APP_ENVIRONMENT),
                        ),
                )

            val result = pipeline.resolve("deployment.path", String::class, ResolutionContext.app())

            assertTrue(result.isOk)
            assertTrue(result.value.value.isNotEmpty())
            assertFalse(result.value.value.contains("\${"))
        }

    @Test
    fun tenantEnvironmentInterpolationIsDeniedForDirectRecursiveAndBulkResolution() =
        runTest {
            val source =
                MutableMapPropertySource("persisted-tenant").apply {
                    addProperty("service.direct", "\${env:PATH:fallback}")
                    addProperty("service.placeholder", "env:PATH")
                    addProperty("service.recursive", "\${\${service.placeholder}}")
                    addProperty("service.safe", "safe")
                }
            val pipeline = createPipelineWithInterpolation(source)
            val tenant = ResolutionContext.tenant("tenant-a")

            val direct = pipeline.resolve("service.direct", String::class, tenant)
            val recursive = pipeline.resolve("service.recursive", String::class, tenant)
            val bulk = pipeline.resolveAll("service", tenant)

            assertTrue(direct.isErr)
            assertTrue(recursive.isErr)
            assertFalse(
                direct.error.message.defaultMessage
                    .contains("PATH"),
            )
            assertTrue(bulk.isErr)
            assertFalse(
                bulk.error.message.defaultMessage
                    .contains("PATH"),
            )
        }

    @Test
    fun tenantPipelineHidesDirectEnvironmentAndProtectedAppValues() =
        runTest {
            val app =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProtectedProperty("service.token", "server-owned", PropertyProtection.PROTECTED)
                    addProperty("service.public", "visible")
                }
            val pipeline =
                createPipelineWithInterpolation(
                    StaticProtectedEnvPropertySourceObject,
                    app,
                )
            val tenant = ResolutionContext.tenant("tenant-a")

            val environment = pipeline.resolve("PATH", String::class, tenant)
            val protected = pipeline.resolve("service.token", String::class, tenant)
            val service = pipeline.resolveAll("service", tenant)

            assertTrue(environment.isErr)
            assertEquals("NOT_FOUND_ERROR", environment.error.code)
            assertFalse(pipeline.containsProperty("PATH", tenant))
            assertTrue(protected.isErr)
            assertEquals("NOT_FOUND_ERROR", protected.error.code)
            assertFalse(pipeline.containsProperty("service.token", tenant))
            assertTrue(service.isOk)
            assertFalse(service.value.containsKey("service.token"))
            assertEquals("visible", service.value["service.public"]?.value)
        }

    @Test
    fun tenantPipelineUsesWinningAppScopeForExplicitEnvironmentButDeniesSimpleEnvironmentSource() =
        runTest {
            val app =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("deployment.explicit", "\${env:PATH}")
                    addProperty("deployment.simple", "\${PATH}")
                }
            val pipeline =
                DefaultConfigResolutionPipeline(
                    propertySources =
                        DefaultPropertySources(
                            mutableListOf(
                                StaticProtectedEnvPropertySourceObject,
                                app,
                            ),
                        ),
                    interpolator = DefaultPropertyInterpolator(),
                    interpolationPolicyProvider =
                        DefaultInterpolationPolicyProvider(
                            mapOf("deployment.explicit" to InterpolationPolicy.APP_ENVIRONMENT),
                        ),
                )
            val tenant = ResolutionContext.tenant("tenant-a")

            val explicit = pipeline.resolve("deployment.explicit", String::class, tenant)
            val simple = pipeline.resolve("deployment.simple", String::class, tenant)

            assertTrue(explicit.isOk)
            assertTrue(explicit.value.value.isNotEmpty())
            assertFalse(explicit.value.value.contains("\${"))
            assertTrue(simple.isErr)
            assertFalse(
                simple.error.message.defaultMessage
                    .contains("PATH"),
                simple.error.message.defaultMessage,
            )
        }

    @Test
    fun rejectsMapSecretReferenceInPropertyInterpolation() =
        runTest {
            val source = MutableMapPropertySource("test-source")
            source.addProperty("db.password", "\${secret:@map:credentials:db-password}")

            val pipeline = createPipelineWithInterpolation(source)
            val context = ResolutionContext.app()

            val result = pipeline.resolve("db.password", String::class, context)

            assertTrue(result.isErr)
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
        }

    @Test
    fun rejectsSecretReferencesEvenWhenInterpolationIsDisabled() =
        runTest {
            val source = MutableMapPropertySource("test-source")
            source.addProperty("api.key", "\${secret:@env:API_KEY}")

            val propertySources = DefaultPropertySources()
            propertySources.add(source)

            val interpolator = DefaultPropertyInterpolator()
            val pipeline = DefaultConfigResolutionPipeline(propertySources, interpolator)
            val context =
                ResolutionContext.app().copy(options = ResolutionOptions(interpolate = false))

            val result = pipeline.resolve("api.key", String::class, context)

            assertTrue(result.isErr)
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
        }
}

class ConfigErrorsTest {
    @Test
    fun propertyNotFoundHasCorrectCode() {
        val error = ConfigErrors.propertyNotFound("test.key")
        assertEquals("NOT_FOUND_ERROR", error.code)
        assertTrue(error.message.defaultMessage.contains("test.key"))
    }

    @Test
    fun interpolationErrorHasCorrectCode() {
        val error = ConfigErrors.interpolationError("test.key", "invalid syntax")
        assertEquals("ILLEGAL_ARGUMENT_ERROR", error.code)
        assertTrue(error.message.defaultMessage.contains("test.key"))
        assertTrue(error.message.defaultMessage.contains("invalid syntax"))
    }

    @Test
    fun circularReferenceErrorIncludesChain() {
        val error = ConfigErrors.circularReference("key.c", listOf("key.a", "key.b", "key.c"))
        assertEquals("ILLEGAL_ARGUMENT_ERROR", error.code)
        assertTrue(error.message.defaultMessage.contains("key.a"))
        assertTrue(error.message.defaultMessage.contains("->"))
    }

    @Test
    fun maxDepthExceededIncludesDepth() {
        val error = ConfigErrors.maxDepthExceeded("deep.key", 10)
        assertEquals("ILLEGAL_ARGUMENT_ERROR", error.code)
        assertTrue(error.message.defaultMessage.contains("10"))
    }

    @Test
    fun conversionErrorIncludesTypes() {
        val error = ConfigErrors.conversionError("count", "String", "Int")
        assertEquals("ILLEGAL_ARGUMENT_ERROR", error.code)
        assertTrue(error.message.defaultMessage.contains("String"))
        assertTrue(error.message.defaultMessage.contains("Int"))
    }
}

/**
 * Tests for data class equals, hashCode, and copy methods to ensure branch coverage.
 */
class ResolutionContextDataClassTest {
    @Test
    fun equalsReturnsTrueForSameValues() {
        val context1 = ResolutionContext(level = ConfigLevel.APP, tenantId = null, principalId = null, sessionId = null, activeProfiles = listOf("default"), options = ResolutionOptions())
        val context2 = ResolutionContext(level = ConfigLevel.APP, tenantId = null, principalId = null, sessionId = null, activeProfiles = listOf("default"), options = ResolutionOptions())
        assertEquals(context1, context2)
        assertEquals(context1.hashCode(), context2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentLevel() {
        val context1 = ResolutionContext(level = ConfigLevel.APP)
        val context2 = ResolutionContext(level = ConfigLevel.TENANT, tenantId = "t1")
        assertFalse(context1 == context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentTenantId() {
        val context1 = ResolutionContext(level = ConfigLevel.TENANT, tenantId = "t1")
        val context2 = ResolutionContext(level = ConfigLevel.TENANT, tenantId = "t2")
        assertFalse(context1 == context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentPrincipalId() {
        val context1 = ResolutionContext(level = ConfigLevel.PRINCIPAL, tenantId = "t1", principalId = "p1")
        val context2 = ResolutionContext(level = ConfigLevel.PRINCIPAL, tenantId = "t1", principalId = "p2")
        assertFalse(context1 == context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentSessionId() {
        val context1 = ResolutionContext(sessionId = "s1")
        val context2 = ResolutionContext(sessionId = "s2")
        assertFalse(context1 == context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentActiveProfiles() {
        val context1 = ResolutionContext(activeProfiles = listOf("dev"))
        val context2 = ResolutionContext(activeProfiles = listOf("prod"))
        assertFalse(context1 == context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentOptions() {
        val context1 = ResolutionContext(options = ResolutionOptions(useCache = true))
        val context2 = ResolutionContext(options = ResolutionOptions(useCache = false))
        assertFalse(context1 == context2)
    }

    @Test
    fun copyCreatesCorrectCopy() {
        val original = ResolutionContext.app()
        val copy = original.copy(level = ConfigLevel.TENANT, tenantId = "t1")
        assertEquals(ConfigLevel.TENANT, copy.level)
        assertEquals("t1", copy.tenantId)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val context = ResolutionContext.app()
        assertFalse(context.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val context = ResolutionContext.app()
        assertFalse(context.equals("not a context"))
    }
}

class ResolutionOptionsDataClassTest {
    @Test
    fun equalsReturnsTrueForSameValues() {
        val options1 = ResolutionOptions()
        val options2 = ResolutionOptions()
        assertEquals(options1, options2)
        assertEquals(options1.hashCode(), options2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentUseCache() {
        val options1 = ResolutionOptions(useCache = true)
        val options2 = ResolutionOptions(useCache = false)
        assertFalse(options1 == options2)
    }

    @Test
    fun equalsReturnsFalseForDifferentInterpolate() {
        val options1 = ResolutionOptions(interpolate = true)
        val options2 = ResolutionOptions(interpolate = false)
        assertFalse(options1 == options2)
    }

    @Test
    fun equalsReturnsFalseForDifferentIncludeMetadata() {
        val options1 = ResolutionOptions(includeMetadata = true)
        val options2 = ResolutionOptions(includeMetadata = false)
        assertFalse(options1 == options2)
    }

    @Test
    fun equalsReturnsFalseForDifferentMaxInterpolationDepth() {
        val options1 = ResolutionOptions(maxInterpolationDepth = 10)
        val options2 = ResolutionOptions(maxInterpolationDepth = 20)
        assertFalse(options1 == options2)
    }

    @Test
    fun equalsReturnsFalseForDifferentProfile() {
        val options1 = ResolutionOptions(profile = "dev")
        val options2 = ResolutionOptions(profile = "prod")
        assertFalse(options1 == options2)
    }

    @Test
    fun equalsReturnsFalseForDifferentAdditionalOptions() {
        val options1 = ResolutionOptions(additionalOptions = mapOf("key" to "value1"))
        val options2 = ResolutionOptions(additionalOptions = mapOf("key" to "value2"))
        assertFalse(options1 == options2)
    }

    @Test
    fun copyCreatesCorrectCopy() {
        val original = ResolutionOptions(useCache = true)
        val copy = original.copy(useCache = false)
        assertFalse(copy.useCache)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val options = ResolutionOptions()
        assertFalse(options.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val options = ResolutionOptions()
        assertFalse(options.equals("not options"))
    }
}

class ResolvedValueDataClassTest {
    @Test
    fun equalsReturnsTrueForSameValues() {
        val resolved1 = ResolvedValue.of("value", "source", ConfigLevel.APP, "key")
        val resolved2 = ResolvedValue.of("value", "source", ConfigLevel.APP, "key")
        // Note: resolvedAt will differ, so we compare individual fields
        assertEquals(resolved1.value, resolved2.value)
        assertEquals(resolved1.metadata.source, resolved2.metadata.source)
    }

    @Test
    fun equalsReturnsFalseForDifferentValue() {
        val resolved1 = ResolvedValue.of("value1", "source", ConfigLevel.APP, "key")
        val resolved2 = ResolvedValue.of("value2", "source", ConfigLevel.APP, "key")
        assertFalse(resolved1.value == resolved2.value)
    }

    @Test
    fun copyCreatesCorrectCopy() {
        val original = ResolvedValue.of("value", "source", ConfigLevel.APP, "key")
        val copy = original.copy(value = "new-value")
        assertEquals("new-value", copy.value)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val resolved = ResolvedValue.of("value", "source", ConfigLevel.APP, "key")
        assertFalse(resolved.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val resolved = ResolvedValue.of("value", "source", ConfigLevel.APP, "key")
        assertFalse(resolved.equals("not a resolved value"))
    }
}

class ResolutionMetadataDataClassTest {
    @Test
    fun equalsReturnsTrueForSameValues() {
        val now =
            kotlin.time.Clock.System
                .now()
        val metadata1 =
            ResolutionMetadata(source = "src", scope = ConfigLevel.APP, originalKey = "key", normalizedKey = "key", order = 0, isSecret = false, isInterpolated = false, resolvedAt = now, ttl = null)
        val metadata2 =
            ResolutionMetadata(source = "src", scope = ConfigLevel.APP, originalKey = "key", normalizedKey = "key", order = 0, isSecret = false, isInterpolated = false, resolvedAt = now, ttl = null)
        assertEquals(metadata1, metadata2)
        assertEquals(metadata1.hashCode(), metadata2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentSource() {
        val now =
            kotlin.time.Clock.System
                .now()
        val metadata1 =
            ResolutionMetadata(source = "src1", scope = ConfigLevel.APP, originalKey = "key", normalizedKey = "key", order = 0, isSecret = false, isInterpolated = false, resolvedAt = now, ttl = null)
        val metadata2 =
            ResolutionMetadata(source = "src2", scope = ConfigLevel.APP, originalKey = "key", normalizedKey = "key", order = 0, isSecret = false, isInterpolated = false, resolvedAt = now, ttl = null)
        assertFalse(metadata1 == metadata2)
    }

    @Test
    fun equalsReturnsFalseForDifferentScope() {
        val now =
            kotlin.time.Clock.System
                .now()
        val metadata1 =
            ResolutionMetadata(source = "src", scope = ConfigLevel.APP, originalKey = "key", normalizedKey = "key", order = 0, isSecret = false, isInterpolated = false, resolvedAt = now, ttl = null)
        val metadata2 =
            ResolutionMetadata(
                source = "src",
                scope = ConfigLevel.TENANT,
                originalKey = "key",
                normalizedKey = "key",
                order = 0,
                isSecret = false,
                isInterpolated = false,
                resolvedAt = now,
                ttl = null,
            )
        assertFalse(metadata1 == metadata2)
    }

    @Test
    fun equalsReturnsFalseForDifferentOriginalKey() {
        val now =
            kotlin.time.Clock.System
                .now()
        val metadata1 =
            ResolutionMetadata(source = "src", scope = ConfigLevel.APP, originalKey = "key1", normalizedKey = "key", order = 0, isSecret = false, isInterpolated = false, resolvedAt = now, ttl = null)
        val metadata2 =
            ResolutionMetadata(source = "src", scope = ConfigLevel.APP, originalKey = "key2", normalizedKey = "key", order = 0, isSecret = false, isInterpolated = false, resolvedAt = now, ttl = null)
        assertFalse(metadata1 == metadata2)
    }

    @Test
    fun equalsReturnsFalseForDifferentNormalizedKey() {
        val now =
            kotlin.time.Clock.System
                .now()
        val metadata1 =
            ResolutionMetadata(source = "src", scope = ConfigLevel.APP, originalKey = "key", normalizedKey = "key1", order = 0, isSecret = false, isInterpolated = false, resolvedAt = now, ttl = null)
        val metadata2 =
            ResolutionMetadata(source = "src", scope = ConfigLevel.APP, originalKey = "key", normalizedKey = "key2", order = 0, isSecret = false, isInterpolated = false, resolvedAt = now, ttl = null)
        assertFalse(metadata1 == metadata2)
    }

    @Test
    fun equalsReturnsFalseForDifferentOrder() {
        val now =
            kotlin.time.Clock.System
                .now()
        val metadata1 =
            ResolutionMetadata(source = "src", scope = ConfigLevel.APP, originalKey = "key", normalizedKey = "key", order = 0, isSecret = false, isInterpolated = false, resolvedAt = now, ttl = null)
        val metadata2 =
            ResolutionMetadata(source = "src", scope = ConfigLevel.APP, originalKey = "key", normalizedKey = "key", order = 10, isSecret = false, isInterpolated = false, resolvedAt = now, ttl = null)
        assertFalse(metadata1 == metadata2)
    }

    @Test
    fun equalsReturnsFalseForDifferentIsSecret() {
        val now =
            kotlin.time.Clock.System
                .now()
        val metadata1 =
            ResolutionMetadata(source = "src", scope = ConfigLevel.APP, originalKey = "key", normalizedKey = "key", order = 0, isSecret = false, isInterpolated = false, resolvedAt = now, ttl = null)
        val metadata2 =
            ResolutionMetadata(source = "src", scope = ConfigLevel.APP, originalKey = "key", normalizedKey = "key", order = 0, isSecret = true, isInterpolated = false, resolvedAt = now, ttl = null)
        assertFalse(metadata1 == metadata2)
    }

    @Test
    fun equalsReturnsFalseForDifferentIsInterpolated() {
        val now =
            kotlin.time.Clock.System
                .now()
        val metadata1 =
            ResolutionMetadata(source = "src", scope = ConfigLevel.APP, originalKey = "key", normalizedKey = "key", order = 0, isSecret = false, isInterpolated = false, resolvedAt = now, ttl = null)
        val metadata2 =
            ResolutionMetadata(source = "src", scope = ConfigLevel.APP, originalKey = "key", normalizedKey = "key", order = 0, isSecret = false, isInterpolated = true, resolvedAt = now, ttl = null)
        assertFalse(metadata1 == metadata2)
    }

    @Test
    fun equalsReturnsFalseForDifferentResolvedAt() {
        val now =
            kotlin.time.Clock.System
                .now()
        val metadata1 =
            ResolutionMetadata(source = "src", scope = ConfigLevel.APP, originalKey = "key", normalizedKey = "key", order = 0, isSecret = false, isInterpolated = false, resolvedAt = now, ttl = null)
        val metadata2 =
            ResolutionMetadata(
                source = "src",
                scope = ConfigLevel.APP,
                originalKey = "key",
                normalizedKey = "key",
                order = 0,
                isSecret = false,
                isInterpolated = false,
                resolvedAt =
                    now + 1.hours,
                ttl = null,
            )
        assertFalse(metadata1 == metadata2)
    }

    @Test
    fun equalsReturnsFalseForDifferentTtl() {
        val now =
            kotlin.time.Clock.System
                .now()
        val metadata1 =
            ResolutionMetadata(
                source = "src",
                scope = ConfigLevel.APP,
                originalKey = "key",
                normalizedKey = "key",
                order = 0,
                isSecret = false,
                isInterpolated = false,
                resolvedAt = now,
                ttl = 1.hours,
            )
        val metadata2 =
            ResolutionMetadata(
                source = "src",
                scope = ConfigLevel.APP,
                originalKey = "key",
                normalizedKey = "key",
                order = 0,
                isSecret = false,
                isInterpolated = false,
                resolvedAt = now,
                ttl = 2.hours,
            )
        assertFalse(metadata1 == metadata2)
    }

    @Test
    fun copyCreatesCorrectCopy() {
        val now =
            kotlin.time.Clock.System
                .now()
        val original =
            ResolutionMetadata(source = "src", scope = ConfigLevel.APP, originalKey = "key", normalizedKey = "key", order = 0, isSecret = false, isInterpolated = false, resolvedAt = now, ttl = null)
        val copy = original.copy(isSecret = true)
        assertTrue(copy.isSecret)
        assertEquals("src", copy.source)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val now =
            kotlin.time.Clock.System
                .now()
        val metadata =
            ResolutionMetadata(source = "src", scope = ConfigLevel.APP, originalKey = "key", normalizedKey = "key", order = 0, isSecret = false, isInterpolated = false, resolvedAt = now, ttl = null)
        assertFalse(metadata.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val now =
            kotlin.time.Clock.System
                .now()
        val metadata =
            ResolutionMetadata(source = "src", scope = ConfigLevel.APP, originalKey = "key", normalizedKey = "key", order = 0, isSecret = false, isInterpolated = false, resolvedAt = now, ttl = null)
        assertFalse(metadata.equals("not metadata"))
    }
}
