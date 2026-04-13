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
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrorResponse
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderSessionStatus
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderSessionStore
import com.sphereon.openid.oid4vci.holder.rest.SessionDeferredPollRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString

/**
 * Command interface for polling a deferred credential endpoint within a session.
 *
 * POST /sessions/{id}/deferred/poll
 *
 * Uses the session's access token to poll the issuer's deferred credential endpoint.
 * On success, updates session status to CREDENTIAL_RECEIVED.
 */
interface SessionDeferredPollEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.client.sessionDeferredPoll"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/sessions/{id}/deferred/poll",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "sessionPollDeferred",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-holder-session"),
                summary = "Poll the deferred credential endpoint using the session's access token",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SessionDeferredPollEndpointCommand>())
class SessionDeferredPollEndpointCommandImpl(
    execution: SessionExecution,
    private val clientService: Oid4vciHolderService,
    private val sessionStore: Oid4vciHolderSessionStore,
) : HttpEndpointCommandAdapter(
        id = SessionDeferredPollEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = SessionDeferredPollEndpointCommand.ENDPOINT,
    ),
    SessionDeferredPollEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams("/sessions/{id}/deferred/poll")

        val sessionId = req.requirePathParam("id").getOrElse { return Err(it) }

        val deferredReq =
            try {
                holderJson.decodeFromString<SessionDeferredPollRequest>(request.body ?: "{}")
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed deferred poll request: ${expected.message}"))
            }

        val session =
            sessionStore.get(sessionId).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Session $sessionId not found"))

        val accessToken =
            session.accessToken
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Session $sessionId has no access token"))

        val response =
            clientService
                .requestDeferredCredential(
                    deferredCredentialEndpoint = deferredReq.deferredCredentialEndpoint,
                    accessToken = accessToken,
                    transactionId = deferredReq.transactionId,
                    credentialResponseEncryption = deferredReq.credentialResponseEncryption,
                ).getOrElse { error ->
                    val errorMsg = error.message.defaultMessage
                    return when {
                        errorMsg == Oid4vciErrors.ISSUANCE_PENDING || error.code == Oid4vciErrors.ISSUANCE_PENDING -> {
                            Ok(
                                GenericHttpResponse(
                                    statusCode = 202,
                                    headers = JSON_HEADERS,
                                    body =
                                        holderJson.encodeToString(
                                            Oid4vciErrorResponse(
                                                error = Oid4vciErrors.ISSUANCE_PENDING,
                                                errorDescription = "Credential issuance is still pending",
                                                interval = 5,
                                            ),
                                        ),
                                ),
                            )
                        }

                        else -> {
                            Err(error)
                        }
                    }
                }

        sessionStore.update(session.copy(status = Oid4vciHolderSessionStatus.CREDENTIAL_RECEIVED))
        return Ok(jsonResponse(200, holderJson.encodeToString(response)))
    }
}
