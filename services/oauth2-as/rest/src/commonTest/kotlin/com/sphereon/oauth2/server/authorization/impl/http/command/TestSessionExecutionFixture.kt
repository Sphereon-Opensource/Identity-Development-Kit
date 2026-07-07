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

package com.sphereon.oauth2.server.authorization.impl.http.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AbstractLogManager
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager

// Lightweight [SessionExecution] / [SessionLogService] / [ContextConfig] stubs for the new
// HttpEndpointCommandImpl unit tests. The impls under test never read config or log from
// `execution`; they go through the injected ServiceCommand for everything substantive. Mirrors
// the fixture in `Oid4VpVerifierHttpAdapterRequestUriTest.kt`.

/**
 * No-op [SessionLogManager] used by the test fixture so call sites that route a warning through
 * [com.sphereon.core.api.context.SessionExecution.log] (e.g. `mapOAuth2ErrorToResponse` logging
 * 4xx/5xx error shapes for diagnostic visibility) do not blow up with `NotImplementedError`. The
 * underlying [AbstractLogManager] is constructed with no loggers, so every `withTag(...)` call
 * returns a `MultiLogService` whose `log(...)` is a no-op, which matches the fixture's intent
 * (the impls under test do not assert on log output).
 */
internal class TestNoOpSessionLogManager :
    AbstractLogManager(scope = IdkScope.SESSION),
    SessionLogManager

internal class TestNoOpSessionLogService(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionLogService {
    override val id: String = "test-log"
    override val isEnabled: Boolean = false
    override val scope = IdkScope.SESSION
    override val logManager: SessionLogManager = TestNoOpSessionLogManager()

    override suspend fun setConfig(config: com.sphereon.core.api.log.LoggerConfig): com.sphereon.core.api.log.LogService = this

    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

    override fun toAsync(): AsyncLogService = throw NotImplementedError("Not needed for test")
}

internal class TestNoOpContextConfig : ContextConfig {
    override val app: AppConfigService get() = throw NotImplementedError("Not needed for test")
    override val tenant: TenantConfigService get() = throw NotImplementedError("Not needed for test")
    override val principal: PrincipalConfigService get() = throw NotImplementedError("Not needed for test")

    override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("Not needed for test")
}

internal class TestSessionExecution(
    override val sessionContext: SessionContext = NoOpSessionContext,
    private val tenantIdOverride: String? = null,
) : SessionExecution {
    override val sessionContextManager: SessionContextManager get() = throw NotImplementedError("Not needed for test")
    override val log: SessionLogService = TestNoOpSessionLogService(sessionContext)
    override val conf: ContextConfig = TestNoOpContextConfig()
    override val tenantId: String
        get() = tenantIdOverride ?: sessionContext.context.tenant.tenantId
}

/**
 * In-memory [com.sphereon.oauth2.server.authorization.storage.MutableOidcLoginSessionIdProvider]
 * for AS endpoint command unit tests. Default value is `null` so existing tests that do not care
 * about the cookie pass an unset provider; tests that exercise the cookie-read path can preset
 * a value or read back what the adapter wrote via [currentLoginSessionId].
 */
internal class TestMutableOidcLoginSessionIdProvider(
    initial: String? = null,
) : com.sphereon.oauth2.server.authorization.storage.MutableOidcLoginSessionIdProvider {
    private var sessionId: String? = initial

    override fun currentLoginSessionId(): String? = sessionId

    override fun setCurrentLoginSessionId(sessionId: String) {
        this.sessionId = sessionId
    }

    override fun clearCurrentLoginSessionId() {
        this.sessionId = null
    }
}

internal class TestOAuth2ServersConfigProvider(
    private val config: com.sphereon.oauth2.common.config.OAuth2ServersConfig =
        com.sphereon.oauth2.common.config
            .OAuth2ServersConfig(),
) : com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider {
    override fun getConfig(): com.sphereon.oauth2.common.config.OAuth2ServersConfig = config

    override fun getServer(id: String): com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig? = config.getServer(id)

    override fun getDefaultServer(): com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig = config.getDefaultServer()

    override fun resolveIssuer(
        serverId: String,
        tenantId: String,
    ): String {
        val server =
            config.getServer(serverId)
                ?: error("OAuth2 server '$serverId' not found in configuration")
        return server.issuer
            ?: server.issuerTemplate?.replace("{tenant-id}", tenantId)
            ?: error("OAuth2 server '$serverId' has no issuer or issuerTemplate")
    }
}
