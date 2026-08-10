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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InterpolatingPropertySourcesPropertyResolverTest {
    private fun createResolver(vararg properties: Pair<String, Any>): PropertyResolver {
        val source = ProtectedMutableMapPropertySource("test-source", ConfigLevel.APP)
        properties.forEach { (key, value) -> source.addProperty(key, value) }

        val propertySources = DefaultPropertySources()
        propertySources.add(source)

        return InterpolatingPropertySourcesPropertyResolver(
            propertySources = propertySources,
            interpolator = DefaultPropertyInterpolator(),
            resolverLevel = ConfigLevel.APP,
        )
    }

    @Test
    fun unscopedWinnerCannotInterpolateAtAppResolverAuthority() {
        val source =
            MutableMapPropertySource("unscoped").apply {
                addProperty("base.url", "https://api.example.com")
                addProperty("endpoint", "\${base.url}/v1")
            }
        val resolver =
            PropertyResolverFactory.withInterpolation(
                propertySources = DefaultPropertySources(mutableListOf(source)),
                resolverLevel = ConfigLevel.APP,
            )

        assertFailsWith<IllegalStateException> {
            resolver.getPropertyAsString("endpoint")
        }
    }

    @Test
    fun getPropertyReturnsExistingValue() {
        val resolver = createResolver("my.key" to "my-value")
        val value = resolver.getProperty("my.key", String::class)
        assertEquals("my-value", value)
    }

    @Test
    fun getPropertyReturnsNullForMissing() {
        val resolver = createResolver()
        val value = resolver.getProperty("missing", String::class)
        assertEquals(null, value)
    }

    @Test
    fun getPropertyReturnsDefaultForMissing() {
        val resolver = createResolver()
        val value = resolver.getProperty("missing", String::class, "default-value")
        assertEquals("default-value", value)
    }

    @Test
    fun getPropertyAsStringReturnsValue() {
        val resolver = createResolver("key" to "value")
        assertEquals("value", resolver.getPropertyAsString("key"))
    }

    @Test
    fun getPropertyAsStringReturnsDefaultForMissing() {
        val resolver = createResolver()
        assertEquals("default", resolver.getPropertyAsString("missing", "default"))
    }

    @Test
    fun containsPropertyReturnsTrueForExisting() {
        val resolver = createResolver("exists" to "value")
        assertTrue(resolver.containsProperty("exists"))
    }

    @Test
    fun containsPropertyReturnsFalseForMissing() {
        val resolver = createResolver()
        assertFalse(resolver.containsProperty("missing"))
    }

    @Test
    fun getAllPropertiesReturnsAllProperties() {
        val resolver =
            createResolver(
                "key1" to "value1",
                "key2" to "value2",
            )

        val all = resolver.getAllProperties()
        assertEquals(2, all.size)
        assertEquals("value1", all["key1"])
        assertEquals("value2", all["key2"])
    }

    @Test
    fun getSubPropertiesFiltersAndStripsPrefix() {
        val resolver =
            createResolver(
                "db.host" to "localhost",
                "db.port" to 5432,
                "app.name" to "test",
            )

        val sub = resolver.getSubProperties(setOf("db"), stripPrefix = true)
        assertEquals(2, sub.size)
        assertTrue(sub.containsKey("host"))
        assertTrue(sub.containsKey("port"))
        assertFalse(sub.containsKey("app.name"))
    }

    @Test
    fun getSubPropertiesWithoutStripping() {
        val resolver =
            createResolver(
                "db.host" to "localhost",
                "db.port" to 5432,
            )

        val sub = resolver.getSubProperties(setOf("db"), stripPrefix = false)
        assertTrue(sub.containsKey("db.host"))
        assertTrue(sub.containsKey("db.port"))
    }

    @Test
    fun shorthandSecretInScopedSourceRegistrationFailsClosed() {
        val source =
            MutableMapPropertySource("tenant-source").apply {
                addProperty("oauth2.clients.service.client-secret", "\${secret:oauth2/service/client-secret}")
            }

        val error =
            assertFailsWith<IllegalArgumentException> {
                DefaultPropertySources().add(ScopedPropertySourceWrapper(source, ConfigLevel.TENANT))
            }
        assertEquals("Configuration value is not permitted", error.message)
    }

    @Test
    fun providerUriInDirectReadFailsClosed() {
        val resolver =
            InterpolatingPropertySourcesPropertyResolver(
                propertySources =
                    DefaultPropertySources(
                        mutableListOf(
                            MapPropertySource(
                                "invalid-import",
                                mapOf("credential" to "secret://tenant/private/value"),
                            ),
                        ),
                    ),
                interpolator = DefaultPropertyInterpolator(),
                resolverLevel = ConfigLevel.APP,
            )

        val error =
            assertFailsWith<IllegalStateException> {
                resolver.getPropertyAsString("credential")
            }

        assertFalse(error.message.orEmpty().contains("tenant/private"))
    }
}

class PropertyResolverFactoryTest {
    @Test
    fun createWithoutInterpolatorReturnsProtectionAwareResolver() {
        val source = ProtectedMutableMapPropertySource("test", ConfigLevel.APP)
        source.addProperty("key", "value")
        source.addProperty("protected.internal.credential", "server-owned")

        val propertySources = DefaultPropertySources()
        propertySources.add(source)

        val resolver = PropertyResolverFactory.create(propertySources, resolverLevel = ConfigLevel.TENANT)

        assertEquals("value", resolver.getPropertyAsString("key"))
        assertNull(resolver.getPropertyAsString("internal.credential"))
        assertTrue(resolver is ProtectedPropertySourcesResolver)
    }

    @Test
    fun createWithInterpolatorReturnsInterpolatingResolver() {
        val source = MutableMapPropertySource("test")
        source.addProperty("key", "value")

        val propertySources = DefaultPropertySources()
        propertySources.add(source)

        val resolver =
            PropertyResolverFactory.create(
                propertySources,
                DefaultPropertyInterpolator(),
                resolverLevel = ConfigLevel.APP,
            )

        assertEquals("value", resolver.getPropertyAsString("key"))
        assertTrue(resolver is InterpolatingPropertySourcesPropertyResolver)
    }

    @Test
    fun withInterpolationCreatesInterpolatingResolver() {
        val source = ProtectedMutableMapPropertySource("test", ConfigLevel.APP)
        source.addProperty("base", "https://api.example.com")
        source.addProperty("url", "\${base}/v1")

        val propertySources = DefaultPropertySources()
        propertySources.add(source)

        val resolver =
            PropertyResolverFactory.withInterpolation(
                propertySources,
                resolverLevel = ConfigLevel.APP,
                interpolationPolicyProvider =
                    DefaultInterpolationPolicyProvider(
                        mapOf("url" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
                    ),
            )

        assertEquals("https://api.example.com/v1", resolver.getPropertyAsString("url"))
    }

    @Test
    fun withInterpolationUsesDefaultValue() {
        val source = ProtectedMutableMapPropertySource("test", ConfigLevel.APP)
        source.addProperty("port", "\${PORT:8080}")

        val propertySources = DefaultPropertySources()
        propertySources.add(source)

        val resolver = PropertyResolverFactory.withInterpolation(propertySources, resolverLevel = ConfigLevel.APP)

        assertEquals("8080", resolver.getPropertyAsString("port"))
    }

    @Test
    fun withInterpolationRejectsProviderBackedSecretReferences() {
        val source = MutableMapPropertySource("test")
        source.addProperty("password", "\${secret:@map:creds:pass}")

        val propertySources = DefaultPropertySources()
        propertySources.add(source)

        val resolver =
            PropertyResolverFactory.withInterpolation(
                propertySources,
                resolverLevel = ConfigLevel.APP,
            )

        assertFailsWith<IllegalStateException> {
            resolver.getPropertyAsString("password")
        }
    }

    @Test
    fun productionFactoryAllowsDeclaredAppEnvironmentReference() {
        val app =
            ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                addProperty("deployment.path", "\${env:PATH}")
            }
        val propertySources = DefaultPropertySources(mutableListOf(app))
        val resolver =
            PropertyResolverFactory.withInterpolation(
                propertySources,
                resolverLevel = ConfigLevel.APP,
                interpolationPolicyProvider =
                    DefaultInterpolationPolicyProvider(
                        mapOf("deployment.path" to InterpolationPolicy.APP_ENVIRONMENT),
                    ),
            )

        val value = resolver.getPropertyAsString("deployment.path")

        assertTrue(value.orEmpty().isNotEmpty())
        assertFalse(value.orEmpty().contains("\${"))
    }

    @Test
    fun productionFactoryRejectsRecursivelyProducedTenantEnvironmentReferenceInDirectAndBulkReads() {
        val tenant =
            ProtectedMutableMapPropertySource("tenant", ConfigLevel.TENANT).apply {
                addProperty("service.placeholder", "env:PATH")
                addProperty("service.endpoint", "\${\${service.placeholder}}")
            }
        val propertySources = DefaultPropertySources(mutableListOf(tenant))
        val resolver =
            PropertyResolverFactory.withInterpolation(
                propertySources = propertySources,
                resolverLevel = ConfigLevel.TENANT,
            )

        assertFailsWith<IllegalStateException> {
            resolver.getPropertyAsString("service.endpoint")
        }
        assertFailsWith<IllegalStateException> {
            resolver.getSubProperties(setOf("service"), stripPrefix = true)
        }
    }

    @Test
    fun productionFactoryReturnsSameDefaultForProtectedAndAbsentAppProperty() {
        val app =
            ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                addProperty("protected.internal.credential", "server-owned")
            }
        val tenant =
            ProtectedMutableMapPropertySource("tenant", ConfigLevel.TENANT).apply {
                addProperty("service.credential", "\${app:internal.credential:fallback}")
            }
        val resolver =
            PropertyResolverFactory.withInterpolation(
                propertySources = DefaultPropertySources(mutableListOf(app, tenant)),
                resolverLevel = ConfigLevel.TENANT,
                interpolationPolicyProvider =
                    DefaultInterpolationPolicyProvider(
                        mapOf(
                            "service.credential" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY,
                            "service.absent" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY,
                        ),
                    ),
            )

        tenant.addProperty("service.absent", "\${app:absent.credential:fallback}")

        assertEquals("fallback", resolver.getPropertyAsString("service.credential"))
        assertEquals(
            resolver.getPropertyAsString("service.credential"),
            resolver.getPropertyAsString("service.absent"),
        )
    }
}

class InterpolatingPropertyResolverWithInterpolationTest {
    private fun createResolver(vararg properties: Pair<String, Any>): PropertyResolver {
        val source = ProtectedMutableMapPropertySource("test-source", ConfigLevel.APP)
        properties.forEach { (key, value) -> source.addProperty(key, value) }

        val propertySources = DefaultPropertySources()
        propertySources.add(source)

        val interpolator = DefaultPropertyInterpolator()

        return InterpolatingPropertySourcesPropertyResolver(
            propertySources = propertySources,
            interpolator = interpolator,
            resolverLevel = ConfigLevel.APP,
            interpolationPolicyProvider =
                FixedInterpolationPolicyProvider(InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
        )
    }

    @Test
    fun interpolatesValuesOnGet() {
        val resolver =
            createResolver(
                "host" to "localhost",
                "url" to "http://\${host}:8080",
            )

        assertEquals("http://localhost:8080", resolver.getPropertyAsString("url"))
    }

    @Test
    fun interpolatesWithDefaultValues() {
        val resolver =
            createResolver(
                "port" to "\${HTTP_PORT:3000}",
            )

        assertEquals("3000", resolver.getPropertyAsString("port"))
    }

    @Test
    fun interpolatesNestedReferences() {
        val resolver =
            createResolver(
                "env" to "prod",
                "db.prod" to "prod-db.example.com",
                "db.host" to "\${db.\${env}}",
            )

        assertEquals("prod-db.example.com", resolver.getPropertyAsString("db.host"))
    }

    @Test
    fun rejectsSecretReferences() {
        val resolver =
            InterpolatingPropertySourcesPropertyResolver(
                propertySources =
                    DefaultPropertySources(
                        mutableListOf(
                            MapPropertySource(
                                "invalid-import",
                                mapOf("api.key" to "\${secret:@map:api:key}"),
                            ),
                        ),
                    ),
                interpolator = DefaultPropertyInterpolator(),
                resolverLevel = ConfigLevel.APP,
                interpolationPolicyProvider =
                    FixedInterpolationPolicyProvider(InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
            )

        assertFailsWith<IllegalStateException> {
            resolver.getPropertyAsString("api.key")
        }
    }

    @Test
    fun getAllPropertiesReturnsInterpolatedValues() {
        val resolver =
            createResolver(
                "base" to "https://api.example.com",
                "url" to "\${base}/v1",
            )

        val all = resolver.getAllProperties()
        assertEquals("https://api.example.com/v1", all["url"])
    }

    @Test
    fun getSubPropertiesReturnsInterpolatedValues() {
        val resolver =
            createResolver(
                "db.host" to "localhost",
                "db.url" to "jdbc:postgresql://\${db.host}:5432/mydb",
            )

        val sub = resolver.getSubProperties(setOf("db"), stripPrefix = true)
        assertEquals("jdbc:postgresql://localhost:5432/mydb", sub["url"])
    }

    @Test
    fun interpolatesAndCoercesPlaceholderToBoolean() {
        // YAML stores `dpop-nonce-required: "${env:OAUTH2_DPOP_NONCE_REQUIRED:false}"`
        // as a String. Boolean-typed callers must still see the interpolated value
        // coerced to Boolean instead of throwing on the raw template.
        val resolver =
            createResolver(
                "feature.enabled" to "\${FEATURE_FLAG:true}",
            )

        assertEquals(true, resolver.getProperty("feature.enabled", Boolean::class))
    }

    @Test
    fun interpolatesAndCoercesPlaceholderToInt() {
        val resolver =
            createResolver(
                "server.port" to "\${HTTP_PORT:8080}",
            )

        assertEquals(8080, resolver.getProperty("server.port", Int::class))
    }

    @Test
    fun interpolatesAndCoercesPlaceholderToLong() {
        val resolver =
            createResolver(
                "session.timeout" to "\${SESSION_TIMEOUT_MS:60000}",
            )

        assertEquals(60_000L, resolver.getProperty("session.timeout", Long::class))
    }

    @Test
    fun interpolatedBooleanFalseIsRespected() {
        val resolver =
            createResolver(
                "feature.enabled" to "\${FLAG:false}",
            )

        assertEquals(false, resolver.getProperty("feature.enabled", Boolean::class))
    }

    @Test
    fun interpolatedNonCoercibleStringFallsThroughToTypedLookup() {
        // When the interpolated value can't be coerced, fall back to the typed lookup
        // (which then throws or returns null per the underlying source's contract).
        // Here the typed lookup returns null because no Boolean is stored.
        val resolver =
            createResolver(
                "feature.enabled" to "\${FLAG:not-a-boolean}",
            )

        assertEquals(null, resolver.getProperty("feature.enabled", Boolean::class))
    }
}

class InterpolatingPropertyResolverErrorHandlingTest {
    @Test
    fun getAllPropertiesReturnsEmptyMapForEmptySources() {
        val propertySources = DefaultPropertySources()
        val resolver = PropertyResolverFactory.create(propertySources, resolverLevel = ConfigLevel.APP)

        val all = resolver.getAllProperties()
        assertTrue(all.isEmpty())
    }

    @Test
    fun getSubPropertiesReturnsEmptyMapForNoMatches() {
        val source = MutableMapPropertySource("test")
        source.addProperty("other.key", "value")
        val propertySources = DefaultPropertySources().apply { add(source) }
        val resolver =
            PropertyResolverFactory.create(
                propertySources,
                DefaultPropertyInterpolator(),
                resolverLevel = ConfigLevel.APP,
            )

        val sub = resolver.getSubProperties(setOf("nonexistent"), stripPrefix = true)
        assertTrue(sub.isEmpty())
    }

    @Test
    fun getSubPropertiesWithMultiplePrefixes() {
        val source = MutableMapPropertySource("test")
        source.addProperty("db.host", "localhost")
        source.addProperty("db.port", 5432)
        source.addProperty("cache.host", "redis-host")
        source.addProperty("cache.port", 6379)
        val propertySources = DefaultPropertySources().apply { add(source) }
        val resolver =
            PropertyResolverFactory.create(
                propertySources,
                DefaultPropertyInterpolator(),
                resolverLevel = ConfigLevel.APP,
            )

        val sub = resolver.getSubProperties(setOf("db", "cache"), stripPrefix = true)

        assertTrue(sub.containsKey("host") || sub.containsKey("port"))
    }
}

class InterpolatingPropertyResolverContainmentTest {
    @Test
    fun tenantResolverAllowsExplicitAppEnvironmentDeclarationButDeniesSimpleEnvironmentSourceReference() {
        val app =
            ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                addProperty("deployment.explicit", "\${env:PATH}")
                addProperty("deployment.simple", "\${PATH}")
                addProtectedProperty("deployment.token", "server-owned", PropertyProtection.PROTECTED)
            }
        val sources =
            DefaultPropertySources(
                mutableListOf(
                    StaticProtectedEnvPropertySourceObject,
                    app,
                ),
            )
        val resolver =
            InterpolatingPropertySourcesPropertyResolver(
                propertySources = sources,
                interpolator = DefaultPropertyInterpolator(),
                resolverLevel = ConfigLevel.TENANT,
                interpolationPolicyProvider =
                    DefaultInterpolationPolicyProvider(
                        mapOf("deployment.explicit" to InterpolationPolicy.APP_ENVIRONMENT),
                    ),
            )

        val explicit = resolver.getPropertyAsString("deployment.explicit")
        assertTrue(!explicit.isNullOrEmpty())
        assertFalse(explicit.contains("\${"))

        assertFailsWith<IllegalStateException> {
            resolver.getPropertyAsString("deployment.simple")
        }

        assertFalse(resolver.containsProperty("deployment.token"))
        assertEquals(null, resolver.getPropertyAsString("deployment.token"))
        assertFailsWith<IllegalStateException> {
            resolver.getAllProperties()
        }
        assertFalse(
            resolver
                .getSubProperties(setOf("deployment.token"), stripPrefix = false)
                .containsKey("deployment.token"),
        )
    }
}
