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

package com.sphereon.oauth2.server.authorization.impl.http.command.authorization

// Role-neutral OAuth HTTP capability. Executable graph bindings remain in the service assembly.

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.HandleIaeFollowUpArgs
import com.sphereon.oauth2.server.authorization.command.HandleIaeFollowUpCommand
import com.sphereon.oauth2.server.authorization.command.HandleIaeInitialRequestArgs
import com.sphereon.oauth2.server.authorization.command.HandleIaeInitialRequestCommand
import com.sphereon.oauth2.server.authorization.command.IaeResult
import com.sphereon.oauth2.server.authorization.command.authorization.IaeHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import com.sphereon.oauth2.server.authorization.impl.http.parseFormBody
import com.sphereon.oauth2.server.authorization.model.IaeAuthorizationCodeResponse
import com.sphereon.oauth2.server.authorization.model.IaeErrorResponse
import com.sphereon.oauth2.server.authorization.model.IaeInteractionRequiredResponse
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * HTTP shell over the IAE (OID4VCI 1.1 §6) commands. Branches on the presence of `auth_session`
 * in the form body to dispatch to either [HandleIaeInitialRequestCommand] or
 * [HandleIaeFollowUpCommand]; this is HTTP-layer parsing (which form params are present), not
 * business logic.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(IaeHttpEndpointCommand.COMMAND_ID)
class IaeHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val handleIaeInitialRequestCommand: HandleIaeInitialRequestCommand,
    private val handleIaeFollowUpCommand: HandleIaeFollowUpCommand,
) : HttpEndpointCommandAdapter(
        id = IaeHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = IaeHttpEndpointCommand.ENDPOINT,
    ),
    IaeHttpEndpointCommand {
    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val requestBody =
            parseFormBody(request.body)
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing or invalid request body", json))

        val params = requestBody.mapValues { (_, values) -> values.first() }

        val authSession = params["auth_session"]
        return if (authSession != null) {
            handleFollowUp(authSession, params)
        } else {
            handleInitial(params)
        }
    }

    private suspend fun handleFollowUp(
        authSession: String,
        params: Map<String, String>,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val openid4vpResponseStr = params["openid4vp_response"]
        val openid4vpResponse: JsonObject? =
            if (openid4vpResponseStr != null) {
                try {
                    json.parseToJsonElement(openid4vpResponseStr).jsonObject
                } catch (expected: Exception) {
                    return Ok(oauth2ErrorResponse(400, "invalid_request", "Invalid openid4vp_response JSON: ${expected.message}", json))
                }
            } else {
                null
            }

        val followUpResult =
            handleIaeFollowUpCommand.execute(
                HandleIaeFollowUpArgs(
                    authSession = authSession,
                    openid4vpResponse = openid4vpResponse,
                    codeVerifier = params["code_verifier"],
                ),
            )

        return if (followUpResult.isOk) {
            Ok(iaeResultToResponse(followUpResult.value))
        } else {
            Ok(oauth2ErrorResponse(400, "invalid_request", followUpResult.error.message.defaultMessage, json))
        }
    }

    private suspend fun handleInitial(params: Map<String, String>): IdkResult<GenericHttpResponse, IdkError> {
        val clientId =
            params["client_id"]
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing required parameter: client_id", json))
        val redirectUri =
            params["redirect_uri"]
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing required parameter: redirect_uri", json))

        val interactionTypesStr = params["interaction_types_supported"] ?: ""
        val interactionTypes =
            if (interactionTypesStr.isBlank()) {
                emptyList()
            } else {
                interactionTypesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }

        val authorizationDetailsStr = params["authorization_details"]
        val authorizationDetails =
            if (authorizationDetailsStr != null) {
                try {
                    val element = json.parseToJsonElement(authorizationDetailsStr)
                    (element as? JsonArray)?.toList()
                } catch (expected: Exception) {
                    return Ok(oauth2ErrorResponse(400, "invalid_request", "Invalid authorization_details JSON: ${expected.message}", json))
                }
            } else {
                null
            }

        val initialResult =
            handleIaeInitialRequestCommand.execute(
                HandleIaeInitialRequestArgs(
                    clientId = clientId,
                    responseType = params["response_type"] ?: "code",
                    redirectUri = redirectUri,
                    interactionTypesSupported = interactionTypes,
                    authorizationDetails = authorizationDetails,
                    scope = params["scope"],
                    codeChallenge = params["code_challenge"],
                    codeChallengeMethod = params["code_challenge_method"],
                    request = params["request"],
                ),
            )

        return if (initialResult.isOk) {
            Ok(iaeResultToResponse(initialResult.value))
        } else {
            Ok(oauth2ErrorResponse(400, "invalid_request", initialResult.error.message.defaultMessage, json))
        }
    }

    private fun iaeResultToResponse(result: IaeResult): GenericHttpResponse =
        when (result) {
            is IaeResult.InteractionRequired -> {
                GenericHttpResponse(
                    statusCode = 200,
                    headers = mapOf("Content-Type" to "application/json", "Cache-Control" to "no-store"),
                    body = json.encodeToString(IaeInteractionRequiredResponse.serializer(), result.response),
                )
            }

            is IaeResult.AuthorizationCode -> {
                GenericHttpResponse(
                    statusCode = 200,
                    headers = mapOf("Content-Type" to "application/json", "Cache-Control" to "no-store"),
                    body = json.encodeToString(IaeAuthorizationCodeResponse.serializer(), result.response),
                )
            }

            is IaeResult.Error -> {
                GenericHttpResponse(
                    statusCode = 400,
                    headers = mapOf("Content-Type" to "application/json", "Cache-Control" to "no-store"),
                    body = json.encodeToString(IaeErrorResponse.serializer(), result.response),
                )
            }
        }
}
