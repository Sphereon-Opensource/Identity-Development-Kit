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
import com.sphereon.core.api.Err
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.di.session.SessionContext

/**
 * Creates a Chain of Responsibility command from a list of handlers.
 *
 * The Chain of Responsibility pattern allows multiple handlers to process a request.
 * Each handler is tried in order. The first handler that:
 * 1. Returns `true` from `supports()`
 * 2. Returns `Ok` from `execute()`
 *
 * ...handles the request. If a handler supports but fails, its error is collected
 * and the next handler is tried. If all handlers fail, a combined error is returned.
 *
 * Example:
 * ```kotlin
 * val resolver = chainOfResponsibility(
 *     "did.resolver.resolve",
 *     listOf(webResolver, ionResolver, keyResolver)
 * )
 * ```
 *
 * @param id The command ID
 * @param handlers The list of handlers to try
 * @param errorMapper The error mapper for creating errors
 * @return A Command that implements Chain of Responsibility
 */
fun <A : Any, R : Any, E : IdkErrorType> chainOfResponsibility(
    id: String,
    handlers: List<BaseCommand<A, R, E>>,
    errorMapper: CommandErrorMapper<E>
): Command<A, R, E> = object : Command<A, R, E> {
    override val id = id
    override val isEnabled = true
    override val subsystem = EventSubsystems.CUSTOM

    override suspend fun supports(args: Any): Boolean =
        handlers.any { it.supports(args) }

    override suspend fun execute(args: A): IdkResult<R, E> {
        val errors = mutableListOf<E>()

        for (handler in handlers) {
            if (!handler.supports(args)) continue

            val result = handler.execute(args)
            if (result.isOk) return result
            if (CommandErrors.isAuthorizationFailure(result.error)) return result

            errors.add(result.error)
        }

        return Err(
            if (errors.isEmpty()) {
                errorMapper.unsupportedArg(this, args)
            } else {
                errorMapper.allHandlersFailed(errors)
            }
        )
    }
}

/**
 * Convenience function for IdkError that uses the default error mapper.
 *
 * @param id The command ID
 * @param handlers The list of handlers to try
 * @return A Command that implements Chain of Responsibility
 */
fun <A : Any, R : Any> chainOfResponsibility(
    id: String,
    handlers: List<BaseCommand<A, R, IdkError>>
): Command<A, R, IdkError> = chainOfResponsibility(id, handlers, IdkErrorCommandErrorMapper)

/**
 * Creates a Chain of Responsibility command using vararg handlers.
 *
 * @param id The command ID
 * @param errorMapper The error mapper for creating errors
 * @param handlers The handlers to try
 * @return A Command that implements Chain of Responsibility
 */
fun <A : Any, R : Any, E : IdkErrorType> chainOfResponsibility(
    id: String,
    errorMapper: CommandErrorMapper<E>,
    vararg handlers: BaseCommand<A, R, E>
): Command<A, R, E> = chainOfResponsibility(id, handlers.toList(), errorMapper)

/**
 * Configuration for Chain of Responsibility behavior.
 */
data class ChainOfResponsibilityConfig(
    /**
     * Whether to stop on the first handler that supports the request,
     * even if it fails. If false, continue trying other handlers on failure.
     */
    val stopOnFirstSupporting: Boolean = false,

    /**
     * Whether to collect errors from all handlers that were tried.
     * If false, only returns the last error.
     */
    val collectAllErrors: Boolean = true
)

/**
 * Creates a Chain of Responsibility command with custom configuration.
 *
 * @param id The command ID
 * @param handlers The list of handlers to try
 * @param errorMapper The error mapper for creating errors
 * @param config Configuration options
 * @return A Command that implements Chain of Responsibility
 */
fun <A : Any, R : Any, E : IdkErrorType> chainOfResponsibilityConfigured(
    id: String,
    handlers: List<BaseCommand<A, R, E>>,
    errorMapper: CommandErrorMapper<E>,
    config: ChainOfResponsibilityConfig
): Command<A, R, E> = object : Command<A, R, E> {
    override val id = id
    override val isEnabled = true
    override val subsystem = EventSubsystems.CUSTOM

    override suspend fun supports(args: Any): Boolean =
        handlers.any { it.supports(args) }

    override suspend fun execute(args: A): IdkResult<R, E> {
        val errors = mutableListOf<E>()

        for (handler in handlers) {
            if (!handler.supports(args)) continue

            val result = handler.execute(args)
            if (result.isOk) return result
            if (CommandErrors.isAuthorizationFailure(result.error)) return result

            if (config.stopOnFirstSupporting) {
                return result
            }

            if (config.collectAllErrors) {
                errors.add(result.error)
            } else {
                errors.clear()
                errors.add(result.error)
            }
        }

        return Err(
            if (errors.isEmpty()) {
                errorMapper.unsupportedArg(this, args)
            } else if (errors.size == 1) {
                errors.first()
            } else {
                errorMapper.allHandlersFailed(errors)
            }
        )
    }
}

/**
 * Creates a "first match" handler - returns the result of the first handler
 * that supports the request, regardless of success/failure.
 *
 * @param id The command ID
 * @param handlers The handlers to try
 * @param errorMapper The error mapper for unsupported args
 * @return A Command that returns the first matching handler's result
 */
fun <A : Any, R : Any, E : IdkErrorType> firstMatch(
    id: String,
    handlers: List<BaseCommand<A, R, E>>,
    errorMapper: CommandErrorMapper<E>
): Command<A, R, E> = chainOfResponsibilityConfigured(
    id,
    handlers,
    errorMapper,
    ChainOfResponsibilityConfig(stopOnFirstSupporting = true)
)

/**
 * Creates a "first success" handler - tries handlers until one succeeds.
 * Returns the combined error if all fail.
 *
 * @param id The command ID
 * @param handlers The handlers to try
 * @param errorMapper The error mapper for errors
 * @return A Command that tries handlers until one succeeds
 */
fun <A : Any, R : Any, E : IdkErrorType> firstSuccess(
    id: String,
    handlers: List<BaseCommand<A, R, E>>,
    errorMapper: CommandErrorMapper<E>
): Command<A, R, E> = chainOfResponsibility(id, handlers, errorMapper)

