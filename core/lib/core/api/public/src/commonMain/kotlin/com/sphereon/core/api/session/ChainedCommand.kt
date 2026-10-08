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

package com.sphereon.core.api.session

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.session.SessionContext

/**
 * A command that chains two commands together, executing them in sequence.
 *
 * The first command produces an intermediate result which is passed as input to the second command.
 * Unlike the old `andThen()` method, this validates `supports()` on the OUTPUT of the first command,
 * not on the original input, which fixes a fundamental bug in the original implementation.
 *
 * @param FirstArg The input type for the first command
 * @param Intermediate The output type of the first command / input type of the second
 * @param FinalResult The output type of the second command
 * @param E The error type
 */
@JsExportCompat
class ChainedCommand<FirstArg : Any, Intermediate : Any, FinalResult : Any, E : IdkErrorType>(
    override val id: String,
    private val first: BaseCommand<FirstArg, Intermediate, E>,
    private val second: BaseCommand<Intermediate, FinalResult, E>,
    private val errorMapper: CommandErrorMapper<E>,
    override val isEnabled: Boolean = true,
    override val subsystem: EventSubsystem = (first as? Command<*, *, *>)?.subsystem ?: EventSubsystems.CUSTOM,
) : Command<FirstArg, FinalResult, E> {
    override suspend fun supports(args: Any): Boolean = first.supports(args)

    override suspend fun execute(args: FirstArg): IdkResult<FinalResult, E> {
        // Execute the first command
        val firstResult = first.execute(args)
        if (firstResult.isErr) {
            return Err(firstResult.error)
        }

        val intermediate = firstResult.value

        // Validate supports() on the OUTPUT of the first command, not the original input
        // This fixes the bug where andThen() validated on input instead of output
        val supportResult = second.supportsOrError(intermediate, errorMapper)
        if (supportResult.isErr) {
            return Err(supportResult.error)
        }

        // Execute the second command
        return second.execute(intermediate)
    }
}

/**
 * Extension function to chain two commands together.
 * Creates a new ChainedCommand that executes them in sequence.
 *
 * @param id The command ID for the chained command
 * @param next The command to execute after this one
 * @param errorMapper The error mapper to use for creating errors
 * @return A new ChainedCommand
 */
fun <A : Any, I : Any, R : Any, E : IdkErrorType> BaseCommand<A, I, E>.chain(
    id: String,
    next: BaseCommand<I, R, E>,
    errorMapper: CommandErrorMapper<E>,
): ChainedCommand<A, I, R, E> = ChainedCommand(id, this, next, errorMapper)

/**
 * Convenience extension for IdkError that uses the default IdkErrorCommandErrorMapper.
 *
 * @param id The command ID for the chained command
 * @param next The command to execute after this one
 * @return A new ChainedCommand with IdkError as the error type
 */
fun <A : Any, I : Any, R : Any> BaseCommand<A, I, IdkError>.chain(
    id: String,
    next: BaseCommand<I, R, IdkError>,
): ChainedCommand<A, I, R, IdkError> = ChainedCommand(id, this, next, IdkErrorCommandErrorMapper)

/**
 * Extension function to chain multiple commands together.
 * Creates a single command that executes all in sequence.
 *
 * @param id The command ID for the resulting command
 * @param commands The commands to chain
 * @param errorMapper The error mapper to use
 * @return A command that executes all in sequence
 */
@Suppress("UNCHECKED_CAST")
fun <A : Any, R : Any, E : IdkErrorType> chainAll(
    id: String,
    commands: List<BaseCommand<*, *, E>>,
    errorMapper: CommandErrorMapper<E>,
): Command<A, R, E> {
    require(commands.isNotEmpty()) { "Cannot chain empty list of commands" }
    if (commands.size == 1) {
        return object : Command<A, R, E> {
            override val id = id
            override val isEnabled = true
            override val subsystem = EventSubsystems.CUSTOM

            override suspend fun supports(args: Any) = commands[0].supports(args)

            override suspend fun execute(args: A) = (commands[0] as BaseCommand<A, R, E>).execute(args)
        }
    }

    var result: BaseCommand<Any, Any, E> = commands[0] as BaseCommand<Any, Any, E>
    for (i in 1 until commands.size) {
        result =
            ChainedCommand(
                id = "$id-step-$i",
                first = result,
                second = commands[i] as BaseCommand<Any, Any, E>,
                errorMapper = errorMapper,
            )
    }

    return object : Command<A, R, E> {
        override val id = id
        override val isEnabled = true
        override val subsystem = EventSubsystems.CUSTOM

        override suspend fun supports(args: Any) = result.supports(args)

        override suspend fun execute(args: A) = (result as BaseCommand<A, R, E>).execute(args)
    }
}
