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
import com.sphereon.openid.oid4vci.holder.rest.SessionExchangeAuthorizationCodeRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Command interface for exchanging an authorization code for tokens within a session.
 *
 * POST /sessions/{id}/auth/exchange
 *
 * Exchanges an authorization code for tokens and updates the session with the obtained
 * access token, token type, c_nonce, and credential identifiers.
 */
interface SessionExchangeAuthCodeEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.client.sessionAuthExchange"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/sessions/{id}/auth/exchange",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "sessionExchangeAuthorizationCode",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-holder-session"),
                summary = "Exchange an authorization code for tokens within a session",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SessionExchangeAuthCodeEndpointCommand>())
class SessionExchangeAuthCodeEndpointCommandImpl(
    execution: SessionExecution,
    private val clientService: Oid4vciHolderService,
    private val sessionStore: Oid4vciHolderSessionStore,
) : HttpEndpointCommandAdapter(
        id = SessionExchangeAuthCodeEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = SessionExchangeAuthCodeEndpointCommand.ENDPOINT,
    ),
    SessionExchangeAuthCodeEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams("/sessions/{id}/auth/exchange")

        val sessionId = req.requirePathParam("id").getOrElse { return Err(it) }

        val exchangeReq =
            try {
                holderJson.decodeFromString<SessionExchangeAuthorizationCodeRequest>(request.body ?: "{}")
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed authorization code exchange request: ${expected.message}"))
            }

        val session =
            sessionStore.get(sessionId).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Session $sessionId not found"))

        val tokenResponse =
            clientService
                .exchangeAuthorizationCode(
                    tokenEndpoint = exchangeReq.tokenEndpoint,
                    code = exchangeReq.code,
                    codeVerifier = exchangeReq.codeVerifier,
                    redirectUri = exchangeReq.redirectUri,
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
