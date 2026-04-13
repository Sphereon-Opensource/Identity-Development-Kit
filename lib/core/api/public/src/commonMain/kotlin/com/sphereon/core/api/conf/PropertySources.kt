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

import com.sphereon.di.HasOrder
import com.sphereon.di.Order
import com.sphereon.di.SortOrder
import com.sphereon.di.context.TenantContextData
import kotlinx.io.files.Path
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.reflect.KClass

@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertySource", exact = true)
interface PropertySource<T> : HasOrder, Comparable<PropertySource<*>> {
    fun hasProperty(name: String): Boolean
    fun <T : Any> getProperty(name: String, targetType: KClass<T>): T?
    fun getPropertyAsString(name: String): String?
    fun removeProperty(name: String)
    fun getName(): String
    fun getSource(): T

    fun getAllPropertyNames(): Set<String>

    val isPlatformSupported: Boolean
}

/**
 * Property source that is explicitly scoped to a configuration level.
 *
 * This enables true scope-aware resolution (APP/TENANT/PRINCIPAL) when
 * resolving properties or interpolating `${scope:key}` placeholders.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScopedPropertySource", exact = true)
interface ScopedPropertySource<T> : PropertySource<T> {
    val configLevel: ConfigLevel
}

/**
 * Type-safe shortcut for `getProperty(name, R::class)`: callers can write
 * `val n: Int? = src.getProperty("key")`.
 * Returns the value as generic type [R]
 */
inline fun <reified R : Any> PropertySource<*>.getProperty(name: String): R? = getProperty(name, R::class)

abstract class AbstractPropertySource<T>(private val name: String, private val source: T, private val order: Int) : PropertySource<T> {
    protected val keyNormalizer: PropertyKeyNormalizer = PropertyKeyNormalizerImpl()
    override fun hasProperty(name: String): Boolean {
        return getProperty(name, Any::class) != null
    }

    override fun getName(): String {
        return name
    }

    override fun getSource(): T {
        return source
    }

    override fun getOrder(): Int {
        return order
    }

    override fun compareTo(other: PropertySource<*>): Int {
        return this.getOrder().compareTo(other.getOrder())
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("MutableMapPropertySource", exact = true)
open class MutableMapPropertySource(name: String, source: MutableMap<String, Any> = mutableMapOf(), order: Int = Order.MEDIUM.orderValue) :
    AbstractPropertySource<MutableMap<String, Any>>(name, source, order) {

    fun addProperties(map: Map<String, Any>) = apply { map.forEach { addProperty(it.key, it.value) } }
    fun addProperty(name: String, value: Any) = apply {
        this.getSource()[keyNormalizer.normalize(name)] = value
    }

    fun deleteProperty(name: String) = apply { getSource().remove(keyNormalizer.normalize(name)) }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getProperty(name: String, targetType: KClass<T>): T? {
        val value = this.getSource()[keyNormalizer.normalize(name)] ?: return null
        if (!targetType.isInstance(value)) {
            throw IllegalArgumentException("$value is not of type ${targetType.simpleName} but type ${value::class.simpleName}")
        }
        return value as T?
    }

    override fun getPropertyAsString(name: String): String? {
        val value = this.getSource()[keyNormalizer.normalize(name)] ?: return null
        return "$value"
    }

    override fun removeProperty(name: String) {
        this.getSource().remove(name)
    }

    override fun getAllPropertyNames(): Set<String> = this.getSource().keys.map { keyNormalizer.normalize(it) }.toSet()

    override val isPlatformSupported: Boolean = true

}

@OptIn(ExperimentalObjCName::class)
@ObjCName("MapPropertySource", exact = true)
open class MapPropertySource(name: String, source: Map<String, Any>, order: Int = Order.MEDIUM.orderValue) :
    AbstractPropertySource<Map<String, Any>>(name, source, order) {

    override fun hasProperty(name: String): Boolean = getSource().keys.contains(keyNormalizer.normalize(name))

    override fun <T : Any> getProperty(name: String, targetType: KClass<T>): T? {
        val value = this.getSource()[keyNormalizer.normalize(name)] ?: return null
        if (!targetType.isInstance(value)) {
            throw IllegalArgumentException("$value is not of type ${targetType.simpleName} but type ${value::class.simpleName}")
        }
        @Suppress("UNCHECKED_CAST")
        return value as T?
    }

    override fun getPropertyAsString(name: String): String? {
        val value = this.getSource()[keyNormalizer.normalize(name)] ?: return null
        return "$value"
    }

    override fun removeProperty(name: String) {
        throw UnsupportedOperationException("removeProperty is not supported on MapPropertySource because it's not mutable ")
    }

    override fun getAllPropertyNames(): Set<String> = this.getSource().keys.map { keyNormalizer.normalize(it) }.toSet()

    override val isPlatformSupported: Boolean = true
}

@AssistedFactory
interface PropertiesFilePropertySourceFactory {
    fun createPropertiesFileSource(name: String, source: Path): PropertiesFilePropertySource
}

@AssistedInject
class PropertiesFilePropertySource(@Assisted name: String, @Assisted source: Path) : MapPropertySource(name, readPropertiesFromPath(source))

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class EnvPropertySource : MapPropertySource(
    name = "environment",
    source = emptyMap(), // Source is lazily populated - actual data comes from lazy fields
    order = Order.HIGHEST.orderValue
) {
    // Lazy loading of normalized environment variables
    private val envMap: Map<String, Any> by lazy {
        Env.getAll().map { keyNormalizer.normalize(it.key.lowercase()) to it.value }.toMap()
    }

    // Mapping from normalized key back to original env key for fallback lookup
    private val keyMapping: Map<String, String> by lazy {
        Env.getAll().entries.associate { (key, _) -> keyNormalizer.normalize(key.lowercase()) to key }
    }

    override fun hasProperty(name: String): Boolean {
        val normalizedKey = keyNormalizer.normalize(name)
        return envMap.containsKey(normalizedKey) || keyMapping.containsKey(normalizedKey)
    }

    override fun getPropertyAsString(name: String): String? {
        return getProperty(name, String::class)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getProperty(name: String, targetType: KClass<T>): T? {
        val normalizedKey = keyNormalizer.normalize(name)

        // First check our lazy-loaded normalized map
        val value = envMap[normalizedKey]
        if (value != null) {
            return value as T?
        }

        // If not found, try the original env key via keyMapping
        val originalKey = keyMapping[normalizedKey]
        if (originalKey != null) {
            return Env.get(originalKey) as T?
        }

        // Final fallback: direct lookup (for platforms that don't support getAll)
        return (Env.get(normalizedKey) ?: Env.get(name)) as T?
    }

    override fun getAllPropertyNames(): Set<String> = envMap.keys

    override val isPlatformSupported: Boolean = true
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("StaticEnvProprtySourceObject", exact = true)
object StaticEnvPropertySourceObject : PropertySource<Map<String, Any>> by ProtectedEnvPropertySource()

@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertySources", exact = true)
interface PropertySources : MutableIterable<PropertySource<*>> {
    /**
     * Structural revision of the source set.
     *
     * Increments whenever sources are added or removed.
     */
    val revision: Long

    fun add(source: PropertySource<*>): PropertySources

    fun remove(source: PropertySource<*>): PropertySources
    fun contains(name: String): Boolean
    fun get(name: String): PropertySource<*>?

    fun copy(additionalSources: PropertySources? = null): PropertySources
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultAppMapPropertySource", exact = true)
object DefaultAppMapPropertySource : ProtectedMutableMapPropertySource(
    sourceName = DefaultAppMapPropertySource.NAME,
    sourceLevel = ConfigLevel.APP,
    order = Order.MEDIUM.orderValue
) {
    const val NAME = "default-app-map"

    override fun addProperty(name: String, value: Any): ProtectedMutableMapPropertySource = apply {
        super.addProperty(name, value)
        DefaultMapPropertySourceSyncRegistry.mirrorAdd(ConfigLevel.APP, name, value)
    }

    override fun addProperties(map: Map<String, Any>): ProtectedMutableMapPropertySource = apply {
        super.addProperties(map)
        DefaultMapPropertySourceSyncRegistry.mirrorAddAll(ConfigLevel.APP, map)
    }

    override fun addProtectedProperty(
        name: String,
        value: Any,
        protection: PropertyProtection
    ): ProtectedMutableMapPropertySource = apply {
        super.addProtectedProperty(name, value, protection)
        DefaultMapPropertySourceSyncRegistry.mirrorAddProtected(ConfigLevel.APP, name, value, protection)
    }

    override fun deleteProperty(name: String): ProtectedMutableMapPropertySource = apply {
        super.deleteProperty(name)
        DefaultMapPropertySourceSyncRegistry.mirrorDelete(ConfigLevel.APP, name)
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultTenantMapPropertySource", exact = true)
object DefaultTenantMapPropertySource : ProtectedMutableMapPropertySource(
    sourceName = DefaultTenantMapPropertySource.NAME,
    sourceLevel = ConfigLevel.TENANT,
    order = Order.MEDIUM.orderValue
) {
    const val NAME = "default-tenant-map"

    override fun addProperty(name: String, value: Any): ProtectedMutableMapPropertySource = apply {
        super.addProperty(name, value)
        DefaultMapPropertySourceSyncRegistry.mirrorAdd(ConfigLevel.TENANT, name, value)
    }

    override fun addProperties(map: Map<String, Any>): ProtectedMutableMapPropertySource = apply {
        super.addProperties(map)
        DefaultMapPropertySourceSyncRegistry.mirrorAddAll(ConfigLevel.TENANT, map)
    }

    override fun addProtectedProperty(
        name: String,
        value: Any,
        protection: PropertyProtection
    ): ProtectedMutableMapPropertySource = apply {
        super.addProtectedProperty(name, value, protection)
        DefaultMapPropertySourceSyncRegistry.mirrorAddProtected(ConfigLevel.TENANT, name, value, protection)
    }

    override fun deleteProperty(name: String): ProtectedMutableMapPropertySource = apply {
        super.deleteProperty(name)
        DefaultMapPropertySourceSyncRegistry.mirrorDelete(ConfigLevel.TENANT, name)
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultPrincipalMapPropertySource", exact = true)
object DefaultPrincipalMapPropertySource : ProtectedMutableMapPropertySource(
    sourceName = DefaultPrincipalMapPropertySource.NAME,
    sourceLevel = ConfigLevel.PRINCIPAL,
    order = Order.MEDIUM.orderValue
) {
    const val NAME = "default-principal-map"

    override fun addProperty(name: String, value: Any): ProtectedMutableMapPropertySource = apply {
        super.addProperty(name, value)
        DefaultMapPropertySourceSyncRegistry.mirrorAdd(ConfigLevel.PRINCIPAL, name, value)
    }

    override fun addProperties(map: Map<String, Any>): ProtectedMutableMapPropertySource = apply {
        super.addProperties(map)
        DefaultMapPropertySourceSyncRegistry.mirrorAddAll(ConfigLevel.PRINCIPAL, map)
    }

    override fun addProtectedProperty(
        name: String,
        value: Any,
        protection: PropertyProtection
    ): ProtectedMutableMapPropertySource = apply {
        super.addProtectedProperty(name, value, protection)
        DefaultMapPropertySourceSyncRegistry.mirrorAddProtected(ConfigLevel.PRINCIPAL, name, value, protection)
    }

    override fun deleteProperty(name: String): ProtectedMutableMapPropertySource = apply {
        super.deleteProperty(name)
        DefaultMapPropertySourceSyncRegistry.mirrorDelete(ConfigLevel.PRINCIPAL, name)
    }
}

/**
 * Factory for creating isolated default mutable property sources per scope/context.
 *
 * This avoids hidden cross-context coupling from shared mutable singleton sources.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultMapPropertySourceFactory", exact = true)
object DefaultMapPropertySourceFactory {
    fun app(): ProtectedMutableMapPropertySource = ProtectedMutableMapPropertySource(
        sourceName = DefaultAppMapPropertySource.NAME,
        sourceLevel = ConfigLevel.APP,
        order = Order.MEDIUM.orderValue
    ).also {
        DefaultMapPropertySourceSyncRegistry.register(ConfigLevel.APP, it)
        it.addProperties(DefaultAppMapPropertySource.getSource())
    }

    fun tenant(): ProtectedMutableMapPropertySource = ProtectedMutableMapPropertySource(
        sourceName = DefaultTenantMapPropertySource.NAME,
        sourceLevel = ConfigLevel.TENANT,
        order = Order.MEDIUM.orderValue
    ).also {
        DefaultMapPropertySourceSyncRegistry.register(ConfigLevel.TENANT, it)
        it.addProperties(DefaultTenantMapPropertySource.getSource())
    }

    fun principal(): ProtectedMutableMapPropertySource = ProtectedMutableMapPropertySource(
        sourceName = DefaultPrincipalMapPropertySource.NAME,
        sourceLevel = ConfigLevel.PRINCIPAL,
        order = Order.MEDIUM.orderValue
    ).also {
        DefaultMapPropertySourceSyncRegistry.register(ConfigLevel.PRINCIPAL, it)
        it.addProperties(DefaultPrincipalMapPropertySource.getSource())
    }
}

/**
 * Internal registry that mirrors singleton default-map updates into scoped default-map sources
 * created by [DefaultMapPropertySourceFactory].
 */
private object DefaultMapPropertySourceSyncRegistry : kotlinx.atomicfu.locks.SynchronizedObject() {
    private val appSources = mutableSetOf<ProtectedMutableMapPropertySource>()
    private val tenantSources = mutableSetOf<ProtectedMutableMapPropertySource>()
    private val principalSources = mutableSetOf<ProtectedMutableMapPropertySource>()

    fun register(level: ConfigLevel, source: ProtectedMutableMapPropertySource) {
        kotlinx.atomicfu.locks.synchronized(this) {
            sourcesFor(level).add(source)
        }
    }

    fun mirrorAdd(level: ConfigLevel, name: String, value: Any) {
        kotlinx.atomicfu.locks.synchronized(this) {
            sourcesFor(level).forEach { it.addProperty(name, value) }
        }
    }

    fun mirrorAddAll(level: ConfigLevel, map: Map<String, Any>) {
        kotlinx.atomicfu.locks.synchronized(this) {
            sourcesFor(level).forEach { it.addProperties(map) }
        }
    }

    fun mirrorAddProtected(level: ConfigLevel, name: String, value: Any, protection: PropertyProtection) {
        kotlinx.atomicfu.locks.synchronized(this) {
            sourcesFor(level).forEach { it.addProtectedProperty(name, value, protection) }
        }
    }

    fun mirrorDelete(level: ConfigLevel, name: String) {
        kotlinx.atomicfu.locks.synchronized(this) {
            sourcesFor(level).forEach { it.deleteProperty(name) }
        }
    }

    private fun sourcesFor(level: ConfigLevel): MutableSet<ProtectedMutableMapPropertySource> =
        when (level) {
            ConfigLevel.APP -> appSources
            ConfigLevel.TENANT -> tenantSources
            ConfigLevel.PRINCIPAL -> principalSources
        }
}


@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PropertySources>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("AppPropertySourcesImpl", exact = true)
class AppPropertySourcesImpl :
    DefaultPropertySources(
        sources = mutableListOf(StaticProtectedEnvPropertySourceObject, DefaultMapPropertySourceFactory.app()),
        sorting = SortOrder.ASC
    ) {}

@Inject
@SingleIn(TenantContextData::class)
@ContributesBinding(TenantContextData::class, binding = binding<PropertySources>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("TenantPropertySourcesImpl", exact = true)
class TenantPropertySourcesImpl :
    DefaultPropertySources(
        sources = mutableListOf(StaticProtectedEnvPropertySourceObject, DefaultMapPropertySourceFactory.tenant()),
        sorting = SortOrder.ASC
    ) {}

@Inject
open class DefaultPropertySources(sources: MutableList<PropertySource<*>> = mutableListOf(), val sorting: SortOrder = SortOrder.ASC) : PropertySources {
    private var sources = mutableListOf(*sources.toTypedArray())
    private var _revision: Long = 0L

    override val revision: Long
        get() = _revision


    init {
        sort()
        _revision = 1L
    }

    override fun add(source: PropertySource<*>) = apply {
        sources.add(source)
        sort()
        incrementRevision()
    }

    override fun remove(source: PropertySource<*>): PropertySources = apply {
        if (sources.remove(source)) {
            sort()
            incrementRevision()
        }
    }

    override fun copy(additionalSources: PropertySources?): PropertySources {
        return DefaultPropertySources(
            sources = mutableListOf(*this.sources.toTypedArray(), *additionalSources?.toList()?.toTypedArray() ?: arrayOf()),
            sorting = sorting
        )
    }

    fun removeByName(name: String): Boolean {
        val removed = get(name)?.let { sources.remove(it) } ?: false
        if (removed) {
            sort()
            incrementRevision()
        }
        return removed
    }

    override fun contains(name: String): Boolean {
        return sources.any { it.getName() == name }
    }

    override fun get(name: String): PropertySource<*>? {
        return sources.firstOrNull { it.getName() == name }
    }

    override fun iterator(): MutableIterator<PropertySource<*>> {
        sort()
        return sources.iterator()
    }

    private fun sort() {
        this.sources = when (sorting) {
            SortOrder.ASC -> sources.sortedWith(naturalOrder()).toMutableList()
            SortOrder.DESC -> sources.sortedWith(reverseOrder()).toMutableList()
            else -> mutableListOf(*sources.toTypedArray())
        }
    }

    private fun incrementRevision() {
        _revision++
    }

}
