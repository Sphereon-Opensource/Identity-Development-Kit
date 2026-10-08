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
import com.sphereon.openid.oid4vp.auth.http.InitiateOid4vpIdvCommand
import com.sphereon.openid.oid4vp.auth.http.model.IdvInitiateResponse
import com.sphereon.openid.oid4vp.auth.orchestration.ReconciliationOrchestratorApi
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Implementation of [InitiateOid4vpIdvCommand].
 *
 * POST /auth/oid4vp/sessions/{sessionId}/idv/initiate
 *
 * Thin wrapper that parses the HTTP request and delegates to [ReconciliationOrchestratorApi].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(InitiateOid4vpIdvCommand.COMMAND_ID)
class InitiateOid4vpIdvCommandImpl(
    execution: SessionExecution,
    private val orchestrator: ReconciliationOrchestratorApi,
) : HttpEndpointCommandAdapter(
        id = InitiateOid4vpIdvCommand.COMMAND_ID,
        execution = execution,
        endpoint = InitiateOid4vpIdvCommand.ENDPOINT,
    ),
    InitiateOid4vpIdvCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args).withExtractedParams(endpoint.pathPattern)

        val sessionId = request.requirePathParam("sessionId").getOrElse { return Err(it) }

        // Parse optional redirectUri from request body
        val bodyRedirectUri =
            try {
                val body = request.body
                if (!body.isNullOrBlank()) {
                    val json = HttpJson.restApi.decodeFromString<JsonObject>(body)
                    (json["redirectUri"] as? JsonPrimitive)?.contentOrNull
                } else {
                    null
                }
            } catch (expected: Exception) {
                log.debug("Failed to parse IDV initiate request body: ${expected.message}")
                null
            }

        val baseUrl = request.pathParams["_baseUrl"]
        val redirectUri = bodyRedirectUri ?: ""

        // Delegate to orchestrator
        val result =
            orchestrator
                .initiateReconciliation(
                    oid4vpSessionId = sessionId,
                    redirectUri = redirectUri,
                    baseUrl = baseUrl,
                ).getOrElse { error ->
                    return Err(error)
                }

        // Format response
        val response =
            IdvInitiateResponse(
                sessionId = result.sessionId,
                redirectUrl = result.authorizationUrl,
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
                body = HttpJson.restApi.encodeToString(IdvInitiateResponse.serializer(), response),
            ),
        )
    }
}
