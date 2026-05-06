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

package com.sphereon.oauth2.server.authorization.impl.http.command.federation

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.federation.ListEnabledFederationProvidersArgs
import com.sphereon.oauth2.server.authorization.command.federation.ListEnabledFederationProvidersCommand
import com.sphereon.oauth2.server.authorization.command.federation.ListFederationProvidersHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * HTTP shell over [ListEnabledFederationProvidersCommand]. Renders the providers as a compact
 * JSON array `[{id, name, enabled}]` for the login UI provider selection.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListFederationProvidersHttpEndpointCommand>())
class ListFederationProvidersHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val listEnabledFederationProvidersCommand: ListEnabledFederationProvidersCommand,
) : HttpEndpointCommandAdapter(
        id = ListFederationProvidersHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ListFederationProvidersHttpEndpointCommand.ENDPOINT,
    ),
    ListFederationProvidersHttpEndpointCommand {
    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        applyDuring(args)
        val result = listEnabledFederationProvidersCommand.execute(ListEnabledFederationProvidersArgs)
        if (!result.isOk) {
            return Ok(oauth2ErrorResponse(500, "server_error", result.error.message.defaultMessage, json))
        }

        val enabledProviders =
            result.value.providers.map { config ->
                JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(config.id),
                        "name" to JsonPrimitive(config.name),
                        "enabled" to JsonPrimitive(config.enabled),
                    ),
                )
            }
        val body = JsonArray(enabledProviders)

        return Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers = mapOf("Content-Type" to "application/json", "Cache-Control" to "no-store"),
                body = body.toString(),
            ),
        )
    }
}
