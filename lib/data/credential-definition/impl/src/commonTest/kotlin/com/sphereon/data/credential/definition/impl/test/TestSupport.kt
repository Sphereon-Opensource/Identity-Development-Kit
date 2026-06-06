@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.credential.definition.impl.test

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
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

const val TEST_TENANT_ID: String = "00000000-0000-0000-0000-000000000001"
const val TEST_PRINCIPAL_ID: String = "test-user-context"

internal fun <V, E> IdkResult<V, E>.expectErr(): E = if (this.isErr) this.error else error("expected Err but was Ok($this)")

internal fun <V, E> IdkResult<V, E>.expectOk(): V = if (this.isOk) this.value else error("expected Ok but was Err($this)")

/** Test-friendly SessionExecution that can be constructed without DI. */
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
                makeActive: Boolean
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
