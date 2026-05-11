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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.di.createCoreApiTestAppGraph
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpBody
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.di.session.SessionContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for [CommandBackedHttpAdapter] and [HttpEndpointCommandAdapter]
 * using the full DI graph hierarchy.
 *
 * These tests verify that the command infrastructure works correctly when
 * integrated with the session lifecycle and execution context.
 */
class HttpCommandDiIntegrationTest {
    @Test
    fun httpEndpointCommandAdapterCanBeCreatedWithExecution() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            val coreApiGraph = session.asCoreApiServiceGraph()
            val execution = coreApiGraph.serviceExecution

            assertNotNull(execution)

            // Create endpoint command with execution
            val endpoint = TestGetEndpointCommand(execution)
            assertNotNull(endpoint)
            assertEquals("test.get", endpoint.id)
            assertTrue(endpoint.isEnabled)

            app.destroy()
        }

    @Test
    fun httpEndpointCommandAdapterSupportsMatchingRequests() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            val coreApiGraph = session.asCoreApiServiceGraph()
            val execution = coreApiGraph.serviceExecution
            val sessionContext = coreApiGraph.sessionContext

            val endpoint = TestGetEndpointCommand(execution)

            // Should match GET /items/{id}
            val matchingRequest = GenericHttpRequest(method = "GET", path = "/items/123")
            assertTrue(endpoint.supports(matchingRequest))

            // Should not match POST
            val wrongMethod = GenericHttpRequest(method = "POST", path = "/items/123")
            assertFalse(endpoint.supports(wrongMethod))

            // Should not match different path
            val wrongPath = GenericHttpRequest(method = "GET", path = "/users/123")
            assertFalse(endpoint.supports(wrongPath))

            // Should not match non-request args
            assertFalse(endpoint.supports("not a request"))
            assertFalse(endpoint.supports(42))

            app.destroy()
        }

    @Test
    fun httpEndpointCommandAdapterExecutesSuccessfully() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            val coreApiGraph = session.asCoreApiServiceGraph()
            val execution = coreApiGraph.serviceExecution
            val sessionContext = coreApiGraph.sessionContext

            val endpoint = TestGetEndpointCommand(execution)

            val request = GenericHttpRequest(method = "GET", path = "/items/456")
            val result = endpoint.execute(request)

            assertTrue(result.isOk)
            val response = (result as Ok).value
            assertEquals(200, response.statusCode)
            assertTrue(response.body?.contains("456") == true)

            app.destroy()
        }

    @Test
    fun commandBackedHttpAdapterCanBeCreatedWithExecution() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            val coreApiGraph = session.asCoreApiServiceGraph()
            val execution = coreApiGraph.serviceExecution

            val adapter = TestItemsHttpAdapter(execution)
            assertNotNull(adapter)
            assertEquals("test-items-adapter", adapter.id)

            app.destroy()
        }

    @Test
    fun commandBackedHttpAdapterDescribesEndpoints() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            val coreApiGraph = session.asCoreApiServiceGraph()
            val execution = coreApiGraph.serviceExecution

            val adapter = TestItemsHttpAdapter(execution)
            val description = adapter.describe()

            assertEquals("test-items-adapter", description.id)
            assertEquals("/api", description.mount.serverPrefix)
            assertEquals("/items", description.mount.adapterBasePath)
            assertEquals(3, description.endpoints.size)

            // Verify endpoints have full paths (adapter base path prepended)
            val paths = description.endpoints.map { it.pathPattern }.toSet()
            assertTrue(paths.contains("/items/{id}"))
            assertTrue(paths.contains("/items/"))

            app.destroy()
        }

    @Test
    fun commandBackedHttpAdapterRoutesRequests() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            val coreApiGraph = session.asCoreApiServiceGraph()
            val execution = coreApiGraph.serviceExecution

            val adapter = TestItemsHttpAdapter(execution)

            // Test GET /items/42
            val getResponse = adapter.handleRequest(GenericHttpRequest(method = "GET", path = "/items/42"))
            assertEquals(200, getResponse.statusCode)
            assertTrue(getResponse.body?.contains("42") == true)

            // Test POST /items/
            val postResponse =
                adapter.handleRequest(
                    GenericHttpRequest(
                        method = "POST",
                        path = "/items/",
                        bodyContent = GenericHttpBody.Text("""{"name":"test"}"""),
                    ),
                )
            assertEquals(201, postResponse.statusCode)

            // Test DELETE /items/42
            val deleteResponse = adapter.handleRequest(GenericHttpRequest(method = "DELETE", path = "/items/42"))
            assertEquals(204, deleteResponse.statusCode)

            app.destroy()
        }

    @Test
    fun commandBackedHttpAdapterReturnsErrorForUnmatched() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            val coreApiGraph = session.asCoreApiServiceGraph()
            val execution = coreApiGraph.serviceExecution

            val adapter = TestItemsHttpAdapter(execution)

            // When the request doesn't match any endpoint, the adapter's supports() returns false
            // and execute() returns COMMAND_ARG_NOT_SUPPORTED_ERROR, which gets mapped to 500.
            // This is different from the mock adapter behavior which returned 404 from doExecute.
            val response = adapter.handleRequest(GenericHttpRequest(method = "PATCH", path = "/items/42"))
            assertTrue(response.statusCode >= 400, "Unmatched requests should return an error status code")

            app.destroy()
        }

    @Test
    fun commandBackedHttpAdapterSupportsMethod() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            val coreApiGraph = session.asCoreApiServiceGraph()
            val execution = coreApiGraph.serviceExecution
            val sessionContext = coreApiGraph.sessionContext

            val adapter = TestItemsHttpAdapter(execution)

            // Should support matching requests
            assertTrue(adapter.supports(GenericHttpRequest(method = "GET", path = "/items/1")))
            assertTrue(adapter.supports(GenericHttpRequest(method = "POST", path = "/items/")))

            // Should not support non-request args
            assertFalse(adapter.supports("not a request"))

            app.destroy()
        }

    @Test
    fun commandBackedHttpAdapterCanHandleMethod() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            val coreApiGraph = session.asCoreApiServiceGraph()
            val execution = coreApiGraph.serviceExecution

            val adapter = TestItemsHttpAdapter(execution)

            // canHandle checks if the adapter's base path matches
            assertTrue(adapter.canHandle(GenericHttpRequest(method = "GET", path = "/items/1")))
            assertTrue(adapter.canHandle(GenericHttpRequest(method = "POST", path = "/items/")))

            // Should not handle paths outside base path
            assertFalse(adapter.canHandle(GenericHttpRequest(method = "GET", path = "/users/1")))

            app.destroy()
        }

    @Test
    fun commandBackedHttpAdapterHandlesErrors() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            val coreApiGraph = session.asCoreApiServiceGraph()
            val execution = coreApiGraph.serviceExecution

            val adapter = TestErrorHttpAdapter(execution)

            // The error endpoint returns an IdkError
            val response = adapter.handleRequest(GenericHttpRequest(method = "GET", path = "/error/test"))
            assertEquals(400, response.statusCode)

            app.destroy()
        }

    @Test
    fun commandBackedHttpAdapterMapsErrorCodesToStatusCodes() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            val coreApiGraph = session.asCoreApiServiceGraph()
            val execution = coreApiGraph.serviceExecution

            // Test NOT_FOUND error
            val notFoundAdapter = TestErrorHttpAdapter(execution, "NOT_FOUND_ERROR")
            val notFoundResponse = notFoundAdapter.handleRequest(GenericHttpRequest(method = "GET", path = "/error/test"))
            assertEquals(404, notFoundResponse.statusCode)

            // Test UNAUTHORIZED error
            val unauthorizedAdapter = TestErrorHttpAdapter(execution, "UNAUTHORIZED_ERROR")
            val unauthorizedResponse = unauthorizedAdapter.handleRequest(GenericHttpRequest(method = "GET", path = "/error/test"))
            assertEquals(401, unauthorizedResponse.statusCode)

            // Test FORBIDDEN error
            val forbiddenAdapter = TestErrorHttpAdapter(execution, "FORBIDDEN_ERROR")
            val forbiddenResponse = forbiddenAdapter.handleRequest(GenericHttpRequest(method = "GET", path = "/error/test"))
            assertEquals(403, forbiddenResponse.statusCode)

            app.destroy()
        }

    @Test
    fun disabledAdapterDoesNotSupportRequests() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            val coreApiGraph = session.asCoreApiServiceGraph()
            val execution = coreApiGraph.serviceExecution
            val sessionContext = coreApiGraph.sessionContext

            val adapter = TestDisabledHttpAdapter(execution)

            // Disabled adapter should not support any requests
            assertFalse(adapter.supports(GenericHttpRequest(method = "GET", path = "/items/1")))

            app.destroy()
        }

    @Test
    fun adapterWithNoBasePathMatchesFirstSegment() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            val coreApiGraph = session.asCoreApiServiceGraph()
            val execution = coreApiGraph.serviceExecution

            val adapter = TestNoBasePathHttpAdapter(execution)

            // With no base path, canHandle uses first segment heuristic
            assertTrue(adapter.canHandle(GenericHttpRequest(method = "GET", path = "/resource/1")))

            app.destroy()
        }

    // ========== Test fixtures ==========

    /**
     * Test endpoint command that gets an item by ID.
     */
    private class TestGetEndpointCommand(
        execution: SessionExecution,
    ) : HttpEndpointCommandAdapter(
            id = "test.get",
            execution = execution,
            endpoint =
                HttpEndpointDescriptor(
                    method = HttpMethod.GET,
                    pathPattern = "/items/{id}",
                    produces = setOf(MediaType.ApplicationJson),
                    operationId = "getItem",
                ),
        ) {
        override suspend fun doExecute(
            args: GenericHttpRequest,
            applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
        ): IdkResult<GenericHttpResponse, IdkError> {
            val request = applyDuring(args)
            val id = request.withExtractedParams("/items/{id}").pathParams["id"]
            return Ok(jsonResponse(200, """{"item":"$id"}"""))
        }
    }

    /**
     * Test adapter with multiple endpoint commands.
     */
    private class TestItemsHttpAdapter(
        execution: SessionExecution,
    ) : CommandBackedHttpAdapter(
            id = "test-items-adapter",
            execution = execution,
            mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/items"),
        ) {
        override val endpointCommands: List<HttpEndpointCommand> by lazy {
            listOf(
                object : HttpEndpointCommandAdapter(
                    id = "test.items.get",
                    execution = execution,
                    endpoint =
                        HttpEndpointDescriptor(
                            method = HttpMethod.GET,
                            pathPattern = "/{id}",
                            produces = setOf(MediaType.ApplicationJson),
                            operationId = "getItem",
                        ),
                ) {
                    override suspend fun doExecute(
                        args: GenericHttpRequest,
                        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
                    ): IdkResult<GenericHttpResponse, IdkError> {
                        val request = applyDuring(args)
                        val id = request.withExtractedParams("/{id}").pathParams["id"]
                        return Ok(jsonResponse(200, """{"item":"$id"}"""))
                    }
                },
                object : HttpEndpointCommandAdapter(
                    id = "test.items.create",
                    execution = execution,
                    endpoint =
                        HttpEndpointDescriptor(
                            method = HttpMethod.POST,
                            pathPattern = "/",
                            consumes = setOf(MediaType.ApplicationJson),
                            produces = setOf(MediaType.ApplicationJson),
                            operationId = "createItem",
                        ),
                ) {
                    override suspend fun doExecute(
                        args: GenericHttpRequest,
                        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
                    ): IdkResult<GenericHttpResponse, IdkError> = Ok(GenericHttpResponse(statusCode = 201, body = """{"created":true}"""))
                },
                object : HttpEndpointCommandAdapter(
                    id = "test.items.delete",
                    execution = execution,
                    endpoint =
                        HttpEndpointDescriptor(
                            method = HttpMethod.DELETE,
                            pathPattern = "/{id}",
                            operationId = "deleteItem",
                        ),
                ) {
                    override suspend fun doExecute(
                        args: GenericHttpRequest,
                        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
                    ): IdkResult<GenericHttpResponse, IdkError> = Ok(GenericHttpResponse(statusCode = 204))
                },
            )
        }
    }

    /**
     * Test adapter that returns errors with specific error codes.
     */
    private class TestErrorHttpAdapter(
        execution: SessionExecution,
        private val errorCode: String = "ILLEGAL_ARGUMENT_ERROR",
    ) : CommandBackedHttpAdapter(
            id = "test-error-adapter",
            execution = execution,
            mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/error"),
        ) {
        override val endpointCommands: List<HttpEndpointCommand> by lazy {
            listOf(
                object : HttpEndpointCommandAdapter(
                    id = "test.error",
                    execution = execution,
                    endpoint =
                        HttpEndpointDescriptor(
                            method = HttpMethod.GET,
                            pathPattern = "/{id}",
                            operationId = "getError",
                        ),
                ) {
                    override suspend fun doExecute(
                        args: GenericHttpRequest,
                        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
                    ): IdkResult<GenericHttpResponse, IdkError> {
                        val error =
                            when (errorCode) {
                                "NOT_FOUND_ERROR" -> IdkError.NOT_FOUND_ERROR(message = "Test error")
                                "UNAUTHORIZED_ERROR" -> IdkError.UNAUTHORIZED_ERROR(message = "Test error")
                                "FORBIDDEN_ERROR" -> IdkError.FORBIDDEN_ERROR(message = "Test error")
                                else -> IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Test error")
                            }
                        return Err(error)
                    }
                },
            )
        }
    }

    /**
     * Test disabled adapter.
     */
    private class TestDisabledHttpAdapter(
        execution: SessionExecution,
    ) : CommandBackedHttpAdapter(
            id = "test-disabled-adapter",
            execution = execution,
            mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/items"),
            isEnabled = false,
        ) {
        override val endpointCommands: List<HttpEndpointCommand> = emptyList()
    }

    /**
     * Test adapter with no base path (empty string).
     */
    private class TestNoBasePathHttpAdapter(
        execution: SessionExecution,
    ) : CommandBackedHttpAdapter(
            id = "test-no-base-path-adapter",
            execution = execution,
            mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = ""),
        ) {
        override val endpointCommands: List<HttpEndpointCommand> by lazy {
            listOf(
                object : HttpEndpointCommandAdapter(
                    id = "test.resource.get",
                    execution = execution,
                    endpoint =
                        HttpEndpointDescriptor(
                            method = HttpMethod.GET,
                            pathPattern = "/resource/{id}",
                            operationId = "getResource",
                        ),
                ) {
                    override suspend fun doExecute(
                        args: GenericHttpRequest,
                        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
                    ): IdkResult<GenericHttpResponse, IdkError> = Ok(GenericHttpResponse(statusCode = 200, body = "OK"))
                },
            )
        }
    }
}
