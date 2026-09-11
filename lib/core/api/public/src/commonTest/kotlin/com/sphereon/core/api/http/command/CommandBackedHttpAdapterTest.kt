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
import com.sphereon.core.api.conf.ConfigUnavailableException
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
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteMatch
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
import kotlin.test.assertFailsWith
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
    fun malformedAdapterIdIsRejectedDuringConstruction() {
        assertFailsWith<IllegalArgumentException> {
            TestUnavailableAdapter(TestSessionExecution(), id = "legacy-adapter")
        }
    }

    @Test
    fun endpointCommandMatchesRequestBasedOnEndpointDescriptor() =
        runTest {
            val endpoint =
                TestEndpointCommand(
                    id = "test.items.get",
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
            val getResponse = adapter.handleTestRequest(GenericHttpRequest(method = "GET", path = "/items/42"))
            assertEquals(200, getResponse.statusCode)
            assertEquals("""{"item":"42"}""", getResponse.body)

            // Test POST /items - full path with adapter base path (endpoint pattern is just "/")
            val postResponse =
                adapter.handleTestRequest(
                    GenericHttpRequest(
                        method = "POST",
                        path = "/items",
                        bodyContent = GenericHttpBody.Text("""{"name":"test"}"""),
                    ),
                )
            assertEquals(201, postResponse.statusCode)
            assertEquals("""{"created":true}""", postResponse.body)

            // Test DELETE /items/{id} - full path with adapter base path
            val deleteResponse = adapter.handleTestRequest(GenericHttpRequest(method = "DELETE", path = "/items/42"))
            assertEquals(204, deleteResponse.statusCode)
        }

    @Test
    fun adapterReturns404ForUnmatchedRequest() =
        runTest {
            val adapter = TestAdapter()

            val response = adapter.handleTestRequest(GenericHttpRequest(method = "PATCH", path = "/items/42"))
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
            val response = adapter.handleTestRequest(GenericHttpRequest(method = "GET", path = "/items/42"))
            assertEquals(404, response.statusCode)

            // POST still works
            val postResponse =
                adapter.handleTestRequest(
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
                    id = "test.items.get",
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

    @Test
    fun configUnavailableExceptionFromEndpointRendersAs503() =
        runTest {
            // §4.2: a config read that races a lazy/remote config fetch raises
            // ConfigUnavailableException from inside endpoint execution. The
            // command-backed adapter must surface this as 503 (UNAVAILABLE) so the
            // caller retries, not the 500 a generic exception produces.
            val adapter = TestUnavailableAdapter(TestSessionExecution())

            val request = GenericHttpRequest(method = "GET", path = "/items/42")
            val response = adapter.handleCommand(request, "test.items.get", "/items/{id}")

            assertEquals(503, response.statusCode)
            assertTrue(
                response.body?.contains("SERVICE_UNAVAILABLE") == true,
                "503 body should carry the SERVICE_UNAVAILABLE code, was: ${response.body}",
            )
        }

    @Test
    fun genericExceptionFromEndpointStillRendersAs500() =
        runTest {
            // Guard the discrimination: a non-config exception must remain a 500.
            val adapter = TestGenericFailureAdapter(TestSessionExecution())

            val request = GenericHttpRequest(method = "GET", path = "/items/42")
            val response = adapter.handleCommand(request, "test.items.get", "/items/{id}")

            assertEquals(500, response.statusCode)
        }

    @Test
    fun selectedRouteConstructsOnlyItsKeyedEndpointCommand() =
        runTest {
            var selectedConstructions = 0
            var unrelatedConstructions = 0
            val registry =
                TestEndpointRegistry(
                    mapOf(
                        "test.items.get" to lazy {
                            selectedConstructions++
                            TestEndpointCommand(
                                id = "test.items.get",
                                endpoint = HttpEndpointDescriptor(HttpMethod.GET, "/{id}"),
                                responseProvider = { jsonResponse(200, "{}") },
                            )
                        },
                        "test.items.delete" to lazy {
                            unrelatedConstructions++
                            TestEndpointCommand(
                                id = "test.items.delete",
                                endpoint = HttpEndpointDescriptor(HttpMethod.DELETE, "/{id}"),
                            )
                        },
                    ),
                )
            val adapter =
                object : CommandBackedHttpAdapter(
                    id = "test.http.adapter",
                    execution = TestSessionExecution(),
                    mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/items"),
                    endpointCommandRegistry = registry,
                ) {}
            val request = GenericHttpRequest(method = "GET", path = "/items/42")

            val response = adapter.handleCommand(request, "test.items.get", "/items/{id}")

            assertEquals(200, response.statusCode)
            assertEquals(1, selectedConstructions)
            assertEquals(0, unrelatedConstructions)
        }

    @Test
    fun nonProtocolAdapterPreservesFirstEndpointPathSegment() =
        runTest {
            val endpoint =
                TestEndpointCommand(
                    id = "test.setup.license-request.generate",
                    endpoint =
                        HttpEndpointDescriptor(
                            method = HttpMethod.POST,
                            pathPattern = "/license-request/generate",
                        ),
                    responseProvider = { jsonResponse(200, "{}") },
                )
            val adapter =
                object : CommandBackedHttpAdapter(
                    id = "test.setup.http",
                    execution = TestSessionExecution(),
                    mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/api/platform/setup/v1"),
                    endpointCommandRegistry = TestEndpointRegistry(mapOf(endpoint.id to lazyOf(endpoint))),
                    tenantPathPolicy = TenantPathPolicy.None,
                ) {}
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/api/platform/setup/v1/license-request/generate",
                )

            val response =
                adapter.handleCommand(
                    request = request,
                    handlerCommandId = endpoint.id,
                    matchedPathPattern = "/api/platform/setup/v1/license-request/generate",
                )

            assertEquals(200, response.statusCode)
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
        override val testEndpoints: List<HttpEndpointCommand> =
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
     * Real [CommandBackedHttpAdapter] whose single endpoint raises
     * [ConfigUnavailableException] — exercises the adapter's transient-config
     * catch branch (→ 503).
     */
    private class TestUnavailableAdapter(
        execution: SessionExecution,
        id: String = "test.http.unavailable",
        endpoint: HttpEndpointCommand =
            TestEndpointCommand(
                id = "test.items.get",
                endpoint =
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/{id}",
                        operationId = "getItem",
                    ),
                responseProvider = { throw ConfigUnavailableException("remote platform config not ready") },
            ),
    ) : CommandBackedHttpAdapter(
            id = id,
            execution = execution,
            mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/items"),
            endpointCommandRegistry = TestEndpointRegistry(mapOf(endpoint.id to lazyOf(endpoint))),
        )

    /**
     * Real [CommandBackedHttpAdapter] whose single endpoint throws a generic
     * exception — guards that only config-unavailability maps to 503 (→ 500).
     */
    private class TestGenericFailureAdapter(
        execution: SessionExecution,
        endpoint: HttpEndpointCommand =
            TestEndpointCommand(
                id = "test.items.get",
                endpoint =
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/{id}",
                        operationId = "getItem",
                    ),
                responseProvider = { throw IllegalStateException("boom") },
            ),
    ) : CommandBackedHttpAdapter(
            id = "test.http.generic-failure",
            execution = execution,
            mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/items"),
            endpointCommandRegistry = TestEndpointRegistry(mapOf(endpoint.id to lazyOf(endpoint))),
        )

    private class TestEndpointRegistry(
        private val endpoints: Map<String, Lazy<HttpEndpointCommand>>,
    ) : HttpEndpointCommandRegistry {
        override fun get(handlerCommandId: String): HttpEndpointCommand? = endpoints[handlerCommandId]?.value

        override fun listHandlerCommandIds(): Set<String> = endpoints.keys
    }

    /**
     * Test adapter with one disabled endpoint.
     */
    private class TestAdapterWithDisabledEndpoint :
        TestCommandBackedAdapter(
            id = "test-adapter-disabled",
            mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/items"),
        ) {
        override val testEndpoints: List<HttpEndpointCommand> =
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

private suspend fun HttpAdapter.handleTestRequest(request: GenericHttpRequest): GenericHttpResponse {
    val matches =
        describe().endpoints.flatMap { endpoint ->
            endpoint.pathPatterns
                .filter { pattern -> request.matches(endpoint.method.name, pattern) }
                .map { pattern -> endpoint to pattern }
        }
    if (matches.isEmpty()) return errorResponse(404, "Not found: ${request.method} ${request.path}")
    if (matches.size > 1) return errorResponse(500, "Multiple endpoints match request")
    val (endpoint, pattern) = matches.single()
    val handlerCommandId = endpoint.handlerCommandId ?: return errorResponse(500, "Missing test handlerCommandId")
    return handleResolvedRequest(
        request,
        HttpAdapterRouteMatch(
            adapterId = id,
            method = request.method,
            originalPath = request.path,
            normalizedPath = request.path,
            matchedPathPattern = pattern,
            handlerCommandId = handlerCommandId,
            tenantIdFromPath = null,
        ),
    )
}

private suspend fun HttpAdapter.handleCommand(
    request: GenericHttpRequest,
    handlerCommandId: String,
    matchedPathPattern: String,
): GenericHttpResponse =
    handleResolvedRequest(
        request,
        HttpAdapterRouteMatch(
            adapterId = id,
            method = request.method,
            originalPath = request.path,
            normalizedPath = request.path,
            matchedPathPattern = matchedPathPattern,
            handlerCommandId = handlerCommandId,
            tenantIdFromPath = null,
        ),
    )

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
    protected abstract val testEndpoints: List<HttpEndpointCommand>

    private val enabledEndpoints: List<HttpEndpointCommand>
        get() = testEndpoints.filter { it.isEnabled }

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = mount,
            endpoints =
                enabledEndpoints.map { endpoint ->
                    endpoint.endpoint.copy(
                        pathPatterns =
                            endpoint.endpoint.pathPatterns.map { pattern ->
                                when {
                                    mount.adapterBasePath.isEmpty() || mount.adapterBasePath == "/" -> pattern
                                    pattern == "/" -> mount.adapterBasePath
                                    else -> mount.adapterBasePath + pattern
                                }
                            },
                        handlerCommandId = endpoint.id,
                    )
                },
            openApiHints = null,
        )

    override suspend fun handleResolvedRequest(
        request: GenericHttpRequest,
        route: HttpAdapterRouteMatch,
    ): GenericHttpResponse {
        // Strip adapter base path before routing to endpoint commands
        val relativeRequest = stripAdapterBasePath(request)
        val endpoint = enabledEndpoints.singleOrNull { it.id == route.handlerCommandId }
        return when (endpoint) {
            null -> errorResponse(500, "Selected endpoint is not registered")
            else -> {
                val result = endpoint.execute(relativeRequest)
                result.fold(
                    success = { it },
                    failure = { error ->
                        errorResponse(500, error.message.defaultMessage)
                    },
                )
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

            val response = adapter.handleTestRequest(GenericHttpRequest(method = "GET", path = "/123"))
            assertEquals(200, response.statusCode)
        }

    @Test
    fun testAdapterWithSlashBasePathRoutes() =
        runTest {
            val adapter = TestAdapterWithSlashBasePath()

            val response = adapter.handleTestRequest(GenericHttpRequest(method = "GET", path = "/items/456"))
            assertEquals(200, response.statusCode)
        }

    @Test
    fun testRequestPathNotMatchingBasePath() =
        runTest {
            val adapter = TestAdapterForPathStripping()

            // Request path doesn't start with the adapter base path
            val response = adapter.handleTestRequest(GenericHttpRequest(method = "GET", path = "/other/123"))
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
        override val testEndpoints: List<HttpEndpointCommand> =
            listOf(
                SimpleTestEndpointCommand(
                    id = "test.items.get",
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
        override val testEndpoints: List<HttpEndpointCommand> =
            listOf(
                SimpleTestEndpointCommand(
                    id = "test.items.get",
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
        override val testEndpoints: List<HttpEndpointCommand> =
            listOf(
                SimpleTestEndpointCommand(
                    id = "test.items.get",
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
    protected abstract val testEndpoints: List<HttpEndpointCommand>

    private val enabledEndpoints: List<HttpEndpointCommand>
        get() = testEndpoints.filter { it.isEnabled }

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = mount,
            endpoints =
                enabledEndpoints.map { endpoint ->
                    // Cover the empty basePath branch
                    val fullPathPatterns =
                        endpoint.endpoint.pathPatterns.map { pattern ->
                            if (mount.adapterBasePath.isEmpty() || mount.adapterBasePath == "/") {
                                pattern
                            } else {
                                mount.adapterBasePath + pattern
                            }
                        }
                    endpoint.endpoint.copy(
                        pathPatterns = fullPathPatterns,
                        handlerCommandId = endpoint.id,
                    )
                },
            openApiHints = null,
        )

    override suspend fun handleResolvedRequest(
        request: GenericHttpRequest,
        route: HttpAdapterRouteMatch,
    ): GenericHttpResponse {
        val relativeRequest = stripAdapterBasePath(request)
        val endpoint = enabledEndpoints.singleOrNull { it.id == route.handlerCommandId }
        return when (endpoint) {
            null -> errorResponse(500, "Selected endpoint is not registered")
            else -> {
                val result = endpoint.execute(relativeRequest)
                result.fold(
                    success = { it },
                    failure = { error ->
                        errorResponse(500, error.message.defaultMessage)
                    },
                )
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
