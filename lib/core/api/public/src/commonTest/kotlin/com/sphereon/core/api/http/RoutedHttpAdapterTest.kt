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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RoutedHttpAdapterTest {
    private class TestRoutedAdapter(
        override val id: String = "test-adapter",
        override val mount: HttpAdapterMount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/users"),
        override val routes: List<HttpRoute> = emptyList(),
        override val openApiHints: OpenApiHints? = null,
    ) : RoutedHttpAdapter()

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
        // The fullPath joins basePath + routePattern, so "/" becomes "/users/"
        assertEquals("/users/", description.endpoints[0].pathPattern)
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
    fun canHandleReturnsTrueForMatchingRoute() {
        val routes =
            httpRoutes {
                get("/") { handle { GenericHttpResponse(statusCode = 200) } }
            }
        val adapter = TestRoutedAdapter(routes = routes)
        val request = GenericHttpRequest(method = "GET", path = "/users")
        assertTrue(adapter.canHandle(request))
    }

    @Test
    fun canHandleReturnsFalseForNonMatchingRoute() {
        val routes =
            httpRoutes {
                get("/") { handle { GenericHttpResponse(statusCode = 200) } }
            }
        val adapter = TestRoutedAdapter(routes = routes)
        val request = GenericHttpRequest(method = "POST", path = "/users")
        assertFalse(adapter.canHandle(request))
    }

    @Test
    fun canHandleReturnsFalseForDifferentPath() {
        val routes =
            httpRoutes {
                get("/") { handle { GenericHttpResponse(statusCode = 200) } }
            }
        val adapter = TestRoutedAdapter(routes = routes)
        val request = GenericHttpRequest(method = "GET", path = "/other")
        assertFalse(adapter.canHandle(request))
    }

    @Test
    fun handleRequestReturns404ForNoMatchingRoute() =
        runTest {
            val adapter = TestRoutedAdapter(routes = emptyList())
            val request = GenericHttpRequest(method = "GET", path = "/users")
            val response = adapter.handleRequest(request)
            assertEquals(404, response.statusCode)
        }

    @Test
    fun handleRequestExecutesMatchingHandler() =
        runTest {
            val routes =
                httpRoutes {
                    get("/") { handle { GenericHttpResponse(statusCode = 200, body = "users list") } }
                }
            val adapter = TestRoutedAdapter(routes = routes)
            val request = GenericHttpRequest(method = "GET", path = "/users")
            val response = adapter.handleRequest(request)
            assertEquals(200, response.statusCode)
            assertEquals("users list", response.body)
        }

    @Test
    fun handleRequestReturnsErrorResponseOnException() =
        runTest {
            val routes =
                httpRoutes {
                    get("/") { handle { throw IllegalArgumentException("Test error") } }
                }
            val adapter = TestRoutedAdapter(routes = routes)
            val request = GenericHttpRequest(method = "GET", path = "/users")
            val response = adapter.handleRequest(request)
            assertEquals(400, response.statusCode)
        }

    @Test
    fun handleRequestMatchesPathVariables() =
        runTest {
            val routes =
                httpRoutes {
                    get("/{id}") {
                        handle { request ->
                            GenericHttpResponse(statusCode = 200, body = "user: ${request.path}")
                        }
                    }
                }
            val adapter = TestRoutedAdapter(routes = routes)
            val request = GenericHttpRequest(method = "GET", path = "/users/123")
            val response = adapter.handleRequest(request)
            assertEquals(200, response.statusCode)
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

        // The fullPath joins basePath + routePattern, so "/" becomes "/users/"
        assertEquals("/users/", adapterWithRoutes1.describe().endpoints[0].pathPattern)
        assertEquals("/users/", adapterWithRoutes2.describe().endpoints[0].pathPattern)
    }
}
