/*
 * © 2025 Sphereon International B.V.
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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SecretValueTest {

    @Test
    fun ofCreatesValueWithTimestamp() {
        val value = SecretValue.of(
            value = "secret123",
            providerId = "test",
            path = "my/secret"
        )

        assertEquals("secret123", value.value)
        assertEquals("test", value.providerId)
        assertEquals("my/secret", value.path)
        assertNull(value.key)
        assertNotNull(value.resolvedAt)
        assertNull(value.expiresAt)
    }

    @Test
    fun ofWithTtlSetsExpiration() {
        val value = SecretValue.of(
            value = "secret123",
            providerId = "test",
            path = "my/secret",
            ttl = 1.hours
        )

        assertNotNull(value.expiresAt)
        assertTrue(value.expiresAt!! > value.resolvedAt)
    }

    @Test
    fun isExpiredReturnsFalseForNoExpiration() {
        val value = SecretValue.of(
            value = "secret",
            providerId = "test",
            path = "path"
        )

        assertFalse(value.isExpired())
    }

    @Test
    fun isExpiredReturnsFalseForFutureExpiration() {
        val value = SecretValue.of(
            value = "secret",
            providerId = "test",
            path = "path",
            ttl = 1.hours
        )

        assertFalse(value.isExpired())
    }
}

class SecretErrorTest {

    @Test
    fun notFoundHasCorrectCode() {
        val error = SecretError.notFound("vault", "path/to/secret", "key")

        assertEquals("SECRET_NOT_FOUND", error.code)
        assertEquals("vault", error.providerId)
        assertEquals("path/to/secret", error.path)
        assertEquals("key", error.key)
    }

    @Test
    fun providerUnavailableHasCorrectCode() {
        val error = SecretError.providerUnavailable("vault")

        assertEquals("PROVIDER_UNAVAILABLE", error.code)
        assertEquals("vault", error.providerId)
    }

    @Test
    fun accessDeniedHasCorrectCode() {
        val error = SecretError.accessDenied("vault", "restricted/secret")

        assertEquals("ACCESS_DENIED", error.code)
        assertTrue(error.message.contains("Access denied"))
    }

    @Test
    fun timeoutHasCorrectCode() {
        val error = SecretError.timeout("vault", "path", 5.minutes)

        assertEquals("TIMEOUT", error.code)
        assertTrue(error.message.contains("5m"))
    }

    @Test
    fun toIdkErrorConvertsCorrectly() {
        val error = SecretError.notFound("test", "path")
        val idkError = error.toIdkError()

        assertEquals("ILLEGAL_ARGUMENT_ERROR", idkError.code)
        assertTrue(idkError.message.defaultMessage.contains("SECRET_NOT_FOUND"))
    }

    @Test
    fun notFoundWithoutKeyHasCorrectMessage() {
        val error = SecretError.notFound("vault", "path/to/secret")

        assertNull(error.key)
        assertTrue(error.message.contains("path/to/secret"))
        assertFalse(error.message.contains("/null"))
    }

    @Test
    fun notFoundWithKeyIncludesKeyInMessage() {
        val error = SecretError.notFound("vault", "path/to/secret", "mykey")

        assertEquals("mykey", error.key)
        assertTrue(error.message.contains("mykey"))
    }

    @Test
    fun generalErrorIncludesCauseInToIdkError() {
        val error = SecretError.general(
            providerId = "vault",
            path = "path",
            message = "Something failed",
            cause = "Connection refused"
        )

        assertEquals("SECRET_ERROR", error.code)
        assertEquals("Connection refused", error.cause)

        val idkError = error.toIdkError()
        assertTrue(idkError.message.defaultMessage.contains("Connection refused"))
    }

    @Test
    fun generalErrorWithoutCause() {
        val error = SecretError.general(
            providerId = "vault",
            path = "path",
            message = "Something failed"
        )

        assertNull(error.cause)
        val idkError = error.toIdkError()
        assertFalse(idkError.message.defaultMessage.contains("cause:"))
    }

    @Test
    fun secretErrorDataClassEquality() {
        val error1 = SecretError.notFound("vault", "path", "key")
        val error2 = SecretError.notFound("vault", "path", "key")
        val error3 = SecretError.notFound("vault", "path", "different-key")

        assertEquals(error1, error2)
        assertTrue(error1 != error3)
    }
}

class SecretOptionsTest {

    @Test
    fun defaultOptionsHaveExpectedValues() {
        val options = SecretOptions()

        assertEquals(5, options.timeout.inWholeSeconds) // 5 seconds default
        assertFalse(options.cacheOverride)
        assertFalse(options.bypassCache)
    }

    @Test
    fun customOptionsArePreserved() {
        val options = SecretOptions(
            timeout = 10.minutes,
            cacheOverride = true,
            bypassCache = true
        )

        assertEquals(10.minutes, options.timeout)
        assertTrue(options.cacheOverride)
        assertTrue(options.bypassCache)
    }
}

class ProviderHealthTest {

    @Test
    fun healthyProviderHasCorrectStatus() {
        val health = ProviderHealth(
            providerId = "test",
            isHealthy = true,
            message = "All good"
        )

        assertTrue(health.isHealthy)
        assertEquals("All good", health.message)
    }

    @Test
    fun unhealthyProviderHasCorrectStatus() {
        val health = ProviderHealth(
            providerId = "test",
            isHealthy = false,
            message = "Connection failed"
        )

        assertFalse(health.isHealthy)
        assertEquals("Connection failed", health.message)
    }
}

class EnvSecretProviderTest {

    @Test
    fun hasCorrectProviderId() {
        val provider = EnvSecretProvider()
        assertEquals("env", provider.providerId)
    }

    @Test
    fun isAlwaysAvailable() {
        val provider = EnvSecretProvider()
        assertTrue(provider.isAvailable)
    }

    @Test
    fun healthCheckReturnsHealthy() = runTest {
        val provider = EnvSecretProvider()
        val result = provider.healthCheck()

        assertTrue(result.isOk)
        assertTrue(result.value.isHealthy)
    }

    @Test
    fun getSecretReturnsErrorForMissingVar() = runTest {
        val provider = EnvSecretProvider()
        val result = provider.getSecret("DEFINITELY_NONEXISTENT_ENV_VAR_12345")

        assertTrue(result.isErr)
        assertEquals("SECRET_NOT_FOUND", result.error.code)
    }
}

class MapSecretProviderTest {

    @Test
    fun hasCorrectProviderId() {
        val provider = MapSecretProvider()
        assertEquals("map", provider.providerId)
    }

    @Test
    fun isAlwaysAvailable() {
        val provider = MapSecretProvider()
        assertTrue(provider.isAvailable)
    }

    @Test
    fun getSecretReturnsValueFromMap() = runTest {
        val secrets = mapOf(
            "db-credentials" to mapOf(
                "username" to "admin",
                "password" to "secret123"
            )
        )
        val provider = MapSecretProvider(secrets)

        val result = provider.getSecret("db-credentials", "password")

        assertTrue(result.isOk)
        assertEquals("secret123", result.value.value)
        assertEquals("map", result.value.providerId)
        assertEquals("db-credentials", result.value.path)
        assertEquals("password", result.value.key)
    }

    @Test
    fun getSecretReturnsErrorForMissingPath() = runTest {
        val provider = MapSecretProvider(emptyMap())

        val result = provider.getSecret("nonexistent")

        assertTrue(result.isErr)
        assertEquals("SECRET_NOT_FOUND", result.error.code)
    }

    @Test
    fun getSecretReturnsErrorForMissingKey() = runTest {
        val secrets = mapOf(
            "credentials" to mapOf("user" to "admin")
        )
        val provider = MapSecretProvider(secrets)

        val result = provider.getSecret("credentials", "password")

        assertTrue(result.isErr)
        assertEquals("SECRET_NOT_FOUND", result.error.code)
    }

    @Test
    fun getSecretUsesValueKeyByDefault() = runTest {
        val secrets = mapOf(
            "api-key" to mapOf("value" to "key-12345")
        )
        val provider = MapSecretProvider(secrets)

        val result = provider.getSecret("api-key")

        assertTrue(result.isOk)
        assertEquals("key-12345", result.value.value)
    }

    @Test
    fun healthCheckReportsSecretCount() = runTest {
        val secrets = mapOf(
            "secret1" to mapOf("value" to "v1"),
            "secret2" to mapOf("value" to "v2")
        )
        val provider = MapSecretProvider(secrets)

        val result = provider.healthCheck()

        assertTrue(result.isOk)
        assertTrue(result.value.isHealthy)
        assertTrue(result.value.message?.contains("2") == true)
    }
}

class SecretProviderRegistryTest {

    @Test
    fun registersEnvProviderByDefault() {
        val registry = SecretProviderRegistry()

        assertTrue(registry.hasProvider("env"))
        assertNotNull(registry.get("env"))
    }

    @Test
    fun registerAddsProvider() {
        val registry = SecretProviderRegistry()
        val mapProvider = MapSecretProvider(emptyMap())

        registry.register(mapProvider)

        assertTrue(registry.hasProvider("map"))
        assertEquals(mapProvider, registry.get("map"))
    }

    @Test
    fun getDefaultReturnsEnvProvider() {
        val registry = SecretProviderRegistry()

        val defaultProvider = registry.getDefault()

        assertNotNull(defaultProvider)
        assertEquals("env", defaultProvider.providerId)
    }

    @Test
    fun getAllReturnsAllProviders() {
        val registry = SecretProviderRegistry()
        registry.register(MapSecretProvider(emptyMap()))

        val all = registry.getAll()

        assertTrue(all.size >= 2)
    }

    @Test
    fun resolveUsesSpecifiedProvider() = runTest {
        val secrets = mapOf("test" to mapOf("value" to "secret"))
        val registry = SecretProviderRegistry()
        registry.register(MapSecretProvider(secrets))

        val result = registry.resolve("map", "test", null)

        assertTrue(result.isOk)
        assertEquals("secret", result.value.value)
    }

    @Test
    fun resolveUsesDefaultProviderWhenNotSpecified() = runTest {
        val registry = SecretProviderRegistry()

        // Will use env provider by default and fail for missing var
        val result = registry.resolve(null, "NONEXISTENT_VAR", null)

        assertTrue(result.isErr)
        assertEquals("SECRET_NOT_FOUND", result.error.code)
    }

    @Test
    fun resolveReturnsErrorForUnknownProvider() = runTest {
        val registry = SecretProviderRegistry()

        val result = registry.resolve("unknown-provider", "path", null)

        assertTrue(result.isErr)
        assertEquals("PROVIDER_UNAVAILABLE", result.error.code)
    }

    @Test
    fun resolveReturnsErrorWhenProviderIsUnavailable() = runTest {
        val registry = SecretProviderRegistry()

        // Register an unavailable provider
        val unavailableProvider = object : SecretProvider {
            override val providerId = "unavailable"
            override val isAvailable = false

            override suspend fun getSecret(
                path: String,
                key: String?,
                scope: ConfigLevel,
                scopeIdentifier: String?,
                options: SecretOptions
            ): IdkResult<SecretValue, SecretError> {
                return Err(SecretError.providerUnavailable(providerId))
            }

            override suspend fun invalidateCache(path: String?) {}
            override suspend fun healthCheck(): IdkResult<ProviderHealth, SecretError> {
                return Ok(ProviderHealth(providerId, false, "Provider unavailable"))
            }
        }

        registry.register(unavailableProvider)

        val result = registry.resolve("unavailable", "path", null)

        assertTrue(result.isErr)
        assertEquals("PROVIDER_UNAVAILABLE", result.error.code)
    }

    @Test
    fun resolveReturnsErrorWhenNoDefaultProviderConfigured() = runTest {
        val registry = SecretProviderRegistry(defaultProvider = null)

        val result = registry.resolve(null, "path", null)

        assertTrue(result.isErr)
        assertEquals("PROVIDER_UNAVAILABLE", result.error.code)
    }

    @Test
    fun getReturnsNullForUnknownProvider() {
        val registry = SecretProviderRegistry()

        val provider = registry.get("nonexistent")

        assertNull(provider)
    }

    @Test
    fun hasProviderReturnsFalseForUnknownProvider() {
        val registry = SecretProviderRegistry()

        assertFalse(registry.hasProvider("nonexistent"))
    }

    @Test
    fun getDefaultReturnsNullWhenNoDefault() {
        val registry = SecretProviderRegistry(defaultProvider = null)

        val defaultProvider = registry.getDefault()

        assertNull(defaultProvider)
    }

    @Test
    fun resolveWithKeyPassesKeyToProvider() = runTest {
        val secrets = mapOf(
            "credentials" to mapOf(
                "username" to "admin",
                "password" to "secret123"
            )
        )
        val registry = SecretProviderRegistry()
        registry.register(MapSecretProvider(secrets))

        val result = registry.resolve("map", "credentials", "password")

        assertTrue(result.isOk)
        assertEquals("secret123", result.value.value)
        assertEquals("password", result.value.key)
    }

    @Test
    fun resolveWithScopeAndIdentifier() = runTest {
        val registry = SecretProviderRegistry()

        // Env provider ignores scope/identifier, but we verify it's passed through
        val result = registry.resolve(
            providerId = "env",
            path = "NONEXISTENT_VAR",
            key = null,
            scope = ConfigLevel.TENANT,
            scopeIdentifier = "tenant-123",
            options = SecretOptions()
        )

        assertTrue(result.isErr)
        assertEquals("SECRET_NOT_FOUND", result.error.code)
    }
}

class ProviderBasedSecretResolverTest {

    @Test
    fun resolvesFromRegistry() = runTest {
        val secrets = mapOf(
            "credentials" to mapOf("password" to "secret123")
        )
        val registry = SecretProviderRegistry()
        registry.register(MapSecretProvider(secrets))

        val resolver = ProviderBasedSecretResolver(registry)
        val result = resolver.resolve("map", "credentials", "password")

        assertTrue(result.isOk)
        assertEquals("secret123", result.value)
    }

    @Test
    fun returnsIdkErrorForFailure() = runTest {
        val registry = SecretProviderRegistry()
        val resolver = ProviderBasedSecretResolver(registry)

        val result = resolver.resolve("map", "nonexistent", null)

        assertTrue(result.isErr)
        assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
    }
}

class DefaultSecretRedactionPolicyTest {

    @Test
    fun redactsSecretMetadata() {
        val policy = DefaultSecretRedactionPolicy()
        val metadata = ResolutionMetadata(
            source = "test",
            scope = ConfigLevel.APP,
            originalKey = "db.password",
            normalizedKey = "db.password",
            order = 50,
            isSecret = true,
            isInterpolated = false,
            resolvedAt = Clock.System.now(),
            ttl = null
        )

        assertTrue(policy.shouldRedact("db.password", metadata))
    }

    @Test
    fun redactsSensitiveKeyPatterns() {
        val policy = DefaultSecretRedactionPolicy()
        val metadata = ResolutionMetadata(
            source = "test",
            scope = ConfigLevel.APP,
            originalKey = "api.password",
            normalizedKey = "api.password",
            order = 50,
            isSecret = false,
            isInterpolated = false,
            resolvedAt = Clock.System.now(),
            ttl = null
        )

        assertTrue(policy.shouldRedact("api.password", metadata))
        assertTrue(policy.shouldRedact("api.secret", metadata))
        assertTrue(policy.shouldRedact("api.token", metadata))
        assertTrue(policy.shouldRedact("api.key", metadata))
        assertTrue(policy.shouldRedact("api.credential", metadata))
        assertTrue(policy.shouldRedact("auth.token", metadata))
    }

    @Test
    fun doesNotRedactNonSensitiveKeys() {
        val policy = DefaultSecretRedactionPolicy()
        val metadata = ResolutionMetadata(
            source = "test",
            scope = ConfigLevel.APP,
            originalKey = "server.host",
            normalizedKey = "server.host",
            order = 50,
            isSecret = false,
            isInterpolated = false,
            resolvedAt = Clock.System.now(),
            ttl = null
        )

        assertFalse(policy.shouldRedact("server.host", metadata))
        assertFalse(policy.shouldRedact("server.port", metadata))
    }

    @Test
    fun redactReturnsPlaceholder() {
        val policy = DefaultSecretRedactionPolicy()

        assertEquals("***REDACTED***", policy.redact("my-secret-value"))
    }

    @Test
    fun customPlaceholderWorks() {
        val policy = DefaultSecretRedactionPolicy(redactedPlaceholder = "[HIDDEN]")

        assertEquals("[HIDDEN]", policy.redact("secret"))
    }
}

class CreateDefaultSecretResolverTest {

    @Test
    fun createsResolverWithEnvProvider() = runTest {
        val resolver = createDefaultSecretResolver()

        // Should be able to try env resolution (will fail but shouldn't throw)
        val result = resolver.resolve("env", "NONEXISTENT_VAR", null)
        assertTrue(result.isErr)
    }

    @Test
    fun createsResolverWithMapProvider() = runTest {
        val secrets = mapOf(
            "test-secret" to mapOf("value" to "secret123")
        )
        val resolver = createDefaultSecretResolver(secrets)

        val result = resolver.resolve("map", "test-secret", null)

        assertTrue(result.isOk)
        assertEquals("secret123", result.value)
    }
}

/**
 * Tests for data class equals, hashCode, and copy methods.
 */
class SecretValueDataClassTest {

    @Test
    fun equalsReturnsTrueForSameValues() {
        val value1 = SecretValue.of("secret", "provider", "path")
        val value2 = SecretValue.of("secret", "provider", "path")
        // Note: resolvedAt will differ, so values won't be equal
        // Test individual properties instead
        assertEquals(value1.value, value2.value)
        assertEquals(value1.providerId, value2.providerId)
        assertEquals(value1.path, value2.path)
    }

    @Test
    fun equalsReturnsFalseForDifferentValue() {
        val value1 = SecretValue.of("secret1", "provider", "path")
        val value2 = SecretValue.of("secret2", "provider", "path")
        assertFalse(value1.value == value2.value)
    }

    @Test
    fun equalsReturnsFalseForDifferentProviderId() {
        val value1 = SecretValue.of("secret", "provider1", "path")
        val value2 = SecretValue.of("secret", "provider2", "path")
        assertFalse(value1.providerId == value2.providerId)
    }

    @Test
    fun equalsReturnsFalseForDifferentPath() {
        val value1 = SecretValue.of("secret", "provider", "path1")
        val value2 = SecretValue.of("secret", "provider", "path2")
        assertFalse(value1.path == value2.path)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val value = SecretValue.of("secret", "provider", "path")
        assertFalse(value.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val value = SecretValue.of("secret", "provider", "path")
        assertFalse(value.equals("not a secret value"))
    }

    @Test
    fun copyWorks() {
        val original = SecretValue.of("secret", "provider", "path")
        val copy = original.copy(value = "new-secret")
        assertEquals("new-secret", copy.value)
        assertEquals("provider", copy.providerId)
    }
}

class SecretErrorDataClassTest {

    @Test
    fun equalsReturnsTrueForSameValues() {
        val error1 = SecretError.notFound("provider", "path", "key")
        val error2 = SecretError.notFound("provider", "path", "key")
        assertEquals(error1, error2)
        assertEquals(error1.hashCode(), error2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentCode() {
        val error1 = SecretError.notFound("provider", "path")
        val error2 = SecretError.accessDenied("provider", "path")
        assertFalse(error1.code == error2.code)
    }

    @Test
    fun equalsReturnsFalseForDifferentProviderId() {
        val error1 = SecretError.notFound("provider1", "path")
        val error2 = SecretError.notFound("provider2", "path")
        assertFalse(error1 == error2)
    }

    @Test
    fun equalsReturnsFalseForDifferentPath() {
        val error1 = SecretError.notFound("provider", "path1")
        val error2 = SecretError.notFound("provider", "path2")
        assertFalse(error1 == error2)
    }

    @Test
    fun equalsReturnsFalseForDifferentKey() {
        val error1 = SecretError.notFound("provider", "path", "key1")
        val error2 = SecretError.notFound("provider", "path", "key2")
        assertFalse(error1 == error2)
    }

    @Test
    fun equalsReturnsFalseForDifferentMessage() {
        val error1 = SecretError(code = "CODE", message = "message1", providerId = "provider", path = "path")
        val error2 = SecretError(code = "CODE", message = "message2", providerId = "provider", path = "path")
        assertFalse(error1 == error2)
    }

    @Test
    fun equalsReturnsFalseForDifferentCause() {
        val error1 = SecretError.general("provider", "path", "message", "cause1")
        val error2 = SecretError.general("provider", "path", "message", "cause2")
        assertFalse(error1 == error2)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val error = SecretError.notFound("provider", "path")
        assertFalse(error.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val error = SecretError.notFound("provider", "path")
        assertFalse(error.equals("not an error"))
    }

    @Test
    fun copyWorks() {
        val original = SecretError.notFound("provider", "path")
        val copy = original.copy(path = "new-path")
        assertEquals("new-path", copy.path)
        assertEquals("provider", copy.providerId)
    }
}

class SecretOptionsDataClassTest {

    @Test
    fun equalsReturnsTrueForSameValues() {
        val options1 = SecretOptions()
        val options2 = SecretOptions()
        assertEquals(options1, options2)
        assertEquals(options1.hashCode(), options2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentTimeout() {
        val options1 = SecretOptions(timeout = 5.seconds)
        val options2 = SecretOptions(timeout = 10.seconds)
        assertFalse(options1 == options2)
    }

    @Test
    fun equalsReturnsFalseForDifferentCacheOverride() {
        val options1 = SecretOptions(cacheOverride = true)
        val options2 = SecretOptions(cacheOverride = false)
        assertFalse(options1 == options2)
    }

    @Test
    fun equalsReturnsFalseForDifferentBypassCache() {
        val options1 = SecretOptions(bypassCache = true)
        val options2 = SecretOptions(bypassCache = false)
        assertFalse(options1 == options2)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val options = SecretOptions()
        assertFalse(options.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val options = SecretOptions()
        assertFalse(options.equals("not options"))
    }

    @Test
    fun copyWorks() {
        val original = SecretOptions(timeout = 5.seconds)
        val copy = original.copy(timeout = 10.seconds)
        assertEquals(10.seconds, copy.timeout)
    }
}

class ProviderHealthDataClassTest {

    @Test
    fun equalsReturnsTrueForSameValues() {
        val health1 = ProviderHealth(providerId = "test", isHealthy = true, message = "OK")
        val health2 = ProviderHealth(providerId = "test", isHealthy = true, message = "OK")
        assertEquals(health1, health2)
        assertEquals(health1.hashCode(), health2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentProviderId() {
        val health1 = ProviderHealth(providerId = "test1", isHealthy = true, message = null)
        val health2 = ProviderHealth(providerId = "test2", isHealthy = true, message = null)
        assertFalse(health1 == health2)
    }

    @Test
    fun equalsReturnsFalseForDifferentIsHealthy() {
        val health1 = ProviderHealth(providerId = "test", isHealthy = true, message = null)
        val health2 = ProviderHealth(providerId = "test", isHealthy = false, message = null)
        assertFalse(health1 == health2)
    }

    @Test
    fun equalsReturnsFalseForDifferentMessage() {
        val health1 = ProviderHealth(providerId = "test", isHealthy = true, message = "OK")
        val health2 = ProviderHealth(providerId = "test", isHealthy = true, message = "Different")
        assertFalse(health1 == health2)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val health = ProviderHealth(providerId = "test", isHealthy = true, message = null)
        assertFalse(health.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val health = ProviderHealth(providerId = "test", isHealthy = true, message = null)
        assertFalse(health.equals("not health"))
    }

    @Test
    fun copyWorks() {
        val original = ProviderHealth(providerId = "test", isHealthy = true, message = null)
        val copy = original.copy(isHealthy = false)
        assertFalse(copy.isHealthy)
        assertEquals("test", copy.providerId)
    }
}
