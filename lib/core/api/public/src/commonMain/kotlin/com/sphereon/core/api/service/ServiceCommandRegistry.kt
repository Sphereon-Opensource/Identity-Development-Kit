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

/**
 * Non-generic marker for DI and registry lookup.
 *
 * This overcomes kotlin-inject's limitation with generic type multibinding.
 * Any command that wants to be discoverable via [ServiceCommandRegistry] should
 * implement this interface. [ServiceCommand] extends this automatically.
 */
interface RegistrableServiceCommand {
    val commandId: String
}

/**
 * App-scoped discovery registry for command metadata.
 *
 * This interface is intentionally **discovery-only** - it answers "does this command
 * exist?" but does NOT provide command instances for execution. This is because
 * most command implementations are session-scoped (they depend on [SessionExecution])
 * and cannot be resolved from app scope.
 *
 * Use this for:
 * - Routing decisions: checking if a local implementation exists before routing remotely
 * - Introspection: listing available commands for documentation or health checks
 * - Validation: verifying that a configured LOCAL-mode command actually has an implementation
 *
 * For actual command execution in LOCAL mode, use [SessionScopedCommandRegistry] which
 * resolves commands within the correct session scope.
 */
interface ServiceCommandRegistry {
    fun has(commandId: String): Boolean
    fun listCommandIds(): List<String>
}

/**
 * Session-scoped registry that can resolve and execute commands.
 *
 * This registry operates within a session scope and can properly instantiate
 * session-scoped commands (which depend on [SessionExecution], tenant context, etc.).
 *
 * Implementations typically collect session-scoped [ServiceCommand] instances
 * via multibinding and index them by commandId.
 */
interface SessionScopedCommandRegistry {
    fun get(commandId: String): ServiceCommand<*, *>?
    fun has(commandId: String): Boolean = get(commandId) != null
    fun listCommandIds(): List<String>

    /**
     * Gets a command implementation bypassing any configuration-based overrides.
     *
     * Default implementation delegates to [get] for backward compatibility.
     *
     * @param commandId The command to look up
     * @return The command implementation, or null if not found
     */
    fun getLocal(commandId: String): ServiceCommand<*, *>? = get(commandId)
}

/**
 * Lightweight descriptor for lazy command registration.
 *
 * Instead of eagerly instantiating all session-scoped commands when the
 * [SessionScopedCommandRegistry] is created, descriptors carry only metadata
 * ([commandId]) and a lazy factory. The registry collects descriptors cheaply
 * and only calls [create] when a specific command is first requested.
 *
 * This is critical for per-request session scopes in REST APIs: without lazy
 * descriptors, every request would instantiate ALL command implementations
 * (even unused ones) just to populate the multibinding set.
 */
interface RegistrableServiceCommandDescriptor {
    val commandId: String
    fun create(): ServiceCommand<*, *>

    companion object {
        fun of(
            commandId: String,
            factory: () -> ServiceCommand<*, *>
        ): RegistrableServiceCommandDescriptor = object : RegistrableServiceCommandDescriptor {
            override val commandId = commandId
            override fun create() = factory()
        }
    }
}
