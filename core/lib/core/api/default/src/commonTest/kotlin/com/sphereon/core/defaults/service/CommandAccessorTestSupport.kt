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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.SessionScopedCommandRegistry

internal class StubRegistry(
    private val commands: Map<String, ServiceCommand<*, *, *>>,
) : SessionScopedCommandRegistry {
    override fun get(commandId: String): ServiceCommand<*, *, *>? = commands[commandId]

    override fun has(commandId: String): Boolean = commands.containsKey(commandId)

    override fun listCommandIds(): List<String> = commands.keys.toList()
}

internal class StubCommand(
    override val commandId: String,
) : ServiceCommand<Any, Any, IdkError> {
    override val id: String get() = commandId
    override val isEnabled: Boolean get() = true
    override val subsystem: EventSubsystem get() = EventSubsystems.CUSTOM
    override val inputTypeToken: TypeToken<Any> get() = typeToken<Any>()
    override val outputTypeToken: TypeToken<Any> get() = typeToken<Any>()

    override suspend fun execute(args: Any): IdkResult<Any, IdkError> = Ok(Unit)
}
