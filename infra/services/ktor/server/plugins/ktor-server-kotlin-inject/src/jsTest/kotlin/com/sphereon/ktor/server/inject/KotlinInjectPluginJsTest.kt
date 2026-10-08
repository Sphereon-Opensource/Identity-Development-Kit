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
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.UserContextLogManager
import com.sphereon.ktor.server.inject.resolver.FixedTenantResolver
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JavaScript test suite for the KotlinInject Ktor plugin.
 *
 * Tests verify that the plugin works correctly on the JS platform with
 * dynamic service resolution.
 */
class KotlinInjectPluginJsTest {
    companion object {
        private var initialized = false

        /**
         * Workaround for Node.js 'os' module import issue in Ktor test framework.
         *
         * The first call to testApplication() triggers initialization that tries to import
         * the Node.js 'os' module, which fails. Subsequent calls work fine because the
         * initialization is cached/already done.
         *
         * This method triggers that initialization once before any tests run, catching
         * and ignoring the error, so that all actual tests can pass.
         */
        fun initializeKtorTestFramework() {
            if (!initialized) {
                try {
                    // Trigger Ktor test framework initialization
                    // This will fail with the 'os' module error, but we catch it
                    testApplication {
                        // Empty test application just to trigger initialization
                        val appGraph =
                            createTestAppGraph(
                                application = Unit,
                                appId = "init",
                                profile = "test",
                                version = "1.0.0",
                            )
                        install(KotlinInjectPlugin) {
                            this.appGraph = appGraph
                            tenantResolver = FixedTenantResolver("init")
                        }
                    }
                    initialized = true
                } catch (expected: Exception) {
                    // Expected: Node.js 'os' module error
                    // Ignore it - the initialization is done enough for subsequent tests
                    console.log("Ktor test framework initialization triggered Node.js 'os' module error (expected): ${expected.message}")
                    initialized = true
                }
            }
        }
    }

    @BeforeTest
    fun setup() {
        // Trigger initialization before each test
        // Only the first call will actually do anything
        initializeKtorTestFramework()
    }

    @Test
    fun testPluginInstallationJS() =
        testApplication {
            // Create a test app graph
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app-js",
                    profile = "test",
                    version = "1.0.0",
                )

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
                tenantResolver = FixedTenantResolver("test-tenant")
            }

            // Verify the plugin is installed by making a request
            routing {
                get("/test-install") {
                    call.respondText("OK from JS")
                }
            }

            val response = client.get("/test-install")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("OK from JS"))
        }

    @Test
    fun testRequestContextResolutionJS() =
        testApplication {
            // Create a test app graph
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app-js",
                    profile = "test",
                    version = "1.0.0",
                )

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
                tenantResolver = FixedTenantResolver("test-tenant")
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
                    header("X-Tenant-ID", "test-tenant-js")
                    header("X-User-ID", "test-user-js")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertNotNull(body)
            assertTrue(body.contains("Context:"))
            assertTrue(body.contains("Session:"))
        }

    @Test
    fun testAppGraphAccessJS() =
        testApplication {
            // Create a test app graph
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app-js-123",
                    profile = "test",
                    version = "1.0.0",
                )

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
                tenantResolver = FixedTenantResolver("test-tenant")
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
            assertTrue(body.contains("test-app-js-123"))
        }

    @Test
    fun testGetAppScopedServiceJS() =
        testApplication {
            // Create a test app graph
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app-js-services",
                    profile = "test",
                    version = "1.0.0",
                )

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
                tenantResolver = FixedTenantResolver("test-tenant")
            }

            routing {
                get("/app-config") {
                    // Access app-scoped service via extension function
                    // On JS, this uses dynamic property access
                    val configEnv = call.getAppService<AppConfigEnvironment>()
                    call.respondText("AppName: ${configEnv.getAppName()}", ContentType.Text.Plain)
                }
            }

            val response = client.get("/app-config")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("test-app-js-services"))
        }

    @Test
    fun testGetUserScopedServiceJS() =
        testApplication {
            // Create a test app graph
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app-js",
                    profile = "test",
                    version = "1.0.0",
                )

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
                tenantResolver = FixedTenantResolver("test-tenant")
            }

            routing {
                get("/user-log") {
                    // Access user-scoped service via extension function
                    val userLogManager = call.getUserService<UserContextLogManager>()
                    userLogManager.withTag("USER-LOG-JS").info("Hello from JS")
                    call.respondText("UserLogManager: ${userLogManager::class.simpleName}", ContentType.Text.Plain)
                }
            }

            // Make a request with tenant/user headers
            val response =
                client.get("/user-log") {
                    header("X-Tenant-ID", "test-tenant-js")
                    header("X-User-ID", "test-user-js")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("UserLogManager"))
        }

    @Test
    fun testGetSessionScopedServiceJS() =
        testApplication {
            // Create a test app graph
            val appGraph =
                createTestAppGraph(
                    application = Unit,
                    appId = "test-app-js",
                    profile = "test",
                    version = "1.0.0",
                )

            install(KotlinInjectPlugin) {
                this.appGraph = appGraph
                tenantResolver = FixedTenantResolver("test-tenant")
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
                    header("X-Tenant-ID", "test-tenant-js")
                    header("X-User-ID", "test-user-js")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("SessionLogManager"))
            assertTrue(body.contains("SessionId:"))
        }
}
