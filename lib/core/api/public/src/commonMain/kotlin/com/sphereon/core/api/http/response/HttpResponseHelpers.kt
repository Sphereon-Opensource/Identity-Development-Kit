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
 *
 */

package com.sphereon.core.api.http.response

import com.sphereon.core.api.error.NotFoundException
import com.sphereon.core.api.http.GenericHttpResponse

/**
 * Top-level helpers to create HTTP responses with less boilerplate.
 *
 * For richer envelopes (paginated, error with `details`, binary content) prefer
 * [ResponseBuilder]. The error helpers here delegate to [ResponseBuilder.error] so the wire
 * shape is identical to command-level errors:
 * `{ "error": { "code", "message" } }`.
 */

fun noContentResponse(statusCode: Int = 204) =
    GenericHttpResponse(
        statusCode = statusCode,
        headers = mapOf("Content-Type" to "application/json"),
    )

fun createdResponse(
    location: String,
    body: String,
) = GenericHttpResponse(
    statusCode = 201,
    headers =
        mapOf(
            "Content-Type" to "application/json",
            "Location" to location,
        ),
    body = body,
)

fun jsonResponse(
    statusCode: Int,
    body: String,
) = GenericHttpResponse(
    statusCode = statusCode,
    headers = mapOf("Content-Type" to "application/json"),
    body = body,
)

/**
 * Map a [Throwable] to a [GenericHttpResponse]. The status code is derived from the exception
 * type and the body uses the canonical error envelope (see [ResponseBuilder.error]).
 */
fun errorResponse(error: Throwable): GenericHttpResponse {
    val statusCode =
        when (error) {
            is IllegalArgumentException -> HTTP_BAD_REQUEST
            is NoSuchElementException -> HTTP_NOT_FOUND
            is NotFoundException -> HTTP_NOT_FOUND
            is IllegalStateException -> HTTP_CONFLICT
            is UnsupportedOperationException -> HTTP_NOT_IMPLEMENTED
            else -> HTTP_INTERNAL_SERVER_ERROR
        }
    return errorResponse(statusCode, error.message ?: "Unknown error")
}

/**
 * Build an error response in the canonical envelope shape:
 * `{ "error": { "code": "<label>", "message": "<msg>" } }`.
 *
 * The code label is derived from [statusCode] (e.g. `404` → `NOT_FOUND`, `409` → `CONFLICT`);
 * unknown status codes fall back to `ERROR`. When you have a more specific machine-readable
 * code, call [ResponseBuilder.error] directly with all four arguments.
 */
fun errorResponse(
    statusCode: Int,
    message: String,
): GenericHttpResponse =
    ResponseBuilder.error(
        statusCode = statusCode,
        code = statusCodeToCode(statusCode),
        message = message,
    )

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_METHOD_NOT_ALLOWED = 405
private const val HTTP_CONFLICT = 409
private const val HTTP_UNPROCESSABLE_ENTITY = 422
private const val HTTP_INTERNAL_SERVER_ERROR = 500
private const val HTTP_NOT_IMPLEMENTED = 501
private const val HTTP_BAD_GATEWAY = 502
private const val HTTP_SERVICE_UNAVAILABLE = 503
private const val HTTP_GATEWAY_TIMEOUT = 504

/**
 * Attach an `ETag` header to this response (RFC 7232).
 *
 * The value is an opaque validator — anything that uniquely identifies the current
 * representation (hash, version number, UUID, etc.). It is wrapped in double quotes per the
 * spec and treated as a strong validator (no `W/` prefix). Any existing `ETag` header is
 * overwritten. Clients echo the value back as the next request's `If-Match` header to enable
 * optimistic-concurrency control end to end.
 */
fun GenericHttpResponse.withETag(value: String): GenericHttpResponse = copy(headers = headers + ("ETag" to "\"$value\""))

/**
 * Map an HTTP status code to a stable, machine-readable error code label used as the `code`
 * field of the canonical error envelope.
 */
private fun statusCodeToCode(statusCode: Int): String =
    when (statusCode) {
        HTTP_BAD_REQUEST -> "BAD_REQUEST"
        HTTP_UNAUTHORIZED -> "UNAUTHORIZED"
        HTTP_FORBIDDEN -> "FORBIDDEN"
        HTTP_NOT_FOUND -> "NOT_FOUND"
        HTTP_METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED"
        HTTP_CONFLICT -> "CONFLICT"
        HTTP_UNPROCESSABLE_ENTITY -> "UNPROCESSABLE_ENTITY"
        HTTP_INTERNAL_SERVER_ERROR -> "INTERNAL_ERROR"
        HTTP_NOT_IMPLEMENTED -> "NOT_IMPLEMENTED"
        HTTP_BAD_GATEWAY -> "BAD_GATEWAY"
        HTTP_SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
        HTTP_GATEWAY_TIMEOUT -> "GATEWAY_TIMEOUT"
        else -> "ERROR"
    }
