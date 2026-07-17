/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.conf.theme.client

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
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine

// Lightweight SessionExecution / log stubs for the remote resolver unit tests, mirroring the
// fixture in the oauth2-as rest module. The resolvers only touch `execution.log` (for the
// warn-once misconfiguration path); config flows through the stubbed ThemeClientConfigProvider.

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
) : SessionExecution {
    override val sessionContextManager: SessionContextManager get() = throw NotImplementedError("Not needed for test")
    override val log: SessionLogService = TestNoOpSessionLogService(sessionContext)
    override val conf: ContextConfig = TestNoOpContextConfig()
    override val tenantId: String
        get() = sessionContext.context.tenant.tenantId
}

internal class TestThemeClientConfigProvider(
    private val baseUrl: String?,
) : ThemeClientConfigProvider {
    override fun getConfig(): ThemeClientConfig = ThemeClientConfig(baseUrl = baseUrl)
}

internal class TestHttpClientFactory(
    private val engine: MockEngine,
) : HttpClientFactory {
    override fun createClient(options: HttpClientOptions): HttpClient = HttpClient(engine)

    override fun isSupportedOptions(options: HttpClientOptions): Boolean = true

    override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(HttpClientEngineType.CIO)

    override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
}
