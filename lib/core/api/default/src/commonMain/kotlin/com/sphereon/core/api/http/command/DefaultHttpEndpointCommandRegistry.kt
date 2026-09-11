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

package com.sphereon.core.api.http.command

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.TimeSource

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HttpEndpointCommandRegistry>())
class DefaultHttpEndpointCommandRegistry(
    private val commands: Map<String, Lazy<HttpEndpointCommand>>,
    private val execution: SessionExecution,
) : HttpEndpointCommandRegistry {
    override fun get(handlerCommandId: String): HttpEndpointCommand? {
        val commandProvider = commands[handlerCommandId]
        if (commandProvider == null) {
            execution.log.info(
                message = "VDX_HTTP_ROUTE_FIRST_RESOLUTION",
                metadata =
                    mapOf(
                        "stage" to "selected-handler-resolution",
                        "handlerCommandId" to handlerCommandId,
                        "outcome" to "missing",
                        "durationMs" to "0",
                    ),
            )
            return null
        }
        val alreadyInitialized = commandProvider.isInitialized()
        val started = TimeSource.Monotonic.markNow()
        val command = commandProvider.value
        execution.log.info(
            message = "VDX_HTTP_ROUTE_FIRST_RESOLUTION",
            metadata =
                mapOf(
                    "stage" to "selected-handler-resolution",
                    "handlerCommandId" to handlerCommandId,
                    "outcome" to "success",
                    "alreadyInitialized" to alreadyInitialized.toString(),
                    "durationMs" to started.elapsedNow().inWholeMilliseconds.coerceAtLeast(0L).toString(),
                ),
        )
        return command
    }

    override fun listHandlerCommandIds(): Set<String> = commands.keys
}
