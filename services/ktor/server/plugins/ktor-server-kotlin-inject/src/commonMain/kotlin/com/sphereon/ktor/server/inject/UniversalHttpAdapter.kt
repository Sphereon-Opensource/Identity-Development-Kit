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

package com.sphereon.ktor.server.inject

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.dispatch.HttpAdapterDispatcher
import dev.zacsweers.metro.createGraph
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/**
 * Configuration for the Universal HTTP Adapter exposure.
 */
class UniversalHttpAdapterConfig {
    /**
     * Optional path prefix for the catch-all route.
     * If null, the catch-all handles all routes not matched by other explicit routes.
     * If set (e.g., "/api"), only routes starting with that prefix are handled.
     */
    var pathPrefix: String? = null

    /**
     * Whether to enable verbose logging for dispatched requests.
     */
    var verboseLogging: Boolean = false

    /**
     * Custom error handler for exceptions during dispatch.
     * If null, a default 500 response is returned.
     */
    var errorHandler: (suspend (ApplicationCall, Throwable) -> Unit)? = null
}

/**
 * Installs the Universal HTTP Adapter exposure, which routes all HTTP requests
 * to the catalog-driven [HttpAdapterDispatcher].
 *
 * This is the OSS-first generic exposure mechanism for Ktor. It:
 * - Converts Ktor [ApplicationCall] to [GenericHttpRequest] with lazy body reading
 * - Delegates to the [HttpAdapterDispatcher] obtained from SessionScope
 * - Converts the [GenericHttpResponse] back to a Ktor response
 *
 * **Prerequisites:**
 * - The `KotlinInject` plugin must be installed on the application
 * - [HttpAdapterDispatcher] must be contributed to SessionScope via kotlin-inject/anvil
 *
 * **Usage:**
 * ```kotlin
 * fun Application.module() {
 *     install(KotlinInject) {
 *         appGraph = { createGraph<MyAppGraph>() }
 *         // ... other config
 *     }
 *
 *     installUniversalHttpAdapters {
 *         pathPrefix = "/api"  // optional
 *     }
 * }
 * ```
 *
 * @param configure Optional configuration block
 */
fun Application.installUniversalHttpAdapters(configure: UniversalHttpAdapterConfig.() -> Unit = {}) {
    val config = UniversalHttpAdapterConfig().apply(configure)

    routing {
        // Create a catch-all route
        route(config.pathPrefix ?: "{...}") {
            handle {
                try {
                    // Get the dispatcher from the session graph directly
                    val dispatcher = (call.sessionInstance.graph as HttpAdapterDispatcher.Graph).httpAdapterDispatcher

                    // Convert Ktor call to GenericHttpRequest
                    val genericRequest = call.toGenericHttpRequest()

                    if (config.verboseLogging) {
                        call.application.log.info("Dispatching: ${genericRequest.method} ${genericRequest.path}")
                    }

                    // Dispatch and get response
                    val genericResponse = dispatcher.dispatch(genericRequest)

                    // Convert GenericHttpResponse to Ktor response
                    call.respondWithGenericResponse(genericResponse)
                } catch (expected: Throwable) {
                    val handler = config.errorHandler
                    if (handler != null) {
                        handler(call, expected)
                    } else {
                        call.application.log.error("Error in UniversalHttpAdapter dispatch", expected)
                        call.respondText(
                            text = "Internal server error: ${expected.message}",
                            contentType = ContentType.Text.Plain,
                            status = HttpStatusCode.InternalServerError,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Alternative installation that mounts under a specific base path.
 *
 * **Usage:**
 * ```kotlin
 * routing {
 *     route("/api") {
 *         installUniversalHttpAdapters()
 *     }
 * }
 * ```
 */
fun Route.installUniversalHttpAdapters(configure: UniversalHttpAdapterConfig.() -> Unit = {}) {
    val config = UniversalHttpAdapterConfig().apply(configure)

    // Create a catch-all under this route
    route("{...}") {
        handle {
            try {
                val dispatcher = (call.sessionInstance.graph as HttpAdapterDispatcher.Graph).httpAdapterDispatcher
                val genericRequest = call.toGenericHttpRequest()

                if (config.verboseLogging) {
                    call.application.log.info("Dispatching: ${genericRequest.method} ${genericRequest.path}")
                }

                val genericResponse = dispatcher.dispatch(genericRequest)
                call.respondWithGenericResponse(genericResponse)
            } catch (expected: Throwable) {
                val handler = config.errorHandler
                if (handler != null) {
                    handler(call, expected)
                } else {
                    call.application.log.error("Error in UniversalHttpAdapter dispatch", expected)
                    call.respondText(
                        text = "Internal server error: ${expected.message}",
                        contentType = ContentType.Text.Plain,
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            }
        }
    }
}

/**
 * Converts a Ktor [ApplicationCall] to a [GenericHttpRequest].
 *
 * This implementation:
 * - Preserves the full request path (including query string separately)
 * - Extracts headers as a map
 * - Extracts query parameters as a map
 * - Provides a lazy body supplier to defer reading the request body
 */
suspend fun ApplicationCall.toGenericHttpRequest(): GenericHttpRequest {
    val method = request.httpMethod.value
    val path = request.path()

    // Extract headers
    val headers =
        request.headers
            .entries()
            .associate { (name, values) -> name to values.joinToString(", ") }

    // Extract query parameters
    val queryParameters =
        request.queryParameters
            .entries()
            .associate { (name, values) -> name to values.firstOrNull().orEmpty() }

    // Path parameters from Ktor routing (if any)
    val pathParameters =
        parameters
            .entries()
            .filter { (key, _) -> key != "..." } // Filter out the catch-all parameter
            .associate { (name, values) -> name to values.firstOrNull().orEmpty() }

    // Create lazy body supplier
    // Note: We read the body once and cache it since receiveText() can only be called once
    val bodyText =
        try {
            receiveText()
        } catch (_: Exception) {
            null
        }

    return GenericHttpRequest(
        method = method,
        path = path,
        headers = headers,
        queryParameters = queryParameters,
        pathParameters = pathParameters,
        bodySupplier =
            if (bodyText != null) {
                { bodyText }
            } else {
                null
            },
    )
}

/**
 * Responds to a Ktor call with a [GenericHttpResponse].
 */
suspend fun ApplicationCall.respondWithGenericResponse(response: GenericHttpResponse) {
    // Set response headers
    response.headers.forEach { (name, value) ->
        this.response.header(name, value)
    }

    // Determine content type from response headers or default to application/json
    val contentType =
        response.headers["Content-Type"]?.let {
            ContentType.parse(it)
        } ?: ContentType.Application.Json

    // Respond with body and status
    val statusCode = HttpStatusCode.fromValue(response.statusCode)
    val body = response.body

    if (body != null) {
        respondText(
            text = body,
            contentType = contentType,
            status = statusCode,
        )
    } else {
        respond(statusCode)
    }
}
