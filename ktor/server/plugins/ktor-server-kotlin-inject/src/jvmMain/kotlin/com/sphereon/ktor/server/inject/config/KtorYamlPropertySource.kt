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

package com.sphereon.ktor.server.inject.config

import com.sphereon.core.api.conf.AbstractPropertySource
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigEnvironment
import com.sphereon.core.api.conf.PropertyKeyNormalizerImpl
import com.sphereon.core.api.conf.PropertySource
import com.sphereon.core.api.conf.TenantConfigEnvironment
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.core.api.log.LogManager
import com.sphereon.core.api.log.UserContextLogManager
import com.sphereon.di.Order
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.Inject
import org.yaml.snakeyaml.Yaml
import software.amazon.app.platform.scope.Scope
import software.amazon.app.platform.scope.Scoped
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet
import java.io.File
import java.io.FileNotFoundException
import kotlin.reflect.KClass

/**
 * Property source that reads YAML configuration files for Ktor servers.
 * This provides equivalent functionality to Spring Boot's YAML configuration for Ktor-based servers.
 *
 * The property source:
 * - Reads application.yml from working directory or classpath
 * - Supports hierarchical property access (e.g., "database.app.default.host")
 * - Uses the same `sphereon.app.` / `sphereon.tenant.` prefix pattern as Spring Boot
 * - Flattens nested YAML to dot-notation keys
 *
 * @property configLevel The configuration level (APP, TENANT, PRINCIPAL)
 * @property tenantId Optional tenant ID for tenant-scoped configuration
 * @property principalId Optional principal ID for principal-scoped configuration
 */
interface KtorYamlPropertySource : PropertySource<Map<String, Any>>

/**
 * App-level Ktor YAML property source.
 * Registered with the AppConfigEnvironment via [KtorYamlAppPropertySourceContribution].
 */
interface KtorYamlAppPropertySource : KtorYamlPropertySource {
    @ContributesTo(AppScope::class)
    interface Component {
        val ktorYamlAppPropertySource: KtorYamlAppPropertySource
    }
}

/**
 * Tenant-level Ktor YAML property source.
 * Registers itself with the TenantConfigEnvironment on construction.
 */
interface KtorYamlTenantPropertySource : KtorYamlPropertySource {
    @ContributesTo(UserScope::class)
    interface Component {
        val ktorYamlTenantPropertySource: KtorYamlTenantPropertySource
    }
}

/**
 * Principal-level Ktor YAML property source.
 * Registers itself with the PrincipalConfigEnvironment on construction.
 */
interface KtorYamlPrincipalPropertySource : KtorYamlPropertySource {
    @ContributesTo(UserScope::class)
    interface Component {
        val ktorYamlPrincipalPropertySource: KtorYamlPrincipalPropertySource
    }
}

@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<KtorYamlPropertySource>())
@ContributesBinding(AppScope::class, binding = binding<KtorYamlAppPropertySource>())
class KtorYamlAppPropertySourceImpl(
    private val appLogManager: AppLogManager
) : KtorYamlAppPropertySource, AbstractKtorYamlPropertySource(
    name = "ktor-yaml-app",
    logManager = appLogManager,
    configLevel = ConfigLevel.APP,
    tenantId = null,
    principalId = null
) {
    private val log = appLogManager.withTag("KtorYamlAppPropertySource")
}

@Inject
@SingleIn(UserScope::class)
@ContributesIntoSet(UserScope::class, binding = binding<KtorYamlPropertySource>())
@ContributesBinding(UserScope::class, binding = binding<KtorYamlTenantPropertySource>())
class KtorYamlTenantPropertySourceImpl(
    private val configEnvironment: TenantConfigEnvironment,
    private val userInstance: UserContextInstance,
    private val userContextLogManager: UserContextLogManager
) : KtorYamlTenantPropertySource, Scoped, AbstractKtorYamlPropertySource(
    name = "ktor-yaml-tenant",
    logManager = userContextLogManager,
    configLevel = ConfigLevel.TENANT,
    tenantId = userInstance.context.tenant.tenantId,
    principalId = null
) {
    private val log = userContextLogManager.withTag("KtorYamlTenantPropertySource")

    init {
        log.debug("Constructor called for tenant=${userInstance.context.tenant.tenantId}")
    }

    override fun onEnterScope(scope: Scope) {
        log.debug("onEnterScope: Registering with TenantConfigEnvironment for tenant=${userInstance.context.tenant.tenantId}")
        configEnvironment.getPropertySources(includeParents = false).add(this)
        log.debug("onEnterScope: Registered successfully")
    }

    override fun onExitScope() {
        log.debug("onExitScope: Removing from TenantConfigEnvironment for tenant=${userInstance.context.tenant.tenantId}")
        configEnvironment.getPropertySources(includeParents = false).remove(this)
        log.debug("onExitScope: Removed successfully")
    }
}

@Inject
@SingleIn(UserScope::class)
@ContributesIntoSet(UserScope::class, binding = binding<KtorYamlPropertySource>())
@ContributesBinding(UserScope::class, binding = binding<KtorYamlPrincipalPropertySource>())
class KtorYamlPrincipalPropertySourceImpl(
    private val configEnvironment: PrincipalConfigEnvironment,
    private val userContextInstance: UserContextInstance,
    private val userContextLogManager: UserContextLogManager
) : KtorYamlPrincipalPropertySource, Scoped, AbstractKtorYamlPropertySource(
    name = "ktor-yaml-principal",
    logManager = userContextLogManager,
    configLevel = ConfigLevel.PRINCIPAL,
    tenantId = userContextInstance.context.tenant.tenantId,
    principalId = userContextInstance.context.principal?.toString()
) {
    private val log = userContextLogManager.withTag("KtorYamlPrincipalPropertySource")

    init {
        log.debug("Constructor called for tenant=${userContextInstance.context.tenant.tenantId}, principal=${userContextInstance.context.principal}")
    }

    override fun onEnterScope(scope: Scope) {
        log.debug("onEnterScope: Registering with PrincipalConfigEnvironment for tenant=${userContextInstance.context.tenant.tenantId}, principal=${userContextInstance.context.principal}")
        configEnvironment.getPropertySources(includeParents = false).add(this)
        log.debug("onEnterScope: Registered successfully")
    }

    override fun onExitScope() {
        log.debug("onExitScope: Removing from PrincipalConfigEnvironment for tenant=${userContextInstance.context.tenant.tenantId}, principal=${userContextInstance.context.principal}")
        configEnvironment.getPropertySources(includeParents = false).remove(this)
        log.debug("onExitScope: Removed successfully")
    }
}

/**
 * Abstract base implementation for Ktor YAML property sources.
 *
 * Property resolution uses the same prefix pattern as Spring Boot:
 * - APP: "sphereon.app.<property>"
 * - TENANT: "sphereon.tenant.<tenantId>.<property>"
 * - PRINCIPAL: "sphereon.tenant.<tenantId>.principal.<principalId>.<property>"
 *
 * YAML files are loaded from:
 * 1. Working directory: ./application.yml
 * 2. Config directory: ./config/application.yml
 * 3. Classpath: application.yml resource
 */
abstract class AbstractKtorYamlPropertySource(
    name: String,
    private val configLevel: ConfigLevel,
    logManager: LogManager,
    private val tenantId: String?,
    private val principalId: String?
) : AbstractPropertySource<Map<String, Any>>(
    name = name,
    source = emptyMap(),
    order = Order.MEDIUM.orderValue
), KtorYamlPropertySource {
    private val log = logManager.withTag("KtorYamlPropertySource")
    private val sourceName = name

    private val yamlKeyNormalizer = PropertyKeyNormalizerImpl()

    /**
     * Lazily loaded and flattened properties from YAML file.
     * Properties are stored as dot-notation keys (e.g., "sphereon.app.database.host").
     */
    private val flattenedProperties: Map<String, Any> by lazy {
        loadAndFlattenYaml()
    }

    /**
     * Index mapping normalized keys to their original flattened keys.
     * This resolves the mismatch where the PropertyKeyNormalizer converts hyphens to dots
     * (e.g., "auth-bridge" → "auth.bridge") but YAML keys preserve hyphens.
     */
    private val normalizedKeyIndex: Map<String, String> by lazy {
        flattenedProperties.keys.associateBy { yamlKeyNormalizer.normalize(it) }
    }

    /**
     * Computes the prefix dynamically based on config level and user context.
     * - APP: "sphereon.app."
     * - TENANT: "sphereon.tenant.<tenantId>."
     * - PRINCIPAL: "sphereon.tenant.<tenantId>.principal.<principalId>."
     */
    private val prefix: String
        get() = when (configLevel) {
            ConfigLevel.APP -> "sphereon.app."
            ConfigLevel.TENANT -> tenantId?.let { "sphereon.tenant.$it." } ?: "sphereon.tenant."
            ConfigLevel.PRINCIPAL -> {
                if (tenantId != null && principalId != null) {
                    "sphereon.tenant.$tenantId.principal.$principalId."
                } else {
                    "sphereon.principal."
                }
            }
        }

    override fun hasProperty(name: String): Boolean {
        return lookupValue(name) != null
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getProperty(name: String, targetType: KClass<T>): T? {
        val value = lookupValue(name) ?: return null
        return convertValue(value, targetType)
    }

    override fun getPropertyAsString(name: String): String? {
        return lookupValue(name)?.toString()
    }

    /**
     * Looks up a value by trying direct key match first, then falling back to
     * normalized key index. This handles the case where the PropertyKeyNormalizer
     * has already converted hyphens to dots before calling this source.
     */
    private fun lookupValue(name: String): Any? {
        // Try direct lookup with standard key variations
        for (key in buildLookupKeys(name)) {
            flattenedProperties[key]?.let { return it }
        }
        // Fallback: try normalized key index to resolve hyphen/dot mismatches
        for (key in buildLookupKeys(name)) {
            val normalizedKey = yamlKeyNormalizer.normalize(key)
            val originalKey = normalizedKeyIndex[normalizedKey]
            if (originalKey != null) {
                flattenedProperties[originalKey]?.let { return it }
            }
        }
        return null
    }

    override fun removeProperty(name: String) {
        throw UnsupportedOperationException("YAML property source is read-only")
    }

    override fun getAllPropertyNames(): Set<String> {
        val currentPrefix = prefix
        return flattenedProperties.keys
            .filter { it.startsWith(currentPrefix) }
            .map { it.removePrefix(currentPrefix) }
            .toSet()
    }

    override val isPlatformSupported: Boolean = true

    /**
     * Builds the list of keys to try when looking up a property.
     * Returns keys in priority order:
     * 1. Exact key as provided: "sphereon.app.<property>"
     * 2. Normalized key: "sphereon.app.<normalized-property>" (hyphens → dots)
     */
    private fun buildLookupKeys(propertyName: String): List<String> {
        val keys = mutableListOf<String>()

        // Try exact key first
        keys.add("$prefix$propertyName")

        // Try normalized key (hyphens → dots)
        val normalizedName = propertyName.replace('-', '.')
        if (normalizedName != propertyName) {
            keys.add("$prefix$normalizedName")
        }

        return keys
    }

    /**
     * Converts a value to the target type.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> convertValue(value: Any, targetType: KClass<T>): T? {
        return when {
            targetType.isInstance(value) -> value as T
            targetType == String::class -> value.toString() as T
            targetType == Int::class -> value.toString().toIntOrNull() as T?
            targetType == Long::class -> value.toString().toLongOrNull() as T?
            targetType == Double::class -> value.toString().toDoubleOrNull() as T?
            targetType == Float::class -> value.toString().toFloatOrNull() as T?
            targetType == Boolean::class -> value.toString().toBooleanStrictOrNull() as T?
            else -> value as? T
        }
    }

    /**
     * Loads YAML files and flattens them to dot-notation keys.
     */
    private fun loadAndFlattenYaml(): Map<String, Any> {
        val properties = mutableMapOf<String, Any>()
        val yaml = Yaml()

        // Try loading from multiple locations
        val yamlFiles = listOf(
            "application.yml",
            "config/application.yml"
        )

        for (fileName in yamlFiles) {
            try {
                val file = File(fileName)
                if (file.exists()) {
                    log.debug("Loading YAML from file: ${file.absolutePath}")
                    file.inputStream().use { inputStream ->
                        val yamlData = yaml.load<Map<String, Any>>(inputStream)
                        if (yamlData != null) {
                            flattenMap("", yamlData, properties)
                        }
                    }
                    log.debug("Loaded ${properties.size} properties from $fileName")
                    break // Stop after first successful load
                }
            } catch (e: FileNotFoundException) {
                log.debug("YAML file not found: $fileName")
            } catch (e: Exception) {
                log.warn("Error loading YAML file $fileName: ${e.message}")
            }
        }

        // Try classpath as fallback
        if (properties.isEmpty()) {
            try {
                val classLoader = Thread.currentThread().contextClassLoader ?: this::class.java.classLoader
                classLoader?.getResourceAsStream("application.yml")?.use { inputStream ->
                    log.debug("Loading YAML from classpath: application.yml")
                    val yamlData = yaml.load<Map<String, Any>>(inputStream)
                    if (yamlData != null) {
                        flattenMap("", yamlData, properties)
                    }
                    log.debug("Loaded ${properties.size} properties from classpath")
                }
            } catch (e: Exception) {
                log.debug("No YAML file found on classpath: ${e.message}")
            }
        }

        return properties
    }

    /**
     * Recursively flattens a nested map to dot-notation keys.
     * Example: {"sphereon": {"app": {"database": {"host": "localhost"}}}}
     * Becomes: {"sphereon.app.database.host": "localhost"}
     */
    @Suppress("UNCHECKED_CAST")
    private fun flattenMap(prefix: String, map: Map<String, Any>, result: MutableMap<String, Any>) {
        for ((key, value) in map) {
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            when (value) {
                is Map<*, *> -> flattenMap(fullKey, value as Map<String, Any>, result)
                is List<*> -> {
                    // Handle lists by indexing
                    value.forEachIndexed { index, item ->
                        when (item) {
                            is Map<*, *> -> flattenMap("$fullKey[$index]", item as Map<String, Any>, result)
                            else -> if (item != null) result["$fullKey[$index]"] = item
                        }
                    }
                }
                else -> result[fullKey] = value
            }
        }
    }
}
