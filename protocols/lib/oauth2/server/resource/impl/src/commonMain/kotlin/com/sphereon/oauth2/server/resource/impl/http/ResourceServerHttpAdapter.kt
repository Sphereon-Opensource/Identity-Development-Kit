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

package com.sphereon.oauth2.server.resource.impl.http

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.util.RequestUtils
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.resource.model.ResourceRequest
import com.sphereon.oauth2.server.resource.model.VerifiedResourceRequest
import com.sphereon.oauth2.server.resource.service.ResourceServerService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Universal HTTP Adapter for OAuth2 Resource Server.
 *
 * This adapter provides a framework-agnostic HTTP API layer that can be used with:
 * - Spring Boot
 * - Ktor Server
 * - AWS Lambda
 * - Azure Functions
 * - Google Cloud Functions
 *
 * **SINGLE SOURCE OF TRUTH** for all OAuth2 Resource Server endpoint routing.
 *
 * Based on RFCs:
 * - RFC 6750: Bearer Token Usage
 * - RFC 9068: JWT Profile for OAuth 2.0 Access Tokens
 * - RFC 9449: DPoP
 *
 * This adapter validates access tokens in incoming requests and returns either:
 * - 200 OK with verified request metadata (for successful validation)
 * - 401 Unauthorized with WWW-Authenticate header (for invalid/missing tokens)
 * - 403 Forbidden (for insufficient scope)
 */
@Inject
@SingleIn(SessionScope::class)
class ResourceServerHttpAdapter(
    private val resourceServerService: ResourceServerService,
) {
    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

    /**
     * Handle HTTP request and validate access token.
     *
     * This is the main entry point for all resource server requests that require
     * access token validation.
     *
     * @param request The incoming HTTP request
     * @param requiredScope Optional scope required to access this resource
     * @param requiredAudience Optional audience required (defaults to resource server identifier from config)
     * @return GenericHttpResponse with either verified request data or error
     */
    suspend fun validateRequest(
        request: GenericHttpRequest,
        requiredScope: String? = null,
        requiredAudience: String? = null,
    ): GenericHttpResponse {
        // Convert GenericHttpRequest to ResourceRequest
        val resourceRequest =
            ResourceRequest(
                method = request.method,
                url = buildFullUrl(request),
                headers = request.headers,
            )

        // Validate the request
        val result =
            resourceServerService.validateRequest(
                request = resourceRequest,
                requiredScope = requiredScope,
                requiredAudience = requiredAudience,
            )

        return result.fold(
            success = { verifiedRequest: VerifiedResourceRequest ->
                // Success: Return 200 with verified request metadata
                val responseBody = json.encodeToString(verifiedRequest)
                GenericHttpResponse(
                    statusCode = 200,
                    headers =
                        mapOf(
                            "Content-Type" to "application/json",
                        ),
                    body = responseBody,
                )
            },
            failure = { error: IdkError ->
                mapErrorToResponse(error)
            },
        )
    }

    /**
     * Build full URL from request.
     */
    private fun buildFullUrl(request: GenericHttpRequest): String = RequestUtils.buildFullUrl(request.headers, request.path)

    /**
     * Map IdkError to HTTP response with WWW-Authenticate header.
     * Uses error codes from ResourceServerError (preserved through IdkError.fromDTO).
     */
    private fun mapErrorToResponse(error: IdkError): GenericHttpResponse {
        val errorDescription = error.message.defaultMessage
        return when (error.code) {
            "insufficient_scope" -> {
                forbiddenResponse(
                    wwwAuthenticate = "Bearer error=\"insufficient_scope\"",
                    error = "insufficient_scope",
                    errorDescription = errorDescription,
                )
            }

            "invalid_dpop_proof", "dpop_binding_mismatch" -> {
                unauthorizedResponse(
                    wwwAuthenticate = "DPoP error=\"invalid_dpop_proof\"",
                    error = "invalid_dpop_proof",
                    errorDescription = errorDescription,
                )
            }

            else -> {
                unauthorizedResponse(
                    wwwAuthenticate = "Bearer error=\"invalid_token\"",
                    error = "invalid_token",
                    errorDescription = errorDescription,
                )
            }
        }
    }

    /**
     * Create a 401 Unauthorized response with WWW-Authenticate header.
     * RFC 6750 Section 3: Error Response
     */
    private fun unauthorizedResponse(
        wwwAuthenticate: String,
        error: String,
        errorDescription: String,
    ): GenericHttpResponse {
        val errorBody =
            mapOf(
                "error" to error,
                "error_description" to errorDescription,
            )

        return GenericHttpResponse(
            statusCode = 401,
            headers =
                mapOf(
                    "WWW-Authenticate" to wwwAuthenticate,
                    "Content-Type" to "application/json",
                    "Cache-Control" to "no-store",
                ),
            body = json.encodeToString(errorBody),
        )
    }

    /**
     * Create a 403 Forbidden response for insufficient scope.
     * RFC 6750 Section 3.1: Error Response
     */
    private fun forbiddenResponse(
        wwwAuthenticate: String,
        error: String,
        errorDescription: String,
    ): GenericHttpResponse {
        val errorBody =
            mapOf(
                "error" to error,
                "error_description" to errorDescription,
            )

        return GenericHttpResponse(
            statusCode = 403,
            headers =
                mapOf(
                    "WWW-Authenticate" to wwwAuthenticate,
                    "Content-Type" to "application/json",
                    "Cache-Control" to "no-store",
                ),
            body = json.encodeToString(errorBody),
        )
    }
}
