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

package com.sphereon.oauth2.server.authorization.impl.http.command.introspection

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.audit.OAuth2AuditEmitter
import com.sphereon.oauth2.server.authorization.audit.OAuth2AuditEventType
import com.sphereon.oauth2.server.authorization.command.introspection.HandleIntrospectionRequestArgs
import com.sphereon.oauth2.server.authorization.command.introspection.HandleIntrospectionRequestCommand
import com.sphereon.oauth2.server.authorization.command.introspection.IntrospectionHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.isBasicAuthorizationHeaderInternal
import com.sphereon.oauth2.server.authorization.impl.http.mapOAuth2ErrorToResponse
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import com.sphereon.oauth2.server.authorization.impl.http.parseFormBody
import com.sphereon.oauth2.server.authorization.impl.http.resolveBaseUrl
import com.sphereon.oauth2.server.authorization.impl.http.withWwwAuthenticateIfBasicInternal
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * HTTP shell over [HandleIntrospectionRequestCommand] (RFC 7662). Parses the form-encoded body
 * and renders the [com.sphereon.oauth2.common.model.TokenIntrospectionResponse] as JSON. On
 * client-auth failure with Basic auth attempted, emits `WWW-Authenticate: Basic` per RFC 6749 §5.2.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<IntrospectionHttpEndpointCommand>())
class IntrospectionHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val handleIntrospectionRequestCommand: HandleIntrospectionRequestCommand,
    private val configProvider: OAuth2ServersConfigProvider,
    private val auditEmitter: OAuth2AuditEmitter,
) : HttpEndpointCommandAdapter(
        id = IntrospectionHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = IntrospectionHttpEndpointCommand.ENDPOINT,
    ),
    IntrospectionHttpEndpointCommand {
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
        val requestBody =
            parseFormBody(request.body)
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing or invalid request body", json))

        val result =
            handleIntrospectionRequestCommand.execute(
                HandleIntrospectionRequestArgs(
                    requestBody = requestBody,
                    requestHeaders = request.headers,
                    httpUrl = "${request.resolveBaseUrl(configProvider)}/introspect",
                ),
            )
        val basicAuthWasAttempted = isBasicAuthorizationHeaderInternal(request.headers)

        // Emit OAuth2 domain audit event. RFC 7662 §2.2 distinguishes "introspection of an
        // inactive token" (active=false response) from "introspection failed" (auth error,
        // missing token param, storage error). Only the latter is INTROSPECT_TOKEN_ERROR; the
        // former is a successful INTROSPECT_TOKEN with `active_response=false` metadata.
        val activeResponse = if (result.isOk) result.value.active else null
        auditEmitter.emit(
            type = if (result.isOk) OAuth2AuditEventType.INTROSPECT_TOKEN else OAuth2AuditEventType.INTROSPECT_TOKEN_ERROR,
            clientId = requestBody["client_id"]?.firstOrNull(),
            metadata =
                buildMap {
                    requestBody["token_type_hint"]?.firstOrNull()?.let { put("token_type_hint", it) }
                    activeResponse?.let { put("active_response", it.toString()) }
                },
            errorCode = if (result.isOk) null else result.error.code,
            errorMessage = if (result.isOk) null else result.error.message.defaultMessage,
        )

        val response =
            if (result.isOk) {
                val responseBody = json.encodeToString(result.value)
                GenericHttpResponse(
                    statusCode = 200,
                    headers = mapOf("Content-Type" to "application/json"),
                    body = responseBody,
                )
            } else {
                mapOAuth2ErrorToResponse(result.error, json, execution).withWwwAuthenticateIfBasicInternal(basicAuthWasAttempted)
            }
        return Ok(response)
    }
}
