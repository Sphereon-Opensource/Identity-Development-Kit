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
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.reflect.KClass

/**
 * Extended PropertyResolver that enforces protection rules.
 *
 * This interface adds methods for checking whether operations on protected
 * properties are allowed based on the requesting scope level.
 */
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
    fun canInterpolateProperty(key: String, fromScope: ConfigLevel): IdkResult<Unit, IdkError>

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
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProtectedPropertySourcesResolver", exact = true)
class ProtectedPropertySourcesResolver(
    private val propertySources: PropertySources,
    override val resolverLevel: ConfigLevel,
    redactionPolicy: SecretRedactionPolicy = DefaultSecretRedactionPolicy()
) : ProtectedPropertyResolver, ScopeAwarePropertyResolver {

    private val delegate = PropertySourcesPropertyResolver(propertySources, redactionPolicy)
    private val keyNormalizerInstance: PropertyKeyNormalizer = PropertyKeyNormalizerImpl()

    override fun getProtection(key: String): PropertyProtection? {
        val normalizedKey = keyNormalizerInstance.normalize(key)

        for (source in propertySources) {
            if (!source.isPlatformSupported) continue
            if (source is ProtectedPropertySource<*>) {
                val protection = source.getProtection(normalizedKey)
                if (protection != null) {
                    return protection
                }
            }
        }
        return null
    }

    override fun canSetProperty(key: String): IdkResult<Unit, IdkError> {
        val normalizedKey = keyNormalizerInstance.normalize(key)

        for (source in propertySources) {
            if (!source.isPlatformSupported) continue
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

    override fun canInterpolateProperty(key: String, fromScope: ConfigLevel): IdkResult<Unit, IdkError> {
        val normalizedKey = keyNormalizerInstance.normalize(key)

        for (source in orderedSourcesByScope(propertySources)) {
            if (!source.isPlatformSupported) continue
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

    // PropertyResolver delegation

    override fun containsProperty(key: String): Boolean = delegate.containsProperty(key)

    override fun <T : Any> getProperty(key: String, targetType: KClass<T>, defaultValue: T?): T? =
        delegate.getProperty(key, targetType, defaultValue)

    override fun <T : Any> getPropertyAtScope(key: String, targetType: KClass<T>, scope: ConfigLevel): T? {
        val normalizedKey = keyNormalizerInstance.normalize(key)
        for (source in sourcesForScope(propertySources, scope)) {
            if (!source.isPlatformSupported) continue
            val prop = source.getProperty(normalizedKey, targetType)
            if (prop != null) {
                return prop
            }
        }
        return null
    }

    override fun getPropertyAsStringAtScope(key: String, scope: ConfigLevel): String? =
        getPropertyAtScope(key, String::class, scope)

    override fun <T : Any> getRequiredProperty(key: String, targetType: KClass<T>, defaultValue: T?): T =
        delegate.getRequiredProperty(key, targetType, defaultValue)

    override fun getPropertyAsString(key: String, defaultValue: String?): String? =
        delegate.getPropertyAsString(key, defaultValue)

    override fun getRequiredPropertyAsString(key: String, defaultValue: String?): String =
        delegate.getRequiredPropertyAsString(key, defaultValue)

    override fun getAllProperties(): Map<String, Any> = delegate.getAllProperties()

    override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> =
        delegate.getAllPropertiesAsString(redact)

    override fun getSubProperties(prefixes: Set<String>, stripPrefix: Boolean): Map<String, Any> =
        delegate.getSubProperties(prefixes, stripPrefix)

    override fun getSubPropertiesAsString(prefixes: Set<String>, stripPrefix: Boolean, redact: Boolean): Map<String, String> =
        delegate.getSubPropertiesAsString(prefixes, stripPrefix, redact)
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
    fun forApp(propertySources: PropertySources): ProtectedPropertySourcesResolver {
        return ProtectedPropertySourcesResolver(propertySources, ConfigLevel.APP)
    }

    /**
     * Create a protected resolver for the TENANT scope.
     */
    fun forTenant(propertySources: PropertySources): ProtectedPropertySourcesResolver {
        return ProtectedPropertySourcesResolver(propertySources, ConfigLevel.TENANT)
    }

    /**
     * Create a protected resolver for the PRINCIPAL scope.
     */
    fun forPrincipal(propertySources: PropertySources): ProtectedPropertySourcesResolver {
        return ProtectedPropertySourcesResolver(propertySources, ConfigLevel.PRINCIPAL)
    }
}
