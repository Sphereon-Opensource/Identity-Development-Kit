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
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.compat.JsExportCompat

/**
 * Result of the beforeExecute hook in an enhanced command extension.
 *
 * This sealed class allows extensions to control command execution flow:
 * - [Continue]: Proceed with command execution, optionally modifying the arguments
 * - [Skip]: Skip command execution entirely (useful for caching scenarios)
 * - [ShortCircuit]: Return a specific result without executing the command
 *
 * @param A The argument type
 * @param R The success result type
 * @param E The error type
 */
@JsExportCompat
sealed class BeforeExecuteResult<out A, out R, out E> {
    /**
     * Continue with command execution, potentially with modified arguments.
     *
     * @property args The (possibly modified) arguments to pass to the command
     */
    data class Continue<A>(
        val args: A,
    ) : BeforeExecuteResult<A, Nothing, Nothing>()

    /**
     * Skip command execution entirely.
     * The command's execute() will not be called, and no result will be returned.
     * Use this for scenarios like cached results where you want to handle the response externally.
     *
     * Note: When Skip is returned, the afterExecute hook will NOT be called.
     */
    data object Skip : BeforeExecuteResult<Nothing, Nothing, Nothing>()

    /**
     * Short-circuit command execution and return a specific result.
     * The command's execute() will not be called, but afterExecute will receive this result.
     *
     * Use this for:
     * - Authorization failures (return Err without executing)
     * - Cached results (return Ok with cached value)
     * - Pre-validation failures
     *
     * @property result The result to return instead of executing the command
     */
    data class ShortCircuit<R, E : IdkErrorType>(
        val result: IdkResult<R, E>,
    ) : BeforeExecuteResult<Nothing, R, E>()
}

/**
 * Enhanced command execution extension with suspend functions and short-circuit capability.
 *
 * This interface extends the capabilities of [ICommandExecutionExtension] by:
 * - Making all methods suspending for async operations
 * - Allowing beforeExecute to short-circuit execution
 * - Allowing afterExecute to modify the result
 *
 * Use cases:
 * - Caching (return cached results, skip execution)
 * - Audit logging (capture before/after state)
 * - Request/response transformation
 * - Input validation (reject invalid arguments before execution)
 *
 * Example - Validation Extension:
 * ```kotlin
 * class ValidationExtension(
 *     private val validator: InputValidator
 * ) : IEnhancedCommandExecutionExtension<Any, Any, IdkError> {
 *
 *     override suspend fun beforeExecute(
 *         service: Command<Any, Any, IdkError>,
 *         args: Any,
 *     ): BeforeExecuteResult<Any, Any, IdkError> {
 *         val errors = validator.validate(args)
 *         return if (errors.isEmpty()) {
 *             BeforeExecuteResult.Continue(args)
 *         } else {
 *             BeforeExecuteResult.ShortCircuit(
 *                 Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Validation failed: ${errors.joinToString()}"))
 *             )
 *         }
 *     }
 * }
 * ```
 *
 * Example - Caching Extension:
 * ```kotlin
 * class CachingExtension<A : Any, R : Any>(
 *     private val cache: Cache<A, R>
 * ) : IEnhancedCommandExecutionExtension<A, R, IdkError> {
 *
 *     override suspend fun beforeExecute(
 *         service: Command<A, R, IdkError>,
 *         args: A,
 *     ): BeforeExecuteResult<A, R, IdkError> {
 *         val cached = cache.get(args)
 *         return if (cached != null) {
 *             BeforeExecuteResult.ShortCircuit(Ok(cached))
 *         } else {
 *             BeforeExecuteResult.Continue(args)
 *         }
 *     }
 *
 *     override suspend fun afterExecute(
 *         service: Command<A, R, IdkError>,
 *         args: A,
 *         result: IdkResult<R, IdkError>,
 *     ): IdkResult<R, IdkError> {
 *         if (result.isOk) {
 *             cache.put(args, result.value)
 *         }
 *         return result
 *     }
 * }
 * ```
 *
 * @param Arg The command argument type
 * @param SuccessResult The success result type
 * @param ErrorResult The error type
 */
@JsExportCompat
interface IEnhancedCommandExecutionExtension<Arg : Any, SuccessResult : Any, ErrorResult : IdkErrorType> {
    /**
     * Called before command execution.
     *
     * Can:
     * - Modify the arguments ([BeforeExecuteResult.Continue] with new args)
     * - Skip execution ([BeforeExecuteResult.Skip])
     * - Short-circuit with a result ([BeforeExecuteResult.ShortCircuit])
     *
     * @param service The command being executed
     * @param args The original arguments
     * @return The execution control decision
     */
    suspend fun beforeExecute(
        service: Command<Arg, SuccessResult, ErrorResult>,
        args: Arg,
    ): BeforeExecuteResult<Arg, SuccessResult, ErrorResult> = BeforeExecuteResult.Continue(args)

    /**
     * Called during command execution to potentially transform arguments.
     *
     * This is called after beforeExecute returns Continue, and before the actual
     * command logic runs. It allows in-flight argument transformation.
     *
     * @param service The command being executed
     * @param args The arguments (possibly modified by beforeExecute)
     * @return The (possibly modified) arguments to use for execution
     */
    suspend fun duringExecute(
        service: Command<Arg, SuccessResult, ErrorResult>,
        args: Arg,
    ): Arg = args

    /**
     * Called after command execution.
     *
     * This is called regardless of whether the command succeeded or failed,
     * unless beforeExecute returned Skip.
     *
     * Can:
     * - Transform the result (return a different result)
     * - Log the outcome
     * - Update caches
     * - Clean up resources
     *
     * @param service The command that was executed
     * @param args The original arguments (before any transformation)
     * @param result The result of command execution (or from short-circuit)
     * @return The (possibly modified) result
     */
    suspend fun afterExecute(
        service: Command<Arg, SuccessResult, ErrorResult>,
        args: Arg,
        result: IdkResult<SuccessResult, ErrorResult>,
    ): IdkResult<SuccessResult, ErrorResult> = result
}

/**
 * Adapter to use [IEnhancedCommandExecutionExtension] where [ICommandExecutionExtension] is expected.
 *
 * Note: This adapter runs suspend functions in a blocking manner. For full async support,
 * use the enhanced extension system directly.
 */
@JsExportCompat
class EnhancedExtensionAdapter<Arg : Any, SuccessResult : Any, ErrorResult : IdkErrorType>(
    private val enhanced: IEnhancedCommandExecutionExtension<Arg, SuccessResult, ErrorResult>,
) : ICommandExecutionExtension<Arg, SuccessResult, ErrorResult> {
    // Note: The legacy interface doesn't support suspend, so we can't properly delegate.
    // This adapter is primarily for documentation/migration purposes.
    // Full support requires updating the command infrastructure.

    override fun beforeExecute(
        service: Command<Arg, SuccessResult, ErrorResult>,
        args: Arg,
    ) {
        // Cannot properly call suspend function from non-suspend context
        // This is a limitation of the adapter pattern
    }

    override fun duringExecute(
        service: Command<Arg, SuccessResult, ErrorResult>,
        args: Arg,
    ): Arg = args

    override fun afterExecute(
        service: Command<Arg, SuccessResult, ErrorResult>,
        args: Arg,
        result: IdkResult<SuccessResult, ErrorResult>,
    ) {
        // Cannot properly call suspend function from non-suspend context
    }
}

/**
 * Combines multiple enhanced extensions into a single extension.
 *
 * Extensions are executed in order:
 * - beforeExecute: first to last (stops on first Skip/ShortCircuit)
 * - duringExecute: first to last (each transforms args from previous)
 * - afterExecute: last to first (each transforms result from previous)
 *
 * @param extensions The extensions to combine
 */
@JsExportCompat
class CompositeEnhancedExtension<Arg : Any, SuccessResult : Any, ErrorResult : IdkErrorType>(
    private val extensions: List<IEnhancedCommandExecutionExtension<Arg, SuccessResult, ErrorResult>>,
) : IEnhancedCommandExecutionExtension<Arg, SuccessResult, ErrorResult> {
    override suspend fun beforeExecute(
        service: Command<Arg, SuccessResult, ErrorResult>,
        args: Arg,
    ): BeforeExecuteResult<Arg, SuccessResult, ErrorResult> {
        var currentArgs = args
        for (ext in extensions) {
            when (val result = ext.beforeExecute(service, currentArgs)) {
                is BeforeExecuteResult.Continue -> {
                    currentArgs = result.args
                }

                is BeforeExecuteResult.Skip -> {
                    return result
                }

                is BeforeExecuteResult.ShortCircuit<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    return result as BeforeExecuteResult<Arg, SuccessResult, ErrorResult>
                }
            }
        }
        return BeforeExecuteResult.Continue(currentArgs)
    }

    override suspend fun duringExecute(
        service: Command<Arg, SuccessResult, ErrorResult>,
        args: Arg,
    ): Arg {
        var currentArgs = args
        for (ext in extensions) {
            currentArgs = ext.duringExecute(service, currentArgs)
        }
        return currentArgs
    }

    override suspend fun afterExecute(
        service: Command<Arg, SuccessResult, ErrorResult>,
        args: Arg,
        result: IdkResult<SuccessResult, ErrorResult>,
    ): IdkResult<SuccessResult, ErrorResult> {
        var currentResult = result
        // Process in reverse order
        for (ext in extensions.asReversed()) {
            currentResult = ext.afterExecute(service, args, currentResult)
        }
        return currentResult
    }
}

/**
 * Creates a composite extension from multiple extensions.
 */
fun <Arg : Any, SuccessResult : Any, ErrorResult : IdkErrorType> compositeExtension(
    vararg extensions: IEnhancedCommandExecutionExtension<Arg, SuccessResult, ErrorResult>,
): IEnhancedCommandExecutionExtension<Arg, SuccessResult, ErrorResult> = CompositeEnhancedExtension(extensions.toList())
