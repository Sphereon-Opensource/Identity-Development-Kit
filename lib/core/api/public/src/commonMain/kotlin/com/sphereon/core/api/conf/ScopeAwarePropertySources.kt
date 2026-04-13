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
 */

package com.sphereon.core.api.conf

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Wrapper to attach a scope to an existing PropertySource.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScopedPropertySourceWrapper", exact = true)
class ScopedPropertySourceWrapper<T>(
    private val delegate: PropertySource<T>,
    override val configLevel: ConfigLevel
) : ScopedPropertySource<T> {
    override fun hasProperty(name: String): Boolean = delegate.hasProperty(name)
    override fun <T : Any> getProperty(name: String, targetType: kotlin.reflect.KClass<T>): T? =
        delegate.getProperty(name, targetType)
    override fun getPropertyAsString(name: String): String? = delegate.getPropertyAsString(name)
    override fun removeProperty(name: String) = delegate.removeProperty(name)
    override fun getName(): String = delegate.getName()
    override fun getSource(): T = delegate.getSource()
    override fun getAllPropertyNames(): Set<String> = delegate.getAllPropertyNames()
    override val isPlatformSupported: Boolean get() = delegate.isPlatformSupported
    override fun getOrder(): Int = delegate.getOrder()
    override fun compareTo(other: PropertySource<*>): Int = delegate.compareTo(other)
}

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
internal fun sourcesForScope(propertySources: PropertySources, scope: ConfigLevel): List<PropertySource<*>> {
    val list = propertySources.toList()
    val hasScoped = list.any { it is ScopedPropertySource<*> }
    if (!hasScoped) {
        return if (scope == ConfigLevel.APP) list else emptyList()
    }

    val filtered = list.filter {
        when (it) {
            is ScopedPropertySource<*> -> it.configLevel == scope
            else -> scope == ConfigLevel.APP
        }
    }
    return filtered.sortedBy { it.getOrder() }
}

