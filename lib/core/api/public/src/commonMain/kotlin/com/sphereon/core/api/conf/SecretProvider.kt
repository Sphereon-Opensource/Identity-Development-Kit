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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Interface for secret providers that resolve sensitive configuration values.
 * Providers can be backed by environment variables, in-memory maps, or cloud services.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SecretProvider", exact = true)
interface SecretProvider {
    /**
     * Unique identifier for this provider (e.g., "env", "vault", "azure").
     */
    val providerId: String

    /**
     * Whether this provider is available and operational.
     */
    val isAvailable: Boolean

    /**
     * Get a secret value.
     *
     * @param path The secret path (interpretation depends on provider)
     * @param key Optional key within the secret (for structured secrets)
     * @param scope The configuration level context
     * @param scopeIdentifier Tenant/principal ID for scope-aware secrets
     * @param options Resolution options
     * @return The secret value with metadata, or error if not found
     */
    suspend fun getSecret(
        path: String,
        key: String? = null,
        scope: ConfigLevel = ConfigLevel.APP,
        scopeIdentifier: String? = null,
        options: SecretOptions = SecretOptions(),
    ): IdkResult<SecretValue, SecretError>

    /**
     * Invalidate cached secrets for a path or all secrets.
     *
     * @param path Optional path to invalidate; null invalidates all
     */
    suspend fun invalidateCache(path: String? = null)

    /**
     * Check provider health.
     *
     * @return Health status with optional error details
     */
    suspend fun healthCheck(): IdkResult<ProviderHealth, SecretError>
}

/**
 * Options for secret resolution.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("SecretOptions", exact = true)
@CoverageExcludedDataClass
data class SecretOptions(
    val timeout: Duration = 5.seconds,
    val cacheOverride: Boolean = false,
    val bypassCache: Boolean = false,
)

/**
 * A resolved secret value with metadata.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("SecretValue", exact = true)
@CoverageExcludedDataClass
data class SecretValue(
    val value: String,
    val providerId: String,
    val path: String,
    val key: String?,
    val resolvedAt: Instant,
    val expiresAt: Instant?,
    val version: String? = null,
) {
    /**
     * Check if this secret has expired.
     */
    fun isExpired(): Boolean = expiresAt != null && Clock.System.now() > expiresAt

    companion object {
        fun of(
            value: String,
            providerId: String,
            path: String,
            key: String? = null,
            ttl: Duration? = null,
            version: String? = null,
        ): SecretValue {
            val now = Clock.System.now()
            return SecretValue(
                value = value,
                providerId = providerId,
                path = path,
                key = key,
                resolvedAt = now,
                expiresAt = ttl?.let { now + it },
                version = version,
            )
        }
    }
}

/**
 * Error type for secret resolution failures.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("SecretError", exact = true)
@CoverageExcludedDataClass
data class SecretError(
    val code: String,
    val message: String,
    val providerId: String,
    val path: String,
    val key: String? = null,
    val cause: String? = null,
) {
    fun toIdkError(): IdkError =
        IdkError.ILLEGAL_ARGUMENT_ERROR(
            message = "[$code] $message${cause?.let { " (cause: $it)" } ?: ""}",
        )

    companion object {
        fun notFound(
            providerId: String,
            path: String,
            key: String? = null,
        ) = SecretError(
            code = "SECRET_NOT_FOUND",
            message = "Secret not found: $path${key?.let { "/$it" } ?: ""}",
            providerId = providerId,
            path = path,
            key = key,
        )

        fun providerUnavailable(providerId: String) =
            SecretError(
                code = "PROVIDER_UNAVAILABLE",
                message = "Secret provider '$providerId' is not available",
                providerId = providerId,
                path = "",
            )

        fun accessDenied(
            providerId: String,
            path: String,
        ) = SecretError(
            code = "ACCESS_DENIED",
            message = "Access denied to secret: $path",
            providerId = providerId,
            path = path,
        )

        fun timeout(
            providerId: String,
            path: String,
            timeout: Duration,
        ) = SecretError(
            code = "TIMEOUT",
            message = "Secret resolution timed out after $timeout for: $path",
            providerId = providerId,
            path = path,
        )

        fun general(
            providerId: String,
            path: String,
            message: String,
            cause: String? = null,
        ) = SecretError(
            code = "SECRET_ERROR",
            message = message,
            providerId = providerId,
            path = path,
            cause = cause,
        )
    }
}

/**
 * Provider health status.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProviderHealth", exact = true)
@CoverageExcludedDataClass
data class ProviderHealth(
    val providerId: String,
    val isHealthy: Boolean,
    val message: String? = null,
    val checkedAt: Instant = Clock.System.now(),
)

/**
 * Policy for redacting secrets in logs and diagnostics.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SecretRedactionPolicy", exact = true)
interface SecretRedactionPolicy {
    /**
     * Check if a value should be redacted based on key and metadata.
     */
    fun shouldRedact(
        key: String,
        metadata: ResolutionMetadata,
    ): Boolean

    /**
     * Redact a secret value.
     */
    fun redact(value: String): String
}

/**
 * Default redaction policy that redacts all secrets.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultSecretRedactionPolicy", exact = true)
class DefaultSecretRedactionPolicy(
    private val redactedPlaceholder: String = "***REDACTED***",
    private val sensitiveKeyPatterns: List<Regex> =
        listOf(
            Regex(".*password.*", RegexOption.IGNORE_CASE),
            Regex(".*secret.*", RegexOption.IGNORE_CASE),
            Regex(".*token.*", RegexOption.IGNORE_CASE),
            Regex(".*key.*", RegexOption.IGNORE_CASE),
            Regex(".*credential.*", RegexOption.IGNORE_CASE),
            Regex(".*auth.*", RegexOption.IGNORE_CASE),
        ),
) : SecretRedactionPolicy {
    override fun shouldRedact(
        key: String,
        metadata: ResolutionMetadata,
    ): Boolean {
        if (metadata.isSecret) {
            return true
        }
        return sensitiveKeyPatterns.any { it.matches(key) }
    }

    override fun redact(value: String): String = redactedPlaceholder
}

/**
 * Environment variable secret provider.
 * Resolves secrets from environment variables.
 * Path is interpreted as the environment variable name.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("EnvSecretProvider", exact = true)
class EnvSecretProvider : SecretProvider {
    override val providerId: String = "env"

    override val isAvailable: Boolean = true

    private val keyNormalizer = PropertyKeyNormalizerImpl.Default

    override suspend fun getSecret(
        path: String,
        key: String?,
        scope: ConfigLevel,
        scopeIdentifier: String?,
        options: SecretOptions,
    ): IdkResult<SecretValue, SecretError> {
        // Env vars are flat strings — always resolve by path.
        // key is only meaningful for structured providers (Vault, AWS, Azure)
        // where it extracts a field from a JSON/map secret value.
        val value = resolveEnvVar(path)

        return if (value != null) {
            Ok(
                SecretValue.of(
                    value = value,
                    providerId = providerId,
                    path = path,
                    key = key,
                ),
            )
        } else {
            Err(SecretError.notFound(providerId, path, key))
        }
    }

    /**
     * Env var resolution contract:
     * 1. Exact match (DB_PASSWORD when path="DB_PASSWORD")
     * 2. Uppercase-underscore transform (db.password -> DB_PASSWORD)
     * 3. Normalized scan against all env vars (handles any delimiter mismatch)
     */
    private fun resolveEnvVar(path: String): String? {
        // 1. Exact match
        Env.get(path)?.let { return it }

        // 2. Uppercase with underscores
        val upper = path.uppercase().replace('.', '_').replace('-', '_')
        if (upper != path) {
            Env.get(upper)?.let { return it }
        }

        // 3. Normalized scan
        val normalizedTarget = keyNormalizer.normalize(path.lowercase())
        return Env
            .getAll()
            .entries
            .firstOrNull {
                keyNormalizer.normalize(it.key.lowercase()) == normalizedTarget
            }?.value
    }

    override suspend fun invalidateCache(path: String?) {
        // Env provider has no cache
    }

    override suspend fun healthCheck(): IdkResult<ProviderHealth, SecretError> =
        Ok(
            ProviderHealth(
                providerId = providerId,
                isHealthy = true,
                message = "Environment variable provider is always available",
            ),
        )
}

/**
 * In-memory map secret provider.
 * Useful for testing and local development.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MapSecretProvider", exact = true)
class MapSecretProvider(
    private val secrets: Map<String, Map<String, String>> = emptyMap(),
) : SecretProvider {
    override val providerId: String = "map"

    override val isAvailable: Boolean = true

    override suspend fun getSecret(
        path: String,
        key: String?,
        scope: ConfigLevel,
        scopeIdentifier: String?,
        options: SecretOptions,
    ): IdkResult<SecretValue, SecretError> {
        val secretMap =
            secrets[path]
                ?: return Err(SecretError.notFound(providerId, path, key))

        // If key is specified, get that specific key; otherwise try to get a value named "value"
        val secretKey = key ?: "value"
        val value =
            secretMap[secretKey]
                ?: return Err(SecretError.notFound(providerId, path, key))

        return Ok(
            SecretValue.of(
                value = value,
                providerId = providerId,
                path = path,
                key = key,
            ),
        )
    }

    override suspend fun invalidateCache(path: String?) {
        // Map provider has no cache
    }

    override suspend fun healthCheck(): IdkResult<ProviderHealth, SecretError> =
        Ok(
            ProviderHealth(
                providerId = providerId,
                isHealthy = true,
                message = "Map provider with ${secrets.size} secret entries",
            ),
        )
}

/**
 * Registry for managing multiple secret providers.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SecretProviderRegistry", exact = true)
class SecretProviderRegistry(
    private val providers: MutableMap<String, SecretProvider> = mutableMapOf(),
    private val defaultProvider: String? = "env",
) {
    init {
        // Register default IDK providers
        register(EnvSecretProvider())
    }

    /**
     * Register a secret provider.
     */
    fun register(provider: SecretProvider) {
        providers[provider.providerId] = provider
    }

    /**
     * Get a provider by ID.
     */
    fun get(providerId: String): SecretProvider? = providers[providerId]

    /**
     * Get the default provider.
     */
    fun getDefault(): SecretProvider? = defaultProvider?.let { providers[it] }

    /**
     * Get all registered providers.
     */
    fun getAll(): Collection<SecretProvider> = providers.values

    /**
     * Check if a provider is registered.
     */
    fun hasProvider(providerId: String): Boolean = providers.containsKey(providerId)

    /**
     * Resolve a secret using the specified or default provider.
     */
    suspend fun resolve(
        providerId: String?,
        path: String,
        key: String?,
        scope: ConfigLevel = ConfigLevel.APP,
        scopeIdentifier: String? = null,
        options: SecretOptions = SecretOptions(),
    ): IdkResult<SecretValue, SecretError> {
        val provider =
            if (providerId != null) {
                providers[providerId]
                    ?: return Err(SecretError.providerUnavailable(providerId))
            } else {
                getDefault()
                    ?: return Err(SecretError.providerUnavailable("default"))
            }

        if (!provider.isAvailable) {
            return Err(SecretError.providerUnavailable(provider.providerId))
        }

        return provider.getSecret(path, key, scope, scopeIdentifier, options)
    }
}

/**
 * Secret resolver that uses the provider registry.
 * This is the IDK implementation used by the interpolation system.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProviderBasedSecretResolver", exact = true)
class ProviderBasedSecretResolver(
    private val registry: SecretProviderRegistry,
) : SecretResolver {
    override suspend fun resolve(
        provider: String,
        path: String,
        key: String?,
    ): IdkResult<String, IdkError> = resolve(provider, path, key, null, null)

    override suspend fun resolve(
        provider: String,
        path: String,
        key: String?,
        scope: ConfigLevel?,
        scopeIdentifier: String?,
    ): IdkResult<String, IdkError> {
        val result =
            registry.resolve(
                providerId = provider,
                path = path,
                key = key,
                scope = scope ?: ConfigLevel.APP,
                scopeIdentifier = scopeIdentifier,
            )

        return if (result.isOk) {
            Ok(result.value.value)
        } else {
            Err(result.error.toIdkError())
        }
    }
}

/**
 * Create a secret resolver from a registry.
 */
fun SecretProviderRegistry.toSecretResolver(): SecretResolver = ProviderBasedSecretResolver(this)

/**
 * Create a secret resolver with default providers.
 */
fun createDefaultSecretResolver(additionalSecrets: Map<String, Map<String, String>> = emptyMap()): SecretResolver {
    val registry = SecretProviderRegistry()
    if (additionalSecrets.isNotEmpty()) {
        registry.register(MapSecretProvider(additionalSecrets))
    }
    return registry.toSecretResolver()
}
