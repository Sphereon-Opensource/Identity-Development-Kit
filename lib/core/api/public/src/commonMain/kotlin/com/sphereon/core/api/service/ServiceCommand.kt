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
 */

package com.sphereon.core.api.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.session.Command
import com.sphereon.core.api.session.ExecutionScopedCommandAdapter
import com.sphereon.core.api.session.ICommandExecutionExtension
import com.sphereon.core.api.session.ICommandInitExtension

/**
 * The type of action a command performs.
 *
 * Used for structured command identity.
 */
enum class ActionType {
    CREATE, READ, UPDATE, DELETE, LIST, EXECUTE
}

/**
 * Unified abstraction for both commands and services.
 *
 * A ServiceCommand is:
 * - A Command (has execute, supports lifecycle/pipelines)
 * - Part of a Service (grouped operations)
 * - Identifiable (has commandId for registry lookup)
 *
 * This pattern replaces the separate "Command interface" vs "Service interface"
 * approach from earlier versions. Benefits:
 * - Single abstraction for command registration
 * - Consistent identity semantics
 * - Unified test approach
 *
 * **Command ID Format:**
 * Command IDs follow a dotted notation: `{module}.{service}.{command}`
 * - `kms.keys.get` - KMS module, keys service, get command
 * - `party.manager.create` - Party module, manager service, create command
 * - `resource.booking.create` - Resource module, booking service, create command
 *
 * **Structured Identity:**
 * The command ID is decomposed into structured identity properties:
 * - [module] - Top-level module (first segment, e.g., "kms")
 * - [service] - Service within the module (second segment, e.g., "keys")
 * - [command] - The operation (last segment, e.g., "get")
 * - [actionType] - Semantic action type (e.g., [ActionType.READ])
 *
 * These properties provide structured identity for registry lookup and
 * convention-based derivation.
 *
 * **Usage:**
 * ```kotlin
 * // Define a service command interface
 * interface GetKeyServiceCommand : ServiceCommand<GetKeyInput, KeyInfo> {
 *     override val commandId: String get() = "kms.keys.get"
 *     override val actionType: ActionType get() = ActionType.READ
 * }
 *
 * // Implement the command
 * @Inject
 * @ContributesBinding(SessionScope::class, binding = binding<GetKeyServiceCommand>())
 * class GetKeyServiceCommandImpl(
 *     execution: SessionExecution,
 *     private val keyStore: KeyStore
 * ) : ExecutionScopedCommandAdapter<GetKeyInput, KeyInfo, IdkError>(
 *     id = "kms.keys.get",
 *     execution = execution
 * ), GetKeyServiceCommand {
 *     override val commandId: String get() = "kms.keys.get"
 *
 *     override suspend fun doExecute(...) { ... }
 * }
 * ```
 *
 * @param TInput The input type for the command
 * @param TOutput The successful output type
 */
interface ServiceCommand<TInput : Any, TOutput : Any> :
    Command<TInput, TOutput, IdkError>,
    RegistrableServiceCommand {

    /**
     * Unique identifier for registry lookup.
     *
     * Format: `{module}.{service}.{command}`
     * Examples: "kms.keys.get", "party.manager.create", "resource.booking.create"
     */
    override val commandId: String

    /**
     * Default [Command.id] delegates to [commandId] so implementations
     * only need to declare commandId.
     */
    override val id: String get() = commandId

    /**
     * Type token for the input type.
     *
     * Used by codecs for deserialization. Implementations must provide this
     * to enable proper type-safe decoding of request bodies.
     *
     * **Example:**
     * ```kotlin
     * override val inputTypeToken: TypeToken<GetKeyInput> = typeToken<GetKeyInput>()
     * ```
     */
    val inputTypeToken: TypeToken<TInput>

    /**
     * Type token for the output type.
     *
     * Used by codecs for serialization. Implementations must provide this
     * to enable proper type-safe encoding of response bodies.
     *
     * **Example:**
     * ```kotlin
     * override val outputTypeToken: TypeToken<KeyInfo> = typeToken<KeyInfo>()
     * ```
     */
    val outputTypeToken: TypeToken<TOutput>

    // ========== Structured Identity Properties ==========

    /**
     * The top-level module this command belongs to.
     *
     * Derived from commandId by taking the first segment.
     * Examples:
     * - "kms.keys.get" -> "kms"
     * - "party.manager.create" -> "party"
     */
    val module: String
        get() = commandId.substringBefore('.')

    /**
     * The service within the module.
     *
     * Derived from commandId by taking the second segment.
     * Falls back to [module] if commandId has fewer than 3 segments.
     * Examples:
     * - "kms.keys.get" -> "keys"
     * - "party.create" -> "party" (fallback to module)
     */
    val service: String
        get() {
            val parts = commandId.split('.')
            return if (parts.size >= 3) parts[1] else module
        }

    /**
     * The command (operation) this command performs.
     *
     * Derived from commandId by taking the last segment.
     * Examples:
     * - "kms.keys.get" -> "get"
     * - "party.manager.create" -> "create"
     */
    val command: String
        get() = commandId.substringAfterLast('.')

    /**
     * The semantic type of action this command performs.
     *
     * Override in command interfaces to provide semantic meaning.
     * Defaults to [ActionType.EXECUTE] for commands that don't specify.
     */
    val actionType: ActionType
        get() = ActionType.EXECUTE

    // ========== Legacy Identity Properties (backward compatible) ==========

    /**
     * Service group this command belongs to.
     *
     * Derived from commandId by taking everything before the last dot.
     * Examples:
     * - "kms.keys.get" -> "kms.keys"
     * - "party.create" -> "party"
     */
    val serviceId: String
        get() = commandId.substringBeforeLast('.')

    /**
     * The operation name within the service.
     *
     * Derived from commandId by taking the last segment.
     * Equivalent to [action].
     * Examples:
     * - "kms.keys.get" -> "get"
     * - "party.create" -> "create"
     */
    val operationName: String
        get() = commandId.substringAfterLast('.')

    /**
     * The top-level service domain.
     *
     * Derived from commandId by taking the first segment.
     * Equivalent to [module].
     * Examples:
     * - "kms.keys.get" -> "kms"
     * - "party.create" -> "party"
     */
    val serviceDomain: String
        get() = commandId.substringBefore('.')
}

/**
 * Service facade that aggregates related ServiceCommands.
 *
 * Facades provide convenience methods that delegate to individual commands.
 * This provides a clean API for consumers while maintaining the routable
 * command pattern underneath.
 *
 * **Usage:**
 * ```kotlin
 * interface KmsServiceFacade : ServiceFacade {
 *     override val serviceId: String get() = "kms"
 *
 *     suspend fun getKey(aliasOrKid: String): IdkResult<KeyInfo, IdkError>
 *     suspend fun listKeys(limit: Int = 100): IdkResult<List<KeyInfo>, IdkError>
 *     suspend fun sign(keyId: String, payload: ByteArray): IdkResult<ByteArray, IdkError>
 * }
 *
 * @Inject
 * @ContributesBinding(SessionScope::class, binding = binding<KmsServiceFacade>())
 * class KmsServiceFacadeImpl(
 *     private val getKeyCommand: GetKeyServiceCommand,
 *     private val listKeysCommand: ListKeysServiceCommand,
 *     private val signCommand: SignServiceCommand,
 *     private val execution: SessionExecution
 * ) : KmsServiceFacade {
 *     override suspend fun getKey(aliasOrKid: String) =
 *         getKeyCommand.execute(GetKeyInput(aliasOrKid))
 *     // ...
 * }
 * ```
 */
interface ServiceFacade {
    /**
     * The service identifier.
     *
     * This should match the common prefix of all commands in this service.
     * Examples: "kms", "party", "resource"
     */
    val serviceId: String
}

/**
 * Base adapter for ServiceCommand implementations with typed input/output.
 *
 * This adapter extends [ExecutionScopedCommandAdapter] and implements [ServiceCommand],
 * providing the type tokens needed for codec-based serialization/deserialization.
 *
 * **Usage:**
 * ```kotlin
 * @Inject
 * @SingleIn(SessionScope::class)
 * @ContributesBinding(SessionScope::class, binding = binding<GetKeyServiceCommand>())
 * class GetKeyServiceCommandImpl(
 *     execution: SessionExecution,
 *     private val keyStore: KeyStore
 * ) : TypedServiceCommandAdapter<GetKeyInput, KeyInfo>(
 *     commandId = "kms.keys.get",
 *     execution = execution,
 *     inputTypeToken = typeToken<GetKeyInput>(),
 *     outputTypeToken = typeToken<KeyInfo>()
 * ), GetKeyServiceCommand {
 *
 *     override suspend fun doExecute(
 *         args: GetKeyInput,
 *         applyDuring: (GetKeyInput) -> GetKeyInput
 *     ): IdkResult<KeyInfo, IdkError> {
 *         val input = applyDuring(args)
 *         return keyStore.getKey(input.aliasOrKid, input.providerId)
 *             ?.let { Ok(it) }
 *             ?: Err(IdkError.NOT_FOUND_ERROR(message = "Key not found: ${input.aliasOrKid}"))
 *     }
 * }
 * ```
 *
 * @param TInput The input type for the command
 * @param TOutput The successful output type
 * @param commandId The unique command identifier for routing
 * @param execution The session execution context
 * @param inputTypeToken Type token for deserializing input
 * @param outputTypeToken Type token for serializing output
 */
abstract class TypedServiceCommandAdapter<TInput : Any, TOutput : Any>(
    override val commandId: String,
    execution: SessionExecution,
    override val inputTypeToken: TypeToken<TInput>,
    override val outputTypeToken: TypeToken<TOutput>,
    isEnabled: Boolean = true,
    initExtensions: Array<ICommandInitExtension<TInput, TOutput, IdkError>> = emptyArray(),
    executionExtensions: Array<ICommandExecutionExtension<TInput, TOutput, IdkError>> = emptyArray(),
) : ExecutionScopedCommandAdapter<TInput, TOutput, IdkError>(
    id = commandId,
    isEnabled = isEnabled,
    initExtensions = initExtensions,
    executionExtensions = executionExtensions,
    execution = execution,
), ServiceCommand<TInput, TOutput> {
    override val id: String get() = commandId

    override suspend fun execute(args: TInput): IdkResult<TOutput, IdkError> = super.execute(args)

    /**
     * Implement this to provide the command's business logic.
     *
     * @param args The typed input arguments
     * @param applyDuring Callback to apply "during" execution extensions
     * @return The result of the command execution
     */
    abstract override suspend fun doExecute(
        args: TInput,
        applyDuring: (TInput) -> TInput
    ): IdkResult<TOutput, IdkError>
}

/**
 * Simplified adapter for commands that don't need input (Unit input).
 *
 * Use this for commands that are triggered without a request body,
 * such as health checks or simple getters.
 *
 * **Usage:**
 * ```kotlin
 * class HealthCheckCommand(
 *     execution: SessionExecution
 * ) : UnitInputServiceCommandAdapter<HealthStatus>(
 *     commandId = "health.check",
 *     execution = execution,
 *     outputTypeToken = typeToken<HealthStatus>()
 * ) {
 *     override suspend fun doExecute(
 *         args: Unit,
 *         applyDuring: (Unit) -> Unit
 *     ): IdkResult<HealthStatus, IdkError> = Ok(HealthStatus(healthy = true))
 * }
 * ```
 */
abstract class UnitInputServiceCommandAdapter<TOutput : Any>(
    commandId: String,
    execution: SessionExecution,
    outputTypeToken: TypeToken<TOutput>,
    isEnabled: Boolean = true,
    initExtensions: Array<ICommandInitExtension<Unit, TOutput, IdkError>> = emptyArray(),
    executionExtensions: Array<ICommandExecutionExtension<Unit, TOutput, IdkError>> = emptyArray(),
) : TypedServiceCommandAdapter<Unit, TOutput>(
    commandId = commandId,
    execution = execution,
    inputTypeToken = TypeToken.UNIT,
    outputTypeToken = outputTypeToken,
    isEnabled = isEnabled,
    initExtensions = initExtensions,
    executionExtensions = executionExtensions,
)
