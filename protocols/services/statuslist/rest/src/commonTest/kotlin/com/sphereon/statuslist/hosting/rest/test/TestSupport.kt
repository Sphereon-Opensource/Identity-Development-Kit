@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.statuslist.hosting.rest.test

import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.DefaultPropertySources
import com.sphereon.core.api.conf.PropertySource
import com.sphereon.core.api.conf.PropertySources
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.PrincipalType
import com.sphereon.di.context.SecuredTenantContextDetails
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.UserContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.reflect.KClass

const val TEST_TENANT_ID: String = "00000000-0000-0000-0000-000000000001"
const val TEST_PRINCIPAL_ID: String = "test-user-context"

/** Minimal map-backed application configuration for REST boundary tests. */
fun createTestAppConfigService(properties: Map<String, Any>): AppConfigService =
    object : AppConfigService {
        override val level: ConfigLevel = ConfigLevel.APP
        override val configLevel: ConfigLevel = ConfigLevel.APP
        override val parent: ConfigService? = null

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> getProperty(
            key: String,
            targetType: KClass<T>,
            defaultValue: T?,
        ): T? {
            val value = properties[key] ?: return defaultValue
            return when (targetType) {
                Boolean::class -> (value as? Boolean ?: value.toString().toBooleanStrictOrNull()) as? T ?: defaultValue
                Int::class -> (value as? Int ?: value.toString().toIntOrNull()) as? T ?: defaultValue
                Long::class -> (value as? Long ?: value.toString().toLongOrNull()) as? T ?: defaultValue
                String::class -> value.toString() as T
                else -> defaultValue
            }
        }

        override fun getPropertyAsString(
            key: String,
            defaultValue: String?,
        ): String? = properties[key]?.toString() ?: defaultValue

        override fun containsProperty(key: String): Boolean = key in properties

        override fun <T : Any> getRequiredProperty(
            key: String,
            targetType: KClass<T>,
            defaultValue: T?,
        ): T = getProperty(key, targetType, defaultValue) ?: error("Property $key not found")

        override fun getRequiredPropertyAsString(
            key: String,
            defaultValue: String?,
        ): String = getPropertyAsString(key, defaultValue) ?: error("Property $key not found")

        override fun getAllProperties(): Map<String, Any> = properties
        override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = properties.mapValues { it.value.toString() }
        override fun getSubProperties(prefixes: Set<String>, stripPrefix: Boolean): Map<String, Any> = emptyMap()
        override fun getSubPropertiesAsString(prefixes: Set<String>, stripPrefix: Boolean, redact: Boolean): Map<String, String> = emptyMap()
        override fun getNamespace(): String = "test"
        override fun addPropertySource(source: PropertySource<*>): ConfigService = this
        override fun removePropertySource(source: PropertySource<*>): ConfigService = this
        override fun getActiveProfile(): String = "test"
        override fun getAppName(): String = "statuslist-rest-test"
        override fun getConfigLocation(): kotlinx.io.files.Path = kotlinx.io.files.Path(".")
        override fun getPropertySources(includeParents: Boolean): PropertySources = DefaultPropertySources()
    }

/** Test-friendly [SessionExecution] that can be constructed without DI. */
fun createTestSessionExecution(
    tenantId: String = TEST_TENANT_ID,
    sessionId: String = Uuid.random().toString(),
    principalId: String = TEST_PRINCIPAL_ID,
): SessionExecution {
    val tenantContextData =
        object : TenantContextData {
            override val tenantId: String = tenantId
        }
    val userContext =
        object : UserContext {
            override val id: String = principalId
            override val secureDetails: SecuredTenantContextDetails? = null
            override val tenant: TenantContextData = tenantContextData
            override val principal: Any? = null
        }
    val sessionContext =
        object : SessionContext {
            override val sessionId: String = sessionId
            override val context: UserContext = userContext

            override fun isAnonymous(): Boolean = false
        }
    val sessionContextManager =
        object : SessionContextManager {
            override val activeInstance: StateFlow<SessionInstance?> = MutableStateFlow(null)

            override fun getActive() = throw NotImplementedError()

            override fun hasActive() = false

            override fun getById(
                sessionId: String,
                makeActive: Boolean
            ) = null

            override fun hasById(sessionId: String) = false

            override fun activateById(sessionId: String) = false

            override fun listIds() = emptySet<String>()

            override fun createOrGetFromCallbacks(sessionContextProvider: () -> SessionContext) = throw NotImplementedError()

            override fun createOrGetFromId(
                sessionId: String,
                correlationId: String,
                makeActive: Boolean,
                secureDetails: SecuredTenantContextDetails?,
                principalType: PrincipalType
            ) = throw NotImplementedError()

            override fun destroyById(sessionId: String) {}

            override fun destroyAll() {}

            override fun getOrCreateBackgroundService(makeActive: Boolean) = throw NotImplementedError()

            override fun getAnonymous(makeActive: Boolean) = throw NotImplementedError()

            override fun getBackgroundServiceId() = "background"
        }
    val contextConfig =
        object : ContextConfig {
            override val app: AppConfigService get() = throw NotImplementedError()
            override val tenant: TenantConfigService get() = throw NotImplementedError()
            override val principal: PrincipalConfigService get() = throw NotImplementedError()

            override fun conf(level: ConfigLevel) = throw NotImplementedError()
        }
    val sessionLogManager =
        object : SessionLogManager {
            override suspend fun setGlobalConfig(config: LoggerConfig) = this

            override suspend fun getGlobalConfig() = LoggerConfig.Default

            override fun withTagAsync(
                tag: String,
                config: LoggerConfig?
            ) = throw NotImplementedError()

            override fun withTag(
                tag: String,
                config: LoggerConfig?
            ) = throw NotImplementedError()
        }
    val sessionLogService =
        object : SessionLogService {
            override val sessionContext: SessionContext = sessionContext
            override val id: String = sessionId
            override val isEnabled: Boolean = true
            override val scope: IdkScope = IdkScope.SESSION
            override val logManager: SessionLogManager = sessionLogManager

            override suspend fun setConfig(config: LoggerConfig): LogService = this

            override suspend fun getConfig() = LoggerConfig.Default

            override fun executeAsync(message: LogMessage) = Ok(Unit)

            override fun toAsync() = throw NotImplementedError()
        }
    return object : SessionExecution {
        override val sessionContextManager: SessionContextManager = sessionContextManager
        override val sessionContext: SessionContext = sessionContext
        override val log: SessionLogService = sessionLogService
        override val conf: ContextConfig = contextConfig
    }
}
