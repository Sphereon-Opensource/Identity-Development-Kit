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

package com.sphereon.ktor.server.inject

import com.sphereon.core.api.conf.AppConfigEnvironment
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.RoutedHttpAdapter
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpRoute
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.describe.httpRoutes
import com.sphereon.core.api.http.jsonResponse
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.UserContextLogManager
import com.sphereon.ktor.server.inject.resolver.DefaultPrincipalResolver
import com.sphereon.ktor.server.inject.resolver.DefaultTenantResolver
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test suite for the KotlinInject Ktor plugin.
 */
class KotlinInjectPluginTest {
    @Test
    fun `test plugin installation`() =
        testApplication {
            // Create a test app graph
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app",
                    profile = "test",
                    version = "1.0.0",
                )

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
            }

            // Verify the plugin is installed by making a request
            routing {
                get("/test-install") {
                    call.respondText("OK")
                }
            }

            val response = client.get("/test-install")
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `test request context resolution`() =
        testApplication {
            // Create a test app graph
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app",
                    profile = "test",
                    version = "1.0.0",
                )

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
            }

            routing {
                get("/test") {
                    val userInstance = call.userInstance
                    val sessionInstance = call.sessionInstance

                    call.respondText(
                        "Context: ${userInstance.contextId}, Session: ${sessionInstance.sessionId}",
                        ContentType.Text.Plain,
                    )
                }
            }

            // Make a request with custom headers
            val response =
                client.get("/test") {
                    header("X-Tenant-ID", "test-tenant")
                    header("X-User-ID", "test-user")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertNotNull(body)
            assertTrue(body.contains("Context:"))
            assertTrue(body.contains("Session:"))
        }

    @Test
    fun `test custom resolvers`() =
        testApplication {
            // Create a test app graph
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app",
                    profile = "test",
                    version = "1.0.0",
                )

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
                tenantResolver = DefaultTenantResolver("X-Custom-Tenant")
                principalResolver = DefaultPrincipalResolver("X-Custom-User")
            }

            routing {
                get("/test") {
                    val userInstance = call.userInstance
                    val tenant = userInstance.context.tenant
                    val principal = userInstance.context.principal
                    call.respondText(
                        "Tenant: $tenant, Principal: $principal",
                        ContentType.Text.Plain,
                    )
                }
            }

            // Make a request with custom headers
            val response =
                client.get("/test") {
                    header("X-Custom-Tenant", "custom-tenant")
                    header("X-Custom-User", "custom-user")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("custom-tenant"))
            assertTrue(body.contains("custom-user"))
        }

    @Test
    fun `test app graph access`() =
        testApplication {
            // Create a test app graph
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app-123",
                    profile = "test",
                    version = "1.0.0",
                )

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
            }

            routing {
                get("/app-info") {
                    val app = call.appGraph
                    call.respondText("AppId: ${app.appId}", ContentType.Text.Plain)
                }
            }

            val response = client.get("/app-info")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("test-app-123"))
        }

    @Test
    fun `test get app scoped service`() =
        testApplication {
            // Create a test app graph
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app",
                    profile = "test",
                    version = "1.0.0",
                )

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
            }

            routing {
                get("/app-config") {
                    // Access app-scoped service via extension function
                    val configEnv = call.getAppService<AppConfigEnvironment>()
                    call.respondText("AppName: ${configEnv.getAppName()}", ContentType.Text.Plain)
                }
            }

            val response = client.get("/app-config")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("test-app"))
        }

    @Test
    fun `test get user scoped service`() =
        testApplication {
            // Create a test app graph
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app",
                    profile = "test",
                    version = "1.0.0",
                )

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
            }

            routing {
                get("/user-log") {
                    // Access user-scoped service via extension function
                    val userLogManager = call.getUserService<UserContextLogManager>()
                    userLogManager.withTag("USER-LOG").info("Hello World")
                    call.respondText("UserLogManager: ${userLogManager::class.simpleName}", ContentType.Text.Plain)
                }
            }

            // Make a request with tenant/user headers
            val response =
                client.get("/user-log") {
                    header("X-Tenant-ID", "test-tenant")
                    header("X-User-ID", "test-user")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("UserLogManager"))
        }

    @Test
    fun `test get session scoped service`() =
        testApplication {
            // Create a test app graph
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app",
                    profile = "test",
                    version = "1.0.0",
                )

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
            }

            routing {
                get("/session-log") {
                    // Access session-scoped service via extension function
                    val sessionLogManager = call.getSessionService<SessionLogManager>()
                    val sessionInstance = call.sessionInstance
                    call.respondText(
                        "SessionLogManager: ${sessionLogManager::class.simpleName}, SessionId: ${sessionInstance.sessionId}",
                        ContentType.Text.Plain,
                    )
                }
            }

            // Make a request with tenant/user headers
            val response =
                client.get("/session-log") {
                    header("X-Tenant-ID", "test-tenant")
                    header("X-User-ID", "test-user")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("SessionLogManager"))
            assertTrue(body.contains("SessionId:"))
        }

    @Test
    fun `test multiple scoped services in single request`() =
        testApplication {
            // Create a test app graph
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "multi-scope-test",
                    profile = "test",
                    version = "1.0.0",
                )

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
            }

            routing {
                get("/multi-scope") {
                    // Access services from all three scopes
                    val appConfig = call.getAppService<AppConfigEnvironment>()
                    val userLogManager = call.getUserService<UserContextLogManager>()
                    val sessionLogManager = call.getSessionService<SessionLogManager>()

                    val userInstance = call.userInstance
                    val sessionInstance = call.sessionInstance

                    call.respondText(
                        """
                        AppName: ${appConfig.getAppName()}
                        Tenant: ${userInstance.context.tenant}
                        Principal: ${userInstance.context.principal}
                        SessionId: ${sessionInstance.sessionId}
                        UserLogManager: ${userLogManager::class.simpleName}
                        SessionLogManager: ${sessionLogManager::class.simpleName}
                        """.trimIndent(),
                        ContentType.Text.Plain,
                    )
                }
            }

            // Make a request with tenant/user headers
            val response =
                client.get("/multi-scope") {
                    header("X-Tenant-ID", "acme-corp")
                    header("X-User-ID", "john.doe")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("multi-scope-test"))
            assertTrue(body.contains("acme-corp"))
            assertTrue(body.contains("john.doe"))
            assertTrue(body.contains("UserLogManager"))
            assertTrue(body.contains("SessionLogManager"))
        }

    @Test
    fun `test service resolution across multiple requests`() =
        testApplication {
            // Create a test app graph
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app",
                    profile = "test",
                    version = "1.0.0",
                )

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
            }

            routing {
                get("/check-user-context") {
                    val userInstance = call.userInstance
                    call.respondText(
                        "Context: ${userInstance.contextId}",
                        ContentType.Text.Plain,
                    )
                }
            }

            // First request with tenant1/user1
            val response1 =
                client.get("/check-user-context") {
                    header("X-Tenant-ID", "tenant1")
                    header("X-User-ID", "user1")
                }
            assertEquals(HttpStatusCode.OK, response1.status)
            val body1 = response1.bodyAsText()
            assertTrue(body1.contains("tenant1"))

            // Second request with tenant2/user2 - should get different context
            val response2 =
                client.get("/check-user-context") {
                    header("X-Tenant-ID", "tenant2")
                    header("X-User-ID", "user2")
                }
            assertEquals(HttpStatusCode.OK, response2.status)
            val body2 = response2.bodyAsText()
            assertTrue(body2.contains("tenant2"))

            // Third request with same tenant1/user1 - should reuse context
            val response3 =
                client.get("/check-user-context") {
                    header("X-Tenant-ID", "tenant1")
                    header("X-User-ID", "user1")
                }
            assertEquals(HttpStatusCode.OK, response3.status)
            val body3 = response3.bodyAsText()
            assertTrue(body3.contains("tenant1"))
        }

    @Test
    fun `test routed http adapter directly`() =
        testApplication {
            // Create an instance of the test HTTP adapter
            val testAdapter = TestHttpAdapter()

            // The dispatcher normally strips serverPrefix before calling the adapter.
            // Since we're testing without the dispatcher, we simulate this by
            // stripping the serverPrefix from the request path before calling handleRequest.
            fun normalizeRequest(
                request: GenericHttpRequest,
                serverPrefix: String,
            ): GenericHttpRequest {
                val normalizedPath = request.path.removePrefix(serverPrefix)
                return request.copy(path = normalizedPath)
            }

            // Manually wire the adapter to Ktor routes
            // This demonstrates how RoutedHttpAdapter handles requests
            routing {
                route("/api/test") {
                    // Wire each route from the adapter
                    get("/items") {
                        val genericRequest = call.toGenericHttpRequest()
                        // Simulate dispatcher stripping serverPrefix ("/api")
                        val normalizedRequest = normalizeRequest(genericRequest, "/api")
                        val response = testAdapter.handleRequest(normalizedRequest)
                        call.respondWithGenericResponse(response)
                    }

                    get("/items/{itemId}") {
                        val genericRequest = call.toGenericHttpRequest()
                        val normalizedRequest = normalizeRequest(genericRequest, "/api")
                        val response = testAdapter.handleRequest(normalizedRequest)
                        call.respondWithGenericResponse(response)
                    }

                    post("/items") {
                        val genericRequest = call.toGenericHttpRequest()
                        val normalizedRequest = normalizeRequest(genericRequest, "/api")
                        val response = testAdapter.handleRequest(normalizedRequest)
                        call.respondWithGenericResponse(response)
                    }
                }
            }

            // Test GET /api/test/items
            val listResponse = client.get("/api/test/items")
            assertEquals(HttpStatusCode.OK, listResponse.status)
            val listBody = listResponse.bodyAsText()
            assertTrue(listBody.contains("items"))
            assertTrue(listBody.contains("item1"))

            // Test GET /api/test/items/{itemId}
            val getResponse = client.get("/api/test/items/my-item-123")
            assertEquals(HttpStatusCode.OK, getResponse.status)
            val getBody = getResponse.bodyAsText()
            assertTrue(getBody.contains("my-item-123"))
            assertTrue(getBody.contains("Test Item"))

            // Test POST /api/test/items
            val postResponse =
                client.post("/api/test/items") {
                    header("Content-Type", "application/json")
                    setBody("""{"name": "New Item"}""")
                }
            assertEquals(HttpStatusCode.Created, postResponse.status)
            val postBody = postResponse.bodyAsText()
            assertTrue(postBody.contains("created"))
        }

    @Test
    fun `test routed http adapter with canHandle routing`() =
        testApplication {
            // Create an instance of the test HTTP adapter
            val testAdapter = TestHttpAdapter()

            // The dispatcher normally strips serverPrefix before calling the adapter.
            fun normalizeRequest(
                request: GenericHttpRequest,
                serverPrefix: String,
            ): GenericHttpRequest {
                val normalizedPath = request.path.removePrefix(serverPrefix)
                return request.copy(path = normalizedPath)
            }

            // Wire the adapter using its canHandle method for dynamic routing
            // This is closer to how the Universal HTTP Adapter dispatcher works
            routing {
                route("/api/test/{...}") {
                    handle {
                        val genericRequest = call.toGenericHttpRequest()
                        // Simulate dispatcher stripping serverPrefix
                        val normalizedRequest = normalizeRequest(genericRequest, "/api")

                        // Check if the adapter can handle this request
                        if (testAdapter.canHandle(normalizedRequest)) {
                            val response = testAdapter.handleRequest(normalizedRequest)
                            call.respondWithGenericResponse(response)
                        } else {
                            call.respondText(
                                "Not found: ${normalizedRequest.method} ${normalizedRequest.path}",
                                status = HttpStatusCode.NotFound,
                            )
                        }
                    }
                }
            }

            // Test GET /api/test/items (adapter should handle)
            val listResponse = client.get("/api/test/items")
            assertEquals(HttpStatusCode.OK, listResponse.status)
            assertTrue(listResponse.bodyAsText().contains("items"))

            // Test GET /api/test/items/item-456 (adapter should handle)
            val getResponse = client.get("/api/test/items/item-456")
            assertEquals(HttpStatusCode.OK, getResponse.status)
            assertTrue(getResponse.bodyAsText().contains("item-456"))

            // Test unknown route (adapter should not handle)
            val unknownResponse = client.get("/api/test/unknown")
            assertEquals(HttpStatusCode.NotFound, unknownResponse.status)
        }

    @Test
    fun `test http adapter describe returns correct metadata`() {
        val adapter = TestHttpAdapter()
        val description = adapter.describe()

        // Verify adapter metadata
        assertEquals(TestHttpAdapter.ID, description.id)
        assertEquals("/api", description.mount.serverPrefix)
        assertEquals("/test", description.mount.adapterBasePath)

        // Verify endpoints
        assertEquals(3, description.endpoints.size)

        val endpoints = description.endpoints.associateBy { it.operationId }
        assertNotNull(endpoints["listTestItems"])
        assertNotNull(endpoints["getTestItem"])
        assertNotNull(endpoints["createTestItem"])

        // Verify GET /test/items endpoint (basePath + relative route, no serverPrefix)
        val listEndpoint = endpoints["listTestItems"]!!
        assertEquals(com.sphereon.core.api.http.describe.HttpMethod.GET, listEndpoint.method)
        assertEquals("/test/items", listEndpoint.pathPattern)
        assertTrue(listEndpoint.produces.contains(MediaType.ApplicationJson))

        // Verify GET /test/items/{itemId} endpoint
        val getEndpoint = endpoints["getTestItem"]!!
        assertEquals(com.sphereon.core.api.http.describe.HttpMethod.GET, getEndpoint.method)
        assertEquals("/test/items/{itemId}", getEndpoint.pathPattern)

        // Verify POST /test/items endpoint
        val postEndpoint = endpoints["createTestItem"]!!
        assertEquals(com.sphereon.core.api.http.describe.HttpMethod.POST, postEndpoint.method)
        assertTrue(postEndpoint.consumes.contains(MediaType.ApplicationJson))
    }
}

/**
 * Test HTTP adapter for Universal HTTP Adapter integration tests.
 *
 * Note: This is a simple test adapter without DI annotations.
 * In production, adapters would use:
 * - @Inject
 * - @Named(MyAdapter.ID)
 * - @SingleIn(SessionScope::class)
 * - @ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
 */
private class TestHttpAdapter : RoutedHttpAdapter() {
    companion object {
        const val ID = "TEST-API"
    }

    override val id: String = ID

    override val mount: HttpAdapterMount =
        HttpAdapterMount(
            serverPrefix = "/api",
            adapterBasePath = "/test",
        )

    // Routes are relative to the mount (serverPrefix + adapterBasePath = /api/test)
    // The mount prefix is automatically prepended when matching requests
    override val routes: List<HttpRoute> =
        httpRoutes {
            get("/items") {
                operationId("listTestItems")
                produces(MediaType.ApplicationJson)
                handle { request -> handleListItems(request) }
            }

            get("/items/{itemId}") {
                operationId("getTestItem")
                produces(MediaType.ApplicationJson)
                handle { request -> handleGetItem(request) }
            }

            post("/items") {
                operationId("createTestItem")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { request -> handleCreateItem(request) }
            }
        }

    private suspend fun handleListItems(request: GenericHttpRequest): GenericHttpResponse = jsonResponse(200, """{"items": ["item1", "item2", "item3"]}""")

    private suspend fun handleGetItem(request: GenericHttpRequest): GenericHttpResponse {
        // Use normalized path pattern (basePath + relative route, no serverPrefix)
        val req = request.withExtractedParams("/test/items/{itemId}")
        val itemId = req.pathParams["itemId"] ?: "unknown"
        return jsonResponse(200, """{"id": "$itemId", "name": "Test Item"}""")
    }

    private suspend fun handleCreateItem(request: GenericHttpRequest): GenericHttpResponse {
        val body = request.body ?: "{}"
        return jsonResponse(201, """{"created": true, "body": $body}""")
    }
}
