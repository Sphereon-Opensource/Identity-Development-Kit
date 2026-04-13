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

@file:Suppress("TooGenericExceptionCaught") // HTTP adapter must handle any exception during request body reading

package com.sphereon.ktor.server.inject.http

import com.sphereon.core.api.http.GenericHttpBody
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.LazyMap
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.contentLength
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText

/**
 * Convert Ktor ApplicationRequest to framework-agnostic GenericHttpRequest.
 *
 * This is the standard implementation for converting Ktor requests to the
 * framework-agnostic GenericHttpRequest used by HttpAdapters and Commands.
 *
 * Performance optimizations:
 * - Lazy maps: headers/query params use lazy delegates (only allocated when accessed)
 * - Optimized header joining: only join multi-value headers when needed
 * - Smart body detection: only reads body when Content-Type is JSON or Content-Length > 0
 *
 * Note: Ktor's receiveText() is a suspend function that can only be called once,
 * so body must be read immediately (cannot be lazily loaded like in Spring).
 *
 * @param call The ApplicationCall containing request context
 * @return A GenericHttpRequest suitable for use with HttpAdapters
 */
suspend fun ApplicationRequest.toGenericHttpRequest(call: ApplicationCall): GenericHttpRequest {
    val path = this.path()
    val method = this.httpMethod.value
    val request = this

    // Read body immediately (Ktor's receiveText is suspend and can only be called once)
    // Only attempt to read body if Content-Type is JSON or Content-Length indicates content
    val body =
        try {
            if (request.contentType()?.match(ContentType.Application.Json) == true ||
                request.contentLength()?.let { it > 0 } == true
            ) {
                call.receiveText()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

    return GenericHttpRequest(
        method = method,
        path = path,
        // Lazy headers - avoid allocation and string joining if never accessed
        headers =
            LazyMap {
                val headerMap = mutableMapOf<String, String>()
                request.headers.entries().forEach { (key, values) ->
                    headerMap[key] =
                        if (values.size == 1) {
                            values[0]
                        } else {
                            values.joinToString(",")
                        }
                }
                headerMap
            },
        // Lazy query parameters - avoid allocation if never accessed
        queryParameters =
            LazyMap {
                val queryMap = mutableMapOf<String, String?>()
                request.queryParameters.entries().forEach { (key, values) ->
                    queryMap[key] =
                        if (values.size == 1) {
                            values[0]
                        } else {
                            values.joinToString(",")
                        }
                }
                queryMap
            },
        // Body supplier that returns already-read body
        bodySupplier = { body },
    )
}

/**
 * Respond with a GenericHttpResponse.
 *
 * Converts a framework-agnostic GenericHttpResponse to a Ktor response.
 *
 * Performance optimization: Single header iteration (not duplicated for body != null case).
 *
 * @param response The GenericHttpResponse from an HttpAdapter
 */
suspend fun ApplicationCall.respondWithGeneric(response: GenericHttpResponse) {
    val status = HttpStatusCode.fromValue(response.statusCode)
    val contentType = ContentType.parse(response.headers["Content-Type"] ?: "application/json")

    // Add headers once (not duplicated between if/else branches)
    this.response.headers.apply {
        response.headers.forEach { (key, value) ->
            append(key, value)
        }
    }

    // Respond based on body content type
    when (val bodyContent = response.bodyContent) {
        is GenericHttpBody.Bytes -> {
            this.respondBytes(
                bytes = bodyContent.value,
                contentType = contentType,
                status = status,
            )
        }

        is GenericHttpBody.LazyBytes -> {
            val bytes = bodyContent.value
            if (bytes != null) {
                this.respondBytes(bytes = bytes, contentType = contentType, status = status)
            } else {
                this.respond(status)
            }
        }

        is GenericHttpBody.Text -> {
            this.respondText(
                text = bodyContent.value,
                contentType = contentType,
                status = status,
            )
        }

        is GenericHttpBody.LazyText -> {
            val text = bodyContent.value
            if (text != null) {
                this.respondText(text = text, contentType = contentType, status = status)
            } else {
                this.respond(status)
            }
        }

        is GenericHttpBody.Empty -> {
            this.respond(status)
        }
    }
}
