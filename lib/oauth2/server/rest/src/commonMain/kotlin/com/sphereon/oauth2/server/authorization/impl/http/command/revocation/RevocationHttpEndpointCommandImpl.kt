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
package com.sphereon.oauth2.server.authorization.impl.http.command.revocation

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
import com.sphereon.oauth2.server.authorization.audit.OAuth2AuditEmitter
import com.sphereon.oauth2.server.authorization.audit.OAuth2AuditEventType
import com.sphereon.oauth2.server.authorization.command.revocation.HandleRevocationRequestArgs
import com.sphereon.oauth2.server.authorization.command.revocation.HandleRevocationRequestCommand
import com.sphereon.oauth2.server.authorization.command.revocation.RevocationHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.isBasicAuthorizationHeaderInternal
import com.sphereon.oauth2.server.authorization.impl.http.mapOAuth2ErrorToResponse
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import com.sphereon.oauth2.server.authorization.impl.http.parseFormBody
import com.sphereon.oauth2.server.authorization.impl.http.withWwwAuthenticateIfBasicInternal
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

/**
 * HTTP shell over [HandleRevocationRequestCommand] (RFC 7009). On success returns 200 with empty
 * body per RFC 7009 §2.2. On client-auth failure with Basic auth attempted, emits
 * `WWW-Authenticate: Basic` per RFC 6749 §5.2.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(RevocationHttpEndpointCommand.COMMAND_ID)
class RevocationHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val handleRevocationRequestCommand: HandleRevocationRequestCommand,
    private val configProvider: OAuth2ServersConfigProvider,
    private val baseUrlResolver: OAuth2ServerBaseUrlResolver,
    private val auditEmitter: OAuth2AuditEmitter,
) : HttpEndpointCommandAdapter(
        id = RevocationHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = RevocationHttpEndpointCommand.ENDPOINT,
    ),
    RevocationHttpEndpointCommand {
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
            handleRevocationRequestCommand.execute(
                HandleRevocationRequestArgs(
                    requestBody = requestBody,
                    requestHeaders = request.headers,
                    httpUrl = "${baseUrlResolver.resolveBaseUrl(request, configProvider)}/revoke",
                ),
            )
        val basicAuthWasAttempted = isBasicAuthorizationHeaderInternal(request.headers)

        // Per RFC 7009 §2.2 the AS returns 200 OK whether or not the token was active at
        // submission time; we therefore emit REVOKE_GRANT for any authenticated revocation,
        // and REVOKE_GRANT_ERROR only for client-auth / parse failures that surface as
        // non-200 responses.
        auditEmitter.emit(
            type = if (result.isOk) OAuth2AuditEventType.REVOKE_GRANT else OAuth2AuditEventType.REVOKE_GRANT_ERROR,
            tenantId = execution.tenantId,
            clientId = requestBody["client_id"]?.firstOrNull(),
            metadata = buildMap { requestBody["token_type_hint"]?.firstOrNull()?.let { put("token_type_hint", it) } },
            errorCode = if (result.isOk) null else result.error.code,
            errorMessage = if (result.isOk) null else result.error.message.defaultMessage,
        )

        val response =
            if (result.isOk) {
                GenericHttpResponse(
                    statusCode = 200,
                    headers =
                        mapOf(
                            "Content-Type" to "application/json",
                            "Cache-Control" to "no-store",
                        ),
                    body = "",
                )
            } else {
                mapOAuth2ErrorToResponse(result.error, json, execution).withWwwAuthenticateIfBasicInternal(basicAuthWasAttempted)
            }
        return Ok(response)
    }
}
