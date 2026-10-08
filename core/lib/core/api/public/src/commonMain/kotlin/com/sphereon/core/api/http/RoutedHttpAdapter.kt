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
import com.sphereon.core.api.http.response.errorResponse
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlin.coroutines.cancellation.CancellationException

/**
 * Base class for adapters that want to avoid duplicating endpoint declarations in:
 * - `describe()` (metadata)
 * - `handleResolvedRequest()` (routing)
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
@JsExportCompat
abstract class RoutedHttpAdapter : HttpAdapter {
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
        if (pattern == "/") {
            return basePathPrefix.ifEmpty { "/" }
        }
        return "$basePathPrefix$pattern".replace("//", "/")
    }

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = mount,
            endpoints =
                routes.map { route ->
                    // Prepend base path to every pattern this descriptor exposes so
                    // catalog matching sees the absolute URLs.
                    route.endpoint.copy(
                        pathPatterns = route.endpoint.pathPatterns.map { fullPath(it) },
                    )
                },
            openApiHints = openApiHints,
        )

    @JsExportIgnoreCompat
    override suspend fun handleResolvedRequest(
        request: GenericHttpRequest,
        route: com.sphereon.core.api.http.dispatch.HttpAdapterRouteMatch,
    ): GenericHttpResponse {
        check(route.adapterId == id && request.method.equals(route.method, ignoreCase = true)) {
            "Preselected route identity does not belong to runtime adapter '$id'"
        }
        val matches =
            routes.filter { candidate ->
                candidate.endpoint.handlerCommandId == route.handlerCommandId &&
                    candidate.endpoint.method.name.equals(route.method, ignoreCase = true) &&
                    candidate.endpoint.pathPatterns.any { pattern ->
                        fullPath(pattern) == route.matchedPathPattern
                    }
            }
        return when (matches.size) {
            0 -> error("Preselected HTTP handler '${route.handlerCommandId}' is not declared by adapter '$id'")

            1 -> {
                try {
                    matches.single().handler(request.withExtractedParams(route.matchedPathPattern))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (expected: Exception) {
                    errorResponse(expected)
                }
            }

            else -> error("Adapter '$id' declares multiple runtime handlers for '${route.handlerCommandId}'")
        }
    }
}
