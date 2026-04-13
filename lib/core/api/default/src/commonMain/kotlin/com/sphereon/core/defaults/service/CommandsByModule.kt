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
 * Accessor for looking up all commands within a module (first segment of the 3-part ID).
 *
 * Delegates to [SessionScopedCommandRegistry], so in EDK/VDX this is automatically
 * routing-aware (local vs remote based on config).
 *
 * Usage:
 * ```kotlin
 * @Inject
 * class MyService(private val byModule: CommandsByModule) {
 *     fun listKmsCommands() = byModule["kms"]  // all kms.*.* commands
 * }
 * ```
 */
@Inject
@SingleIn(SessionScope::class)
class CommandsByModule(
    private val registry: SessionScopedCommandRegistry,
) {
    operator fun get(module: String): Map<String, ServiceCommand<*, *>> =
        registry
            .listCommandIds()
            .filter { it.substringBefore('.') == module }
            .mapNotNull { id -> registry.get(id)?.let { id to it } }
            .toMap()

    fun listModules(): List<String> =
        registry
            .listCommandIds()
            .map { it.substringBefore('.') }
            .distinct()
            .sorted()

    fun has(module: String): Boolean = registry.listCommandIds().any { it.substringBefore('.') == module }
}
