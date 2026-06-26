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

import com.sphereon.core.api.IdkResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaceholderTokenTest {
    @Test
    fun simpleTokenHasCorrectType() {
        val token = PlaceholderToken.simple("\${key}", "key")
        assertEquals(PlaceholderType.SIMPLE, token.type)
        assertEquals("key", token.key)
        assertNull(token.defaultValue)
        assertNull(token.provider)
    }

    @Test
    fun simpleTokenWithDefaultHasCorrectValues() {
        val token = PlaceholderToken.simple("\${key:default}", "key", "default")
        assertEquals(PlaceholderType.SIMPLE, token.type)
        assertEquals("key", token.key)
        assertEquals("default", token.defaultValue)
    }

    @Test
    fun envTokenHasCorrectType() {
        val token = PlaceholderToken.env("\${env:HOME}", "HOME")
        assertEquals(PlaceholderType.ENV, token.type)
        assertEquals("HOME", token.key)
    }

    @Test
    fun scopeTokenHasCorrectTypeAndProvider() {
        val token = PlaceholderToken.scope("\${app:key}", "app", "key")
        assertEquals(PlaceholderType.SCOPE, token.type)
        assertEquals("key", token.key)
        assertEquals("app", token.provider)
    }

    @Test
    fun secretTokenHasCorrectTypeAndPath() {
        val token = PlaceholderToken.secret("\${secret:vault:path/to/secret}", "vault", "path/to/secret", "password")
        assertEquals(PlaceholderType.SECRET, token.type)
        assertEquals("password", token.key)
        assertEquals("vault", token.provider)
        assertEquals("path/to/secret", token.path)
    }
}

class DefaultPropertyInterpolatorContainsPlaceholdersTest {
    private val interpolator = DefaultPropertyInterpolator()

    @Test
    fun detectsSimplePlaceholder() {
        assertTrue(interpolator.containsPlaceholders("\${key}"))
    }

    @Test
    fun detectsPlaceholderWithDefault() {
        assertTrue(interpolator.containsPlaceholders("\${key:default}"))
    }

    @Test
    fun detectsEnvPlaceholder() {
        assertTrue(interpolator.containsPlaceholders("\${env:HOME}"))
    }

    @Test
    fun detectsSecretPlaceholder() {
        assertTrue(interpolator.containsPlaceholders("\${secret:vault:path}"))
    }

    @Test
    fun detectsMultiplePlaceholders() {
        assertTrue(interpolator.containsPlaceholders("\${a} and \${b}"))
    }

    @Test
    fun returnsFalseForNoPlaceholders() {
        assertFalse(interpolator.containsPlaceholders("plain text"))
    }

    @Test
    fun returnsFalseForIncompletePlaceholder() {
        assertFalse(interpolator.containsPlaceholders("\${incomplete"))
    }

    @Test
    fun returnsFalseForEmptyString() {
        assertFalse(interpolator.containsPlaceholders(""))
    }
}

class DefaultPropertyInterpolatorIsSecretReferenceTest {
    private val interpolator = DefaultPropertyInterpolator()

    @Test
    fun detectsSecretEnvReference() {
        assertTrue(interpolator.isSecretReference("\${secret:env:API_KEY}"))
    }

    @Test
    fun detectsSecretVaultReference() {
        assertTrue(interpolator.isSecretReference("\${secret:vault:path/to/secret}"))
    }

    @Test
    fun detectsSecretMapReference() {
        assertTrue(interpolator.isSecretReference("\${secret:map:credentials:key}"))
    }

    @Test
    fun returnsFalseForNonSecretPlaceholder() {
        assertFalse(interpolator.isSecretReference("\${plain.key}"))
    }

    @Test
    fun returnsFalseForPlainText() {
        assertFalse(interpolator.isSecretReference("not a secret"))
    }
}

class DefaultPropertyInterpolatorParsePlaceholdersTest {
    private val interpolator = DefaultPropertyInterpolator()

    @Test
    fun parsesSimplePlaceholder() {
        val tokens = interpolator.parsePlaceholders("\${my.key}")
        assertEquals(1, tokens.size)
        assertEquals(PlaceholderType.SIMPLE, tokens[0].type)
        assertEquals("my.key", tokens[0].key)
    }

    @Test
    fun parsesPlaceholderWithDefault() {
        val tokens = interpolator.parsePlaceholders("\${port:8080}")
        assertEquals(1, tokens.size)
        assertEquals("port", tokens[0].key)
        assertEquals("8080", tokens[0].defaultValue)
    }

    @Test
    fun parsesEnvPlaceholder() {
        val tokens = interpolator.parsePlaceholders("\${env:HOME}")
        assertEquals(1, tokens.size)
        assertEquals(PlaceholderType.ENV, tokens[0].type)
        assertEquals("HOME", tokens[0].key)
    }

    @Test
    fun parsesEnvPlaceholderWithDefault() {
        val tokens = interpolator.parsePlaceholders("\${env:PORT:3000}")
        assertEquals(1, tokens.size)
        assertEquals(PlaceholderType.ENV, tokens[0].type)
        assertEquals("PORT", tokens[0].key)
        assertEquals("3000", tokens[0].defaultValue)
    }

    @Test
    fun parsesScopePlaceholder() {
        val tokens = interpolator.parsePlaceholders("\${app:config.key}")
        assertEquals(1, tokens.size)
        assertEquals(PlaceholderType.SCOPE, tokens[0].type)
        assertEquals("config.key", tokens[0].key)
        assertEquals("app", tokens[0].provider)
    }

    @Test
    fun parsesPinnedSecretPlaceholder() {
        // Pinned provider via leading @. Provider-native slash paths (e.g. Vault kv paths) are
        // NOT treated as key delimiters, so the path is preserved verbatim (only lowercased).
        val tokens = interpolator.parsePlaceholders("\${secret:@vault:kv/data/myapp:password}")
        assertEquals(1, tokens.size)
        assertEquals(PlaceholderType.SECRET, tokens[0].type)
        assertEquals("vault", tokens[0].provider)
        assertEquals("kv/data/myapp", tokens[0].path)
        assertEquals("password", tokens[0].key)
    }

    @Test
    fun parsesPinnedSecretPlaceholderWithoutSubKey() {
        val tokens = interpolator.parsePlaceholders("\${secret:@env:API_KEY}")
        assertEquals(1, tokens.size)
        assertEquals(PlaceholderType.SECRET, tokens[0].type)
        assertEquals("env", tokens[0].provider)
        // The logical key is passed through verbatim here; normalization is owned by the
        // SecretAddressResolver during resolution.
        assertEquals("API_KEY", tokens[0].path)
    }

    @Test
    fun parsesCascadeSecretPlaceholder() {
        // No provider (cascade): provider is null, logical key normalized.
        val tokens = interpolator.parsePlaceholders("\${secret:db.password}")
        assertEquals(1, tokens.size)
        assertEquals(PlaceholderType.SECRET, tokens[0].type)
        assertNull(tokens[0].provider)
        assertEquals("db.password", tokens[0].path)
    }

    @Test
    fun cascadeSecretLogicalKeyIsPassedThroughVerbatim() {
        // The parser does NOT normalize; it carries the raw logical key. Normalization equivalence
        // (dotted == UPPER_SNAKE == hyphen == slash) is owned by SecretAddressResolver and covered
        // by SecretAddressResolverTest.
        val dotted = interpolator.parsePlaceholders("\${secret:example.secret.ref.value}")[0]
        val upper = interpolator.parsePlaceholders("\${secret:EXAMPLE_SECRET_REF_VALUE}")[0]
        assertEquals("example.secret.ref.value", dotted.path)
        assertEquals("EXAMPLE_SECRET_REF_VALUE", upper.path)
        assertNull(dotted.provider)
        assertNull(upper.provider)
    }

    @Test
    fun parsesMultiplePlaceholders() {
        val tokens = interpolator.parsePlaceholders("host=\${db.host} port=\${db.port}")
        assertEquals(2, tokens.size)
        assertEquals("db.host", tokens[0].key)
        assertEquals("db.port", tokens[1].key)
    }

    @Test
    fun returnsEmptyListForNoPlaceholders() {
        val tokens = interpolator.parsePlaceholders("plain text")
        assertTrue(tokens.isEmpty())
    }
}

class DefaultPropertyInterpolatorInterpolateTest {
    private fun createResolver(properties: Map<String, String>): PropertyResolver {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        return PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
    }

    @Test
    fun interpolatesSimplePlaceholder() =
        runTest {
            val resolver = createResolver(mapOf("name" to "World"))
            val interpolator = DefaultPropertyInterpolator()

            val result = interpolator.interpolate("Hello, \${name}!", resolver)

            assertTrue(result.isOk)
            assertEquals("Hello, World!", result.value)
        }

    @Test
    fun interpolatesMultiplePlaceholders() =
        runTest {
            val resolver =
                createResolver(
                    mapOf(
                        "first" to "Hello",
                        "second" to "World",
                    ),
                )
            val interpolator = DefaultPropertyInterpolator()

            val result = interpolator.interpolate("\${first}, \${second}!", resolver)

            assertTrue(result.isOk)
            assertEquals("Hello, World!", result.value)
        }

    @Test
    fun usesDefaultValueWhenPropertyMissing() =
        runTest {
            val resolver = createResolver(emptyMap())
            val interpolator = DefaultPropertyInterpolator()

            val result = interpolator.interpolate("Port: \${port:8080}", resolver)

            assertTrue(result.isOk)
            assertEquals("Port: 8080", result.value)
        }

    @Test
    fun handlesNestedPlaceholders() =
        runTest {
            val resolver =
                createResolver(
                    mapOf(
                        "env" to "prod",
                        "db.prod" to "prod-db.example.com",
                    ),
                )
            val interpolator = DefaultPropertyInterpolator()

            val result = interpolator.interpolate("\${db.\${env}}", resolver)

            assertTrue(result.isOk)
            assertEquals("prod-db.example.com", result.value)
        }

    @Test
    fun returnsErrorForMissingPropertyWithoutDefault() =
        runTest {
            val resolver = createResolver(emptyMap())
            val interpolator = DefaultPropertyInterpolator()

            val result = interpolator.interpolate("Value: \${missing}", resolver)

            assertTrue(result.isErr)
            assertEquals("NOT_FOUND_ERROR", result.error.code)
        }

    @Test
    fun detectsCircularReference() =
        runTest {
            val resolver =
                createResolver(
                    mapOf(
                        "a" to "\${b}",
                        "b" to "\${a}",
                    ),
                )
            val interpolator = DefaultPropertyInterpolator()

            val result = interpolator.interpolate("\${a}", resolver)

            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("Circular"),
            )
        }

    @Test
    fun respectsMaxDepth() =
        runTest {
            val properties = mutableMapOf<String, String>()
            for (i in 0..15) {
                properties["level$i"] = "\${level${i + 1}}"
            }
            properties["level16"] = "final"

            val resolver = createResolver(properties)
            val interpolator = DefaultPropertyInterpolator(maxDepth = 10)

            val result = interpolator.interpolate("\${level0}", resolver)

            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("depth"),
            )
        }

    @Test
    fun returnsOriginalStringWhenNoPlaceholders() =
        runTest {
            val resolver = createResolver(emptyMap())
            val interpolator = DefaultPropertyInterpolator()

            val result = interpolator.interpolate("plain text without placeholders", resolver)

            assertTrue(result.isOk)
            assertEquals("plain text without placeholders", result.value)
        }

    @Test
    fun preservesPartialTextAroundPlaceholders() =
        runTest {
            val resolver = createResolver(mapOf("host" to "localhost", "port" to "5432"))
            val interpolator = DefaultPropertyInterpolator()

            val result = interpolator.interpolate("jdbc:postgresql://\${host}:\${port}/mydb", resolver)

            assertTrue(result.isOk)
            assertEquals("jdbc:postgresql://localhost:5432/mydb", result.value)
        }
}

class BasicSecretResolverTest {
    @Test
    fun resolvesMapSecret() =
        runTest {
            val secretMaps =
                mapOf(
                    "db-creds" to
                        mapOf(
                            "username" to "admin",
                            "password" to "secret123",
                        ),
                )
            val resolver = BasicSecretResolver(secretMaps)

            val result = resolver.resolve("map", "db-creds", "password")

            assertTrue(result.isOk)
            assertEquals("secret123", result.value)
        }

    @Test
    fun returnsErrorForUnknownSecretMap() =
        runTest {
            val resolver = BasicSecretResolver(emptyMap())

            val result = resolver.resolve("map", "unknown-map", "key")

            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("not found"),
            )
        }

    @Test
    fun returnsErrorForMissingKeyInMap() =
        runTest {
            val secretMaps = mapOf("creds" to mapOf("user" to "admin"))
            val resolver = BasicSecretResolver(secretMaps)

            val result = resolver.resolve("map", "creds", "password")

            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("not found"),
            )
        }

    @Test
    fun returnsErrorForUnknownProvider() =
        runTest {
            val resolver = BasicSecretResolver(emptyMap())

            val result = resolver.resolve("vault", "path/to/secret", "key")

            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("Unknown provider"),
            )
        }

    @Test
    fun returnsErrorForMapSecretWithoutKey() =
        runTest {
            val secretMaps = mapOf("creds" to mapOf("user" to "admin"))
            val resolver = BasicSecretResolver(secretMaps)

            val result = resolver.resolve("map", "creds", null)

            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("No key specified"),
            )
        }
}

class PropertyInterpolatorWithSecretsTest {
    private fun createResolverAndInterpolator(
        properties: Map<String, String>,
        secrets: Map<String, Map<String, String>>,
    ): Pair<PropertyResolver, DefaultPropertyInterpolator> {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        val resolver = PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })

        val secretResolver = BasicSecretResolver(secrets)
        val interpolator = DefaultPropertyInterpolator(secretResolver = secretResolver)

        return resolver to interpolator
    }

    @Test
    fun interpolatesPinnedMapSecretReference() =
        runTest {
            val (resolver, interpolator) =
                createResolverAndInterpolator(
                    properties = mapOf("db.password" to "\${secret:@map:credentials:password}"),
                    secrets = mapOf("credentials" to mapOf("password" to "supersecret")),
                )

            val rawValue = resolver.getPropertyAsString("db.password")
            assertNotNull(rawValue)

            val result = interpolator.interpolate(rawValue, resolver)

            assertTrue(result.isOk)
            assertEquals("supersecret", result.value)
        }

    @Test
    fun combinesRegularAndPinnedSecretInterpolation() =
        runTest {
            val (resolver, interpolator) =
                createResolverAndInterpolator(
                    properties =
                        mapOf(
                            "db.host" to "localhost",
                            "db.url" to "jdbc:postgresql://\${db.host}:5432/mydb?password=\${secret:@map:creds:pass}",
                        ),
                    secrets = mapOf("creds" to mapOf("pass" to "secret123")),
                )

            val rawValue = resolver.getPropertyAsString("db.url")
            assertNotNull(rawValue)

            val result = interpolator.interpolate(rawValue, resolver)

            assertTrue(result.isOk)
            assertEquals("jdbc:postgresql://localhost:5432/mydb?password=secret123", result.value)
        }
}

/**
 * Captures the (provider, path, key, scope, scopeIdentifier) tuple the interpolator forwards,
 * and returns a fixed value, to verify cascade + scope-identity threading end-to-end.
 */
private class CapturingSecretResolver(
    private val returnValue: String = "resolved",
) : SecretResolver {
    var lastProvider: String? = null
    var lastPath: String? = null
    var lastKey: String? = null
    var lastScope: ConfigLevel? = null
    var lastScopeIdentifier: String? = null

    override suspend fun resolve(
        provider: String?,
        path: String,
        key: String?,
    ): IdkResult<String, com.sphereon.core.api.error.IdkError> = resolve(provider, path, key, null, null, null)

    override suspend fun resolve(
        provider: String?,
        path: String,
        key: String?,
        scope: ConfigLevel?,
        scopeIdentifier: String?,
    ): IdkResult<String, com.sphereon.core.api.error.IdkError> = resolve(provider, path, key, scope, scopeIdentifier, null)

    override suspend fun resolve(
        provider: String?,
        path: String,
        key: String?,
        scope: ConfigLevel?,
        scopeIdentifier: String?,
        resolver: PropertyResolver?,
    ): IdkResult<String, com.sphereon.core.api.error.IdkError> {
        lastProvider = provider
        lastPath = path
        lastKey = key
        lastScope = scope
        lastScopeIdentifier = scopeIdentifier
        return com.sphereon.core.api
            .Ok(returnValue)
    }
}

class PropertyInterpolatorScopeThreadingTest {
    private fun emptyResolver(): PropertyResolver = PropertySourcesPropertyResolver(DefaultPropertySources())

    @Test
    fun cascadeSecretForwardsNullProviderAndScopeIdentifier() =
        runTest {
            val capturing = CapturingSecretResolver(returnValue = "tenant-secret")
            val interpolator = DefaultPropertyInterpolator(secretResolver = capturing)

            val result =
                interpolator.interpolate(
                    value = "\${secret:db.password}",
                    resolver = emptyResolver(),
                    requestingScope = ConfigLevel.TENANT,
                    maxDepth = null,
                    resolveSecrets = true,
                    scopeIdentifier = "tenant-123",
                )

            assertTrue(result.isOk)
            assertEquals("tenant-secret", result.value)
            // Cascade form -> null provider; normalized logical key; threaded scope identity.
            assertNull(capturing.lastProvider)
            assertEquals("db.password", capturing.lastPath)
            assertEquals(ConfigLevel.TENANT, capturing.lastScope)
            assertEquals("tenant-123", capturing.lastScopeIdentifier)
        }

    @Test
    fun pinnedSecretForwardsProviderAndScopeIdentifier() =
        runTest {
            val capturing = CapturingSecretResolver()
            val interpolator = DefaultPropertyInterpolator(secretResolver = capturing)

            val result =
                interpolator.interpolate(
                    value = "\${secret:@vault:db.password}",
                    resolver = emptyResolver(),
                    requestingScope = ConfigLevel.PRINCIPAL,
                    maxDepth = null,
                    resolveSecrets = true,
                    scopeIdentifier = "principal-7",
                )

            assertTrue(result.isOk)
            assertEquals("vault", capturing.lastProvider)
            assertEquals("db.password", capturing.lastPath)
            assertEquals(ConfigLevel.PRINCIPAL, capturing.lastScope)
            assertEquals("principal-7", capturing.lastScopeIdentifier)
        }
}
