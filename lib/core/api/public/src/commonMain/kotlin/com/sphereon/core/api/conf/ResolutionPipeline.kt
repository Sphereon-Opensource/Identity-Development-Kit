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
import kotlin.concurrent.Volatile
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Core configuration resolution pipeline interface.
 * Provides unified property resolution with metadata for diagnostics.
 */
@JsExportCompat
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
        context: ResolutionContext,
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
        context: ResolutionContext,
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
        redactionPolicy: SecretRedactionPolicy = DefaultSecretRedactionPolicy(),
    ): IdkResult<Map<String, String>, IdkError>

    /**
     * Check if a property exists at any scope level.
     *
     * @param key The property key to check
     * @param context Resolution context with scope information
     * @return True if the property exists
     */
    suspend fun containsProperty(
        key: String,
        context: ResolutionContext,
    ): Boolean

    /**
     * Invalidate cached resolution for a key or prefix.
     *
     * @param keyOrPrefix The key or prefix to invalidate
     * @param context Resolution context for scope-aware invalidation
     */
    suspend fun invalidate(
        keyOrPrefix: String,
        context: ResolutionContext,
    )
}

/**
 * Reified extension for type-safe property resolution.
 */
suspend inline fun <reified T : Any> ConfigResolutionPipeline.resolve(
    key: String,
    context: ResolutionContext,
): IdkResult<ResolvedValue<T>, IdkError> = resolve(key, T::class, context)

/**
 * Context for property resolution operations.
 * Contains scope information and resolution options.
 */
@JsExportCompat
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
    val options: ResolutionOptions = ResolutionOptions(),
) {
    companion object {
        @JvmStatic
        fun app(profiles: List<String> = listOf("default")) =
            ResolutionContext(
                level = ConfigLevel.APP,
                activeProfiles = profiles,
            )

        @JvmStatic
        fun tenant(
            tenantId: String,
            profiles: List<String> = listOf("default"),
        ) = ResolutionContext(
            level = ConfigLevel.TENANT,
            tenantId = tenantId,
            activeProfiles = profiles,
        )

        @JvmStatic
        fun principal(
            tenantId: String,
            principalId: String,
            profiles: List<String> = listOf("default"),
        ) = ResolutionContext(
            level = ConfigLevel.PRINCIPAL,
            tenantId = tenantId,
            principalId = principalId,
            activeProfiles = profiles,
        )
    }
}

/**
 * Options controlling resolution behavior.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolutionOptions", exact = true)
@CoverageExcludedDataClass
data class ResolutionOptions(
    val useCache: Boolean = true,
    val interpolate: Boolean = true,
    val includeMetadata: Boolean = true,
    val maxInterpolationDepth: Int = 10,
    val profile: String? = null,
    val additionalOptions: Map<String, String> = emptyMap(),
)

/**
 * A resolved configuration value with full metadata.
 *
 * @property value The resolved value
 * @property metadata Resolution metadata for diagnostics
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolvedValue", exact = true)
@CoverageExcludedDataClass
data class ResolvedValue<T>(
    val value: T,
    val metadata: ResolutionMetadata,
) {
    companion object {
        @JvmStatic
        fun <T> of(
            value: T,
            source: String,
            scope: ConfigLevel,
            originalKey: String,
            normalizedKey: String = originalKey,
            order: Int = 50,
            isSecret: Boolean = false,
            isInterpolated: Boolean = false,
            ttl: Duration? = null,
        ) = ResolvedValue(
            value = value,
            metadata =
                ResolutionMetadata(
                    source = source,
                    scope = scope,
                    originalKey = originalKey,
                    normalizedKey = normalizedKey,
                    order = order,
                    isSecret = isSecret,
                    isInterpolated = isInterpolated,
                    resolvedAt = Clock.System.now(),
                    ttl = ttl,
                    provenance =
                        ResolutionProvenance
                            .known(scope, sensitive = isSecret)
                            .let { provenance ->
                                if (isInterpolated) {
                                    provenance.withTaint(ResolutionTaint.INTERPOLATED)
                                } else {
                                    provenance
                                }
                            },
                ),
        )
    }
}

/**
 * Metadata about how a property value was resolved.
 * Useful for debugging and diagnostics.
 */
@JsExportCompat
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
    val ttl: Duration?,
    val provenance: ResolutionProvenance = ResolutionProvenance.unknown(),
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
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultConfigResolutionPipeline", exact = true)
class DefaultConfigResolutionPipeline(
    private val propertySources: PropertySources,
    private val interpolator: PropertyInterpolator? = null,
    private val keyNormalizer: PropertyKeyNormalizer = PropertyKeyNormalizerImpl.Default,
    private val resolverLevel: ConfigLevel = ConfigLevel.APP,
    private val snapshotCache: SyncConfigSnapshotCache? = null,
    private val redactionPolicy: SecretRedactionPolicy = DefaultSecretRedactionPolicy(),
    private val interpolationPolicyProvider: InterpolationPolicyProvider = DefaultInterpolationPolicyProvider(),
) : ConfigResolutionPipeline {
    @Volatile
    private var cachedOrderedRevision: Long = Long.MIN_VALUE

    @Volatile
    private var cachedOrderedSources: List<PropertySource<*>> = emptyList()

    private fun cachedOrderedSourcesByScope(): List<PropertySource<*>> {
        val revision = propertySources.revision
        if (cachedOrderedRevision == revision) {
            return cachedOrderedSources
        }
        val ordered = orderedSourcesByScope(propertySources)
        cachedOrderedSources = ordered
        cachedOrderedRevision = revision
        return ordered
    }

    override suspend fun <T : Any> resolve(
        key: String,
        targetType: KClass<T>,
        context: ResolutionContext,
    ): IdkResult<ResolvedValue<T>, IdkError> {
        if (!isWithinAuthority(context)) {
            return policyDenied()
        }
        val normalizedKey = keyNormalizer.normalize(key)
        val directResolver = ProtectedPropertySourcesResolver(propertySources, context.level, redactionPolicy)
        if (directResolver.canReadProperty(normalizedKey, context.level).isErr) {
            return propertyNotFound(key)
        }

        for (source in cachedOrderedSourcesByScope()) {
            if (!source.isPlatformSupported || !source.isDirectlyVisibleAt(context.level)) {
                continue
            }

            val rawValue =
                try {
                    source.getProperty(normalizedKey, targetType)
                } catch (_: IllegalStateException) {
                    return policyDenied()
                }
            if (rawValue != null) {
                val sourceScope =
                    (source as? ScopedPropertySource<*>)?.configLevel
                        ?: return policyDenied()
                try {
                    validateConfigurationValueForRead(rawValue, sourceScope)
                } catch (_: IllegalStateException) {
                    return policyDenied()
                }
                val sensitive =
                    redactionPolicy.shouldRedact(
                        normalizedKey,
                        directMetadata(
                            source = source,
                            sourceScope = sourceScope,
                            originalKey = key,
                            normalizedKey = normalizedKey,
                        ),
                    )
                val sourceProvenance = ResolutionProvenance.known(sourceScope, sensitive)
                val interpolatedValue =
                    if (context.options.interpolate && interpolator != null && rawValue is String) {
                        val interpolationResolver = ProtectedPropertySourcesResolver(propertySources, sourceScope, redactionPolicy)
                        val interpolated =
                            interpolator.interpolateWithProvenance(
                                value = rawValue,
                                resolver = interpolationResolver,
                                requestingScope = sourceScope,
                                maxDepth = context.options.maxInterpolationDepth,
                                policy = interpolationPolicyProvider.policyFor(normalizedKey, sourceScope),
                                sourceProvenance = sourceProvenance,
                            )
                        if (interpolated.isErr) {
                            return Err(interpolated.error)
                        }
                        interpolated.value
                    } else {
                        InterpolatedPropertyValue(rawValue.toString(), sourceProvenance)
                    }

                val isInterpolated =
                    context.options.interpolate &&
                        interpolator != null &&
                        rawValue is String &&
                        interpolator.containsPlaceholders(rawValue)
                val finalValue =
                    if (rawValue is String) {
                        @Suppress("UNCHECKED_CAST")
                        interpolatedValue.value as T
                    } else {
                        rawValue
                    }

                return Ok(
                    ResolvedValue(
                        value = finalValue,
                        metadata =
                            ResolutionMetadata(
                                source = source.getName(),
                                scope = sourceScope,
                                originalKey = key,
                                normalizedKey = normalizedKey,
                                order = source.getOrder(),
                                isSecret = sensitive,
                                isInterpolated = isInterpolated,
                                resolvedAt = Clock.System.now(),
                                ttl = null,
                                provenance = interpolatedValue.provenance,
                            ),
                    ),
                )
            }
        }

        return propertyNotFound(key)
    }

    private fun <T> propertyNotFound(key: String): IdkResult<T, IdkError> =
        Err(
            IdkError.NOT_FOUND_ERROR(
                resource = "config property",
                message = "Property not found: $key",
            ),
        )

    override suspend fun resolveAll(
        prefix: String,
        context: ResolutionContext,
    ): IdkResult<Map<String, ResolvedValue<Any>>, IdkError> =
        resolveAllInternal(prefix, context, redactionPolicy)

    private suspend fun resolveAllInternal(
        prefix: String,
        context: ResolutionContext,
        effectiveRedactionPolicy: SecretRedactionPolicy,
    ): IdkResult<Map<String, ResolvedValue<Any>>, IdkError> {
        if (!isWithinAuthority(context)) {
            return policyDenied()
        }
        val normalizedPrefix = keyNormalizer.normalize(prefix)
        val result = mutableMapOf<String, ResolvedValue<Any>>()
        val directResolver = ProtectedPropertySourcesResolver(propertySources, context.level, effectiveRedactionPolicy)

        for (source in cachedOrderedSourcesByScope()) {
            if (!source.isPlatformSupported || !source.isDirectlyVisibleAt(context.level)) {
                continue
            }

            val propertyNames =
                try {
                    source.getAllPropertyNames()
                } catch (_: IllegalStateException) {
                    return policyDenied()
                }
            for (propertyName in propertyNames) {
                // Check prefix matching with dot boundary to avoid partial matches
                // (e.g., "app" should not match "application.name")
                if (normalizedPrefix.isNotEmpty() &&
                    !propertyName.startsWith("$normalizedPrefix.") &&
                    propertyName != normalizedPrefix
                ) {
                    continue
                }
                if (result.containsKey(propertyName)) {
                    continue
                }
                if (directResolver.canReadProperty(propertyName, context.level).isErr) {
                    continue
                }

                val value =
                    try {
                        source.getProperty(propertyName, Any::class)
                    } catch (_: IllegalStateException) {
                        return policyDenied()
                    } ?: continue
                val sourceScope =
                    (source as? ScopedPropertySource<*>)?.configLevel
                        ?: return policyDenied()
                try {
                    validateConfigurationValueForRead(value, sourceScope)
                } catch (_: IllegalStateException) {
                    return policyDenied()
                }

                val sensitive =
                    effectiveRedactionPolicy.shouldRedact(
                        propertyName,
                        directMetadata(
                            source = source,
                            sourceScope = sourceScope,
                            originalKey = propertyName,
                            normalizedKey = propertyName,
                        ),
                    )
                val sourceProvenance = ResolutionProvenance.known(sourceScope, sensitive)
                val interpolatedValue =
                    if (context.options.interpolate && interpolator != null && value is String) {
                        val interpolationResolver =
                            ProtectedPropertySourcesResolver(
                                propertySources,
                                sourceScope,
                                effectiveRedactionPolicy,
                            )
                        val interpolated =
                            interpolator.interpolateWithProvenance(
                                value = value,
                                resolver = interpolationResolver,
                                requestingScope = sourceScope,
                                maxDepth = context.options.maxInterpolationDepth,
                                policy = interpolationPolicyProvider.policyFor(propertyName, sourceScope),
                                sourceProvenance = sourceProvenance,
                            )
                        if (interpolated.isErr) {
                            continue
                        }
                        interpolated.value
                    } else {
                        InterpolatedPropertyValue(value.toString(), sourceProvenance)
                    }

                val isInterpolated =
                    context.options.interpolate &&
                        interpolator != null &&
                        value is String &&
                        interpolator.containsPlaceholders(value)
                val finalValue = if (value is String) interpolatedValue.value else value

                result[propertyName] =
                    ResolvedValue(
                        value = finalValue,
                        metadata =
                            ResolutionMetadata(
                                source = source.getName(),
                                scope = sourceScope,
                                originalKey = propertyName,
                                normalizedKey = propertyName,
                                order = source.getOrder(),
                                isSecret = sensitive,
                                isInterpolated = isInterpolated,
                                resolvedAt = Clock.System.now(),
                                ttl = null,
                                provenance = interpolatedValue.provenance,
                            ),
                    )
            }
        }

        return Ok(result)
    }

    override suspend fun resolveAllAsString(
        prefix: String,
        context: ResolutionContext,
        redact: Boolean,
        redactionPolicy: SecretRedactionPolicy,
    ): IdkResult<Map<String, String>, IdkError> {
        val effectiveRedactionPolicy =
            CombinedSecretRedactionPolicy(
                authoritative = this.redactionPolicy,
                additional = redactionPolicy,
            )
        val resolveResult = resolveAllInternal(prefix, context, effectiveRedactionPolicy)
        if (resolveResult.isErr) {
            return Err(resolveResult.error)
        }

        val stringMap =
            resolveResult.value.mapValues { (key, resolved) ->
                val value = resolved.value
                if (value == null) {
                    "null"
                } else if (
                    redact &&
                    (
                        resolved.metadata.provenance.hasTaint(ResolutionTaint.SENSITIVE) ||
                            effectiveRedactionPolicy.shouldRedact(key, resolved.metadata)
                    )
                ) {
                    if (this.redactionPolicy.shouldRedact(key, resolved.metadata)) {
                        this.redactionPolicy.redact(value.toString())
                    } else {
                        redactionPolicy.redact(value.toString())
                    }
                } else {
                    value.toString()
                }
            }

        return Ok(stringMap)
    }

    override suspend fun containsProperty(
        key: String,
        context: ResolutionContext,
    ): Boolean {
        if (!isWithinAuthority(context)) {
            return false
        }
        val normalizedKey = keyNormalizer.normalize(key)
        val directResolver = ProtectedPropertySourcesResolver(propertySources, context.level, redactionPolicy)
        if (directResolver.canReadProperty(normalizedKey, context.level).isErr) {
            return false
        }
        for (source in cachedOrderedSourcesByScope()) {
            if (!source.isPlatformSupported || !source.isDirectlyVisibleAt(context.level)) {
                continue
            }
            val present = runCatching { source.hasProperty(normalizedKey) }.getOrDefault(false)
            if (!present) {
                continue
            }
            val value = runCatching { source.getProperty(normalizedKey, Any::class) }.getOrNull()
            val sourceScope = (source as? ScopedPropertySource<*>)?.configLevel ?: return false
            return runCatching {
                validateConfigurationValueForRead(value, sourceScope)
            }.isSuccess
        }
        return false
    }

    override suspend fun invalidate(
        keyOrPrefix: String,
        context: ResolutionContext,
    ) {
        if (!isWithinAuthority(context)) {
            return
        }
        // Invalidate cached entries if cache is available
        snapshotCache?.invalidateByPrefix(keyOrPrefix)
    }

    private fun isWithinAuthority(context: ResolutionContext): Boolean = context.level.level >= resolverLevel.level

    private fun directMetadata(
        source: PropertySource<*>,
        sourceScope: ConfigLevel,
        originalKey: String,
        normalizedKey: String,
    ): ResolutionMetadata =
        ResolutionMetadata(
            source = source.getName(),
            scope = sourceScope,
            originalKey = originalKey,
            normalizedKey = normalizedKey,
            order = source.getOrder(),
            isSecret = false,
            isInterpolated = false,
            resolvedAt = Clock.System.now(),
            ttl = null,
            provenance = ResolutionProvenance.known(sourceScope),
        )

    private fun <T> policyDenied(): IdkResult<T, IdkError> =
        Err(
            ConfigErrors.interpolationError(
                key = "policy",
                reason = "configuration value is not permitted",
            ),
        )
}

/**
 * A call-specific policy may make redaction stricter, but cannot remove sensitivity
 * established by the pipeline's constructor-owned policy.
 */
private class CombinedSecretRedactionPolicy(
    private val authoritative: SecretRedactionPolicy,
    private val additional: SecretRedactionPolicy,
) : SecretRedactionPolicy {
    override fun shouldRedact(
        key: String,
        metadata: ResolutionMetadata,
    ): Boolean =
        authoritative.shouldRedact(key, metadata) ||
            additional.shouldRedact(key, metadata)

    override fun redact(value: String): String = authoritative.redact(value)
}

/**
 * Configuration error definitions for the resolution pipeline.
 */
object ConfigErrors {
    fun propertyNotFound(key: String) =
        IdkError.NOT_FOUND_ERROR(
            resource = "config property",
            message = "Property not found: $key",
        )

    fun interpolationError(
        key: String,
        reason: String,
    ) = IdkError.ILLEGAL_ARGUMENT_ERROR(
        message = "Interpolation failed for property '$key': $reason",
    )

    fun circularReference(
        key: String,
        chain: List<String>,
    ) = IdkError.ILLEGAL_ARGUMENT_ERROR(
        message = "Circular reference detected for property '$key': ${chain.joinToString(" -> ")}",
    )

    fun maxDepthExceeded(
        @Suppress("UNUSED_PARAMETER") key: String,
        depth: Int,
    ) = IdkError.ILLEGAL_ARGUMENT_ERROR(
        message = "Max interpolation depth ($depth) exceeded for a configuration property",
    )

    fun conversionError(
        key: String,
        fromType: String,
        toType: String,
    ) = IdkError.ILLEGAL_ARGUMENT_ERROR(
        message = "Cannot convert property '$key' from $fromType to $toType",
    )

    fun bindError(
        prefix: String,
        expectedType: String,
        reason: String,
        collectionType: String? = null,
        entry: String? = null,
        path: String? = null,
        failures: List<Map<String, String>> = emptyList(),
    ): IdkError {
        val safeReason = "configuration value could not be bound to the expected type"
        val messagePrefix =
            if (collectionType != null) {
                "Failed to bind $collectionType config for '$prefix' as '$expectedType'"
            } else {
                "Failed to bind config for '$prefix' as '$expectedType'"
            }

        val meta =
            mutableMapOf<String, Any?>(
                "prefix" to prefix,
                "expectedType" to expectedType,
                "reason" to safeReason,
            )
        collectionType?.let { meta["collectionType"] = it }
        entry?.let { meta["entry"] = it }
        path?.let { meta["path"] = it }
        if (failures.isNotEmpty()) {
            meta["failures"] =
                failures.map { failure ->
                    failure.mapValues { (key, value) ->
                        if (key == "reason") safeReason else value
                    }
                }
        }
        val diagnosticPaths =
            (
                listOfNotNull(path) +
                    failures.mapNotNull { failure -> failure["path"] }
            ).distinct()
        val pathSuffix =
            if (diagnosticPaths.isEmpty()) {
                ""
            } else {
                diagnosticPaths.joinToString(
                    prefix = " at ",
                    separator = ", ",
                ) { diagnosticPath -> "'$diagnosticPath'" }
            }

        return IdkError(
            code = "CONFIG_BIND_ERROR",
            message =
                IdkError.Message(
                    i18nKey = "com.sphereon.core.error.config-bind-error",
                    defaultMessage = "$messagePrefix$pathSuffix: $safeReason",
                ),
            exception = null,
            meta = meta,
        )
    }
}
