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

package com.sphereon.openid.oid4vp.verifier.impl.http.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestUriHandler
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Command interface for fetching request objects by request_uri (POST).
 *
 * Used when `request_uri_method=post` is specified, allowing the wallet to send
 * `wallet_metadata` and `wallet_nonce` to the verifier before it returns the
 * signed JAR.
 *
 * Per OID4VP 1.0 final §5.10.1 (verbatim, line 627-628):
 *
 *   "The request MUST use the HTTP POST method with the `https` scheme, and the
 *    content type `application/x-www-form-urlencoded` and the `Accept` header
 *    set to `application/oauth-authz-req+jwt`."
 *
 * Hence `consumes = ApplicationFormUrlEncoded`. The response Content-Type is
 * the same JAR media type used by the GET path.
 */
interface PostRequestObjectEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vp.requesturi.post"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/request-uri/{correlationId}",
                consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                produces = setOf(MediaType.Custom("application/oauth-authz-req+jwt")),
                operationId = "postRequestObjectByRequestUri",
                handlerCommandId = COMMAND_ID,
                tags = setOf("oid4vp", "request-uri"),
                summary = "Fetch OID4VP request object by request_uri with wallet metadata",
            )
    }
}

/**
 * Implementation of [PostRequestObjectEndpointCommand].
 *
 * POST /request-uri/{correlationId}
 *
 * Accepts wallet_metadata and wallet_nonce in the request body and returns
 * a signed JAR (JWT Authorization Request) for the given correlation ID.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(PostRequestObjectEndpointCommand.COMMAND_ID)
class PostRequestObjectEndpointCommandImpl(
    execution: SessionExecution,
    private val requestUriHandler: RequestUriHandler,
) : HttpEndpointCommandAdapter(
        id = PostRequestObjectEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = PostRequestObjectEndpointCommand.ENDPOINT,
    ),
    PostRequestObjectEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams("/request-uri/{correlationId}")
        val correlationId = req.requirePathParam("correlationId").getOrElse { return Err(it) }

        val params = parseFormBody(request.body)
        val walletMetadata = params["wallet_metadata"]
        val walletNonce = params["wallet_nonce"]

        // Build full path for handler (it expects the full path like /oid4vp/request-uri/{id})
        val fullPath = "/oid4vp/request-uri/$correlationId"

        return requestUriHandler
            .handlePost(
                requestUriPath = fullPath,
                walletMetadata = walletMetadata,
                walletNonce = walletNonce,
            ).map { result ->
                GenericHttpResponse(
                    statusCode = 200,
                    headers =
                        mapOf(
                            "Content-Type" to result.contentType,
                            "Cache-Control" to "no-store",
                        ),
                    body = result.signedJar,
                )
            }
    }

    /**
     * Parse application/x-www-form-urlencoded body into a map. Per OID4VP §5.10.1 the wallet
     * MUST send the request with this content type, so a body that fails to parse is treated
     * as empty (not as a JSON shape).
     */
    private fun parseFormBody(body: String?): Map<String, String> {
        if (body.isNullOrBlank()) return emptyMap()
        return body
            .split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    urlDecode(parts[0]) to urlDecode(parts[1])
                } else {
                    null
                }
            }.toMap()
    }

    private fun urlDecode(value: String): String =
        buildString {
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
