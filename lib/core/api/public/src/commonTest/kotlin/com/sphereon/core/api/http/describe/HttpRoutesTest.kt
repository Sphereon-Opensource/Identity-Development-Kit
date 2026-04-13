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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HttpRouteTest {

    @Test
    fun routeHasEndpoint() {
        val endpoint = HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/test")
        val route = HttpRoute(endpoint = endpoint, handler = { GenericHttpResponse(statusCode = 200, body = "OK") })
        assertEquals(endpoint, route.endpoint)
    }

    @Test
    fun routeHasHandler() = runTest {
        val endpoint = HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/test")
        val route = HttpRoute(endpoint = endpoint, handler = { GenericHttpResponse(statusCode = 200, body = "OK") })
        val response = route.handler(GenericHttpRequest(method = "GET", path = "/test"))
        assertEquals(200, response.statusCode)
    }

    @Test
    fun matchesReturnsTrueForMatchingRequest() {
        val endpoint = HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/users")
        val route = HttpRoute(endpoint = endpoint, handler = { GenericHttpResponse(statusCode = 200) })
        val request = GenericHttpRequest(method = "GET", path = "/users")
        assertTrue(route.matches(request))
    }
}

class HttpRoutesBuilderTest {

    @Test
    fun httpRoutesCreatesEmptyListWhenNoRoutes() {
        val routes = httpRoutes { }
        assertTrue(routes.isEmpty())
    }

    @Test
    fun httpRoutesCreatesGetRoute() = runTest {
        val routes = httpRoutes {
            get("/users") {
                handle { GenericHttpResponse(200, emptyMap(), "users") }
            }
        }
        assertEquals(1, routes.size)
        assertEquals(HttpMethod.GET, routes[0].endpoint.method)
        assertEquals("/users", routes[0].endpoint.pathPattern)
    }

    @Test
    fun httpRoutesCreatesPostRoute() = runTest {
        val routes = httpRoutes {
            post("/users") {
                handle { GenericHttpResponse(201, emptyMap(), "created") }
            }
        }
        assertEquals(1, routes.size)
        assertEquals(HttpMethod.POST, routes[0].endpoint.method)
    }

    @Test
    fun httpRoutesCreatesPutRoute() = runTest {
        val routes = httpRoutes {
            put("/users/{id}") {
                handle { GenericHttpResponse(200, emptyMap(), "updated") }
            }
        }
        assertEquals(1, routes.size)
        assertEquals(HttpMethod.PUT, routes[0].endpoint.method)
    }

    @Test
    fun httpRoutesCreatesDeleteRoute() = runTest {
        val routes = httpRoutes {
            delete("/users/{id}") {
                handle { GenericHttpResponse(204, emptyMap(), null) }
            }
        }
        assertEquals(1, routes.size)
        assertEquals(HttpMethod.DELETE, routes[0].endpoint.method)
    }

    @Test
    fun httpRoutesCreatesPatchRoute() = runTest {
        val routes = httpRoutes {
            patch("/users/{id}") {
                handle { GenericHttpResponse(200, emptyMap(), "patched") }
            }
        }
        assertEquals(1, routes.size)
        assertEquals(HttpMethod.PATCH, routes[0].endpoint.method)
    }

    @Test
    fun httpRoutesCreatesMultipleRoutes() = runTest {
        val routes = httpRoutes {
            get("/users") { handle { GenericHttpResponse(200, emptyMap(), null) } }
            post("/users") { handle { GenericHttpResponse(201, emptyMap(), null) } }
            get("/users/{id}") { handle { GenericHttpResponse(200, emptyMap(), null) } }
        }
        assertEquals(3, routes.size)
    }
}

class HttpRouteBuilderTest {

    @Test
    fun builderSetsConsumes() = runTest {
        val routes = httpRoutes {
            post("/data") {
                consumes(MediaType.ApplicationJson)
                handle { GenericHttpResponse(201, emptyMap(), null) }
            }
        }
        assertTrue(routes[0].endpoint.consumes.contains(MediaType.ApplicationJson))
    }

    @Test
    fun builderSetsProduces() = runTest {
        val routes = httpRoutes {
            get("/data") {
                produces(MediaType.ApplicationJson, MediaType.TextPlain)
                handle { GenericHttpResponse(200, emptyMap(), null) }
            }
        }
        assertEquals(2, routes[0].endpoint.produces.size)
        assertTrue(routes[0].endpoint.produces.contains(MediaType.ApplicationJson))
    }

    @Test
    fun builderSetsOperationId() = runTest {
        val routes = httpRoutes {
            get("/users") {
                operationId("listUsers")
                handle { GenericHttpResponse(200, emptyMap(), null) }
            }
        }
        assertEquals("listUsers", routes[0].endpoint.operationId)
    }

    @Test
    fun builderSetsTags() = runTest {
        val routes = httpRoutes {
            get("/users") {
                tags("users", "admin")
                handle { GenericHttpResponse(200, emptyMap(), null) }
            }
        }
        assertEquals(2, routes[0].endpoint.tags.size)
        assertTrue(routes[0].endpoint.tags.contains("users"))
        assertTrue(routes[0].endpoint.tags.contains("admin"))
    }

    @Test
    fun builderSetsSummary() = runTest {
        val routes = httpRoutes {
            get("/users") {
                summary("List all users")
                handle { GenericHttpResponse(200, emptyMap(), null) }
            }
        }
        assertEquals("List all users", routes[0].endpoint.summary)
    }

    @Test
    fun builderRequiresHandler() {
        assertFailsWith<IllegalArgumentException> {
            httpRoutes {
                get("/users") {
                    // Missing handle { ... }
                }
            }
        }
    }

    @Test
    fun handlerIsExecuted() = runTest {
        val routes = httpRoutes {
            get("/test") {
                handle { request ->
                    GenericHttpResponse(statusCode = 200, body = "Path: ${request.path}")
                }
            }
        }
        val request = GenericHttpRequest(method = "GET", path = "/test")
        val response = routes[0].handler(request)
        assertEquals("Path: /test", response.body)
    }
}
