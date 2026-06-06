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

package com.sphereon.core.api.service

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.OptionalBinding
import kotlin.jvm.JvmStatic

/**
 * Non-generic marker for registry lookup and policy enforcement.
 *
 * Any command that wants to be discoverable via [ServiceCommandRegistry] should
 * implement this interface. [ServiceCommand] extends this automatically.
 */
@JsExportCompat
interface RegistrableServiceCommand {
    val commandId: String
}

/**
 * Lightweight descriptor for lazy command registration via multibinding.
 *
 * Each descriptor holds a command ID and a factory that lazily creates the command
 * instance. This enables session-scoped commands to be registered in the DI graph
 * without eagerly instantiating them.
 */
@JsExportCompat
interface RegistrableServiceCommandDescriptor {
    val commandId: String

    fun create(): ServiceCommand<*, *, *>

    companion object {
        @JvmStatic
        fun of(
            commandId: String,
            factory: () -> ServiceCommand<*, *, *>,
        ): RegistrableServiceCommandDescriptor =
            object : RegistrableServiceCommandDescriptor {
                override val commandId: String = commandId

                override fun create(): ServiceCommand<*, *, *> = factory()
            }
    }
}

/**
 * App-scoped discovery registry for command metadata.
 *
 * This interface is intentionally **discovery-only** - it answers "does this command
 * exist?" but does NOT provide command instances for execution. This is because
 * most command implementations are session-scoped (they depend on SessionExecution)
 * and cannot be resolved from app scope.
 *
 * Use this for:
 * - Routing decisions: checking if a local implementation exists before routing remotely
 * - Introspection: listing available commands for documentation or health checks
 * - Validation: verifying that a configured LOCAL-mode command actually has an implementation
 *
 * For actual command execution, use [SessionScopedCommandRegistry] which
 * resolves commands within the correct session scope.
 */
@JsExportCompat
interface ServiceCommandRegistry {
    fun has(commandId: String): Boolean

    fun listCommandIds(): List<String>
}

/**
 * Session-scoped registry that can resolve and execute commands.
 *
 * This registry operates within a session scope and can properly instantiate
 * session-scoped commands (which depend on SessionExecution, tenant context, etc.).
 *
 * Commands are registered via map multibinding (`@IntoMap @StringKey`) and
 * lazily instantiated on first access.
 */
interface SessionScopedCommandRegistry {
    fun get(commandId: String): ServiceCommand<*, *, *>?

    fun has(commandId: String): Boolean = get(commandId) != null

    fun listCommandIds(): List<String>

    /**
     * Gets a command implementation bypassing any configuration-based overrides.
     *
     * Default implementation delegates to [get].
     *
     * @param commandId The command to look up
     * @return The command implementation, or null if not found
     */
    fun getLocal(commandId: String): ServiceCommand<*, *, *>? = get(commandId)

    @ContributesTo(SessionScope::class)
    interface Graph {
        val sessionScopedCommandRegistry: SessionScopedCommandRegistry
    }
}

/**
 * Exposes [ServiceCommandRegistry] as an optional graph accessor so that consumers declaring
 * `ServiceCommandRegistry? = null` constructor parameters resolve cleanly under the Metro
 * `nullable type key`. Suppliers add a second
 * `@ContributesBinding(AppScope::class, binding = binding<ServiceCommandRegistry?>())` so the
 * default `null` body here is overridden whenever a real binding is present in the graph.
 */
@ContributesTo(AppScope::class)
interface ServiceCommandRegistryOptionalProvider {
    @OptionalBinding
    val optionalServiceCommandRegistry: ServiceCommandRegistry? get() = null
}

/**
 * Exposes [SessionScopedCommandRegistry] as an optional graph accessor so that consumers declaring
 * `SessionScopedCommandRegistry? = null` constructor parameters resolve cleanly under the Metro
 * `nullable type key`. Suppliers (the IDK [com.sphereon.core.defaults.service.SimpleSessionScopedCommandRegistry]
 * and the VDX `DefaultSessionScopedCommandRegistry`) add a second
 * `@ContributesBinding(SessionScope::class, binding = binding<SessionScopedCommandRegistry?>())`
 * so the default `null` body here is overridden whenever a real binding is present in the graph.
 */
@ContributesTo(SessionScope::class)
interface SessionScopedCommandRegistryOptionalProvider {
    @OptionalBinding
    val optionalSessionScopedCommandRegistry: SessionScopedCommandRegistry? get() = null
}
