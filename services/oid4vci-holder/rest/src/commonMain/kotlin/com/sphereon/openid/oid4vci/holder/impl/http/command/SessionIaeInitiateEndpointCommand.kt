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
import com.sphereon.openid.oid4vci.holder.InitiateIaeArgs
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderSessionStore
import com.sphereon.openid.oid4vci.holder.rest.SessionInitiateIaeRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString

/**
 * Command interface for initiating an IAE flow within a session.
 *
 * POST /sessions/{id}/iae/initiate
 *
 * Uses the session's credential configuration IDs to build authorization_details
 * and initiates an OID4VCI 1.1 IAE flow.
 */
interface SessionIaeInitiateEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.client.sessionIaeInitiate"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/sessions/{id}/iae/initiate",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "sessionInitiateIae",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-holder-session"),
                summary = "Initiate an IAE flow using the session's credential configuration IDs",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SessionIaeInitiateEndpointCommand>())
class SessionIaeInitiateEndpointCommandImpl(
    execution: SessionExecution,
    private val clientService: Oid4vciHolderService,
    private val sessionStore: Oid4vciHolderSessionStore,
) : HttpEndpointCommandAdapter(
        id = SessionIaeInitiateEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = SessionIaeInitiateEndpointCommand.ENDPOINT,
    ),
    SessionIaeInitiateEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams("/sessions/{id}/iae/initiate")

        val sessionId = req.requirePathParam("id").getOrElse { return Err(it) }

        val iaeReq =
            try {
                holderJson.decodeFromString<SessionInitiateIaeRequest>(request.body ?: "{}")
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed IAE initiate request: ${expected.message}"))
            }

        val session =
            sessionStore.get(sessionId).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Session $sessionId not found"))

        val authorizationDetails = buildAuthorizationDetailsFromConfigIds(session.credentialConfigurationIds)

        return clientService
            .initiateIae(
                InitiateIaeArgs(
                    iaeEndpoint = iaeReq.iaeEndpoint,
                    clientId = iaeReq.clientId,
                    redirectUri = iaeReq.redirectUri,
                    interactionTypesSupported = iaeReq.interactionTypesSupported,
                    authorizationDetails = authorizationDetails,
                    scope = iaeReq.scope,
                ),
            ).map { result -> jsonResponse(200, holderJson.encodeToString(result)) }
    }
}
