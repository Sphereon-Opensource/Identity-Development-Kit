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

import com.sphereon.core.api.coroutines.runBlockingCompat
import com.sphereon.core.api.log.Log
import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.reflect.KClass

/**
 * A [PropertyResolver] that delegates to [PropertySourcesPropertyResolver] and applies
 * [PropertyInterpolator] to resolve `${...}` placeholders in string values.
 *
 * This is a lightweight alternative to routing through [ConfigResolutionPipeline] —
 * property resolution is fully synchronous via [PropertySourcesPropertyResolver],
 * and only the interpolation step invokes the suspend [PropertyInterpolator.interpolate].
 *
 * On JVM/Native, [runBlockingCompat] delegates to `runBlocking`.
 * On JS/wasmJs, it uses `startCoroutine` which works as long as the interpolation
 * completes without actual suspension. Property interpolation never resolves secrets.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("InterpolatingPropertySourcesPropertyResolver", exact = true)
class InterpolatingPropertySourcesPropertyResolver(
    private val propertySources: PropertySources,
    private val interpolator: PropertyInterpolator,
    redactionPolicy: SecretRedactionPolicy = DefaultSecretRedactionPolicy(),
    override val resolverLevel: ConfigLevel,
    private val interpolationPolicyProvider: InterpolationPolicyProvider = DefaultInterpolationPolicyProvider(),
) : AbstractPropertyResolver(redactionPolicy),
    ScopeAwarePropertyResolver,
    ProtectedPropertyResolver {
    private val protectedDelegate = ProtectedPropertySourcesResolver(propertySources, this.resolverLevel, redactionPolicy)
    private val delegate = protectedDelegate
    private val keyNormalizer = PropertyKeyNormalizerImpl.Default
    private val logger = Log.app().withTag("ConfigurationInterpolation")

    override fun canSetProperty(key: String) = protectedDelegate.canSetProperty(key)

    override fun canInterpolateProperty(
        key: String,
        fromScope: ConfigLevel,
    ) = protectedDelegate.canInterpolateProperty(key, fromScope)

    override fun canReadProperty(
        key: String,
        fromScope: ConfigLevel,
    ) = protectedDelegate.canReadProperty(key, fromScope)

    override fun canInterpolateEnvironment(
        name: String,
        fromScope: ConfigLevel,
    ) = protectedDelegate.canInterpolateEnvironment(name, fromScope)

    override fun getProtection(key: String): PropertyProtection? = protectedDelegate.getProtection(key)

    override fun resolvePropertyWithScope(
        key: String,
        requiredScope: ConfigLevel?,
    ): ResolvedPropertyWithScope? =
        resolveCanonicalPropertyInternal(key, requiredScope)?.toResolvedPropertyWithScope()

    internal fun resolveCanonicalPropertyInternal(
        key: String,
        requiredScope: ConfigLevel?,
    ): ResolvedValue<Any>? {
        val raw = protectedDelegate.resolveCanonicalPropertyInternal(key, requiredScope) ?: return null
        return interpolateCanonical(keyNormalizer.normalize(key), raw)
    }

    internal fun resolveCanonicalPropertiesInternal(prefixes: Set<String>?): Map<String, ResolvedValue<Any>> =
        protectedDelegate
            .resolveCanonicalPropertiesInternal(prefixes)
            .mapValues { (key, raw) ->
                interpolateCanonical(key, raw)
            }

    override fun containsProperty(key: String): Boolean = delegate.containsProperty(key)

    override fun <T : Any> getProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T? =
        resolveCanonicalPropertyInternal(key, requiredScope = null)
            ?.value
            ?.let { coerceCanonicalValue(it, targetType) }
            ?: defaultValue

    override fun <T : Any> getPropertyAtScope(
        key: String,
        targetType: KClass<T>,
        scope: ConfigLevel,
    ): T? =
        resolveCanonicalPropertyInternal(key, requiredScope = scope)
            ?.value
            ?.let { coerceCanonicalValue(it, targetType) }

    /**
     * When the caller asks for a non-String type (Boolean, Int, etc.) and the underlying
     * source stores a String with `${...}` placeholders, the source's strict type check
     * throws before interpolation can run. Probe for a String value first; if it has
     * placeholders, interpolate then coerce.
     *
     * Returns:
     * - A null conversion when this path does not apply (target is
     *   String/Any, no String value at this key, or the value has no placeholders) —
     *   the caller falls back to the standard typed lookup.
     * - The coerced value (possibly null) when the path applied — the caller commits
     *   to that result. Falling back here would re-throw on the same raw template
     *   string the strict type check rejected, which is the bug we're fixing.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> coerceCanonicalValue(
        value: Any,
        targetType: KClass<T>,
    ): T? =
        when (targetType) {
            Any::class -> value
            String::class -> value.toString()
            Boolean::class -> if (value is Boolean) value else (value as? String)?.toBooleanStrictOrNull()
            Int::class -> if (value is Int) value else (value as? String)?.toIntOrNull()
            Long::class -> if (value is Long) value else (value as? String)?.toLongOrNull()
            Double::class -> if (value is Double) value else (value as? String)?.toDoubleOrNull()
            Float::class -> if (value is Float) value else (value as? String)?.toFloatOrNull()
            else -> if (targetType.isInstance(value)) value else null
        } as T?

    override fun getPropertyAsStringAtScope(
        key: String,
        scope: ConfigLevel,
    ): String? = getPropertyAtScope(key, String::class, scope)

    override fun getAllProperties(): Map<String, Any> =
        resolveCanonicalPropertiesInternal().mapValues { (_, resolved) -> resolved.value }

    override fun getSubProperties(
        prefixes: Set<String>,
        stripPrefix: Boolean,
    ): Map<String, Any> {
        // Keep the full property key until after interpolation so the winning scoped source
        // remains available for property-level protection checks.
        val normalizedPrefixes = prefixes.map { keyNormalizer.normalize(it) }.toSet()
        val resolved =
            resolveCanonicalPropertiesInternal(normalizedPrefixes)
                .mapValues { (_, value) -> value.value }
        return if (stripPrefix) stripPrefixes(resolved, normalizedPrefixes) else resolved
    }

    private fun interpolateCanonical(
        key: String,
        raw: ResolvedValue<Any>,
    ): ResolvedValue<Any> {
        val value = raw.value
        if (value !is String || !interpolator.containsPlaceholders(value)) {
            return raw
        }
        rejectForbiddenExternalSource(value)
        val result =
            runBlockingCompat {
                interpolateCanonicalValue(key, raw)
            }
        if (result.isErr) {
            val causeType = result.error::class.simpleName ?: "IdkError"
            val detail =
                result.error.message.defaultMessage
                    .replace('\r', ' ')
                    .replace('\n', ' ')
                    .take(240)
            logger.warn(
                "VDX_CONFIGURATION_INTERPOLATION_DENIED " +
                    "property=${key.configurationLogToken()} cause=$causeType detail=$detail",
            )
            throw IllegalStateException("Configuration interpolation was denied")
        }
        val provenance = result.value.provenance
        return ResolvedValue(
            value = result.value.value,
            metadata =
                raw.metadata.copy(
                    scope = provenance.sourceScope ?: raw.metadata.scope,
                    isSecret = provenance.hasTaint(ResolutionTaint.SENSITIVE),
                    isInterpolated = true,
                    provenance = provenance,
                ),
        )
    }

    private fun String.configurationLogToken(): String =
        replace(Regex("[^A-Za-z0-9._\\-\\[\\]]"), "_").take(200).ifBlank { "unknown" }

    private suspend fun interpolateCanonicalValue(
        key: String,
        resolved: ResolvedValue<Any>,
    ): com.sphereon.core.api.IdkResult<InterpolatedPropertyValue, com.sphereon.core.api.error.IdkError> {
        val rawValue = resolved.value as? String
            ?: return com.sphereon.core.api.Err(
                ConfigErrors.interpolationError(
                    key = "property",
                    reason = "property value is not interpolatable",
                ),
            )
        val interpolationResolver = ProtectedPropertySourcesResolver(propertySources, resolved.metadata.scope, redactionPolicy)
        return interpolator.interpolateWithProvenance(
            value = rawValue,
            resolver = interpolationResolver,
            requestingScope = resolved.metadata.scope,
            maxDepth = null,
            policy = interpolationPolicyProvider.policyFor(key, resolved.metadata.scope),
            sourceProvenance = resolved.metadata.provenance,
        )
    }

    private fun rejectForbiddenExternalSource(value: String) {
        if (forbiddenExternalReferenceError(value) != null) {
            throw IllegalStateException("External secret-provider references are forbidden in configuration")
        }
    }

    private fun stripPrefixes(
        props: Map<String, Any>,
        prefixes: Set<String>,
    ): Map<String, Any> =
        props.mapKeys { (key, _) ->
            val normalizedKey = keyNormalizer.normalize(key)
            prefixes
                .firstOrNull { normalizedKey == it || normalizedKey.startsWith("$it.") }
                ?.let { normalizedKey.removePrefix(it).removePrefix(".") }
                ?: key
        }
}
