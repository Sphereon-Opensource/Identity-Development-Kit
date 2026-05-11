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

package com.sphereon.core.api.http.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.http.GenericHttpBody
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.response.errorResponse
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [CommandBackedHttpAdapter] and [HttpEndpointCommand].
 *
 * These tests verify the command-backed adapter pattern works correctly:
 * - Endpoint commands match requests based on descriptors
 * - Adapters route to the correct endpoint command
 * - Disabled endpoints are excluded from routing and metadata
 */
class CommandBackedHttpAdapterTest {
    @Test
    fun endpointCommandMatchesRequestBasedOnEndpointDescriptor() =
        runTest {
            val endpoint =
                TestEndpointCommand(
                    id = "test.get",
                    endpoint =
                        HttpEndpointDescriptor(
                            method = HttpMethod.GET,
                            pathPattern = "/{id}",
                            produces = setOf(MediaType.ApplicationJson),
                        ),
                )

            // Should match (relative path after adapter strips base path)
            val matchingRequest = GenericHttpRequest(method = "GET", path = "/123")
            assertTrue(endpoint.supports(matchingRequest), "Should match GET /{id}")

            // Should not match - wrong method
            val wrongMethod = GenericHttpRequest(method = "POST", path = "/123")
            assertFalse(endpoint.supports(wrongMethod), "Should not match POST /{id}")

            // Should not match - wrong path structure
            val wrongPath = GenericHttpRequest(method = "GET", path = "/users/123")
            assertFalse(endpoint.supports(wrongPath), "Should not match GET /users/{id}")
        }

    @Test
    fun adapterRoutesToCorrectEndpointCommand() =
        runTest {
            val adapter = TestAdapter()

            // Test GET /items/{id} - full path with adapter base path
            val getResponse = adapter.handleRequest(GenericHttpRequest(method = "GET", path = "/items/42"))
            assertEquals(200, getResponse.statusCode)
            assertEquals("""{"item":"42"}""", getResponse.body)

            // Test POST /items - full path with adapter base path (endpoint pattern is just "/")
            val postResponse =
                adapter.handleRequest(
                    GenericHttpRequest(
                        method = "POST",
                        path = "/items",
                        bodyContent = GenericHttpBody.Text("""{"name":"test"}"""),
                    ),
                )
            assertEquals(201, postResponse.statusCode)
            assertEquals("""{"created":true}""", postResponse.body)

            // Test DELETE /items/{id} - full path with adapter base path
            val deleteResponse = adapter.handleRequest(GenericHttpRequest(method = "DELETE", path = "/items/42"))
            assertEquals(204, deleteResponse.statusCode)
        }

    @Test
    fun adapterReturns404ForUnmatchedRequest() =
        runTest {
            val adapter = TestAdapter()

            val response = adapter.handleRequest(GenericHttpRequest(method = "PATCH", path = "/items/42"))
            assertEquals(404, response.statusCode)
            assertTrue(response.body?.contains("Not found") == true)
        }

    @Test
    fun adapterDescribeReturnsCorrectMetadata() {
        val adapter = TestAdapter()

        val description = adapter.describe()
        assertEquals("test-adapter", description.id)
        assertEquals("/api", description.mount.serverPrefix)
        assertEquals("/items", description.mount.adapterBasePath)
        assertEquals(3, description.endpoints.size)

        val endpoints = description.endpoints.sortedBy { it.operationId }
        assertEquals("createItem", endpoints[0].operationId)
        assertEquals("deleteItem", endpoints[1].operationId)
        assertEquals("getItem", endpoints[2].operationId)
    }

    @Test
    fun disabledEndpointCommandIsExcludedFromRouting() =
        runTest {
            val adapter = TestAdapterWithDisabledEndpoint()

            // The GET endpoint is disabled
            val response = adapter.handleRequest(GenericHttpRequest(method = "GET", path = "/items/42"))
            assertEquals(404, response.statusCode)

            // POST still works
            val postResponse =
                adapter.handleRequest(
                    GenericHttpRequest(
                        method = "POST",
                        path = "/items",
                        bodyContent = GenericHttpBody.Text("""{"name":"test"}"""),
                    ),
                )
            assertEquals(201, postResponse.statusCode)
        }

    @Test
    fun disabledEndpointNotIncludedInDescribe() {
        val adapter = TestAdapterWithDisabledEndpoint()

        val description = adapter.describe()
        assertEquals(1, description.endpoints.size)
        assertEquals("createItem", description.endpoints.first().operationId)
    }

    @Test
    fun endpointCommandSupportsMethodReturnsFalseForNonRequestArgs() =
        runTest {
            val endpoint =
                TestEndpointCommand(
                    id = "test.get",
                    endpoint =
                        HttpEndpointDescriptor(
                            method = HttpMethod.GET,
                            pathPattern = "/{id}",
                        ),
                )

            // Non-request arg should not be supported
            assertFalse(endpoint.supports("not a request"))
            assertFalse(endpoint.supports(123))
            assertFalse(endpoint.supports(mapOf("key" to "value")))
        }

    // ========== Test fixtures ==========

    /**
     * Simple endpoint command for testing.
     */
    private class TestEndpointCommand(
        override val id: String,
        override val endpoint: HttpEndpointDescriptor,
        override val isEnabled: Boolean = true,
        private val responseProvider: suspend (GenericHttpRequest) -> GenericHttpResponse = { jsonResponse(200, "{}") },
    ) : HttpEndpointCommand {
        override suspend fun execute(args: GenericHttpRequest): IdkResult<GenericHttpResponse, IdkError> = Ok(responseProvider(args))
    }

    /**
     * Test adapter with multiple endpoint commands.
     *
     * Note: Endpoint patterns are RELATIVE to the adapter's base path.
     * The adapter strips its base path before routing to endpoint commands.
     */
    private class TestAdapter :
        TestCommandBackedAdapter(
            id = "test-adapter",
            mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/items"),
        ) {
        override val endpointCommands: List<HttpEndpointCommand> =
            listOf(
                TestEndpointCommand(
                    id = "test.items.get",
                    endpoint =
                        HttpEndpointDescriptor(
                            method = HttpMethod.GET,
                            pathPattern = "/{id}",
                            produces = setOf(MediaType.ApplicationJson),
                            operationId = "getItem",
                        ),
                    responseProvider = { req ->
                        val id = req.withExtractedParams("/{id}").pathParams["id"]
                        jsonResponse(200, """{"item":"$id"}""")
                    },
                ),
                TestEndpointCommand(
                    id = "test.items.create",
                    endpoint =
                        HttpEndpointDescriptor(
                            method = HttpMethod.POST,
                            pathPattern = "/",
                            consumes = setOf(MediaType.ApplicationJson),
                            produces = setOf(MediaType.ApplicationJson),
                            operationId = "createItem",
                        ),
                    responseProvider = {
                        GenericHttpResponse(statusCode = 201, body = """{"created":true}""")
                    },
                ),
                TestEndpointCommand(
                    id = "test.items.delete",
                    endpoint =
                        HttpEndpointDescriptor(
                            method = HttpMethod.DELETE,
                            pathPattern = "/{id}",
                            operationId = "deleteItem",
                        ),
                    responseProvider = {
                        GenericHttpResponse(statusCode = 204)
                    },
                ),
            )
    }

    /**
     * Test adapter with one disabled endpoint.
     */
    private class TestAdapterWithDisabledEndpoint :
        TestCommandBackedAdapter(
            id = "test-adapter-disabled",
            mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/items"),
        ) {
        override val endpointCommands: List<HttpEndpointCommand> =
            listOf(
                TestEndpointCommand(
                    id = "test.items.get",
                    endpoint =
                        HttpEndpointDescriptor(
                            method = HttpMethod.GET,
                            pathPattern = "/{id}",
                            produces = setOf(MediaType.ApplicationJson),
                            operationId = "getItem",
                        ),
                    isEnabled = false, // Disabled!
                ),
                TestEndpointCommand(
                    id = "test.items.create",
                    endpoint =
                        HttpEndpointDescriptor(
                            method = HttpMethod.POST,
                            pathPattern = "/",
                            consumes = setOf(MediaType.ApplicationJson),
                            produces = setOf(MediaType.ApplicationJson),
                            operationId = "createItem",
                        ),
                    responseProvider = {
                        GenericHttpResponse(statusCode = 201, body = """{"created":true}""")
                    },
                ),
            )
    }

    private class NoOpSessionLogService(
        override val sessionContext: SessionContext = NoOpSessionContext,
    ) : SessionLogService {
        override val id: String = "test-http-log"
        override val isEnabled: Boolean = false
        override val scope = com.sphereon.core.api.context.IdkScope.SESSION
        override val logManager: SessionLogManager
            get() = throw NotImplementedError("Not needed for test")

        override suspend fun setConfig(config: com.sphereon.core.api.log.LoggerConfig): com.sphereon.core.api.log.LogService = this

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

        override fun toAsync(): AsyncLogService = throw NotImplementedError("Not needed for test")
    }

    private class NoOpContextConfig : ContextConfig {
        override val app: AppConfigService
            get() = throw NotImplementedError("Not needed for test")
        override val tenant: TenantConfigService
            get() = throw NotImplementedError("Not needed for test")
        override val principal: PrincipalConfigService
            get() = throw NotImplementedError("Not needed for test")

        override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("Not needed for test")
    }

    private class TestSessionExecution(
        override val sessionContext: SessionContext = NoOpSessionContext,
    ) : SessionExecution {
        override val sessionContextManager: SessionContextManager
            get() = throw NotImplementedError("Not needed for test")
        override val log: SessionLogService = NoOpSessionLogService(sessionContext)
        override val conf: ContextConfig = NoOpContextConfig()
    }
}

/**
 * Simplified CommandBackedHttpAdapter for testing that doesn't require SessionExecution.
 *
 * This variant handles the case where tests don't have a full DI context.
 * The real [CommandBackedHttpAdapter] requires SessionExecution for lifecycle integration.
 */
private abstract class TestCommandBackedAdapter(
    override val id: String,
    private val mount: HttpAdapterMount,
) : HttpAdapter {
    protected abstract val endpointCommands: List<HttpEndpointCommand>

    private val enabledEndpoints: List<HttpEndpointCommand>
        get() = endpointCommands.filter { it.isEnabled }

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = mount,
            endpoints = enabledEndpoints.map { it.endpoint },
            openApiHints = null,
        )

    override suspend fun handleRequest(request: GenericHttpRequest): GenericHttpResponse {
        // Strip adapter base path before routing to endpoint commands
        val relativeRequest = stripAdapterBasePath(request)
        val matchingEndpoints = enabledEndpoints.filter { it.supports(relativeRequest) }

        return when (matchingEndpoints.size) {
            0 -> {
                errorResponse(404, "Not found: ${request.method} ${request.path}")
            }

            1 -> {
                val result = matchingEndpoints.single().execute(relativeRequest)
                result.fold(
                    success = { it },
                    failure = { error ->
                        errorResponse(500, error.message.defaultMessage)
                    },
                )
            }

            else -> {
                errorResponse(500, "Multiple endpoints match request")
            }
        }
    }

    private fun stripAdapterBasePath(request: GenericHttpRequest): GenericHttpRequest {
        val basePath = mount.adapterBasePath
        if (basePath.isEmpty() || basePath == "/") return request
        val path = request.path
        return if (path.startsWith(basePath)) {
            val relativePath = path.removePrefix(basePath).let { if (it.isEmpty()) "/" else it }
            request.copy(path = relativePath)
        } else {
            request
        }
    }
}

/**
 * Additional tests for edge cases in the simplified TestCommandBackedAdapter.
 */
class AdditionalCommandBackedHttpAdapterTest {
    @Test
    fun testAdapterWithEmptyBasePathRoutes() =
        runTest {
            val adapter = TestAdapterWithEmptyBasePath()

            val response = adapter.handleRequest(GenericHttpRequest(method = "GET", path = "/123"))
            assertEquals(200, response.statusCode)
        }

    @Test
    fun testAdapterWithSlashBasePathRoutes() =
        runTest {
            val adapter = TestAdapterWithSlashBasePath()

            val response = adapter.handleRequest(GenericHttpRequest(method = "GET", path = "/items/456"))
            assertEquals(200, response.statusCode)
        }

    @Test
    fun testRequestPathNotMatchingBasePath() =
        runTest {
            val adapter = TestAdapterForPathStripping()

            // Request path doesn't start with the adapter base path
            val response = adapter.handleRequest(GenericHttpRequest(method = "GET", path = "/other/123"))
            assertEquals(404, response.statusCode)
        }

    /**
     * Test adapter with empty base path to cover the empty basePath branch.
     */
    private class TestAdapterWithEmptyBasePath :
        TestCommandBackedAdapterBase(
            id = "test-adapter-empty",
            mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = ""),
        ) {
        override val endpointCommands: List<HttpEndpointCommand> =
            listOf(
                SimpleTestEndpointCommand(
                    id = "test.get",
                    endpoint =
                        HttpEndpointDescriptor(
                            method = HttpMethod.GET,
                            pathPattern = "/{id}",
                            operationId = "getItem",
                        ),
                ),
            )
    }

    /**
     * Test adapter with "/" base path to cover that branch.
     */
    private class TestAdapterWithSlashBasePath :
        TestCommandBackedAdapterBase(
            id = "test-adapter-slash",
            mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/"),
        ) {
        override val endpointCommands: List<HttpEndpointCommand> =
            listOf(
                SimpleTestEndpointCommand(
                    id = "test.get",
                    endpoint =
                        HttpEndpointDescriptor(
                            method = HttpMethod.GET,
                            pathPattern = "/items/{id}",
                            operationId = "getItem",
                        ),
                ),
            )
    }

    /**
     * Test adapter for path stripping edge cases.
     */
    private class TestAdapterForPathStripping :
        TestCommandBackedAdapterBase(
            id = "test-adapter-strip",
            mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/items"),
        ) {
        override val endpointCommands: List<HttpEndpointCommand> =
            listOf(
                SimpleTestEndpointCommand(
                    id = "test.get",
                    endpoint =
                        HttpEndpointDescriptor(
                            method = HttpMethod.GET,
                            pathPattern = "/{id}",
                            operationId = "getItem",
                        ),
                ),
            )
    }

    private class SimpleTestEndpointCommand(
        override val id: String,
        override val endpoint: HttpEndpointDescriptor,
        override val isEnabled: Boolean = true,
    ) : HttpEndpointCommand {
        override suspend fun execute(args: GenericHttpRequest): IdkResult<GenericHttpResponse, IdkError> = Ok(jsonResponse(200, """{"success":true}"""))
    }
}

/**
 * Base class for test command-backed adapters.
 */
private abstract class TestCommandBackedAdapterBase(
    override val id: String,
    private val mount: HttpAdapterMount,
) : HttpAdapter {
    protected abstract val endpointCommands: List<HttpEndpointCommand>

    private val enabledEndpoints: List<HttpEndpointCommand>
        get() = endpointCommands.filter { it.isEnabled }

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = mount,
            endpoints =
                enabledEndpoints.map { endpoint ->
                    // Cover the empty basePath branch
                    val fullPathPattern =
                        if (mount.adapterBasePath.isEmpty() || mount.adapterBasePath == "/") {
                            endpoint.endpoint.pathPattern
                        } else {
                            mount.adapterBasePath + endpoint.endpoint.pathPattern
                        }
                    endpoint.endpoint.copy(pathPattern = fullPathPattern)
                },
            openApiHints = null,
        )

    override suspend fun handleRequest(request: GenericHttpRequest): GenericHttpResponse {
        val relativeRequest = stripAdapterBasePath(request)
        val matchingEndpoints = enabledEndpoints.filter { it.supports(relativeRequest) }

        return when (matchingEndpoints.size) {
            0 -> {
                errorResponse(404, "Not found: ${request.method} ${request.path}")
            }

            1 -> {
                val result = matchingEndpoints.single().execute(relativeRequest)
                result.fold(
                    success = { it },
                    failure = { error ->
                        errorResponse(500, error.message.defaultMessage)
                    },
                )
            }

            else -> {
                errorResponse(500, "Multiple endpoints match request")
            }
        }
    }

    private fun stripAdapterBasePath(request: GenericHttpRequest): GenericHttpRequest {
        val basePath = mount.adapterBasePath
        if (basePath.isEmpty() || basePath == "/") return request
        val path = request.path
        return if (path.startsWith(basePath)) {
            val relativePath = path.removePrefix(basePath).let { if (it.isEmpty()) "/" else it }
            request.copy(path = relativePath)
        } else {
            request
        }
    }
}
