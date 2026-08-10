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
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.OptionalBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.concurrent.Volatile
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.reflect.KClass
import kotlin.time.Clock

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertyResolver", exact = true)
interface PropertyResolver {
    fun containsProperty(key: String): Boolean

    fun <T : Any> getProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T? = null,
    ): T?

    fun <T : Any> getRequiredProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T? = null,
    ): T

    fun getPropertyAsString(
        key: String,
        defaultValue: String? = null,
    ): String?

    fun getRequiredPropertyAsString(
        key: String,
        defaultValue: String? = null,
    ): String

    fun getAllProperties(): Map<String, Any>

    fun getAllPropertiesAsString(redact: Boolean = true): Map<String, String>

    fun getSubProperties(
        prefixes: Set<String>,
        stripPrefix: Boolean = true,
    ): Map<String, Any>

    fun getSubPropertiesAsString(
        prefixes: Set<String>,
        stripPrefix: Boolean = true,
        redact: Boolean = true,
    ): Map<String, String>
}

/**
 * PropertyResolver with explicit scope-aware resolution support.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScopeAwarePropertyResolver", exact = true)
interface ScopeAwarePropertyResolver : PropertyResolver {
    fun <T : Any> getPropertyAtScope(
        key: String,
        targetType: KClass<T>,
        scope: ConfigLevel,
    ): T?

    fun getPropertyAsStringAtScope(
        key: String,
        scope: ConfigLevel,
    ): String?
}

inline fun <reified T : Any> PropertyResolver.getProperty(
    key: String,
    defaultValue: T? = null,
): T? = getProperty(key, T::class, defaultValue)

inline fun <reified T : Any> PropertyResolver.getRequiredProperty(
    key: String,
    defaultValue: T? = null,
): T = getRequiredProperty(key, T::class, defaultValue)

fun redactIfNeeded(
    key: String,
    value: Any?,
    redact: Boolean,
    scope: ConfigLevel = ConfigLevel.APP,
    redactionPolicy: SecretRedactionPolicy,
    provenance: ResolutionProvenance = ResolutionProvenance.unknown(),
): String {
    if (value == null) {
        return "null"
    }
    if (!redact) {
        return value.toString()
    }

    val metadata =
        ResolutionMetadata(
            source = "PropertyResolver",
            scope = scope,
            originalKey = key,
            normalizedKey = key,
            order = 0,
            isSecret = false,
            isInterpolated = false,
            resolvedAt = Clock.System.now(),
            ttl = null,
            provenance = provenance,
        )

    return if (redactionPolicy.shouldRedact(key, metadata)) {
        redactionPolicy.redact(value.toString())
    } else {
        value.toString()
    }
}

internal fun redactBulkValueIfNeeded(
    outputKey: String,
    value: Any?,
    prefixes: Set<String>?,
    stripPrefix: Boolean,
    redact: Boolean,
    resolver: PropertyResolver,
    scope: ConfigLevel,
    redactionPolicy: SecretRedactionPolicy,
): String {
    if (value == null) {
        return "null"
    }
    if (!redact) {
        return value.toString()
    }

    val keyNormalizer = PropertyKeyNormalizerImpl.Default
    val normalizedOutputKey = keyNormalizer.normalize(outputKey)
    val candidateKeys =
        if (stripPrefix && !prefixes.isNullOrEmpty()) {
            prefixes
                .map(keyNormalizer::normalize)
                .map { prefix ->
                    if (normalizedOutputKey.isEmpty()) {
                        prefix
                    } else {
                        "$prefix.$normalizedOutputKey"
                    }
                }.toSet()
        } else {
            setOf(normalizedOutputKey)
        }
    val protectedResolver = resolver as? ProtectedPropertyResolver
    val shouldRedact =
        if (protectedResolver != null) {
            val resolvedCandidates =
                candidateKeys.mapNotNull { candidateKey ->
                    protectedResolver
                        .resolvePropertyWithScope(candidateKey, requiredScope = null)
                        ?.takeIf { it.value == value.toString() }
                        ?.let { candidateKey to it }
                }
            // Bulk enumeration and provenance lookup are separate operations for this legacy API.
            // If the source changed, failed, or cannot prove the materialized value's provenance,
            // fail closed rather than classifying an unknown value as safe.
            resolvedCandidates.isEmpty() ||
                resolvedCandidates.any { (candidateKey, resolved) ->
                    val provenance = resolved.provenance
                    val metadata =
                        ResolutionMetadata(
                            source = resolved.sourceName ?: "PropertyResolver",
                            scope = resolved.sourceScope,
                            originalKey = candidateKey,
                            normalizedKey = candidateKey,
                            order = resolved.sourceOrder ?: 0,
                            isSecret = provenance.hasTaint(ResolutionTaint.SENSITIVE),
                            isInterpolated = provenance.hasTaint(ResolutionTaint.INTERPOLATED),
                            resolvedAt = Clock.System.now(),
                            ttl = null,
                            provenance = provenance,
                        )
                    provenance.sourceScope == null ||
                        provenance.hasTaint(ResolutionTaint.UNKNOWN) ||
                        provenance.hasTaint(ResolutionTaint.SENSITIVE) ||
                        redactionPolicy.shouldRedact(candidateKey, metadata)
                }
        } else {
            candidateKeys.any { candidateKey ->
                redactionPolicy.shouldRedact(
                    candidateKey,
                    ResolutionMetadata(
                        source = "PropertyResolver",
                        scope = scope,
                        originalKey = candidateKey,
                        normalizedKey = candidateKey,
                        order = 0,
                        isSecret = false,
                        isInterpolated = false,
                        resolvedAt = Clock.System.now(),
                        ttl = null,
                        provenance = ResolutionProvenance.known(scope),
                    ),
                )
            }
        }

    return if (shouldRedact) {
        redactionPolicy.redact(value.toString())
    } else {
        value.toString()
    }
}

// TODO: Integrate conversions instead of relying on plain casts
@JsExportCompat
abstract class AbstractPropertyResolver(
    protected val redactionPolicy: SecretRedactionPolicy = DefaultSecretRedactionPolicy(),
) : PropertyResolver {
    override fun getPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String? = getProperty(key, String::class, defaultValue)

    override fun <T : Any> getRequiredProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ) = getProperty(key, targetType, defaultValue) ?: throw IllegalStateException("No property found for key: $key")

    override fun getRequiredPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String = getRequiredProperty(key, String::class, defaultValue)

    override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> =
        getAllProperties().mapValues { (key, value) ->
            redactBulkValueIfNeeded(
                outputKey = key,
                value = value,
                prefixes = null,
                stripPrefix = false,
                redact = redact,
                resolver = this,
                scope = (this as? ProtectedPropertyResolver)?.resolverLevel ?: ConfigLevel.APP,
                redactionPolicy = redactionPolicy,
            )
        }

    override fun getSubPropertiesAsString(
        prefixes: Set<String>,
        stripPrefix: Boolean,
        redact: Boolean,
    ): Map<String, String> =
        getSubProperties(prefixes, stripPrefix).mapValues { (key, value) ->
            redactBulkValueIfNeeded(
                outputKey = key,
                value = value,
                prefixes = prefixes,
                stripPrefix = stripPrefix,
                redact = redact,
                resolver = this,
                scope = (this as? ProtectedPropertyResolver)?.resolverLevel ?: ConfigLevel.APP,
                redactionPolicy = redactionPolicy,
            )
        }
}

@JsExportCompat
class PropertySourcesPropertyResolver(
    private val propertySources: PropertySources,
    redactionPolicy: SecretRedactionPolicy = DefaultSecretRedactionPolicy(),
) : AbstractPropertyResolver(redactionPolicy),
    ScopeAwarePropertyResolver {
    private val keyNormalizer = PropertyKeyNormalizerImpl.Default

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
            return if (scope == ConfigLevel.APP) {
                ordered
            } else {
                emptyList()
            }
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

    override fun <T : Any> getProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T? {
        val normalizedKey = keyNormalizer.normalize(key)
        for (source in orderedSources()) {
            if (!source.isPlatformSupported) {
                continue
            }
            val prop = source.getProperty(normalizedKey, targetType)
            if (prop != null) {
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
        val normalizedKey = keyNormalizer.normalize(key)
        for (source in sourcesAtScope(scope)) {
            if (!source.isPlatformSupported) {
                continue
            }
            val prop = source.getProperty(normalizedKey, targetType)
            if (prop != null) {
                return prop
            }
        }
        return null
    }

    override fun getPropertyAsStringAtScope(
        key: String,
        scope: ConfigLevel,
    ): String? = getPropertyAtScope(key, String::class, scope)

    override fun getAllProperties(): Map<String, Any> = this.getProperties(null, false)

    override fun getSubProperties(
        prefixes: Set<String>,
        stripPrefix: Boolean,
    ): Map<String, Any> = this.getProperties(prefixes, stripPrefix)

    private fun getProperties(
        prefixes: Set<String>? = null,
        stripPrefix: Boolean = true,
    ): Map<String, Any> {
        val normalizedPrefixes = prefixes?.map { keyNormalizer.normalize(it) }?.toSet()
        val result = mutableMapOf<String, Any>()
        for (source in orderedSources()) {
            if (!source.isPlatformSupported) {
                continue
            }
            for (propertyName in source.getAllPropertyNames()) {
                // Check prefix matching with dot boundary to avoid partial matches
                // (e.g., "app" should not match "application.name")
                if (!normalizedPrefixes.isNullOrEmpty()) {
                    val matchingPrefix =
                        normalizedPrefixes.firstOrNull { prefix ->
                            propertyName.startsWith("$prefix.") || propertyName == prefix
                        }
                    if (matchingPrefix == null) {
                        continue
                    }
                }
                val resultName =
                    if (!normalizedPrefixes.isNullOrEmpty()) {
                        stripPrefix(propertyName, normalizedPrefixes, stripPrefix)
                    } else {
                        propertyName
                    }
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

    /**
     * Returns all properties with source attribution in the value string.
     *
     * Output format per entry: `"value  [source: yaml.app, scope: APP]"`
     *
     * This is a diagnostic method — use [getAllPropertiesAsString] for programmatic access
     * to plain values.
     */
    fun getAllPropertiesWithAttribution(redact: Boolean = true): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (source in orderedSources()) {
            if (!source.isPlatformSupported) {
                continue
            }
            val scope =
                if (source is ScopedPropertySource<*>) {
                    source.configLevel
                } else {
                    ConfigLevel.APP
                }
            for (propertyName in source.getAllPropertyNames()) {
                if (result.containsKey(propertyName)) {
                    continue
                }
                source.getProperty(propertyName, Any::class)?.let { value ->
                    val displayValue = redactIfNeeded(propertyName, value, redact, redactionPolicy = redactionPolicy)
                    result[propertyName] = "$displayValue  [source: ${source.getName()}, scope: ${scope.name}]"
                }
            }
        }
        return result
    }

    /**
     * Returns the list of active property sources in resolution order.
     *
     * Each entry contains the source name, order, and scope.
     * Useful for diagnosing which sources are registered and their priority.
     */
    fun getActiveSourcesSummary(): List<String> =
        orderedSources()
            .filter { it.isPlatformSupported }
            .map { source ->
                val scope =
                    if (source is ScopedPropertySource<*>) {
                        source.configLevel.name
                    } else {
                        "APP"
                    }
                "${source.getName()} (order=${source.getOrder()}, scope=$scope, keys=${source.getAllPropertyNames().size})"
            }

    /**
     * Resolve a single property and return its value with source attribution.
     *
     * @return `"value  [source: name, scope: SCOPE]"` or null if not found
     */
    fun resolveWithAttribution(
        key: String,
        redact: Boolean = true,
    ): String? {
        val normalizedKey = keyNormalizer.normalize(key)
        for (source in orderedSources()) {
            if (!source.isPlatformSupported) {
                continue
            }
            val value = source.getProperty(normalizedKey, Any::class) ?: continue
            val scope =
                if (source is ScopedPropertySource<*>) {
                    source.configLevel
                } else {
                    ConfigLevel.APP
                }
            val displayValue = redactIfNeeded(normalizedKey, value, redact, redactionPolicy = redactionPolicy)
            return "$displayValue  [source: ${source.getName()}, scope: ${scope.name}]"
        }
        return null
    }

    protected fun stripPrefix(
        key: String,
        prefixes: Set<String>,
        stripPrefix: Boolean,
    ): String {
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
// @Inject
@ContributesIntoSet(AppScope::class, binding = binding<IPropertyValueConversion<*>>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("StringPropertyValueConverterImpl", exact = true)
class StringPropertyValueConverterImpl : IPropertyValueConversion<String> {
    override fun supports(value: Any): Boolean =
        when (value) {
            is Boolean -> true
            is Number -> true
            is String -> true
            else -> false
        }

    override fun convert(value: Any): String =
        when (value) {
            is String -> value
            else -> value.toString()
        }
}

@Inject
@SingleIn(AppScope::class)
// @ContributesIntoSet(AppScope::class, binding = binding<IPropertyValueConversion<*>>()) // TODO: Currently throws an exception: Caused by: java.lang.IllegalStateException: KSType 'IPropertyValueConversion<*>' has type arguments, which are not supported for ClassName conversion. Use KSType.toTypeName().
// 	at com.squareup.kotlinpoet.ksp.KsTypesKt.toClassName(KsTypes.kt:44)
// 	at software.amazon.lastmile.kotlin.inject.anvil.processor.ContributesBindingProcessor$GeneratedFunction.bindingMethodReturnType_delegate$lambda$0(ContributesBindingProcessor.kt:303)
// 	at kotlin.SynchronizedLazyImpl.getValue(LazyJVM.kt:83)
// 	at software.amazon.lastmile.kotlin.inject.anvil.processor.ContributesBindingProcessor$GeneratedFunction.getBindingMethodReturnType(ContributesBindingProcessor.kt:302)
// 	at software.amazon.lastmile.kotlin.inject.anvil.processor.ContributesBindingProcessor.generateComponentInterface(ContributesBindingProcessor.kt:103)
// 	at software.amazon.lastmile.kotlin.inject.anvil.processor.ContributesBindingProcessor.process(ContributesBindingProcessor.kt:81)
// 	at software.amazon.lastmile.kotlin.inject.anvil.CompositeSymbolProcessor.process(CompositeSymbolProcessor.kt:17)
// 	at com.google.devtools.ksp.impl.KotlinSymbolProcessing$execute$1$1.invoke(KotlinSymbolProcessing.kt:579)
@OptIn(ExperimentalObjCName::class)
@ObjCName("NumberPropertyValueConverterImpl", exact = true)
class NumberPropertyValueConverterImpl : IPropertyValueConversion<Number> {
    override fun supports(value: Any): Boolean =
        when (value) {
            is Boolean -> true
            is Number -> true
            is String -> true
            else -> false
        }

    override fun convert(value: Any): Number =
        when (value) {
            is Number -> {
                value
            }

            is ULong -> {
                value.toLong()
            }

            is UInt -> {
                value.toInt()
            }

            is UShort -> {
                value.toShort()
            }

            is Boolean -> {
                if (value) {
                    1
                } else {
                    0
                }
            }

            is Char -> {
                value.code.toLong()
            }

            is String -> {
                value.toLongOrNull() ?: throw IllegalArgumentException("Cannot convert string '$value' to Number: invalid numeric format")
            }

            else -> {
                throw IllegalArgumentException("Cannot convert value: $value to Number")
            }
        }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("IPropertyValueConversion", exact = true)
interface IPropertyValueConversion<T : Any> {
    fun supports(value: Any): Boolean

    fun convert(value: Any): T
}

/**
 * Exposes [PropertyResolver] as an optional graph accessor so that consumers declaring
 * `PropertyResolver? = null` constructor parameters resolve cleanly under the Metro
 * `nullable type key`. No IDK supplier publishes a Metro binding for [PropertyResolver]
 * (it is held by [com.sphereon.core.defaults.conf.AbstractConfigEnvironment] and reached
 * via `execution.conf` rather than DI), so consumers receive `null` here. EDK / VDX
 * deployments that DO bind [PropertyResolver] in the Metro graph add a second
 * `@ContributesBinding(AppScope::class, binding = binding<PropertyResolver?>())` so this
 * default `null` body is overridden whenever a real binding is present.
 */
@ContributesTo(AppScope::class)
interface PropertyResolverOptionalProvider {
    @OptionalBinding
    val optionalPropertyResolver: PropertyResolver? get() = null
}
