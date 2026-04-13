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

import kotlinx.datetime.Clock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass

@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertyResolver", exact = true)
interface PropertyResolver {
    fun containsProperty(key: String): Boolean

    fun <T : Any> getProperty(key: String, targetType: KClass<T>, defaultValue: T? = null): T?

    fun <T : Any> getRequiredProperty(key: String, targetType: KClass<T>, defaultValue: T? = null): T

    fun getPropertyAsString(key: String, defaultValue: String? = null): String?
    fun getRequiredPropertyAsString(key: String, defaultValue: String? = null): String

    fun getAllProperties(): Map<String, Any>
    fun getAllPropertiesAsString(redact: Boolean = true): Map<String, String>

    fun getSubProperties(prefixes: Set<String>, stripPrefix: Boolean = true): Map<String, Any>
    fun getSubPropertiesAsString(prefixes: Set<String>, stripPrefix: Boolean = true, redact: Boolean = true): Map<String, String>

}

/**
 * PropertyResolver with explicit scope-aware resolution support.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScopeAwarePropertyResolver", exact = true)
interface ScopeAwarePropertyResolver : PropertyResolver {
    fun <T : Any> getPropertyAtScope(key: String, targetType: KClass<T>, scope: ConfigLevel): T?
    fun getPropertyAsStringAtScope(key: String, scope: ConfigLevel): String?
}

inline fun <reified T : Any> PropertyResolver.getProperty(key: String, defaultValue: T? = null): T? = getProperty(key, T::class, defaultValue)
inline fun <reified T : Any> PropertyResolver.getRequiredProperty(key: String, defaultValue: T? = null): T = getRequiredProperty(key, T::class, defaultValue)

// TODO: Integrate conversions instead of relying on plain casts
abstract class AbstractPropertyResolver(
    protected val redactionPolicy: SecretRedactionPolicy = DefaultSecretRedactionPolicy()
) : PropertyResolver {
    override fun getPropertyAsString(key: String, defaultValue: String?): String? = getProperty(key, String::class, defaultValue)
    override fun <T : Any> getRequiredProperty(key: String, targetType: KClass<T>, defaultValue: T?) =
        getProperty(key, targetType, defaultValue) ?: throw IllegalStateException("No property found for key: $key")

    override fun getRequiredPropertyAsString(key: String, defaultValue: String?): String = getRequiredProperty(key, String::class, defaultValue)

    override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> =
        getAllProperties().mapValues { (key, value) -> redactIfNeeded(key, value, redact) }

    override fun getSubPropertiesAsString(prefixes: Set<String>, stripPrefix: Boolean, redact: Boolean): Map<String, String> =
        getSubProperties(prefixes, stripPrefix).mapValues { (key, value) -> redactIfNeeded(key, value, redact) }

    /**
     * Apply redaction to a property value based on key patterns and policy.
     *
     * @param key The property key (used for pattern matching)
     * @param value The property value
     * @param redact Whether redaction should be applied
     * @param scope The configuration level scope for metadata
     * @return The string representation of the value, redacted if applicable
     */
    protected fun redactIfNeeded(key: String, value: Any?, redact: Boolean, scope: ConfigLevel = ConfigLevel.APP): String {
        if (value == null) return "null"
        if (!redact) return value.toString()

        val metadata = ResolutionMetadata(
            source = "PropertyResolver",
            scope = scope,
            originalKey = key,
            normalizedKey = key,
            order = 0,
            isSecret = false,  // Key-based detection only at this level
            isInterpolated = false,
            resolvedAt = Clock.System.now(),
            ttl = null
        )

        return if (redactionPolicy.shouldRedact(key, metadata)) {
            redactionPolicy.redact(value.toString())
        } else {
            value.toString()
        }
    }
}


class PropertySourcesPropertyResolver(
    private val propertySources: PropertySources,
    redactionPolicy: SecretRedactionPolicy = DefaultSecretRedactionPolicy()
) : AbstractPropertyResolver(redactionPolicy), ScopeAwarePropertyResolver {
    private val keyNormalizer = PropertyKeyNormalizerImpl()
    @Volatile
    private var cachedOrderedRevision: Long = Long.MIN_VALUE
    @Volatile
    private var cachedOrderedSources: List<PropertySource<*>> = emptyList()

    private fun orderedSources(): List<PropertySource<*>> {
        val revision = propertySources.revision
        if (cachedOrderedRevision == revision) {
            return cachedOrderedSources
        }
        val ordered = orderedSourcesByScope(propertySources)
        cachedOrderedSources = ordered
        cachedOrderedRevision = revision
        return ordered
    }

    private fun sourcesAtScope(scope: ConfigLevel): List<PropertySource<*>> {
        val ordered = orderedSources()
        val hasScopedSources = ordered.any { it is ScopedPropertySource<*> }
        if (!hasScopedSources) {
            return if (scope == ConfigLevel.APP) ordered else emptyList()
        }
        return ordered.filter { source ->
            when (source) {
                is ScopedPropertySource<*> -> source.configLevel == scope
                else -> scope == ConfigLevel.APP
            }
        }
    }

    override fun containsProperty(key: String): Boolean {
        val normalizedKey = keyNormalizer.normalize(key)
        return orderedSources().any { source ->
            source.isPlatformSupported && source.hasProperty(normalizedKey)
        }
    }

    override fun <T : Any> getProperty(key: String, targetType: KClass<T>, defaultValue: T?): T? {
        val normalizedKey = keyNormalizer.normalize(key)
        for (source in orderedSources()) {
            if (!source.isPlatformSupported) continue
            val prop = source.getProperty(normalizedKey, targetType)
            if (prop != null) {
                return prop
            }
        }
        return defaultValue
    }

    override fun <T : Any> getPropertyAtScope(key: String, targetType: KClass<T>, scope: ConfigLevel): T? {
        val normalizedKey = keyNormalizer.normalize(key)
        for (source in sourcesAtScope(scope)) {
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

    override fun getAllProperties(): Map<String, Any> {
        return this.getProperties(null, false)
    }

    override fun getSubProperties(prefixes: Set<String>, stripPrefix: Boolean): Map<String, Any> {
        return this.getProperties(prefixes, stripPrefix)
    }

    private fun getProperties(prefixes: Set<String>? = null, stripPrefix: Boolean = true): Map<String, Any> {
        val normalizedPrefixes = prefixes?.map { keyNormalizer.normalize(it) }?.toSet()
        val result = mutableMapOf<String, Any>()
        for (source in orderedSources()) {
            if (!source.isPlatformSupported) continue
            for (propertyName in source.getAllPropertyNames()) {
                // Check prefix matching with dot boundary to avoid partial matches
                // (e.g., "app" should not match "application.name")
                if (!normalizedPrefixes.isNullOrEmpty()) {
                    val matchingPrefix = normalizedPrefixes.firstOrNull { prefix ->
                        propertyName.startsWith("$prefix.") || propertyName == prefix
                    }
                    if (matchingPrefix == null) continue
                }
                val resultName = if (!normalizedPrefixes.isNullOrEmpty()) stripPrefix(propertyName, normalizedPrefixes, stripPrefix) else propertyName
                if (result.containsKey(resultName)) {
                    continue
                }
                source.getProperty(propertyName, Any::class)?.let { value ->
                    result[resultName] = value
                }
            }
        }
        return result
    }


    protected fun stripPrefix(key: String, prefixes: Set<String>, stripPrefix: Boolean): String {
        if (!stripPrefix) {
            return key
        }
        // Find the matching prefix with dot boundary validation
        for (prefix in prefixes) {
            if (key.startsWith("$prefix.")) {
                return key.removePrefix("$prefix.")
            } else if (key == prefix) {
                return ""
            }
        }
        return key
    }
}


@Inject
@SingleIn(AppScope::class)
//@Inject
@ContributesIntoSet(AppScope::class, binding = binding<IPropertyValueConversion<*>>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("StringPropertyValueConverterImpl", exact = true)
class StringPropertyValueConverterImpl() : IPropertyValueConversion<String> {
    override fun supports(value: Any): Boolean {
        return when (value) {
            is Boolean -> true
            is Number -> true
            is String -> true
            else -> false
        }
    }

    override fun convert(value: Any): String {
        return when (value) {
            is String -> value
            else -> value.toString()
        }
    }
}


@Inject
@SingleIn(AppScope::class)
//@ContributesIntoSet(AppScope::class, binding = binding<IPropertyValueConversion<*>>()) // TODO: Currently throws an exception: Caused by: java.lang.IllegalStateException: KSType 'IPropertyValueConversion<*>' has type arguments, which are not supported for ClassName conversion. Use KSType.toTypeName().
//	at com.squareup.kotlinpoet.ksp.KsTypesKt.toClassName(KsTypes.kt:44)
//	at software.amazon.lastmile.kotlin.inject.anvil.processor.ContributesBindingProcessor$GeneratedFunction.bindingMethodReturnType_delegate$lambda$0(ContributesBindingProcessor.kt:303)
//	at kotlin.SynchronizedLazyImpl.getValue(LazyJVM.kt:83)
//	at software.amazon.lastmile.kotlin.inject.anvil.processor.ContributesBindingProcessor$GeneratedFunction.getBindingMethodReturnType(ContributesBindingProcessor.kt:302)
//	at software.amazon.lastmile.kotlin.inject.anvil.processor.ContributesBindingProcessor.generateComponentInterface(ContributesBindingProcessor.kt:103)
//	at software.amazon.lastmile.kotlin.inject.anvil.processor.ContributesBindingProcessor.process(ContributesBindingProcessor.kt:81)
//	at software.amazon.lastmile.kotlin.inject.anvil.CompositeSymbolProcessor.process(CompositeSymbolProcessor.kt:17)
//	at com.google.devtools.ksp.impl.KotlinSymbolProcessing$execute$1$1.invoke(KotlinSymbolProcessing.kt:579)
@OptIn(ExperimentalObjCName::class)
@ObjCName("NumberPropertyValueConverterImpl", exact = true)
class NumberPropertyValueConverterImpl() : IPropertyValueConversion<Number> {
    override fun supports(value: Any): Boolean {
        return when (value) {
            is Boolean -> true
            is Number -> true
            is String -> true
            else -> false
        }
    }

    override fun convert(value: Any): Number {
        return when (value) {
            is Number -> value
            is ULong -> value.toLong()
            is UInt -> value.toInt()
            is UShort -> value.toShort()
            is Boolean -> if (value) 1 else 0
            is Char -> value.code.toLong()
            is String -> value.toLongOrNull() ?: throw IllegalArgumentException("Cannot convert string '$value' to Number: invalid numeric format")
            else -> throw IllegalArgumentException("Cannot convert value: $value to Number")
        }
    }
}


@OptIn(ExperimentalObjCName::class)
@ObjCName("IPropertyValueConversion", exact = true)
interface IPropertyValueConversion<T : Any> {
    fun supports(value: Any): Boolean
    fun convert(value: Any): T
}
