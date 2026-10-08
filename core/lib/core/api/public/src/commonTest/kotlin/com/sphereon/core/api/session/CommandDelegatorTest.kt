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

package com.sphereon.core.api.session

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.annotations.InternalApi
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(InternalApi::class)
class CommandDelegatorTest {
    private class TestCommand(
        private val result: IdkResult<String, IdkErrorType>,
    ) : BaseCommand<String, String, IdkErrorType> {
        var lastArgs: String? = null
        var lastContext: SessionContext? = null

        override suspend fun execute(args: String): IdkResult<String, IdkErrorType> {
            lastArgs = args
            return result
        }
    }

    private class TestSessionExecution(
        override val sessionContext: SessionContext = NoOpSessionContext,
    ) : SessionExecution {
        override val sessionContextManager: SessionContextManager
            get() = throw NotImplementedError("Not needed for test")
        override val log: SessionLogService
            get() = throw NotImplementedError("Not needed for test")
        override val conf: ContextConfig
            get() = throw NotImplementedError("Not needed for test")
    }

    private class TestCommandDelegator(
        override val execution: SessionExecution,
    ) : CommandDelegator

    @Test
    fun invokeCallsCommandWithSessionContext() =
        runTest {
            val command = TestCommand(IdkResult.ok("success"))
            val execution = TestSessionExecution()
            val delegator = TestCommandDelegator(execution)

            with(delegator) {
                val result = command("test-args")
                assertTrue(result.isOk)
                assertEquals("success", result.value)
                assertEquals("test-args", command.lastArgs)
                assertEquals(null, command.lastContext)
            }
        }

    @Test
    fun invokeReturnsErrorResult() =
        runTest {
            val error = IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Test error")
            val command = TestCommand(IdkResult.err(error))
            val execution = TestSessionExecution()
            val delegator = TestCommandDelegator(execution)

            with(delegator) {
                val result = command("test-args")
                assertTrue(result.isErr)
                assertEquals(error, result.error)
            }
        }

    @Test
    fun invokePropagatesCommandNotAuthorizedError() =
        runTest {
            val error =
                IdkError.COMMAND_NOT_AUTHORIZED_ERROR(
                    commandId = "core.session.command.execute",
                    reason = "policy denied",
                )
            val command = TestCommand(IdkResult.err(error))
            val execution = TestSessionExecution()
            val delegator = TestCommandDelegator(execution)

            with(delegator) {
                val result = command("test-args")
                assertTrue(result.isErr)
                assertEquals("COMMAND_NOT_AUTHORIZED", result.error.code)
            }
        }

    @Test
    fun invokePropagatesNotFoundError() =
        runTest {
            val error =
                IdkError.NOT_FOUND_ERROR(
                    resource = "command:core.session.command.execute",
                    message = "Command not found",
                )
            val command = TestCommand(IdkResult.err(error))
            val execution = TestSessionExecution()
            val delegator = TestCommandDelegator(execution)

            with(delegator) {
                val result = command("test-args")
                assertTrue(result.isErr)
                assertEquals("NOT_FOUND_ERROR", result.error.code)
            }
        }

    @Test
    fun sessionContextDelegatesToExecution() {
        val context = NoOpSessionContext
        val execution = TestSessionExecution(context)
        val delegator = TestCommandDelegator(execution)

        assertEquals(context, delegator.sessionContext)
    }
}

@OptIn(InternalApi::class)
@Suppress("DEPRECATION")
class SimpleCommandDelegatorTest {
    private class TestCommand(
        private val result: IdkResult<Int, IdkErrorType>,
    ) : BaseCommand<Int, Int, IdkErrorType> {
        var lastArgs: Int? = null
        var lastContext: SessionContext? = null

        override suspend fun execute(args: Int): IdkResult<Int, IdkErrorType> {
            lastArgs = args
            return result
        }
    }

    private class TestSimpleCommandDelegator(
        override val sessionContext: SessionContext,
    ) : SimpleCommandDelegator

    @Test
    fun invokeCallsCommandWithSessionContext() =
        runTest {
            val command = TestCommand(IdkResult.ok(42))
            val context = NoOpSessionContext
            val delegator = TestSimpleCommandDelegator(context)

            with(delegator) {
                val result = command(10)
                assertTrue(result.isOk)
                assertEquals(42, result.value)
                assertEquals(10, command.lastArgs)
                assertEquals(null, command.lastContext)
            }
        }

    @Test
    fun invokeReturnsErrorResult() =
        runTest {
            val error = IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Simple test error")
            val command = TestCommand(IdkResult.err(error))
            val delegator = TestSimpleCommandDelegator(NoOpSessionContext)

            with(delegator) {
                val result = command(5)
                assertTrue(result.isErr)
                assertEquals(error, result.error)
            }
        }

    @Test
    fun invokePropagatesCommandNotAuthorizedError() =
        runTest {
            val error =
                IdkError.COMMAND_NOT_AUTHORIZED_ERROR(
                    commandId = "core.session.command.execute",
                    reason = "policy denied",
                )
            val command = TestCommand(IdkResult.err(error))
            val delegator = TestSimpleCommandDelegator(NoOpSessionContext)

            with(delegator) {
                val result = command(5)
                assertTrue(result.isErr)
                assertEquals("COMMAND_NOT_AUTHORIZED", result.error.code)
            }
        }

    @Test
    fun invokePropagatesNotFoundError() =
        runTest {
            val error =
                IdkError.NOT_FOUND_ERROR(
                    resource = "command:core.session.command.execute",
                    message = "Command not found",
                )
            val command = TestCommand(IdkResult.err(error))
            val delegator = TestSimpleCommandDelegator(NoOpSessionContext)

            with(delegator) {
                val result = command(5)
                assertTrue(result.isErr)
                assertEquals("NOT_FOUND_ERROR", result.error.code)
            }
        }

    @Test
    fun invokeOrThrowReturnsValueOnSuccess() =
        runTest {
            val command = TestCommand(IdkResult.ok(100))
            val delegator = TestSimpleCommandDelegator(NoOpSessionContext)

            with(delegator) {
                val value = command.invokeOrThrow(50)
                assertEquals(100, value)
            }
        }

    @Test
    fun invokeOrThrowThrowsOnError() =
        runTest {
            val error = IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Simple throw test")
            val command = TestCommand(IdkResult.err(error))
            val delegator = TestSimpleCommandDelegator(NoOpSessionContext)

            with(delegator) {
                assertFailsWith<RuntimeException> {
                    command.invokeOrThrow(1)
                }
            }
        }
}
