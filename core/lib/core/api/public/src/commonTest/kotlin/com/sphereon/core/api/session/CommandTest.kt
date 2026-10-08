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
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventSubsystems
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaseCommandTest {
    @Test
    fun defaultSupportsReturnsTrue() =
        runTest {
            val command =
                object : BaseCommand<String, String, IdkErrorType> {
                    override suspend fun execute(args: String): IdkResult<String, IdkErrorType> = IdkResult.ok(args)
                }
            assertTrue(command.supports("any"))
        }

    @Test
    fun supportsDelegatesToOverride() =
        runTest {
            var supportsCalled = false
            val command =
                object : BaseCommand<String, String, IdkErrorType> {
                    override suspend fun supports(args: Any): Boolean {
                        supportsCalled = true
                        return args == "any"
                    }

                    override suspend fun execute(args: String): IdkResult<String, IdkErrorType> = IdkResult.ok(args)
                }

            assertTrue(command.supports("any"))
            assertTrue(supportsCalled)
        }

    @Test
    fun executeReturnsResult() =
        runTest {
            val command =
                object : BaseCommand<String, String, IdkErrorType> {
                    override suspend fun execute(args: String): IdkResult<String, IdkErrorType> = IdkResult.ok("result: $args")
                }
            val result = command.execute("test")
            assertTrue(result.isOk)
            assertEquals("result: test", result.value)
        }

    @Test
    fun getOrElseReturnsValueOnSuccess() =
        runTest {
            val command =
                object : BaseCommand<String, String, IdkErrorType> {
                    override suspend fun execute(args: String): IdkResult<String, IdkErrorType> = IdkResult.ok("success")
                }
            val result = command.execute("args")
            with(command) {
                assertEquals("success", result.getOrElse("default"))
            }
        }

    @Test
    fun getOrElseReturnsDefaultOnError() =
        runTest {
            val command =
                object : BaseCommand<String, String, IdkErrorType> {
                    override suspend fun execute(args: String): IdkResult<String, IdkErrorType> = IdkResult.err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "error"))
                }
            val result = command.execute("args")
            with(command) {
                assertEquals("default", result.getOrElse("default"))
            }
        }

    @Test
    fun andThenCommandChainsOnSuccess() =
        runTest {
            val firstCommand =
                object : BaseCommand<String, Int, IdkErrorType> {
                    override suspend fun execute(args: String): IdkResult<Int, IdkErrorType> = IdkResult.ok(args.length)
                }
            val secondCommand =
                object : BaseCommand<Int, String, IdkErrorType> {
                    override suspend fun execute(args: Int): IdkResult<String, IdkErrorType> = IdkResult.ok("Length: $args")
                }

            val firstResult = firstCommand.execute("hello")
            with(firstCommand) {
                val finalResult = firstResult.andThenCommand(secondCommand)
                assertTrue(finalResult.isOk)
                assertEquals("Length: 5", finalResult.value)
            }
        }

    @Test
    fun andThenCommandPropagatesError() =
        runTest {
            val error = IdkError.ILLEGAL_ARGUMENT_ERROR(message = "first error")
            val firstCommand =
                object : BaseCommand<String, Int, IdkErrorType> {
                    override suspend fun execute(args: String): IdkResult<Int, IdkErrorType> = IdkResult.err(error)
                }
            val secondCommand =
                object : BaseCommand<Int, String, IdkErrorType> {
                    override suspend fun execute(args: Int): IdkResult<String, IdkErrorType> = IdkResult.ok("should not reach")
                }

            val firstResult = firstCommand.execute("hello")
            with(firstCommand) {
                val finalResult = firstResult.andThenCommand(secondCommand)
                assertTrue(finalResult.isErr)
                assertEquals(error, finalResult.error)
            }
        }

    @Test
    fun andThenComposesCommands() =
        runTest {
            val first =
                object : BaseCommand<String, Int, IdkErrorType> {
                    override suspend fun execute(args: String): IdkResult<Int, IdkErrorType> = IdkResult.ok(args.length)
                }
            val second =
                object : BaseCommand<Int, String, IdkErrorType> {
                    override suspend fun execute(args: Int): IdkResult<String, IdkErrorType> = IdkResult.ok("Doubled: ${args * 2}")
                }

            with(first) {
                val composed = first.andThen(second, IdkErrorTypeCommandErrorMapper)
                val result = composed.execute("test")
                assertTrue(result.isOk)
                assertEquals("Doubled: 8", result.value)
            }
        }

    @Test
    fun andThenComposedSupportsChecksFirst() =
        runTest {
            val first =
                object : BaseCommand<String, Int, IdkErrorType> {
                    override suspend fun supports(args: Any): Boolean = args == "valid"

                    override suspend fun execute(args: String): IdkResult<Int, IdkErrorType> = IdkResult.ok(args.length)
                }
            val second =
                object : BaseCommand<Int, String, IdkErrorType> {
                    override suspend fun execute(args: Int): IdkResult<String, IdkErrorType> = IdkResult.ok("result")
                }

            with(first) {
                val composed = first.andThen(second, IdkErrorTypeCommandErrorMapper)
                assertTrue(composed.supports("valid"))
                assertFalse(composed.supports("invalid"))
            }
        }

    @Test
    fun andThenComposedExecutePropagatesFirstError() =
        runTest {
            val error = IdkError.ILLEGAL_ARGUMENT_ERROR(message = "first command error")
            val first =
                object : BaseCommand<String, Int, IdkErrorType> {
                    override suspend fun execute(args: String): IdkResult<Int, IdkErrorType> = IdkResult.err(error)
                }
            val second =
                object : BaseCommand<Int, String, IdkErrorType> {
                    var executed = false

                    override suspend fun execute(args: Int): IdkResult<String, IdkErrorType> {
                        executed = true
                        return IdkResult.ok("should not reach")
                    }
                }

            with(first) {
                val composed = first.andThen(second, IdkErrorTypeCommandErrorMapper)
                val result = composed.execute("test")
                assertTrue(result.isErr)
                assertEquals(error, result.error)
                assertFalse(second.executed)
            }
        }

    @Test
    fun andThenComposedExecuteReturnsErrorWhenSecondDoesNotSupport() =
        runTest {
            val first =
                object : BaseCommand<String, Int, IdkErrorType> {
                    override suspend fun execute(args: String): IdkResult<Int, IdkErrorType> = IdkResult.ok(args.length)
                }
            val second =
                object : BaseCommand<Int, String, IdkErrorType> {
                    override suspend fun supports(args: Any): Boolean = false

                    override suspend fun execute(args: Int): IdkResult<String, IdkErrorType> = IdkResult.ok("should not reach")
                }

            with(first) {
                val composed = first.andThen(second, IdkErrorTypeCommandErrorMapper)
                val result = composed.execute("test")
                assertTrue(result.isErr)
                assertEquals("COMMAND_ARG_NOT_SUPPORTED_ERROR", result.error.code)
            }
        }

    @Test
    fun andThenComposedSupportsReturnsFalseWhenSecondDoesNotSupport() =
        runTest {
            val first =
                object : BaseCommand<String, Int, IdkErrorType> {
                    override suspend fun supports(args: Any): Boolean = true

                    override suspend fun execute(args: String): IdkResult<Int, IdkErrorType> = IdkResult.ok(args.length)
                }
            val second =
                object : BaseCommand<Int, String, IdkErrorType> {
                    override suspend fun supports(args: Any): Boolean = false

                    override suspend fun execute(args: Int): IdkResult<String, IdkErrorType> = IdkResult.ok("result")
                }

            with(first) {
                val composed = first.andThen(second, IdkErrorTypeCommandErrorMapper)
                assertFalse(composed.supports("test"))
            }
        }

    @Test
    fun andThenComposedSupportsRespectsDelegateSupports() =
        runTest {
            val first =
                object : BaseCommand<String, Int, IdkErrorType> {
                    override suspend fun supports(args: Any): Boolean = args == "test"

                    override suspend fun execute(args: String): IdkResult<Int, IdkErrorType> = IdkResult.ok(args.length)
                }
            val second =
                object : BaseCommand<Int, String, IdkErrorType> {
                    override suspend fun supports(args: Any): Boolean = args is String && args == "test"

                    override suspend fun execute(args: Int): IdkResult<String, IdkErrorType> = IdkResult.ok("result")
                }

            with(first) {
                val composed = first.andThen(second, IdkErrorTypeCommandErrorMapper)
                assertTrue(composed.supports("test"))
                assertFalse(composed.supports("other"))
            }
        }

    @Test
    fun andThenComposedExecuteEvaluatesSecondSupportsOnce() =
        runTest {
            val first =
                object : BaseCommand<String, Int, IdkErrorType> {
                    override suspend fun execute(args: String): IdkResult<Int, IdkErrorType> = IdkResult.ok(args.length)
                }
            val second =
                object : BaseCommand<Int, String, IdkErrorType> {
                    val supportsArgs = mutableListOf<Any>()

                    override suspend fun supports(args: Any): Boolean = (args == 4).also { supportsArgs.add(args) }

                    override suspend fun execute(args: Int): IdkResult<String, IdkErrorType> = IdkResult.ok("second: $args")
                }

            with(first) {
                val composed = first.andThen(second, IdkErrorTypeCommandErrorMapper)
                val result = composed.execute("test")
                assertTrue(result.isOk)
                assertEquals("second: 4", result.value)
                assertEquals(1, second.supportsArgs.size)
                assertEquals(4, second.supportsArgs.first())
            }
        }
}

class CommandAdapterTest {
    private class TestableCommandAdapter(
        id: String = "test-command",
        isEnabled: Boolean = true,
        private val result: IdkResult<String, IdkErrorType> = IdkResult.ok("success"),
        private val supportsResult: Boolean = true,
        private val enhancedExecutionExtensions: Array<IEnhancedCommandExecutionExtension<String, String, IdkErrorType>> = emptyArray(),
        override val subsystem: EventSubsystem = EventSubsystems.CUSTOM,
    ) : CommandAdapter<String, String, IdkErrorType>(
            id = id,
            isEnabled = isEnabled,
            initExtensions = emptyArray(),
            executionExtensions = emptyArray(),
            enhancedExecutionExtensions = enhancedExecutionExtensions,
        ) {
        var executeCallCount = 0
        var lastArgs: String? = null
        var supportsCallCount = 0

        override suspend fun supports(args: Any): Boolean {
            supportsCallCount++
            return supportsResult
        }

        override suspend fun doExecute(
            args: String,
            applyDuring: (String) -> String,
        ): IdkResult<String, IdkErrorType> {
            executeCallCount++
            lastArgs = applyDuring(args)
            return result
        }
    }

    @Test
    fun commandHasId() {
        val command = TestableCommandAdapter(id = "my-command")
        assertEquals("my-command", command.id)
    }

    @Test
    fun commandDefaultsToEnabled() {
        val command = TestableCommandAdapter()
        assertTrue(command.isEnabled)
    }

    @Test
    fun commandCanBeDisabled() {
        val command = TestableCommandAdapter(isEnabled = false)
        assertFalse(command.isEnabled)
    }

    @Test
    fun executeCallsDoExecuteWhenEnabled() =
        runTest {
            val command = TestableCommandAdapter()
            val result = command.execute("args")

            assertTrue(result.isOk)
            assertEquals("success", result.value)
            assertEquals(1, command.executeCallCount)
            assertEquals("args", command.lastArgs)
            assertEquals(1, command.supportsCallCount)
        }

    @Test
    fun executeReturnsDisabledErrorWhenDisabled() =
        runTest {
            val command = TestableCommandAdapter(isEnabled = false)
            val result = command.execute("args")

            assertTrue(result.isErr)
            assertEquals("COMMAND_DISABLED", result.error.code)
            assertEquals(0, command.executeCallCount)
        }

    @Test
    fun executeReturnsNotSupportedErrorWhenNotSupported() =
        runTest {
            val command = TestableCommandAdapter(supportsResult = false)
            val result = command.execute("args")

            assertTrue(result.isErr)
            assertEquals("COMMAND_ARG_NOT_SUPPORTED_ERROR", result.error.code)
            assertEquals(0, command.executeCallCount)
        }

    @Test
    fun executeReturnsCommandSkippedWhenEnhancedExtensionSkips() =
        runTest {
            val extension =
                object : IEnhancedCommandExecutionExtension<String, String, IdkErrorType> {
                    override suspend fun beforeExecute(
                        service: Command<String, String, IdkErrorType>,
                        args: String,
                    ): BeforeExecuteResult<String, String, IdkErrorType> = BeforeExecuteResult.Skip
                }
            val command =
                TestableCommandAdapter(
                    enhancedExecutionExtensions = arrayOf(extension),
                )

            val result = command.execute("args")

            assertTrue(result.isErr)
            assertEquals("COMMAND_SKIPPED", result.error.code)
            assertEquals(0, command.executeCallCount)
        }

    @Test
    fun executeReturnsNotAuthorizedWhenEnhancedExtensionShortCircuitsWithPolicyError() =
        runTest {
            val extension =
                object : IEnhancedCommandExecutionExtension<String, String, IdkErrorType> {
                    override suspend fun beforeExecute(
                        service: Command<String, String, IdkErrorType>,
                        args: String,
                    ): BeforeExecuteResult<String, String, IdkErrorType> =
                        BeforeExecuteResult.ShortCircuit(
                            IdkResult.err(
                                CommandErrors.notAuthorized(
                                    commandId = CommandId("core.session.command-execute"),
                                    reason = "policy denied",
                                ),
                            ),
                        )
                }
            val command =
                TestableCommandAdapter(
                    id = "core.session.command-execute",
                    enhancedExecutionExtensions = arrayOf(extension),
                )

            val result = command.execute("args")

            assertTrue(result.isErr)
            assertEquals("COMMAND_NOT_AUTHORIZED", result.error.code)
            assertEquals(0, command.executeCallCount)
        }

    @Test
    fun defaultSubsystemIsCustom() {
        val command = TestableCommandAdapter()
        assertEquals(EventSubsystems.CUSTOM, command.subsystem)
    }

    @Test
    fun subsystemCanBeOverridden() {
        val command = TestableCommandAdapter(subsystem = EventSubsystems.KMS)
        assertEquals(EventSubsystems.KMS, command.subsystem)
    }
}

class ChainCommandConfigTest {
    @Test
    fun defaultMinExecutionsIsOne() {
        val config =
            object : IChainCommandConfig {
                override val maxExecutions: Int? = 5
            }
        assertEquals(1, config.minExecutions)
    }

    @Test
    fun maxExecutionsCanBeNull() {
        val config =
            object : IChainCommandConfig {
                override val maxExecutions: Int? = null
            }
        assertEquals(null, config.maxExecutions)
    }

    @Test
    fun maxExecutionsCanBeSet() {
        val config =
            object : IChainCommandConfig {
                override val maxExecutions: Int? = 10
            }
        assertEquals(10, config.maxExecutions)
    }
}

class CommandExecutionPhaseTest {
    @Test
    fun beforePhaseExists() {
        assertEquals("BEFORE", CommandExecutionPhase.BEFORE.name)
    }

    @Test
    fun duringPhaseExists() {
        assertEquals("DURING", CommandExecutionPhase.DURING.name)
    }

    @Test
    fun afterPhaseExists() {
        assertEquals("AFTER", CommandExecutionPhase.AFTER.name)
    }

    @Test
    fun hasThreePhases() {
        assertEquals(3, CommandExecutionPhase.entries.size)
    }
}

class CommandSupportsTest {
    @Test
    fun supportsInterfaceCanBeImplemented() =
        runTest {
            val supports =
                CommandSupports { args ->
                    args is String && args.startsWith("valid")
                }

            assertTrue(supports.supports("valid-input"))
            assertFalse(supports.supports("invalid-input"))
            assertFalse(supports.supports(123))
        }
}
