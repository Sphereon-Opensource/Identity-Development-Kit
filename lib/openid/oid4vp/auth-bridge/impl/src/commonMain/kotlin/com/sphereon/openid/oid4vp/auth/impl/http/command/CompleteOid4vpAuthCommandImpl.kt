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

package com.sphereon.openid.oid4vp.auth.impl.http.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpJson
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.auth.bridge.Oid4vpAuthBridge
import com.sphereon.openid.oid4vp.auth.http.CompleteOid4vpAuthCommand
import com.sphereon.openid.oid4vp.auth.http.model.CompleteOid4vpAuthResponse
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthErrorCode
import com.sphereon.openid.oid4vp.auth.store.Oid4vpAuthSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Implementation of [CompleteOid4vpAuthCommand].
 *
 * POST /auth/oid4vp/sessions/{sessionId}/complete
 *
 * Exchanges a verified authentication session for user identity and claims.
 * This endpoint can only be called when the session status is VERIFIED.
 *
 * Returns:
 * - 200 OK with user identity and claims on success
 * - 404 Not Found if session does not exist
 * - 409 Conflict if session is not in VERIFIED state or already completed
 * - 500 Internal Server Error for unexpected errors
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CompleteOid4vpAuthCommand>())
class CompleteOid4vpAuthCommandImpl(
    execution: SessionExecution,
    private val authBridge: Oid4vpAuthBridge,
    private val sessionStore: Oid4vpAuthSessionStore,
) : HttpEndpointCommandAdapter(
        id = CompleteOid4vpAuthCommand.COMMAND_ID,
        execution = execution,
        endpoint = CompleteOid4vpAuthCommand.ENDPOINT,
    ),
    CompleteOid4vpAuthCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args).withExtractedParams(endpoint.pathPattern)

        // Extract sessionId from path parameter
        val sessionId = request.requirePathParam("sessionId").getOrElse { return Err(it) }

        // Complete authentication via bridge
        val authResult =
            authBridge.completeAuthentication(sessionId).getOrElse { error ->
                // IDV_REQUIRED is a flow-control signal, not a server-side failure: the
                // session needs identity verification before we can hand back claims.
                // Surface it as HTTP 202 with the IDV context the frontend needs to
                // route into the IDV flow, instead of letting the default IdkError
                // mapping turn it into a 5xx (which masks the actual flow state).
                if (error.code == Oid4vpAuthErrorCode.IDV_REQUIRED.name) {
                    return Ok(idvRequiredResponse(sessionId, error))
                }
                return Err(error)
            }

        val response = CompleteOid4vpAuthResponse.from(authResult)

        return Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers =
                    mapOf(
                        "Content-Type" to "application/json",
                        "Cache-Control" to "no-store",
                    ),
                body =
                    HttpJson.restApi.encodeToString(
                        CompleteOid4vpAuthResponse.serializer(),
                        response,
                    ),
            ),
        )
    }

    /**
     * Build a 202 response with the IDV context. The session was just transitioned to
     * `IDV_REQUIRED` by the auth bridge, so the relevant message/reason live on the
     * persisted session — pull them out as best-effort (the response stays usable
     * with just `status` + `sessionId` + `message` if the lookup fails).
     */
    private suspend fun idvRequiredResponse(
        sessionId: String,
        error: IdkError,
    ): GenericHttpResponse {
        val session = sessionStore.get(sessionId).getOrNull()
        val message = session?.idvMessage ?: error.message.defaultMessage
        val reason = session?.idvRequirementReason?.name
        val body =
            buildJsonObject {
                put("status", JsonPrimitive("IDV_REQUIRED"))
                put("sessionId", JsonPrimitive(sessionId))
                put("message", JsonPrimitive(message))
                if (reason != null) put("reason", JsonPrimitive(reason))
            }
        return GenericHttpResponse(
            statusCode = 202,
            headers =
                mapOf(
                    "Content-Type" to "application/json",
                    "Cache-Control" to "no-store",
                ),
            body = body.toString(),
        )
    }
}
