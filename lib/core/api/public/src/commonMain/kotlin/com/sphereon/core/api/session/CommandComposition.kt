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

/**
 * Functional composition helpers for commands.
 * These functions allow transforming command inputs, outputs, and errors
 * without modifying the underlying command implementation.
 */

/**
 * Maps the input of a command by transforming arguments before execution.
 *
 * @param transform Function to transform the input
 * @return A new command that accepts the new argument type
 */
@Suppress("UNCHECKED_CAST")
fun <NewArg : Any, Arg : Any, Result : Any, E : IdkErrorType> BaseCommand<Arg, Result, E>.mapInput(
    transform: suspend (NewArg) -> IdkResult<Arg, E>
): BaseCommand<NewArg, Result, E> = object : BaseCommand<NewArg, Result, E> {

    override suspend fun supports(args: Any): Boolean {
        val typedArgs = args as? NewArg ?: return false
        val mapped = transform(typedArgs)
        return mapped.isOk && this@mapInput.supports(mapped.value)
    }

    override suspend fun execute(args: NewArg): IdkResult<Result, E> {
        val mapped = transform(args)
        return if (mapped.isOk) {
            this@mapInput.execute(mapped.value)
        } else {
            Err(mapped.error)
        }
    }
}

/**
 * Maps the output of a command by transforming the result after execution.
 *
 * @param transform Function to transform the output
 * @return A new command that produces the new result type
 */
fun <Arg : Any, Result : Any, NewResult : Any, E : IdkErrorType> BaseCommand<Arg, Result, E>.mapOutput(
    transform: suspend (Result) -> IdkResult<NewResult, E>
): BaseCommand<Arg, NewResult, E> = object : BaseCommand<Arg, NewResult, E> {

    override suspend fun supports(args: Any): Boolean =
        this@mapOutput.supports(args)

    override suspend fun execute(args: Arg): IdkResult<NewResult, E> {
        val result = this@mapOutput.execute(args)
        return if (result.isOk) {
            transform(result.value)
        } else {
            Err(result.error)
        }
    }
}

/**
 * Maps the error type of a command by transforming errors.
 *
 * @param transform Function to transform errors
 * @return A new command with the new error type
 */
fun <Arg : Any, Result : Any, E : IdkErrorType, NewE : IdkErrorType> BaseCommand<Arg, Result, E>.mapError(
    transform: suspend (E) -> NewE
): BaseCommand<Arg, Result, NewE> = object : BaseCommand<Arg, Result, NewE> {

    override suspend fun supports(args: Any): Boolean =
        this@mapError.supports(args)

    override suspend fun execute(args: Arg): IdkResult<Result, NewE> {
        val result = this@mapError.execute(args)
        return if (result.isOk) {
            Ok(result.value)
        } else {
            Err(transform(result.error))
        }
    }
}

/**
 * Convenience function for simple input transformation without session context.
 *
 * @param transform Simple function to transform the input
 * @return A new command that accepts the new argument type
 */
fun <NewArg : Any, Arg : Any, Result : Any, E : IdkErrorType> BaseCommand<Arg, Result, E>.mapInputSimple(
    transform: suspend (NewArg) -> Arg
): BaseCommand<NewArg, Result, E> = mapInput { arg -> Ok(transform(arg)) }

/**
 * Convenience function for simple output transformation without session context.
 *
 * @param transform Simple function to transform the output
 * @return A new command that produces the new result type
 */
fun <Arg : Any, Result : Any, NewResult : Any, E : IdkErrorType> BaseCommand<Arg, Result, E>.mapOutputSimple(
    transform: suspend (Result) -> NewResult
): BaseCommand<Arg, NewResult, E> = mapOutput { result -> Ok(transform(result)) }

/**
 * Filters the command output, returning an error if the predicate fails.
 *
 * @param predicate Function to test the result
 * @param onFailure Function to create an error when predicate fails
 * @return A new command that filters results
 */
fun <Arg : Any, Result : Any, E : IdkErrorType> BaseCommand<Arg, Result, E>.filterOutput(
    predicate: suspend (Result) -> Boolean,
    onFailure: suspend (Result) -> E
): BaseCommand<Arg, Result, E> = object : BaseCommand<Arg, Result, E> {

    override suspend fun supports(args: Any): Boolean =
        this@filterOutput.supports(args)

    override suspend fun execute(args: Arg): IdkResult<Result, E> {
        val result = this@filterOutput.execute(args)
        return if (result.isOk) {
            if (predicate(result.value)) {
                result
            } else {
                Err(onFailure(result.value))
            }
        } else {
            result
        }
    }
}

/**
 * Recovers from errors by providing an alternative result.
 *
 * @param recover Function to provide alternative result on error
 * @return A new command that recovers from errors
 */
fun <Arg : Any, Result : Any, E : IdkErrorType> BaseCommand<Arg, Result, E>.recover(
    recover: suspend (E) -> IdkResult<Result, E>
): BaseCommand<Arg, Result, E> = object : BaseCommand<Arg, Result, E> {

    override suspend fun supports(args: Any): Boolean =
        this@recover.supports(args)

    override suspend fun execute(args: Arg): IdkResult<Result, E> {
        val result = this@recover.execute(args)
        return if (result.isOk) {
            result
        } else {
            recover(result.error)
        }
    }
}

/**
 * Provides a fallback value when the command fails.
 *
 * @param fallback The fallback value to use on error
 * @return A new command that never fails (always returns Ok)
 */
fun <Arg : Any, Result : Any, E : IdkErrorType> BaseCommand<Arg, Result, E>.withFallback(
    fallback: Result
): BaseCommand<Arg, Result, E> = object : BaseCommand<Arg, Result, E> {

    override suspend fun supports(args: Any): Boolean =
        this@withFallback.supports(args)

    override suspend fun execute(args: Arg): IdkResult<Result, E> {
        val result = this@withFallback.execute(args)
        return if (result.isOk) result else Ok(fallback)
    }
}

/**
 * Adds a side effect that runs after successful execution.
 * Does not modify the result.
 *
 * @param effect Side effect to run on success
 * @return The same command with added side effect
 */
fun <Arg : Any, Result : Any, E : IdkErrorType> BaseCommand<Arg, Result, E>.onSuccess(
    effect: suspend (Result) -> Unit
): BaseCommand<Arg, Result, E> = object : BaseCommand<Arg, Result, E> {

    override suspend fun supports(args: Any): Boolean =
        this@onSuccess.supports(args)

    override suspend fun execute(args: Arg): IdkResult<Result, E> {
        val result = this@onSuccess.execute(args)
        if (result.isOk) {
            effect(result.value)
        }
        return result
    }
}

/**
 * Adds a side effect that runs after failed execution.
 * Does not modify the error.
 *
 * @param effect Side effect to run on failure
 * @return The same command with added side effect
 */
fun <Arg : Any, Result : Any, E : IdkErrorType> BaseCommand<Arg, Result, E>.onFailure(
    effect: suspend (E) -> Unit
): BaseCommand<Arg, Result, E> = object : BaseCommand<Arg, Result, E> {

    override suspend fun supports(args: Any): Boolean =
        this@onFailure.supports(args)

    override suspend fun execute(args: Arg): IdkResult<Result, E> {
        val result = this@onFailure.execute(args)
        if (result.isErr) {
            effect(result.error)
        }
        return result
    }
}

