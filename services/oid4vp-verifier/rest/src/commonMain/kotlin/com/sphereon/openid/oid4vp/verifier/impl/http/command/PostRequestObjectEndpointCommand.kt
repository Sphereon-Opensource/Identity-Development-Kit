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
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Command interface for fetching request objects by request_uri (POST).
 *
 * Used when `request_uri_method=post` is specified, allowing the wallet
 * to send wallet_metadata and wallet_nonce in the request body.
 */
interface PostRequestObjectEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vp.requesturi.post"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/request-uri/{correlationId}",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.Custom("application/oauth-authz-req+jwt"), MediaType.ApplicationJson),
                operationId = "postRequestObjectByRequestUri",
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
@ContributesBinding(SessionScope::class, binding = binding<PostRequestObjectEndpointCommand>())
class PostRequestObjectEndpointCommandImpl(
    execution: SessionExecution,
    private val requestUriHandler: RequestUriHandler,
) : HttpEndpointCommandAdapter(
        id = PostRequestObjectEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = PostRequestObjectEndpointCommand.ENDPOINT,
    ),
    PostRequestObjectEndpointCommand {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams("/request-uri/{correlationId}")
        val correlationId = req.requirePathParam("correlationId").getOrElse { return Err(it) }

        val (walletMetadata, walletNonce) = parseRequestUriPostBody(request.body)

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
     * Parse request_uri POST body.
     *
     * Both snake_case and camelCase field names are accepted:
     * - `wallet_metadata` / `walletMetadata`
     * - `wallet_nonce` / `walletNonce`
     */
    private fun parseRequestUriPostBody(body: String?): Pair<String?, String?> {
        if (body.isNullOrBlank()) return null to null
        val element =
            try {
                json.parseToJsonElement(body)
            } catch (_: Exception) {
                return null to null
            }
        val obj = (element as? JsonObject) ?: return null to null

        fun JsonObject.optString(vararg keys: String): String? {
            for (k in keys) {
                val v: JsonElement = this[k] ?: continue
                val p = v as? JsonPrimitive ?: continue
                if (p.isString) return p.content
            }
            return null
        }

        val walletMetadata = obj.optString("wallet_metadata", "walletMetadata")
        val walletNonce = obj.optString("wallet_nonce", "walletNonce")
        return walletMetadata to walletNonce
    }
}
