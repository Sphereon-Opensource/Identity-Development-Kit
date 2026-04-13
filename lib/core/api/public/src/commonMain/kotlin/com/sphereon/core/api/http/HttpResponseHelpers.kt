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

package com.sphereon.core.api.http

import com.sphereon.core.api.error.NotFoundException

/**
 * Helper functions to create HTTP responses with less boilerplate.
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

private const val ASCII_CONTROL_CHAR_LIMIT = 32
private const val HEX_RADIX = 16
private const val UNICODE_ESCAPE_PAD_LENGTH = 4
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_NOT_FOUND = 404
private const val HTTP_CONFLICT = 409
private const val HTTP_INTERNAL_SERVER_ERROR = 500
private const val HTTP_NOT_IMPLEMENTED = 501

fun errorResponse(
    statusCode: Int,
    message: String,
) = GenericHttpResponse(
    statusCode = statusCode,
    headers = mapOf("Content-Type" to "application/json"),
    body = """{"code": "$statusCode", "message": ${escapeJsonString(message)}}""",
)

/**
 * Escapes a string for safe inclusion in a JSON document.
 * Handles special characters that could break JSON parsing or enable injection.
 */
private fun escapeJsonString(value: String): String {
    val sb = StringBuilder("\"")
    for (char in value) {
        when (char) {
            '"' -> {
                sb.append("\\\"")
            }

            '\\' -> {
                sb.append("\\\\")
            }

            '\n' -> {
                sb.append("\\n")
            }

            '\r' -> {
                sb.append("\\r")
            }

            '\t' -> {
                sb.append("\\t")
            }

            '\b' -> {
                sb.append("\\b")
            }

            '\u000C' -> {
                sb.append("\\f")
            }

            else -> {
                if (char.code < ASCII_CONTROL_CHAR_LIMIT) {
                    // Escape other control characters as unicode
                    sb.append("\\u${char.code.toString(HEX_RADIX).padStart(UNICODE_ESCAPE_PAD_LENGTH, '0')}")
                } else {
                    sb.append(char)
                }
            }
        }
    }
    sb.append("\"")
    return sb.toString()
}
