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

package com.sphereon.ktor.http.client.support

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Ensures the HTTP response is successful (status code 200-299).
 * Throws a descriptive exception for error responses.
 *
 * @throws HttpException if the response status code indicates an error
 * @return this HttpResponse for chaining
 */
suspend fun HttpResponse.ensureSuccess(): HttpResponse {
    if (status.isSuccess()) {
        return this
    }

    val errorBody =
        try {
            bodyAsText()
        } catch (expected: Exception) {
            "<unable to read response body: ${expected.message}>"
        }

    val errorMessage =
        buildString {
            append("HTTP request failed with status ${status.value} ${status.description}")
            append("\nURL: ${call.request.url}")
            append("\nMethod: ${call.request.method.value}")

            if (errorBody.isNotBlank()) {
                append("\nResponse body: ")
                append(
                    if (errorBody.length > 500) {
                        errorBody.take(500) + "..."
                    } else {
                        errorBody
                    }
                )
            }
        }

    throw HttpException(status.value, errorMessage, errorBody)
}

/**
 * Exception thrown when an HTTP request fails.
 *
 * @property statusCode The HTTP status code
 * @property message Descriptive error message
 * @property responseBody The raw response body (if available)
 */
class HttpException(
    val statusCode: Int,
    override val message: String,
    val responseBody: String,
) : Exception(message)
