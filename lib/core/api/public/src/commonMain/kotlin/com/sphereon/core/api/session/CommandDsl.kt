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

/**
 * DSL marker for command building to prevent scope leakage.
 */
@DslMarker
annotation class CommandDslMarker

/**
 * Builder for creating commands using a type-safe DSL.
 *
 * This builder allows you to construct commands declaratively with
 * compile-time type safety.
 *
 * Example:
 * ```kotlin
 * val greetCommand = command<String, String>("util.greeting.create") {
 *     subsystem(EventSubsystems.CUSTOM)
 *
 *     supports { args ->
 *         args is String && args.isNotBlank()
 *     }
 *
 *     execute { name, _ ->
 *         Ok("Hello, $name!")
 *     }
 * }
 * ```
 *
 * @param Arg The command argument type
 * @param Result The command result type
 * @property id The command ID
 */
@CommandDslMarker
class CommandBuilder<Arg : Any, Result : Any>(private val id: String) {
    private var supportsFn: suspend (Any) -> Boolean = { true }
    private var executeFn: (suspend (Arg) -> IdkResult<Result, IdkError>)? = null
    private var subsystemValue: EventSubsystem = EventSubsystems.CUSTOM
    private var isEnabledValue: Boolean = true

    /**
     * Sets the supports predicate for the command.
     *
     * The supports function determines whether the command can handle
     * the given arguments. This is called before execute.
     *
     * @param fn A suspend function that returns true if the arguments are supported
     */
    fun supports(fn: suspend (Any) -> Boolean) {
        supportsFn = fn
    }

    /**
     * Sets the execute function for the command.
     *
     * This is the main logic of the command. It receives typed arguments
     * and returns an IdkResult.
     *
     * @param fn The execution function
     */
    fun execute(fn: suspend (Arg) -> IdkResult<Result, IdkError>) {
        executeFn = fn
    }

    /**
     * Sets the event subsystem for the command.
     *
     * The subsystem is used for event tagging and categorization.
     *
     * @param subsystem The event subsystem
     */
    fun subsystem(subsystem: EventSubsystem) {
        subsystemValue = subsystem
    }

    /**
     * Sets whether the command is enabled.
     *
     * Disabled commands will return an error when executed.
     *
     * @param enabled Whether the command is enabled
     */
    fun enabled(enabled: Boolean) {
        isEnabledValue = enabled
    }

    /**
     * Builds the command from the configured properties.
     *
     * @return A [Command] instance
     * @throws IllegalStateException if execute block was not provided
     */
    fun build(): Command<Arg, Result, IdkError> {
        val exec = executeFn
            ?: error("execute block is required for command '$id'")

        return object : Command<Arg, Result, IdkError> {
            override val id: String = this@CommandBuilder.id
            override val isEnabled: Boolean = isEnabledValue
            override val subsystem: EventSubsystem = subsystemValue

            override suspend fun supports(args: Any): Boolean =
                supportsFn(args)

            override suspend fun execute(args: Arg): IdkResult<Result, IdkError> =
                exec(args)
        }
    }
}

/**
 * Creates a command using a type-safe DSL.
 *
 * This is the main entry point for the command DSL. Use it to define
 * commands declaratively with compile-time type checking.
 *
 * Basic example:
 * ```kotlin
 * val lengthCommand = command<String, Int>("util.string.length") {
 *     execute { str -> Ok(str.length) }
 * }
 * ```
 *
 * Full example with all options:
 * ```kotlin
 * val userCommand = command<UserId, User>("data.user.get") {
 *     subsystem(EventSubsystems.CUSTOM)
 *     enabled(true)
 *
 *     supports { args ->
 *         args is UserId
 *     }
 *
 *     execute { userId ->
 *         userRepository.findById(userId)
 *             ?.let { Ok(it) }
 *             ?: Err(IdkError.NOT_FOUND_ERROR(message = "User not found: $userId"))
 *     }
 * }
 * ```
 *
 * @param Arg The argument type (reified for type checking)
 * @param Result The result type
 * @param id The command ID (should follow hierarchical format)
 * @param block The builder configuration block
 * @return A configured [Command] instance
 */
inline fun <reified Arg : Any, Result : Any> command(
    id: String,
    block: CommandBuilder<Arg, Result>.() -> Unit
): Command<Arg, Result, IdkError> = CommandBuilder<Arg, Result>(id).apply(block).build()

/**
 * Builder for simple commands (without SessionContext) using DSL.
 *
 * This is a lighter-weight alternative to [CommandBuilder] for commands
 * that don't need access to the session context.
 *
 * Example:
 * ```kotlin
 * val parseCommand = simpleCommand<String, Int>("util.integer.parse") {
 *     supports { args -> args is String && args.toIntOrNull() != null }
 *
 *     execute { str ->
 *         str.toIntOrNull()
 *             ?.let { Ok(it) }
 *             ?: Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid integer: $str"))
 *     }
 * }
 * ```
 *
 * @param Arg The command argument type
 * @param Result The command result type
 * @property id The command ID
 */
@CommandDslMarker
class SimpleCommandBuilder<Arg : Any, Result : Any>(private val id: String) {
    private var supportsFn: (Any) -> Boolean = { true }
    private var executeFn: (suspend (Arg) -> IdkResult<Result, IdkError>)? = null

    /**
     * Sets the supports predicate for the command.
     *
     * @param fn A function that returns true if the arguments are supported
     */
    fun supports(fn: (Any) -> Boolean) {
        supportsFn = fn
    }

    /**
     * Sets the execute function for the command.
     *
     * @param fn The execution function (does not receive SessionContext)
     */
    fun execute(fn: suspend (Arg) -> IdkResult<Result, IdkError>) {
        executeFn = fn
    }

    /**
     * Builds the simple command from the configured properties.
     *
     * @return A [SimpleCommand] instance
     * @throws IllegalStateException if execute block was not provided
     */
    fun build(): SimpleCommand<Arg, Result> {
        val exec = executeFn
            ?: error("execute block is required for simple command '$id'")

        return object : SimpleCommand<Arg, Result> {
            override val id: String = this@SimpleCommandBuilder.id
            override fun supports(args: Any): Boolean = supportsFn(args)
            override suspend fun execute(args: Arg): IdkResult<Result, IdkError> = exec(args)
        }
    }
}

/**
 * Creates a simple command using a type-safe DSL.
 *
 * Simple commands don't have access to SessionContext, making them
 * ideal for pure transformations and utility functions.
 *
 * Example:
 * ```kotlin
 * val uppercase = simpleCommandDsl<String, String>("util.string.uppercase") {
 *     execute { str -> Ok(str.uppercase()) }
 * }
 *
 * // Convert to full command when needed:
 * val fullCommand = uppercase.asCommand()
 * ```
 *
 * @param Arg The argument type (reified for type checking)
 * @param Result The result type
 * @param id The command ID
 * @param block The builder configuration block
 * @return A configured [SimpleCommand] instance
 */
inline fun <reified Arg : Any, Result : Any> simpleCommandDsl(
    id: String,
    block: SimpleCommandBuilder<Arg, Result>.() -> Unit
): SimpleCommand<Arg, Result> = SimpleCommandBuilder<Arg, Result>(id).apply(block).build()

