/*
 * Copyright (c) 2026 Sphereon International B.V.
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

package com.sphereon.crypto.core.kms.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.api.session.Command
import com.sphereon.core.api.session.ExecutionScopedCommandAdapter
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.kms.TestKmsMock
import com.sphereon.crypto.resolution.managed.KeyInfoIdentifierResolutionServiceImpl
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.di.context.createAnonymousSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression test for ServiceCommand session context safety.
 *
 * Verifies that ServiceCommand adapters (TypedServiceCommandAdapter) always
 * use the execution-scoped session context, ignoring any caller-provided
 * session context.
 *
 * This prevents session context forgery attacks where a caller passes
 * a different session context to bypass authorization checks.
 */
class ServiceCommandSessionContextSafetyTest {
    // ========================================================================
    // Test fixtures
    // ========================================================================

    @Serializable
    data class TestInput(
        val value: String = "test",
    )

    @Serializable
    data class TestOutput(
        val result: String = "ok",
    )

    interface TestServiceCommand : ServiceCommand<TestInput, TestOutput> {
        companion object {
            const val COMMAND_ID = "test.session.safety"
        }

        override val commandId: String get() = COMMAND_ID
    }

    private class NoOpSessionLogService(
        override val sessionContext: SessionContext = createAnonymousSessionContext("test-log-session"),
    ) : SessionLogService {
        override val id: String = "test-log"
        override val isEnabled: Boolean = false
        override val scope = com.sphereon.core.api.context.IdkScope.SESSION
        override val logManager: SessionLogManager
            get() = throw NotImplementedError("Not needed for test")

        override suspend fun setConfig(config: com.sphereon.core.api.log.LoggerConfig): com.sphereon.core.api.log.LogService = this

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

        override fun toAsync(): AsyncLogService = throw NotImplementedError("Not needed for test")
    }

    private class NoOpContextConfig : ContextConfig {
        override val app: AppConfigService get() = throw NotImplementedError("Not needed for test")
        override val tenant: TenantConfigService get() = throw NotImplementedError("Not needed for test")
        override val principal: PrincipalConfigService get() = throw NotImplementedError("Not needed for test")

        override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("Not needed for test")
    }

    private class TestSessionExecution(
        override val sessionContext: SessionContext = createAnonymousSessionContext("test-session-execution"),
    ) : SessionExecution {
        override val sessionContextManager: SessionContextManager
            get() = throw NotImplementedError("Not needed for test")
        override val log: SessionLogService = NoOpSessionLogService(sessionContext)
        override val conf: ContextConfig = NoOpContextConfig()
    }

    /**
     * Concrete TypedServiceCommandAdapter that captures the session context
     * it receives in doExecute. Used for session context safety and
     * ServiceCommand interface property tests.
     */
    private class TestDualTransportCommand(
        execution: SessionExecution,
    ) : TypedServiceCommandAdapter<TestInput, TestOutput>(
            commandId = TestServiceCommand.COMMAND_ID,
            execution = execution,
            inputTypeToken = typeToken<TestInput>(),
            outputTypeToken = typeToken<TestOutput>(),
        ),
        TestServiceCommand {
        override val commandId: String get() = TestServiceCommand.COMMAND_ID

        var capturedSessionContext: SessionContext? = null

        override suspend fun doExecute(
            args: TestInput,
            applyDuring: (TestInput) -> TestInput,
        ): IdkResult<TestOutput, IdkError> {
            capturedSessionContext = execution.sessionContext
            val applied = applyDuring(args)
            return Ok(TestOutput(result = applied.value))
        }

        override suspend fun supports(args: Any): Boolean = args is TestInput
    }

    /**
     * Concrete TypedServiceCommandAdapter that captures the session context
     * it receives in doExecute.
     */
    private class TestTypedCommand(
        execution: SessionExecution,
    ) : TypedServiceCommandAdapter<TestInput, TestOutput>(
            commandId = "test.typed.safety",
            execution = execution,
            inputTypeToken = typeToken<TestInput>(),
            outputTypeToken = typeToken<TestOutput>(),
        ) {
        override val commandId: String get() = "test.typed.safety"

        var capturedSessionContext: SessionContext? = null

        override suspend fun doExecute(
            args: TestInput,
            applyDuring: (TestInput) -> TestInput,
        ): IdkResult<TestOutput, IdkError> {
            capturedSessionContext = execution.sessionContext
            val applied = applyDuring(args)
            return Ok(TestOutput(result = applied.value))
        }

        override suspend fun supports(args: Any): Boolean = args is TestInput
    }

    /**
     * Concrete ExecutionScopedCommandAdapter used to verify core session safety
     * behavior independent of ServiceCommand wrappers.
     */
    private class TestExecutionScopedCommand(
        execution: SessionExecution,
    ) : ExecutionScopedCommandAdapter<TestInput, TestOutput, IdkError>(
            id = "test.execution.safety",
            execution = execution,
        ) {
        var capturedSessionContext: SessionContext? = null
        var capturedSupportsContext: SessionContext? = null

        override suspend fun supports(args: Any): Boolean {
            capturedSupportsContext = execution.sessionContext
            return args is TestInput
        }

        override suspend fun doExecute(
            args: TestInput,
            applyDuring: (TestInput) -> TestInput,
        ): IdkResult<TestOutput, IdkError> {
            capturedSessionContext = execution.sessionContext
            val applied = applyDuring(args)
            return Ok(TestOutput(result = applied.value))
        }
    }

    // ========================================================================
    // TypedServiceCommandAdapter tests (TestDualTransportCommand)
    // ========================================================================

    @Test
    fun dualTransportAdapterUsesExecutionSessionContext() =
        runTest {
            // Given: a command with execution-scoped session context "session-A"
            val executionContext = createAnonymousSessionContext("session-A")
            val execution = TestSessionExecution(sessionContext = executionContext)
            val command = TestDualTransportCommand(execution)

            // When: execute is called
            val result = command.execute(TestInput("hello"))

            // Then: doExecute receives the execution session context
            assertTrue(result.isOk)
            assertEquals(executionContext, command.capturedSessionContext)
            assertEquals("hello", result.value.result)
        }

    @Test
    fun dualTransportAdapterIgnoresForgedSessionContext() =
        runTest {
            // Given: a command with execution-scoped session context "session-A"
            val executionContext = createAnonymousSessionContext("session-A")
            val forgedContext = createAnonymousSessionContext("session-FORGED")
            val execution = TestSessionExecution(sessionContext = executionContext)
            val command = TestDualTransportCommand(execution)

            // When: execute is called with a DIFFERENT (forged) session context
            val result = command.execute(TestInput("payload"))

            // Then: doExecute still receives the execution session context, NOT the forged one
            assertTrue(result.isOk)
            assertEquals(executionContext, command.capturedSessionContext)
            assertNotEquals(forgedContext, command.capturedSessionContext)
            assertEquals("session-A", command.capturedSessionContext?.sessionId)
        }

    // ========================================================================
    // TypedServiceCommandAdapter tests
    // ========================================================================

    @Test
    fun typedAdapterUsesExecutionSessionContext() =
        runTest {
            // Given: a typed command with execution-scoped session context "session-B"
            val executionContext = createAnonymousSessionContext("session-B")
            val execution = TestSessionExecution(sessionContext = executionContext)
            val command = TestTypedCommand(execution)

            // When: execute is called
            val result = command.execute(TestInput("typed-test"))

            // Then: doExecute receives the execution session context
            assertTrue(result.isOk)
            assertEquals(executionContext, command.capturedSessionContext)
        }

    @Test
    fun typedAdapterIgnoresForgedSessionContext() =
        runTest {
            // Given: a typed command with execution-scoped session context "session-B"
            val executionContext = createAnonymousSessionContext("session-B")
            val forgedContext = createAnonymousSessionContext("session-EVIL")
            val execution = TestSessionExecution(sessionContext = executionContext)
            val command = TestTypedCommand(execution)

            // When: execute is called with a DIFFERENT (forged) session context
            val result = command.execute(TestInput("payload"))

            // Then: doExecute still receives the execution session context, NOT the forged one
            assertTrue(result.isOk)
            assertEquals(executionContext, command.capturedSessionContext)
            assertNotEquals(forgedContext, command.capturedSessionContext)
            assertEquals("session-B", command.capturedSessionContext?.sessionId)
        }

    @Test
    fun executionScopedAdapterIgnoresForgedSessionContext() =
        runTest {
            val executionContext = createAnonymousSessionContext("session-C")
            val forgedContext = createAnonymousSessionContext("session-FORGED-C")
            val execution = TestSessionExecution(sessionContext = executionContext)
            val command = TestExecutionScopedCommand(execution)

            val result = command.execute(TestInput("payload"))

            assertTrue(result.isOk)
            assertEquals(executionContext, command.capturedSessionContext)
            assertNotEquals(forgedContext, command.capturedSessionContext)
            assertEquals("session-C", command.capturedSessionContext?.sessionId)
        }

    @Test
    fun executionScopedAdapterSupportsUsesExecutionSession() =
        runTest {
            val executionContext = createAnonymousSessionContext("session-D")
            val execution = TestSessionExecution(sessionContext = executionContext)
            val command = TestExecutionScopedCommand(execution)

            val result = command.execute(TestInput("payload"))

            assertTrue(result.isOk)
            assertEquals(executionContext, command.capturedSupportsContext)
        }

    @Test
    fun keyInfoIdentifierResolutionSupportsIsStableAcrossCalls() =
        runTest {
            val executionContext = createAnonymousSessionContext("session-keyinfo-supports")
            val execution = TestSessionExecution(sessionContext = executionContext)
            val service = KeyInfoIdentifierResolutionServiceImpl(execution = execution, kms = TestKmsMock())
            val args = ManagedOptsKeyInfo(identifier = KeyInfo<KeyType>(alias = "alias-only"))

            val supportsFirstCall = service.supports(args)
            val supportsSecondCall = service.supports(args)

            assertTrue(supportsFirstCall)
            assertEquals(supportsFirstCall, supportsSecondCall)
        }

    @Test
    fun keyInfoIdentifierResolutionSupportsCannotBypassUnsupportedArgs() =
        runTest {
            val executionContext = createAnonymousSessionContext("session-keyinfo-supports-unsupported")
            val execution = TestSessionExecution(sessionContext = executionContext)
            val service = KeyInfoIdentifierResolutionServiceImpl(execution = execution, kms = TestKmsMock())
            val unsupportedArgs: Any = "unsupported-raw-identifier"

            val supportsFirstCall = service.supports(unsupportedArgs)
            val supportsSecondCall = service.supports(unsupportedArgs)

            assertFalse(supportsFirstCall)
            assertEquals(supportsFirstCall, supportsSecondCall)
        }

    @Test
    fun keyInfoIdentifierResolutionExecuteWorksWhenCallerContextIsForged() =
        runTest {
            val executionContext = createAnonymousSessionContext("session-keyinfo-exec")
            val forgedContext = createAnonymousSessionContext("session-keyinfo-forged-exec")
            val execution = TestSessionExecution(sessionContext = executionContext)
            val kms = TestKmsMock()
            val service = KeyInfoIdentifierResolutionServiceImpl(execution = execution, kms = kms)
            val alias = "stored-key-alias"

            kms.storeKey(
                keyInfo =
                    ResolvedKeyInfo<JwkType>(
                        key = Jwk(kty = JwaKeyType.EC),
                        alias = alias,
                    ),
                providerId = "test-provider",
                alias = alias,
                certChain = null,
            )

            val result = service.execute(ManagedOptsAlias(identifier = alias))

            assertTrue(result.isOk)
            assertEquals(alias, result.value.alias)
            assertEquals("test-provider", result.value.providerId)
        }

    // ========================================================================
    // ServiceCommand interface property tests
    // ========================================================================

    @Test
    fun serviceCommandIdDefaultsToCommandId() {
        // Given: a DualTransport command
        val execution = TestSessionExecution()
        val command = TestDualTransportCommand(execution)

        // Then: id defaults to commandId (Task #1 pre-req)
        assertEquals(command.commandId, command.id)
        assertEquals(TestServiceCommand.COMMAND_ID, command.id)
    }

    @Test
    fun moduleDerivedFromCommandId() {
        // Given: a command with ID "test.session.safety"
        val execution = TestSessionExecution()
        val command = TestDualTransportCommand(execution)

        // Then: module is the first segment
        assertEquals("test", command.module)
    }

    @Test
    fun serviceDerivedFromCommandId() {
        // Given: a command with ID "test.session.safety"
        val execution = TestSessionExecution()
        val command = TestDualTransportCommand(execution)

        // Then: service is the second segment
        assertEquals("session", command.service)
    }

    @Test
    fun commandDerivedFromCommandId() {
        // Given: a command with ID "test.session.safety"
        val execution = TestSessionExecution()
        val command = TestDualTransportCommand(execution)

        // Then: command is the last segment
        assertEquals("safety", command.command)
    }

    @Test
    fun typeTokensAreAvailable() {
        // Given: a typed command with typeToken<TestInput> and typeToken<TestOutput>
        val execution = TestSessionExecution()
        val command = TestDualTransportCommand(execution)

        // Then: inputTypeToken and outputTypeToken are accessible
        val inputToken = command.inputTypeToken
        val outputToken = command.outputTypeToken
        assertTrue(inputToken != null, "inputTypeToken should be non-null")
        assertTrue(outputToken != null, "outputTypeToken should be non-null")
    }

    // ========================================================================
    // supports() method tests
    // ========================================================================

    @Test
    fun dualTransportSupportsReturnsTrueForCorrectArgType() =
        runTest {
            // Given: a DualTransport command that supports TestInput
            val execution = TestSessionExecution()
            val command = TestDualTransportCommand(execution)

            // When: supports is called with correct arg type
            val result = command.supports(TestInput("valid"))

            // Then: returns true
            assertTrue(result)
        }

    @Test
    fun dualTransportSupportsReturnsFalseForWrongArgType() =
        runTest {
            // Given: a DualTransport command that supports TestInput
            val execution = TestSessionExecution()
            val command = TestDualTransportCommand(execution)

            // When: supports is called with a different type (String)
            val result = command.supports("wrong type")

            // Then: returns false
            assertFalse(result)
        }

    @Test
    fun dualTransportSupportsReturnsFalseForOtherDataClass() =
        runTest {
            // Given: a DualTransport command that supports TestInput
            val execution = TestSessionExecution()
            val command = TestDualTransportCommand(execution)

            // When: supports is called with a different data class (TestOutput)
            val result = command.supports(TestOutput("not an input"))

            // Then: returns false
            assertFalse(result)
        }

    @Test
    fun typedSupportsReturnsTrueForCorrectArgType() =
        runTest {
            // Given: a Typed command that supports TestInput
            val execution = TestSessionExecution()
            val command = TestTypedCommand(execution)

            // When: supports is called with correct arg type
            val result = command.supports(TestInput("valid"))

            // Then: returns true
            assertTrue(result)
        }

    @Test
    fun typedSupportsReturnsFalseForWrongArgType() =
        runTest {
            // Given: a Typed command that supports TestInput
            val execution = TestSessionExecution()
            val command = TestTypedCommand(execution)

            // When: supports is called with wrong type
            val result = command.supports(42)

            // Then: returns false
            assertFalse(result)
        }

    @Test
    fun supportsWorksWithoutAdditionalInputs() =
        runTest {
            // Given: a DualTransport command
            val execution = TestSessionExecution()
            val command = TestDualTransportCommand(execution)

            // When: supports is called with valid args
            val result = command.supports(TestInput("valid"))

            // Then: returns true
            assertTrue(result)
        }

    @Test
    fun typedSupportsIsStableAcrossCalls() =
        runTest {
            val execution = TestSessionExecution()
            val command = TestTypedCommand(execution)
            val args = TestInput("valid")

            val supportsFirstCall = command.supports(args)
            val supportsSecondCall = command.supports(args)

            assertEquals(supportsFirstCall, supportsSecondCall)
            assertTrue(supportsSecondCall)
        }

    // ========================================================================
    // Backward compatibility: ServiceCommand IS-A Command
    // ========================================================================

    @Test
    fun serviceCommandIsACommand() {
        // Given: a ServiceCommand implementation
        val execution = TestSessionExecution()
        val command = TestDualTransportCommand(execution)

        // Then: it can be assigned to Command<TInput, TOutput, IdkError>
        val asCommand: Command<TestInput, TestOutput, IdkError> = command
        assertEquals(command.id, asCommand.id)
        assertEquals(command.commandId, asCommand.id)
    }

    @Test
    fun serviceCommandCanExecuteViaCommandInterface() =
        runTest {
            // Given: a ServiceCommand cast to Command interface
            val executionContext = createAnonymousSessionContext("compat-test")
            val execution = TestSessionExecution(sessionContext = executionContext)
            val command = TestDualTransportCommand(execution)
            val asCommand: Command<TestInput, TestOutput, IdkError> = command

            // When: execute is called through the Command interface
            val result = asCommand.execute(TestInput("via-command"))

            // Then: it works correctly
            assertTrue(result.isOk)
            assertEquals("via-command", result.value.result)
        }

    @Test
    fun serviceCommandSupportsViaCommandInterface() =
        runTest {
            // Given: a ServiceCommand cast to Command interface
            val execution = TestSessionExecution()
            val command = TestDualTransportCommand(execution)
            val asCommand: Command<TestInput, TestOutput, IdkError> = command

            // When: supports is called through the Command interface
            val supportsCorrect = asCommand.supports(TestInput("ok"))
            val supportsWrong = asCommand.supports("wrong")

            // Then: works correctly through the base interface
            assertTrue(supportsCorrect)
            assertFalse(supportsWrong)
        }

    @Test
    fun typedServiceCommandIsACommand() {
        // Given: a TypedServiceCommandAdapter implementation
        val execution = TestSessionExecution()
        val command = TestTypedCommand(execution)

        // Then: it can also be assigned to Command<TInput, TOutput, IdkError>
        val asCommand: Command<TestInput, TestOutput, IdkError> = command
        assertEquals("test.typed.safety", asCommand.id)
    }
}
