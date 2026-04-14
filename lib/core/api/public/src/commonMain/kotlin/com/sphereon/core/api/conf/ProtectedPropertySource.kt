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

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.Order
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.reflect.KClass

/**
 * Extended PropertySource that tracks protection metadata for properties.
 *
 * This interface adds protection-awareness to property sources, enabling:
 * - Tracking of which properties have FINAL or PROTECTED status
 * - Enforcement of override restrictions
 * - Enforcement of interpolation restrictions
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProtectedPropertySource", exact = true)
interface ProtectedPropertySource<T> : PropertySource<T> {
    /**
     * Get protection metadata for a canonical key.
     *
     * @param canonicalKey The normalized key without protection prefixes
     * @return The protection metadata, or null if no protection is defined
     */
    fun getProtection(canonicalKey: String): PropertyProtection?

    /**
     * Check if setting a property is allowed from the given scope.
     *
     * For FINAL properties, only scopes at the same level or higher (lower number)
     * than where the property is defined can set the value.
     *
     * @param key The property key to check
     * @param fromScope The scope attempting to set the property
     * @return True if setting is allowed, false if denied
     */
    fun canSet(
        key: String,
        fromScope: ConfigLevel,
    ): Boolean

    /**
     * Check if interpolating a property is allowed from the given scope.
     *
     * For PROTECTED properties, only scopes at the same level or higher (lower number)
     * than where the property is defined can interpolate the value.
     *
     * @param key The property key to check
     * @param fromScope The scope attempting to interpolate the property
     * @return True if interpolation is allowed, false if denied
     */
    fun canInterpolate(
        key: String,
        fromScope: ConfigLevel,
    ): Boolean
}

/**
 * Mutable map property source with protection prefix support.
 *
 * When adding properties via [addProtectedProperty], protection prefixes are automatically parsed and stored:
 * - "final.db.host" → stores "db.host" with FINAL protection
 * - "protected.api.key" → stores "api.key" with PROTECTED protection
 * - "final.protected.secret" → stores "secret" with FINAL + PROTECTED protection
 *
 * @param name The name of this property source
 * @param sourceLevel The configuration level of this source (APP, TENANT, or PRINCIPAL)
 * @param order The priority order for this source
 * @param keyParser The parser for extracting protection prefixes
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProtectedMutableMapPropertySource", exact = true)
open class ProtectedMutableMapPropertySource(
    private val sourceName: String,
    private val sourceLevel: ConfigLevel,
    private val order: Int = Order.MEDIUM.orderValue,
    private val keyParser: ProtectionKeyParser = DefaultProtectionKeyParser,
) : ProtectedPropertySource<MutableMap<String, Any>>,
    ScopedPropertySource<MutableMap<String, Any>> {
    private val keyNormalizer: PropertyKeyNormalizer = PropertyKeyNormalizerImpl.Default
    private val properties = mutableMapOf<String, Any>()

    // Protection registry: normalized canonical key -> protection metadata
    private val protectionRegistry = mutableMapOf<String, PropertyProtection>()

    override val isPlatformSupported: Boolean = true

    override val configLevel: ConfigLevel = sourceLevel

    /**
     * Add a property, parsing protection from key prefix.
     *
     * If the key has protection prefixes (e.g., "final.db.host"), they are parsed
     * and stored in the protection registry. The property is stored under the
     * canonical key (prefixes stripped).
     */
    open fun addProperty(
        name: String,
        value: Any,
    ) = apply {
        val parsed = keyParser.parse(name)
        val normalizedKey = keyNormalizer.normalize(parsed.canonicalKey)

        properties[normalizedKey] = value

        if (parsed.protection.hasRestrictions) {
            protectionRegistry[normalizedKey] = parsed.protection.withScope(sourceLevel)
        }
    }

    /**
     * Add multiple properties at once.
     */
    open fun addProperties(map: Map<String, Any>) =
        apply {
            map.forEach { addProperty(it.key, it.value) }
        }

    /**
     * Add a property with explicit protection (for programmatic use).
     *
     * This allows setting protection without using key prefixes.
     *
     * @param name The property key (without protection prefixes)
     * @param value The property value
     * @param protection The protection to apply
     */
    open fun addProtectedProperty(
        name: String,
        value: Any,
        protection: PropertyProtection,
    ) = apply {
        val normalizedKey = keyNormalizer.normalize(name)
        properties[normalizedKey] = value
        if (protection.hasRestrictions) {
            protectionRegistry[normalizedKey] = protection.withScope(sourceLevel)
        }
    }

    /**
     * Delete a property.
     */
    open fun deleteProperty(name: String) =
        apply {
            val normalizedKey = keyNormalizer.normalize(name)
            properties.remove(normalizedKey)
            protectionRegistry.remove(normalizedKey)
        }

    override fun getProtection(canonicalKey: String): PropertyProtection? {
        val normalizedKey = keyNormalizer.normalize(canonicalKey)
        return protectionRegistry[normalizedKey]
    }

    override fun canSet(
        key: String,
        fromScope: ConfigLevel,
    ): Boolean {
        val normalizedKey = keyNormalizer.normalize(key)
        val protection = protectionRegistry[normalizedKey] ?: return true

        // Cannot override if FINAL and requesting from lower scope (higher level number)
        if (protection.isFinal) {
            val definedAt = protection.definedAt ?: sourceLevel
            return fromScope.level <= definedAt.level
        }
        return true
    }

    override fun canInterpolate(
        key: String,
        fromScope: ConfigLevel,
    ): Boolean {
        val normalizedKey = keyNormalizer.normalize(key)
        val protection = protectionRegistry[normalizedKey] ?: return true

        // Cannot interpolate if PROTECTED and requesting from lower scope (higher level number)
        if (protection.isInterpolationProtected) {
            val definedAt = protection.definedAt ?: sourceLevel
            return fromScope.level <= definedAt.level
        }
        return true
    }

    /**
     * Get all protection entries for inspection/debugging.
     */
    fun getAllProtections(): Map<String, PropertyProtection> = protectionRegistry.toMap()

    // PropertySource implementation

    override fun hasProperty(name: String): Boolean = properties.containsKey(keyNormalizer.normalize(name))

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getProperty(
        name: String,
        targetType: KClass<T>,
    ): T? {
        val value = properties[keyNormalizer.normalize(name)] ?: return null
        if (targetType.isInstance(value)) {
            return value as T?
        }
        // Coerce values to the requested type
        val coerced: Any? =
            when {
                value is String -> {
                    when (targetType) {
                        Int::class -> value.toIntOrNull()
                        Long::class -> value.toLongOrNull()
                        Boolean::class -> value.lowercase().toBooleanStrictOrNull()
                        Double::class -> value.toDoubleOrNull()
                        Float::class -> value.toFloatOrNull()
                        String::class -> value
                        else -> null
                    }
                }

                value is Number -> {
                    when (targetType) {
                        Int::class -> value.toInt()
                        Long::class -> value.toLong()
                        Double::class -> value.toDouble()
                        Float::class -> value.toFloat()
                        String::class -> value.toString()
                        else -> null
                    }
                }

                else -> {
                    null
                }
            }
        if (coerced != null) {
            return coerced as T
        }
        throw IllegalArgumentException("$value is not of type ${targetType.simpleName} but type ${value::class.simpleName}")
    }

    override fun getPropertyAsString(name: String): String? {
        val value = properties[keyNormalizer.normalize(name)] ?: return null
        return "$value"
    }

    override fun removeProperty(name: String) {
        deleteProperty(name)
    }

    override fun getName(): String = sourceName

    override fun getSource(): MutableMap<String, Any> = properties

    override fun getAllPropertyNames(): Set<String> = properties.keys.map { keyNormalizer.normalize(it) }.toSet()

    override fun getOrder(): Int = order

    override fun compareTo(other: PropertySource<*>): Int = this.getOrder().compareTo(other.getOrder())
}

/**
 * Environment variable property source with FINAL_/PROTECTED_ prefix support.
 *
 * This source reads environment variables and parses protection prefixes:
 * - FINAL_DB_HOST → stores "db.host" (normalized) with FINAL protection
 * - PROTECTED_API_KEY → stores "api.key" (normalized) with PROTECTED protection
 * - FINAL_PROTECTED_SECRET → stores "secret" (normalized) with FINAL + PROTECTED protection
 *
 * Environment variables are always considered to be at APP scope level.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProtectedEnvPropertySource", exact = true)
open class ProtectedEnvPropertySource(
    private val keyParser: ProtectionKeyParser = EnvPrefixProtectionParser(),
) : ProtectedPropertySource<Map<String, Any>>,
    ScopedPropertySource<Map<String, Any>> {
    private val keyNormalizerInstance: PropertyKeyNormalizer = PropertyKeyNormalizerImpl.Default
    private val delegateSource =
        MapPropertySource(
            name = "protected-environment",
            source = Env.getAll().map { keyNormalizerInstance.normalize(it.key.lowercase()) to it.value }.toMap(),
            order = Order.HIGHEST.orderValue,
        )

    // Lazily built protection registry from env var prefixes
    private val protectionRegistry: Map<String, PropertyProtection> by lazy {
        buildProtectionRegistry()
    }

    // Mapping from canonical normalized key to original env key for value lookup
    private val keyMapping: Map<String, String> by lazy {
        buildKeyMapping()
    }

    override val isPlatformSupported: Boolean = true

    override val configLevel: ConfigLevel = ConfigLevel.APP

    private fun buildProtectionRegistry(): Map<String, PropertyProtection> {
        val result = mutableMapOf<String, PropertyProtection>()
        for (envKey in Env.getAll().keys) {
            val parsed = keyParser.parse(envKey)
            if (parsed.protection.hasRestrictions) {
                val normalizedKey = keyNormalizerInstance.normalize(parsed.canonicalKey.lowercase())
                result[normalizedKey] = parsed.protection.withScope(ConfigLevel.APP)
            }
        }
        return result
    }

    private fun buildKeyMapping(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (envKey in Env.getAll().keys) {
            val parsed = keyParser.parse(envKey)
            val normalizedKey = keyNormalizerInstance.normalize(parsed.canonicalKey.lowercase())
            result[normalizedKey] = envKey
        }
        return result
    }

    override fun <T : Any> getProperty(
        name: String,
        targetType: KClass<T>,
    ): T? {
        val normalizedKey = keyNormalizerInstance.normalize(name)
        // Look up using the original env key
        val originalKey = keyMapping[normalizedKey]
        if (originalKey != null) {
            val value = Env.get(originalKey)
            @Suppress("UNCHECKED_CAST")
            return value as? T
        }
        // Fall back to delegate
        return delegateSource.getProperty(name, targetType)
    }

    override fun getProtection(canonicalKey: String): PropertyProtection? {
        val normalizedKey = keyNormalizerInstance.normalize(canonicalKey)
        return protectionRegistry[normalizedKey]
    }

    override fun canSet(
        key: String,
        fromScope: ConfigLevel,
    ): Boolean {
        val normalizedKey = keyNormalizerInstance.normalize(key)
        val protection = protectionRegistry[normalizedKey] ?: return true
        if (protection.isFinal) {
            return fromScope.level <= ConfigLevel.APP.level
        }
        return true
    }

    override fun canInterpolate(
        key: String,
        fromScope: ConfigLevel,
    ): Boolean {
        val normalizedKey = keyNormalizerInstance.normalize(key)
        val protection = protectionRegistry[normalizedKey] ?: return true
        if (protection.isInterpolationProtected) {
            return fromScope.level <= ConfigLevel.APP.level
        }
        return true
    }

    // PropertySource delegation

    override fun hasProperty(name: String): Boolean = delegateSource.hasProperty(name)

    override fun getPropertyAsString(name: String): String? {
        val normalizedKey = keyNormalizerInstance.normalize(name)
        val originalKey = keyMapping[normalizedKey]
        if (originalKey != null) {
            return Env.get(originalKey)
        }
        return delegateSource.getPropertyAsString(name)
    }

    override fun removeProperty(name: String): Unit = throw UnsupportedOperationException("Cannot remove environment variables")

    override fun getName(): String = "protected-environment"

    override fun getSource(): Map<String, Any> = delegateSource.getSource()

    override fun getAllPropertyNames(): Set<String> = keyMapping.keys

    override fun getOrder(): Int = Order.HIGHEST.orderValue

    override fun compareTo(other: PropertySource<*>): Int = this.getOrder().compareTo(other.getOrder())
}

/**
 * Singleton instance of ProtectedEnvPropertySource for use in property sources.
 * This provides protection-aware environment variable access with FINAL_/PROTECTED_ prefix support.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("StaticProtectedEnvPropertySourceObject", exact = true)
object StaticProtectedEnvPropertySourceObject : ProtectedEnvPropertySource()
