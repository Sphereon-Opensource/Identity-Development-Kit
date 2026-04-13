/*
 * Â© 2025 Sphereon International B.V.
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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventSubsystems
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Simplified command interface for commands that don't need SessionContext.
 *
 * This interface provides a lighter-weight alternative to [Command] for
 * simple use cases where session context is not required. It's particularly
 * useful for:
 * - Pure transformations
 * - Utility commands
 * - Testing
 * - Prototyping
 *
 * Example usage:
 * ```kotlin
 * class UppercaseCommand : SimpleCommand<String, String> {
 *     override val id = "util.string.uppercase"
 *
 *     override suspend fun execute(args: String): IdkResult<String, IdkError> {
 *         return Ok(args.uppercase())
 *     }
 * }
 *
 * // Convert to full Command when needed:
 * val fullCommand: Command<String, String, IdkError> = UppercaseCommand().asCommand()
 * ```
 *
 * @param Arg The type of the input argument
 * @param Result The type of the success result
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SimpleCommand", exact = true)
interface SimpleCommand<Arg : Any, Result : Any> {
    /**
     * The unique identifier for this command.
     *
     * Should follow the hierarchical command ID format:
     * `<module>.<service>.<action>`
     */
    val id: String

    /**
     * Executes the command with the given arguments.
     *
     * @param args The input arguments
     * @return The result of execution
     */
    suspend fun execute(args: Arg): IdkResult<Result, IdkError>

    /**
     * Checks if this command supports the given arguments.
     *
     * Override this method to implement argument validation or type checking.
     * The default implementation returns true for all arguments.
     *
     * @param args The arguments to check
     * @return true if the arguments are supported
     */
    fun supports(args: Any): Boolean = true
}

/**
 * Converts a [SimpleCommand] to a full [Command].
 *
 * The resulting command:
 * - Ignores the SessionContext (the simple command's execute is called directly)
 * - Uses the simple command's id
 * - Is always enabled
 * - Uses CUSTOM subsystem by default
 *
 * Example:
 * ```kotlin
 * val simpleCommand = object : SimpleCommand<String, Int> {
 *     override val id = "util.string.length"
 *     override suspend fun execute(args: String) = Ok(args.length)
 * }
 *
 * val fullCommand = simpleCommand.asCommand()
 * val result = fullCommand.execute("hello")  // Returns Ok(5)
 * ```
 *
 * @param subsystem The event subsystem to use (defaults to CUSTOM)
 * @return A [Command] that wraps this simple command
 */
fun <A : Any, R : Any> SimpleCommand<A, R>.asCommand(
    subsystem: EventSubsystem = EventSubsystems.CUSTOM
): Command<A, R, IdkError> = object : Command<A, R, IdkError> {
    override val id: String = this@asCommand.id
    override val isEnabled: Boolean = true
    override val subsystem: EventSubsystem = subsystem

    override suspend fun supports(args: Any): Boolean =
        this@asCommand.supports(args)

    override suspend fun execute(args: A): IdkResult<R, IdkError> =
        this@asCommand.execute(args)
}

/**
 * Converts a [SimpleCommand] to a full [Command] with a custom error type.
 *
 * Use this variant when you need to work with a command infrastructure
 * that uses a custom error type other than [IdkError].
 *
 * @param errorMapper Function to map IdkError to the target error type
 * @param subsystem The event subsystem to use (defaults to CUSTOM)
 * @return A [Command] that wraps this simple command with mapped errors
 */
@Suppress("UNCHECKED_CAST")
fun <A : Any, R : Any, E : com.sphereon.core.api.error.IdkErrorType> SimpleCommand<A, R>.asCommandWithError(
    errorMapper: (IdkError) -> E,
    subsystem: EventSubsystem = EventSubsystems.CUSTOM
): Command<A, R, E> = object : Command<A, R, E> {
    override val id: String = this@asCommandWithError.id
    override val isEnabled: Boolean = true
    override val subsystem: EventSubsystem = subsystem

    override suspend fun supports(args: Any): Boolean =
        this@asCommandWithError.supports(args)

    override suspend fun execute(args: A): IdkResult<R, E> {
        val result = this@asCommandWithError.execute(args)
        return if (result.isOk) {
            IdkResult.ok(result.value)
        } else {
            IdkResult.err(errorMapper(result.error))
        }
    }
}

/**
 * Creates a simple command from a function.
 *
 * This is a convenience function for creating simple commands inline
 * without defining a class.
 *
 * Example:
 * ```kotlin
 * val lengthCommand = simpleCommand("util.string.length") { str: String ->
 *     Ok(str.length)
 * }
 * ```
 *
 * @param id The command ID
 * @param supports Optional supports check (defaults to always true)
 * @param execute The execution function
 * @return A [SimpleCommand] instance
 */
inline fun <reified A : Any, R : Any> simpleCommand(
    id: String,
    crossinline supports: (Any) -> Boolean = { true },
    crossinline execute: suspend (A) -> IdkResult<R, IdkError>
): SimpleCommand<A, R> = object : SimpleCommand<A, R> {
    override val id: String = id
    override fun supports(args: Any): Boolean = supports(args)
    override suspend fun execute(args: A): IdkResult<R, IdkError> = execute(args)
}

