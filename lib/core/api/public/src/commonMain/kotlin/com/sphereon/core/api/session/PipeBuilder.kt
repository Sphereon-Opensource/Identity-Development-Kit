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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventSubsystems

/**
 * A type-safe builder for constructing command pipelines.
 *
 * The PipeBuilder provides a fluent API for chaining commands while maintaining
 * compile-time type safety. Each step in the pipeline transforms the data,
 * with the output of one step becoming the input of the next.
 *
 * Example:
 * ```kotlin
 * val pipeline = pipe("my.pipeline.process", parseCommand)
 *     .then(validateCommand)
 *     .thenMapSimple { result -> result.copy(processed = true) }
 *     .build()
 * ```
 *
 * @param In The input type for the pipeline
 * @param Current The current output type (changes as commands are chained)
 * @param E The error type
 */
class PipeBuilder<In : Any, Current : Any, E : IdkErrorType> private constructor(
    private val id: String,
    private val command: BaseCommand<In, Current, E>,
    private val errorMapper: CommandErrorMapper<E>,
) {
    /**
     * Chains another command to the pipeline.
     *
     * @param next The command to add to the pipeline
     * @return A new PipeBuilder with the chained command
     */
    fun <Next : Any> then(next: BaseCommand<Current, Next, E>): PipeBuilder<In, Next, E> = PipeBuilder(id, command.chain("$id-step", next, errorMapper), errorMapper)

    /**
     * Adds a transformation step to the pipeline.
     *
     * @param transform Function to transform the current value
     * @return A new PipeBuilder with the transformation
     */
    fun <Next : Any> thenMap(transform: suspend (Current) -> IdkResult<Next, E>): PipeBuilder<In, Next, E> = PipeBuilder(id, command.mapOutput(transform), errorMapper)

    /**
     * Adds a simple transformation step without session context.
     *
     * @param transform Function to transform the current value
     * @return A new PipeBuilder with the transformation
     */
    fun <Next : Any> thenMapSimple(transform: suspend (Current) -> Next): PipeBuilder<In, Next, E> = thenMap { current -> Ok(transform(current)) }

    /**
     * Adds a filtering step to the pipeline.
     * Returns an error if the predicate fails.
     *
     * @param predicate Function to test the current value
     * @param onFailure Function to create an error when predicate fails
     * @return A new PipeBuilder with the filter
     */
    fun filter(
        predicate: suspend (Current) -> Boolean,
        onFailure: suspend (Current) -> E,
    ): PipeBuilder<In, Current, E> = PipeBuilder(id, command.filterOutput(predicate, onFailure), errorMapper)

    /**
     * Adds a side effect step that doesn't modify the data.
     *
     * @param effect Side effect to run
     * @return A new PipeBuilder (same types)
     */
    fun tap(effect: suspend (Current) -> Unit): PipeBuilder<In, Current, E> = PipeBuilder(id, command.onSuccess(effect), errorMapper)

    /**
     * Builds the final pipeline command.
     *
     * @return A Command that executes the entire pipeline
     */
    fun build(): Command<In, Current, E> = NamedCommand(id, command)

    companion object {
        /**
         * Starts a new pipeline with the given command.
         *
         * @param id The command ID for the resulting pipeline
         * @param first The first command in the pipeline
         * @param errorMapper The error mapper for handling errors
         * @return A new PipeBuilder
         */
        fun <I : Any, O : Any, E : IdkErrorType> start(
            id: String,
            first: BaseCommand<I, O, E>,
            errorMapper: CommandErrorMapper<E>,
        ): PipeBuilder<I, O, E> = PipeBuilder(id, first, errorMapper)
    }
}

/**
 * A command wrapper that provides a specific ID and implements the Command interface.
 */
private class NamedCommand<I : Any, O : Any, E : IdkErrorType>(
    override val id: String,
    private val delegate: BaseCommand<I, O, E>,
    override val isEnabled: Boolean = true,
    override val subsystem: EventSubsystem = EventSubsystems.CUSTOM,
) : Command<I, O, E> {
    override suspend fun supports(args: Any) = delegate.supports(args)

    override suspend fun execute(args: I) = delegate.execute(args)
}

/**
 * Creates a new pipeline builder starting with the given command.
 *
 * @param id The command ID for the resulting pipeline
 * @param first The first command in the pipeline
 * @param errorMapper The error mapper for handling errors
 * @return A new PipeBuilder
 */
fun <I : Any, O : Any, E : IdkErrorType> pipe(
    id: String,
    first: BaseCommand<I, O, E>,
    errorMapper: CommandErrorMapper<E>,
) = PipeBuilder.start(id, first, errorMapper)

/**
 * Convenience function for IdkError pipelines that uses the default error mapper.
 *
 * @param id The command ID for the resulting pipeline
 * @param first The first command in the pipeline
 * @return A new PipeBuilder with IdkError error type
 */
fun <I : Any, O : Any> pipe(
    id: String,
    first: BaseCommand<I, O, IdkError>,
) = PipeBuilder.start(id, first, IdkErrorCommandErrorMapper)

/**
 * Creates a command from a simple transformation function.
 * Useful for inline steps in a pipeline.
 *
 * @param id The command ID
 * @param transform The transformation function
 * @return A Command that applies the transformation
 */
fun <I : Any, O : Any, E : IdkErrorType> transformCommand(
    id: String,
    transform: suspend (I) -> IdkResult<O, E>,
): Command<I, O, E> =
    object : Command<I, O, E> {
        override val id = id
        override val isEnabled = true
        override val subsystem = EventSubsystems.CUSTOM

        override suspend fun supports(args: Any) = true

        override suspend fun execute(args: I) = transform(args)
    }

/**
 * Creates a command from a simple transformation function (IdkError convenience).
 *
 * @param id The command ID
 * @param transform The transformation function
 * @return A Command that applies the transformation
 */
fun <I : Any, O : Any> transformCommandSimple(
    id: String,
    transform: suspend (I) -> O,
): Command<I, O, IdkError> =
    object : Command<I, O, IdkError> {
        override val id = id
        override val isEnabled = true
        override val subsystem = EventSubsystems.CUSTOM

        override suspend fun supports(args: Any) = true

        override suspend fun execute(args: I) = Ok(transform(args))
    }
