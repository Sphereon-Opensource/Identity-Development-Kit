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

private val FORBIDDEN_EXTERNAL_PLACEHOLDER =
    Regex("""\$\{\s*secret(?:\s*[:.]|//|\s*\})""", RegexOption.IGNORE_CASE)
private val FORBIDDEN_EXTERNAL_URI =
    Regex("""\bsecret://""", RegexOption.IGNORE_CASE)
private val ENVIRONMENT_PLACEHOLDER =
    Regex("""\$\{\s*env:([A-Za-z_][A-Za-z0-9_]*)""")

internal fun forbiddenExternalReferenceError(value: String): IdkError? =
    if (FORBIDDEN_EXTERNAL_PLACEHOLDER.containsMatchIn(value) || FORBIDDEN_EXTERNAL_URI.containsMatchIn(value)) {
        ConfigErrors.interpolationError(
            key = "external-source",
            reason = "secret-provider references are forbidden",
        )
    } else {
        null
    }

/**
 * Returns the process-environment names explicitly declared by `${env:NAME...}` placeholders.
 *
 * This deliberately recognizes only direct declarations. A placeholder assembled through
 * recursive interpolation is not an allowlist declaration and is rejected when it reaches the
 * environment-resolution step.
 */
internal fun declaredEnvironmentReferences(value: String): Set<String> =
    ENVIRONMENT_PLACEHOLDER
        .findAll(value)
        .map { it.groupValues[1] }
        .toSet()

/**
 * Enforces the recursive write-time configuration-reference policy.
 *
 * Deferred external secret-provider references are rejected for every scope before persistence.
 * APP configuration may retain legitimate environment placeholders and is validated again
 * against its declaration-bound allowlist at resolution time. TENANT and PRINCIPAL configuration
 * may never persist a direct environment placeholder.
 */
fun validateEnvironmentReferencesForWrite(
    value: Any?,
    scope: ConfigLevel,
) {
    var visitedNodes = 0

    fun validateNested(
        nestedValue: Any?,
        depth: Int,
    ) {
        visitedNodes += 1
        if (depth > MAX_CONFIGURATION_VALUE_DEPTH || visitedNodes > MAX_CONFIGURATION_VALUE_NODES) {
            throw IllegalArgumentException("Configuration value exceeds the permitted validation bounds")
        }

        when (nestedValue) {
            is String -> {
                if (forbiddenExternalReferenceError(nestedValue) != null) {
                    throw IllegalArgumentException("Configuration value is not permitted")
                }
                if (scope != ConfigLevel.APP && declaredEnvironmentReferences(nestedValue).isNotEmpty()) {
                    throw IllegalArgumentException("Environment references are not permitted in this configuration scope")
                }
            }

            is Map<*, *> -> {
                nestedValue.forEach { (key, mapValue) ->
                    validateNested(key, depth + 1)
                    validateNested(mapValue, depth + 1)
                }
            }

            is Iterable<*> -> {
                nestedValue.forEach { validateNested(it, depth + 1) }
            }

            is Array<*> -> {
                nestedValue.forEach { validateNested(it, depth + 1) }
            }
        }
    }

    validateNested(value, 0)
}

private const val MAX_CONFIGURATION_VALUE_DEPTH = 32
private const val MAX_CONFIGURATION_VALUE_NODES = 10_000

fun validateConfigurationValueForRead(
    value: Any?,
    sourceScope: ConfigLevel,
) {
    var visitedNodes = 0

    fun validateNested(
        nestedValue: Any?,
        depth: Int,
    ) {
        visitedNodes += 1
        if (depth > MAX_CONFIGURATION_VALUE_DEPTH || visitedNodes > MAX_CONFIGURATION_VALUE_NODES) {
            throw IllegalStateException("Configuration value exceeds the permitted validation bounds")
        }

        when (nestedValue) {
            is String -> {
                if (forbiddenExternalReferenceError(nestedValue) != null) {
                    throw IllegalStateException("Configuration value is not permitted")
                }
            }

            is Map<*, *> -> {
                nestedValue.forEach { (key, mapValue) ->
                    validateNested(key, depth + 1)
                    validateNested(mapValue, depth + 1)
                }
            }

            is Iterable<*> -> {
                nestedValue.forEach { validateNested(it, depth + 1) }
            }

            is Array<*> -> {
                nestedValue.forEach { validateNested(it, depth + 1) }
            }
        }
    }

    try {
        validateEnvironmentReferencesForWrite(value, sourceScope)
        validateNested(value, 0)
    } catch (_: IllegalArgumentException) {
        throw IllegalStateException("Configuration value is not permitted")
    }
}

internal fun validatePropertySourceEnvironmentReferencesForWrite(source: PropertySource<*>) {
    val scope = (source as? ScopedPropertySource<*>)?.configLevel ?: return
    if (scope == ConfigLevel.APP || !source.isPlatformSupported) {
        return
    }
    source.getAllPropertyNames().forEach { key ->
        validateEnvironmentReferencesForWrite(
            value =
                runCatching { source.getProperty(key, Any::class) }.getOrNull()
                    ?: runCatching { source.getPropertyAsString(key) }.getOrNull(),
            scope = scope,
        )
    }
}

/**
 * Interface for interpolating property values containing placeholders.
 *
 * Supported patterns:
 * - `${VAR}` - Simple substitution
 * - `${VAR:default}` - With default value
 * - `${env:VAR}` - Deployment environment configuration, optionally with a default
 * - `${scope:key}` - Explicit scope prefix (app, tenant, principal)
 * - `${db.${env}}` - Recursive (max depth configurable)
 *
 * Provider-backed secret sources are intentionally absent from this public grammar. Runtime
 * consumers resolve server-generated opaque IDs directly through [OpaqueSecretResolver].
 * Environment interpolation remains supported only for explicitly declared, operator-owned APP
 * configuration because deployment configuration and persisted upgrade state use `${env:...}`
 * references; those are distinct from `${secret:...}` addresses.
 *
 * Protection-aware interpolation:
 * When using a [ProtectedPropertyResolver], scope-prefixed interpolations (e.g., `${app:db.password}`)
 * are checked for PROTECTED status. If a property is marked PROTECTED at a higher scope,
 * lower scopes cannot interpolate its value.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertyInterpolator", exact = true)
interface PropertyInterpolator {
    /**
     * Interpolate placeholders in a value string.
     *
     * @param value The value containing placeholders
     * @param resolver Property resolver for looking up referenced values
     * @return The interpolated string, or error if resolution fails
     */
    suspend fun interpolate(
        value: String,
        resolver: ProtectedPropertyResolver,
    ): IdkResult<String, IdkError>

    /**
     * Interpolate placeholders with scope-aware protection checking.
     *
     * When the resolver is a [ProtectedPropertyResolver], this method checks
     * protection status BEFORE reading values. If a scope-prefixed interpolation
     * (e.g., `${app:db.password}`) references a PROTECTED property, and the
     * requesting scope is lower than where the property is protected, the
     * interpolation is denied without exposing the value.
     *
     * @param value The value containing placeholders
     * @param resolver Property resolver for looking up referenced values
     * @param requestingScope The scope level requesting the interpolation
     * @return The interpolated string, or error if resolution fails or protection denies access
     */
    suspend fun interpolate(
        value: String,
        resolver: ProtectedPropertyResolver,
        requestingScope: ConfigLevel,
    ): IdkResult<String, IdkError>

    /**
     * Interpolate placeholders with an explicit recursion limit.
     *
     * External secret references are always rejected. There is deliberately no option that can
     * enable secret resolution through the property pipeline.
     */
    suspend fun interpolate(
        value: String,
        resolver: ProtectedPropertyResolver,
        requestingScope: ConfigLevel,
        maxDepth: Int?,
    ): IdkResult<String, IdkError> = interpolate(value, resolver, requestingScope)

    /**
     * Interpolate while retaining security provenance for the complete recursive resolution.
     *
     * Production pipelines, caches, and policy-aware resolvers must use this method rather than
     * reconstructing provenance from the final materialized string.
     */
    suspend fun interpolateWithProvenance(
        value: String,
        resolver: ProtectedPropertyResolver,
        requestingScope: ConfigLevel,
        maxDepth: Int?,
        policy: InterpolationPolicy,
        sourceProvenance: ResolutionProvenance,
    ): IdkResult<InterpolatedPropertyValue, IdkError>

    /**
     * Check if a value contains placeholders that need interpolation.
     *
     * @param value The value to check
     * @return True if the value contains `${...}` patterns
     */
    fun containsPlaceholders(value: String): Boolean

    /**
     * Parse placeholders from a value string.
     *
     * @param value The value to parse
     * @return List of parsed placeholder tokens
     */
    fun parsePlaceholders(value: String): List<PlaceholderToken>
}

/**
 * A materialized interpolation result whose provenance survives the interpolation boundary.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("InterpolatedPropertyValue", exact = true)
@CoverageExcludedDataClass
data class InterpolatedPropertyValue(
    val value: String,
    val provenance: ResolutionProvenance,
)

/**
 * Represents a parsed placeholder token.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("PlaceholderToken", exact = true)
@CoverageExcludedDataClass
data class PlaceholderToken(
    val fullMatch: String,
    val type: PlaceholderType,
    /** Stable token identity used for lookups and circular-reference detection. */
    val key: String,
    val defaultValue: String?,
    /** Explicit configuration scope for [PlaceholderType.SCOPE]; absent for other token types. */
    val scope: ConfigLevel? = null,
) {
    companion object {
        @JvmStatic
        fun simple(
            match: String,
            key: String,
            defaultValue: String? = null,
        ) = PlaceholderToken(
            fullMatch = match,
            type = PlaceholderType.SIMPLE,
            key = key,
            defaultValue = defaultValue,
        )

        @JvmStatic
        fun env(
            match: String,
            key: String,
            defaultValue: String? = null,
        ) = PlaceholderToken(
            fullMatch = match,
            type = PlaceholderType.ENV,
            key = key,
            defaultValue = defaultValue,
        )

        @JvmStatic
        fun scope(
            match: String,
            scope: ConfigLevel,
            key: String,
            defaultValue: String? = null,
        ) = PlaceholderToken(
            fullMatch = match,
            type = PlaceholderType.SCOPE,
            key = key,
            defaultValue = defaultValue,
            scope = scope,
        )

        @JvmStatic
        fun forbiddenExternalSource(match: String) =
            PlaceholderToken(
                fullMatch = match,
                type = PlaceholderType.FORBIDDEN_EXTERNAL_SOURCE,
                key = "external-source",
                defaultValue = null,
            )
    }
}

/**
 * Types of placeholder patterns.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("PlaceholderType", exact = true)
enum class PlaceholderType {
    /** Simple `${key}` or `${key:default}` */
    SIMPLE,

    /** Explicit deployment configuration `${env:KEY}`. */
    ENV,

    /** Explicit `${scope:key}` where scope is app/tenant/principal */
    SCOPE,

    /** Environment/provider-backed sources are forbidden in regular configuration. */
    FORBIDDEN_EXTERNAL_SOURCE,
}

/**
 * Default implementation of PropertyInterpolator.
 *
 * Supports protection-aware interpolation when used with a [ProtectedPropertyResolver].
 * When protection checking is enabled, PROTECTED properties at higher scopes cannot
 * be interpolated from lower scopes.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultPropertyInterpolator", exact = true)
class DefaultPropertyInterpolator(
    private val maxDepth: Int = 10,
) : PropertyInterpolator {
    // Simple pattern for non-nested placeholders (no nested ${} inside)
    // Note: closing brace must be escaped for JS/wasmJs regex Unicode mode compatibility
    private val simplePlaceholderPattern = Regex("""\$\{([^{}]+)\}""")
    private val envPattern = Regex("""^env:(.+)$""")
    private val scopePattern = Regex("""^(app|tenant|principal):(.+)$""")
    private val forbiddenExternalSourcePattern = Regex("""^secret(?:[:.]|//|$).*""", RegexOption.IGNORE_CASE)

    override suspend fun interpolate(
        value: String,
        resolver: ProtectedPropertyResolver,
    ): IdkResult<String, IdkError> {
        val result =
            interpolateWithProvenance(
                value = value,
                resolver = resolver,
                requestingScope = resolver.resolverLevel,
                maxDepth = maxDepth,
                policy = legacyPolicy(),
                sourceProvenance = ResolutionProvenance.known(resolver.resolverLevel),
            )
        return result.toLegacyStringResult()
    }

    override suspend fun interpolate(
        value: String,
        resolver: ProtectedPropertyResolver,
        requestingScope: ConfigLevel,
    ): IdkResult<String, IdkError> {
        val result =
            interpolateWithProvenance(
                value = value,
                resolver = resolver,
                requestingScope = requestingScope,
                maxDepth = maxDepth,
                policy = legacyPolicy(),
                sourceProvenance = ResolutionProvenance.known(requestingScope),
            )
        return result.toLegacyStringResult()
    }

    override suspend fun interpolate(
        value: String,
        resolver: ProtectedPropertyResolver,
        requestingScope: ConfigLevel,
        maxDepth: Int?,
    ): IdkResult<String, IdkError> {
        val result =
            interpolateWithProvenance(
                value = value,
                resolver = resolver,
                requestingScope = requestingScope,
                maxDepth = maxDepth,
                policy = legacyPolicy(),
                sourceProvenance = ResolutionProvenance.known(requestingScope),
            )
        return result.toLegacyStringResult()
    }

    override suspend fun interpolateWithProvenance(
        value: String,
        resolver: ProtectedPropertyResolver,
        requestingScope: ConfigLevel,
        maxDepth: Int?,
        policy: InterpolationPolicy,
        sourceProvenance: ResolutionProvenance,
    ): IdkResult<InterpolatedPropertyValue, IdkError> {
        if (isAuthorityEscalation(requestingScope, resolver)) {
            return Err(propertyReferenceDenied())
        }
        forbiddenExternalReferenceError(value)?.let { return Err(it) }
        if (containsPlaceholders(value) && policy == InterpolationPolicy.DENY) {
            return Err(interpolationPolicyDenied())
        }
        return interpolateRecursive(
            value = value,
            resolver = resolver,
            requestingScope = requestingScope,
            authorizedEnvironmentNames = declaredEnvironmentReferences(value),
            visited = mutableSetOf(),
            depth = 0,
            effectiveMaxDepth = maxDepth ?: this.maxDepth,
            policy = policy,
            sourceProvenance = sourceProvenance,
        )
    }

    private fun isAuthorityEscalation(
        requestingScope: ConfigLevel,
        resolver: ProtectedPropertyResolver,
    ): Boolean = requestingScope.level < resolver.resolverLevel.level

    private data class ResolvedInterpolationPart(
        val value: String,
        val sourceScope: ConfigLevel,
        val isAuthoritativePropertyValue: Boolean,
        val provenance: ResolutionProvenance,
    )

    /**
     * Compatibility boundary for the original String API. All production resolution paths use
     * [interpolateWithProvenance] and retain the accompanying metadata.
     */
    private fun IdkResult<InterpolatedPropertyValue, IdkError>.toLegacyStringResult(): IdkResult<String, IdkError> =
        if (isErr) {
            Err(error)
        } else {
            Ok(value.value)
        }

    /**
     * The keyless compatibility API cannot consult an exact field policy, so it may never read
     * process environment. Callers that need approved APP environment interpolation must use
     * [interpolateWithProvenance] with an exact-key policy decision.
     */
    private fun legacyPolicy(): InterpolationPolicy = InterpolationPolicy.PROPERTY_REFERENCES_ONLY

    private suspend fun interpolateRecursive(
        value: String,
        resolver: ProtectedPropertyResolver,
        requestingScope: ConfigLevel,
        authorizedEnvironmentNames: Set<String>,
        visited: MutableSet<String>,
        depth: Int,
        effectiveMaxDepth: Int = maxDepth,
        policy: InterpolationPolicy,
        sourceProvenance: ResolutionProvenance,
    ): IdkResult<InterpolatedPropertyValue, IdkError> {
        forbiddenExternalReferenceError(value)?.let { return Err(it) }

        if (depth > effectiveMaxDepth) {
            return Err(ConfigErrors.maxDepthExceeded("property", effectiveMaxDepth))
        }

        if (!containsPlaceholders(value)) {
            return Ok(InterpolatedPropertyValue(value, sourceProvenance))
        }

        var result = value
        var effectiveScope = requestingScope
        var provenance = sourceProvenance.withTaint(ResolutionTaint.INTERPOLATED)

        // Keep resolving innermost placeholders until no more
        while (containsPlaceholders(result)) {
            // Parse only simple (non-nested) placeholders - innermost ones
            val tokens = parseSimplePlaceholders(result).ifEmpty { parseBalancedPlaceholders(result) }
            if (tokens.isEmpty()) {
                // No balanced placeholders found but containsPlaceholders is true.
                // This means malformed placeholders - break to avoid infinite loop.
                break
            }

            var madeProgress = false
            for (token in tokens) {
                if (!policy.permits(token.type, effectiveScope)) {
                    return Err(interpolationPolicyDenied())
                }
                // Track the token key for circular reference detection
                if (visited.contains(token.key)) {
                    return Err(ConfigErrors.circularReference(token.key, visited.toList() + token.key))
                }

                visited.add(token.key)

                // Check protection BEFORE resolving any property reference. Normalize denials at
                // this boundary so interpolation cannot reveal whether a protected key exists.
                if (token.type == PlaceholderType.SIMPLE || token.type == PlaceholderType.SCOPE) {
                    val canInterpolate = resolver.canInterpolateProperty(token.key, effectiveScope)
                    if (canInterpolate.isErr) {
                        if (token.defaultValue != null) {
                            result = result.replace(token.fullMatch, token.defaultValue)
                            provenance = provenance.withTaint(ResolutionTaint.INTERPOLATED)
                            madeProgress = true
                            visited.remove(token.key)
                            continue
                        }
                        visited.remove(token.key)
                        return Err(propertyReferenceDenied())
                    }
                }

                val resolvedValue =
                    when (token.type) {
                        PlaceholderType.SIMPLE -> {
                            resolveSimple(token, resolver, effectiveScope)
                        }

                        PlaceholderType.ENV -> {
                            val environmentResult =
                                resolveEnv(
                                    token,
                                    resolver,
                                    effectiveScope,
                                    authorizedEnvironmentNames,
                                )
                            if (environmentResult.isErr) {
                                visited.remove(token.key)
                                return Err(environmentResult.error)
                            }
                            environmentResult
                        }

                        PlaceholderType.SCOPE -> {
                            resolveScope(token, resolver, effectiveScope)
                        }

                        PlaceholderType.FORBIDDEN_EXTERNAL_SOURCE -> {
                            Err(
                                ConfigErrors.interpolationError(
                                    "external-source",
                                    "secret-provider references are forbidden",
                                ),
                            )
                        }
                }

                if (resolvedValue.isErr) {
                    if (token.defaultValue != null) {
                            result = result.replace(token.fullMatch, token.defaultValue)
                            provenance =
                                provenance.merge(
                                    defaultProvenance(
                                        token = token,
                                        requestingScope = effectiveScope,
                                    ),
                                )
                            madeProgress = true
                        visited.remove(token.key)
                        continue
                    }

                    if (token.type == PlaceholderType.SIMPLE || token.type == PlaceholderType.SCOPE) {
                        visited.remove(token.key)
                        return Err(propertyReferenceDenied())
                    }

                    visited.remove(token.key)
                    return Err(resolvedValue.error)
                } else {
                    // Recursively interpolate the resolved value (for chained references)
                    val childAuthorizedEnvironmentNames =
                        if (effectiveScope == ConfigLevel.APP &&
                            resolvedValue.value.sourceScope == ConfigLevel.APP &&
                            resolvedValue.value.isAuthoritativePropertyValue
                        ) {
                            authorizedEnvironmentNames + declaredEnvironmentReferences(resolvedValue.value.value)
                        } else {
                            authorizedEnvironmentNames
                        }
                    val recursiveResult =
                        interpolateRecursive(
                            resolvedValue.value.value,
                            resolver,
                            lessPrivilegedScope(effectiveScope, resolvedValue.value.sourceScope),
                            childAuthorizedEnvironmentNames,
                            visited.toMutableSet(),
                            depth + 1,
                            effectiveMaxDepth,
                            policy,
                            resolvedValue.value.provenance,
                        )
                    if (recursiveResult.isErr) {
                        visited.remove(token.key)
                        return recursiveResult
                    }
                    result = result.replace(token.fullMatch, recursiveResult.value.value)
                    provenance = provenance.merge(recursiveResult.value.provenance)
                    effectiveScope =
                        recursiveResult.value.provenance.sourceScope?.let {
                            lessPrivilegedScope(effectiveScope, it)
                        } ?: effectiveScope
                    madeProgress = true
                }

                visited.remove(token.key)
            }

            if (!madeProgress) {
                break
            }
        }

        forbiddenExternalReferenceError(result)?.let { return Err(it) }
        return Ok(InterpolatedPropertyValue(result, provenance))
    }

    private fun lessPrivilegedScope(
        first: ConfigLevel,
        second: ConfigLevel,
    ): ConfigLevel = if (first.level >= second.level) first else second

    /**
     * Parse only simple (non-nested) placeholders.
     * For `${db.${env}}`, this will only match `${env}` (the innermost one).
     */
    private fun parseSimplePlaceholders(value: String): List<PlaceholderToken> =
        simplePlaceholderPattern
            .findAll(value)
            .map { match ->
                val content = match.groupValues[1]
                parseToken(match.value, content)
            }.toList()

    private fun resolveSimple(
        token: PlaceholderToken,
        resolver: ProtectedPropertyResolver,
        requestingScope: ConfigLevel,
    ): IdkResult<ResolvedInterpolationPart, IdkError> {
        val canInterpolate = resolver.canInterpolateProperty(token.key, requestingScope)
        if (canInterpolate.isErr) {
            return Err(canInterpolate.error)
        }

        val resolved = resolver.resolvePropertyWithScope(token.key, requiredScope = null)
        return if (resolved != null) {
            Ok(
                ResolvedInterpolationPart(
                    value = resolved.value,
                    sourceScope = resolved.sourceScope,
                    isAuthoritativePropertyValue = true,
                    provenance = resolved.provenance,
                ),
            )
        } else if (token.defaultValue != null) {
            Ok(
                ResolvedInterpolationPart(
                    token.defaultValue,
                    requestingScope,
                    isAuthoritativePropertyValue = false,
                    provenance = defaultProvenance(token, requestingScope),
                ),
            )
        } else {
            Err(propertyReferenceDenied())
        }
    }

    private fun resolveScope(
        token: PlaceholderToken,
        resolver: ProtectedPropertyResolver,
        requestingScope: ConfigLevel,
    ): IdkResult<ResolvedInterpolationPart, IdkError> {
        val scopeLevel = token.scope ?: return Err(ConfigErrors.propertyNotFound(token.key))

        // Check protection BEFORE accessing the value
        // The protection check was already done in interpolateRecursive for SCOPE tokens,
        // but we double-check here for safety
        val canInterpolate = resolver.canInterpolateProperty(token.key, requestingScope)
        if (canInterpolate.isErr) {
            return Err(canInterpolate.error)
        }

        val resolved = resolver.resolvePropertyWithScope(token.key, requiredScope = scopeLevel)
        return if (resolved != null) {
            Ok(
                ResolvedInterpolationPart(
                    value = resolved.value,
                    sourceScope = resolved.sourceScope,
                    isAuthoritativePropertyValue = true,
                    provenance = resolved.provenance,
                ),
            )
        } else if (token.defaultValue != null) {
            Ok(
                ResolvedInterpolationPart(
                    token.defaultValue,
                    requestingScope,
                    isAuthoritativePropertyValue = false,
                    provenance = defaultProvenance(token, requestingScope),
                ),
            )
        } else {
            Err(propertyReferenceDenied())
        }
    }

    private fun resolveEnv(
        token: PlaceholderToken,
        resolver: ProtectedPropertyResolver,
        requestingScope: ConfigLevel,
        authorizedEnvironmentNames: Set<String>,
    ): IdkResult<ResolvedInterpolationPart, IdkError> {
        if (token.key !in authorizedEnvironmentNames) {
            return Err(propertyReferenceDenied())
        }
        val canInterpolate = resolver.canInterpolateEnvironment(token.key, requestingScope)
        if (canInterpolate.isErr) {
            return Err(canInterpolate.error)
        }
        val envValue = Env.get(token.key)
        return if (envValue != null) {
            Ok(
                ResolvedInterpolationPart(
                    envValue,
                    requestingScope,
                    isAuthoritativePropertyValue = false,
                    provenance =
                        ResolutionProvenance
                            .known(requestingScope)
                            .withTaint(ResolutionTaint.ENVIRONMENT),
                ),
            )
        } else if (token.defaultValue != null) {
            Ok(
                ResolvedInterpolationPart(
                    token.defaultValue,
                    requestingScope,
                    isAuthoritativePropertyValue = false,
                    provenance = defaultProvenance(token, requestingScope),
                ),
            )
        } else {
            Err(ConfigErrors.propertyNotFound("env:${token.key}"))
        }
    }

    private fun defaultProvenance(
        token: PlaceholderToken,
        requestingScope: ConfigLevel,
    ): ResolutionProvenance =
        ResolutionProvenance
            .known(requestingScope)
            .withTaint(ResolutionTaint.INTERPOLATED)
            .let { provenance ->
                if (token.type == PlaceholderType.ENV) {
                    provenance.withTaint(ResolutionTaint.ENVIRONMENT)
                } else {
                    provenance
                }
            }

    private fun InterpolationPolicy.permits(
        placeholderType: PlaceholderType,
        requestingScope: ConfigLevel,
    ): Boolean =
        when (this) {
            InterpolationPolicy.DENY -> false
            InterpolationPolicy.PROPERTY_REFERENCES_ONLY ->
                placeholderType == PlaceholderType.SIMPLE || placeholderType == PlaceholderType.SCOPE
            InterpolationPolicy.APP_ENVIRONMENT ->
                (placeholderType == PlaceholderType.SIMPLE || placeholderType == PlaceholderType.SCOPE) ||
                    (placeholderType == PlaceholderType.ENV && requestingScope == ConfigLevel.APP)
        }

    private fun propertyReferenceDenied(): IdkError =
        ConfigErrors.interpolationError(
            key = "property",
            reason = "property reference is not permitted",
        )

    private fun interpolationPolicyDenied(): IdkError =
        ConfigErrors.interpolationError(
            key = "policy",
            reason = "interpolation is not permitted for this configuration field",
        )

    override fun containsPlaceholders(value: String): Boolean {
        // Check if there's any complete ${...} pattern (with matching closing brace)
        // First quick check for ${
        if (!value.contains("\${")) {
            return false
        }
        // Verify there's at least one properly balanced placeholder
        return hasBalancedPlaceholder(value)
    }

    /**
     * Check if the value contains at least one properly balanced ${...} placeholder.
     */
    private fun hasBalancedPlaceholder(value: String): Boolean {
        var i = 0
        while (i < value.length - 1) {
            if (value[i] == '$' && value[i + 1] == '{') {
                var depth = 0
                var j = i
                while (j < value.length) {
                    when (value[j]) {
                        '{' -> {
                            depth++
                        }

                        '}' -> {
                            depth--
                            if (depth == 0) {
                                return true // Found a complete placeholder
                            }
                        }
                    }
                    j++
                }
                // If we get here, we found ${ but no matching }
                i = j
            } else {
                i++
            }
        }
        return false
    }

    override fun parsePlaceholders(value: String): List<PlaceholderToken> {
        // For public API, parse all balanced placeholders including nested ones
        return parseBalancedPlaceholders(value)
    }

    /**
     * Parse all balanced placeholder expressions, handling nested braces.
     * For `${db.${env}}`, this returns the full `${db.${env}}` as a token.
     */
    private fun parseBalancedPlaceholders(value: String): List<PlaceholderToken> {
        val tokens = mutableListOf<PlaceholderToken>()
        var i = 0
        while (i < value.length - 1) {
            if (value[i] == '$' && value[i + 1] == '{') {
                val start = i
                var depth = 0
                var j = i
                while (j < value.length) {
                    when (value[j]) {
                        '{' -> {
                            depth++
                        }

                        '}' -> {
                            depth--
                            if (depth == 0) {
                                val fullMatch = value.substring(start, j + 1)
                                val content = value.substring(start + 2, j)
                                tokens.add(parseToken(fullMatch, content))
                                i = j
                                break
                            }
                        }
                    }
                    j++
                }
            }
            i++
        }
        return tokens
    }

    private fun parseToken(
        fullMatch: String,
        content: String,
    ): PlaceholderToken {
        envPattern.matchEntire(content)?.let { envMatch ->
            val (key, defaultValue) = parseDefaultValue(envMatch.groupValues[1])
            return PlaceholderToken.env(fullMatch, key, defaultValue)
        }

        forbiddenExternalSourcePattern.matchEntire(content)?.let {
            return PlaceholderToken.forbiddenExternalSource(fullMatch)
        }

        // Check for scope pattern: ${app:key}, ${tenant:key}, ${principal:key}
        scopePattern.matchEntire(content)?.let { scopeMatch ->
            val scope = ConfigLevel.valueOf(scopeMatch.groupValues[1].uppercase())
            val key = scopeMatch.groupValues[2]
            val (actualKey, defaultValue) = parseDefaultValue(key)
            return PlaceholderToken.scope(fullMatch, scope, actualKey, defaultValue)
        }

        // Simple pattern: ${key} or ${key:default}
        val (key, defaultValue) = parseDefaultValue(content)
        return PlaceholderToken.simple(fullMatch, key, defaultValue)
    }

    private fun parseDefaultValue(content: String): Pair<String, String?> {
        // Find the first colon that's not escaped
        val colonIndex = content.indexOf(':')
        return if (colonIndex > 0) {
            val key = content.substring(0, colonIndex)
            val defaultValue = content.substring(colonIndex + 1)
            key to defaultValue
        } else {
            content to null
        }
    }
}
