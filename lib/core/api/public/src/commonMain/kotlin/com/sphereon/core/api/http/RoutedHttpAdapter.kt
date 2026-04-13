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

package com.sphereon.core.api.http

import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpRoute
import com.sphereon.core.api.http.describe.OpenApiHints

private const val HTTP_NOT_FOUND = 404

/**
 * Base class for adapters that want to avoid duplicating endpoint declarations in:
 * - `describe()` (metadata)
 * - `handleRequest()` (routing)
 *
 * The single source of truth becomes [routes]. Both metadata and runtime dispatch are derived from it.
 *
 * Routes are defined relative to the adapter's base path (e.g., `/`, `/{id}`, `/subresource`).
 * The [mount.adapterBasePath] is automatically prepended when matching requests and
 * generating endpoint descriptions.
 *
 * Note: The dispatcher strips the [mount.serverPrefix] before calling the adapter,
 * so the adapter receives paths like `/keys/abc123` (basePath + route), not
 * `/api/kms/keys/abc123` (serverPrefix + basePath + route).
 */
abstract class RoutedHttpAdapter : RoutableHttpAdapter {
    protected abstract val mount: HttpAdapterMount
    protected abstract val routes: List<HttpRoute>
    protected open val openApiHints: OpenApiHints? = null

    /**
     * Computes the adapter base path prefix from the mount configuration.
     * E.g., adapterBasePath="/keys" -> "/keys"
     *
     * Note: serverPrefix is NOT included because the dispatcher strips it
     * before calling the adapter.
     */
    protected val basePathPrefix: String by lazy {
        mount.adapterBasePath
            .let {
                if (it.startsWith("/")) {
                    it
                } else {
                    "/$it"
                }
            }.trimEnd('/')
    }

    /**
     * Returns the full path for a route pattern by prepending the base path prefix.
     * E.g., with basePath="/keys" and routePattern="/{id}", returns "/keys/{id}"
     */
    protected fun fullPath(routePattern: String): String {
        val pattern =
            routePattern.let {
                if (it.startsWith("/")) {
                    it
                } else {
                    "/$it"
                }
            }
        return "$basePathPrefix$pattern".replace("//", "/")
    }

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = mount,
            endpoints =
                routes.map { route ->
                    route.endpoint.copy(pathPattern = fullPath(route.endpoint.pathPattern))
                },
            openApiHints = openApiHints,
        )

    override fun canHandle(request: GenericHttpRequest): Boolean = routes.any { request.matches(it.endpoint.method.name, fullPath(it.endpoint.pathPattern)) }

    override suspend fun handleRequest(request: GenericHttpRequest): GenericHttpResponse {
        val matches = routes.filter { request.matches(it.endpoint.method.name, fullPath(it.endpoint.pathPattern)) }
        return when (matches.size) {
            0 -> {
                errorResponse(HTTP_NOT_FOUND, "Not found: ${request.method} ${request.path}")
            }

            1 -> {
                try {
                    matches.single().handler(request)
                } catch (expected: Exception) {
                    errorResponse(expected)
                }
            }

            else -> {
                val best = matches.maxByOrNull { CompiledPathPattern.compile(fullPath(it.endpoint.pathPattern)).specificity }!!
                try {
                    best.handler(request)
                } catch (expected: Exception) {
                    errorResponse(expected)
                }
            }
        }
    }
}
