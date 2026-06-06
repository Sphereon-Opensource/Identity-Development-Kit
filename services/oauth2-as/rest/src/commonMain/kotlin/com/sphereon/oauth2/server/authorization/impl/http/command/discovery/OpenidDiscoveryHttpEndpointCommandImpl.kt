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
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.server.authorization.command.discovery.HandleDiscoveryRequestArgs
import com.sphereon.oauth2.server.authorization.command.discovery.HandleDiscoveryRequestCommand
import com.sphereon.oauth2.server.authorization.command.discovery.OpenidDiscoveryHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.mapOAuth2ErrorToResponse
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * HTTP shell over [HandleDiscoveryRequestCommand] for `GET /.well-known/openid-configuration`
 * (OpenID Connect Discovery 1.0). Reuses the same ServiceCommand the OAuth2 metadata endpoint
 * does — discovery output is the same shape, with OIDC-only fields filled in when the `oidc`
 * feature policy is enabled. When `oidc` is disabled this endpoint returns 404.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OpenidDiscoveryHttpEndpointCommand>())
class OpenidDiscoveryHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val handleDiscoveryRequestCommand: HandleDiscoveryRequestCommand,
    private val configProvider: OAuth2ServersConfigProvider,
    private val baseUrlResolver: OAuth2ServerBaseUrlResolver,
) : HttpEndpointCommandAdapter(
        id = OpenidDiscoveryHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = OpenidDiscoveryHttpEndpointCommand.ENDPOINT,
    ),
    OpenidDiscoveryHttpEndpointCommand {
    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

    override suspend fun supports(args: Any): Boolean =
        if (args is GenericHttpRequest) {
            args.matches(endpoint.method.name, endpoint.pathPattern) ||
                args.matches(endpoint.method.name, OpenidDiscoveryHttpEndpointCommand.TENANT_PATH_PATTERN)
        } else {
            false
        }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        if (!configProvider.serverConfig.oidc.isEnabled) {
            return Ok(
                GenericHttpResponse(
                    statusCode = 404,
                    headers = mapOf("Content-Type" to "application/json"),
                    body = json.encodeToString(mapOf("error" to "not_found", "error_description" to "OIDC is not enabled")),
                ),
            )
        }
        val tenantPath = request.queryParameters["tenant-path"]
        val baseUrlOverride = baseUrlResolver.resolveBaseUrl(request, configProvider, tenantPath)

        val result =
            handleDiscoveryRequestCommand.execute(
                HandleDiscoveryRequestArgs(baseUrlOverride = baseUrlOverride),
            )

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
