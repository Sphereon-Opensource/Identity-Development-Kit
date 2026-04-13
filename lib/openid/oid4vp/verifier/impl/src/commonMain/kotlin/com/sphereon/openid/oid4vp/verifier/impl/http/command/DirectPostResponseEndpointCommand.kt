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

package com.sphereon.openid.oid4vp.verifier.impl.http.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.errorResponse
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.verifier.HandleDirectPostResponseArgs
import com.sphereon.openid.oid4vp.verifier.HandleDirectPostResponseCommand
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

/**
 * Command interface for the OID4VP direct_post response endpoint.
 *
 * The wallet POSTs the VP token and presentation_submission to this endpoint
 * after the user has approved the presentation request.
 *
 * Per OpenID4VP 1.0 Section 8.4 - Response Mode: direct_post
 */
interface DirectPostResponseEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vp.directpost.response"

        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.POST,
            pathPattern = "/auth/response",
            consumes = setOf(MediaType.ApplicationFormUrlEncoded),
            produces = setOf(MediaType.ApplicationJson),
            operationId = "handleDirectPostResponse",
            tags = setOf("oid4vp", "direct-post"),
            summary = "Handle OID4VP direct_post authorization response from wallet"
        )
    }
}

/**
 * Implementation of [DirectPostResponseEndpointCommand].
 *
 * POST /oid4vp/auth/response
 *
 * Receives the wallet's authorization response (vp_token, state, presentation_submission)
 * via application/x-www-form-urlencoded POST and delegates to [HandleDirectPostResponseCommand].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DirectPostResponseEndpointCommand>())
class DirectPostResponseEndpointCommandImpl(
    execution: SessionExecution,
    private val handleDirectPostCommand: HandleDirectPostResponseCommand,
    private val authorizationSessionStore: AuthorizationSessionStore
) : HttpEndpointCommandAdapter(
    id = DirectPostResponseEndpointCommand.COMMAND_ID,
    execution = execution,
    endpoint = DirectPostResponseEndpointCommand.ENDPOINT
), DirectPostResponseEndpointCommand {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        // Parse form-urlencoded body into response params
        val responseParams = parseFormBody(request.body)
        if (responseParams.isEmpty()) {
            return Ok(errorResponse(400, "Missing or empty request body"))
        }

        // Per OID4VP spec, the wallet echoes back the state parameter from the
        // authorization request URL. This is used to correlate the response to the session.
        val state = responseParams["state"]

        if (state.isNullOrBlank()) {
            return Ok(errorResponse(400, "Missing state parameter"))
        }
        val correlationId = state

        // Look up the authorization session to get the original request
        val session = authorizationSessionStore.getByCorrelationId(correlationId).getOrNull()
            ?: return Ok(errorResponse(404, "Authorization session not found: $correlationId"))

        // Resolve the redirect_uri for the direct_post response.
        // Per OID4VP Section 7.2, redirect_uri is OPTIONAL in the response.
        // If configured on the session's authorization request, the wallet navigates there.
        // Otherwise, omit it — the wallet stays on its current screen.
        val sessionRedirectUri = session.authorizationRequest.redirectUri ?: ""

        // Build args for the direct_post handler
        val directPostArgs = HandleDirectPostResponseArgs(
            responseParams = responseParams,
            originalRequest = session.authorizationRequest,
            dcqlQuery = session.dcqlQuery,
            redirectUri = sessionRedirectUri
        )

        // Delegate to the service command
        return handleDirectPostCommand.execute(directPostArgs).fold(
            success = { result ->
                // Per OID4VP 1.0: response is HTTP 200 with optional redirect_uri
                // containing response_code as fragment. When no redirect is configured,
                // return empty JSON — the wallet treats HTTP 200 as success.
                val includeRedirect = sessionRedirectUri.isNotBlank()
                Ok(
                    GenericHttpResponse(
                        statusCode = 200,
                        headers = mapOf(
                            "Content-Type" to "application/json",
                            "Cache-Control" to "no-store"
                        ),
                        body = json.encodeToString(
                            kotlinx.serialization.serializer(),
                            buildJsonObject {
                                if (includeRedirect) {
                                    put("redirect_uri", result.redirectUri)
                                }
                            }
                        )
                    )
                )
            },
            failure = { error ->
                Ok(errorResponse(
                    when (error.code) {
                        "NOT_FOUND_ERROR" -> 404
                        "ILLEGAL_ARGUMENT_ERROR" -> 400
                        else -> 500
                    },
                    error.message.defaultMessage
                ))
            }
        )
    }

    /**
     * Parse application/x-www-form-urlencoded body into a map.
     */
    private fun parseFormBody(body: String?): Map<String, String> {
        if (body.isNullOrBlank()) return emptyMap()
        return body.split("&").mapNotNull { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                urlDecode(parts[0]) to urlDecode(parts[1])
            } else null
        }.toMap()
    }

    private fun urlDecode(value: String): String {
        return buildString {
            var i = 0
            while (i < value.length) {
                when {
                    value[i] == '%' && i + 2 < value.length -> {
                        val hex = value.substring(i + 1, i + 3)
                        append(hex.toInt(16).toChar())
                        i += 3
                    }
                    value[i] == '+' -> {
                        append(' ')
                        i++
                    }
                    else -> {
                        append(value[i])
                        i++
                    }
                }
            }
        }
    }
}
