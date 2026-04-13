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

package com.sphereon.openid.oid4vci.holder.impl.http.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.jsonResponse
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderSessionStatus
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderSessionStore
import com.sphereon.openid.oid4vci.holder.rest.SessionTokenRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Command interface for exchanging a pre-authorized code for a token within a session.
 *
 * POST /sessions/{id}/token
 *
 * Exchanges the pre-authorized code for an access token and updates the session.
 */
interface SessionTokenEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.client.sessionToken"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/sessions/{id}/token",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "sessionExchangeToken",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-holder-session"),
                summary = "Exchange a pre-authorized code for a token within a session",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SessionTokenEndpointCommand>())
class SessionTokenEndpointCommandImpl(
    execution: SessionExecution,
    private val clientService: Oid4vciHolderService,
    private val sessionStore: Oid4vciHolderSessionStore,
) : HttpEndpointCommandAdapter(
        id = SessionTokenEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = SessionTokenEndpointCommand.ENDPOINT,
    ),
    SessionTokenEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams("/sessions/{id}/token")

        val sessionId = req.requirePathParam("id").getOrElse { return Err(it) }

        val tokenReq =
            try {
                holderJson.decodeFromString<SessionTokenRequest>(request.body ?: "{}")
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed token request: ${expected.message}"))
            }

        val session =
            sessionStore.get(sessionId).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Session $sessionId not found"))

        val tokenResponse =
            clientService
                .exchangePreAuthorizedCode(
                    tokenEndpoint = tokenReq.tokenEndpoint,
                    preAuthorizedCode = tokenReq.preAuthorizedCode,
                    txCode = tokenReq.txCode,
                ).getOrElse { error -> return Err(error) }

        val credentialIdentifiers =
            tokenResponse.authorizationDetails
                ?.flatMap { detail ->
                    try {
                        val detailObj = detail.jsonObject
                        detailObj["credential_identifiers"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }?.ifEmpty { null }

        val updated =
            session.copy(
                accessToken = tokenResponse.accessToken,
                tokenType = tokenResponse.tokenType,
                cNonce = tokenResponse.cNonce,
                credentialIdentifiers = credentialIdentifiers,
                status = Oid4vciHolderSessionStatus.TOKEN_OBTAINED,
            )
        sessionStore.update(updated).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, holderJson.encodeToString(tokenResponse)))
    }
}
