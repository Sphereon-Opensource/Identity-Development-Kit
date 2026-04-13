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
 */

package com.sphereon.core.api.session

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.context.UserScope
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Type-safe command executor.
 *
 * Types flow from the [ServiceCommand] parameter rather than being erased to `<Any, Any>`:
 *
 * ```kotlin
 * val cmd = executor.resolve<GetKeyServiceCommand>("kms.keys.get")!!
 * val result = executor.execute(cmd, GetKeyInput("my-key"))
 * // ^ TInput=GetKeyInput, TOutput=KeyInfo — compiler enforced
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CommandExecutor", exact = true)
interface CommandExecutor {
    /**
     * Resolve a command by ID. Returns the raw [ServiceCommand].
     * Use the reified extension for typed resolution.
     */
    fun resolve(commandId: String): ServiceCommand<*, *>?

    /**
     * Execute a command. Types are inferred from the [ServiceCommand] parameter.
     */
    suspend fun <TInput : Any, TOutput : Any> execute(
        command: ServiceCommand<TInput, TOutput>,
        input: TInput,
    ): IdkResult<TOutput, IdkError>

    fun has(commandId: String): Boolean

    fun listCommandIds(): List<String>
}

/**
 * Resolve a command typed as a specific command interface.
 *
 * ```kotlin
 * val cmd = executor.resolve<GetKeyServiceCommand>("kms.keys.get")!!
 * ```
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified C : ServiceCommand<*, *>> CommandExecutor.resolve(commandId: String): C? = resolve(commandId) as? C

/**
 * Convenience: resolve + execute by commandId in one call.
 * Types flow from the reified command interface.
 *
 * ```kotlin
 * val result = executor.executeById<GetKeyServiceCommand>("kms.keys.get", GetKeyInput("my-key"))
 * ```
 */
@Suppress("UNCHECKED_CAST")
suspend inline fun <reified C : ServiceCommand<TInput, TOutput>, TInput : Any, TOutput : Any> CommandExecutor.executeById(
    commandId: String,
    input: TInput,
): IdkResult<TOutput, IdkError> {
    val command =
        resolve<C>(commandId)
            ?: return IdkResult.err(IdkError.NOT_FOUND_ERROR(message = "Command '$commandId' not found"))
    return execute(command, input)
}

// ========== Session-scoped implementation ==========

/**
 * Session-scoped [CommandExecutor] backed by [SessionScopedCommandRegistry].
 *
 * Uses the registry directly — participates in LOCAL/SERVER routing when the
 * VDX [DefaultSessionScopedCommandRegistry] replaces the IDK default.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CommandExecutor>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionScopeCommandExecutor", exact = true)
class SessionScopeCommandExecutor(
    private val registry: SessionScopedCommandRegistry,
    private val errorMapper: CommandErrorMapper<IdkError> = IdkErrorCommandErrorMapper,
) : CommandExecutor {
    override fun resolve(commandId: String): ServiceCommand<*, *>? = registry.get(commandId)

    override suspend fun <TInput : Any, TOutput : Any> execute(
        command: ServiceCommand<TInput, TOutput>,
        input: TInput,
    ): IdkResult<TOutput, IdkError> {
        try {
            if (!command.isEnabled) {
                return IdkResult.err(errorMapper.commandDisabled(command.commandId))
            }
            if (!command.supports(input)) {
                return IdkResult.err(errorMapper.unsupportedArg(command, input))
            }
            return command.execute(input)
        } catch (expected: Exception) {
            return IdkResult.err(
                errorMapper.unknown(
                    message = "Error executing command '${command.commandId}'",
                    cause = expected,
                ),
            )
        }
    }

    override fun has(commandId: String): Boolean = registry.has(commandId)

    override fun listCommandIds(): List<String> = registry.listCommandIds()
}

// ========== User-scoped implementation ==========

/**
 * User-scoped [CommandExecutor] that delegates to the session-scoped executor
 * via a background service session.
 */
@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<CommandExecutor>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("UserScopeCommandExecutor", exact = true)
class UserScopeCommandExecutor(
    private val sessionContextManager: SessionContextManager,
) : CommandExecutor {
    private fun sessionExecutor(): CommandExecutor {
        val session = sessionContextManager.getOrCreateBackgroundService()
        return (session.graph as CommandExecutorGraph).commandExecutor
    }

    override fun resolve(commandId: String): ServiceCommand<*, *>? = sessionExecutor().resolve(commandId)

    override suspend fun <TInput : Any, TOutput : Any> execute(
        command: ServiceCommand<TInput, TOutput>,
        input: TInput,
    ): IdkResult<TOutput, IdkError> = sessionExecutor().execute(command, input)

    override fun has(commandId: String): Boolean = sessionExecutor().has(commandId)

    override fun listCommandIds(): List<String> = sessionExecutor().listCommandIds()
}

// ========== Session graph ==========

/**
 * Provides access to [CommandExecutor] from the session graph graph.
 *
 * Used by [UserScopeCommandExecutor] and [AppCommandExecutorImpl] to access
 * the session-scoped executor via `session.graph as CommandExecutorGraph`.
 */
@ContributesTo(SessionScope::class)
interface CommandExecutorGraph {
    val commandExecutor: CommandExecutor
}
