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
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestUriHandler
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Command interface for fetching request objects by request_uri (GET).
 *
 * Wallets fetch the request object from a `request_uri` URL using GET.
 */
interface GetRequestObjectEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vp.requesturi.get"

        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.GET,
            pathPattern = "/request-uri/{correlationId}",
            produces = setOf(MediaType.Custom("application/oauth-authz-req+jwt"), MediaType.ApplicationJson),
            operationId = "getRequestObjectByRequestUri",
            tags = setOf("oid4vp", "request-uri"),
            summary = "Fetch OID4VP request object by request_uri"
        )
    }
}

/**
 * Implementation of [GetRequestObjectEndpointCommand].
 *
 * GET /request-uri/{correlationId}
 *
 * Returns the signed JAR (JWT Authorization Request) for the given correlation ID.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetRequestObjectEndpointCommand>())
class GetRequestObjectEndpointCommandImpl(
    execution: SessionExecution,
    private val requestUriHandler: RequestUriHandler
) : HttpEndpointCommandAdapter(
    id = GetRequestObjectEndpointCommand.COMMAND_ID,
    execution = execution,
    endpoint = GetRequestObjectEndpointCommand.ENDPOINT
), GetRequestObjectEndpointCommand {

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams("/request-uri/{correlationId}")
        val correlationId = req.pathParams["correlationId"]
            ?: return Ok(errorResponse(400, "Missing path parameter: correlationId"))

        // Build full path for handler (it expects the full path like /oid4vp/request-uri/{id})
        val fullPath = "/oid4vp/request-uri/$correlationId"

        return requestUriHandler.handleGet(requestUriPath = fullPath).fold(
            success = { result ->
                Ok(
                    GenericHttpResponse(
                        statusCode = 200,
                        headers = mapOf(
                            "Content-Type" to result.contentType,
                            "Cache-Control" to "no-store"
                        ),
                        body = result.signedJar
                    )
                )
            },
            failure = { error -> Ok(mapIdkError(error)) }
        )
    }

    private fun mapIdkError(e: IdkError): GenericHttpResponse {
        val statusCode = when (e.code) {
            "NOT_FOUND_ERROR" -> 404
            "ILLEGAL_ARGUMENT_ERROR" -> 400
            else -> 400
        }
        return errorResponse(statusCode, e.message.defaultMessage)
    }
}
