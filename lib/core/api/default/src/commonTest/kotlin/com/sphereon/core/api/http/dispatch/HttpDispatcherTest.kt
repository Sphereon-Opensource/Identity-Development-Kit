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

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.config.UniversalHttpAdapterOverride
import com.sphereon.core.api.http.config.UniversalHttpConfig
import com.sphereon.core.api.http.config.UniversalHttpDefaults
import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.TenantPathMode
import com.sphereon.core.api.http.describe.TenantResolutionPriority
import com.sphereon.core.api.testutil.createCoreApiTestAppGraph
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.di.Order
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HttpDispatcherTest {
    private fun createAppGraph() =
        createCoreApiTestAppGraph(
            this,
            "http-dispatcher-test",
            "test-profile",
            "0.0.1-TEST",
        )

    // ========== DefaultHttpAdapterCatalog Tests (via standalone creation) ==========

    @Test
    fun catalogWithEmptyProvidersHasEmptyDescriptions() {
        val appGraph = createAppGraph()
        try {
            val catalog = DefaultHttpAdapterCatalog(emptySet(), UniversalHttpConfig.DEFAULT)
            assertTrue(catalog.descriptions.isEmpty())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun catalogDescribeAllReturnsDescriptions() {
        val appGraph = createAppGraph()
        try {
            val catalog = DefaultHttpAdapterCatalog(emptySet(), UniversalHttpConfig.DEFAULT)
            val descriptions = catalog.describeAll()
            assertNotNull(descriptions)
            assertTrue(descriptions.isEmpty())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun catalogDescriptionByIdReturnsNullForUnknown() {
        val appGraph = createAppGraph()
        try {
            val catalog = DefaultHttpAdapterCatalog(emptySet(), UniversalHttpConfig.DEFAULT)
            val description = catalog.descriptionById("unknown-adapter-id")
            assertEquals(null, description)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun catalogHasDiagnostics() {
        val appGraph = createAppGraph()
        try {
            val catalog = DefaultHttpAdapterCatalog(emptySet(), UniversalHttpConfig.DEFAULT)
            assertNotNull(catalog.diagnostics)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun catalogGetOrderReturnsMedium() {
        val appGraph = createAppGraph()
        try {
            val catalog = DefaultHttpAdapterCatalog(emptySet(), UniversalHttpConfig.DEFAULT)
            assertEquals(Order.MEDIUM.orderValue, catalog.getOrder())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun catalogRequireNoCollisionsDoesNotThrowWhenEmpty() {
        val appGraph = createAppGraph()
        try {
            val catalog = DefaultHttpAdapterCatalog(emptySet(), UniversalHttpConfig.DEFAULT)
            // Should not throw if there are no collisions
            catalog.requireNoCollisions()
        } finally {
            appGraph.destroy()
        }
    }

    // ========== Config-Driven Catalog Tests ==========

    @Test
    fun catalogAppliesConfigServerPrefixOverride() {
        // Given
        val provider =
            stubDescriptorProvider(
                id = "KMS-KEYS",
                serverPrefix = "",
                adapterBasePath = "/keys",
            )
        val config =
            UniversalHttpConfig(
                overrides =
                    mapOf(
                        "KMS-KEYS" to UniversalHttpAdapterOverride(serverPrefix = "/api/kms"),
                    ),
            )

        // When
        val catalog = DefaultHttpAdapterCatalog(setOf(provider), config)

        // Then
        val desc = catalog.descriptionById("KMS-KEYS")
        assertNotNull(desc)
        assertEquals("/api/kms", desc.mount.serverPrefix)
        assertEquals("/keys", desc.mount.adapterBasePath)
    }

    @Test
    fun catalogAppliesConfigBasePathOverride() {
        // Given
        val provider =
            stubDescriptorProvider(
                id = "MY-ADAPTER",
                serverPrefix = "/api",
                adapterBasePath = "/old-path",
            )
        val config =
            UniversalHttpConfig(
                overrides =
                    mapOf(
                        "MY-ADAPTER" to UniversalHttpAdapterOverride(adapterBasePath = "/new-path"),
                    ),
            )

        // When
        val catalog = DefaultHttpAdapterCatalog(setOf(provider), config)

        // Then
        val desc = catalog.descriptionById("MY-ADAPTER")
        assertNotNull(desc)
        assertEquals("/api", desc.mount.serverPrefix)
        assertEquals("/new-path", desc.mount.adapterBasePath)
    }

    @Test
    fun catalogAppliesGlobalDefaultServerPrefix() {
        // Given - adapter has empty serverPrefix, config has global default
        val provider =
            stubDescriptorProvider(
                id = "ADAPTER-A",
                serverPrefix = "",
                adapterBasePath = "/items",
            )
        val config =
            UniversalHttpConfig(
                defaults = UniversalHttpDefaults(serverPrefix = "/api"),
            )

        // When
        val catalog = DefaultHttpAdapterCatalog(setOf(provider), config)

        // Then
        val desc = catalog.descriptionById("ADAPTER-A")
        assertNotNull(desc)
        assertEquals("/api", desc.mount.serverPrefix)
    }

    @Test
    fun catalogExcludesDisabledAdapters() {
        // Given
        val enabledProvider =
            stubDescriptorProvider(
                id = "ENABLED",
                serverPrefix = "",
                adapterBasePath = "/enabled",
            )
        val disabledProvider =
            stubDescriptorProvider(
                id = "DISABLED",
                serverPrefix = "",
                adapterBasePath = "/disabled",
            )
        val config =
            UniversalHttpConfig(
                overrides =
                    mapOf(
                        "DISABLED" to UniversalHttpAdapterOverride(enabled = false),
                    ),
            )

        // When
        val catalog = DefaultHttpAdapterCatalog(setOf(enabledProvider, disabledProvider), config)

        // Then
        assertEquals(1, catalog.descriptions.size)
        assertNotNull(catalog.descriptionById("ENABLED"))
        assertEquals(null, catalog.descriptionById("DISABLED"))
    }

    @Test
    fun catalogDefaultConfigPassesThroughOriginalMount() {
        // Given - default config with no overrides
        val provider =
            stubDescriptorProvider(
                id = "ORIGINAL",
                serverPrefix = "/my-prefix",
                adapterBasePath = "/my-path",
            )

        // When
        val catalog = DefaultHttpAdapterCatalog(setOf(provider), UniversalHttpConfig.DEFAULT)

        // Then
        val desc = catalog.descriptionById("ORIGINAL")
        assertNotNull(desc)
        assertEquals("/my-prefix", desc.mount.serverPrefix)
        assertEquals("/my-path", desc.mount.adapterBasePath)
    }

    @Test
    fun catalogPerAdapterOverrideTakesPrecedenceOverGlobalDefault() {
        // Given
        val provider =
            stubDescriptorProvider(
                id = "ADAPTER-X",
                serverPrefix = "",
                adapterBasePath = "/x",
            )
        val config =
            UniversalHttpConfig(
                defaults = UniversalHttpDefaults(serverPrefix = "/global"),
                overrides =
                    mapOf(
                        "ADAPTER-X" to UniversalHttpAdapterOverride(serverPrefix = "/specific"),
                    ),
            )

        // When
        val catalog = DefaultHttpAdapterCatalog(setOf(provider), config)

        // Then
        val desc = catalog.descriptionById("ADAPTER-X")
        assertNotNull(desc)
        assertEquals("/specific", desc.mount.serverPrefix)
    }

    private fun stubDescriptorProvider(
        id: String,
        serverPrefix: String,
        adapterBasePath: String,
    ): HttpAdapterDescriptorProvider =
        object : HttpAdapterDescriptorProvider {
            override val id: String = id

            override fun describe() =
                HttpAdapterDescription(
                    id = id,
                    mount = HttpAdapterMount(serverPrefix = serverPrefix, adapterBasePath = adapterBasePath),
                    endpoints =
                        listOf(
                            HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/", operationId = "test"),
                        ),
                )
        }

    // ========== DefaultHttpAdapterDispatcher Tests ==========

    @Test
    fun dispatcherGetOrderReturnsMedium() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionInstance = userContextInstance.sessionContextManager.createOrGetFromId("test-session")
            val dispatcher = (sessionInstance.graph as HttpAdapterDispatcher.Graph).httpAdapterDispatcher
            assertEquals(Order.MEDIUM.orderValue, dispatcher.getOrder())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun dispatcherReturns404ForUnknownPath() =
        runTest {
            val appGraph = createAppGraph()
            try {
                val userContextInstance =
                    appGraph.userContextManager.createOrGetFromInputs(
                        DefaultTenantInputString("test-tenant"),
                        DefaultPrincipalInputString("test-user"),
                    )
                val sessionInstance = userContextInstance.sessionContextManager.createOrGetFromId("test-session")
                val dispatcher = (sessionInstance.graph as HttpAdapterDispatcher.Graph).httpAdapterDispatcher

                val request =
                    GenericHttpRequest(
                        method = "GET",
                        path = "/unknown/path/that/does/not/exist",
                    )

                val response = dispatcher.dispatch(request)
                assertEquals(404, response.statusCode)
                assertTrue(response.body?.contains("Not found") == true)
            } finally {
                appGraph.destroy()
            }
        }

    // ========== HttpAdapterMount Tests ==========

    @Test
    fun httpAdapterMountDefaultTenantPathModeIsOff() {
        val appGraph = createAppGraph()
        try {
            val mount =
                HttpAdapterMount(
                    serverPrefix = "/api",
                    adapterBasePath = "/items",
                )
            assertEquals(TenantPathMode.OFF, mount.tenantPathMode)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun httpAdapterMountDefaultTenantResolutionPriority() {
        val appGraph = createAppGraph()
        try {
            val mount =
                HttpAdapterMount(
                    serverPrefix = "/api",
                    adapterBasePath = "/items",
                )
            assertEquals(TenantResolutionPriority.HEADER_THEN_PATH, mount.tenantResolutionPriority)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun httpAdapterMountWithBeforeServerPrefix() {
        val appGraph = createAppGraph()
        try {
            val mount =
                HttpAdapterMount(
                    serverPrefix = "/api",
                    adapterBasePath = "/items",
                    tenantPathMode = TenantPathMode.BEFORE_SERVER_PREFIX,
                )
            assertEquals(TenantPathMode.BEFORE_SERVER_PREFIX, mount.tenantPathMode)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun httpAdapterMountWithAfterServerPrefix() {
        val appGraph = createAppGraph()
        try {
            val mount =
                HttpAdapterMount(
                    serverPrefix = "/api",
                    adapterBasePath = "/items",
                    tenantPathMode = TenantPathMode.AFTER_SERVER_PREFIX,
                )
            assertEquals(TenantPathMode.AFTER_SERVER_PREFIX, mount.tenantPathMode)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun httpAdapterMountWithBothTenantPathMode() {
        val appGraph = createAppGraph()
        try {
            val mount =
                HttpAdapterMount(
                    serverPrefix = "/api",
                    adapterBasePath = "/items",
                    tenantPathMode = TenantPathMode.BOTH,
                )
            assertEquals(TenantPathMode.BOTH, mount.tenantPathMode)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun httpAdapterMountWithPathThenHeader() {
        val appGraph = createAppGraph()
        try {
            val mount =
                HttpAdapterMount(
                    serverPrefix = "/api",
                    adapterBasePath = "/items",
                    tenantResolutionPriority = TenantResolutionPriority.PATH_THEN_HEADER,
                )
            assertEquals(TenantResolutionPriority.PATH_THEN_HEADER, mount.tenantResolutionPriority)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== HttpAdapterDescription Tests ==========

    @Test
    fun httpAdapterDescriptionCanBeCreated() {
        val appGraph = createAppGraph()
        try {
            val description =
                HttpAdapterDescription(
                    id = "test-adapter",
                    mount =
                        HttpAdapterMount(
                            serverPrefix = "/api",
                            adapterBasePath = "/test",
                        ),
                    endpoints =
                        listOf(
                            HttpEndpointDescriptor(
                                method = HttpMethod.GET,
                                pathPattern = "/items",
                            ),
                        ),
                )
            assertEquals("test-adapter", description.id)
            assertEquals("/api", description.mount.serverPrefix)
            assertEquals(1, description.endpoints.size)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun httpAdapterDescriptionWithMultipleEndpoints() {
        val appGraph = createAppGraph()
        try {
            val description =
                HttpAdapterDescription(
                    id = "multi-endpoint-adapter",
                    mount =
                        HttpAdapterMount(
                            serverPrefix = "/api",
                            adapterBasePath = "/items",
                        ),
                    endpoints =
                        listOf(
                            HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/"),
                            HttpEndpointDescriptor(method = HttpMethod.POST, pathPattern = "/"),
                            HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/{id}"),
                            HttpEndpointDescriptor(method = HttpMethod.PUT, pathPattern = "/{id}"),
                            HttpEndpointDescriptor(method = HttpMethod.DELETE, pathPattern = "/{id}"),
                        ),
                )
            assertEquals(5, description.endpoints.size)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== HttpEndpointDescriptor Tests ==========

    @Test
    fun httpEndpointDescriptorHasMethod() {
        val appGraph = createAppGraph()
        try {
            val descriptor =
                HttpEndpointDescriptor(
                    method = HttpMethod.GET,
                    pathPattern = "/items",
                )
            assertEquals(HttpMethod.GET, descriptor.method)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun httpEndpointDescriptorHasPathPattern() {
        val appGraph = createAppGraph()
        try {
            val descriptor =
                HttpEndpointDescriptor(
                    method = HttpMethod.POST,
                    pathPattern = "/items/{itemId}",
                )
            assertEquals("/items/{itemId}", descriptor.pathPattern)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun httpEndpointDescriptorAllMethods() {
        val appGraph = createAppGraph()
        try {
            val methods =
                listOf(
                    HttpMethod.GET,
                    HttpMethod.POST,
                    HttpMethod.PUT,
                    HttpMethod.DELETE,
                    HttpMethod.PATCH,
                    HttpMethod.HEAD,
                    HttpMethod.OPTIONS,
                )
            methods.forEach { method ->
                val descriptor = HttpEndpointDescriptor(method = method, pathPattern = "/test")
                assertEquals(method, descriptor.method)
            }
        } finally {
            appGraph.destroy()
        }
    }

    // ========== HttpAdapterCatalogDiagnostics Tests ==========

    @Test
    fun catalogDiagnosticsHasCollisions() {
        val appGraph = createAppGraph()
        try {
            val diagnostics = HttpAdapterCatalogDiagnostics(collisions = emptyList())
            assertTrue(diagnostics.collisions.isEmpty())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun catalogDiagnosticsFromEmptyDescriptions() {
        val appGraph = createAppGraph()
        try {
            val diagnostics = HttpAdapterCatalogDiagnostics.from(emptyList())
            assertTrue(diagnostics.collisions.isEmpty())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun catalogDiagnosticsFromNonCollidingDescriptions() {
        val appGraph = createAppGraph()
        try {
            val descriptions =
                listOf(
                    HttpAdapterDescription(
                        id = "adapter-1",
                        mount = HttpAdapterMount(serverPrefix = "/api/v1", adapterBasePath = "/items"),
                        endpoints = listOf(HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/")),
                    ),
                    HttpAdapterDescription(
                        id = "adapter-2",
                        mount = HttpAdapterMount(serverPrefix = "/api/v2", adapterBasePath = "/items"),
                        endpoints = listOf(HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/")),
                    ),
                )
            val diagnostics = HttpAdapterCatalogDiagnostics.from(descriptions)
            assertTrue(diagnostics.collisions.isEmpty())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun catalogDiagnosticsDetectsDuplicateIds() {
        val appGraph = createAppGraph()
        try {
            val descriptions =
                listOf(
                    HttpAdapterDescription(
                        id = "same-id",
                        mount = HttpAdapterMount(serverPrefix = "/api/v1", adapterBasePath = "/items"),
                        endpoints = listOf(HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/")),
                    ),
                    HttpAdapterDescription(
                        id = "same-id",
                        mount = HttpAdapterMount(serverPrefix = "/api/v2", adapterBasePath = "/other"),
                        endpoints = listOf(HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/")),
                    ),
                )
            val diagnostics = HttpAdapterCatalogDiagnostics.from(descriptions)
            assertTrue(diagnostics.collisions.isNotEmpty())
        } finally {
            appGraph.destroy()
        }
    }

    // ========== NoOpHttpAdapter Tests ==========

    @Test
    fun noOpHttpAdapterHasId() {
        val appGraph = createAppGraph()
        try {
            val adapter = NoOpHttpAdapter()
            assertEquals(NoOpHttpAdapter.ID, adapter.id)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun noOpHttpAdapterCanDescribe() {
        val appGraph = createAppGraph()
        try {
            val adapter = NoOpHttpAdapter()
            val description = adapter.describe()
            assertNotNull(description)
            assertEquals(NoOpHttpAdapter.ID, description.id)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun noOpHttpAdapterDescriptionHasEmptyEndpoints() {
        val appGraph = createAppGraph()
        try {
            val adapter = NoOpHttpAdapter()
            val description = adapter.describe()
            assertTrue(description.endpoints.isEmpty())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun noOpHttpAdapterHandleRequestReturns404() =
        runTest {
            val appGraph = createAppGraph()
            try {
                val adapter = NoOpHttpAdapter()
                val request = GenericHttpRequest(method = "GET", path = "/test")
                val response = adapter.handleRequest(request)
                assertEquals(404, response.statusCode)
            } finally {
                appGraph.destroy()
            }
        }
}
