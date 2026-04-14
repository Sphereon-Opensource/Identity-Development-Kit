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

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.session.SessionContext

/**
 * Base class for services that aggregate multiple commands and provide higher-level operations.
 *
 * This class implements [CommandDelegator], providing convenient command invocation via
 * the `invoke()` extension function on commands.
 *
 * Subclasses should define a `commands` property (or individual command properties) and
 * expose business operations that delegate to those commands.
 *
 * Example:
 * ```kotlin
 * @Inject
 * @SingleIn(SessionScope::class)
 * class UserService(
 *     override val execution: SessionExecution,
 *     private val createUserCommand: CreateUserCommand,
 *     private val getUserCommand: GetUserCommand,
 *     private val deleteUserCommand: DeleteUserCommand
 * ) : CommandAggregatorService() {
 *
 *     suspend fun createUser(name: String, email: String): IdkResult<User, IdkError> =
 *         createUserCommand(CreateUserArgs(name, email))
 *
 *     suspend fun getUser(id: String): IdkResult<User, IdkError> =
 *         getUserCommand(GetUserArgs(id))
 *
 *     suspend fun deleteUser(id: String): IdkResult<Unit, IdkError> =
 *         deleteUserCommand(DeleteUserArgs(id))
 * }
 * ```
 *
 * @param execution The session execution context providing access to session context and services
 */
@JsExportCompat
abstract class CommandAggregatorService(
    override val execution: SessionExecution,
) : CommandDelegator {
    /**
     * The session context extracted from execution for convenience.
     * Useful when passing context to other services or for logging.
     */
    override val sessionContext: SessionContext
        get() = execution.sessionContext

    /**
     * The session log service for structured logging within the session context.
     */
    protected val log: SessionLogService
        get() = execution.log
}

/**
 * A variant of CommandAggregatorService that uses a typed commands container.
 *
 * This is useful when you want to group related commands into a single object
 * and expose them as a property.
 *
 * Example:
 * ```kotlin
 * data class UserCommands(
 *     val create: CreateUserCommand,
 *     val get: GetUserCommand,
 *     val delete: DeleteUserCommand
 * )
 *
 * @Inject
 * @SingleIn(SessionScope::class)
 * class UserService(
 *     override val execution: SessionExecution,
 *     override val commands: UserCommands
 * ) : TypedCommandAggregatorService<UserCommands>() {
 *
 *     suspend fun createUser(name: String): IdkResult<User, IdkError> =
 *         commands.create(CreateUserArgs(name))
 *
 *     suspend fun getUser(id: String): IdkResult<User, IdkError> =
 *         commands.get(GetUserArgs(id))
 * }
 * ```
 *
 * @param C The type of the commands container
 */
@JsExportCompat
abstract class TypedCommandAggregatorService<C>(
    override val execution: SessionExecution,
) : CommandDelegator {
    /**
     * The commands container holding all commands used by this service.
     */
    abstract val commands: C

    /**
     * The session context extracted from execution for convenience.
     */
    override val sessionContext: SessionContext
        get() = execution.sessionContext

    /**
     * The session log service for structured logging.
     */
    protected val log: SessionLogService
        get() = execution.log
}
