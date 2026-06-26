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
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Interface for secret providers that resolve sensitive configuration values.
 * Providers can be backed by environment variables, in-memory maps, or cloud services.
 */
@JsExportCompat
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
 * Optional write capability for secret providers.
 *
 * Read-only providers (env, map) intentionally do NOT implement this. Backing stores that
 * support writes (e.g. Vault, Azure Key Vault, AWS Secrets Manager) implement it so the
 * platform can provision/rotate secrets and obtain a canonical reference to store in config.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("WritableSecretProvider", exact = true)
interface WritableSecretProvider : SecretProvider {
    /**
     * Store (or update) a secret value.
     *
     * @param path The secret logical key / path
     * @param key Optional key within the secret (for structured secrets)
     * @param value The secret value to store
     * @param scope The configuration level context
     * @param scopeIdentifier Tenant/principal ID for scope-aware secrets
     * @return The canonical `${secret:<path>[:<key>]}` reference to persist in config, or error
     */
    suspend fun putSecret(
        path: String,
        key: String? = null,
        value: String,
        scope: ConfigLevel = ConfigLevel.APP,
        scopeIdentifier: String? = null,
    ): IdkResult<String, SecretError>
}

/**
 * The outcome of a symmetric secret write performed via [writeSecretSymmetrically].
 *
 * @param reference the canonical `${secret:<logicalKey>[:<key>]}` reference to persist in config. It
 *   is the LOGICAL key form (NOT the physical, sharded backend address), so a later read re-derives
 *   the identical physical address through the SAME [SecretAddressResolver] and finds the value.
 * @param providerType the selected provider type the value was written through.
 * @param physicalAddress the physical, backend-native address the value was actually stored at —
 *   surfaced for diagnostics/tests only; it is never persisted as the reference.
 */
@CoverageExcludedDataClass
data class SecretWriteResult(
    val reference: String,
    val providerType: String,
    val physicalAddress: String,
)

/**
 * Why a symmetric secret write could not be performed (before any backend call).
 */
enum class SecretWriteRejection {
    /** No provider is selected for the tenant (and no app-scope fallback). */
    NO_PROVIDER_SELECTED,

    /** The selected provider type is not registered as a deployment capability. */
    PROVIDER_NOT_REGISTERED,

    /** The selected provider is read-only (env, map, kubernetes-mount) and cannot store secrets. */
    PROVIDER_READ_ONLY,
}

/**
 * Performs a write that is SYMMETRIC with [ProviderBasedSecretResolver]'s read path.
 *
 * The read path computes the physical address via [SecretAddressResolver] from
 * `(logicalKey, providerType, scope, scopeIdentifier, instanceId, strategy)` and then calls
 * [SecretProvider.getSecret] with that already-physical address. Writers MUST do the SAME, otherwise
 * a value written under the logical key is stored at a different physical address than reads look up.
 *
 * This helper centralizes that contract for ALL writers (the IdP-secret backend writer and the
 * `secrets-admin.values.put` command):
 * 1. resolve the selected provider for [scope]/[scopeIdentifier] (cascade: requested scope, then APP),
 * 2. require it is registered (capability) AND a [WritableSecretProvider],
 * 3. compute the physical address with the EXACT same inputs the read path uses (the selection's
 *    partition strategy + instance id, defaulting per provider type),
 * 4. call [WritableSecretProvider.putSecret] with the physical address,
 * 5. return the LOGICAL `${secret:<logicalKey>[:<key>]}` reference (NOT the physical address) so the
 *    persisted reference round-trips back to the identical physical address on read.
 *
 * It is deliberately a LOCAL, in-process resolution: it takes a [SecretProviderRegistry] and a
 * [SecretProviderSelectionResolver] directly — NO `CommandInvoker` is involved.
 *
 * @param logicalKey the logical secret key (e.g. `idp`); the same value the read side interpolates.
 * @return [Ok] with the [SecretWriteResult], or [Err] with the rejection plus optional backend error.
 */
suspend fun writeSecretSymmetrically(
    registry: SecretProviderRegistry,
    selectionResolver: SecretProviderSelectionResolver,
    addressResolver: SecretAddressResolver,
    logicalKey: String,
    key: String?,
    value: String,
    scope: ConfigLevel,
    scopeIdentifier: String?,
): IdkResult<SecretWriteResult, SecretWriteFailure> {
    val selection =
        selectionResolver.selectedProviderSelection(scope, scopeIdentifier)
            ?: selectionResolver.selectedProviderSelection(ConfigLevel.APP, null)
            ?: return Err(SecretWriteFailure(SecretWriteRejection.NO_PROVIDER_SELECTED))

    val provider =
        registry.get(selection.providerType)
            ?: return Err(SecretWriteFailure(SecretWriteRejection.PROVIDER_NOT_REGISTERED, selection.providerType))

    val writable =
        provider as? WritableSecretProvider
            ?: return Err(SecretWriteFailure(SecretWriteRejection.PROVIDER_READ_ONLY, selection.providerType))

    // SAME inputs the read path threads through SecretAddressResolver, so read==write address.
    val physicalAddress =
        addressResolver.physicalAddress(
            logicalKey = logicalKey,
            providerType = selection.providerType,
            scope = scope,
            scopeIdentifier = scopeIdentifier,
            instanceId = selection.instanceId,
            strategy = selection.partitionStrategy ?: PartitionStrategy.defaultFor(selection.providerType),
        )

    val putResult =
        writable.putSecret(
            path = physicalAddress,
            key = key,
            value = value,
            scope = scope,
            scopeIdentifier = scopeIdentifier,
        )

    return if (putResult.isOk) {
        Ok(
            SecretWriteResult(
                // The persisted reference is the LOGICAL key form, not the provider's physical-based echo.
                reference = canonicalSecretReference(logicalKey, key),
                providerType = selection.providerType,
                physicalAddress = physicalAddress,
            ),
        )
    } else {
        // Backend rejected the write: no pre-flight rejection, carry the provider error through.
        Err(SecretWriteFailure(rejection = null, providerType = selection.providerType, backendError = putResult.error))
    }
}

/**
 * A symmetric-write failure: either a pre-flight [rejection] (no/unsuitable provider) or a
 * [backendError] surfaced by the provider's `putSecret`. Exactly one is non-null.
 */
@CoverageExcludedDataClass
data class SecretWriteFailure(
    val rejection: SecretWriteRejection?,
    val providerType: String? = null,
    val backendError: SecretError? = null,
) {
    /** Map this failure onto an [IdkError] for the given [operation] (command id / SPI op name). */
    fun toIdkError(operation: String): IdkError {
        backendError?.let { return it.toIdkError() }
        val reason =
            when (rejection) {
                SecretWriteRejection.NO_PROVIDER_SELECTED -> {
                    "no secret provider selected"
                }

                SecretWriteRejection.PROVIDER_NOT_REGISTERED -> {
                    "selected secret provider '${providerType ?: "?"}' is not registered as a deployment capability"
                }

                SecretWriteRejection.PROVIDER_READ_ONLY -> {
                    "secret provider '${providerType ?: "?"}' is read-only and cannot store secrets"
                }

                null -> {
                    "secret write failed"
                }
            }
        return IdkError.UNSUPPORTED_OPERATION_ERROR(operation = operation, reason = reason)
    }
}

/** Build the canonical `${secret:<logicalKey>[:<key>]}` reference for the LOGICAL key. */
internal fun canonicalSecretReference(
    logicalKey: String,
    key: String?,
): String = if (key != null) "\${secret:$logicalKey:$key}" else "\${secret:$logicalKey}"

/**
 * SPI that resolves which secret provider is selected for a given configuration scope.
 *
 * The cascade form (`${secret:<key>}`) consults this resolver to learn, for the requesting
 * tenant/principal/app, which concrete provider id should serve the secret. Returning `null`
 * means "no selection at this scope" and the cascade falls through to the next scope (and
 * ultimately the env floor).
 *
 * The IDK ships an env-only default ([EnvOnlySecretProviderSelectionResolver]). EDK/VDX
 * contribute scope-aware implementations backed by per-tenant configuration.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("SecretProviderSelectionResolver", exact = true)
interface SecretProviderSelectionResolver {
    /**
     * @param scope The scope whose selected provider is requested
     * @param scopeIdentifier The tenant/principal id for the scope (may be null)
     * @return The selected provider id, or null if no provider is selected at this scope
     */
    suspend fun selectedProvider(
        scope: ConfigLevel,
        scopeIdentifier: String?,
    ): String?

    /**
     * Richer selection that surfaces the selected provider type AND the partition/sharding hints
     * (partition strategy + instance id) the scope's secret backend descriptor pins. The cascade
     * resolver consults this to compute the physical secret address with the EXACT partition strategy
     * and instance id the writer used, preserving read/write address symmetry — rather than falling
     * back to [PartitionStrategy.defaultFor] and a null instance id.
     *
     * Defaulted to wrap [selectedProvider] (no partition hints) so every JVM/JS/wasm implementor keeps
     * compiling without change; config-backed EDK/VDX resolvers override it to read
     * `secrets.provider.<type>.partitionStrategy` / `.instanceId`.
     *
     * @return the selection, or null when no provider is selected at [scope].
     */
    suspend fun selectedProviderSelection(
        scope: ConfigLevel,
        scopeIdentifier: String?,
    ): SecretProviderSelection? = selectedProvider(scope, scopeIdentifier)?.let { SecretProviderSelection(providerType = it) }
}

/**
 * The selected secret provider for a scope plus the partition hints its backend descriptor pins.
 *
 * @param providerType the selected canonical provider type id (`vault`, `azure`, `aws`,
 *   `kubernetes-mount`, `env`).
 * @param partitionStrategy the partition strategy the descriptor pins; null falls back to
 *   [PartitionStrategy.defaultFor] of [providerType].
 * @param instanceId the logical instance id partitioning secrets within a tenant; null when
 *   tenant-wide.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("SecretProviderSelection", exact = true)
@CoverageExcludedDataClass
data class SecretProviderSelection(
    val providerType: String,
    val partitionStrategy: PartitionStrategy? = null,
    val instanceId: String? = null,
)

/**
 * IDK default selection resolver: no per-scope selection, so cascade resolution falls back to
 * the environment-variable floor. EDK/VDX replace this with config-backed implementations.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("EnvOnlySecretProviderSelectionResolver", exact = true)
class EnvOnlySecretProviderSelectionResolver : SecretProviderSelectionResolver {
    override suspend fun selectedProvider(
        scope: ConfigLevel,
        scopeIdentifier: String?,
    ): String? = null
}

/**
 * Options for secret resolution.
 */
@JsExportCompat
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
@JsExportCompat
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

    /**
     * Redacts the resolved secret so an accidental log/`$secretValue`
     * interpolation or exception message never leaks the credential. Only the
     * non-sensitive metadata is rendered; the serializer is intentionally left
     * untouched because the value must still travel from provider to resolver.
     */
    override fun toString(): String =
        "SecretValue(value=***, providerId=$providerId, path=$path, key=$key, " +
            "resolvedAt=$resolvedAt, expiresAt=$expiresAt, version=$version)"

    companion object {
        @JvmStatic
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
@JsExportCompat
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
        @JvmStatic
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

        @JvmStatic
        fun providerUnavailable(providerId: String) =
            SecretError(
                code = "PROVIDER_UNAVAILABLE",
                message = "Secret provider '$providerId' is not available",
                providerId = providerId,
                path = "",
            )

        @JvmStatic
        fun accessDenied(
            providerId: String,
            path: String,
        ) = SecretError(
            code = "ACCESS_DENIED",
            message = "Access denied to secret: $path",
            providerId = providerId,
            path = path,
        )

        @JvmStatic
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

        @JvmStatic
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
@JsExportCompat
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
@JsExportCompat
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
@JsExportCompat
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
@JsExportCompat
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
@JsExportCompat
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
@JsExportCompat
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
 *
 * Resolution modes:
 * - **Pinned** (`provider != null`): resolves using exactly that provider id.
 * - **Cascade** (`provider == null`): consults the [selectionResolver] for the TENANT scope
 *   first, then the APP scope, and finally falls back to the `env` floor. The first provider
 *   that successfully returns the secret wins.
 *
 * ## Central sharding
 *
 * This resolver owns the logical->physical mapping for EVERY provider, including the env floor.
 * Before dispatching to a provider it computes the physical address via [addressResolver] using the
 * RESOLVED provider type (the pinned provider id, or each cascade candidate id) and that type's
 * default [PartitionStrategy], then passes the already-physical address as the provider's `path`.
 * Providers therefore never re-shard, and the env floor — which cannot inject an EDK resolver — is
 * sharded identically to every cloud backend.
 *
 * @param registry The provider registry to resolve against
 * @param selectionResolver SPI selecting the per-scope provider for cascade resolution.
 *   Defaults to the env-only floor ([EnvOnlySecretProviderSelectionResolver]).
 * @param addressResolver Maps the logical key onto the physical, backend-native address.
 *   Defaults to [DefaultSecretAddressResolver].
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProviderBasedSecretResolver", exact = true)
class ProviderBasedSecretResolver(
    private val registry: SecretProviderRegistry,
    private val selectionResolver: SecretProviderSelectionResolver = EnvOnlySecretProviderSelectionResolver(),
    private val addressResolver: SecretAddressResolver = DefaultSecretAddressResolver(),
) : SecretResolver {
    override suspend fun resolve(
        provider: String?,
        path: String,
        key: String?,
    ): IdkResult<String, IdkError> = resolve(provider, path, key, null, null)

    override suspend fun resolve(
        provider: String?,
        path: String,
        key: String?,
        scope: ConfigLevel?,
        scopeIdentifier: String?,
    ): IdkResult<String, IdkError> {
        val effectiveScope = scope ?: ConfigLevel.APP

        // Pinned: resolve via exactly the requested provider. The physical address is computed for
        // that provider type before dispatch (the pinned form carries no descriptor partition hints).
        if (provider != null) {
            val physicalPath = physicalAddressFor(provider, path, effectiveScope, scopeIdentifier, null, null)
            return registry
                .resolve(
                    providerId = provider,
                    path = physicalPath,
                    key = key,
                    scope = effectiveScope,
                    scopeIdentifier = scopeIdentifier,
                ).toIdkStringResult()
        }

        // Cascade: tenant-selected -> app-selected -> env floor.
        // TODO(phase2): fail fast on a non-NOT_FOUND error (ACCESS_DENIED / PROVIDER_UNAVAILABLE)
        // from a candidate rather than silently falling through to a lower scope, which could mask a
        // security-relevant failure.
        //
        // Each candidate's partition strategy + instance id come from the scope's selection (the
        // backend descriptor pins them), so the physical address is computed with the SAME inputs the
        // writer used — preserving read/write symmetry. The env floor has no descriptor selection and
        // falls back to per-type defaults.
        val candidates = LinkedHashMap<String, SecretProviderSelection?>()

        fun addCandidate(
            providerType: String,
            selection: SecretProviderSelection?
        ) {
            if (!candidates.containsKey(providerType)) candidates[providerType] = selection
        }
        selectionResolver
            .selectedProviderSelection(ConfigLevel.TENANT, scopeIdentifier)
            ?.let { addCandidate(it.providerType, it) }
        selectionResolver
            .selectedProviderSelection(ConfigLevel.APP, null)
            ?.let { addCandidate(it.providerType, it) }
        addCandidate(ENV_PROVIDER_ID, null)

        var lastError: SecretError? = null
        for ((candidate, selection) in candidates) {
            // Each candidate may be a different backend type, so the physical address is computed
            // per-candidate (the env floor included), threading the descriptor partition hints.
            val physicalPath =
                physicalAddressFor(
                    providerType = candidate,
                    logicalKey = path,
                    scope = effectiveScope,
                    scopeIdentifier = scopeIdentifier,
                    strategy = selection?.partitionStrategy,
                    instanceId = selection?.instanceId,
                )
            val result =
                registry.resolve(
                    providerId = candidate,
                    path = physicalPath,
                    key = key,
                    scope = effectiveScope,
                    scopeIdentifier = scopeIdentifier,
                )
            if (result.isOk) {
                return Ok(result.value.value)
            }
            lastError = result.error
        }

        return Err(
            (lastError ?: SecretError.notFound(ENV_PROVIDER_ID, path, key)).toIdkError(),
        )
    }

    /**
     * Compute the physical, backend-native address for [logicalKey] as served by [providerType].
     *
     * When the scope's selection pins a [strategy] and/or [instanceId] (from its
     * [SecretsBackendDescriptor]-equivalent config) they are threaded through verbatim so a read
     * resolves to the SAME physical address the writer used. A null [strategy] falls back to that
     * provider type's default ([PartitionStrategy.defaultFor]); a null [instanceId] means tenant-wide.
     * App scope / null identifier requests are not sharded (handled inside [addressResolver]).
     */
    private fun physicalAddressFor(
        providerType: String,
        logicalKey: String,
        scope: ConfigLevel,
        scopeIdentifier: String?,
        strategy: PartitionStrategy?,
        instanceId: String?,
    ): String =
        addressResolver.physicalAddress(
            logicalKey = logicalKey,
            providerType = providerType,
            scope = scope,
            scopeIdentifier = scopeIdentifier,
            instanceId = instanceId,
            strategy = strategy ?: PartitionStrategy.defaultFor(providerType),
        )

    private fun IdkResult<SecretValue, SecretError>.toIdkStringResult(): IdkResult<String, IdkError> =
        if (isOk) {
            Ok(value.value)
        } else {
            Err(error.toIdkError())
        }

    companion object {
        private const val ENV_PROVIDER_ID = "env"
    }
}

/**
 * Create a secret resolver from a registry.
 *
 * @param selectionResolver SPI selecting the per-scope provider for cascade resolution;
 *   defaults to the env-only floor.
 * @param addressResolver Maps the logical key onto the physical address; defaults to
 *   [DefaultSecretAddressResolver].
 */
fun SecretProviderRegistry.toSecretResolver(
    selectionResolver: SecretProviderSelectionResolver = EnvOnlySecretProviderSelectionResolver(),
    addressResolver: SecretAddressResolver = DefaultSecretAddressResolver(),
): SecretResolver = ProviderBasedSecretResolver(this, selectionResolver, addressResolver)

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
