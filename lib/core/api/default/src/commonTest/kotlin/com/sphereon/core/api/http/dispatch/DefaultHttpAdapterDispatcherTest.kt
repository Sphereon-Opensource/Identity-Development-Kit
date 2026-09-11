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

package com.sphereon.core.api.http.dispatch

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.RoutedHttpAdapter
import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.HttpRoute
import com.sphereon.core.api.http.describe.TenantPathMode
import com.sphereon.core.api.http.response.errorResponse
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultHttpAdapterDispatcherTest {
    /**
     * Simple test adapter that captures the normalized request for verification.
     */
    private class TestAdapter(
        override val id: String,
        private val adapterMount: HttpAdapterMount,
        private val routeSpecs: List<Pair<HttpMethod, String>>,
        private val responseCode: Int = 200,
        private val captureNormalizedPath: ((String) -> Unit)? = null,
    ) : RoutedHttpAdapter() {
        override val mount: HttpAdapterMount get() = adapterMount

        override val routes: List<HttpRoute> =
            routeSpecs.mapIndexed { index, (method, pattern) ->
                HttpRoute(
                    endpoint =
                        HttpEndpointDescriptor(
                            method = method,
                            pathPattern = pattern,
                            handlerCommandId = "test.handler.route-$index",
                        ),
                    handler = { request ->
                        captureNormalizedPath?.invoke(request.path)
                        GenericHttpResponse(
                            statusCode = responseCode,
                            body = "id=$id,path=${request.path},tenantId=${request.pathParameters["tenantId"]}",
                        )
                    },
                )
            }
    }

    private class TestDescriptorProvider(
        override val id: String,
        private val mount: HttpAdapterMount,
        private val endpoints: List<HttpEndpointDescriptor>,
    ) : HttpAdapterDescriptorProvider {
        override fun describe(): HttpAdapterDescription =
            HttpAdapterDescription(
                id = id,
                mount = mount,
                endpoints =
                    endpoints.mapIndexed { index, endpoint ->
                        endpoint.copy(
                            handlerCommandId = endpoint.handlerCommandId ?: "test.handler.route-$index",
                        )
                    },
            )
    }

    private fun createCatalog(providers: Set<HttpAdapterDescriptorProvider>): HttpAdapterCatalog = DefaultHttpAdapterCatalog(providers, com.sphereon.core.api.http.config.UniversalHttpConfig.DEFAULT)

    private class TestDispatcher(
        private val selector: HttpAdapterRouteSelector,
        private val dispatcher: DefaultHttpAdapterDispatcher,
    ) {
        suspend fun dispatch(request: GenericHttpRequest): GenericHttpResponse =
            when (val selection = selector.select(request.method, request.path)) {
                is HttpAdapterRouteSelection.Selected -> dispatcher.dispatch(request, selection.match)
                is HttpAdapterRouteSelection.NotFound -> errorResponse(404, "Not found")
                is HttpAdapterRouteSelection.Ambiguous -> errorResponse(500, "Internal server error")
                is HttpAdapterRouteSelection.Misconfigured -> errorResponse(500, "Internal server error")
            }

        suspend fun dispatch(
            request: GenericHttpRequest,
            route: HttpAdapterRouteMatch,
        ): GenericHttpResponse = dispatcher.dispatch(request, route)
    }

    private fun createDispatcher(
        catalog: HttpAdapterCatalog,
        vararg adapters: HttpAdapter,
    ): TestDispatcher {
        val adaptersById = adapters.groupBy(HttpAdapter::id)
        val duplicateIds = adaptersById.filterValues { it.size > 1 }.keys.sorted()
        require(duplicateIds.isEmpty()) {
            "Multiple runtime HttpAdapter instances found for ids: ${duplicateIds.joinToString(", ")}"
        }
        return createDispatcher(
            catalog,
            adaptersById.mapValues { (_, values) -> lazyOf(values.single()) },
        )
    }

    private fun createDispatcher(
        catalog: HttpAdapterCatalog,
        adapters: Map<String, Lazy<HttpAdapter>>,
        execution: SessionExecution = TestSessionExecution,
    ): TestDispatcher =
        TestDispatcher(
            selector = DefaultHttpAdapterRouteSelector(catalog),
            dispatcher =
                DefaultHttpAdapterDispatcher(
                    adapters = adapters,
                    execution = execution,
                ),
        )

    private object TestSessionExecution : SessionExecution {
        override val sessionContext: SessionContext = NoOpSessionContext
        override val sessionContextManager: SessionContextManager
            get() = error("Not needed for dispatcher tests")
        override val log: SessionLogService = NoOpSessionLogService
        override val conf: ContextConfig = NoOpContextConfig
    }

    private object NoOpSessionLogService : SessionLogService {
        override val sessionContext: SessionContext = NoOpSessionContext
        override val id: String = "test-http-dispatch"
        override val isEnabled: Boolean = false
        override val scope: IdkScope = IdkScope.SESSION
        override val logManager: SessionLogManager
            get() = throw NotImplementedError("Not needed for dispatcher tests")

        override suspend fun setConfig(config: LoggerConfig): LogService = this

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

        override fun toAsync(): AsyncLogService = throw NotImplementedError("Not needed for dispatcher tests")
    }

    private class CapturingSessionLogService : SessionLogService {
        val messages = mutableListOf<LogMessage>()
        override val sessionContext: SessionContext = NoOpSessionContext
        override val id: String = "capturing-http-dispatch"
        override val isEnabled: Boolean = true
        override val scope: IdkScope = IdkScope.SESSION
        override val logManager: SessionLogManager
            get() = throw NotImplementedError("Not needed for dispatcher tests")

        override suspend fun setConfig(config: LoggerConfig): LogService = this

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> =
            Ok(Unit).also { messages += message }

        override fun toAsync(): AsyncLogService = throw NotImplementedError("Not needed for dispatcher tests")
    }

    private class CapturingSessionExecution(
        override val log: SessionLogService,
    ) : SessionExecution {
        override val sessionContext: SessionContext = NoOpSessionContext
        override val sessionContextManager: SessionContextManager
            get() = error("Not needed for dispatcher tests")
        override val conf: ContextConfig = NoOpContextConfig
    }

    private object NoOpContextConfig : ContextConfig {
        override val app: AppConfigService
            get() = error("Not needed for dispatcher tests")
        override val tenant: TenantConfigService
            get() = error("Not needed for dispatcher tests")
        override val principal: PrincipalConfigService
            get() = error("Not needed for dispatcher tests")

        override fun conf(level: ConfigLevel): ConfigService = error("Not needed for dispatcher tests")
    }

    // ========== Path normalization tests (TenantPathMode.OFF) ==========

    @Test
    fun dispatchMatchesRootMountedAbsolutePatternAdapter() =
        runTest {
            // The command transport mounts at the server root with an absolute endpoint
            // pattern (serverPrefix "", basePath "", pattern "/api/commands").
            val adapter =
                TestAdapter(
                    id = "transport-command-http",
                    adapterMount = HttpAdapterMount(serverPrefix = "", adapterBasePath = ""),
                    routeSpecs = listOf(HttpMethod.POST to "/api/commands"),
                )
            val provider =
                TestDescriptorProvider(
                    id = "transport-command-http",
                    mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = ""),
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.POST, "/api/commands")),
                )
            val catalog = createCatalog(setOf(provider))
            val dispatcher = createDispatcher(catalog, adapter)

            val response = dispatcher.dispatch(GenericHttpRequest(method = "POST", path = "/api/commands"))

            assertEquals(200, response.statusCode)
        }

    @Test
    fun dispatchUsesAppScopePreselectedRouteWithoutRepeatingCatalogSelection() =
        runTest {
            var capturedPath: String? = null
            val adapter =
                TestAdapter(
                    id = "KMS_KEYS",
                    adapterMount = HttpAdapterMount(serverPrefix = "/api/kms", adapterBasePath = "/keys"),
                    routeSpecs = listOf(HttpMethod.GET to "/{keyId}"),
                    captureNormalizedPath = { capturedPath = it },
                )
            val provider =
                TestDescriptorProvider(
                    id = adapter.id,
                    mount = adapter.describe().mount,
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/keys/{keyId}")),
                )
            val dispatcher = createDispatcher(createCatalog(setOf(provider)), adapter)
            val request = GenericHttpRequest(method = "GET", path = "/api/kms/keys/abc123")
            val route =
                HttpAdapterRouteMatch(
                    adapterId = adapter.id,
                    method = request.method,
                    originalPath = request.path,
                    normalizedPath = "/keys/abc123",
                    matchedPathPattern = "/keys/{keyId}",
                    handlerCommandId = "test.handler.route-0",
                    tenantIdFromPath = null,
                )

            val response = dispatcher.dispatch(request, route)

            assertEquals(200, response.statusCode)
            assertEquals("/keys/abc123", capturedPath)
        }

    @Test
    fun dispatchRejectsPreselectedRouteWhoseRuntimeAdapterIsAbsent() =
        runTest {
            val log = CapturingSessionLogService()
            val dispatcher =
                createDispatcher(
                    catalog = createCatalog(emptySet()),
                    adapters = emptyMap(),
                    execution = CapturingSessionExecution(log),
                )
            val route =
                HttpAdapterRouteMatch(
                    adapterId = "missing-adapter",
                    method = "GET",
                    originalPath = "/missing",
                    normalizedPath = "/missing",
                    matchedPathPattern = "/missing",
                    handlerCommandId = "missing.endpoint.get",
                    tenantIdFromPath = null,
                )

            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/missing"), route)

            assertEquals(500, response.statusCode)
            assertTrue(response.body?.contains("Internal server error") == true)
            assertTrue(response.body?.contains("missing-adapter") != true)
            val resolution = log.messages.single { it.message == "VDX_HTTP_ROUTE_FIRST_RESOLUTION" }
            assertEquals("selected-adapter-resolution", resolution.metadata?.get("stage"))
            assertEquals("missing", resolution.metadata?.get("outcome"))
            val failure = log.messages.single { it.message == "HTTP_DISPATCH_FAILED" }
            assertEquals("HTTP_DISPATCH_FAILED", failure.message)
            assertEquals("runtime_adapter_missing", failure.metadata?.get("reason"))
            assertEquals("missing-adapter", failure.metadata?.get("adapterId"))
            assertEquals("missing.endpoint.get", failure.metadata?.get("handlerCommandId"))
        }

    @Test
    fun dispatchNormalizesPathByStrippingServerPrefix() =
        runTest {
            var capturedPath: String? = null
            val adapter =
                TestAdapter(
                    id = "KMS_KEYS",
                    adapterMount = HttpAdapterMount(serverPrefix = "/api/kms", adapterBasePath = "/keys"),
                    // Routes are now relative to adapterBasePath
                    routeSpecs = listOf(HttpMethod.GET to "/{keyId}"),
                    captureNormalizedPath = { capturedPath = it },
                )
            val provider =
                TestDescriptorProvider(
                    id = "KMS_KEYS",
                    mount = adapter.describe().mount,
                    // Endpoints include basePath prefix (after describe() transforms them)
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/keys/{keyId}")),
                )

            val catalog = createCatalog(setOf(provider))
            val dispatcher = createDispatcher(catalog, adapter)

            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/api/kms/keys/abc123"))

            assertEquals(200, response.statusCode)
            assertEquals("/keys/abc123", capturedPath)
        }

    @Test
    fun dispatchRoutesToCorrectAdapterByServerPrefix() =
        runTest {
            val kmsAdapter =
                TestAdapter(
                    id = "KMS_KEYS",
                    adapterMount = HttpAdapterMount(serverPrefix = "/api/kms", adapterBasePath = "/keys"),
                    // Routes relative to adapterBasePath
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                    responseCode = 201,
                )
            val oauthAdapter =
                TestAdapter(
                    id = "OAUTH2_AS",
                    adapterMount = HttpAdapterMount(serverPrefix = "/oauth2", adapterBasePath = "/"),
                    routeSpecs = listOf(HttpMethod.GET to "/token"),
                    responseCode = 202,
                )
            val providers =
                setOf(
                    TestDescriptorProvider("KMS_KEYS", kmsAdapter.describe().mount, listOf(HttpEndpointDescriptor(HttpMethod.GET, "/keys"))),
                    TestDescriptorProvider("OAUTH2_AS", oauthAdapter.describe().mount, listOf(HttpEndpointDescriptor(HttpMethod.GET, "/token"))),
                )

            val catalog = createCatalog(providers)
            val dispatcher = createDispatcher(catalog, kmsAdapter, oauthAdapter)

            val kmsResponse = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/api/kms/keys"))
            assertEquals(201, kmsResponse.statusCode, kmsResponse.body)

            val oauthResponse = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/oauth2/token"))
            assertEquals(202, oauthResponse.statusCode, oauthResponse.body)
        }

    @Test
    fun dispatchReturns404WhenNoAdapterMatches() =
        runTest {
            val adapter =
                TestAdapter(
                    id = "KMS_KEYS",
                    adapterMount = HttpAdapterMount(serverPrefix = "/api/kms", adapterBasePath = "/keys"),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                )
            val provider =
                TestDescriptorProvider(
                    id = "KMS_KEYS",
                    mount = adapter.describe().mount,
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/keys")),
                )

            val catalog = createCatalog(setOf(provider))
            val dispatcher = createDispatcher(catalog, adapter)

            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/unknown/path"))

            assertEquals(404, response.statusCode)
        }

    @Test
    fun dispatchReturns404WhenMethodDoesNotMatch() =
        runTest {
            val adapter =
                TestAdapter(
                    id = "TEST_ADAPTER",
                    adapterMount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/items"),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                )
            val provider =
                TestDescriptorProvider(
                    id = "TEST_ADAPTER",
                    mount = adapter.describe().mount,
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/items")),
                )

            val catalog = createCatalog(setOf(provider))
            val dispatcher = createDispatcher(catalog, adapter)

            val response = dispatcher.dispatch(GenericHttpRequest(method = "POST", path = "/api/items"))

            assertEquals(404, response.statusCode)
        }

    // ========== Tenant-in-path tests: BEFORE_SERVER_PREFIX ==========

    @Test
    fun dispatchExtractsTenantIdWhenBeforeServerPrefix() =
        runTest {
            var capturedTenantId: String? = null
            val adapter =
                TestAdapter(
                    id = "KMS_KEYS",
                    adapterMount =
                        HttpAdapterMount(
                            serverPrefix = "/api/kms",
                            adapterBasePath = "/keys",
                            tenantPathMode = TenantPathMode.BEFORE_SERVER_PREFIX,
                        ),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                    captureNormalizedPath = { },
                )
            val provider =
                TestDescriptorProvider(
                    id = "KMS_KEYS",
                    mount = adapter.describe().mount,
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/keys")),
                )

            val catalog = createCatalog(setOf(provider))
            val dispatcher = createDispatcher(catalog, adapter)

            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/t/tenant123/api/kms/keys"))

            assertEquals(200, response.statusCode)
            assertTrue(response.body?.contains("tenantId=tenant123") == true)
        }

    @Test
    fun dispatchNormalizesPathWhenTenantBeforeServerPrefix() =
        runTest {
            var capturedPath: String? = null
            val adapter =
                TestAdapter(
                    id = "KMS_KEYS",
                    adapterMount =
                        HttpAdapterMount(
                            serverPrefix = "/api/kms",
                            adapterBasePath = "/keys",
                            tenantPathMode = TenantPathMode.BEFORE_SERVER_PREFIX,
                        ),
                    routeSpecs = listOf(HttpMethod.GET to "/{keyId}"),
                    captureNormalizedPath = { capturedPath = it },
                )
            val provider =
                TestDescriptorProvider(
                    id = "KMS_KEYS",
                    mount = adapter.describe().mount,
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/keys/{keyId}")),
                )

            val catalog = createCatalog(setOf(provider))
            val dispatcher = createDispatcher(catalog, adapter)

            dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/t/myTenant/api/kms/keys/key456"))

            assertEquals("/keys/key456", capturedPath)
        }

    // ========== Tenant-in-path tests: AFTER_SERVER_PREFIX ==========

    @Test
    fun dispatchExtractsTenantIdWhenAfterServerPrefix() =
        runTest {
            val adapter =
                TestAdapter(
                    id = "KMS_KEYS",
                    adapterMount =
                        HttpAdapterMount(
                            serverPrefix = "/api/kms",
                            adapterBasePath = "/keys",
                            tenantPathMode = TenantPathMode.AFTER_SERVER_PREFIX,
                        ),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                )
            val provider =
                TestDescriptorProvider(
                    id = "KMS_KEYS",
                    mount = adapter.describe().mount,
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/keys")),
                )

            val catalog = createCatalog(setOf(provider))
            val dispatcher = createDispatcher(catalog, adapter)

            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/api/kms/t/tenant999/keys"))

            assertEquals(200, response.statusCode)
            assertTrue(response.body?.contains("tenantId=tenant999") == true)
        }

    @Test
    fun dispatchNormalizesPathWhenTenantAfterServerPrefix() =
        runTest {
            var capturedPath: String? = null
            val adapter =
                TestAdapter(
                    id = "KMS_KEYS",
                    adapterMount =
                        HttpAdapterMount(
                            serverPrefix = "/api/kms",
                            adapterBasePath = "/keys",
                            tenantPathMode = TenantPathMode.AFTER_SERVER_PREFIX,
                        ),
                    routeSpecs = listOf(HttpMethod.GET to "/{keyId}"),
                    captureNormalizedPath = { capturedPath = it },
                )
            val provider =
                TestDescriptorProvider(
                    id = "KMS_KEYS",
                    mount = adapter.describe().mount,
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/keys/{keyId}")),
                )

            val catalog = createCatalog(setOf(provider))
            val dispatcher = createDispatcher(catalog, adapter)

            dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/api/kms/t/tenantABC/keys/keyXYZ"))

            assertEquals("/keys/keyXYZ", capturedPath)
        }

    // ========== Tenant-in-path tests: BOTH ==========

    @Test
    fun dispatchAcceptsTenantBeforeWhenModeIsBoth() =
        runTest {
            val adapter =
                TestAdapter(
                    id = "KMS_KEYS",
                    adapterMount =
                        HttpAdapterMount(
                            serverPrefix = "/api/kms",
                            adapterBasePath = "/keys",
                            tenantPathMode = TenantPathMode.BOTH,
                        ),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                )
            val provider =
                TestDescriptorProvider(
                    id = "KMS_KEYS",
                    mount = adapter.describe().mount,
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/keys")),
                )

            val catalog = createCatalog(setOf(provider))
            val dispatcher = createDispatcher(catalog, adapter)

            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/t/beforeTenant/api/kms/keys"))

            assertEquals(200, response.statusCode)
            assertTrue(response.body?.contains("tenantId=beforeTenant") == true)
        }

    @Test
    fun dispatchAcceptsTenantAfterWhenModeIsBoth() =
        runTest {
            val adapter =
                TestAdapter(
                    id = "KMS_KEYS",
                    adapterMount =
                        HttpAdapterMount(
                            serverPrefix = "/api/kms",
                            adapterBasePath = "/keys",
                            tenantPathMode = TenantPathMode.BOTH,
                        ),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                )
            val provider =
                TestDescriptorProvider(
                    id = "KMS_KEYS",
                    mount = adapter.describe().mount,
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/keys")),
                )

            val catalog = createCatalog(setOf(provider))
            val dispatcher = createDispatcher(catalog, adapter)

            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/api/kms/t/afterTenant/keys"))

            assertEquals(200, response.statusCode)
            assertTrue(response.body?.contains("tenantId=afterTenant") == true)
        }

    // ========== Tenant authority tests ==========

    @Test
    fun dispatchUsesAuthenticatedTenantInsteadOfPathTenant() =
        runTest {
            val adapter =
                TestAdapter(
                    id = "KMS_KEYS",
                    adapterMount =
                        HttpAdapterMount(
                            serverPrefix = "/api/kms",
                            adapterBasePath = "/keys",
                            tenantPathMode = TenantPathMode.BEFORE_SERVER_PREFIX,
                        ),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                )
            val provider =
                TestDescriptorProvider(
                    id = "KMS_KEYS",
                    mount = adapter.describe().mount,
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/keys")),
                )

            val catalog = createCatalog(setOf(provider))
            val dispatcher = createDispatcher(catalog, adapter)

            val response =
                dispatcher.dispatch(
                    GenericHttpRequest(
                        method = "GET",
                        path = "/t/pathTenant/api/kms/keys",
                        resolvedTenantId = "jwtTenant",
                    ),
                )

            assertEquals(200, response.statusCode)
            assertTrue(response.body?.contains("tenantId=jwtTenant") == true)
        }

    @Test
    fun dispatchUsesPathTenantOnlyWhenAuthenticatedTenantIsAbsent() =
        runTest {
            val adapter =
                TestAdapter(
                    id = "KMS_KEYS",
                    adapterMount =
                        HttpAdapterMount(
                            serverPrefix = "/api/kms",
                            adapterBasePath = "/keys",
                            tenantPathMode = TenantPathMode.BEFORE_SERVER_PREFIX,
                        ),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                )
            val provider =
                TestDescriptorProvider(
                    id = "KMS_KEYS",
                    mount = adapter.describe().mount,
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/keys")),
                )

            val catalog = createCatalog(setOf(provider))
            val dispatcher = createDispatcher(catalog, adapter)

            val response =
                dispatcher.dispatch(
                    GenericHttpRequest(
                        method = "GET",
                        path = "/t/pathTenant/api/kms/keys",
                    ),
                )

            assertEquals(200, response.statusCode)
            assertTrue(response.body?.contains("tenantId=pathTenant") == true)
        }

    // ========== Custom tenant segment pattern tests ==========

    @Test
    fun dispatchUsesCustomTenantSegmentPattern() =
        runTest {
            val adapter =
                TestAdapter(
                    id = "KMS_KEYS",
                    adapterMount =
                        HttpAdapterMount(
                            serverPrefix = "/api/kms",
                            adapterBasePath = "/keys",
                            tenantPathMode = TenantPathMode.BEFORE_SERVER_PREFIX,
                            tenantSegmentPattern = "/tenant/{tenantId}", // custom pattern
                        ),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                )
            val provider =
                TestDescriptorProvider(
                    id = "KMS_KEYS",
                    mount = adapter.describe().mount,
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/keys")),
                )

            val catalog = createCatalog(setOf(provider))
            val dispatcher = createDispatcher(catalog, adapter)

            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/tenant/customTenant/api/kms/keys"))

            assertEquals(200, response.statusCode)
            assertTrue(response.body?.contains("tenantId=customTenant") == true)
        }

    @Test
    fun dispatchMatchesCatalogLeadingSlugPolicyAndKeepsSlugForRuntimeAdapter() =
        runTest {
            var capturedPath: String? = null
            val adapter =
                TestAdapter(
                    id = "OID4VP",
                    adapterMount =
                        HttpAdapterMount(
                            serverPrefix = "",
                            adapterBasePath = "/oid4vp",
                            tenantPathPolicy = TenantPathPolicy.LeadingSlug(maxDepth = 1),
                        ),
                    routeSpecs = listOf(HttpMethod.GET to "/request-uri/{id}"),
                    captureNormalizedPath = { capturedPath = it },
                )
            val provider =
                TestDescriptorProvider(
                    id = "OID4VP",
                    mount =
                        HttpAdapterMount(
                            serverPrefix = "",
                            adapterBasePath = "/oid4vp",
                            tenantPathPolicy = TenantPathPolicy.LeadingSlug(maxDepth = 1),
                        ),
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/oid4vp/request-uri/{id}")),
                )

            val catalog = createCatalog(setOf(provider))
            val dispatcher = createDispatcher(catalog, adapter)

            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/acme/oid4vp/request-uri/123"))

            assertEquals(200, response.statusCode)
            assertEquals("/acme/oid4vp/request-uri/123", capturedPath)
        }

    @Test
    fun dispatchMatchesCatalogWellKnownSuffixPolicyAndKeepsSuffixForRuntimeAdapter() =
        runTest {
            var capturedPath: String? = null
            val adapter =
                TestAdapter(
                    id = "OAUTH2_DISCOVERY",
                    adapterMount =
                        HttpAdapterMount(
                            serverPrefix = "",
                            adapterBasePath = "/",
                            tenantPathPolicy = TenantPathPolicy.WellKnownSuffix(maxDepth = 1),
                        ),
                    routeSpecs = listOf(HttpMethod.GET to "/.well-known/openid-configuration"),
                    captureNormalizedPath = { capturedPath = it },
                )
            val provider =
                TestDescriptorProvider(
                    id = "OAUTH2_DISCOVERY",
                    mount = adapter.describe().mount,
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/.well-known/openid-configuration")),
                )

            val catalog = createCatalog(setOf(provider))
            val dispatcher = createDispatcher(catalog, adapter)

            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/.well-known/openid-configuration/acme"))

            assertEquals(200, response.statusCode)
            assertEquals("/.well-known/openid-configuration/acme", capturedPath)
        }

    // ========== Specificity scoring tests ==========

    @Test
    fun dispatchSelectsMoreSpecificServerPrefixWhenAmbiguous() =
        runTest {
            // Two adapters: one with /api, one with /api/kms
            val generalAdapter =
                TestAdapter(
                    id = "GENERAL",
                    adapterMount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/keys"),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                    responseCode = 201,
                )
            val specificAdapter =
                TestAdapter(
                    id = "KMS_SPECIFIC",
                    adapterMount = HttpAdapterMount(serverPrefix = "/api/kms", adapterBasePath = "/keys"),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                    responseCode = 202,
                )
            val providers =
                setOf(
                    TestDescriptorProvider("GENERAL", generalAdapter.describe().mount, listOf(HttpEndpointDescriptor(HttpMethod.GET, "/keys"))),
                    TestDescriptorProvider("KMS_SPECIFIC", specificAdapter.describe().mount, listOf(HttpEndpointDescriptor(HttpMethod.GET, "/keys"))),
                )

            val catalog = createCatalog(providers)
            val dispatcher = createDispatcher(catalog, generalAdapter, specificAdapter)

            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/api/kms/keys"))

            // The more specific serverPrefix (/api/kms) should win
            assertEquals(202, response.statusCode)
        }

    @Test
    fun dispatchSelectsMoreSpecificEndpointPattern() =
        runTest {
            // Two routes in one adapter: /items and /items/{id}
            val adapter =
                TestAdapter(
                    id = "ITEMS_ADAPTER",
                    adapterMount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/items"),
                    routeSpecs =
                        listOf(
                            HttpMethod.GET to "/",
                            HttpMethod.GET to "/{id}",
                        ),
                )
            val provider =
                TestDescriptorProvider(
                    id = "ITEMS_ADAPTER",
                    mount = adapter.describe().mount,
                    endpoints =
                        listOf(
                            HttpEndpointDescriptor(HttpMethod.GET, "/items"),
                            HttpEndpointDescriptor(HttpMethod.GET, "/items/{id}"),
                        ),
                )

            val catalog = createCatalog(setOf(provider))
            val dispatcher = createDispatcher(catalog, adapter)

            // Request to /api/items/123 should match the more specific /items/{id}
            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/api/items/123"))

            assertEquals(200, response.statusCode)
            assertTrue(response.body?.contains("path=/items/123") == true)
        }

    // ========== Error handling tests ==========

    @Test
    fun routeNotFoundDoesNotConstructAnySessionAdapter() =
        runTest {
            var selectedConstructions = 0
            var unrelatedConstructions = 0
            val selectedProvider =
                TestDescriptorProvider(
                    id = "SELECTED_ADAPTER",
                    mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/selected"),
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/selected")),
                )
            val dispatcher =
                createDispatcher(
                    createCatalog(setOf(selectedProvider)),
                    mapOf(
                        "SELECTED_ADAPTER" to lazy {
                            selectedConstructions++
                            TestAdapter(
                                id = "SELECTED_ADAPTER",
                                adapterMount = HttpAdapterMount("/api", "/selected"),
                                routeSpecs = listOf(HttpMethod.GET to "/"),
                            )
                        },
                        "UNRELATED_ADAPTER" to lazy {
                            unrelatedConstructions++
                            TestAdapter(
                                id = "UNRELATED_ADAPTER",
                                adapterMount = HttpAdapterMount("/api", "/unrelated"),
                                routeSpecs = listOf(HttpMethod.GET to "/"),
                            )
                        },
                    ),
                )

            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/api/unknown"))

            assertEquals(404, response.statusCode)
            assertEquals(0, selectedConstructions)
            assertEquals(0, unrelatedConstructions)
        }

    @Test
    fun selectedRouteConstructsOnlyItsKeyedSessionAdapter() =
        runTest {
            var selectedConstructions = 0
            var unrelatedConstructions = 0
            val selectedProvider =
                TestDescriptorProvider(
                    id = "SELECTED_ADAPTER",
                    mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/selected"),
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/selected")),
                )
            val dispatcher =
                createDispatcher(
                    createCatalog(setOf(selectedProvider)),
                    mapOf(
                        "SELECTED_ADAPTER" to lazy {
                            selectedConstructions++
                            TestAdapter(
                                id = "SELECTED_ADAPTER",
                                adapterMount = HttpAdapterMount("/api", "/selected"),
                                routeSpecs = listOf(HttpMethod.GET to "/"),
                            )
                        },
                        "UNRELATED_ADAPTER" to lazy {
                            unrelatedConstructions++
                            TestAdapter(
                                id = "UNRELATED_ADAPTER",
                                adapterMount = HttpAdapterMount("/api", "/unrelated"),
                                routeSpecs = listOf(HttpMethod.GET to "/"),
                            )
                        },
                    ),
                )

            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/api/selected"))

            assertEquals(200, response.statusCode)
            assertEquals(1, selectedConstructions)
            assertEquals(0, unrelatedConstructions)
        }

    @Test
    fun dispatchFailsWhenKeyedAdapterHasDifferentRuntimeIdentity() =
        runTest {
            val wrongAdapter =
                TestAdapter(
                    id = "WRONG_ID",
                    adapterMount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/a"),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                )
            val provider =
                TestDescriptorProvider(
                    id = "EXPECTED_ID",
                    mount = wrongAdapter.describe().mount,
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/a")),
                )
            val catalog = createCatalog(setOf(provider))
            val dispatcher =
                createDispatcher(
                    catalog,
                    mapOf("EXPECTED_ID" to lazyOf<HttpAdapter>(wrongAdapter)),
                )

            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/api/a"))

            assertEquals(500, response.statusCode)
            assertTrue(response.body?.contains("Internal server error") == true)
            assertTrue(response.body?.contains("WRONG_ID") != true)
        }

    @Test
    fun catalogConstructionFailsWhenEndpointsCollide() {
            // Two adapters with identical mounts and endpoints — an ambiguous route. The
            // fail-fast collision guard rejects this at construction instead of returning a
            // runtime 500 only when the colliding route is hit.
            val adapter1 =
                TestAdapter(
                    id = "ADAPTER_A",
                    adapterMount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/items"),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                    responseCode = 201,
                )
            val adapter2 =
                TestAdapter(
                    id = "ADAPTER_B",
                    adapterMount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/items"),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                    responseCode = 202,
                )
            val providers =
                setOf(
                    TestDescriptorProvider("ADAPTER_A", adapter1.describe().mount, listOf(HttpEndpointDescriptor(HttpMethod.GET, "/items"))),
                    TestDescriptorProvider("ADAPTER_B", adapter2.describe().mount, listOf(HttpEndpointDescriptor(HttpMethod.GET, "/items"))),
                )

            assertFailsWith<IllegalArgumentException> { createCatalog(providers) }
    }

    @Test
    fun dispatchFailsLoudlyWhenDescriptorHasNoRuntimeAdapter() =
        runTest {
            // Catalog has a descriptor but no runtime adapter with that id. That is a wiring
            // defect, not an unserved route. The public response stays generic; the session log
            // carries the descriptor and handler identities needed for diagnosis.
            val provider =
                TestDescriptorProvider(
                    id = "MISSING_ADAPTER",
                    mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/missing"),
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/missing")),
                )

            val catalog = createCatalog(setOf(provider))

            val dispatcher = createDispatcher(catalog, emptyMap<String, Lazy<HttpAdapter>>())
            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/api/missing"))

            assertEquals(500, response.statusCode)
            assertTrue(response.body?.contains("Internal server error") == true)
            assertTrue(response.body?.contains("MISSING_ADAPTER") != true)
        }

    @Test
    fun dispatchReturns404WhenAdapterHasNoDescriptor() =
        runTest {
            // The silent-404 case this guard exists to kill: an adapter is contributed to
            // runtime adapter map but its AppScope descriptor provider was forgotten, so the catalog
            // never advertises its routes.
            val withDescriptor =
                TestAdapter(
                    id = "HAS_DESCRIPTOR",
                    adapterMount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/a"),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                )
            val orphanAdapter =
                TestAdapter(
                    id = "NO_DESCRIPTOR",
                    adapterMount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/b"),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                )
            val catalog =
                createCatalog(
                    setOf(
                        TestDescriptorProvider("HAS_DESCRIPTOR", withDescriptor.describe().mount, listOf(HttpEndpointDescriptor(HttpMethod.GET, "/a"))),
                    ),
                )

            val dispatcher = createDispatcher(catalog, withDescriptor, orphanAdapter)
            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/api/b"))

            assertEquals(404, response.statusCode)
            assertTrue(response.body?.contains("Not found") == true, "unexpected body: ${response.body}")
        }
}
