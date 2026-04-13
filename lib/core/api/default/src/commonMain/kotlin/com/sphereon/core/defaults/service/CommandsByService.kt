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

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Accessor for looking up all commands within a service group (first two segments of the 3-part ID).
 *
 * Delegates to [SessionScopedCommandRegistry], so in EDK/VDX this is automatically
 * routing-aware (local vs remote based on config).
 *
 * Usage:
 * ```kotlin
 * @Inject
 * class MyService(private val byService: CommandsByService) {
 *     fun listKeyCommands() = byService["kms.keys"]  // kms.keys.get, kms.keys.list, etc.
 * }
 * ```
 */
@Inject
@SingleIn(SessionScope::class)
class CommandsByService(
    private val registry: SessionScopedCommandRegistry,
) {
    /**
     * Get all commands in a service group.
     * @param serviceId The module.service prefix, e.g. "kms.keys"
     * @return Map of full commandId to command
     */
    operator fun get(serviceId: String): Map<String, ServiceCommand<*, *>> =
        registry
            .listCommandIds()
            .filter { it.servicePrefix() == serviceId }
            .mapNotNull { id -> registry.get(id)?.let { id to it } }
            .toMap()

    fun listServices(): List<String> =
        registry
            .listCommandIds()
            .map { it.servicePrefix() }
            .distinct()
            .sorted()

    fun has(serviceId: String): Boolean = registry.listCommandIds().any { it.servicePrefix() == serviceId }

    private fun String.servicePrefix(): String {
        val parts = split('.')
        return if (parts.size >= 2) {
            "${parts[0]}.${parts[1]}"
        } else {
            this
        }
    }
}
