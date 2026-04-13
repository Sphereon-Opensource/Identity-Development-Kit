/*
 * © 2025 Sphereon International B.V.
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
 *
 */

package com.sphereon.openid.oid4vp.auth.impl.http.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.errorResponse
import com.sphereon.openid.oid4vp.auth.error.Oid4vpAuthErrors

import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.auth.bridge.CreateSessionArgs
import com.sphereon.openid.oid4vp.auth.bridge.Oid4vpAuthBridge
import com.sphereon.openid.oid4vp.auth.http.CreateOid4vpAuthSessionCommand
import com.sphereon.openid.oid4vp.auth.http.model.CreateOid4vpAuthSessionRequest
import com.sphereon.openid.oid4vp.auth.http.model.CreateOid4vpAuthSessionResponse
import com.sphereon.core.api.http.HttpJson
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of [CreateOid4vpAuthSessionCommand].
 *
 * POST /auth/oid4vp/sessions
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateOid4vpAuthSessionCommand>())
class CreateOid4vpAuthSessionCommandImpl(
    execution: SessionExecution,
    private val authBridgeService: Oid4vpAuthBridge
) : HttpEndpointCommandAdapter(
    id = CreateOid4vpAuthSessionCommand.COMMAND_ID,
    execution = execution,
    endpoint = CreateOid4vpAuthSessionCommand.ENDPOINT
), CreateOid4vpAuthSessionCommand {

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        // 1. Parse request body (optional - can create with defaults, but malformed body is rejected)
        val input = parseInput(request.body).getOrElse { error ->
            return Ok(errorResponse(400, error.message.defaultMessage))
        }

        // 2. Create session
        val createArgs = CreateSessionArgs(
            queryId = input?.queryId,
            oauthSessionId = input?.oauthSessionId,
            returnUrl = input?.returnUrl,
            requestedProjection = input?.requestedProjection,
            ttlSeconds = input?.ttlSeconds,
            forceReconciliation = input?.forceReconciliation ?: false
        )

        val result = authBridgeService.createSession(createArgs).getOrElse { error ->
            return Ok(errorResponse(500, error.message.defaultMessage))
        }

        // 3. Build response
        val response = CreateOid4vpAuthSessionResponse.from(result)

        return Ok(
            GenericHttpResponse(
                statusCode = 201,
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Cache-Control" to "no-store"
                ),
                body = HttpJson.restApi.encodeToString(CreateOid4vpAuthSessionResponse.serializer(), response)
            )
        )
    }

    private fun parseInput(body: String?): IdkResult<CreateOid4vpAuthSessionRequest?, IdkError> {
        if (body.isNullOrBlank()) return Ok(null)
        return try {
            Ok(HttpJson.restApi.decodeFromString(CreateOid4vpAuthSessionRequest.serializer(), body))
        } catch (e: Exception) {
            Err(Oid4vpAuthErrors.invalidRequestBody(e.message ?: "Malformed JSON", e))
        }
    }
}
