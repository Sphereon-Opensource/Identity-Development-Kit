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
import com.sphereon.core.api.error.IdkError
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * Core configuration resolution pipeline interface.
 * Provides unified property resolution with metadata for diagnostics.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ConfigResolutionPipeline", exact = true)
interface ConfigResolutionPipeline {
    /**
     * Resolve a single property value with full metadata.
     *
     * @param key The property key to resolve
     * @param targetType The expected type of the value
     * @param context Resolution context with scope and options
     * @return The resolved value with metadata, or error if not found/conversion fails
     */
    suspend fun <T : Any> resolve(
        key: String,
        targetType: KClass<T>,
        context: ResolutionContext
    ): IdkResult<ResolvedValue<T>, IdkError>

    /**
     * Resolve all properties matching a prefix.
     *
     * @param prefix The key prefix to match
     * @param context Resolution context with scope and options
     * @return Map of resolved values with metadata
     */
    suspend fun resolveAll(
        prefix: String,
        context: ResolutionContext
    ): IdkResult<Map<String, ResolvedValue<Any>>, IdkError>

    /**
     * Resolve all properties matching a prefix and return as strings with optional redaction.
     *
     * @param prefix The key prefix to match
     * @param context Resolution context with scope and options
     * @param redact Whether to redact sensitive values (defaults to true)
     * @param redactionPolicy The policy to use for redaction
     * @return Map of property keys to string values (with sensitive values redacted if enabled)
     */
    suspend fun resolveAllAsString(
        prefix: String,
        context: ResolutionContext,
        redact: Boolean = true,
        redactionPolicy: SecretRedactionPolicy = DefaultSecretRedactionPolicy()
    ): IdkResult<Map<String, String>, IdkError>

    /**
     * Check if a property exists at any scope level.
     *
     * @param key The property key to check
     * @param context Resolution context with scope information
     * @return True if the property exists
     */
    suspend fun containsProperty(key: String, context: ResolutionContext): Boolean

    /**
     * Invalidate cached resolution for a key or prefix.
     *
     * @param keyOrPrefix The key or prefix to invalidate
     * @param context Resolution context for scope-aware invalidation
     */
    suspend fun invalidate(keyOrPrefix: String, context: ResolutionContext)
}

/**
 * Reified extension for type-safe property resolution.
 */
suspend inline fun <reified T : Any> ConfigResolutionPipeline.resolve(
    key: String,
    context: ResolutionContext
): IdkResult<ResolvedValue<T>, IdkError> = resolve(key, T::class, context)

/**
 * Context for property resolution operations.
 * Contains scope information and resolution options.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolutionContext", exact = true)
@CoverageExcludedDataClass
data class ResolutionContext(
    val level: ConfigLevel = ConfigLevel.APP,
    val tenantId: String? = null,
    val principalId: String? = null,
    val sessionId: String? = null,
    val activeProfiles: List<String> = listOf("default"),
    val options: ResolutionOptions = ResolutionOptions()
) {
    companion object {
        fun app(profiles: List<String> = listOf("default")) = ResolutionContext(
            level = ConfigLevel.APP,
            activeProfiles = profiles
        )

        fun tenant(tenantId: String, profiles: List<String> = listOf("default")) = ResolutionContext(
            level = ConfigLevel.TENANT,
            tenantId = tenantId,
            activeProfiles = profiles
        )

        fun principal(
            tenantId: String,
            principalId: String,
            profiles: List<String> = listOf("default")
        ) = ResolutionContext(
            level = ConfigLevel.PRINCIPAL,
            tenantId = tenantId,
            principalId = principalId,
            activeProfiles = profiles
        )
    }
}

/**
 * Options controlling resolution behavior.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolutionOptions", exact = true)
@CoverageExcludedDataClass
data class ResolutionOptions(
    val useCache: Boolean = true,
    val interpolate: Boolean = true,
    val resolveSecrets: Boolean = true,
    val includeMetadata: Boolean = true,
    val maxInterpolationDepth: Int = 10,
    val profile: String? = null,
    val additionalOptions: Map<String, String> = emptyMap()
)

/**
 * A resolved configuration value with full metadata.
 *
 * @property value The resolved value
 * @property metadata Resolution metadata for diagnostics
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolvedValue", exact = true)
@CoverageExcludedDataClass
data class ResolvedValue<T>(
    val value: T,
    val metadata: ResolutionMetadata
) {
    companion object {
        fun <T> of(
            value: T,
            source: String,
            scope: ConfigLevel,
            originalKey: String,
            normalizedKey: String = originalKey,
            order: Int = 50,
            isSecret: Boolean = false,
            isInterpolated: Boolean = false,
            ttl: Duration? = null
        ) = ResolvedValue(
            value = value,
            metadata = ResolutionMetadata(
                source = source,
                scope = scope,
                originalKey = originalKey,
                normalizedKey = normalizedKey,
                order = order,
                isSecret = isSecret,
                isInterpolated = isInterpolated,
                resolvedAt = Clock.System.now(),
                ttl = ttl
            )
        )
    }
}

/**
 * Metadata about how a property value was resolved.
 * Useful for debugging and diagnostics.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolutionMetadata", exact = true)
@CoverageExcludedDataClass
data class ResolutionMetadata(
    val source: String,
    val scope: ConfigLevel,
    val originalKey: String,
    val normalizedKey: String,
    val order: Int,
    val isSecret: Boolean,
    val isInterpolated: Boolean,
    val resolvedAt: Instant,
    val ttl: Duration?
)

/**
 * Default implementation of ConfigResolutionPipeline using PropertySources.
 *
 * @param propertySources The property sources to resolve from
 * @param interpolator Optional interpolator for variable substitution
 * @param keyNormalizer Key normalizer for consistent key lookups
 * @param resolverLevel The scope level of this pipeline, used for protection enforcement
 * @param snapshotCache Optional cache for prefix-based query results
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultConfigResolutionPipeline", exact = true)
class DefaultConfigResolutionPipeline(
    private val propertySources: PropertySources,
    private val interpolator: PropertyInterpolator? = null,
    private val keyNormalizer: PropertyKeyNormalizer = PropertyKeyNormalizerImpl(),
    private val resolverLevel: ConfigLevel = ConfigLevel.APP,
    private val snapshotCache: SyncConfigSnapshotCache? = null
) : ConfigResolutionPipeline {

    override suspend fun <T : Any> resolve(
        key: String,
        targetType: KClass<T>,
        context: ResolutionContext
    ): IdkResult<ResolvedValue<T>, IdkError> {
        val normalizedKey = keyNormalizer.normalize(key)

        for (source in orderedSourcesByScope(propertySources)) {
            if (!source.isPlatformSupported) continue

            val rawValue = source.getProperty(normalizedKey, targetType)
            if (rawValue != null) {
                val finalValue = if (context.options.interpolate && interpolator != null && rawValue is String) {
                    // Use scope-aware interpolation with full options support
                    val interpolated = interpolator.interpolate(
                        value = rawValue,
                        resolver = createPropertyResolver(),
                        requestingScope = context.level,
                        maxDepth = context.options.maxInterpolationDepth,
                        resolveSecrets = context.options.resolveSecrets
                    )
                    if (interpolated.isErr) {
                        return Err(interpolated.error)
                    }
                    @Suppress("UNCHECKED_CAST")
                    interpolated.value as T
                } else {
                    rawValue
                }

                val isSecret = interpolator?.isSecretReference(rawValue.toString()) ?: false
                val isInterpolated = interpolator?.containsPlaceholders(rawValue.toString()) ?: false

                return Ok(
                    ResolvedValue(
                        value = finalValue,
                        metadata = ResolutionMetadata(
                            source = source.getName(),
                            scope = context.level,
                            originalKey = key,
                            normalizedKey = normalizedKey,
                            order = source.getOrder(),
                            isSecret = isSecret,
                            isInterpolated = isInterpolated,
                            resolvedAt = Clock.System.now(),
                            ttl = null
                        )
                    )
                )
            }
        }

        return Err(
            IdkError.NOT_FOUND_ERROR(
                resource = "config property",
                message = "Property not found: $key"
            )
        )
    }

    override suspend fun resolveAll(
        prefix: String,
        context: ResolutionContext
    ): IdkResult<Map<String, ResolvedValue<Any>>, IdkError> {
        val normalizedPrefix = keyNormalizer.normalize(prefix)
        val result = mutableMapOf<String, ResolvedValue<Any>>()

        for (source in orderedSourcesByScope(propertySources)) {
            if (!source.isPlatformSupported) continue

            for (propertyName in source.getAllPropertyNames()) {
                // Check prefix matching with dot boundary to avoid partial matches
                // (e.g., "app" should not match "application.name")
                if (normalizedPrefix.isNotEmpty() &&
                    !propertyName.startsWith("$normalizedPrefix.") &&
                    propertyName != normalizedPrefix
                ) continue
                if (result.containsKey(propertyName)) continue

                val value = source.getProperty(propertyName, Any::class) ?: continue

                val finalValue = if (context.options.interpolate && interpolator != null && value is String) {
                    // Use scope-aware interpolation with full options support
                    val interpolated = interpolator.interpolate(
                        value = value,
                        resolver = createPropertyResolver(),
                        requestingScope = context.level,
                        maxDepth = context.options.maxInterpolationDepth,
                        resolveSecrets = context.options.resolveSecrets
                    )
                    if (interpolated.isErr) continue
                    interpolated.value
                } else {
                    value
                }

                val isSecret = interpolator?.isSecretReference(value.toString()) ?: false
                val isInterpolated = interpolator?.containsPlaceholders(value.toString()) ?: false

                result[propertyName] = ResolvedValue(
                    value = finalValue,
                    metadata = ResolutionMetadata(
                        source = source.getName(),
                        scope = context.level,
                        originalKey = propertyName,
                        normalizedKey = propertyName,
                        order = source.getOrder(),
                        isSecret = isSecret,
                        isInterpolated = isInterpolated,
                        resolvedAt = Clock.System.now(),
                        ttl = null
                    )
                )
            }
        }

        return Ok(result)
    }

    override suspend fun resolveAllAsString(
        prefix: String,
        context: ResolutionContext,
        redact: Boolean,
        redactionPolicy: SecretRedactionPolicy
    ): IdkResult<Map<String, String>, IdkError> {
        val resolveResult = resolveAll(prefix, context)
        if (resolveResult.isErr) {
            return Err(resolveResult.error)
        }

        val stringMap = resolveResult.value.mapValues { (key, resolved) ->
            val value = resolved.value
            if (value == null) {
                "null"
            } else if (redact && redactionPolicy.shouldRedact(key, resolved.metadata)) {
                redactionPolicy.redact(value.toString())
            } else {
                value.toString()
            }
        }

        return Ok(stringMap)
    }

    override suspend fun containsProperty(key: String, context: ResolutionContext): Boolean {
        val normalizedKey = keyNormalizer.normalize(key)
        return orderedSourcesByScope(propertySources).any { source ->
            source.isPlatformSupported && source.hasProperty(normalizedKey)
        }
    }

    override suspend fun invalidate(keyOrPrefix: String, context: ResolutionContext) {
        // Invalidate cached entries if cache is available
        snapshotCache?.invalidateByPrefix(keyOrPrefix)
    }

    /**
     * Create a property resolver for interpolation.
     *
     * Uses ProtectedPropertySourcesResolver to enforce FINAL/PROTECTED protection
     * during interpolation, preventing lower scopes from accessing protected values.
     */
    private fun createPropertyResolver(): PropertyResolver {
        return ProtectedPropertySourcesResolver(propertySources, resolverLevel)
    }
}

/**
 * Configuration error definitions for the resolution pipeline.
 */
object ConfigErrors {
    fun propertyNotFound(key: String) = IdkError.NOT_FOUND_ERROR(
        resource = "config property",
        message = "Property not found: $key"
    )

    fun interpolationError(key: String, reason: String) = IdkError.ILLEGAL_ARGUMENT_ERROR(
        message = "Interpolation failed for property '$key': $reason"
    )

    fun circularReference(key: String, chain: List<String>) = IdkError.ILLEGAL_ARGUMENT_ERROR(
        message = "Circular reference detected for property '$key': ${chain.joinToString(" -> ")}"
    )

    fun maxDepthExceeded(key: String, depth: Int) = IdkError.ILLEGAL_ARGUMENT_ERROR(
        message = "Max interpolation depth ($depth) exceeded for property '$key'"
    )

    fun secretResolutionFailed(key: String, provider: String, reason: String) = IdkError.ILLEGAL_ARGUMENT_ERROR(
        message = "Secret resolution failed for '$key' using provider '$provider': $reason"
    )

    fun conversionError(key: String, fromType: String, toType: String) = IdkError.ILLEGAL_ARGUMENT_ERROR(
        message = "Cannot convert property '$key' from $fromType to $toType"
    )

    fun bindError(
        prefix: String,
        expectedType: String,
        reason: String,
        collectionType: String? = null,
        entry: String? = null,
        path: String? = null,
        receivedValue: String? = null,
        failures: List<Map<String, String>> = emptyList()
    ): IdkError {
        val messagePrefix = if (collectionType != null) {
            "Failed to bind $collectionType config for '$prefix' as '$expectedType'"
        } else {
            "Failed to bind config for '$prefix' as '$expectedType'"
        }

        val meta = mutableMapOf<String, Any?>(
            "prefix" to prefix,
            "expectedType" to expectedType,
            "reason" to reason
        )
        collectionType?.let { meta["collectionType"] = it }
        entry?.let { meta["entry"] = it }
        path?.let { meta["path"] = it }
        receivedValue?.let { meta["receivedValue"] = it }
        if (failures.isNotEmpty()) {
            meta["failures"] = failures
        }

        return IdkError(
            code = "CONFIG_BIND_ERROR",
            message = IdkError.Message(
                i18nKey = "com.sphereon.core.error.config-bind-error",
                defaultMessage = "$messagePrefix: $reason"
            ),
            exception = null,
            meta = meta
        )
    }
}
