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
 *
 */

package com.sphereon.core.api.session

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.session.testing.CommandTestSupport.testContext
import com.sphereon.di.session.SessionContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChainedCommandTest {
    // Helper to create simple test commands
    private fun <A : Any, R : Any> testCommand(
        commandId: String,
        supportsFn: (Any) -> Boolean = { true },
        executeFn: suspend (A) -> IdkResult<R, IdkError>,
    ): Command<A, R, IdkError> =
        object : Command<A, R, IdkError> {
            override val id = commandId
            override val isEnabled = true
            override val subsystem = EventSubsystems.CUSTOM

            override suspend fun supports(args: Any) = supportsFn(args)

            override suspend fun execute(args: A) = executeFn(args)
        }

    @Test
    fun chainedCommandExecutesBothCommandsInSequence() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.chain.string.parse") { input ->
                    Ok(input.length)
                }

            val doubleCommand =
                testCommand<Int, Int>("test.chain.int.double") { input ->
                    Ok(input * 2)
                }

            val chained =
                ChainedCommand(
                    id = "test.chain.string.doubled",
                    first = parseCommand,
                    second = doubleCommand,
                    errorMapper = IdkErrorCommandErrorMapper,
                )

            val result = chained.execute("hello")
            assertTrue(result.isOk)
            assertEquals(10, result.value) // "hello".length * 2 = 5 * 2 = 10
        }

    @Test
    fun chainedCommandReturnsErrorWhenFirstCommandFails() =
        runTest {
            val failingCommand =
                testCommand<String, Int>("test.chain.string.fail") { _ ->
                    Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "First command failed"))
                }

            val doubleCommand =
                testCommand<Int, Int>("test.chain.int.double") { input ->
                    Ok(input * 2)
                }

            val chained =
                ChainedCommand(
                    id = "test.chain.string.doubled",
                    first = failingCommand,
                    second = doubleCommand,
                    errorMapper = IdkErrorCommandErrorMapper,
                )

            val result = chained.execute("hello")
            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("First command failed"),
            )
        }

    @Test
    fun chainedCommandReturnsErrorWhenSecondCommandDoesNotSupportIntermediate() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.chain.string.parse") { input ->
                    Ok(input.length)
                }

            val selectiveCommand =
                testCommand<Int, Int>(
                    commandId = "test.chain.int.selective",
                    supportsFn = { it is Int && it > 10 }, // Only supports integers > 10
                    executeFn = { input -> Ok(input * 2) },
                )

            val chained =
                ChainedCommand(
                    id = "test.chain.string.selective",
                    first = parseCommand,
                    second = selectiveCommand,
                    errorMapper = IdkErrorCommandErrorMapper,
                )

            // "hello" has length 5, which doesn't meet > 10 requirement
            val result = chained.execute("hello")
            assertTrue(result.isErr)
            assertEquals("COMMAND_ARG_NOT_SUPPORTED_ERROR", result.error.code)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("does not support"),
            )
        }

    @Test
    fun chainedCommandPropagatesAuthorizationFailureFromFirstCommand() =
        runTest {
            var secondExecuted = false
            val unauthorizedCommand =
                testCommand<String, Int>("test.chain.string.authz") { _ ->
                    Err(
                        IdkError.COMMAND_NOT_AUTHORIZED_ERROR(
                            commandId = "test.chain.string.authz",
                            reason = "Denied by policy",
                        ),
                    )
                }
            val secondCommand =
                testCommand<Int, Int>("test.chain.int.next") { input ->
                    secondExecuted = true
                    Ok(input * 2)
                }
            val chained =
                ChainedCommand(
                    id = "test.chain.authz.propagation",
                    first = unauthorizedCommand,
                    second = secondCommand,
                    errorMapper = IdkErrorCommandErrorMapper,
                )

            val result = chained.execute("hello")

            assertTrue(result.isErr)
            assertEquals("COMMAND_NOT_AUTHORIZED", result.error.code)
            assertFalse(secondExecuted)
        }

    @Test
    fun chainedCommandReturnsErrorWhenSecondCommandFails() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.chain.string.parse") { input ->
                    Ok(input.length)
                }

            val failingCommand =
                testCommand<Int, Int>("test.chain.int.fail") { _ ->
                    Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Second command failed"))
                }

            val chained =
                ChainedCommand(
                    id = "test.chain.string.fail",
                    first = parseCommand,
                    second = failingCommand,
                    errorMapper = IdkErrorCommandErrorMapper,
                )

            val result = chained.execute("hello")
            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("Second command failed"),
            )
        }

    @Test
    fun chainedCommandUsesContextFreePrimaryForSecondSupports() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.chain.legacy.string.parse") { input ->
                    Ok(input.length)
                }

            val legacySecond =
                object : Command<Int, Int, IdkError> {
                    override val id = "test.chain.double"
                    override val isEnabled = true
                    override val subsystem = EventSubsystems.CUSTOM

                    override suspend fun supports(args: Any): Boolean = args is Int && args > 10

                    override suspend fun execute(args: Int): IdkResult<Int, IdkError> = Ok(args * 2)
                }

            val chained =
                ChainedCommand(
                    id = "test.chain.legacy.bridge",
                    first = parseCommand,
                    second = legacySecond,
                    errorMapper = IdkErrorCommandErrorMapper,
                )

            val result = chained.execute("hello")
            assertTrue(result.isErr)
            assertEquals("COMMAND_ARG_NOT_SUPPORTED_ERROR", result.error.code)
        }

    @Test
    fun chainedCommandSupportsReturnsFirstCommandSupports() =
        runTest {
            val selectiveFirst =
                testCommand<String, Int>(
                    commandId = "test.chain.string.selective",
                    supportsFn = { it is String && it.length > 3 },
                    executeFn = { input -> Ok(input.length) },
                )

            val doubleCommand =
                testCommand<Int, Int>("test.chain.int.double") { input ->
                    Ok(input * 2)
                }

            val chained =
                ChainedCommand(
                    id = "test.chain.string.doubled",
                    first = selectiveFirst,
                    second = doubleCommand,
                    errorMapper = IdkErrorCommandErrorMapper,
                )

            assertTrue(chained.supports("hello"))
            assertFalse(chained.supports("hi"))
            assertFalse(chained.supports(123))
        }

    @Test
    fun chainedCommandHasCorrectIdAndSubsystem() {
        val parseCommand =
            testCommand<String, Int>("test.chain.string.parse") { input ->
                Ok(input.length)
            }

        val doubleCommand =
            testCommand<Int, Int>("test.chain.int.double") { input ->
                Ok(input * 2)
            }

        val chained =
            ChainedCommand(
                id = "test.chain.combined.process",
                first = parseCommand,
                second = doubleCommand,
                errorMapper = IdkErrorCommandErrorMapper,
            )

        assertEquals("test.chain.combined.process", chained.id)
        assertEquals(EventSubsystems.CUSTOM, chained.subsystem)
        assertTrue(chained.isEnabled)
    }

    @Test
    fun chainExtensionFunctionCreatesChainedCommand() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.chain.string.parse") { input ->
                    Ok(input.length)
                }

            val doubleCommand =
                testCommand<Int, Int>("test.chain.int.double") { input ->
                    Ok(input * 2)
                }

            val chained = parseCommand.chain("test.chain.string.doubled", doubleCommand)

            val result = chained.execute("hello")
            assertTrue(result.isOk)
            assertEquals(10, result.value)
        }

    @Test
    fun chainAllWithEmptyListThrows() {
        assertFailsWith<IllegalArgumentException> {
            chainAll<String, Int, IdkError>(
                id = "test.chain.empty.list",
                commands = emptyList(),
                errorMapper = IdkErrorCommandErrorMapper,
            )
        }
    }

    @Test
    fun chainAllWithSingleCommandWrapsIt() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.chain.string.parse") { input ->
                    Ok(input.length)
                }

            val chained =
                chainAll<String, Int, IdkError>(
                    id = "test.chain.single.command",
                    commands = listOf(parseCommand),
                    errorMapper = IdkErrorCommandErrorMapper,
                )

            assertEquals("test.chain.single.command", chained.id)

            val result = chained.execute("hello")
            assertTrue(result.isOk)
            assertEquals(5, result.value)
        }

    @Test
    fun chainAllWithMultipleCommandsChainsAll() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.chain.string.parse") { input ->
                    Ok(input.length)
                }

            val doubleCommand =
                testCommand<Int, Int>("test.chain.int.double") { input ->
                    Ok(input * 2)
                }

            val addTenCommand =
                testCommand<Int, Int>("test.chain.int.add") { input ->
                    Ok(input + 10)
                }

            val chained =
                chainAll<String, Int, IdkError>(
                    id = "test.chain.multi.pipeline",
                    commands = listOf(parseCommand, doubleCommand, addTenCommand),
                    errorMapper = IdkErrorCommandErrorMapper,
                )

            assertEquals("test.chain.multi.pipeline", chained.id)

            val result = chained.execute("hello")
            assertTrue(result.isOk)
            // "hello".length = 5, *2 = 10, +10 = 20
            assertEquals(20, result.value)
        }

    @Test
    fun chainAllSupportsForwardsToFirstCommand() =
        runTest {
            val selectiveFirst =
                testCommand<String, Int>(
                    commandId = "test.chain.string.selective",
                    supportsFn = { it is String && it.startsWith("test") },
                    executeFn = { input -> Ok(input.length) },
                )

            val doubleCommand =
                testCommand<Int, Int>("test.chain.int.double") { input ->
                    Ok(input * 2)
                }

            val chained =
                chainAll<String, Int, IdkError>(
                    id = "test.chain.multi.pipeline",
                    commands = listOf(selectiveFirst, doubleCommand),
                    errorMapper = IdkErrorCommandErrorMapper,
                )

            assertTrue(chained.supports("testing"))
            assertFalse(chained.supports("hello"))
        }

    @Test
    fun chainedCommandCanBeChainedFurther() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.chain.string.parse") { input ->
                    Ok(input.length)
                }

            val doubleCommand =
                testCommand<Int, Int>("test.chain.int.double") { input ->
                    Ok(input * 2)
                }

            val toStringCommand =
                testCommand<Int, String>("test.chain.int.string") { input ->
                    Ok("Result: $input")
                }

            // Chain three commands
            val chained =
                parseCommand
                    .chain("test.chain.step.one", doubleCommand)
                    .chain("test.chain.step.two", toStringCommand)

            val result = chained.execute("hello")
            assertTrue(result.isOk)
            assertEquals("Result: 10", result.value)
        }

    @Test
    fun chainedCommandWithDisabledFlagRespectsSetting() {
        val parseCommand =
            testCommand<String, Int>("test.chain.string.parse") { input ->
                Ok(input.length)
            }

        val doubleCommand =
            testCommand<Int, Int>("test.chain.int.double") { input ->
                Ok(input * 2)
            }

        val disabled =
            ChainedCommand(
                id = "test.chain.string.disabled",
                first = parseCommand,
                second = doubleCommand,
                errorMapper = IdkErrorCommandErrorMapper,
                isEnabled = false,
            )

        assertFalse(disabled.isEnabled)
    }
}
