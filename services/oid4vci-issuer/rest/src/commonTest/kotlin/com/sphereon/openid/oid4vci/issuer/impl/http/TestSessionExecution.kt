package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.PropertySource
import com.sphereon.core.api.conf.PropertySources
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.openid.oid4vci.rest.Oid4vciRestConfig
import com.sphereon.openid.oid4vci.rest.Oid4vciRestConfigProvider
import kotlinx.io.files.Path
import kotlin.reflect.KClass

internal class NoOpSessionLogService(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionLogService {
    override val id: String = "test-log"
    override val isEnabled: Boolean = false
    override val scope = com.sphereon.core.api.context.IdkScope.SESSION
    override val logManager: SessionLogManager get() = throw NotImplementedError("Not needed for test")

    override suspend fun setConfig(config: com.sphereon.core.api.log.LoggerConfig): com.sphereon.core.api.log.LogService = this

    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

    override fun toAsync(): AsyncLogService = throw NotImplementedError("Not needed for test")
}

internal object NoOpAppConfigService : AppConfigService {
    override val configLevel: ConfigLevel = ConfigLevel.APP
    override val level: ConfigLevel = ConfigLevel.APP
    override val parent: ConfigService? = null

    override fun addPropertySource(source: PropertySource<*>): ConfigService = this

    override fun removePropertySource(source: PropertySource<*>): ConfigService = this

    override fun getActiveProfile(): String = "test"

    override fun getAppName(): String = "oid4vci-rest-test"

    override fun getConfigLocation(): Path = Path(".")

    override fun getPropertySources(includeParents: Boolean): PropertySources = throw NotImplementedError("Not needed for test")

    override fun containsProperty(key: String): Boolean = false

    override fun <T : Any> getProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T? = defaultValue

    override fun getPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String? =
        when (key) {
            Oid4vciIssuerProtocolHttpAdapter.BASE_PATH_KEY -> "/oid4vci"
            else -> defaultValue
        }

    override fun <T : Any> getRequiredProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T = defaultValue ?: throw IllegalStateException("No value for $key")

    override fun getRequiredPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String = defaultValue ?: throw IllegalStateException("No value for $key")

    override fun getAllProperties(): Map<String, Any> = emptyMap()

    override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = emptyMap()

    override fun getSubProperties(
        prefixes: Set<String>,
        stripPrefix: Boolean,
    ): Map<String, Any> = emptyMap()

    override fun getSubPropertiesAsString(
        prefixes: Set<String>,
        stripPrefix: Boolean,
        redact: Boolean,
    ): Map<String, String> = emptyMap()

    override fun getNamespace(): String = "test"
}

internal object NoOpOid4vciRestConfigProvider : Oid4vciRestConfigProvider {
    override fun getConfig(): Oid4vciRestConfig = Oid4vciRestConfig(externalBaseUrl = null)
}

internal class NoOpContextConfig : ContextConfig {
    override val app: AppConfigService = NoOpAppConfigService
    override val tenant: TenantConfigService get() = throw NotImplementedError("Not needed for test")
    override val principal: PrincipalConfigService get() = throw NotImplementedError("Not needed for test")

    override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("Not needed for test")
}

internal class TestSessionExecution(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionExecution {
    override val sessionContextManager: SessionContextManager get() = throw NotImplementedError("Not needed for test")
    override val log: SessionLogService = NoOpSessionLogService(sessionContext)
    override val conf: ContextConfig = NoOpContextConfig()
}
