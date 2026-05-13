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
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.core.compat.JsExportCompat
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
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CommandInvoker", exact = true)
interface CommandInvoker {
    /**
     * Resolve a command by ID. Returns the raw [ServiceCommand].
     * Use the reified extension for typed resolution.
     */
    fun resolve(commandId: String): ServiceCommand<*, *, *>?

    /**
     * Execute a command. Types are inferred from the [ServiceCommand] parameter.
     */
    suspend fun <TInput : Any, TOutput : Any, TError : IdkErrorType> execute(
        command: ServiceCommand<TInput, TOutput, TError>,
        input: TInput,
    ): IdkResult<TOutput, TError>

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
inline fun <reified C : ServiceCommand<*, *, *>> CommandInvoker.resolve(commandId: String): C? = resolve(commandId) as? C

/**
 * Convenience: resolve + execute by commandId in one call.
 * Types flow from the reified command interface.
 *
 * ```kotlin
 * val result = executor.executeById<GetKeyServiceCommand>("kms.keys.get", GetKeyInput("my-key"))
 * ```
 */
@Suppress("UNCHECKED_CAST")
suspend inline fun <
    reified C : ServiceCommand<TInput, TOutput, IdkError>,
    TInput : Any,
    TOutput : Any,
> CommandInvoker.executeById(
    commandId: String,
    input: TInput,
): IdkResult<TOutput, IdkError> {
    val command =
        resolve<C>(commandId)
            ?: return IdkResult.err(IdkError.NOT_FOUND_ERROR(message = "Command '$commandId' not found"))
    return execute(command, input)
}

/**
 * Resolve + execute by commandId where the caller knows only `TInput`/`TOutput`,
 * not the concrete `ServiceCommand` interface.
 *
 * Useful for dispatch sites that hold a string id and a typed payload but cannot
 * reify the command interface (e.g. transport adapters, durable workers reading
 * persisted rows).
 *
 * Validation:
 *  - Returns `Err(NOT_FOUND_ERROR)` if the id is not registered.
 *  - Returns `Err(COMMAND_ARG_NOT_SUPPORTED_ERROR)` if the resolved command's
 *    [com.sphereon.core.api.service.ServiceCommand.inputTypeToken] does not match
 *    the reified `TInput` at runtime, OR if the command's `supports(input)`
 *    rejects the value.
 *
 * ```kotlin
 * val result = invoker.executeById<GetKeyInput, KeyInfo>("kms.keys.get", GetKeyInput("my-key"))
 * ```
 */
@Suppress("UNCHECKED_CAST")
@kotlin.jvm.JvmName("executeByIdTyped")
suspend inline fun <reified TInput : Any, reified TOutput : Any> CommandInvoker.executeById(
    commandId: String,
    input: TInput,
): IdkResult<TOutput, IdkError> {
    val command =
        resolve(commandId)
            ?: return IdkResult.err(IdkError.NOT_FOUND_ERROR(message = "Command '$commandId' not found"))
    val expectedType = command.inputTypeToken.kType
    val actualType = kotlin.reflect.typeOf<TInput>()
    if (expectedType != actualType) {
        return IdkResult.err(
            IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(
                command = command,
                arg = input,
                message =
                    "Command '$commandId' expects input type $expectedType but " +
                        "byId caller supplied $actualType",
            ),
        )
    }
    if (!command.supports(input)) {
        return IdkResult.err(
            IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(command = command, arg = input),
        )
    }
    val typed = command as ServiceCommand<TInput, TOutput, IdkError>
    return execute(typed, input)
}

// ========== Session-scoped implementation ==========

/**
 * Session-scoped [CommandInvoker] backed by [SessionScopedCommandRegistry].
 *
 * Uses the registry directly — participates in LOCAL/SERVER routing when the
 * VDX [DefaultSessionScopedCommandRegistry] replaces the IDK default.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CommandInvoker>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionScopeCommandInvoker", exact = true)
class SessionScopeCommandInvoker(
    private val registry: SessionScopedCommandRegistry,
    private val errorMapper: CommandErrorMapper<IdkError> = IdkErrorCommandErrorMapper,
) : CommandInvoker {
    override fun resolve(commandId: String): ServiceCommand<*, *, *>? = registry.get(commandId)

    @Suppress("UNCHECKED_CAST")
    override suspend fun <TInput : Any, TOutput : Any, TError : IdkErrorType> execute(
        command: ServiceCommand<TInput, TOutput, TError>,
        input: TInput,
    ): IdkResult<TOutput, TError> {
        try {
            if (!command.isEnabled) {
                return IdkResult.err(errorMapper.commandDisabled(command.commandId) as TError)
            }
            if (!command.supports(input)) {
                return IdkResult.err(errorMapper.unsupportedArg(command, input) as TError)
            }
            return command.execute(input)
        } catch (expected: Exception) {
            return IdkResult.err(
                errorMapper.unknown(
                    message = "Error executing command '${command.commandId}'",
                    cause = expected,
                ) as TError,
            )
        }
    }

    override fun has(commandId: String): Boolean = registry.has(commandId)

    override fun listCommandIds(): List<String> = registry.listCommandIds()
}

// ========== User-scoped implementation ==========

/**
 * User-scoped [CommandInvoker] that delegates to the session-scoped executor
 * via a background service session.
 */
@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<CommandInvoker>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("UserScopeCommandInvoker", exact = true)
class UserScopeCommandInvoker(
    private val sessionContextManager: SessionContextManager,
) : CommandInvoker {
    private fun sessionExecutor(): CommandInvoker {
        val session = sessionContextManager.getOrCreateBackgroundService()
        return (session.graph as CommandInvokerGraph).commandInvoker
    }

    override fun resolve(commandId: String): ServiceCommand<*, *, *>? = sessionExecutor().resolve(commandId)

    override suspend fun <TInput : Any, TOutput : Any, TError : IdkErrorType> execute(
        command: ServiceCommand<TInput, TOutput, TError>,
        input: TInput,
    ): IdkResult<TOutput, TError> = sessionExecutor().execute(command, input)

    override fun has(commandId: String): Boolean = sessionExecutor().has(commandId)

    override fun listCommandIds(): List<String> = sessionExecutor().listCommandIds()
}

// ========== Session graph ==========

/**
 * Provides access to [CommandInvoker] from the session graph graph.
 *
 * Used by [UserScopeCommandInvoker] and [AppCommandInvokerImpl] to access
 * the session-scoped executor via `session.graph as CommandInvokerGraph`.
 */
@ContributesTo(SessionScope::class)
interface CommandInvokerGraph {
    val commandInvoker: CommandInvoker
}
