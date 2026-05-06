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

package com.sphereon.core.defaults.service

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Simple session-scoped command registry for IDK standalone usage.
 *
 * Consumes local commands via map multibinding (`@IntoMap @StringKey`) and
 * lazily instantiates them on first access. In IDK standalone mode (no EDK/VDX),
 * all commands are LOCAL so no config-based routing is needed.
 *
 * VDX replaces this with `DefaultSessionScopedCommandRegistry` which adds
 * config-aware LOCAL/SERVER selection via [RoutingCommandDelegate] and
 * app-registry fallback.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SessionScopedCommandRegistry>())
class SimpleSessionScopedCommandRegistry(
    private val commands: Map<String, Lazy<ServiceCommand<*, *, *>>>,
) : SessionScopedCommandRegistry {
    private val cache = mutableMapOf<String, ServiceCommand<*, *, *>>()

    override fun get(commandId: String): ServiceCommand<*, *, *>? = cache.getOrPut(commandId) { commands[commandId]?.value ?: return null }

    override fun getLocal(commandId: String): ServiceCommand<*, *, *>? = get(commandId)

    override fun listCommandIds(): List<String> = commands.keys.toList()
}
