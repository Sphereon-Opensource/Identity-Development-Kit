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

package com.sphereon.oauth2.server.authorization.impl.http.command.device

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
import com.sphereon.oauth2.server.authorization.command.device.DeviceAuthorizationHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.device.IssueDeviceAuthorizationArgs
import com.sphereon.oauth2.server.authorization.command.device.IssueDeviceAuthorizationCommand
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.mapOAuth2ErrorToResponse
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import com.sphereon.oauth2.server.authorization.impl.http.parseFormBody
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * HTTP shell over [IssueDeviceAuthorizationCommand] (RFC 8628 §3.1: Device Authorization Request).
 *
 * Parses the `application/x-www-form-urlencoded` body for `client_id`, optional `scope`, and the
 * RFC 8707 multi-value `resource` / `audience` parameters, resolves the per-request base URL the
 * way `/token` and `/par` do, and dispatches to the backing ServiceCommand. The §3.2 success
 * response is rendered with `Cache-Control: no-store` per RFC 6749 §5.1, and any
 * [com.sphereon.oauth2.server.authorization.error.AuthorizationServerError] surfacing from the
 * ServiceCommand is mapped through the shared [mapOAuth2ErrorToResponse] renderer.
 *
 * The `deviceFlow` feature policy gates the surface: when disabled the endpoint returns a 404
 * `not_found` error, mirroring how `/par` and `/attestation-challenge` short-circuit when their
 * features are off so discovery and the runtime stay consistent.
 *
 * Client authentication is not handled inline. Public clients (the dominant device-flow case)
 * authenticate by `client_id` alone; confidential clients carry their credentials through the
 * standard token-endpoint paths and the AS validates them at higher layers via
 * `VerifyClientAuthenticationCommand` when the device-code grant is later exchanged at `/token`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeviceAuthorizationHttpEndpointCommand>())
class DeviceAuthorizationHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val issueDeviceAuthorizationCommand: IssueDeviceAuthorizationCommand,
    private val configProvider: OAuth2ServersConfigProvider,
    private val baseUrlResolver: OAuth2ServerBaseUrlResolver,
) : HttpEndpointCommandAdapter(
        id = DeviceAuthorizationHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = DeviceAuthorizationHttpEndpointCommand.ENDPOINT,
    ),
    DeviceAuthorizationHttpEndpointCommand {
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

        if (!configProvider.serverConfig.deviceFlow.isEnabled) {
            return Ok(oauth2ErrorResponse(404, "not_found", "Device authorization endpoint is not enabled on this server", json))
        }

        // RFC 8628 §3.1: the device authorization request MUST be a POST. Other methods are not
        // defined on this endpoint, so reject them with 405 before touching the body.
        if (!request.method.equals("POST", ignoreCase = true)) {
            return Ok(
                GenericHttpResponse(
                    statusCode = 405,
                    headers =
                        mapOf(
                            "Content-Type" to "application/json",
                            "Cache-Control" to "no-store",
                            "Pragma" to "no-cache",
                            "Allow" to "POST",
                        ),
                    body = json.encodeToString(JsonObject.serializer(), JsonObject(mapOf("error" to JsonPrimitive("invalid_request")))),
                ),
            )
        }

        // RFC 8628 §3.1: the request body MUST be `application/x-www-form-urlencoded`.
        val contentType = request.headers["Content-Type"] ?: request.headers["content-type"]
        if (contentType == null || !contentType.substringBefore(';').trim().equals("application/x-www-form-urlencoded", ignoreCase = true)) {
            return Ok(oauth2ErrorResponse(400, "invalid_request", "Content-Type must be application/x-www-form-urlencoded", json))
        }

        val requestBody =
            parseFormBody(request.body)
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing or invalid request body", json))

        val clientId =
            requestBody["client_id"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing required parameter: client_id", json))

        val scope = requestBody["scope"]?.firstOrNull()?.takeIf { it.isNotBlank() }
        val resource = requestBody["resource"]?.takeIf { it.isNotEmpty() }
        val audience = requestBody["audience"]?.takeIf { it.isNotEmpty() }

        val baseUrl = baseUrlResolver.resolveBaseUrl(request, configProvider)

        val result =
            issueDeviceAuthorizationCommand.execute(
                IssueDeviceAuthorizationArgs(
                    clientId = clientId,
                    scope = scope,
                    resource = resource,
                    audience = audience,
                    baseUrlOverride = baseUrl,
                ),
            )

        if (result.isErr) {
            return Ok(mapOAuth2ErrorToResponse(result.error, json, execution))
        }

        val issued = result.value
        val body =
            JsonObject(
                mapOf(
                    "device_code" to JsonPrimitive(issued.deviceCode),
                    "user_code" to JsonPrimitive(issued.userCode),
                    "verification_uri" to JsonPrimitive(issued.verificationUri),
                    "verification_uri_complete" to JsonPrimitive(issued.verificationUriComplete),
                    "expires_in" to JsonPrimitive(issued.expiresIn),
                    "interval" to JsonPrimitive(issued.intervalSeconds),
                ),
            )
        return Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers =
                    mapOf(
                        "Content-Type" to "application/json",
                        "Cache-Control" to "no-store",
                        "Pragma" to "no-cache",
                    ),
                body = json.encodeToString(JsonObject.serializer(), body),
            ),
        )
    }
}
