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
 * completes without actual suspension (true for all placeholder types except
 * secret references backed by async providers).
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("InterpolatingPropertySourcesPropertyResolver", exact = true)
class InterpolatingPropertySourcesPropertyResolver(
    private val propertySources: PropertySources,
    private val interpolator: PropertyInterpolator,
    redactionPolicy: SecretRedactionPolicy = DefaultSecretRedactionPolicy(),
) : AbstractPropertyResolver(redactionPolicy),
    ScopeAwarePropertyResolver {
    private val delegate = PropertySourcesPropertyResolver(propertySources, redactionPolicy)

    override fun containsProperty(key: String): Boolean = delegate.containsProperty(key)

    override fun <T : Any> getProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T? {
        val raw = delegate.getProperty(key, targetType, defaultValue) ?: return null
        return interpolateIfString(raw)
    }

    override fun <T : Any> getPropertyAtScope(
        key: String,
        targetType: KClass<T>,
        scope: ConfigLevel,
    ): T? {
        val raw = delegate.getPropertyAtScope(key, targetType, scope) ?: return null
        return interpolateIfString(raw)
    }

    override fun getPropertyAsStringAtScope(
        key: String,
        scope: ConfigLevel,
    ): String? = getPropertyAtScope(key, String::class, scope)

    override fun getAllProperties(): Map<String, Any> = delegate.getAllProperties().mapValues { (_, value) -> interpolateIfString(value) }

    override fun getSubProperties(
        prefixes: Set<String>,
        stripPrefix: Boolean,
    ): Map<String, Any> = delegate.getSubProperties(prefixes, stripPrefix).mapValues { (_, value) -> interpolateIfString(value) }

    @Suppress("UNCHECKED_CAST")
    private fun <T> interpolateIfString(value: T): T {
        if (value !is String) {
            return value
        }
        if (!interpolator.containsPlaceholders(value)) {
            return value
        }
        val result =
            runBlockingCompat {
                interpolator.interpolate(value, delegate)
            }
        return if (result.isOk) {
            result.value as T
        } else {
            value
        }
    }
}
