/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.ktor.http.client

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

/**
 * Lightweight [SessionExecution] for http-client unit tests (no Metro app graph).
 * [AbstractLogManager] with empty loggers makes [SessionLogManager.withTag] a no-op.
 */
internal class TestNoOpSessionLogManager :
    AbstractLogManager(scope = IdkScope.SESSION),
    SessionLogManager

internal class TestNoOpSessionLogService(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionLogService {
    override val id: String = "http-client-test-log"
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
}
