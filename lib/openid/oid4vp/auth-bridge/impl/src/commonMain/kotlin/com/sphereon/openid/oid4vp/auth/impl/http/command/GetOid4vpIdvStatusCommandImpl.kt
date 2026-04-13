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
import com.sphereon.openid.oid4vp.auth.http.GetOid4vpIdvStatusCommand
import com.sphereon.openid.oid4vp.auth.http.model.IdvStatusResponse
import com.sphereon.openid.oid4vp.auth.orchestration.ReconciliationOrchestratorApi
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of [GetOid4vpIdvStatusCommand].
 *
 * GET /auth/oid4vp/sessions/{sessionId}/idv/status
 *
 * Thin wrapper that extracts the session ID and delegates to [ReconciliationOrchestratorApi].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetOid4vpIdvStatusCommand>())
class GetOid4vpIdvStatusCommandImpl(
    execution: SessionExecution,
    private val orchestrator: ReconciliationOrchestratorApi,
) : HttpEndpointCommandAdapter(
        id = GetOid4vpIdvStatusCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetOid4vpIdvStatusCommand.ENDPOINT,
    ),
    GetOid4vpIdvStatusCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args).withExtractedParams(endpoint.pathPattern)

        val sessionId = request.requirePathParam("sessionId").getOrElse { return Err(it) }

        // Delegate to orchestrator
        val result =
            orchestrator.getStatus(oid4vpSessionId = sessionId).getOrElse { error ->
                return Err(error)
            }

        // Format response
        val response =
            IdvStatusResponse(
                sessionId = result.sessionId,
                status = result.status,
                message = result.message,
                planType = result.planType,
                idvRequirementReason = result.idvRequirementReason,
            )

        return Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers =
                    mapOf(
                        "Content-Type" to "application/json",
                        "Cache-Control" to "no-store",
                    ),
                body = HttpJson.restApi.encodeToString(IdvStatusResponse.serializer(), response),
            ),
        )
    }
}
