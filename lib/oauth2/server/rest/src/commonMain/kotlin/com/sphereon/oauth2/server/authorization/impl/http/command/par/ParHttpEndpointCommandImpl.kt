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

// Role-neutral OAuth REST capability. Executable server startup remains in services-oauth2-as-rest.
package com.sphereon.oauth2.server.authorization.impl.http.command.par

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.server.authorization.command.par.HandlePushedAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.par.HandlePushedAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.par.ParHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.mapOAuth2ErrorToResponse
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import com.sphereon.oauth2.server.authorization.impl.http.parseFormBody
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * HTTP shell over [HandlePushedAuthorizationRequestCommand] (RFC 9126). Gated behind the per-server
 * `par` FeaturePolicy: when disabled, returns 404 — consistent with omitting
 * `pushed_authorization_request_endpoint` from discovery metadata.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(ParHttpEndpointCommand.COMMAND_ID)
class ParHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val handlePushedAuthorizationRequestCommand: HandlePushedAuthorizationRequestCommand,
    private val configProvider: OAuth2ServersConfigProvider,
    private val baseUrlResolver: OAuth2ServerBaseUrlResolver,
) : HttpEndpointCommandAdapter(
        id = ParHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ParHttpEndpointCommand.ENDPOINT,
    ),
    ParHttpEndpointCommand {
    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        if (!configProvider.serverConfig.par.isEnabled) {
            return Ok(oauth2ErrorResponse(404, "not_found", "Pushed Authorization Requests are not enabled on this server", json))
        }

        val requestBody =
            parseFormBody(request.body)
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing or invalid request body", json))

        val baseUrl = baseUrlResolver.resolveBaseUrl(request, configProvider)
        val result =
            handlePushedAuthorizationRequestCommand.execute(
                HandlePushedAuthorizationRequestArgs(
                    requestBody = requestBody,
                    requestHeaders = request.headers,
                    baseUrlOverride = baseUrl,
                    parEndpointUrl = baseUrl?.let { "$it/par" },
                ),
            )

        val response =
            if (result.isOk) {
                val responseBody = json.encodeToString(result.value)
                GenericHttpResponse(
                    statusCode = 201,
                    headers =
                        mapOf(
                            "Content-Type" to "application/json",
                            "Cache-Control" to "no-cache",
                        ),
                    body = responseBody,
                )
            } else {
                mapOAuth2ErrorToResponse(result.error, json, execution)
            }
        return Ok(response)
    }
}
