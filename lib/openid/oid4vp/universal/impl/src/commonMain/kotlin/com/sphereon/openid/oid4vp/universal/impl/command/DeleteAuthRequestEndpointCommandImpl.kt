/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.universal.impl.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.errorResponse
import com.sphereon.core.events.SessionEventService
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.universal.DeleteAuthRequestEndpointCommand
import com.sphereon.openid.oid4vp.universal.UniversalOid4vpEventTypes
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of [DeleteAuthRequestEndpointCommand].
 *
 * DELETE /oid4vp/backend/auth/requests/{correlationId}
 *
 * Deletes an authorization session, cleaning up all associated state.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeleteAuthRequestEndpointCommand>())
class DeleteAuthRequestEndpointCommandImpl(
    execution: SessionExecution,
    private val authorizationSessionStore: AuthorizationSessionStore,
    private val sessionEventService: SessionEventService
) : HttpEndpointCommandAdapter(
    id = DeleteAuthRequestEndpointCommand.COMMAND_ID,
    execution = execution,
    endpoint = DeleteAuthRequestEndpointCommand.ENDPOINT
), DeleteAuthRequestEndpointCommand {

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        // 1. Extract correlationId from path
        val req = request.withExtractedParams(endpoint.pathPattern)
        val correlationId = req.pathParams["correlationId"]
            ?: return Ok(errorResponse(400, "Missing path parameter: correlationId"))

        // 2. Check if session exists
        val session = authorizationSessionStore.getByCorrelationId(correlationId).getOrNull()
        if (session == null) {
            return Ok(errorResponse(404, "Authorization request not found: $correlationId"))
        }

        // 3. Delete the session
        authorizationSessionStore.delete(correlationId).getOrElse { error ->
            return Ok(errorResponse(500, "Failed to delete session: ${error.message.defaultMessage}"))
        }

        // 4. Emit SESSION_DELETED event
        emitSessionDeletedEvent(correlationId)

        // 5. Return 204 No Content
        return Ok(
            GenericHttpResponse(
                statusCode = 204,
                headers = mapOf(
                    "Cache-Control" to "no-store"
                ),
                body = null
            )
        )
    }

    private suspend fun emitSessionDeletedEvent(correlationId: String) {
        try {
            sessionEventService.emit(
                sessionEventService.eventBuilder()
                    .type(UniversalOid4vpEventTypes.SESSION_DELETED)
                    .origin(DeleteAuthRequestEndpointCommand.COMMAND_ID)
                    .payload(buildJsonObject {
                        put("correlationId", correlationId)
                    })
                    .build()
            )
        } catch (e: Exception) {
            // Best effort
            log.debug("Failed to emit SESSION_DELETED event: ${e.message}")
        }
    }
}
