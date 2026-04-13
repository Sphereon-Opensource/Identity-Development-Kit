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
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

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
}
