/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.core.api.conf

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PropertyInterpolatorTest {
    private val interpolator = DefaultPropertyInterpolator()

    private suspend fun interpolateApprovedAppEnvironment(
        value: String,
        resolver: ProtectedPropertyResolver,
    ): IdkResult<InterpolatedPropertyValue, IdkError> =
        interpolator.interpolateWithProvenance(
            value = value,
            resolver = resolver,
            requestingScope = ConfigLevel.APP,
            maxDepth = null,
            policy = InterpolationPolicy.APP_ENVIRONMENT,
            sourceProvenance = ResolutionProvenance.known(ConfigLevel.APP),
        )

    private fun protectedResolver(
        vararg sources: PropertySource<*>,
        level: ConfigLevel = ConfigLevel.APP,
    ): ProtectedPropertySourcesResolver {
        val propertySources = DefaultPropertySources()
        sources.forEach(propertySources::add)
        return ProtectedPropertySourcesResolver(propertySources, level)
    }

    @Test
    fun interpolatesSimpleScopedAndRecursiveProperties() =
        runTest {
            val app =
                MutableMapPropertySource("app").apply {
                    addProperty("host", "api.example.com")
                    addProperty("endpoint", "https://\${host}/v1")
                }
            val resolver =
                protectedResolver(ScopedPropertySourceWrapper(app, ConfigLevel.APP))

            val simple = interpolator.interpolate("\${endpoint}", resolver)
            val scoped = interpolator.interpolate("\${app:host}", resolver, ConfigLevel.TENANT)

            assertTrue(simple.isOk)
            assertEquals("https://api.example.com/v1", simple.value)
            assertTrue(scoped.isOk)
            assertEquals("api.example.com", scoped.value)
        }

    @Test
    fun supportsDefaultsWithoutConsultingExternalSources() =
        runTest {
            val resolver = protectedResolver()
            val result = interpolator.interpolate("\${missing:default-value}", resolver)

            assertTrue(result.isOk)
            assertEquals("default-value", result.value)
        }

    @Test
    fun resolvesEnvironmentConfigurationAndDefaults() =
        runTest {
            val app =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("path", "\${env:PATH}")
                    addProperty("missing", "\${env:IDK_MISSING_ENV_7B3F:default-value}")
                }
            val resolver = protectedResolver(app)
            val present = interpolateApprovedAppEnvironment("\${env:PATH}", resolver)
            val missing = interpolateApprovedAppEnvironment("\${env:IDK_MISSING_ENV_7B3F:default-value}", resolver)

            assertTrue(present.isOk)
            assertTrue(present.value.value.isNotEmpty())
            assertFalse(present.value.value.contains("\${"))
            assertTrue(missing.isOk)
            assertEquals("default-value", missing.value.value)
        }

    @Test
    fun deniesEnvironmentOutsideAppEvenWhenAppDeclaredAndDefaultExists() =
        runTest {
            val app =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("path", "\${env:PATH}")
                }
            val resolver = protectedResolver(app)

            val tenant = interpolator.interpolate("\${env:PATH:fallback}", resolver, ConfigLevel.TENANT)
            val principal = interpolator.interpolate("\${env:PATH:fallback}", resolver, ConfigLevel.PRINCIPAL)

            assertTrue(tenant.isErr)
            assertTrue(principal.isErr)
            assertEquals(tenant.error.message.defaultMessage, principal.error.message.defaultMessage)
            assertFalse(
                tenant.error.message.defaultMessage
                    .contains("PATH")
            )
            assertFalse(
                tenant.error.message.defaultMessage
                    .contains("fallback")
            )
        }

    @Test
    fun deniesRecursivelyProducedEnvironmentReferenceOutsideApp() =
        runTest {
            val tenant =
                ProtectedMutableMapPropertySource("tenant", ConfigLevel.TENANT).apply {
                    addProperty("placeholder.type", "env:PATH")
                    addProperty("indirect", "\${\${placeholder.type}}")
                }
            val resolver = protectedResolver(tenant)

            val result = interpolator.interpolate("\${indirect}", resolver, ConfigLevel.TENANT)

            assertTrue(result.isErr)
            assertFalse(
                result.error.message.defaultMessage
                    .contains("PATH")
            )
        }

    @Test
    fun appEnvironmentNameMustBeExplicitlyDeclaredByAppSource() =
        runTest {
            val resolver = protectedResolver(ProtectedMutableMapPropertySource("app", ConfigLevel.APP))

            val result = interpolateApprovedAppEnvironment("\${env:PATH}", resolver)

            assertTrue(result.isErr)
            assertFalse(
                result.error.message.defaultMessage
                    .contains("PATH")
            )
        }

    @Test
    fun unscopedAndEnvironmentNamedSourcesCannotDeclareAppEnvironmentAuthority() =
        runTest {
            val unscoped =
                MutableMapPropertySource("unscoped").apply {
                    addProperty("deployment.path", "\${env:PATH}")
                }
            val spoofed =
                ScopedPropertySourceWrapper(
                    MutableMapPropertySource("environment").apply {
                        addProperty("deployment.path", "\${env:PATH}")
                    },
                    ConfigLevel.APP,
                )
            val unscopedResolver =
                PropertyResolverFactory.withInterpolation(
                    DefaultPropertySources(mutableListOf(unscoped)),
                    resolverLevel = ConfigLevel.APP,
                )
            val directUnscopedResolver = protectedResolver(unscoped)
            val spoofedResolver =
                PropertyResolverFactory.withInterpolation(
                    DefaultPropertySources(mutableListOf(spoofed)),
                    resolverLevel = ConfigLevel.APP,
                )

            val unscopedError =
                assertFailsWith<IllegalStateException> {
                    unscopedResolver.getPropertyAsString("deployment.path")
                }
            val spoofedError =
                assertFailsWith<IllegalStateException> {
                    spoofedResolver.getPropertyAsString("deployment.path")
                }
            val unscopedChild =
                interpolator.interpolate(
                    "\${deployment.path}",
                    directUnscopedResolver,
                    ConfigLevel.APP,
                )

            assertFalse(unscopedError.message.orEmpty().contains("PATH"))
            assertFalse(spoofedError.message.orEmpty().contains("PATH"))
            assertTrue(unscopedChild.isErr)
            assertFalse(unscopedChild.error.message.defaultMessage.contains("PATH"))
        }

    @Test
    fun twoArgumentInterpolationUsesResolverScope() =
        runTest {
            val app =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("path", "\${env:PATH}")
                }
            val tenantResolver = protectedResolver(app, level = ConfigLevel.TENANT)

            val result = interpolator.interpolate("\${env:PATH}", tenantResolver)

            assertTrue(result.isErr)
            assertFalse(
                result.error.message.defaultMessage
                    .contains("PATH")
            )
        }

    @Test
    fun explicitScopeOverloadsCannotEscalatePastResolverAuthority() =
        runTest {
            val app =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("protected.internal.credential", "server-owned")
                    addProperty("deployment.path", "\${env:PATH}")
                }
            val tenantResolver = protectedResolver(app, level = ConfigLevel.TENANT)

            val results =
                listOf(
                    interpolator.interpolate(
                        "\${app:internal.credential:protected-three-fallback}",
                        tenantResolver,
                        ConfigLevel.APP,
                    ),
                    interpolator.interpolate(
                        "\${env:PATH:environment-three-fallback}",
                        tenantResolver,
                        ConfigLevel.APP,
                    ),
                    interpolator.interpolate(
                        "\${app:internal.credential:protected-four-fallback}",
                        tenantResolver,
                        ConfigLevel.APP,
                        maxDepth = 2,
                    ),
                    interpolator.interpolate(
                        "\${env:PATH:environment-four-fallback}",
                        tenantResolver,
                        ConfigLevel.APP,
                        maxDepth = 2,
                    ),
                )

            assertTrue(results.all { it.isErr })
            val denialMessages = results.map { it.error.message.defaultMessage }
            assertEquals(1, denialMessages.distinct().size)
            val combinedDenial = denialMessages.joinToString()
            listOf(
                "internal.credential",
                "server-owned",
                "PATH",
                "protected-three-fallback",
                "environment-three-fallback",
                "protected-four-fallback",
                "environment-four-fallback",
            ).forEach {
                assertFalse(combinedDenial.contains(it))
            }
        }

    @Test
    fun recursiveInterpolationCannotSmuggleEnvironmentOrProtectedAppAuthority() =
        runTest {
            val app =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("deployment.path", "\${env:PATH}")
                    addProperty("app.environment.child", "\${env:PATH}")
                    addProperty("app.environment.fragment", "env:PATH")
                    addProperty("protected.internal.credential", "server-owned")
                }
            val tenant =
                ProtectedMutableMapPropertySource("tenant", ConfigLevel.TENANT).apply {
                    addProperty("tenant.environment.fragment", "env:PATH")
                    addProperty("tenant.protected.fragment", "app:internal.credential")
                }
            val principal =
                ProtectedMutableMapPropertySource("principal", ConfigLevel.PRINCIPAL).apply {
                    addProperty("principal.environment.fragment", "env:PATH")
                }
            val resolver = protectedResolver(app, tenant, principal)

            val tenantEnvironment =
                interpolator.interpolate(
                    "\${\${tenant.environment.fragment}}",
                    resolver,
                    ConfigLevel.APP,
                )
            val tenantProtected =
                interpolator.interpolate(
                    "\${\${tenant.protected.fragment}}",
                    resolver,
                    ConfigLevel.APP,
                )
            val synthesizedAppEnvironment =
                interpolateApprovedAppEnvironment(
                    "\${\${app.environment.fragment}}",
                    resolver,
                )
            val principalEnvironment =
                interpolator.interpolate(
                    "\${\${principal.environment.fragment}}",
                    resolver,
                    ConfigLevel.APP,
                )
            val chainedAppEnvironment =
                interpolateApprovedAppEnvironment(
                    "\${app.environment.child}",
                    resolver,
                )
            val directAppEnvironment =
                interpolateApprovedAppEnvironment(
                    "\${env:PATH}",
                    resolver,
                )

            assertTrue(tenantEnvironment.isErr)
            assertTrue(tenantProtected.isErr)
            assertTrue(synthesizedAppEnvironment.isErr)
            assertTrue(principalEnvironment.isErr)
            assertTrue(chainedAppEnvironment.isOk)
            assertTrue(chainedAppEnvironment.value.value.isNotEmpty())
            assertTrue(directAppEnvironment.isOk)
            assertTrue(directAppEnvironment.value.value.isNotEmpty())

            val denialMessages =
                listOf(
                    tenantEnvironment.error.message.defaultMessage,
                    tenantProtected.error.message.defaultMessage,
                    synthesizedAppEnvironment.error.message.defaultMessage,
                    principalEnvironment.error.message.defaultMessage,
                ).joinToString()
            listOf("PATH", "internal.credential", "server-owned").forEach {
                assertFalse(denialMessages.contains(it))
            }
        }

    @Test
    fun assembledSecretUriIsRejectedAfterAllSubstitutions() =
        runTest {
            val app =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("scheme", "sec")
                    addProperty("locator", "ret://tenant/private/value")
                }
            val resolver = protectedResolver(app)

            val result = interpolator.interpolate("\${scheme}\${locator}", resolver)

            assertTrue(result.isErr)
            assertFalse(
                result.error.message.defaultMessage
                    .contains("tenant/private"),
            )
        }

    @Test
    fun maxDepthDiagnosticsNeverUseMaterializedValueAsPropertyKey() =
        runTest {
            val app =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("hidden.value", "depth-materialized-secret")
                }
            val result =
                interpolator.interpolateWithProvenance(
                    value = "\${hidden.value}",
                    resolver = protectedResolver(app),
                    requestingScope = ConfigLevel.APP,
                    maxDepth = 0,
                    policy = InterpolationPolicy.PROPERTY_REFERENCES_ONLY,
                    sourceProvenance = ResolutionProvenance.known(ConfigLevel.APP),
                )

            assertTrue(result.isErr)
            assertFalse(result.error.message.defaultMessage.contains("depth-materialized-secret"))
            assertFalse(result.error.message.defaultMessage.contains("hidden.value"))
        }

    @Test
    fun protectedAndAbsentReferencesWithDefaultsHaveIdenticalFallbackOutcome() =
        runTest {
            val app =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("protected.internal.credential", "server-owned")
                }
            val resolver = protectedResolver(app)

            val protected =
                interpolator.interpolate(
                    "\${app:internal.credential:safe-fallback}",
                    resolver,
                    ConfigLevel.TENANT,
                )
            val absent =
                interpolator.interpolate(
                    "\${app:absent.credential:safe-fallback}",
                    resolver,
                    ConfigLevel.TENANT,
                )
            val protectedWithoutDefault =
                interpolator.interpolate(
                    "\${app:internal.credential}",
                    resolver,
                    ConfigLevel.TENANT,
                )
            val absentWithoutDefault =
                interpolator.interpolate(
                    "\${app:absent.credential}",
                    resolver,
                    ConfigLevel.TENANT,
                )

            assertTrue(protected.isOk)
            assertTrue(absent.isOk)
            assertEquals("safe-fallback", protected.value)
            assertEquals(protected.value, absent.value)
            assertTrue(protectedWithoutDefault.isErr)
            assertTrue(absentWithoutDefault.isErr)
            assertEquals(protectedWithoutDefault.error.code, absentWithoutDefault.error.code)
            assertEquals(
                protectedWithoutDefault.error.message.defaultMessage,
                absentWithoutDefault.error.message.defaultMessage,
            )
            assertFalse(
                protectedWithoutDefault.error.message.defaultMessage
                    .contains("internal.credential"),
            )
            assertFalse(
                protectedWithoutDefault.error.message.defaultMessage
                    .contains("server-owned"),
            )
            assertFalse(
                absentWithoutDefault.error.message.defaultMessage
                    .contains("absent.credential"),
            )
        }

    @Test
    fun rejectsCallerCreatedSecretReferenceWithoutInvokingOpaqueResolver() =
        runTest {
            val resolver = protectedResolver()
            val result = interpolator.interpolate("\${secret:01JZZZZZZZZZZZZZZZZZZZZZZZ}", resolver)

            assertTrue(result.isErr)
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("secret-provider references are forbidden")
            )
        }

    @Test
    fun rejectsPinnedProviderReference() =
        runTest {
            val resolver = protectedResolver()
            val result = interpolator.interpolate("\${secret:@vault:tenant/path}", resolver)

            assertTrue(result.isErr)
            assertFalse(
                result.error.message.defaultMessage
                    .contains("tenant/path")
            )
        }

    @Test
    fun rejectsSecretUrisWithoutLeakingTheirLocator() =
        runTest {
            val resolver = protectedResolver()
            val secretUri = interpolator.interpolate("secret://tenant/private/value", resolver)
            val artifactUri = interpolator.interpolate("vault://artifacts/tenant/logo.svg", resolver)
            val ordinaryUri = interpolator.interpolate("https://api.example.com/v1", resolver)

            assertTrue(secretUri.isErr)
            assertFalse(
                secretUri.error.message.defaultMessage
                    .contains("tenant/private")
            )
            assertTrue(artifactUri.isOk)
            assertEquals("vault://artifacts/tenant/logo.svg", artifactUri.value)
            assertTrue(ordinaryUri.isOk)
            assertEquals("https://api.example.com/v1", ordinaryUri.value)
        }

    @Test
    fun parsesEnvironmentConfigurationSeparatelyFromDeniedSecretReferences() {
        val environment = interpolator.parsePlaceholders("\${env:PATH}").single()
        val secret = interpolator.parsePlaceholders("\${secret:@vault:tenant/path}").single()
        val scoped = interpolator.parsePlaceholders("\${tenant:service.endpoint}").single()

        assertEquals(PlaceholderType.ENV, environment.type)
        assertEquals(PlaceholderType.FORBIDDEN_EXTERNAL_SOURCE, secret.type)
        assertEquals(PlaceholderType.SCOPE, scoped.type)
        assertEquals("PATH", environment.key)
        assertEquals("external-source", secret.key)
        assertEquals(ConfigLevel.TENANT, scoped.scope)
    }

    @Test
    fun detectsBalancedPlaceholdersOnly() {
        assertTrue(interpolator.containsPlaceholders("\${key}"))
        assertFalse(interpolator.containsPlaceholders("\${incomplete"))
        assertFalse(interpolator.containsPlaceholders("plain text"))
    }

    @Test
    fun legacyKeylessInterpolationCannotReadAppEnvironment() =
        runTest {
            val app =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("deployment.path", "\${env:PATH}")
                }
            val resolver = protectedResolver(app)

            val result = interpolator.interpolate("\${env:PATH}", resolver, ConfigLevel.APP)

            assertTrue(result.isErr)
            assertFalse(result.error.message.defaultMessage.contains("PATH"))
        }

    @Test
    fun policyAwareInterpolationCarriesDirectAndRecursiveEnvironmentTaint() =
        runTest {
            val app =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("deployment.path", "\${env:PATH}")
                    addProperty("deployment.alias", "\${deployment.path}")
                }
            val resolver = protectedResolver(app)

            val direct = interpolateApprovedAppEnvironment("\${env:PATH}", resolver)
            val recursive = interpolateApprovedAppEnvironment("\${deployment.alias}", resolver)

            assertTrue(direct.isOk)
            assertTrue(recursive.isOk)
            listOf(direct.value.provenance, recursive.value.provenance).forEach { provenance ->
                assertEquals(ConfigLevel.APP, provenance.sourceScope)
                assertTrue(provenance.hasTaint(ResolutionTaint.ENVIRONMENT))
                assertTrue(provenance.hasTaint(ResolutionTaint.INTERPOLATED))
                assertFalse(provenance.isCacheSafe())
            }
        }

    @Test
    fun recursiveSensitivePropertyTaintIsNeverStripped() =
        runTest {
            val app =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("db.password", "server-owned")
                    addProperty("db.alias", "\${db.password}")
                }
            val resolver = protectedResolver(app)

            val result =
                interpolator.interpolateWithProvenance(
                    value = "\${db.alias}",
                    resolver = resolver,
                    requestingScope = ConfigLevel.APP,
                    maxDepth = null,
                    policy = InterpolationPolicy.PROPERTY_REFERENCES_ONLY,
                    sourceProvenance = ResolutionProvenance.known(ConfigLevel.APP),
                )

            assertTrue(result.isOk)
            assertTrue(result.value.provenance.hasTaint(ResolutionTaint.SENSITIVE))
            assertFalse(result.value.provenance.isCacheSafe())
        }
}
