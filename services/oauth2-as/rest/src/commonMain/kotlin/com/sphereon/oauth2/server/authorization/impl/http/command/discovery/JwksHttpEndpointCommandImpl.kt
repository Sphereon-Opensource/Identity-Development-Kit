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

package com.sphereon.oauth2.server.authorization.impl.http.command.discovery

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.discovery.JwksHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.jwks.HandleJwksRequestArgs
import com.sphereon.oauth2.server.authorization.command.jwks.HandleJwksRequestCommand
import com.sphereon.oauth2.server.authorization.impl.http.mapOAuth2ErrorToResponse
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * HTTP shell over [HandleJwksRequestCommand]. Always available regardless of OIDC mode since
 * JWKS is needed to verify access tokens.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<JwksHttpEndpointCommand>())
class JwksHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val handleJwksRequestCommand: HandleJwksRequestCommand,
) : HttpEndpointCommandAdapter(
        id = JwksHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = JwksHttpEndpointCommand.ENDPOINT,
    ),
    JwksHttpEndpointCommand {
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
        val result = handleJwksRequestCommand.execute(HandleJwksRequestArgs())

        val response =
            if (result.isOk) {
                val responseBody = json.encodeToString(result.value)
                GenericHttpResponse(
                    statusCode = 200,
                    headers =
                        mapOf(
                            "Content-Type" to "application/json",
                            "Cache-Control" to "max-age=3600",
                        ),
                    body = responseBody,
                )
            } else {
                mapOAuth2ErrorToResponse(result.error, json)
            }
        return Ok(response)
    }
}
