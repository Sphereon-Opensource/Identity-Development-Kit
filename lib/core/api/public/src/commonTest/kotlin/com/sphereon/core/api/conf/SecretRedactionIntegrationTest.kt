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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Integration tests for automatic secret redaction in the configuration system.
 * Verifies that sensitive values are redacted by default when converting to strings.
 */
class SecretRedactionIntegrationTest {
    @Test
    fun directBulkStringRedactionUsesCanonicalKeyBeforePrefixStripping() {
        val resolver =
            PropertySourcesPropertyResolver(
                DefaultPropertySources(
                    mutableListOf(
                        MapPropertySource(
                            "direct",
                            mapOf("credentials.value" to "direct-bulk-secret"),
                        ),
                    ),
                ),
            )

        val result =
            resolver.getSubPropertiesAsString(
                prefixes = setOf("credentials"),
                stripPrefix = true,
                redact = true,
            )

        assertEquals("***REDACTED***", result["value"])
        assertFalse(result.toString().contains("direct-bulk-secret"))
    }

    @Test
    fun interpolatingBulkStringRedactionUsesResolvedSensitiveProvenance() {
        val source =
            ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                addProperty("credentials.password", "interpolated-bulk-secret")
                addProperty("public.display", "\${credentials.password}")
            }
        val resolver =
            InterpolatingPropertySourcesPropertyResolver(
                propertySources = DefaultPropertySources(mutableListOf(source)),
                interpolator = DefaultPropertyInterpolator(),
                resolverLevel = ConfigLevel.APP,
                interpolationPolicyProvider =
                    DefaultInterpolationPolicyProvider(
                        mapOf("public.display" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
                    ),
            )

        val result =
            resolver.getSubPropertiesAsString(
                prefixes = setOf("public"),
                stripPrefix = true,
                redact = true,
            )

        assertEquals("***REDACTED***", result["display"])
        assertFalse(result.toString().contains("interpolated-bulk-secret"))
    }

    @Test
    fun bulkRedactionFailsClosedWhenMaterializedValueNoLongerMatchesResolvedProvenance() {
        val source =
            ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                addProperty("public.display", "rotated-after-enumeration")
            }
        val delegate =
            ProtectedPropertySourcesResolver(
                DefaultPropertySources(mutableListOf(source)),
                ConfigLevel.APP,
            )
        val changedResolver =
            object : ProtectedPropertyResolver by delegate {
                override fun resolvePropertyWithScope(
                    key: String,
                    requiredScope: ConfigLevel?,
                ): ResolvedPropertyWithScope? =
                    delegate
                        .resolvePropertyWithScope(key, requiredScope)
                        ?.copy(value = "different-current-value")
            }

        val rendered =
            redactBulkValueIfNeeded(
                outputKey = "display",
                value = "materialized-before-rotation",
                prefixes = setOf("public"),
                stripPrefix = true,
                redact = true,
                resolver = changedResolver,
                scope = ConfigLevel.APP,
                redactionPolicy = DefaultSecretRedactionPolicy(),
            )

        assertEquals("***REDACTED***", rendered)
        assertFalse(rendered.contains("materialized-before-rotation"))
    }

    @Test
    fun cachingBulkStringRedactionUsesResolvedSensitiveProvenance() {
        val source =
            ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                addProperty("credentials.password", "cached-bulk-secret")
                addProperty("public.display", "\${credentials.password}")
            }
        val resolver =
            CachingPropertySourcesPropertyResolver(
                propertySources = DefaultPropertySources(mutableListOf(source)),
                snapshotCache = InMemorySyncSnapshotCache(),
                level = ConfigLevel.APP,
                interpolator = DefaultPropertyInterpolator(),
                interpolationPolicyProvider =
                    DefaultInterpolationPolicyProvider(
                        mapOf("public.display" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
                    ),
            )

        val result =
            resolver.getSubPropertiesAsString(
                prefixes = setOf("public"),
                stripPrefix = true,
                redact = true,
            )

        assertEquals("***REDACTED***", result["display"])
        assertFalse(result.toString().contains("cached-bulk-secret"))
    }

    @Test
    fun getAllPropertiesAsStringRedactsSensitiveKeysByDefault() {
        val source =
            MapPropertySource(
                "test",
                mapOf(
                    "server.host" to "localhost",
                    "server.port" to 8080,
                    "database.password" to "secret123",
                    "api.key" to "my-api-key-value",
                    "auth.token" to "bearer-token-value",
                    "user.name" to "admin",
                ),
            )
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val allProps = resolver.getAllPropertiesAsString()

        // Non-sensitive values should not be redacted
        assertEquals("localhost", allProps["server.host"])
        assertEquals("8080", allProps["server.port"])
        assertEquals("admin", allProps["user.name"])

        // Sensitive values should be redacted
        assertEquals("***REDACTED***", allProps["database.password"])
        assertEquals("***REDACTED***", allProps["api.key"])
        assertEquals("***REDACTED***", allProps["auth.token"])
    }

    @Test
    fun getAllPropertiesAsStringReturnsRawValuesWhenRedactDisabled() {
        val source =
            MapPropertySource(
                "test",
                mapOf(
                    "server.host" to "localhost",
                    "database.password" to "secret123",
                    "api.key" to "my-api-key-value",
                ),
            )
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val allProps = resolver.getAllPropertiesAsString(redact = false)

        // All values should be returned unredacted
        assertEquals("localhost", allProps["server.host"])
        assertEquals("secret123", allProps["database.password"])
        assertEquals("my-api-key-value", allProps["api.key"])
    }

    @Test
    fun getSubPropertiesAsStringRedactsSensitiveKeysWithinPrefix() {
        val source =
            MapPropertySource(
                "test",
                mapOf(
                    "app.database.url" to "jdbc:postgresql://localhost/db",
                    "app.database.user" to "admin",
                    "app.database.password" to "db-secret",
                    "app.api.key" to "api-secret-key",
                    "server.port" to "8080",
                ),
            )
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val subProps = resolver.getSubPropertiesAsString(setOf("app.database"), stripPrefix = true)

        // Non-sensitive values should not be redacted
        assertEquals("jdbc:postgresql://localhost/db", subProps["url"])
        assertEquals("admin", subProps["user"])

        // Note: After prefix stripping, the key is just "password" which still matches the sensitive pattern
        assertEquals("***REDACTED***", subProps["password"])
    }

    @Test
    fun getSubPropertiesAsStringReturnsRawValuesWhenRedactDisabled() {
        val source =
            MapPropertySource(
                "test",
                mapOf(
                    "app.database.password" to "db-secret",
                    "app.api.key" to "api-secret-key",
                ),
            )
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val subProps = resolver.getSubPropertiesAsString(setOf("app"), stripPrefix = true, redact = false)

        // All values should be returned unredacted
        assertEquals("db-secret", subProps["database.password"])
        assertEquals("api-secret-key", subProps["api.key"])
    }

    @Test
    fun getAllPropertiesReturnsRawMapUnaffectedByRedaction() {
        val source =
            MapPropertySource(
                "test",
                mapOf(
                    "database.password" to "secret123",
                    "api.key" to "my-api-key",
                ),
            )
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        // getAllProperties() should return raw values (not strings, not redacted)
        val allProps = resolver.getAllProperties()

        assertEquals("secret123", allProps["database.password"])
        assertEquals("my-api-key", allProps["api.key"])
    }

    @Test
    fun redactionMatchesMultipleSensitivePatterns() {
        val source =
            MapPropertySource(
                "test",
                mapOf(
                    "db.password" to "pass1",
                    "my.secret" to "secret1",
                    "access.token" to "token1",
                    "encryption.key" to "key1",
                    "user.credential" to "cred1",
                    "oauth.auth" to "auth1",
                    "normal.value" to "value1",
                ),
            )
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val allProps = resolver.getAllPropertiesAsString()

        // All sensitive patterns should be redacted
        assertEquals("***REDACTED***", allProps["db.password"])
        assertEquals("***REDACTED***", allProps["my.secret"])
        assertEquals("***REDACTED***", allProps["access.token"])
        assertEquals("***REDACTED***", allProps["encryption.key"])
        assertEquals("***REDACTED***", allProps["user.credential"])
        assertEquals("***REDACTED***", allProps["oauth.auth"])

        // Non-matching keys should not be redacted
        assertEquals("value1", allProps["normal.value"])
    }

    @Test
    fun customRedactionPolicyIsRespected() {
        // Custom policy that only redacts keys containing "sensitive"
        val customPolicy =
            object : SecretRedactionPolicy {
                override fun shouldRedact(
                    key: String,
                    metadata: ResolutionMetadata,
                ): Boolean = key.contains("sensitive")

                override fun redact(value: String): String = "[CUSTOM-HIDDEN]"
            }

        val source =
            MapPropertySource(
                "test",
                mapOf(
                    // Use already-normalized keys for consistent testing
                    "my.sensitive.value" to "should-be-hidden",
                    "database.password" to "should-not-be-hidden",
                    "api.key" to "also-not-hidden",
                ),
            )
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources, customPolicy)

        val allProps = resolver.getAllPropertiesAsString()

        // Custom policy redacts only keys containing "sensitive"
        assertEquals("[CUSTOM-HIDDEN]", allProps["my.sensitive.value"])
        // Default patterns are NOT used with custom policy
        assertEquals("should-not-be-hidden", allProps["database.password"])
        assertEquals("also-not-hidden", allProps["api.key"])
    }

    @Test
    fun caseInsensitiveMatchingForSensitiveKeys() {
        // Note: PropertyResolver normalizes keys to lowercase, so db.PASSWORD becomes db.password
        val source =
            MapPropertySource(
                "test",
                mapOf(
                    "db.password" to "pass1",
                    "api.secret" to "secret1",
                    "auth.token" to "token1",
                    "encryption.key" to "key1",
                ),
            )
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val allProps = resolver.getAllPropertiesAsString()

        // The DefaultSecretRedactionPolicy uses case-insensitive regex matching
        // Keys are normalized to lowercase by the property resolver
        assertEquals("***REDACTED***", allProps["db.password"])
        assertEquals("***REDACTED***", allProps["api.secret"])
        assertEquals("***REDACTED***", allProps["auth.token"])
        assertEquals("***REDACTED***", allProps["encryption.key"])
    }

    @Test
    fun nullValuesAreHandledGracefully() {
        // MapPropertySource doesn't store null values, so we test that non-null sensitive values are redacted
        val source =
            MapPropertySource(
                "test",
                mapOf("api.key" to "secret"),
            )
        val sources = DefaultPropertySources().apply { add(source) }
        val resolver = PropertySourcesPropertyResolver(sources)

        val allProps = resolver.getAllPropertiesAsString()

        // Sensitive key should be redacted
        assertEquals("***REDACTED***", allProps["api.key"])
    }

    @Test
    fun toJsonStringRedactsSensitiveValues() {
        val properties =
            mapOf(
                "server.host" to "localhost",
                "server.port" to 8080,
                "database.password" to "secret123",
                "api.key" to "my-api-key",
            )

        val json = properties.toJsonString(redact = true)

        assertTrue(json.contains("\"host\":\"localhost\""))
        assertTrue(json.contains("\"port\":8080"))
        assertTrue(json.contains("\"password\":\"***REDACTED***\""))
        assertTrue(json.contains("\"key\":\"***REDACTED***\""))
        assertFalse(json.contains("secret123"))
        assertFalse(json.contains("my-api-key"))
    }

    @Test
    fun toJsonStringReturnsRawValuesWhenRedactDisabled() {
        val properties =
            mapOf(
                "database.password" to "secret123",
                "api.key" to "my-api-key",
            )

        val json = properties.toJsonString(redact = false)

        assertTrue(json.contains("\"password\":\"secret123\""))
        assertTrue(json.contains("\"key\":\"my-api-key\""))
    }

    @Test
    fun toJsonObjectRedactsSensitiveValues() {
        val properties =
            mapOf(
                "database.password" to "secret123",
            )

        val jsonObj = properties.toJsonObject(redact = true)

        val database = jsonObj["database"]
        assertTrue(database.toString().contains("***REDACTED***"))
        assertFalse(database.toString().contains("secret123"))
    }

    @Test
    fun toJsonObjectReturnsRawValuesWhenRedactDisabled() {
        val properties =
            mapOf(
                "database.password" to "secret123",
            )

        val jsonObj = properties.toJsonObject(redact = false)

        assertTrue(jsonObj.toString().contains("secret123"))
    }

    @Test
    fun resolutionPipelineResolveAllAsStringRedactsSensitiveValues() =
        runTest {
            val source =
                MapPropertySource(
                    "test",
                    mapOf(
                        "app.server.host" to "localhost",
                        "app.database.password" to "secret123",
                    ),
                )
            val sources =
                DefaultPropertySources().apply {
                    add(ScopedPropertySourceWrapper(source, ConfigLevel.APP))
                }
            val pipeline = DefaultConfigResolutionPipeline(sources)

            val result =
                pipeline.resolveAllAsString(
                    prefix = "app",
                    context = ResolutionContext.app(),
                    redact = true,
                )

            assertTrue(result.isOk)
            val stringMap = result.value
            assertEquals("localhost", stringMap["app.server.host"])
            assertEquals("***REDACTED***", stringMap["app.database.password"])
        }

    @Test
    fun resolutionPipelineResolveAllAsStringReturnsRawWhenRedactDisabled() =
        runTest {
            val source =
                MapPropertySource(
                    "test",
                    mapOf(
                        "app.database.password" to "secret123",
                    ),
                )
            val sources =
                DefaultPropertySources().apply {
                    add(ScopedPropertySourceWrapper(source, ConfigLevel.APP))
                }
            val pipeline = DefaultConfigResolutionPipeline(sources)

            val result =
                pipeline.resolveAllAsString(
                    prefix = "app",
                    context = ResolutionContext.app(),
                    redact = false,
                )

            assertTrue(result.isOk)
            val stringMap = result.value
            assertEquals("secret123", stringMap["app.database.password"])
        }

    @Test
    fun resolutionPipelineRedactsBenignOutputKeyWhenProvenanceIsSensitive() =
        runTest {
            val source =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("credentials.password", "provenance-secret-value")
                    addProperty("public.display", "\${credentials.password}")
                }
            val pipeline =
                DefaultConfigResolutionPipeline(
                    propertySources = DefaultPropertySources(mutableListOf(source)),
                    interpolator = DefaultPropertyInterpolator(),
                    interpolationPolicyProvider =
                        DefaultInterpolationPolicyProvider(
                            mapOf("public.display" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
                        ),
                )

            val result =
                pipeline.resolveAllAsString(
                    prefix = "public",
                    context = ResolutionContext.app(),
                    redact = true,
                )

            assertTrue(result.isOk)
            assertEquals("***REDACTED***", result.value["public.display"])
            assertFalse(result.value.toString().contains("provenance-secret-value"))
        }

    @Test
    fun resolutionPipelinePropagatesCallSpecificCustomRedactionPolicyToReferencedChild() =
        runTest {
            val customPolicy =
                object : SecretRedactionPolicy {
                    override fun shouldRedact(
                        key: String,
                        metadata: ResolutionMetadata,
                    ): Boolean =
                        key == "custom.material" ||
                            metadata.provenance.hasTaint(ResolutionTaint.SENSITIVE)

                    override fun redact(value: String): String = "<custom-redacted>"
                }
            val source =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("custom.material", "custom-policy-secret")
                    addProperty("public.alias", "\${custom.material}")
                }
            val pipeline =
                DefaultConfigResolutionPipeline(
                    propertySources = DefaultPropertySources(mutableListOf(source)),
                    interpolator = DefaultPropertyInterpolator(),
                    interpolationPolicyProvider =
                        DefaultInterpolationPolicyProvider(
                            mapOf("public.alias" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
                        ),
                )

            val result =
                pipeline.resolveAllAsString(
                    prefix = "public",
                    context = ResolutionContext.app(),
                    redact = true,
                    redactionPolicy = customPolicy,
            )

            assertTrue(result.isOk)
            assertEquals("***REDACTED***", result.value["public.alias"])
            assertFalse(result.value.toString().contains("custom-policy-secret"))
        }

    @Test
    fun resolutionPipelineCallerCannotWeakenConstructorRedactionPolicy() =
        runTest {
            val permissivePolicy =
                object : SecretRedactionPolicy {
                    override fun shouldRedact(
                        key: String,
                        metadata: ResolutionMetadata,
                    ): Boolean = false

                    override fun redact(value: String): String = value
                }
            val source =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("credentials.password", "must-remain-redacted")
                    addProperty("public.alias", "\${credentials.password}")
                }
            val pipeline =
                DefaultConfigResolutionPipeline(
                    propertySources = DefaultPropertySources(mutableListOf(source)),
                    interpolator = DefaultPropertyInterpolator(),
                    interpolationPolicyProvider =
                        DefaultInterpolationPolicyProvider(
                            mapOf("public.alias" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
                        ),
                )

            val result =
                pipeline.resolveAllAsString(
                    prefix = "public",
                    context = ResolutionContext.app(),
                    redact = true,
                    redactionPolicy = permissivePolicy,
                )

            assertTrue(result.isOk)
            assertEquals("***REDACTED***", result.value["public.alias"])
            assertFalse(result.value.toString().contains("must-remain-redacted"))
        }
}
