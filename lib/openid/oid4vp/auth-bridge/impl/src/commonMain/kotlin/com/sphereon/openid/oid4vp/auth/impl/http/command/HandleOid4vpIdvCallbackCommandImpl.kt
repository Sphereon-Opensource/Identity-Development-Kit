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

package com.sphereon.openid.oid4vp.auth.impl.http.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.errorResponse
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.auth.http.HandleOid4vpIdvCallbackCommand
import com.sphereon.openid.oid4vp.auth.orchestration.ReconciliationOrchestratorApi
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of [HandleOid4vpIdvCallbackCommand].
 *
 * GET /auth/oid4vp/idv/callback?code=...&state=...
 *
 * Thin wrapper that extracts query parameters and delegates to [ReconciliationOrchestratorApi].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleOid4vpIdvCallbackCommand>())
class HandleOid4vpIdvCallbackCommandImpl(
    execution: SessionExecution,
    private val orchestrator: ReconciliationOrchestratorApi
) : HttpEndpointCommandAdapter(
    id = HandleOid4vpIdvCallbackCommand.COMMAND_ID,
    execution = execution,
    endpoint = HandleOid4vpIdvCallbackCommand.ENDPOINT
), HandleOid4vpIdvCallbackCommand {

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args).withExtractedParams(endpoint.pathPattern)

        // 1. Extract code and state from query parameters
        val code = request.queryParams["code"]
            ?: return Ok(errorResponse(400, "Missing query parameter: code"))
        val state = request.queryParams["state"]
            ?: return Ok(errorResponse(400, "Missing query parameter: state"))

        // 2. Delegate to orchestrator (wallet attributes will be resolved by the orchestrator
        // from the linked OID4VP session's verified data)
        val result = orchestrator.handleCallback(
            code = code,
            state = state,
            walletAttributes = emptyMap() // Wallet attributes are extracted from the session by the orchestrator
        ).getOrElse { error ->
            return Ok(errorResponse(500, error.message.defaultMessage))
        }

        // 3. Return success with session ID and match ID
        return Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Cache-Control" to "no-store"
                ),
                body = """{"status":"COMPLETED","sessionId":"${result.oid4vpSessionId}","matchId":"${result.matchId}"}"""
            )
        )
    }
}
