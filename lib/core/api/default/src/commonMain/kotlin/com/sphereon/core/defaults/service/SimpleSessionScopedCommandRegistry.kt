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

package com.sphereon.core.defaults.service

import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Simple session-scoped command registry for IDK standalone usage.
 *
 * Collects lightweight [RegistrableServiceCommandDescriptor]s via multibinding and
 * lazily instantiates commands on first access. In IDK standalone mode (no EDK/VDX),
 * all commands are LOCAL so no config-based routing is needed.
 *
 * VDX replaces this with [DefaultSessionScopedCommandRegistry] which adds
 * config-aware LOCAL/SERVER selection and app-registry fallback.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SessionScopedCommandRegistry>())
class SimpleSessionScopedCommandRegistry(
    descriptors: Set<RegistrableServiceCommandDescriptor>
) : SessionScopedCommandRegistry {

    companion object {
        const val NOOP_COMMAND_ID = "__noop_descriptor__"
    }

    private val descriptorMap: Map<String, RegistrableServiceCommandDescriptor> = descriptors
        .filter { it.commandId != NOOP_COMMAND_ID }
        .associateBy { it.commandId }

    private val cache = mutableMapOf<String, ServiceCommand<*, *>>()

    override fun get(commandId: String): ServiceCommand<*, *>? =
        cache.getOrPut(commandId) { descriptorMap[commandId]?.create() ?: return null }

    override fun getLocal(commandId: String): ServiceCommand<*, *>? = get(commandId)

    override fun listCommandIds(): List<String> = descriptorMap.keys.toList()
}

/**
 * Provides access to [SessionScopedCommandRegistry] from the session component.
 */
@ContributesTo(SessionScope::class)
interface SimpleSessionScopedCommandRegistryComponent {
    val sessionScopedCommandRegistry: SessionScopedCommandRegistry
}

/**
 * NoOp descriptor stub that ensures `Set<RegistrableServiceCommandDescriptor>` always
 * has at least one binding at [SessionScope].
 *
 * kotlin-inject multibinding requires at least one `@IntoSet` provider to produce
 * a `Set<T>`. Without this stub, modules without any real command descriptors would
 * fail to compile. The sentinel is filtered out by all registry implementations.
 */
@ContributesTo(SessionScope::class)
interface NoOpDescriptorComponent {
    @Provides
    @IntoSet
    fun noOpDescriptor(): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(
            SimpleSessionScopedCommandRegistry.NOOP_COMMAND_ID
        ) {
            error("NoOp descriptor should never be instantiated")
        }
}
