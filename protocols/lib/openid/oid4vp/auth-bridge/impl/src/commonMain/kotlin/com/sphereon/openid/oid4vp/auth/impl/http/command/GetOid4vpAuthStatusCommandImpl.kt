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
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.auth.bridge.Oid4vpAuthBridge
import com.sphereon.openid.oid4vp.auth.http.GetOid4vpAuthStatusCommand
import com.sphereon.openid.oid4vp.auth.http.model.Oid4vpAuthStatusResponse
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of [GetOid4vpAuthStatusCommand].
 *
 * GET /auth/oid4vp/sessions/{sessionId}/status
 *
 * Returns the current status of an OID4VP authentication session.
 * This endpoint is used by clients to poll for verification completion.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(GetOid4vpAuthStatusCommand.COMMAND_ID)
class GetOid4vpAuthStatusCommandImpl(
    execution: SessionExecution,
    private val authBridge: Oid4vpAuthBridge,
) : HttpEndpointCommandAdapter(
        id = GetOid4vpAuthStatusCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetOid4vpAuthStatusCommand.ENDPOINT,
    ),
    GetOid4vpAuthStatusCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args).withExtractedParams(endpoint.pathPattern)

        // Extract sessionId from path parameter
        val sessionId = request.requirePathParam("sessionId").getOrElse { return Err(it) }

        // Get session status from bridge
        val response =
            authBridge.getSessionStatus(sessionId).getOrElse { error ->
                return Err(error)
            }

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
                        Oid4vpAuthStatusResponse.serializer(),
                        response,
                    ),
            ),
        )
    }
}
