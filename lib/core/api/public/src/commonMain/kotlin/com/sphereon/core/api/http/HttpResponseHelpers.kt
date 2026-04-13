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
 *
 */

package com.sphereon.core.api.http

import com.sphereon.core.api.error.NotFoundException

/**
 * Helper functions to create HTTP responses with less boilerplate.
 */

fun noContentResponse(statusCode: Int = 204) = GenericHttpResponse(
    statusCode = statusCode,
    headers = mapOf("Content-Type" to "application/json")
)

fun createdResponse(location: String, body: String) = GenericHttpResponse(
    statusCode = 201,
    headers = mapOf(
        "Content-Type" to "application/json",
        "Location" to location
    ),
    body = body
)

fun jsonResponse(statusCode: Int, body: String) = GenericHttpResponse(
    statusCode = statusCode,
    headers = mapOf("Content-Type" to "application/json"),
    body = body
)

fun errorResponse(error: Throwable): GenericHttpResponse {
    val statusCode = when (error) {
        is IllegalArgumentException -> 400
        is NoSuchElementException -> 404
        is NotFoundException -> 404
        is IllegalStateException -> 409
        is UnsupportedOperationException -> 501
        else -> 500
    }
    return errorResponse(statusCode, error.message ?: "Unknown error")
}

fun errorResponse(statusCode: Int, message: String) = GenericHttpResponse(
    statusCode = statusCode,
    headers = mapOf("Content-Type" to "application/json"),
    body = """{"code": "$statusCode", "message": ${escapeJsonString(message)}}"""
)

/**
 * Escapes a string for safe inclusion in a JSON document.
 * Handles special characters that could break JSON parsing or enable injection.
 */
private fun escapeJsonString(value: String): String {
    val sb = StringBuilder("\"")
    for (char in value) {
        when (char) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            else -> {
                if (char.code < 32) {
                    // Escape other control characters as unicode
                    sb.append("\\u${char.code.toString(16).padStart(4, '0')}")
                } else {
                    sb.append(char)
                }
            }
        }
    }
    sb.append("\"")
    return sb.toString()
}
