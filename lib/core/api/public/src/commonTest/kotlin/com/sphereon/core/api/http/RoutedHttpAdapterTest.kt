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

import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.HttpRoute
import com.sphereon.core.api.http.describe.OpenApiHints
import com.sphereon.core.api.http.describe.httpRoutes
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteMatch
import com.sphereon.core.api.http.response.errorResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RoutedHttpAdapterTest {
    private class TestRoutedAdapter(
        override val id: String = "test-adapter",
        override val mount: HttpAdapterMount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/users"),
        routes: List<HttpRoute> = emptyList(),
        override val openApiHints: OpenApiHints? = null,
    ) : RoutedHttpAdapter() {
        override val routes: List<HttpRoute> =
            routes.mapIndexed { index, route ->
                route.copy(
                    endpoint =
                        route.endpoint.copy(
                            handlerCommandId = route.endpoint.handlerCommandId ?: "test.routed.route-$index",
                        ),
                )
            }
    }

    @Test
    fun describeReturnsCorrectId() {
        val adapter = TestRoutedAdapter(id = "my-adapter")
        val description = adapter.describe()
        assertEquals("my-adapter", description.id)
    }

    @Test
    fun describeReturnsMount() {
        val mount = HttpAdapterMount(serverPrefix = "/api/v1", adapterBasePath = "/resources")
        val adapter = TestRoutedAdapter(mount = mount)
        val description = adapter.describe()
        assertEquals(mount, description.mount)
    }

    @Test
    fun describeReturnsEndpointsWithFullPaths() {
        val routes =
            httpRoutes {
                get("/") { handle { GenericHttpResponse(statusCode = 200) } }
                get("/{id}") { handle { GenericHttpResponse(statusCode = 200) } }
            }
        val adapter = TestRoutedAdapter(routes = routes)
        val description = adapter.describe()

        assertEquals(2, description.endpoints.size)
        // A "/" route is the adapter root, so fullPath collapses it to the base path.
        assertEquals("/users", description.endpoints[0].pathPattern)
        assertEquals("/users/{id}", description.endpoints[1].pathPattern)
    }

    @Test
    fun describeIncludesOpenApiHints() {
        val hints = OpenApiHints(tags = setOf("users", "admin"))
        val adapter = TestRoutedAdapter(openApiHints = hints)
        val description = adapter.describe()
        assertNotNull(description.openApiHints)
        assertTrue(description.openApiHints!!.tags.contains("users"))
    }

    @Test
    fun selectedRouteReturns404ForNoMatchingRoute() =
        runTest {
            val adapter = TestRoutedAdapter(routes = emptyList())
            val request = GenericHttpRequest(method = "GET", path = "/users")
            val response = adapter.handleTestRequest(request)
            assertEquals(404, response.statusCode)
        }

    @Test
    fun selectedRouteExecutesMatchingHandler() =
        runTest {
            val routes =
                httpRoutes {
                    get("/") { handle { GenericHttpResponse(statusCode = 200, body = "users list") } }
                }
            val adapter = TestRoutedAdapter(routes = routes)
            val request = GenericHttpRequest(method = "GET", path = "/users")
            val response = adapter.handleTestRequest(request)
            assertEquals(200, response.statusCode)
            assertEquals("users list", response.body)
        }

    @Test
    fun selectedRouteReturnsErrorResponseOnException() =
        runTest {
            val routes =
                httpRoutes {
                    get("/") { handle { throw IllegalArgumentException("Test error") } }
                }
            val adapter = TestRoutedAdapter(routes = routes)
            val request = GenericHttpRequest(method = "GET", path = "/users")
            val response = adapter.handleTestRequest(request)
            assertEquals(400, response.statusCode)
        }

    @Test
    fun selectedRouteExtractsPathVariables() =
        runTest {
            val routes =
                httpRoutes {
                    get("/{id}") {
                        handle { request ->
                            GenericHttpResponse(
                                statusCode = 200,
                                body = "user: ${request.pathParameters["id"]}",
                            )
                        }
                    }
                }
            val adapter = TestRoutedAdapter(routes = routes)
            val request = GenericHttpRequest(method = "GET", path = "/users/123")
            val response = adapter.handleTestRequest(request)
            assertEquals(200, response.statusCode)
            assertEquals("user: 123", response.body)
        }

    @Test
    fun basePathPrefixHandlesLeadingSlash() {
        val mountWithSlash = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/users")
        val mountWithoutSlash = HttpAdapterMount(serverPrefix = "", adapterBasePath = "users")

        val routes =
            httpRoutes {
                get("/") { handle { GenericHttpResponse(statusCode = 200) } }
            }

        val adapterWithRoutes1 = TestRoutedAdapter(mount = mountWithSlash, routes = routes)
        val adapterWithRoutes2 = TestRoutedAdapter(mount = mountWithoutSlash, routes = routes)

        // A "/" route is the adapter root, so fullPath collapses it to the base path.
        assertEquals("/users", adapterWithRoutes1.describe().endpoints[0].pathPattern)
        assertEquals("/users", adapterWithRoutes2.describe().endpoints[0].pathPattern)
    }
}

private suspend fun RoutedHttpAdapter.handleTestRequest(request: GenericHttpRequest): GenericHttpResponse {
    val matches =
        describe().endpoints.flatMap { endpoint ->
            endpoint.pathPatterns
                .filter { pattern -> request.matches(endpoint.method.name, pattern) }
                .map { pattern -> endpoint to pattern }
        }
    if (matches.isEmpty()) return errorResponse(404, "Not found")
    val (endpoint, pattern) = matches.single()
    return handleResolvedRequest(
        request,
        HttpAdapterRouteMatch(
            adapterId = id,
            method = request.method,
            originalPath = request.path,
            normalizedPath = request.path,
            matchedPathPattern = pattern,
            handlerCommandId = requireNotNull(endpoint.handlerCommandId),
            tenantIdFromPath = null,
        ),
    )
}
