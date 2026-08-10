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
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.UserContextLogManager
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.core.defaults.context.JwtClaimsInput
import com.sphereon.core.defaults.context.markValidated
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.context.PrincipalType
import com.sphereon.ktor.server.inject.resolver.PrincipalResolver
import com.sphereon.ktor.server.inject.resolver.FixedTenantResolver
import com.sphereon.ktor.server.inject.resolver.TenantResolver
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test suite for the KotlinInject Ktor plugin.
 */
class KotlinInjectPluginTest {
    @Test
    fun `cors preflight bypasses tenant and principal session resolution`() =
        testApplication {
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app",
                    profile = "test",
                    version = "1.0.0",
                )
            var tenantResolutionAttempts = 0

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
                tenantResolver =
                    object : TenantResolver {
                        override fun resolve(call: ApplicationCall): com.sphereon.di.context.TenantInput {
                            tenantResolutionAttempts += 1
                            error("Preflight must not resolve a tenant")
                        }
                    }
            }

            routing {
                options("/api/test") {
                    call.respondText("", status = HttpStatusCode.NoContent)
                }
            }

            val response =
                client.options("/api/test") {
                    header(HttpHeaders.Origin, "http://localhost:3002")
                    header("Access-Control-Request-Method", "GET")
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
            assertEquals(0, tenantResolutionAttempts)
        }

    @Test
    fun `public request stays anonymous and ignores spoofed identity header`() =
        testApplication {
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app",
                    profile = "test",
                    version = "1.0.0",
                )

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
                tenantResolver = FixedTenantResolver("public-route-tenant")
            }

            routing {
                get("/authorize") {
                    val context = call.userInstance.context
                    call.respondText("${context.principal}|${context.principalType}")
                }
            }

            val response =
                client.get("/authorize") {
                    header("X-User-ID", "attacker-controlled")
                    header("X-Principal-ID", "attacker-controlled")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                "${IdentityConstants.ANONYMOUS_PRINCIPAL_ID}|${PrincipalType.ANONYMOUS}",
                response.bodyAsText(),
            )
        }

    @Test
    fun `authenticated principal comes from validated JWT claims attribute`() =
        testApplication {
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app",
                    profile = "test",
                    version = "1.0.0",
                )

            install(ValidatedJwtTestPlugin)
            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
                tenantResolver = FixedTenantResolver("tenant-from-validated-jwt")
            }

            routing {
                get("/authenticated") {
                    val context = call.userInstance.context
                    call.respondText("${context.principal}|${context.principalType}")
                }
            }

            val response = client.get("/authenticated")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("jwt-user|${PrincipalType.USER}", response.bodyAsText())
        }

    @Test
    fun `validated client credentials identity creates a workload context`() =
        testApplication {
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app",
                    profile = "test",
                    version = "1.0.0",
                )

            install(ValidatedWorkloadJwtTestPlugin)
            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
                tenantResolver = FixedTenantResolver("workload-tenant")
            }

            routing {
                get("/workload") {
                    val context = call.userInstance.context
                    call.respondText("${context.principal}|${context.principalType}")
                }
            }

            val response = client.get("/workload")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("issuer-service:workload-tenant|${PrincipalType.WORKLOAD}", response.bodyAsText())
        }

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
                tenantResolver = FixedTenantResolver("test-tenant")
                principalResolver = FixedPrincipalResolver("test-user")
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
                tenantResolver = FixedTenantResolver("test-tenant")
                principalResolver = FixedPrincipalResolver("test-user")
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
                tenantResolver = FixedTenantResolver("custom-tenant")
                principalResolver = FixedPrincipalResolver("custom-user")
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
                tenantResolver = FixedTenantResolver("test-tenant")
                principalResolver = FixedPrincipalResolver("test-user")
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
                tenantResolver = FixedTenantResolver("test-tenant")
                principalResolver = FixedPrincipalResolver("test-user")
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
                tenantResolver = FixedTenantResolver("test-tenant")
                principalResolver = FixedPrincipalResolver("test-user")
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
                tenantResolver = FixedTenantResolver("test-tenant")
                principalResolver = FixedPrincipalResolver("test-user")
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
    fun `test request sessions are destroyed after response`() =
        testApplication {
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app",
                    profile = "test",
                    version = "1.0.0",
                )

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
                tenantResolver = FixedTenantResolver("test-tenant")
                principalResolver = FixedPrincipalResolver("test-user")
            }

            routing {
                get("/session-lifecycle") {
                    val sessionsDuringRequest = call.userInstance.sessionContextManager.listIds()
                    call.respondText(
                        "SessionId: ${call.sessionInstance.sessionId}, Count: ${sessionsDuringRequest.size}",
                        ContentType.Text.Plain,
                    )
                }
            }

            fun requestUserContext() =
                appGraph.userContextManager.createOrGetFromInputs(
                    tenantInput = DefaultTenantInputString("test-tenant"),
                    principalInput = DefaultPrincipalInputString("test-user"),
                    makeActive = false,
                )

            repeat(3) {
                val response =
                    client.get("/session-lifecycle") {
                        header("X-User-ID", "test-user")
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.bodyAsText().contains("Count: 1"))
                assertEquals(emptySet(), requestUserContext().sessionContextManager.listIds())
            }
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
                tenantResolver = FixedTenantResolver("acme-corp")
                principalResolver = FixedPrincipalResolver("john.doe")
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
                tenantResolver = FixedTenantResolver("tenant1")
                principalResolver = FixedPrincipalResolver("user1")
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

            // A second request with spoofed identity headers must retain JWT-resolved identity.
            val response2 =
                client.get("/check-user-context") {
                    header("X-Tenant-ID", "tenant2")
                    header("X-User-ID", "user2")
                }
            assertEquals(HttpStatusCode.OK, response2.status)
            val body2 = response2.bodyAsText()
            assertTrue(body2.contains("tenant1"))

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

    /**
     * `respondWithGenericResponse` must route binary [GenericHttpResponse] payloads through
     * Ktor's `respondBytes` so non-UTF-8 byte sequences (PNG, PDF, etc.) round-trip
     * byte-identical. Synthetic JPEG SOI marker bytes (0xFF 0xD8 0xFF 0xE0) are not valid
     * UTF-8, so a `decodeToString()` round-trip would corrupt them.
     */
    @Test
    fun respondWithGenericResponse_withBinaryBody_roundTripsBytesIdentically() =
        testApplication {
            val jpegSoi = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

            routing {
                get("/binary-asset") {
                    val response =
                        GenericHttpResponse.withBinaryBody(
                            statusCode = 200,
                            body = jpegSoi,
                            headers = mapOf("Content-Type" to "image/jpeg"),
                        )
                    call.respondWithGenericResponse(response)
                }
            }

            val response = client.get("/binary-asset")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Image.JPEG, response.contentType())
            val received = response.readRawBytes()
            assertContentEquals(
                jpegSoi,
                received,
                "binary body MUST round-trip byte-identical through the Ktor adapter; " +
                    "corruption indicates a `decodeToString()` round-trip on the response path",
            )
        }
}

private val ValidatedJwtTestPlugin =
    createApplicationPlugin("ValidatedJwtTestPlugin") {
        onCall { call ->
            call.attributes.put(
                ValidatedJwtClaimsAttribute,
                JwtClaimsInput(
                    claims =
                        mapOf(
                            "sub" to JsonPrimitive("jwt-user"),
                            "tenant_id" to JsonPrimitive("tenant-from-validated-jwt"),
                        ),
                    rawToken = "header.payload.signature",
                ).markValidated(),
            )
        }
    }

private val ValidatedWorkloadJwtTestPlugin =
    createApplicationPlugin("ValidatedWorkloadJwtTestPlugin") {
        onCall { call ->
            call.attributes.put(
                ValidatedJwtClaimsAttribute,
                JwtClaimsInput(
                    claims =
                        mapOf(
                            "sub" to JsonPrimitive("issuer-service:workload-tenant"),
                            "azp" to JsonPrimitive("issuer-service:workload-tenant"),
                            "client_id" to JsonPrimitive("issuer-service:workload-tenant"),
                            "tenant_id" to JsonPrimitive("workload-tenant"),
                        ),
                    rawToken = "header.payload.signature",
                ).markValidated(),
            )
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

private class FixedPrincipalResolver(
    private val principalId: String,
) : PrincipalResolver {
    override fun resolve(call: ApplicationCall) = DefaultPrincipalInputString(principalId)
}
