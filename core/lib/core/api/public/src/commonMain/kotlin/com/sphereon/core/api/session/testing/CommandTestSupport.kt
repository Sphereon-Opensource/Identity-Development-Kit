/*
 * Â© 2026 Sphereon International B.V.
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

package com.sphereon.core.api.session.testing

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.session.BaseCommand
import com.sphereon.core.api.session.Command
import com.sphereon.core.api.session.CommandErrorMapper
import com.sphereon.core.api.session.CommandId
import com.sphereon.core.api.session.IdkErrorCommandErrorMapper
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext

/**
 * Testing utilities for command testing.
 *
 * Provides pre-configured test instances and helper functions for
 * writing unit tests for commands.
 *
 * Example usage:
 * ```kotlin
 * import com.sphereon.core.api.session.testing.CommandTestSupport.testContext
 * import com.sphereon.core.api.session.testing.CommandTestSupport.testExecute
 *
 * class MyCommandTest {
 *     @Test
 *     fun testCommandExecution() = runTest {
 *         val command = MyCommand()
 *         val result = command.testExecute(MyArgs("hello"))
 *
 *         assertTrue(result.isOk)
 *         assertEquals("HELLO", result.value)
 *     }
 * }
 * ```
 */
object CommandTestSupport {
    /**
     * A no-op session context for testing.
     *
     * Use this when testing commands that don't depend on session state.
     * This is an alias for [NoOpSessionContext].
     */
    val testContext: SessionContext = NoOpSessionContext

    /**
     * Default error mapper for testing.
     *
     * Uses [IdkErrorCommandErrorMapper] which maps to [IdkError].
     */
    val testErrorMapper: CommandErrorMapper<IdkError> = IdkErrorCommandErrorMapper

    /**
     * Executes a command with the test context.
     *
     * This is a convenience extension for testing commands without
     * needing to manually pass a session context.
     *
     * Example:
     * ```kotlin
     * val result = myCommand.testExecute(args)
     * assertTrue(result.isOk)
     * ```
     *
     * @param args The command arguments
     * @return The execution result
     */
    suspend fun <A : Any, R : Any, E : IdkErrorType> BaseCommand<A, R, E>.testExecute(args: A): IdkResult<R, E> = execute(args)

    /**
     * Checks if a command supports the given arguments using the
     * supports contract.
     *
     * @param args The arguments to check
     * @return true if the command supports the arguments
     */
    suspend fun <A : Any, R : Any, E : IdkErrorType> BaseCommand<A, R, E>.testSupports(args: Any): Boolean = supports(args)
}

/**
 * Asserts that a result is Ok and returns the value.
 *
 * Throws [AssertionError] if the result is Err.
 *
 * Example:
 * ```kotlin
 * val result = command.testExecute(args)
 * val value = result.assertOkOrFail()
 * assertEquals(expected, value)
 * ```
 *
 * @param message Optional message to include in failure
 * @return The success value
 * @throws AssertionError if the result is Err
 */
fun <R : Any, E : IdkErrorType> IdkResult<R, E>.assertOkOrFail(message: String = "Expected Ok"): R {
    if (isErr) {
        val errorInfo = errorOrNull()?.let { "${it.code}: ${it.message}" } ?: "unknown error"
        throw AssertionError("$message but got Err: $errorInfo")
    }
    return value
}

/**
 * Asserts that a result is Err and returns the error.
 *
 * Throws [AssertionError] if the result is Ok.
 *
 * Example:
 * ```kotlin
 * val result = command.testExecute(invalidArgs)
 * val error = result.assertErrOrFail()
 * assertEquals("INVALID_ARGUMENT", error.code)
 * ```
 *
 * @param message Optional message to include in failure
 * @return The error value
 * @throws AssertionError if the result is Ok
 */
fun <R : Any, E : IdkErrorType> IdkResult<R, E>.assertErrOrFail(message: String = "Expected Err"): E {
    if (isOk) {
        throw AssertionError("$message but got Ok: ${getOrNull()}")
    }
    return error
}

/**
 * Converts a result to an Ok if successful, or returns an IdkError describing the original error.
 *
 * Useful for converting between error types in test assertions.
 *
 * @param message The message prefix for the converted error
 * @return Ok with the value, or Err with an IdkError
 */
fun <R : Any, E : IdkErrorType> IdkResult<R, E>.expectOk(message: String = "Expected Ok"): IdkResult<R, IdkError> =
    if (isOk) {
        Ok(value)
    } else {
        val errorInfo = errorOrNull()?.let { "${it.code}: ${it.message}" } ?: "unknown error"
        Err(IdkError.UNKNOWN_ERROR(message = "$message but got Err: $errorInfo"))
    }

/**
 * Converts an Err result to an Ok containing the error, or returns an IdkError if Ok.
 *
 * Useful for asserting expected error scenarios.
 *
 * @param message The message prefix for unexpected success
 * @return Ok with the error value, or Err with an IdkError
 */
fun <R : Any, E : IdkErrorType> IdkResult<R, E>.expectErr(message: String = "Expected Err"): IdkResult<E, IdkError> =
    if (isErr) {
        Ok(error)
    } else {
        Err(IdkError.UNKNOWN_ERROR(message = "$message but got Ok: ${getOrNull()}"))
    }

/**
 * Creates a mock command for testing purposes.
 *
 * This is useful for creating test doubles when testing command
 * composition, chaining, or other patterns.
 *
 * Example:
 * ```kotlin
 * val mockCommand = mockCommand<String, Int>("test.mock.execute") { args ->
 *     Ok(args.length)
 * }
 *
 * val result = mockCommand.testExecute("hello")
 * assertEquals(Ok(5), result)
 * ```
 *
 * @param id The command ID (defaults to a test ID)
 * @param supports The supports function (defaults to always true)
 * @param allowContextSensitiveSupportsBridge Opt-in compatibility bridge for context-sensitive legacy predicates
 * @param execute The execute function
 * @return A [Command] for testing
 */
fun <A : Any, R : Any> mockCommand(
    id: String = "test.mock.execute",
    supports: suspend (Any) -> Boolean = { true },
    execute: suspend (A) -> IdkResult<R, IdkError>,
): Command<A, R, IdkError> =
    object : Command<A, R, IdkError> {
        override val id: String = id
        override val isEnabled: Boolean = true
        override val subsystem: EventSubsystem = EventSubsystems.CUSTOM

        override suspend fun supports(args: Any): Boolean = supports(args)

        override suspend fun execute(args: A): IdkResult<R, IdkError> = execute(args)
    }

/**
 * Creates a mock command that always succeeds with the given value.
 *
 * @param id The command ID
 * @param result The fixed result to return
 * @return A [Command] that always returns Ok(result)
 */
fun <A : Any, R : Any> succeedingCommand(
    id: String = "test.mock.succeed",
    result: R,
): Command<A, R, IdkError> = mockCommand(id) { _ -> Ok(result) }

/**
 * Creates a mock command that always fails with the given error.
 *
 * @param id The command ID
 * @param error The error to return
 * @return A [Command] that always returns Err(error)
 */
fun <A : Any, R : Any> failingCommand(
    id: String = "test.mock.fail",
    error: IdkError = IdkError.UNKNOWN_ERROR(message = "Test error"),
): Command<A, R, IdkError> = mockCommand(id) { _ -> Err(error) }

/**
 * Creates a mock command with a supports predicate based on a type check.
 *
 * @param id The command ID
 * @param supportedType The class of supported arguments
 * @param execute The execute function
 * @return A [Command] that only supports arguments of the given type
 */
inline fun <reified A : Any, R : Any> typedMockCommand(
    id: String = "test.mock.typed",
    crossinline execute: suspend (A) -> IdkResult<R, IdkError>,
): Command<A, R, IdkError> =
    mockCommand(
        id = id,
        supports = { args -> args is A },
        execute = { args -> execute(args) },
    )
