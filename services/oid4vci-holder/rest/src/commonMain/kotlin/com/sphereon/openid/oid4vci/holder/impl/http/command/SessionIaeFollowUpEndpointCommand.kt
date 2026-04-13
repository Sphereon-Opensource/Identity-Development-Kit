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
import com.sphereon.openid.oid4vci.holder.FollowUpIaeArgs
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderSessionStore
import com.sphereon.openid.oid4vci.holder.rest.SessionIaeFollowUpRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString

/**
 * Command interface for submitting an IAE follow-up request within a session.
 *
 * POST /sessions/{id}/iae/followUp
 *
 * Submits an OID4VCI 1.1 IAE follow-up request (VP presentation or code exchange)
 * using the session context.
 */
interface SessionIaeFollowUpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.client.sessionIaeFollowUp"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/sessions/{id}/iae/followUp",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "sessionIaeFollowUp",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-holder-session"),
                summary = "Submit an IAE follow-up request within a session",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SessionIaeFollowUpEndpointCommand>())
class SessionIaeFollowUpEndpointCommandImpl(
    execution: SessionExecution,
    private val clientService: Oid4vciHolderService,
    private val sessionStore: Oid4vciHolderSessionStore,
) : HttpEndpointCommandAdapter(
        id = SessionIaeFollowUpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = SessionIaeFollowUpEndpointCommand.ENDPOINT,
    ),
    SessionIaeFollowUpEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams("/sessions/{id}/iae/followUp")

        val sessionId = req.requirePathParam("id").getOrElse { return Err(it) }

        val iaeReq =
            try {
                holderJson.decodeFromString<SessionIaeFollowUpRequest>(request.body ?: "{}")
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed IAE follow-up request: ${expected.message}"))
            }

        // Validate session exists (used for context / future enrichment)
        sessionStore.get(sessionId).getOrElse { return Err(it) }
            ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Session $sessionId not found"))

        return clientService
            .followUpIae(
                FollowUpIaeArgs(
                    iaeEndpoint = iaeReq.iaeEndpoint,
                    authSession = iaeReq.authSession,
                    openid4vpResponse = iaeReq.openid4vpResponse,
                    codeVerifier = iaeReq.codeVerifier,
                ),
            ).map { result -> jsonResponse(200, holderJson.encodeToString(result)) }
    }
}
