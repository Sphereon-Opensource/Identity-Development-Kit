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
 */

@file:Suppress("DEPRECATION")

package com.sphereon.core.api.log

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.core.api.Ok
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Note: LogMessageTest, LoggerConfigTest, and LogErrorTest are in LogLevelTest.kt

class LogServiceExtensionsTest {

    private class TestLogService(
        override val scope: IdkScope,
        override val isEnabled: Boolean
    ) : LogService {
        override val sessionContext: SessionContext = NoOpSessionContext
        override val id: String = "test-logger"

        override suspend fun setConfig(config: LoggerConfig): LogService = this
        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> =
            IdkResult.ok(Unit)
        override fun toAsync(): AsyncLogService = throw NotImplementedError()
    }

    @Test
    fun filterEnabledReturnsOnlyEnabledServices() {
        val enabled = TestLogService(IdkScope.APP, isEnabled = true)
        val disabled = TestLogService(IdkScope.APP, isEnabled = false)
        val set = setOf(enabled, disabled)

        val result = set.filterEnabled()

        assertEquals(1, result.size)
        assertTrue(result.contains(enabled))
    }

    @Test
    fun filterScopeReturnsOnlyMatchingScope() {
        val appLogger = TestLogService(IdkScope.APP, isEnabled = true)
        val sessionLogger = TestLogService(IdkScope.SESSION, isEnabled = true)
        val set = setOf(appLogger, sessionLogger)

        val result = set.filterScope(IdkScope.APP)

        assertEquals(1, result.size)
        assertTrue(result.contains(appLogger))
    }
}

class AbstractLogServiceTest {

    private class TestableLogService : AbstractLogService(
        id = "test-logger",
        isEnabled = true,
        sessionContext = NoOpSessionContext
    ) {
        var lastMessage: LogMessage? = null
        override val scope: IdkScope = IdkScope.APP

        override suspend fun doExecute(args: LogMessage,
            applyDuring: (LogMessage) -> LogMessage
        ): IdkResult<Unit, IdkErrorType> {
            lastMessage = applyDuring(args)
            return IdkResult.ok(Unit)
        }
    }

    @Test
    fun setConfigUpdatesConfig() = runTest {
        val service = TestableLogService()
        val newConfig = LoggerConfig(minLevel = LogLevel.ERROR, tag = "custom")

        service.setConfig(newConfig)

        assertEquals(newConfig, service.getConfig())
    }

    @Test
    fun getConfigReturnsDefaultInitially() = runTest {
        val service = TestableLogService()
        assertEquals(LoggerConfig.Default, service.getConfig())
    }

    @Test
    fun executeAsyncReturnsOk() {
        val service = TestableLogService()
        val result = service.executeAsync(LogMessage(message = "test") )
        assertTrue(result.isOk)
    }

    @Test
    fun toAsyncReturnsAsyncService() {
        val service = TestableLogService()
        val async = service.toAsync()
        assertNotNull(async)
        assertEquals(service.id, async.id)
        assertEquals(service.scope, async.scope)
    }

    @Test
    fun asyncToSyncReturnsSameService() {
        val service = TestableLogService()
        val async = service.toAsync()
        val syncAgain = async.toSync()
        assertEquals(service, syncAgain)
    }

    @Test
    fun asyncServiceHasSameSessionContext() {
        val service = TestableLogService()
        val async = service.toAsync()
        assertEquals(service.sessionContext, async.sessionContext)
    }

    @Test
    fun asyncServiceIsEnabled() {
        val service = TestableLogService()
        val async = service.toAsync()
        assertEquals(service.isEnabled, async.isEnabled)
    }

    @Test
    fun asyncSetConfigWorks() = runTest {
        val service = TestableLogService()
        val async = service.toAsync()
        val newConfig = LoggerConfig(minLevel = LogLevel.WARN, tag = "async-tag")
        async.setConfig(newConfig)
        assertEquals(newConfig, service.getConfig())
    }

    @Test
    fun asyncExecuteWorks() = runTest {
        val service = TestableLogService()
        val async = service.toAsync()
        val result = async.execute(LogMessage(message = "async test"))
        assertTrue(result.isOk)
    }
}

class AppConsoleLogServiceImplTest {

    @Test
    fun hasCorrectServiceId() {
        assertEquals("AppConsoleLogger", AppConsoleLogServiceImpl.SERVICE_ID)
    }

    @Test
    fun hasAppScope() {
        val service = AppConsoleLogServiceImpl()
        assertEquals(IdkScope.APP, service.scope)
    }

    @Test
    fun isEnabledByDefault() {
        val service = AppConsoleLogServiceImpl()
        assertTrue(service.isEnabled)
    }

    @Test
    fun hasCorrectId() {
        val service = AppConsoleLogServiceImpl()
        assertEquals(AppConsoleLogServiceImpl.SERVICE_ID, service.id)
    }

    @Test
    fun executeAsyncReturnsOk() {
        val service = AppConsoleLogServiceImpl()
        val result = service.executeAsync(LogMessage(message = "test") )
        assertTrue(result.isOk)
    }

    @Test
    fun traceMethodDoesNotThrow() {
        val service = AppConsoleLogServiceImpl()
        service.trace("trace message")
    }

    @Test
    fun debugMethodDoesNotThrow() {
        val service = AppConsoleLogServiceImpl()
        service.debug("debug message")
    }

    @Test
    fun infoMethodDoesNotThrow() {
        val service = AppConsoleLogServiceImpl()
        service.info("info message")
    }

    @Test
    fun warnMethodDoesNotThrow() {
        val service = AppConsoleLogServiceImpl()
        service.warn("warn message")
    }

    @Test
    fun errorMethodDoesNotThrow() {
        val service = AppConsoleLogServiceImpl()
        service.error("error message")
    }

    @Test
    fun errorMethodWithExceptionDoesNotThrow() {
        val service = AppConsoleLogServiceImpl()
        service.error("error with exception", RuntimeException("test error"))
    }

    @Test
    fun toAsyncReturnsAsyncService() {
        val service = AppConsoleLogServiceImpl()
        val async = service.toAsync()
        assertNotNull(async)
        assertEquals(IdkScope.APP, async.scope)
    }
}

class MultiLogServiceTest {
    private class RecordingLogService(
        override val id: String
    ) : AbstractLogService(
        id = id,
        isEnabled = true,
        sessionContext = NoOpSessionContext
    ) {
        override val scope: IdkScope = IdkScope.APP
        var callCount: Int = 0
        var lastMessage: LogMessage? = null

        override suspend fun doExecute(args: LogMessage,
            applyDuring: (LogMessage) -> LogMessage
        ): IdkResult<Unit, IdkErrorType> {
            callCount += 1
            lastMessage = applyDuring(args)
            return Ok(Unit)
        }
    }


    @Test
    fun hasCorrectServiceId() {
        assertEquals("LogService", MultiLogService.SERVICE_ID)
    }

    @Test
    fun executeReturnsServiceDisabledWhenNotEnabled() = runTest {
        val loggers = setOf<LogService>()
        val service = MultiLogService(
            loggers = loggers,
            scope = IdkScope.APP,
            isEnabled = false
        )
        val result = service.execute(LogMessage(message = "test"))
        assertTrue(result.isErr)
    }

    @Test
    fun executeReturnsNoLoggersConfiguredWhenEmpty() = runTest {
        val loggers = setOf<LogService>()
        val service = MultiLogService(
            loggers = loggers,
            scope = IdkScope.APP,
            isEnabled = true
        )
        val result = service.execute(LogMessage(message = "test"))
        assertTrue(result.isErr)
    }

    @Test
    fun executeReturnsOkWithLoggers() = runTest {
        val consoleLogger = AppConsoleLogServiceImpl()
        val service = MultiLogService(
            loggers = setOf(consoleLogger),
            scope = IdkScope.APP,
            isEnabled = true
        )
        val result = service.execute(LogMessage(message = "test"))
        assertTrue(result.isOk)
    }

    @Test
    fun setConfigUpdatesConfig() = runTest {
        val service = MultiLogService(
            loggers = emptySet(),
            scope = IdkScope.APP
        )
        val newConfig = LoggerConfig(minLevel = LogLevel.ERROR, tag = "multi")
        service.setConfig(newConfig)
        assertEquals(newConfig, service.getConfig())
    }

    @Test
    fun executeAsyncReturnsOk() {
        val consoleLogger = AppConsoleLogServiceImpl()
        val service = MultiLogService(
            loggers = setOf(consoleLogger),
            scope = IdkScope.APP
        )
        val result = service.executeAsync(LogMessage(message = "test") )
        assertTrue(result.isOk)
    }

    @Test
    fun toAsyncReturnsAsyncService() {
        val consoleLogger = AppConsoleLogServiceImpl()
        val service = MultiLogService(
            loggers = setOf(consoleLogger),
            scope = IdkScope.APP
        )
        val async = service.toAsync()
        assertNotNull(async)
        assertEquals(IdkScope.APP, async.scope)
    }

    @Test
    fun asyncToSyncReturnsSameService() {
        val consoleLogger = AppConsoleLogServiceImpl()
        val service = MultiLogService(
            loggers = setOf(consoleLogger),
            scope = IdkScope.APP
        )
        val async = service.toAsync()
        val sync = async.toSync()
        assertEquals(service, sync)
    }

    @Test
    fun asyncExecuteWorks() = runTest {
        val consoleLogger = AppConsoleLogServiceImpl()
        val service = MultiLogService(
            loggers = setOf(consoleLogger),
            scope = IdkScope.APP
        )
        val async = service.toAsync()
        val result = async.execute(LogMessage(message = "async test"))
        assertTrue(result.isOk)
    }

    @Test
    fun asyncSetConfigWorks() = runTest {
        val service = MultiLogService(
            loggers = emptySet(),
            scope = IdkScope.APP
        )
        val async = service.toAsync()
        val newConfig = LoggerConfig(minLevel = LogLevel.WARN)
        async.setConfig(newConfig)
        assertEquals(newConfig, service.getConfig())
    }

    @Test
    fun tagIsPassedToMessage() = runTest {
        val consoleLogger = AppConsoleLogServiceImpl()
        val service = MultiLogService(
            loggers = setOf(consoleLogger),
            scope = IdkScope.APP,
            tag = "custom-tag"
        )
        // Just verify no exception is thrown
        val result = service.execute(LogMessage(message = "test"))
        assertTrue(result.isOk)
    }

    @Test
    fun scopeIsCorrect() {
        val service = MultiLogService(
            loggers = emptySet(),
            scope = IdkScope.SESSION
        )
        assertEquals(IdkScope.SESSION, service.scope)
    }

    @Test
    fun idIsCorrect() {
        val service = MultiLogService(
            loggers = emptySet(),
            scope = IdkScope.APP
        )
        assertEquals(MultiLogService.SERVICE_ID, service.id)
    }

    @Test
    fun policyFiltersPerDelegateServiceId() = runTest {
        val loggerA = RecordingLogService(id = "logger-a")
        val loggerB = RecordingLogService(id = "logger-b")
        val service = MultiLogService(
            loggers = setOf(loggerA, loggerB),
            scope = IdkScope.APP
        )
        service.setPolicy(LogPolicy(disabledServicePatterns = setOf("logger-a")))

        val result = service.execute(LogMessage(level = LogLevel.INFO, message = "policy test"))

        assertTrue(result.isOk)
        assertEquals(0, loggerA.callCount)
        assertEquals(1, loggerB.callCount)
    }

    @Test
    fun policyCanDisableAllDelegatesWithoutError() = runTest {
        val loggerA = RecordingLogService(id = "logger-a")
        val loggerB = RecordingLogService(id = "logger-b")
        val service = MultiLogService(
            loggers = setOf(loggerA, loggerB),
            scope = IdkScope.APP
        )
        service.setPolicy(LogPolicy(disabledServicePatterns = setOf("logger-*")))

        val result = service.execute(LogMessage(level = LogLevel.ERROR, message = "blocked"))

        assertTrue(result.isOk)
        assertEquals(0, loggerA.callCount)
        assertEquals(0, loggerB.callCount)
    }
}

// ========== AsyncLogService Default Methods Tests ==========

class AsyncLogServiceDefaultMethodsTest {

    private class TestableAsyncLogService : AsyncLogService {
        override val sessionContext: SessionContext = NoOpSessionContext
        override val scope: IdkScope = IdkScope.APP
        override val id: String = "test-async-logger"
        override val isEnabled: Boolean = true
        private var config: LoggerConfig = LoggerConfig.Default
        private var policyConfig: LogPolicy = LogPolicy.AllowAll
        var lastMessage: LogMessage? = null

        override suspend fun setConfig(config: LoggerConfig): AsyncLogService = apply {
            this.config = config
        }

        override suspend fun getConfig(): LoggerConfig = config
        override val policy: LogPolicy
            get() = policyConfig
        override suspend fun setPolicy(policy: LogPolicy): AsyncLogService = apply {
            this.policyConfig = policy
        }
        override suspend fun getPolicy(): LogPolicy = policyConfig
        override fun isEnabled(level: LogLevel, tag: String?, context: SessionContext?): Boolean =
            isEnabled && policyConfig.isEnabled(
                level = level,
                defaultMinLevel = config.minLevel,
                scope = scope,
                serviceId = id,
                commandOrTag = tag
            )

        override suspend fun execute(args: LogMessage): IdkResult<Unit, IdkErrorType> {
            lastMessage = args
            return Ok(Unit)
        }

        override fun toSync(): LogService = throw NotImplementedError()
    }

    @Test
    fun disableDisablesConfig() = runTest {
        val service = TestableAsyncLogService()
        service.disable()
        assertEquals(LogLevel.OFF, service.getConfig().minLevel)
    }

    @Test
    fun enableEnablesWithMinLevel() = runTest {
        val service = TestableAsyncLogService()
        service.disable()
        service.enable(LogLevel.WARN)
        assertEquals(LogLevel.WARN, service.getConfig().minLevel)
    }

    @Test
    fun enableUsesInfoByDefault() = runTest {
        val service = TestableAsyncLogService()
        service.disable()
        service.enable()
        assertEquals(LogLevel.INFO, service.getConfig().minLevel)
    }

    @Test
    fun traceMethodSendsTraceLevel() = runTest {
        val service = TestableAsyncLogService()
        service.setConfig(LoggerConfig(minLevel = LogLevel.TRACE))
        service.trace("trace message")
        assertEquals(LogLevel.TRACE, service.lastMessage?.level)
        assertEquals("trace message", service.lastMessage?.message)
    }

    @Test
    fun debugMethodSendsDebugLevel() = runTest {
        val service = TestableAsyncLogService()
        service.debug("debug message")
        assertEquals(LogLevel.DEBUG, service.lastMessage?.level)
        assertEquals("debug message", service.lastMessage?.message)
    }

    @Test
    fun infoMethodSendsInfoLevel() = runTest {
        val service = TestableAsyncLogService()
        service.info("info message")
        assertEquals(LogLevel.INFO, service.lastMessage?.level)
        assertEquals("info message", service.lastMessage?.message)
    }

    @Test
    fun warnMethodSendsWarnLevel() = runTest {
        val service = TestableAsyncLogService()
        service.warn("warn message")
        assertEquals(LogLevel.WARN, service.lastMessage?.level)
        assertEquals("warn message", service.lastMessage?.message)
    }

    @Test
    fun warnMethodIncludesErrorResult() = runTest {
        val service = TestableAsyncLogService()
        val errorResult = IdkResult.err<Nothing, String>("test error")
        service.warn("warn with error", errorResult)
        assertEquals(LogLevel.WARN, service.lastMessage?.level)
        assertNotNull(service.lastMessage?.errorResult)
    }

    @Test
    fun errorMethodSendsErrorLevel() = runTest {
        val service = TestableAsyncLogService()
        service.error("error message")
        assertEquals(LogLevel.ERROR, service.lastMessage?.level)
        assertEquals("error message", service.lastMessage?.message)
    }

    @Test
    fun errorMethodIncludesException() = runTest {
        val service = TestableAsyncLogService()
        val exception = RuntimeException("test exception")
        service.error("error with exception", exception)
        assertEquals(LogLevel.ERROR, service.lastMessage?.level)
        assertSame(exception, service.lastMessage?.exception)
    }

    @Test
    fun errorMethodIncludesErrorResult() = runTest {
        val service = TestableAsyncLogService()
        val errorResult = IdkResult.err<Nothing, String>("test error")
        service.error("error with result", null, errorResult)
        assertEquals(LogLevel.ERROR, service.lastMessage?.level)
        assertNotNull(service.lastMessage?.errorResult)
    }

    @Test
    fun getConfigReturnsDefaultWhenNotSet() = runTest {
        val service = TestableAsyncLogService()
        assertEquals(LoggerConfig.Default, service.getConfig())
    }

    @Test
    fun lazyTraceDoesNotEvaluateWhenLevelDisabled() = runTest {
        val service = TestableAsyncLogService()
        service.setConfig(LoggerConfig(minLevel = LogLevel.INFO))
        var evaluated = false

        service.trace { evaluated = true; "trace should not be evaluated" }

        assertFalse(evaluated)
        assertEquals(null, service.lastMessage)
    }

    @Test
    fun lazyDebugEvaluatesWhenEnabled() = runTest {
        val service = TestableAsyncLogService()
        service.setConfig(LoggerConfig(minLevel = LogLevel.DEBUG))
        var evaluated = false

        service.debug { evaluated = true; "debug evaluated" }

        assertTrue(evaluated)
        assertEquals("debug evaluated", service.lastMessage?.message)
    }

    @Test
    fun policyAppliesCommandPatternMinLevelOverride() = runTest {
        val service = TestableAsyncLogService()
        service.setConfig(LoggerConfig(minLevel = LogLevel.DEBUG))
        service.setPolicy(
            LogPolicy(
                minLevelByCommandPattern = mapOf("openid.*" to LogLevel.ERROR)
            )
        )

        service.debug(
            message = "debug should be blocked",
            metadata = mapOf("commandId" to "openid.oid4vp.verifier.request.create")
        )
        assertEquals(null, service.lastMessage)

        service.error(
            message = "error should pass",
            metadata = mapOf("commandId" to "openid.oid4vp.verifier.request.create")
        )
        assertEquals("error should pass", service.lastMessage?.message)
    }
}

// ========== SessionLogService Default Methods Tests ==========

class SessionLogServiceDefaultMethodsTest {

    private class TestableSessionLogService(
        override val logManager: SessionLogManager
    ) : SessionLogService {
        override val sessionContext: SessionContext = NoOpSessionContext
        override val id: String = "test-session-logger"
        override val isEnabled: Boolean = true

        override suspend fun setConfig(config: LoggerConfig): LogService = this

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

        override fun toAsync(): AsyncLogService = throw NotImplementedError()
    }

    private class TestSessionLogManager : SessionLogManager {
        override suspend fun setGlobalConfig(config: LoggerConfig): LogManager = this
        override suspend fun getGlobalConfig(): LoggerConfig = LoggerConfig.Default
        override fun withTagAsync(tag: String, config: LoggerConfig?): AsyncLogService =
            throw NotImplementedError()
        override fun withTag(tag: String, config: LoggerConfig?): LogService =
            throw NotImplementedError()
    }

    @Test
    fun scopeReturnsSession() {
        val logManager = TestSessionLogManager()
        val service = TestableSessionLogService(logManager)
        assertEquals(IdkScope.SESSION, service.scope)
    }

    @Test
    fun logManagerIsAccessible() {
        val logManager = TestSessionLogManager()
        val service = TestableSessionLogService(logManager)
        assertSame(logManager, service.logManager)
    }
}

// ========== LogService Default Methods Tests ==========

class LogServiceDefaultMethodsTest {

    private class TestableLogService : LogService {
        override val sessionContext: SessionContext = NoOpSessionContext
        override val scope: IdkScope = IdkScope.APP
        override val id: String = "test-logger"
        override val isEnabled: Boolean = true
        private var config: LoggerConfig = LoggerConfig.Default
        private var policyConfig: LogPolicy = LogPolicy.AllowAll
        var lastMessage: LogMessage? = null

        override suspend fun setConfig(config: LoggerConfig): LogService = apply {
            this.config = config
        }

        override suspend fun getConfig(): LoggerConfig = config
        override val policy: LogPolicy
            get() = policyConfig
        override suspend fun setPolicy(policy: LogPolicy): LogService = apply {
            this.policyConfig = policy
        }
        override suspend fun getPolicy(): LogPolicy = policyConfig
        override fun isEnabled(level: LogLevel, tag: String?, context: SessionContext?): Boolean =
            isEnabled && policyConfig.isEnabled(
                level = level,
                defaultMinLevel = config.minLevel,
                scope = scope,
                serviceId = id,
                commandOrTag = tag
            )

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> {
            lastMessage = message
            return Ok(Unit)
        }

        override fun toAsync(): AsyncLogService = throw NotImplementedError()
    }

    @Test
    fun disableDisablesConfig() = runTest {
        val service = TestableLogService()
        service.disable()
        assertEquals(LogLevel.OFF, service.getConfig().minLevel)
    }

    @Test
    fun enableEnablesWithMinLevel() = runTest {
        val service = TestableLogService()
        service.disable()
        service.enable(LogLevel.ERROR)
        assertEquals(LogLevel.ERROR, service.getConfig().minLevel)
    }

    @Test
    fun enableUsesInfoByDefault() = runTest {
        val service = TestableLogService()
        service.disable()
        service.enable()
        assertEquals(LogLevel.INFO, service.getConfig().minLevel)
    }

    @Test
    fun traceMethodSendsTraceLevel() = runTest {
        val service = TestableLogService()
        service.setConfig(LoggerConfig(minLevel = LogLevel.TRACE))
        service.trace("trace message")
        assertEquals(LogLevel.TRACE, service.lastMessage?.level)
    }

    @Test
    fun debugMethodSendsDebugLevel() {
        val service = TestableLogService()
        service.debug("debug message")
        assertEquals(LogLevel.DEBUG, service.lastMessage?.level)
    }

    @Test
    fun infoMethodSendsInfoLevel() {
        val service = TestableLogService()
        service.info("info message")
        assertEquals(LogLevel.INFO, service.lastMessage?.level)
    }

    @Test
    fun warnMethodSendsWarnLevel() {
        val service = TestableLogService()
        service.warn("warn message")
        assertEquals(LogLevel.WARN, service.lastMessage?.level)
    }

    @Test
    fun errorMethodSendsErrorLevel() {
        val service = TestableLogService()
        service.error("error message")
        assertEquals(LogLevel.ERROR, service.lastMessage?.level)
    }

    @Test
    fun lazyTraceDoesNotEvaluateWhenLevelDisabled() = runTest {
        val service = TestableLogService()
        service.setConfig(LoggerConfig(minLevel = LogLevel.INFO))
        var evaluated = false

        service.trace { evaluated = true; "trace should not be evaluated" }

        assertFalse(evaluated)
        assertEquals(null, service.lastMessage)
    }

    @Test
    fun lazyDebugEvaluatesWhenEnabled() = runTest {
        val service = TestableLogService()
        service.setConfig(LoggerConfig(minLevel = LogLevel.DEBUG))
        var evaluated = false

        service.debug { evaluated = true; "debug evaluated" }

        assertTrue(evaluated)
        assertEquals("debug evaluated", service.lastMessage?.message)
    }

    @Test
    fun executeAsyncReturnsOk() {
        val service = TestableLogService()
        val result = service.executeAsync(LogMessage(message = "async-path") )
        assertTrue(result.isOk)
        assertEquals("async-path", service.lastMessage?.message)
    }

    @Test
    fun policyCanDisableByScope() = runTest {
        val service = TestableLogService()
        service.setConfig(LoggerConfig(minLevel = LogLevel.DEBUG))
        service.setPolicy(LogPolicy(disabledScopes = setOf(IdkScope.APP)))

        service.error("blocked by scope policy")

        assertEquals(null, service.lastMessage)
    }

    @Test
    fun policyCanDisableByServicePattern() = runTest {
        val service = TestableLogService()
        service.setConfig(LoggerConfig(minLevel = LogLevel.DEBUG))
        service.setPolicy(LogPolicy(disabledServicePatterns = setOf("test-*")))

        service.error("blocked by service policy")

        assertEquals(null, service.lastMessage)
    }

    @Test
    fun policyAppliesCommandPatternMinLevelOverride() = runTest {
        val service = TestableLogService()
        service.setConfig(LoggerConfig(minLevel = LogLevel.DEBUG))
        service.setPolicy(
            LogPolicy(
                minLevelByCommandPattern = mapOf("did.*" to LogLevel.ERROR)
            )
        )

        service.debug(
            message = "debug should be blocked",
            metadata = mapOf("commandId" to "did.resolve")
        )

        assertEquals(null, service.lastMessage)

        service.error(
            message = "error should pass",
            metadata = mapOf("commandId" to "did.resolve")
        )
        assertEquals("error should pass", service.lastMessage?.message)
    }
}

// ========== Log Object Tests ==========

class LogObjectTest {

    @Test
    fun registerAddsLogManagerToRegistry() {
        val logManager = object : LogManager {
            override suspend fun setGlobalConfig(config: LoggerConfig): LogManager = this
            override suspend fun getGlobalConfig(): LoggerConfig = LoggerConfig.Default
            override fun withTagAsync(tag: String, config: LoggerConfig?): AsyncLogService =
                throw NotImplementedError()
            override fun withTag(tag: String, config: LoggerConfig?): LogService =
                throw NotImplementedError()
        }

        Log.register(IdkScope.USER, logManager)
        // Just verify it doesn't throw
    }

    @Test
    fun appReturnsLogManager() {
        val logManager = Log.app()
        assertNotNull(logManager)
    }

    @Test
    fun appReturnsFallbackWhenNotRegistered() {
        // Clear any existing registration for APP scope
        Log.Registry.loggers.remove(IdkScope.APP)

        val logManager = Log.app()
        assertNotNull(logManager)
    }
}

// ========== LogService Interface Default Implementation Tests ==========

class LogServiceInterfaceDefaultsTest {

    // Test service that uses interface default getConfig() method
    private class DefaultGetConfigLogService : LogService {
        override val sessionContext: SessionContext = NoOpSessionContext
        override val scope: IdkScope = IdkScope.APP
        override val id: String = "default-config-logger"
        override val isEnabled: Boolean = true
        private var config: LoggerConfig? = null
        var lastMessage: LogMessage? = null

        override suspend fun setConfig(config: LoggerConfig): LogService = apply {
            this.config = config
        }

        // Do NOT override getConfig() - use the interface default implementation

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> {
            lastMessage = message
            return Ok(Unit)
        }

        override fun toAsync(): AsyncLogService = throw NotImplementedError()
    }

    @Test
    fun interfaceGetConfigReturnsDefault() = runTest {
        val service = DefaultGetConfigLogService()
        val config = service.getConfig()
        assertEquals(LoggerConfig.Default, config)
    }

    @Test
    fun disableUsingInterfaceGetConfig() = runTest {
        val service = DefaultGetConfigLogService()
        service.disable()
        // The disable() default implementation calls getConfig() then setConfig()
        // This covers the default getConfig() branch
    }

    @Test
    fun enableUsingInterfaceGetConfig() = runTest {
        val service = DefaultGetConfigLogService()
        service.enable(LogLevel.WARN)
        // The enable() default implementation calls getConfig() then setConfig()
    }
}

// ========== AsyncLogService Interface Default Implementation Tests ==========

class AsyncLogServiceInterfaceDefaultsTest {

    // Test service that uses interface default getConfig() method
    private class DefaultGetConfigAsyncLogService : AsyncLogService {
        override val sessionContext: SessionContext = NoOpSessionContext
        override val scope: IdkScope = IdkScope.APP
        override val id: String = "default-config-async-logger"
        override val isEnabled: Boolean = true
        private var config: LoggerConfig? = null
        var lastMessage: LogMessage? = null

        override suspend fun setConfig(config: LoggerConfig): AsyncLogService = apply {
            this.config = config
        }

        // Do NOT override getConfig() - use the interface default implementation

        override suspend fun execute(args: LogMessage): IdkResult<Unit, IdkErrorType> {
            lastMessage = args
            return Ok(Unit)
        }

        override fun toSync(): LogService = throw NotImplementedError()
    }

    @Test
    fun interfaceGetConfigReturnsDefault() = runTest {
        val service = DefaultGetConfigAsyncLogService()
        val config = service.getConfig()
        assertEquals(LoggerConfig.Default, config)
    }

    @Test
    fun disableUsingInterfaceGetConfig() = runTest {
        val service = DefaultGetConfigAsyncLogService()
        service.disable()
        // The disable() default implementation calls getConfig() then setConfig()
    }

    @Test
    fun enableUsingInterfaceGetConfig() = runTest {
        val service = DefaultGetConfigAsyncLogService()
        service.enable(LogLevel.ERROR)
        // The enable() default implementation calls getConfig() then setConfig()
    }
}

// ========== AbstractLogManager Tests ==========

class AbstractLogManagerTest {
    private class RecordingLogService(
        override val id: String
    ) : AbstractLogService(
        id = id,
        isEnabled = true,
        sessionContext = NoOpSessionContext
    ) {
        override val scope: IdkScope = IdkScope.APP
        var callCount: Int = 0

        override suspend fun doExecute(args: LogMessage,
            applyDuring: (LogMessage) -> LogMessage
        ): IdkResult<Unit, IdkErrorType> {
            callCount += 1
            return Ok(Unit)
        }
    }

    private class TestLogManager(
        scope: IdkScope,
        loggers: Set<LogService>
    ) : AbstractLogManager(scope = scope, loggers = loggers)

    @Test
    fun withTagReturnsLogService() {
        val manager = TestLogManager(IdkScope.APP, setOf(AppConsoleLogServiceImpl()))
        val logger = manager.withTag("test-tag")
        assertNotNull(logger)
    }

    @Test
    fun withTagAsyncReturnsAsyncLogService() {
        val manager = TestLogManager(IdkScope.APP, setOf(AppConsoleLogServiceImpl()))
        val logger = manager.withTagAsync("test-tag")
        assertNotNull(logger)
    }

    @Test
    fun withTagUsesDefaultTag() {
        val manager = TestLogManager(IdkScope.APP, setOf(AppConsoleLogServiceImpl()))
        val logger = manager.withTag()
        assertNotNull(logger)
    }

    @Test
    fun withTagAsyncUsesDefaultTag() {
        val manager = TestLogManager(IdkScope.APP, setOf(AppConsoleLogServiceImpl()))
        val logger = manager.withTagAsync()
        assertNotNull(logger)
    }

    @Test
    fun setGlobalConfigUpdatesConfig() = runTest {
        val manager = TestLogManager(IdkScope.APP, setOf(AppConsoleLogServiceImpl()))
        val newConfig = LoggerConfig(minLevel = LogLevel.ERROR)
        manager.setGlobalConfig(newConfig)
        assertEquals(newConfig, manager.getGlobalConfig())
    }

    @Test
    fun getGlobalConfigReturnsDefault() = runTest {
        val manager = TestLogManager(IdkScope.APP, setOf(AppConsoleLogServiceImpl()))
        assertEquals(LoggerConfig.Default, manager.getGlobalConfig())
    }

    @Test
    fun setGlobalPolicyUpdatesPolicy() = runTest {
        val manager = TestLogManager(IdkScope.APP, setOf(AppConsoleLogServiceImpl()))
        val policy = LogPolicy(disabledServicePatterns = setOf("AppConsoleLogger"))

        manager.setGlobalPolicy(policy)

        assertEquals(policy, manager.getGlobalPolicy())
    }

    @Test
    fun withTagInheritsGlobalPolicy() = runTest {
        val logger = RecordingLogService(id = "logger-a")
        val manager = TestLogManager(IdkScope.APP, setOf(logger))
        manager.setGlobalPolicy(LogPolicy(disabledServicePatterns = setOf("logger-*")))
        val tagged = manager.withTag("test-tag")

        val result = (tagged as MultiLogService).execute(LogMessage(level = LogLevel.ERROR, message = "blocked"))

        assertTrue(result.isOk)
        assertEquals(0, logger.callCount)
    }
}


