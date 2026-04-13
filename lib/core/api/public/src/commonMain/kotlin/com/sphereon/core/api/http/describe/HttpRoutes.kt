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
 */

package com.sphereon.core.api.http.describe

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse

/**
 * A single route definition: endpoint metadata plus handler function.
 *
 * This is intended to be the single source of truth so:
 * - endpoint metadata is not duplicated in `describe()` and `handleRequest()`
 * - generic hosting/dispatching can introspect routes
 */
data class HttpRoute(
    val endpoint: HttpEndpointDescriptor,
    val handler: suspend (GenericHttpRequest) -> GenericHttpResponse
) {
    fun matches(request: GenericHttpRequest): Boolean =
        request.matches(endpoint.method.name, endpoint.pathPattern)
}

/**
 * DSL to define routes without `listOf(...)` or `setOf(...)`.
 */
fun httpRoutes(block: HttpRoutesBuilder.() -> Unit): List<HttpRoute> =
    HttpRoutesBuilder().apply(block).build()

class HttpRoutesBuilder internal constructor() {
    private val routes: MutableList<HttpRoute> = mutableListOf()

    fun get(pathPattern: String, block: HttpRouteBuilder.() -> Unit) = add(HttpMethod.GET, pathPattern, block)
    fun post(pathPattern: String, block: HttpRouteBuilder.() -> Unit) = add(HttpMethod.POST, pathPattern, block)
    fun put(pathPattern: String, block: HttpRouteBuilder.() -> Unit) = add(HttpMethod.PUT, pathPattern, block)
    fun delete(pathPattern: String, block: HttpRouteBuilder.() -> Unit) = add(HttpMethod.DELETE, pathPattern, block)
    fun patch(pathPattern: String, block: HttpRouteBuilder.() -> Unit) = add(HttpMethod.PATCH, pathPattern, block)

    private fun add(method: HttpMethod, pathPattern: String, block: HttpRouteBuilder.() -> Unit) {
        routes += HttpRouteBuilder(method, pathPattern).apply(block).build()
    }

    internal fun build(): List<HttpRoute> = routes.toList()
}

class HttpRouteBuilder internal constructor(
    private val method: HttpMethod,
    private val pathPattern: String
) {
    private val consumes: MutableSet<MediaType> = linkedSetOf()
    private val produces: MutableSet<MediaType> = linkedSetOf()
    private var operationId: String? = null
    private val tags: MutableSet<String> = linkedSetOf()
    private var summary: String? = null
    private var handler: (suspend (GenericHttpRequest) -> GenericHttpResponse)? = null

    fun consumes(vararg types: MediaType) {
        consumes += types
    }

    fun produces(vararg types: MediaType) {
        produces += types
    }

    fun operationId(value: String) {
        operationId = value
    }

    fun tags(vararg values: String) {
        tags += values
    }

    fun summary(value: String) {
        summary = value
    }

    fun handle(block: suspend (GenericHttpRequest) -> GenericHttpResponse) {
        handler = block
    }

    internal fun build(): HttpRoute {
        val finalHandler = requireNotNull(handler) { "Route $method $pathPattern requires handle { ... }" }
        return HttpRoute(
            endpoint = HttpEndpointDescriptor(
                method = method,
                pathPattern = pathPattern,
                consumes = consumes.toSet(),
                produces = produces.toSet(),
                operationId = operationId,
                tags = tags.toSet(),
                summary = summary
            ),
            handler = finalHandler
        )
    }
}
