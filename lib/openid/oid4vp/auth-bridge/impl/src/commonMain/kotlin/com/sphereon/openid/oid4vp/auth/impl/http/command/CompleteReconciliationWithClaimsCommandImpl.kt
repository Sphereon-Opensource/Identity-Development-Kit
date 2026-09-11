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
import com.sphereon.core.api.http.command.requireBody
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.auth.http.CompleteReconciliationWithClaimsCommand
import com.sphereon.openid.oid4vp.auth.orchestration.ReconciliationOrchestratorApi
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Implementation of [CompleteReconciliationWithClaimsCommand].
 *
 * POST /auth/oid4vp/sessions/{sessionId}/reconciliation/complete
 *
 * Accepts pre-extracted OIDC claims from the STS and delegates to the
 * [ReconciliationOrchestratorApi] for identity matching and binding creation.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(CompleteReconciliationWithClaimsCommand.COMMAND_ID)
class CompleteReconciliationWithClaimsCommandImpl(
    execution: SessionExecution,
    private val orchestrator: ReconciliationOrchestratorApi,
) : HttpEndpointCommandAdapter(
        id = CompleteReconciliationWithClaimsCommand.COMMAND_ID,
        execution = execution,
        endpoint = CompleteReconciliationWithClaimsCommand.ENDPOINT,
    ),
    CompleteReconciliationWithClaimsCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args).withExtractedParams(endpoint.pathPattern)

        val sessionId = request.requirePathParam("sessionId").getOrElse { return Err(it) }

        // Parse request body
        val raw = request.requireBody().getOrElse { return Err(it) }
        val body =
            try {
                HttpJson.restApi.decodeFromString<JsonObject>(raw)
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid JSON body: ${expected.message}", throwable = expected))
            }

        val claimsJson =
            body["claims"]?.jsonObject
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing 'claims' field in request body"))
        val issuer =
            (body["issuer"] as? JsonPrimitive)?.contentOrNull
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing 'issuer' field in request body"))
        val providerId =
            (body["providerId"] as? JsonPrimitive)?.contentOrNull
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing 'providerId' field in request body"))

        // Delegate to orchestrator — pass claims as JsonElement to preserve arrays/objects
        val result =
            orchestrator
                .handleCallbackWithClaims(
                    oid4vpSessionId = sessionId,
                    claims = claimsJson,
                    issuer = issuer,
                    providerId = providerId,
                ).getOrElse { error ->
                    return Err(error)
                }

        // Return result
        val responseBody =
            JsonObject(
                mapOf(
                    "status" to JsonPrimitive("completed"),
                    "oid4vpSessionId" to JsonPrimitive(result.oid4vpSessionId),
                    "matchId" to JsonPrimitive(result.matchId),
                    "resolvedUserId" to JsonPrimitive(result.resolvedUserId),
                ),
            )

        return Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers =
                    mapOf(
                        "Content-Type" to "application/json",
                        "Cache-Control" to "no-store",
                    ),
                body = HttpJson.restApi.encodeToString(JsonObject.serializer(), responseBody),
            ),
        )
    }
}
