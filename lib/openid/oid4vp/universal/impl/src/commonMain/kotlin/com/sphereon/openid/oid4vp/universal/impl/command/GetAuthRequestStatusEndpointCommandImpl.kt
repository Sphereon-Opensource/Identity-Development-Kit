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
import com.sphereon.openid.oid4vp.universal.GetAuthRequestStatusEndpointCommand
import com.sphereon.openid.oid4vp.universal.GetAuthorizationRequestStatusOutput
import com.sphereon.openid.oid4vp.universal.SessionError
import com.sphereon.openid.oid4vp.universal.UniversalOid4vpEventTypes
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of [GetAuthRequestStatusEndpointCommand].
 *
 * GET /oid4vp/backend/auth/requests/{correlationId}
 *
 * Returns the current status of an authorization session, including
 * verified credential data when verification is complete.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetAuthRequestStatusEndpointCommand>())
class GetAuthRequestStatusEndpointCommandImpl(
    execution: SessionExecution,
    private val authorizationSessionStore: AuthorizationSessionStore,
    private val sessionEventService: SessionEventService
) : HttpEndpointCommandAdapter(
    id = GetAuthRequestStatusEndpointCommand.COMMAND_ID,
    execution = execution,
    endpoint = GetAuthRequestStatusEndpointCommand.ENDPOINT
), GetAuthRequestStatusEndpointCommand {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        // 1. Extract correlationId from path
        val req = request.withExtractedParams(endpoint.pathPattern)
        val correlationId = req.pathParams["correlationId"]
            ?: return Ok(errorResponse(400, "Missing path parameter: correlationId"))

        // 2. Get session from store
        val session = authorizationSessionStore.getByCorrelationId(correlationId).getOrNull()
            ?: return Ok(errorResponse(404, "Authorization request not found: $correlationId"))

        // 3. Build verified data if session is verified
        val verifiedData = if (session.status == AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_VERIFIED) {
            buildVerifiedData(session)
        } else {
            null
        }

        // 4. Emit STATUS_POLLED event
        emitStatusPolledEvent(correlationId, session.status)

        // 5. Build response
        val sessionError = session.error?.let { error ->
            SessionError(code = error.code, message = error.message)
        }

        val output = GetAuthorizationRequestStatusOutput(
            correlationId = session.correlationId,
            queryId = session.queryId,
            status = session.status,
            lastUpdated = session.updatedAt,
            error = sessionError,
            verifiedData = verifiedData
        )

        return Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Cache-Control" to "no-store"
                ),
                body = json.encodeToString(GetAuthorizationRequestStatusOutput.serializer(), output)
            )
        )
    }

    private suspend fun emitStatusPolledEvent(
        correlationId: String,
        status: AuthorizationSessionStatus
    ) {
        try {
            sessionEventService.emit(
                sessionEventService.eventBuilder()
                    .type(UniversalOid4vpEventTypes.STATUS_POLLED)
                    .origin(GetAuthRequestStatusEndpointCommand.COMMAND_ID)
                    .payload(buildJsonObject {
                        put("correlationId", correlationId)
                        put("status", status.name)
                    })
                    .build()
            )
        } catch (e: Exception) {
            // Best effort
            log.debug("Failed to emit STATUS_POLLED event: ${e.message}")
        }
    }
}
