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

package com.sphereon.oauth2.server.authorization.impl.http.command.token

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.token.RegisterPreAuthorizedCodeArgs
import com.sphereon.oauth2.server.authorization.command.token.RegisterPreAuthorizedCodeCommand
import com.sphereon.oauth2.server.authorization.command.token.RegisterPreAuthorizedCodeHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Internal endpoint for cross-service pre-authorized code registration. Used when the OID4VCI
 * issuer runs in a separate process from the authorization server. Basic-auth parsing stays in
 * the HTTP layer; the credential check + storage write live in [RegisterPreAuthorizedCodeCommand].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RegisterPreAuthorizedCodeHttpEndpointCommand>())
class RegisterPreAuthorizedCodeHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val registerPreAuthorizedCodeCommand: RegisterPreAuthorizedCodeCommand,
) : HttpEndpointCommandAdapter(
        id = RegisterPreAuthorizedCodeHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = RegisterPreAuthorizedCodeHttpEndpointCommand.ENDPOINT,
    ),
    RegisterPreAuthorizedCodeHttpEndpointCommand {
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

        val authHeader = request.headers["authorization"] ?: request.headers["Authorization"] ?: ""
        if (!authHeader.startsWith("Basic ", ignoreCase = true)) {
            return Ok(oauth2ErrorResponse(401, "invalid_client", "Basic authentication required", json))
        }
        val decoded =
            try {
                authHeader
                    .removePrefix("Basic ")
                    .removePrefix("basic ")
                    .trim()
                    .decodeFromBase64()
                    .decodeToString()
            } catch (_: Exception) {
                return Ok(oauth2ErrorResponse(401, "invalid_client", "Invalid Basic auth encoding", json))
            }
        val parts = decoded.split(":", limit = 2)
        if (parts.size != 2) {
            return Ok(oauth2ErrorResponse(401, "invalid_client", "Invalid Basic auth format", json))
        }
        val (basicAuthClientId, basicAuthClientSecret) = parts

        val body =
            request.body
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing request body", json))
        val req =
            try {
                json.decodeFromString<PreAuthCodeRegistrationRequest>(body)
            } catch (expected: Exception) {
                return Ok(oauth2ErrorResponse(400, "invalid_request", "Invalid JSON: ${expected.message}", json))
            }

        val result =
            registerPreAuthorizedCodeCommand.execute(
                RegisterPreAuthorizedCodeArgs(
                    basicAuthClientId = basicAuthClientId,
                    basicAuthClientSecret = basicAuthClientSecret,
                    code = req.code,
                    sessionId = req.sessionId,
                    credentialConfigurationIds = req.credentialConfigurationIds,
                    txCodeRequired = req.txCodeRequired,
                    txCodeHash = req.txCodeHash,
                    issuerIdentifier = req.issuerIdentifier,
                    useCredentialIdentifiers = req.useCredentialIdentifiers,
                ),
            )
        if (!result.isOk) {
            val error = result.error
            val status = if (error.code == "invalid_client") 401 else 500
            val oauthCode = error.code ?: "server_error"
            return Ok(oauth2ErrorResponse(status, oauthCode, error.message.defaultMessage, json))
        }

        return Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers = mapOf("Content-Type" to "application/json"),
                body = json.encodeToString(mapOf("status" to result.value.status)),
            ),
        )
    }
}

/**
 * Wire shape for the Basic-auth-protected pre-authorized code registration request body.
 */
@Serializable
internal data class PreAuthCodeRegistrationRequest(
    val code: String,
    val sessionId: String,
    val credentialConfigurationIds: List<String>,
    val txCodeRequired: Boolean = false,
    val txCodeHash: String? = null,
    val issuerIdentifier: String? = null,
    val useCredentialIdentifiers: Boolean = true,
)
