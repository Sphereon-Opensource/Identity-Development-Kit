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
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.reflect.KClass

/**
 * Extended PropertyResolver that enforces protection rules.
 *
 * This interface adds methods for checking whether operations on protected
 * properties are allowed based on the requesting scope level.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolvedPropertyWithScope", exact = true)
data class ResolvedPropertyWithScope(
    val value: String,
    val sourceScope: ConfigLevel,
    val provenance: ResolutionProvenance = ResolutionProvenance.known(sourceScope),
    val sourceName: String? = null,
    val sourceOrder: Int? = null,
    /** The same atomically resolved value before string rendering, preserving its runtime type. */
    val rawValue: Any = value,
)

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProtectedPropertyResolver", exact = true)
interface ProtectedPropertyResolver : PropertyResolver {
    /**
     * The scope level of this resolver.
     *
     * This is used to determine whether operations are allowed based on
     * the protection rules of properties.
     */
    val resolverLevel: ConfigLevel

    /**
     * Check if a property can be set at this resolver's scope level.
     *
     * For FINAL properties, this returns an error if the property is marked
     * FINAL at a higher scope and this resolver is at a lower scope.
     *
     * @param key The property key to check
     * @return Ok(Unit) if setting is allowed, Err with details if denied
     */
    fun canSetProperty(key: String): IdkResult<Unit, IdkError>

    /**
     * Check if a property can be interpolated from the given scope.
     *
     * For PROTECTED properties, this returns an error if the property is marked
     * PROTECTED at a higher scope and the requesting scope is lower.
     *
     * IMPORTANT: This check must be performed BEFORE reading the actual value
     * to ensure protected values are never exposed.
     *
     * @param key The property key to check
     * @param fromScope The scope attempting to interpolate the property
     * @return Ok(Unit) if interpolation is allowed, Err with details if denied
     */
    fun canInterpolateProperty(
        key: String,
        fromScope: ConfigLevel,
    ): IdkResult<Unit, IdkError>

    /**
     * Check whether a property is directly visible to the given scope.
     *
     * Protected values and direct environment property sources are absent to lower scopes.
     */
    fun canReadProperty(
        key: String,
        fromScope: ConfigLevel,
    ): IdkResult<Unit, IdkError>

    /**
     * Check whether a process-environment name may be interpolated.
     *
     * Only names explicitly declared by an operator-owned APP property source are eligible.
     * TENANT and PRINCIPAL requests are always denied before the environment is read.
     */
    fun canInterpolateEnvironment(
        name: String,
        fromScope: ConfigLevel,
    ): IdkResult<Unit, IdkError>

    /**
     * Resolve a string value and its authoritative source scope in one traversal.
     *
     * An unscoped winning source is not authoritative and must return null. When
     * [requiredScope] is non-null, only a source explicitly bound to that scope
     * may satisfy the lookup.
     */
    fun resolvePropertyWithScope(
        key: String,
        requiredScope: ConfigLevel?,
    ): ResolvedPropertyWithScope?

    /**
     * Get protection metadata for a key.
     *
     * @param key The property key
     * @return The protection metadata if defined, null otherwise
     */
    fun getProtection(key: String): PropertyProtection?
}

/**
 * Implementation that aggregates protection from multiple sources.
 *
 * This resolver iterates through all property sources and aggregates
 * protection metadata. It enforces protection rules when checking
 * whether properties can be set or interpolated.
 *
 * Uses composition with PropertySourcesPropertyResolver for property resolution.
 *
 * @param propertySources The sources to resolve properties from
 * @param resolverLevel The scope level of this resolver
 * @param redactionPolicy Policy for redacting sensitive values
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProtectedPropertySourcesResolver", exact = true)
class ProtectedPropertySourcesResolver(
    private val propertySources: PropertySources,
    override val resolverLevel: ConfigLevel,
    redactionPolicy: SecretRedactionPolicy = DefaultSecretRedactionPolicy(),
) : AbstractPropertyResolver(redactionPolicy),
    ProtectedPropertyResolver,
    ScopeAwarePropertyResolver {
    private val keyNormalizerInstance: PropertyKeyNormalizer = PropertyKeyNormalizerImpl.Default

    override fun getProtection(key: String): PropertyProtection? {
        val normalizedKey = keyNormalizerInstance.normalize(key)

        for (source in propertySources) {
            if (!source.isPlatformSupported) {
                continue
            }
            if (source is ProtectedPropertySource<*>) {
                val protection = source.getProtection(normalizedKey)
                if (protection != null) {
                    return protection
                }
            }
        }
        return null
    }

    override fun resolvePropertyWithScope(
        key: String,
        requiredScope: ConfigLevel?,
    ): ResolvedPropertyWithScope? =
        resolveCanonicalPropertyInternal(key, requiredScope)?.toResolvedPropertyWithScope()

    /**
     * Internal built-in resolver hook that keeps a typed value and its canonical metadata
     * together from the single winning-source read.
     */
    internal fun resolveCanonicalPropertyInternal(
        key: String,
        requiredScope: ConfigLevel?,
    ): ResolvedValue<Any>? {
        val normalizedKey = keyNormalizerInstance.normalize(key)
        if (canReadProperty(normalizedKey, resolverLevel).isErr) {
            return null
        }
        for (source in directSources()) {
            val sourceScope = (source as? ScopedPropertySource<*>)?.configLevel
            if (requiredScope != null && sourceScope != requiredScope) {
                continue
            }
            val validationScope = sourceScope ?: resolverLevel
            val rawValue =
                runCatching { source.getProperty(normalizedKey, Any::class) }
                    .getOrElse { return null }
                    ?: continue
            validateCanonicalValueForRead(rawValue, validationScope)
            return canonicalResolvedValue(
                key = normalizedKey,
                value = rawValue,
                source = source,
                sourceScope = sourceScope,
            )
        }
        return null
    }

    internal fun resolveCanonicalPropertiesInternal(prefixes: Set<String>?): Map<String, ResolvedValue<Any>> {
        val normalizedPrefixes = prefixes?.map(keyNormalizerInstance::normalize)?.toSet()
        val result = linkedMapOf<String, ResolvedValue<Any>>()
        for (source in directSources()) {
            val sourceScope = (source as? ScopedPropertySource<*>)?.configLevel
            val validationScope = sourceScope ?: resolverLevel
            val propertyNames =
                runCatching { source.getAllPropertyNames() }
                    .getOrElse { throw IllegalStateException("Configuration source enumeration failed") }
            for (propertyName in propertyNames) {
                val normalizedKey = keyNormalizerInstance.normalize(propertyName)
                val matches =
                    normalizedPrefixes.isNullOrEmpty() ||
                        normalizedPrefixes.any { prefix ->
                            normalizedKey == prefix || normalizedKey.startsWith("$prefix.")
                        }
                if (!matches || result.containsKey(normalizedKey)) {
                    continue
                }
                if (canReadProperty(normalizedKey, resolverLevel).isErr) {
                    continue
                }
                val rawValue =
                    runCatching { source.getProperty(normalizedKey, Any::class) }
                        .getOrElse { throw IllegalStateException("Configuration source value read failed") }
                        ?: continue
                validateCanonicalValueForRead(rawValue, validationScope)
                result[normalizedKey] =
                    canonicalResolvedValue(
                        key = normalizedKey,
                        value = rawValue,
                        source = source,
                        sourceScope = sourceScope,
                    )
            }
        }
        return result
    }

    private fun canonicalResolvedValue(
        key: String,
        value: Any,
        source: PropertySource<*>,
        sourceScope: ConfigLevel?,
    ): ResolvedValue<Any> {
        val effectiveScope = sourceScope ?: resolverLevel
        val sensitive =
            redactionPolicy.isSensitiveKey(key, effectiveScope) ||
                getProtection(key)?.isInterpolationProtected == true
        val provenance =
            if (sourceScope == null) {
                ResolutionProvenance.unknown().let { unknown ->
                    if (sensitive) unknown.withTaint(ResolutionTaint.SENSITIVE) else unknown
                }
            } else {
                ResolutionProvenance.known(sourceScope, sensitive)
            }
        return ResolvedValue(
            value = value,
            metadata =
                ResolutionMetadata(
                    source = source.getName(),
                    scope = effectiveScope,
                    originalKey = key,
                    normalizedKey = key,
                    order = source.getOrder(),
                    isSecret = sensitive,
                    isInterpolated = false,
                    resolvedAt = kotlin.time.Clock.System.now(),
                    ttl = null,
                    provenance = provenance,
                ),
        )
    }

    private fun validateCanonicalValueForRead(
        value: Any,
        sourceScope: ConfigLevel,
    ) {
        try {
            validateConfigurationValueForRead(value, sourceScope)
        } catch (_: IllegalStateException) {
            // Invalid configuration content is a server-side denial, not an absent property.
            // Do not attach the original exception because it may contain source material.
            throw IllegalStateException("Configuration value is not permitted")
        }
    }

    override fun canSetProperty(key: String): IdkResult<Unit, IdkError> {
        val normalizedKey = keyNormalizerInstance.normalize(key)

        for (source in propertySources) {
            if (!source.isPlatformSupported) {
                continue
            }
            if (source is ProtectedPropertySource<*>) {
                if (!source.canSet(normalizedKey, resolverLevel)) {
                    val protection = source.getProtection(normalizedKey)
                    val definedAt = protection?.definedAt ?: ConfigLevel.APP
                    return Err(ProtectionErrors.overrideNotAllowed(key, definedAt, resolverLevel))
                }
            }
        }
        return Ok(Unit)
    }

    override fun canInterpolateProperty(
        key: String,
        fromScope: ConfigLevel,
    ): IdkResult<Unit, IdkError> {
        val normalizedKey = keyNormalizerInstance.normalize(key)
        var winningSource: PropertySource<*>? = null
        for (source in orderedSourcesByScope(propertySources)) {
            if (!source.isPlatformSupported) {
                continue
            }
            val sourceLookupKey =
                if (source.isDirectEnvironmentPropertySource()) {
                    keyNormalizerInstance.normalize(key.lowercase())
                } else {
                    normalizedKey
                }
            val hasProperty =
                try {
                    source
                        .getAllPropertyNames()
                        .any { propertyName -> keyNormalizerInstance.normalize(propertyName) == sourceLookupKey } ||
                        source.hasProperty(sourceLookupKey)
                } catch (_: IllegalStateException) {
                    return deniedPropertyRead()
                }
            if (hasProperty) {
                winningSource = source
                break
            }
        }
        if (winningSource?.isDirectEnvironmentPropertySource() == true) {
            return deniedPropertyRead()
        }

        return canReadProperty(normalizedKey, fromScope)
    }

    override fun canReadProperty(
        key: String,
        fromScope: ConfigLevel,
    ): IdkResult<Unit, IdkError> {
        val normalizedKey = keyNormalizerInstance.normalize(key)

        for (source in orderedSourcesByScope(propertySources)) {
            if (!source.isPlatformSupported) {
                continue
            }
            if (source is ProtectedPropertySource<*>) {
                if (!source.canInterpolate(normalizedKey, fromScope)) {
                    val protection = source.getProtection(normalizedKey)
                    val definedAt = protection?.definedAt ?: ConfigLevel.APP
                    return Err(ProtectionErrors.interpolationNotAllowed(key, definedAt, fromScope))
                }
            }
        }
        return Ok(Unit)
    }

    override fun canInterpolateEnvironment(
        name: String,
        fromScope: ConfigLevel,
    ): IdkResult<Unit, IdkError> {
        val allowed =
            fromScope == ConfigLevel.APP &&
                propertySources
                    .asSequence()
                    .filter { it.isPlatformSupported }
                    .filterIsInstance<ScopedPropertySource<*>>()
                    .filter { it.configLevel == ConfigLevel.APP }
                    .filterNot { it.isDirectEnvironmentPropertySource() }
                    .flatMap { source ->
                        source.getAllPropertyNames().asSequence().flatMap { key ->
                            runCatching { source.getPropertyAsString(key) }
                                .getOrNull()
                                ?.let(::declaredEnvironmentReferences)
                                .orEmpty()
                                .asSequence()
                        }
                    }.any { it == name }

        return if (allowed) {
            Ok(Unit)
        } else {
            Err(
                ConfigErrors.interpolationError(
                    key = "environment",
                    reason = "environment reference is not permitted",
                ),
            )
        }
    }

    // PropertyResolver delegation

    override fun containsProperty(key: String): Boolean {
        val normalizedKey = keyNormalizerInstance.normalize(key)
        if (canReadProperty(normalizedKey, resolverLevel).isErr) {
            return false
        }
        for (source in directSources()) {
            val hasProperty =
                try {
                    source.hasProperty(normalizedKey)
                } catch (_: IllegalStateException) {
                    return false
                }
            if (!hasProperty) {
                continue
            }
            val value = runCatching { source.getProperty(normalizedKey, Any::class) }.getOrNull()
            return runCatching {
                validateConfigurationValueForRead(value, source.effectiveSourceScope(resolverLevel))
            }.isSuccess
        }
        return false
    }

    override fun <T : Any> getProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T? {
        val normalizedKey = keyNormalizerInstance.normalize(key)
        if (canReadProperty(normalizedKey, resolverLevel).isErr) {
            return defaultValue
        }
        for (source in directSources()) {
            val prop = source.getProperty(normalizedKey, targetType)
            if (prop != null) {
                validateConfigurationValueForRead(prop, source.effectiveSourceScope(resolverLevel))
                return prop
            }
        }
        return defaultValue
    }

    override fun <T : Any> getPropertyAtScope(
        key: String,
        targetType: KClass<T>,
        scope: ConfigLevel,
    ): T? {
        val normalizedKey = keyNormalizerInstance.normalize(key)
        if (canReadProperty(normalizedKey, resolverLevel).isErr) {
            return null
        }
        for (source in sourcesForScope(propertySources, scope)) {
            if (!source.isPlatformSupported || !source.isDirectlyVisibleAt(resolverLevel)) {
                continue
            }
            val prop = source.getProperty(normalizedKey, targetType)
            if (prop != null) {
                validateConfigurationValueForRead(prop, source.effectiveSourceScope(resolverLevel))
                return prop
            }
        }
        return null
    }

    override fun getPropertyAsStringAtScope(
        key: String,
        scope: ConfigLevel,
    ): String? = getPropertyAtScope(key, String::class, scope)

    override fun getAllProperties(): Map<String, Any> = getProperties()

    override fun getSubProperties(
        prefixes: Set<String>,
        stripPrefix: Boolean,
    ): Map<String, Any> = getProperties(prefixes, stripPrefix)

    private fun directSources(): List<PropertySource<*>> =
        orderedSourcesByScope(propertySources)
            .filter { source -> source.isPlatformSupported && source.isDirectlyVisibleAt(resolverLevel) }

    private fun getProperties(
        prefixes: Set<String>? = null,
        stripPrefix: Boolean = true,
    ): Map<String, Any> {
        val normalizedPrefixes = prefixes?.map(keyNormalizerInstance::normalize)?.toSet()
        return resolveCanonicalPropertiesInternal(normalizedPrefixes)
            .mapKeys { (propertyName, _) ->
                val matchingPrefix =
                    normalizedPrefixes
                        ?.firstOrNull { prefix ->
                            propertyName == prefix || propertyName.startsWith("$prefix.")
                        }
                if (stripPrefix && matchingPrefix != null) {
                    propertyName.removePrefix(matchingPrefix).removePrefix(".")
                } else {
                    propertyName
                }
            }.mapValues { (_, resolved) -> resolved.value }
    }

    private fun deniedPropertyRead(): IdkResult<Unit, IdkError> =
        Err(
            ConfigErrors.interpolationError(
                key = "property",
                reason = "property reference is not permitted",
            ),
        )
}

internal fun ResolvedValue<Any>.toResolvedPropertyWithScope(): ResolvedPropertyWithScope =
    ResolvedPropertyWithScope(
        value = value.toString(),
        sourceScope = metadata.scope,
        provenance = metadata.provenance,
        sourceName = metadata.source,
        sourceOrder = metadata.order,
        rawValue = value,
    )

/**
 * Internal dispatch for the built-in protected resolver chain. Keeping this out of the
 * public supertypes avoids exposing the canonical cache/interpolation protocol as API.
 */
internal fun ProtectedPropertyResolver.resolveCanonicalPropertyInternal(
    key: String,
    requiredScope: ConfigLevel? = null,
): ResolvedValue<Any>? =
    when (this) {
        is ProtectedPropertySourcesResolver -> resolveCanonicalPropertyInternal(key, requiredScope)
        is InterpolatingPropertySourcesPropertyResolver -> resolveCanonicalPropertyInternal(key, requiredScope)
        is CachingPropertySourcesPropertyResolver -> resolveCanonicalPropertyInternal(key, requiredScope)
        else -> error("Property resolver does not provide canonical resolution")
    }

internal fun ProtectedPropertyResolver.resolveCanonicalPropertiesInternal(
    prefixes: Set<String>? = null,
): Map<String, ResolvedValue<Any>> =
    when (this) {
        is ProtectedPropertySourcesResolver -> resolveCanonicalPropertiesInternal(prefixes)
        is InterpolatingPropertySourcesPropertyResolver -> resolveCanonicalPropertiesInternal(prefixes)
        is CachingPropertySourcesPropertyResolver -> resolveCanonicalPropertiesInternal(prefixes)
        else -> error("Property resolver does not provide canonical resolution")
    }

/**
 * Factory for creating protection-aware property resolvers.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProtectedPropertyResolverFactory", exact = true)
object ProtectedPropertyResolverFactory {
    /**
     * Create a protected resolver for the APP scope.
     */
    fun forApp(propertySources: PropertySources): ProtectedPropertySourcesResolver = ProtectedPropertySourcesResolver(propertySources, ConfigLevel.APP)

    /**
     * Create a protected resolver for the TENANT scope.
     */
    fun forTenant(propertySources: PropertySources): ProtectedPropertySourcesResolver = ProtectedPropertySourcesResolver(propertySources, ConfigLevel.TENANT)

    /**
     * Create a protected resolver for the PRINCIPAL scope.
     */
    fun forPrincipal(propertySources: PropertySources): ProtectedPropertySourcesResolver = ProtectedPropertySourcesResolver(propertySources, ConfigLevel.PRINCIPAL)
}
