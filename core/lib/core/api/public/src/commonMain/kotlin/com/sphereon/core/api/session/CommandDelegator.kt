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
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.session.SessionContext

/**
 * A mixin interface that provides convenient command invocation from services.
 *
 * When implemented by a service class, it provides `invoke` and `invokeOrThrow`
 * extension functions on commands, automatically passing the session context.
 *
 * Example:
 * ```kotlin
 * class MyService(
 *     override val execution: SessionExecution,
 *     private val createUserCommand: CreateUserCommand,
 *     private val getUserCommand: GetUserCommand
 * ) : CommandDelegator {
 *
 *     suspend fun createUser(name: String): IdkResult<User, IdkError> =
 *         createUserCommand(CreateUserArgs(name))
 *
 *     suspend fun getUser(id: String): IdkResult<User, IdkError> =
 *         getUserCommand(GetUserArgs(id))
 * }
 * ```
 */
interface CommandDelegator {
    /**
     * The session execution context providing access to sessionContext and other session-scoped services.
     */
    val execution: SessionExecution

    /**
     * The session context extracted from execution for convenience.
     */
    val sessionContext: SessionContext
        get() = execution.sessionContext

    /**
     * Invokes a command with the given arguments using the session context from this delegator.
     *
     * @param args The command arguments
     * @return The command result
     */
    suspend operator fun <A : Any, R : Any, E : IdkErrorType> BaseCommand<A, R, E>.invoke(args: A): IdkResult<R, E> = this.execute(args)

    /**
     * Invokes a command and returns the success value directly, throwing on error.
     */
    suspend fun <A : Any, R : Any, E : IdkErrorType> BaseCommand<A, R, E>.invokeOrThrow(args: A): R {
        val result = this.execute(args)
        if (result.isOk) {
            return result.value
        }
        throw RuntimeException(result.error.toString())
    }
}

/**
 * A variant of CommandDelegator that only requires a SessionContext instead of full SessionExecution.
 * Useful for simpler scenarios where the full execution context isn't needed.
 */
interface SimpleCommandDelegator {
    /**
     * The session context to use for command invocation.
     */
    val sessionContext: SessionContext

    /**
     * Invokes a command with the given arguments using this delegator's session context.
     */
    suspend operator fun <A : Any, R : Any, E : IdkErrorType> BaseCommand<A, R, E>.invoke(args: A): IdkResult<R, E> = this.execute(args)

    /**
     * Invokes a command and returns the success value directly, throwing on error.
     */
    suspend fun <A : Any, R : Any, E : IdkErrorType> BaseCommand<A, R, E>.invokeOrThrow(args: A): R {
        val result = this.execute(args)
        if (result.isOk) {
            return result.value
        }
        throw RuntimeException(result.error.toString())
    }
}
