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
 *
 */

package com.sphereon.core.api.session

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.Err
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventSubsystems

/**
 * A command that can be compensated (rolled back) if subsequent operations fail.
 *
 * Used in saga patterns where a series of operations must either all succeed
 * or all be rolled back.
 *
 * @param Arg The input argument type
 * @param Result The success result type
 * @param E The error type
 */
interface CompensatableCommand<Arg : Any, Result : Any, E : IdkErrorType> : Command<Arg, Result, E> {
    /**
     * Compensates (rolls back) a previously executed operation.
     *
     * @param args The original arguments passed to execute
     * @param result The result that was produced by execute
     * @return Ok if compensation succeeded, Err otherwise
     */
    suspend fun compensate(args: Arg, result: Result): IdkResult<Unit, E>
}

/**
 * Represents a step in a saga.
 * Type-erased to allow heterogeneous step collections.
 */
sealed interface SagaStep<E : IdkErrorType> {
    /**
     * Executes this step with the given input.
     */
    suspend fun execute(input: Any): IdkResult<Any, E>

    /**
     * Compensates this step if a later step failed.
     *
     * @param input The original input
     * @param output The output that was produced
     */
    suspend fun compensate(input: Any, output: Any): IdkResult<Unit, E>
}

/**
 * A typed saga step that wraps a CompensatableCommand.
 */
class TypedSagaStep<A : Any, R : Any, E : IdkErrorType>(
    private val command: CompensatableCommand<A, R, E>,
    private val errorMapper: CommandErrorMapper<E>
) : SagaStep<E> {

    @Suppress("UNCHECKED_CAST")
    override suspend fun execute(input: Any): IdkResult<Any, E> {
        val typedInput = input as? A
            ?: return Err(errorMapper.unsupportedArg(command, input))
        return command.execute(typedInput).map { it as Any }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun compensate(input: Any, output: Any): IdkResult<Unit, E> {
        val typedInput = input as? A
            ?: return Err(errorMapper.unsupportedArg(command, input))
        val typedOutput = output as? R
            ?: return Err(errorMapper.unsupportedArg(command, output))
        return command.compensate(typedInput, typedOutput)
    }
}

/**
 * A command that executes a saga - a series of compensatable steps.
 * If any step fails, all previously executed steps are compensated in reverse order.
 *
 * @param Arg The input type for the saga
 * @param Result The final result type
 * @param E The error type
 */
class SagaCommandAdapter<Arg : Any, Result : Any, E : IdkErrorType>(
    override val id: String,
    private val steps: List<SagaStep<E>>,
    private val errorMapper: CommandErrorMapper<E>,
    override val isEnabled: Boolean = true,
    override val subsystem: EventSubsystem = EventSubsystems.CUSTOM
) : Command<Arg, Result, E> {

    override suspend fun supports(args: Any): Boolean =
        steps.isNotEmpty()

    @Suppress("UNCHECKED_CAST")
    override suspend fun execute(args: Arg): IdkResult<Result, E> {
        // Track executed steps for potential compensation
        val executed = mutableListOf<Triple<SagaStep<E>, Any, Any>>()
        var current: Any = args

        for (step in steps) {
            val result = step.execute(current)

            if (result.isErr) {
                // Compensate all previously executed steps in reverse order
                for ((s, inArg, outArg) in executed.asReversed()) {
                    // Best effort compensation - we don't fail if compensation fails
                    s.compensate(inArg, outArg)
                }
                return Err(result.error)
            }

            executed.add(Triple(step, current, result.value))
            current = result.value
        }

        // All steps succeeded
        return try {
            Ok(current as Result)
        } catch (e: ClassCastException) {
            Err(errorMapper.unsupportedArg(this, current))
        }
    }
}

/**
 * Creates a typed saga step from a compensatable command.
 *
 * @param command The compensatable command
 * @param errorMapper The error mapper
 * @return A SagaStep
 */
fun <A : Any, R : Any, E : IdkErrorType> sagaStep(
    command: CompensatableCommand<A, R, E>,
    errorMapper: CommandErrorMapper<E>
): SagaStep<E> = TypedSagaStep(command, errorMapper)

/**
 * Convenience function for IdkError saga steps.
 */
fun <A : Any, R : Any> sagaStep(
    command: CompensatableCommand<A, R, IdkError>
): SagaStep<IdkError> = TypedSagaStep(command, IdkErrorCommandErrorMapper)

/**
 * Creates a saga command from a list of steps.
 *
 * @param id The command ID
 * @param errorMapper The error mapper
 * @param steps The saga steps
 * @return A Command that executes the saga
 */
fun <A : Any, R : Any, E : IdkErrorType> saga(
    id: String,
    errorMapper: CommandErrorMapper<E>,
    vararg steps: SagaStep<E>
): Command<A, R, E> = SagaCommandAdapter(id, steps.toList(), errorMapper)

/**
 * Convenience function for IdkError sagas.
 */
fun <A : Any, R : Any> saga(
    id: String,
    vararg steps: SagaStep<IdkError>
): Command<A, R, IdkError> = SagaCommandAdapter(id, steps.toList(), IdkErrorCommandErrorMapper)

/**
 * Builder for creating saga commands with a fluent API.
 *
 * Example:
 * ```kotlin
 * val saga = sagaBuilder<CreateOrderArgs, Order, IdkError>("order.saga.create")
 *     .step(createOrderCommand)
 *     .step(reserveInventoryCommand)
 *     .step(chargePaymentCommand)
 *     .build()
 * ```
 */
class SagaBuilder<A : Any, R : Any, E : IdkErrorType>(
    private val id: String,
    private val errorMapper: CommandErrorMapper<E>
) {
    private val steps = mutableListOf<SagaStep<E>>()

    /**
     * Adds a compensatable command step to the saga.
     */
    fun <StepArg : Any, StepResult : Any> step(
        command: CompensatableCommand<StepArg, StepResult, E>
    ): SagaBuilder<A, R, E> {
        steps.add(TypedSagaStep(command, errorMapper))
        return this
    }

    /**
     * Builds the saga command.
     */
    fun build(): Command<A, R, E> = SagaCommandAdapter(id, steps, errorMapper)
}

/**
 * Creates a new saga builder.
 *
 * @param id The command ID for the saga
 * @param errorMapper The error mapper
 * @return A SagaBuilder
 */
fun <A : Any, R : Any, E : IdkErrorType> sagaBuilder(
    id: String,
    errorMapper: CommandErrorMapper<E>
): SagaBuilder<A, R, E> = SagaBuilder(id, errorMapper)

/**
 * Convenience function for IdkError saga builders.
 */
fun <A : Any, R : Any> sagaBuilder(
    id: String
): SagaBuilder<A, R, IdkError> = SagaBuilder(id, IdkErrorCommandErrorMapper)

/**
 * Creates a simple compensatable command from execute and compensate functions.
 * Useful for inline saga steps.
 *
 * @param id The command ID
 * @param executeFn The execute function
 * @param compensateFn The compensate function
 * @return A CompensatableCommand
 */
fun <A : Any, R : Any, E : IdkErrorType> compensatable(
    id: String,
    executeFn: suspend (A) -> IdkResult<R, E>,
    compensateFn: suspend (A, R) -> IdkResult<Unit, E>
): CompensatableCommand<A, R, E> = object : CompensatableCommand<A, R, E> {
    override val id = id
    override val isEnabled = true
    override val subsystem = EventSubsystems.CUSTOM

    override suspend fun supports(args: Any) = true

    override suspend fun execute(args: A) =
        executeFn(args)

    override suspend fun compensate(args: A, result: R) =
        compensateFn(args, result)
}

