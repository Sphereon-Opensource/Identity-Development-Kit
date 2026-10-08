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
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Typed accessor for looking up commands by their full 3-part ID (module.service.command).
 *
 * Delegates to [SessionScopedCommandRegistry], so in EDK/VDX this is automatically
 * routing-aware (local vs remote based on config).
 *
 * Usage:
 * ```kotlin
 * @Inject
 * class MyService(private val commands: CommandById) {
 *     suspend fun doStuff(args: GetKeyArgs) {
 *         val cmd = commands.typed<GetKeyArgs, GetKeyResult>("kms.keys.get")
 *         cmd.execute(args)
 *     }
 * }
 * ```
 */
@Inject
@SingleIn(SessionScope::class)
class CommandById(
    private val registry: SessionScopedCommandRegistry,
) {
    operator fun get(commandId: String): ServiceCommand<*, *, *> =
        registry.get(commandId)
            ?: throw IllegalArgumentException("Unknown command: $commandId")

    @Suppress("UNCHECKED_CAST")
    fun <I : Any, O : Any> typed(commandId: String): ServiceCommand<I, O, IdkError> = get(commandId) as ServiceCommand<I, O, IdkError>

    fun getOrNull(commandId: String): ServiceCommand<*, *, *>? = registry.get(commandId)

    fun has(commandId: String): Boolean = registry.has(commandId)

    fun listIds(): List<String> = registry.listCommandIds()
}
