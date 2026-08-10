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
 */

package com.sphereon.core.api.conf

import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Wrapper to attach a scope to an existing PropertySource.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScopedPropertySourceWrapper", exact = true)
class ScopedPropertySourceWrapper<T>(
    internal val delegateSource: PropertySource<T>,
    override val configLevel: ConfigLevel,
) : ScopedPropertySource<T>,
    ProtectedPropertySource<T>,
    RefreshablePropertySource {
    init {
        if (configLevel != ConfigLevel.APP && delegateSource.isPlatformSupported) {
            delegateSource.getAllPropertyNames().forEach { key ->
                val value =
                    runCatching { delegateSource.getProperty(key, Any::class) }.getOrNull()
                        ?: runCatching { delegateSource.getPropertyAsString(key) }.getOrNull()
                validateEnvironmentReferencesForWrite(value, configLevel)
            }
        }
    }

    override val isPlatformSupported: Boolean get() = delegateSource.isPlatformSupported

    override fun hasProperty(name: String): Boolean {
        val present = delegateSource.hasProperty(name)
        if (present) {
            validateCurrentValue(name)
        }
        return present
    }

    override fun <T : Any> getProperty(
        name: String,
        targetType: kotlin.reflect.KClass<T>,
    ): T? {
        validateCurrentValue(name)
        return delegateSource.getProperty(name, targetType)
    }

    override fun getPropertyAsString(name: String): String? {
        validateCurrentValue(name)
        return delegateSource.getPropertyAsString(name)
    }

    override fun removeProperty(name: String) = delegateSource.removeProperty(name)

    override fun getName(): String = delegateSource.getName()

    @Suppress("UNCHECKED_CAST")
    override fun getSource(): T {
        val source = delegateSource.getSource()
        return if (source is MutableMap<*, *>) {
            source.toMutableMap() as T
        } else {
            source
        }
    }

    override fun getAllPropertyNames(): Set<String> {
        val names = delegateSource.getAllPropertyNames()
        if (configLevel != ConfigLevel.APP) {
            names.forEach(::validateCurrentValue)
        }
        return names
    }

    override fun getOrder(): Int = delegateSource.getOrder()

    override fun compareTo(other: PropertySource<*>): Int = delegateSource.compareTo(other)

    override fun getProtection(canonicalKey: String): PropertyProtection? = (delegateSource as? ProtectedPropertySource<*>)?.getProtection(canonicalKey)

    override fun canSet(
        key: String,
        fromScope: ConfigLevel,
    ): Boolean = (delegateSource as? ProtectedPropertySource<*>)?.canSet(key, fromScope) ?: true

    override fun canInterpolate(
        key: String,
        fromScope: ConfigLevel,
    ): Boolean = (delegateSource as? ProtectedPropertySource<*>)?.canInterpolate(key, fromScope) ?: true

    override val contentRevision: Long
        get() = (delegateSource as? RefreshablePropertySource)?.contentRevision ?: 0L

    override fun refreshIfNeeded() {
        (delegateSource as? RefreshablePropertySource)?.refreshIfNeeded()
    }

    private fun validateCurrentValue(name: String) {
        if (configLevel == ConfigLevel.APP) {
            return
        }
        val value =
            runCatching { delegateSource.getProperty(name, Any::class) }.getOrNull()
                ?: runCatching { delegateSource.getPropertyAsString(name) }.getOrNull()
        validateConfigurationValueForRead(value, configLevel)
    }
}

internal fun PropertySource<*>.unwrappedPropertySource(): PropertySource<*> =
    if (this is ScopedPropertySourceWrapper<*>) {
        delegateSource.unwrappedPropertySource()
    } else {
        this
    }

internal fun PropertySource<*>.isDirectEnvironmentPropertySource(): Boolean {
    val unwrapped = unwrappedPropertySource()
    return unwrapped is EnvPropertySource ||
        unwrapped is ProtectedEnvPropertySource ||
        unwrapped.getName() == "environment" ||
        unwrapped.getName() == "protected-environment"
}

internal fun PropertySource<*>.isDirectlyVisibleAt(level: ConfigLevel): Boolean = level == ConfigLevel.APP || !isDirectEnvironmentPropertySource()

internal fun PropertySource<*>.effectiveSourceScope(fallback: ConfigLevel): ConfigLevel = (this as? ScopedPropertySource<*>)?.configLevel ?: fallback

/**
 * Return property sources ordered by scope first (PRINCIPAL -> TENANT -> APP),
 * then by order within the same scope.
 *
 * If no ScopedPropertySource entries exist, fall back to iteration order.
 */
internal fun orderedSourcesByScope(propertySources: PropertySources): List<PropertySource<*>> {
    val list = propertySources.toList()
    if (list.none { it is ScopedPropertySource<*> }) {
        return list
    }

    fun scopeRank(source: PropertySource<*>): Int {
        val level = (source as? ScopedPropertySource<*>)?.configLevel ?: ConfigLevel.APP
        return when (level) {
            ConfigLevel.PRINCIPAL -> 0
            ConfigLevel.TENANT -> 1
            ConfigLevel.APP -> 2
        }
    }

    return list.sortedWith(compareBy<PropertySource<*>> { scopeRank(it) }.thenBy { it.getOrder() })
}

/**
 * Return property sources limited to a specific scope.
 *
 * If there are no scoped sources, returns all sources for APP scope and none for others.
 */
internal fun sourcesForScope(
    propertySources: PropertySources,
    scope: ConfigLevel,
): List<PropertySource<*>> {
    val list = propertySources.toList()
    val hasScoped = list.any { it is ScopedPropertySource<*> }
    if (!hasScoped) {
        return if (scope == ConfigLevel.APP) {
            list
        } else {
            emptyList()
        }
    }

    val filtered =
        list.filter {
            when (it) {
                is ScopedPropertySource<*> -> it.configLevel == scope
                else -> scope == ConfigLevel.APP
            }
        }
    return filtered.sortedBy { it.getOrder() }
}
