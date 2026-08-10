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

package com.sphereon.core.api.binary

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.compat.JsExportCompat
import kotlin.jvm.JvmStatic

/**
 * Transport-neutral binary request.
 *
 * This class lives in IDK because:
 * - Used by IDK protocol servers (OID4VP, OAuth2, KMS REST)
 * - Prevents type drift between EDK and VDX
 * - Core abstraction, not enterprise-specific
 *
 * @property commandId Unique identifier for routing and transport binding
 * @property contentType Content-Type header value
 * @property accept Accept header values (ordered by preference)
 * @property headers All request headers
 * @property pathParams Path parameters extracted from URL
 * @property queryParams Query parameters
 * @property metadata Additional metadata for cross-cutting concerns
 * @property body The request body
 */
@JsExportCompat
data class BinaryRequest(
    val commandId: String,
    val contentType: String? = null,
    val accept: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    /** Tenant established by validated authentication, never from request data. */
    val resolvedTenantId: String? = null,
    /**
     * Raw multi-value header view, preserving each `name → List<value>` arrival shape from the
     * source HTTP request. Mirrors [GenericHttpRequest.multiValueHeaders] across the binary
     * transport boundary so consumers that need single-occurrence semantics (e.g. RFC 9449 §4.1:
     * a single `DPoP` header is REQUIRED) can detect duplicates without re-parsing the joined
     * scalar [headers]. Empty by default — only HTTP-origin requests populate it.
     */
    val multiValueHeaders: Map<String, List<String>> = emptyMap(),
    val pathParams: Map<String, String> = emptyMap(),
    val queryParams: Map<String, String> = emptyMap(),
    val metadata: Map<String, String> = emptyMap(),
    val body: StreamingBody = StreamingBody.Empty,
) {
    /**
     * Gets a header value (case-insensitive).
     */
    fun header(name: String): String? = headers[name] ?: headers[name.lowercase()] ?: headers[name.uppercase()]

    /**
     * Gets a path parameter by name.
     */
    fun pathParam(name: String): String? = pathParams[name]

    /**
     * Gets a query parameter by name.
     */
    fun queryParam(name: String): String? = queryParams[name]

    /**
     * Creates a copy with additional headers.
     */
    fun withHeaders(additionalHeaders: Map<String, String>): BinaryRequest = copy(headers = headers + additionalHeaders)

    /**
     * Creates a copy with additional metadata.
     */
    fun withMetadata(additionalMetadata: Map<String, String>): BinaryRequest = copy(metadata = metadata + additionalMetadata)

    /**
     * Converts this BinaryRequest to a GenericHttpRequest.
     */
    fun toGenericHttpRequest(): GenericHttpRequest =
        GenericHttpRequest(
            method = headers["X-HTTP-Method"] ?: "POST",
            path = headers["X-HTTP-Path"] ?: "/$commandId",
            pathParameters = pathParams,
            queryParameters = queryParams,
            headers = headers,
            resolvedTenantId = resolvedTenantId,
            multiValueHeaders = multiValueHeaders,
            bodyContent = body.toGenericHttpBody(),
        )

    companion object {
        /**
         * Creates a BinaryRequest from a GenericHttpRequest.
         *
         * @param request The HTTP request to convert
         * @param commandId The command ID for routing
         */
        @JvmStatic
        fun fromGenericHttpRequest(
            request: GenericHttpRequest,
            commandId: String,
        ): BinaryRequest =
            BinaryRequest(
                commandId = commandId,
                contentType = request.contentType,
                accept = request.accept,
                headers = request.headers,
                resolvedTenantId = request.resolvedTenantId,
                multiValueHeaders = request.multiValueHeaders,
                pathParams = request.pathParameters,
                queryParams = request.queryParameters.mapValues { it.value ?: "" },
                body = StreamingBody.fromGenericHttpBody(request.bodyContent),
            )
    }
}

/**
 * Transport-neutral binary response.
 *
 * @property statusCode HTTP status code (or equivalent for other transports)
 * @property contentType Content-Type header value
 * @property headers All response headers
 * @property body The response body
 */
@JsExportCompat
data class BinaryResponse(
    val statusCode: Int,
    val contentType: String,
    val headers: Map<String, String> = emptyMap(),
    val body: StreamingBody = StreamingBody.Empty,
) {
    /**
     * Whether this response indicates success (2xx status code).
     */
    val isSuccess: Boolean get() = statusCode in HTTP_SUCCESS_RANGE

    /**
     * Whether this response indicates a client error (4xx status code).
     */
    val isClientError: Boolean get() = statusCode in HTTP_CLIENT_ERROR_RANGE

    /**
     * Whether this response indicates a server error (5xx status code).
     */
    val isServerError: Boolean get() = statusCode in HTTP_SERVER_ERROR_RANGE

    /**
     * Creates a copy with additional headers.
     */
    fun withHeaders(additionalHeaders: Map<String, String>): BinaryResponse = copy(headers = headers + additionalHeaders)

    /**
     * Converts this BinaryResponse to a GenericHttpResponse.
     */
    fun toGenericHttpResponse(): GenericHttpResponse =
        GenericHttpResponse(
            statusCode = statusCode,
            headers = headers + ("Content-Type" to contentType),
            bodyContent = body.toGenericHttpBody(),
        )

    companion object {
        private val HTTP_SUCCESS_RANGE = 200..299
        private val HTTP_CLIENT_ERROR_RANGE = 400..499
        private val HTTP_SERVER_ERROR_RANGE = 500..599

        /**
         * Common content types.
         */
        const val CONTENT_TYPE_JSON = "application/json"
        const val CONTENT_TYPE_PROTOBUF = "application/protobuf"
        const val CONTENT_TYPE_CBOR = "application/cbor"

        /**
         * Creates a success response with JSON content.
         */
        @JvmStatic
        fun ok(
            body: String,
            contentType: String = CONTENT_TYPE_JSON,
        ): BinaryResponse =
            BinaryResponse(
                statusCode = 200,
                contentType = contentType,
                body = StreamingBody.Text(body),
            )

        /**
         * Creates a success response with binary content.
         */
        @JvmStatic
        fun okBytes(
            body: ByteArray,
            contentType: String = CONTENT_TYPE_PROTOBUF,
        ): BinaryResponse =
            BinaryResponse(
                statusCode = 200,
                contentType = contentType,
                body = StreamingBody.Bytes(body),
            )

        /**
         * Creates a no-content response (204).
         */
        @JvmStatic
        fun noContent(): BinaryResponse =
            BinaryResponse(
                statusCode = 204,
                contentType = CONTENT_TYPE_JSON,
                body = StreamingBody.Empty,
            )

        /**
         * Creates a created response (201).
         */
        @JvmStatic
        fun created(
            body: String,
            contentType: String = CONTENT_TYPE_JSON,
        ): BinaryResponse =
            BinaryResponse(
                statusCode = 201,
                contentType = contentType,
                body = StreamingBody.Text(body),
            )

        /**
         * Creates an error response from a GenericHttpResponse.
         */
        @JvmStatic
        fun fromGenericHttpResponse(response: GenericHttpResponse): BinaryResponse =
            BinaryResponse(
                statusCode = response.statusCode,
                contentType = response.contentType ?: CONTENT_TYPE_JSON,
                headers = response.headers,
                body = StreamingBody.fromGenericHttpBody(response.bodyContent),
            )
    }
}
