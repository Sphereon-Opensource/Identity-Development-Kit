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

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readLine
import com.sphereon.di.Order

import com.sphereon.di.app.AppProperties
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.native.ObjCName
import kotlin.reflect.KClass
import kotlin.reflect.cast


interface ConfigContextIds : AppProperties {
    val serviceId: String?
    val tenantId: String?
    val principalId: String?
}

internal fun supportedScopes(context: ConfigContextIds? = null): List<ConfigContext> {
    // TODO BUILD scope
    val scopes = mutableListOf<ConfigContext>(/*ConfigContext.APP*/)
    /*if (context?.serviceId != null) {
        scopes.add(ConfigContext.SERVICE)
    }
    if (context?.tenantId != null) {
        scopes.add(ConfigContext.TENANT)
    }
    if (context?.principalId != null) {
        scopes.add(ConfigContext.PRINCIPAL)
    }*/
    return scopes.toList()
}

fun supportsScope(context: ConfigContextIds, scope: ConfigContext): Boolean {
    return supportedScopes(context).contains(scope)
}

interface ConfigKeyParts : AppProperties {
    val serviceId: String?
    val tenantId: String?
    val principalId: String?

    companion object {
        const val DELIMITER = "."
    }
}

interface ConfigKey : ConfigKeyParts {
    val key: String
}

fun <T: Any> getConfigValue(scope: ConfigScope = ConfigScope.SERVICE, sources: List<ConfigSource> = listOf(ConfigSource.APP, ConfigSource.PROFILE), key: String, defaultValue: T? = null): T {
    /**
     * app.profile.serviceId.tenantId.principalId.key
     * app.default.serviceId.tenantA.principalId.key.subkey
     *
     *

     *
     * appName.profileName.global.default.default.key.subkey // key.subkey for app appName, with profile profileName, and global scope for everyone (no tenant nor principal)
     * appName.profileName.global.tenantA.default.key.subkey // key.subkey for app appName, with profile profileName, and global scope for all principals in tenantA
     * appName.profileName.global.tenantA.principalX.key.subkey // key.subkey for app appName, with profile profileName, and global scope for principal principalX in tenantA
     *
     * If you would getConfigValue for key.subkey and scope is GLOBAL, and sources PROFILE, TENANT, PRINCIPAL then you would get the value from the above hierarchy. If you would not include PROFILE you would get nothing
     *
     * appName.default.global.default.default.key.subkey // key.subkey for app appName, with profile profileName, and global scope for everyone (no tenant nor principal)
     *
     * This would add a value for key.subkey for the entire app and for everyone, so no profile, tenant or principal is needed.
     */
    return null as T
}

/**
 *  getConfigValue("services.serviceId.key")
 */

@OptIn(ExperimentalObjCName::class)
@ObjCName("ConfigKeyProperties", exact = true)
data class ConfigKeyProperties(
    override val appId: String,
    override val profile: String,
    override val version: String,
    override val serviceId: String? = null,
    override val tenantId: String? = null,
    override val principalId: String? = null,
    override val key: String
) :
    ConfigKey {

    companion object {
        @JsStatic
        fun fromConfigContext(environment: ConfigContextIds, context: ConfigContext, key: String): ConfigKeyProperties {
            val scopes = listOf(context.source)
            val level = context.scope
            return ConfigKeyProperties(
                appId = environment.appId,
                profile = environment.profile,
                version = environment.version,
                serviceId = (level == ConfigScope.SERVICE).let { environment.serviceId },
                tenantId = scopes.contains(ConfigSource.TENANT).let { environment.tenantId },
                principalId = scopes.contains(ConfigSource.PRINCIPAL).let { environment.principalId },
                key = key
            )
        }
    }
    /*constructor(environment: IConfigContextIds, key: String, scopes: List<ConfigContext>) : this(
        appId = environment.appId,
        profile = environment.profile,
        serviceId = scopes.contains(ConfigContext.SERVICE).let { environment.serviceId },
        tenantId = scopes.contains(ConfigContext.TENANT).let { environment.tenantId },
        principalId = scopes.contains(ConfigContext.PRINCIPAL).let { environment.principalId },
        key = key
    )*/


}

/*
fun toConfigKeyProperties(
    key: String,
    sourceKeyDelimiter: String = IConfigKeyParts.DELIMITER,
    environment: IConfigContextIds? = null,
    scopes: List<ConfigContext> = supportedScopes(environment)
): IConfigKey {
    if (environment != null) {
        return ConfigKeyProperties(environment, key.replace(sourceKeyDelimiter, IConfigKeyParts.DELIMITER), scopes)
    }

    // appId and profile are always present no matter the scope
    val length = 2 + scopes.filter { it != ConfigContext.BUILD && it != ConfigContext.APP }.size
    return key.split(IConfigKeyParts.DELIMITER, limit = length).let { parts ->
        if (parts.size < length) {
            throw IllegalArgumentException("Invalid key format: $key. Should be: <appId>.<profile>.<serviceId?>.<tenantId?>.<principalId?>.<key>. Length $length expected, but was ${parts.size}.")
        }
        val hasServiceId = scopes.contains(ConfigContext.SERVICE)
        val serviceIdx = if (hasServiceId) 2 else null
        val tenantIdx = if (hasServiceId) 3 else if (scopes.contains(ConfigContext.TENANT)) 2 else null
        val principalIdx =
            if (scopes.contains(ConfigContext.PRINCIPAL)) (if (tenantIdx == null) throw IllegalArgumentException("Cannot have principal configuration without tenantId") else tenantIdx + 1) else null
        return ConfigKeyProperties(
            appId = parts[0],
            profile = parts[1],
            serviceId = serviceIdx?.let { parts[it] },
            tenantId = tenantIdx?.let { parts[it] },
            principalId = principalIdx?.let { parts[it] },
            key = parts[length].replace(sourceKeyDelimiter, IConfigKeyParts.DELIMITER)
        )
    }
}
*/

fun toConfigKey(keyProperties: ConfigKey, targetKeyDelimiter: String = ConfigKeyParts.DELIMITER, keyOnly: Boolean = false): String {
    if (keyOnly) {
        return keyProperties.key.replace(ConfigKeyParts.DELIMITER, targetKeyDelimiter)
    }
    return listOfNotNull(
        keyProperties.appId,
        keyProperties.profile,
        keyProperties.serviceId,
        keyProperties.tenantId,
        keyProperties.principalId,
        keyProperties.key.replace(ConfigKeyParts.DELIMITER, targetKeyDelimiter)
    ).joinToString(
        targetKeyDelimiter
    )
}

fun interface ConfigValueByKey {
    suspend fun getConfigValue(key: String, defaultValue: Any?, isRequired: Boolean?): Any?
}

fun interface ConfigValueRequiredByKey {
    suspend fun getRequiredConfigValue(key: String): Any
}

fun interface ConfigValueStringByKey {
    suspend fun getConfigValueAsString(key: String, defaultValue: String?, isRequired: Boolean?): String?
}


fun interface ConfigValueBooleanByKey {
    suspend fun getConfigValueAsBool(key: String, defaultValue: Boolean?, isRequired: Boolean?): Boolean
}

interface ConfigurationGetAll {
    suspend fun getConfig(scopes: List<ConfigContext> = listOf(/*ConfigContext.APP, ConfigContext.TENANT*/)): Map<String, Any>
}

private fun <T> resolveConfigValue(value: T?, defaultValue: T?, key: String, isRequired: Boolean?): T? =
    value ?: defaultValue ?: if (isRequired == true) throw IllegalArgumentException("Property $key is required") else null

private fun <T : Any> safeCast(value: Any?, to: KClass<T>): T? {
    return if (value == null) null else {
        if (to.isInstance(value)) to.cast(value) else throw IllegalArgumentException("Cannot cast $value to type ${to.simpleName}")
    }

}

interface ConfigProvider : ConfigContextIds, ConfigValueByKey, ConfigValueRequiredByKey, ConfigValueBooleanByKey, ConfigValueStringByKey, ConfigurationGetAll {
    override suspend fun getRequiredConfigValue(key: String): Any =
        resolveConfigValue(getConfigValue(key, null, true), null, key, true) ?: throw IllegalArgumentException("Property $key is required")

    override suspend fun getConfigValueAsString(key: String, defaultValue: String?, isRequired: Boolean?): String? =
        getConfigValue(key, defaultValue, isRequired)?.let { safeCast(it, String::class) }

    override suspend fun getConfigValueAsBool(key: String, defaultValue: Boolean?, isRequired: Boolean?): Boolean =
        (getConfigValue(key, defaultValue, isRequired)?.let { safeCast(it, Boolean::class) } == true)
}

interface PropertiesConfigProvider : ConfigProvider {
    override suspend fun getConfigValue(key: String, defaultValue: Any?, isRequired: Boolean?): String? =
        resolveConfigValue(getConfig()[key]?.let { safeCast(it, String::class) }, defaultValue?.let { safeCast(it, String::class) }, key, isRequired)
}


interface IConfigResolver : Comparable<IConfigResolver> {
    val order: Order

    val supportedScopes: List<ConfigContext>

    suspend fun resolveConfigForScope(
        configContext: ConfigContextIds,
        scope: ConfigContext = supportedScopes(configContext).lastOrNull() ?: supportedScopes.first(),
    ): Map<String, Any>

    suspend fun resolveConfigs(configContext: ConfigContextIds, scopes: List<ConfigContext> = supportedScopes(configContext)): Map<ConfigContext, Map<String, Any>> {
        scopes.associate { scope -> scope to resolveConfigForScope(configContext, scope) }.let { return it }
    }

    override fun compareTo(other: IConfigResolver): Int {
        if (this === other) return 0
        if (order == other.order) return 1
        return order.compareTo(other.order)
    }
}


@OptIn(ExperimentalObjCName::class)
@ObjCName("MergeConfigResolverImpl", exact = true)
class MergeConfigResolverImpl(private val resolvers: Set<IConfigResolver>, override val order: Order = Order.HIGH, override val supportedScopes: List<ConfigContext>) :
    IConfigResolver {
    override suspend fun resolveConfigForScope(configContext: ConfigContextIds, scope: ConfigContext): Map<String, Any> {
        val configs = resolvers.filter { it.supportedScopes.contains(scope) }.sortedByDescending { it.order }.map { it.resolveConfigForScope(configContext, scope) }
        val result = mutableMapOf<String, Any>()
        return configs.fold(result) { result, config -> result + config; result }
    }
}

abstract class AbstractConfigProvider(val configContext: ConfigContextIds, val configResolvers: List<IConfigResolver>) : PropertiesConfigProvider {
    protected suspend fun mergeResolvedConfigs(configContext: ConfigContextIds, scopeOrder: List<ConfigContext> = supportedScopes(configContext)): Map<String, Any> {
        val configs =
            scopeOrder.associate { scope -> scope to MergeConfigResolverImpl(configResolvers.toSet(), supportedScopes = listOf(scope)).resolveConfigForScope(configContext, scope) }
        return configs.entries.fold(mutableMapOf()) { result, entry -> result + entry.value; result }
    }
}

abstract class PropertyFileConfigResolver(
    val basePath: String = "./config",
    override val order: Order = Order.MEDIUM,
    override val supportedScopes: List<ConfigContext>
) : IConfigResolver {


    override suspend fun resolveConfigForScope(configContext: ConfigContextIds, scope: ConfigContext): Map<String, Any> {
        val parts = mutableListOf<String>(configContext.appId, configContext.profile)
        /*if (scope != ConfigContext.APP) {
            // FIXME: Split up App and Service into a config context and then have scope values: Default, Tenant and Principal, so we can have 6 permutations
            parts.add(configContext.serviceId ?: throw IllegalArgumentException("ServiceId is required for scope $scope"))

            if (scope == ConfigContext.TENANT || scope == ConfigContext.PRINCIPAL) {
                parts.add(configContext.tenantId ?: throw IllegalArgumentException("TenantId is required for scope $scope"))
            }
            if (scope == ConfigContext.PRINCIPAL) {
                parts.add(configContext.principalId ?: throw IllegalArgumentException("PrincipalId is required for scope $scope"))
            }
        }*/
        return readPropertiesFile(basePath, *parts.toTypedArray())
    }

    protected fun readPropertiesFile(base: String, vararg parts: String): Map<String, String> {
        val path = Path(base, *parts)
        val properties = mutableMapOf<String, String>()
        if (!SystemFileSystem.exists(path)) {
            return properties
        }

        SystemFileSystem.source(path).buffered().use { source ->
            while (true) {
                val line = source.readLine()
                if (line == null) {
                    // We use while true with a break to not have to use a var for line which then is initialized before the while and updated at the end of the loop
                    break
                }

                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || trimmedLine.startsWith("!") || trimmedLine.startsWith(";") || trimmedLine.startsWith(":")) {
                    continue
                }

                val keyValue = trimmedLine.split("=", limit = 2)
                if (keyValue.size == 2) {
                    properties[keyValue[0].trim(' ', '"', '\'')] = keyValue[1].trim(' ', '"', '\'') // We trim all whitespace and quotes
                }
            }
        }
        return properties
    }
}


abstract class PropertyFileConfigProvider(configContext: ConfigContextIds, configResolvers: List<IConfigResolver>) : AbstractConfigProvider(configContext, configResolvers) {

    override suspend fun getConfig(scopes: List<ConfigContext>): Map<String, Any> {
        return mergeResolvedConfigs(configContext, scopes)
    }


}
