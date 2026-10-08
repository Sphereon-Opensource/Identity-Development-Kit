package com.sphereon.core.api.session

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
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExecutionScopedCommandAdapterCommandIdTest {
    @Test
    fun `accepts a canonical three-segment command ID`() {
        val command = TestExecutionScopedCommand("core.commands.execute")

        assertEquals("core.commands.execute", command.id)
    }

    @Test
    fun `rejects a two-segment command ID at construction`() {
        assertInvalidCommandId("core.execute")
    }

    @Test
    fun `rejects a four-segment command ID at construction`() {
        assertInvalidCommandId("core.commands.execute.now")
    }

    @Test
    fun `rejects a five-segment command ID at construction`() {
        assertInvalidCommandId("core.commands.execute.right.away")
    }

    private fun assertInvalidCommandId(id: String) {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                TestExecutionScopedCommand(id)
            }

        assertEquals(
            "Invalid command ID format: $id. Format: module.service.command",
            failure.message,
        )
    }

    private class TestExecutionScopedCommand(
        id: String,
        execution: SessionExecution = TestSessionExecution(),
    ) : ExecutionScopedCommandAdapter<Unit, Unit, IdkError>(
            id = id,
            execution = execution,
        ) {
        override suspend fun doExecute(
            args: Unit,
            applyDuring: (Unit) -> Unit,
        ): IdkResult<Unit, IdkError> = Ok(applyDuring(args))
    }

    private class TestSessionExecution(
        override val sessionContext: SessionContext = NoOpSessionContext,
    ) : SessionExecution {
        override val sessionContextManager: SessionContextManager
            get() = throw NotImplementedError("Not needed for construction test")
        override val log: SessionLogService = NoOpSessionLogService(sessionContext)
        override val conf: ContextConfig = NoOpContextConfig
    }

    private class NoOpSessionLogService(
        override val sessionContext: SessionContext,
    ) : SessionLogService {
        override val id: String = "test-command-id-log"
        override val isEnabled: Boolean = false
        override val scope: IdkScope = IdkScope.SESSION
        override val logManager: SessionLogManager
            get() = throw NotImplementedError("Not needed for construction test")

        override suspend fun setConfig(config: LoggerConfig): LogService = this

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

        override fun toAsync(): AsyncLogService = throw NotImplementedError("Not needed for construction test")
    }

    private object NoOpContextConfig : ContextConfig {
        override val app: AppConfigService
            get() = throw NotImplementedError("Not needed for construction test")
        override val tenant: TenantConfigService
            get() = throw NotImplementedError("Not needed for construction test")
        override val principal: PrincipalConfigService
            get() = throw NotImplementedError("Not needed for construction test")

        override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("Not needed for construction test")
    }
}
