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
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderSessionStore
import com.sphereon.openid.oid4vci.holder.rest.SessionNotifyRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Command interface for sending a credential lifecycle notification within a session.
 *
 * POST /sessions/{id}/notify
 *
 * Uses the session's access token to send a notification to the issuer. Returns 204 on success.
 */
interface SessionNotifyEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.client.sessionNotify"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/sessions/{id}/notify",
                consumes = setOf(MediaType.ApplicationJson),
                operationId = "sessionSendNotification",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-holder-session"),
                summary = "Send a credential lifecycle notification using the session's access token",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SessionNotifyEndpointCommand>())
class SessionNotifyEndpointCommandImpl(
    execution: SessionExecution,
    private val clientService: Oid4vciHolderService,
    private val sessionStore: Oid4vciHolderSessionStore,
) : HttpEndpointCommandAdapter(
        id = SessionNotifyEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = SessionNotifyEndpointCommand.ENDPOINT,
    ),
    SessionNotifyEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams("/sessions/{id}/notify")

        val sessionId = req.requirePathParam("id").getOrElse { return Err(it) }

        val notifyReq =
            try {
                holderJson.decodeFromString<SessionNotifyRequest>(request.body ?: "{}")
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed notification request: ${expected.message}"))
            }

        val session =
            sessionStore.get(sessionId).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Session $sessionId not found"))

        val accessToken =
            session.accessToken
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Session $sessionId has no access token"))

        return clientService
            .sendNotification(
                notificationEndpoint = notifyReq.notificationEndpoint,
                accessToken = accessToken,
                notificationId = notifyReq.notificationId,
                event = notifyReq.event,
                eventDescription = notifyReq.eventDescription,
            ).map { GenericHttpResponse(statusCode = 204, headers = emptyMap(), body = null) }
    }
}
