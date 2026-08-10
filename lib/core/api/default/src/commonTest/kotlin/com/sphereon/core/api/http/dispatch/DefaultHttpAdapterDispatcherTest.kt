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
import com.sphereon.core.api.http.RoutedHttpAdapter
import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.HttpRoute
import com.sphereon.core.api.http.describe.TenantPathMode
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
            routeSpecs.map { (method, pattern) ->
                HttpRoute(
                    endpoint = HttpEndpointDescriptor(method = method, pathPattern = pattern),
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
                endpoints = endpoints,
            )
    }

    private fun createCatalog(providers: Set<HttpAdapterDescriptorProvider>): HttpAdapterCatalog = DefaultHttpAdapterCatalog(providers, com.sphereon.core.api.http.config.UniversalHttpConfig.DEFAULT)

    /**
     * Mirrors production DI: the dispatcher's parity guard compares the runtime adapter id set
     * against the RAW descriptor-provider id set. The helper derives that provider set from the
     * catalog so the happy-path tests (paired adapter + descriptor) construct cleanly. Tests that
     * exercise the parity / collision guards pass explicit, deliberately-mismatched sets.
     */
    private fun providersFromCatalog(catalog: HttpAdapterCatalog): Set<HttpAdapterDescriptorProvider> = catalog.describeAll().map { TestDescriptorProvider(it.id, it.mount, it.endpoints) }.toSet()

    private fun createDispatcher(
        catalog: HttpAdapterCatalog,
        adapters: Set<HttpAdapter>,
        descriptorProviders: Set<HttpAdapterDescriptorProvider> = providersFromCatalog(catalog),
    ): DefaultHttpAdapterDispatcher = DefaultHttpAdapterDispatcher(catalog, adapters, descriptorProviders)

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
            val dispatcher = createDispatcher(catalog, setOf(adapter))

            val response = dispatcher.dispatch(GenericHttpRequest(method = "POST", path = "/api/commands"))

            assertEquals(200, response.statusCode)
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
            val dispatcher = createDispatcher(catalog, setOf(adapter))

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
            val dispatcher = createDispatcher(catalog, setOf(kmsAdapter, oauthAdapter))

            val kmsResponse = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/api/kms/keys"))
            assertEquals(201, kmsResponse.statusCode)

            val oauthResponse = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/oauth2/token"))
            assertEquals(202, oauthResponse.statusCode)
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
            val dispatcher = createDispatcher(catalog, setOf(adapter))

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
            val dispatcher = createDispatcher(catalog, setOf(adapter))

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
            val dispatcher = createDispatcher(catalog, setOf(adapter))

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
            val dispatcher = createDispatcher(catalog, setOf(adapter))

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
            val dispatcher = createDispatcher(catalog, setOf(adapter))

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
            val dispatcher = createDispatcher(catalog, setOf(adapter))

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
            val dispatcher = createDispatcher(catalog, setOf(adapter))

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
            val dispatcher = createDispatcher(catalog, setOf(adapter))

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
            val dispatcher = createDispatcher(catalog, setOf(adapter))

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
            val dispatcher = createDispatcher(catalog, setOf(adapter))

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
            val dispatcher = createDispatcher(catalog, setOf(adapter))

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
                            adapterBasePath = "/",
                            tenantPathPolicy = TenantPathPolicy.LeadingSlug(maxDepth = 1),
                        ),
                    routeSpecs = listOf(HttpMethod.GET to "/{tenantSlug}/oid4vp/request-uri/{id}"),
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
            val dispatcher = createDispatcher(catalog, setOf(adapter))

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
                    routeSpecs = listOf(HttpMethod.GET to "/.well-known/openid-configuration/{tenantSlug}"),
                    captureNormalizedPath = { capturedPath = it },
                )
            val provider =
                TestDescriptorProvider(
                    id = "OAUTH2_DISCOVERY",
                    mount = adapter.describe().mount,
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/.well-known/openid-configuration")),
                )

            val catalog = createCatalog(setOf(provider))
            val dispatcher = createDispatcher(catalog, setOf(adapter))

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
            val dispatcher = createDispatcher(catalog, setOf(generalAdapter, specificAdapter))

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
            val dispatcher = createDispatcher(catalog, setOf(adapter))

            // Request to /api/items/123 should match the more specific /items/{id}
            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/api/items/123"))

            assertEquals(200, response.statusCode)
            assertTrue(response.body?.contains("path=/items/123") == true)
        }

    // ========== Error handling tests ==========

    @Test
    fun constructionFailsWhenMultipleRuntimeAdaptersHaveSameId() =
        runTest {
            // Duplicate adapter IDs are rejected at construction time (fail-fast)
            val adapter1 =
                TestAdapter(
                    id = "DUPLICATE_ID",
                    adapterMount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/a"),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                )
            val adapter2 =
                TestAdapter(
                    id = "DUPLICATE_ID",
                    adapterMount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/b"),
                    routeSpecs = listOf(HttpMethod.GET to "/"),
                )
            val provider =
                TestDescriptorProvider(
                    id = "DUPLICATE_ID",
                    mount = adapter1.describe().mount,
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/a")),
                )

            val catalog = createCatalog(setOf(provider))

            val ex =
                assertFailsWith<IllegalArgumentException> {
                    createDispatcher(catalog, setOf(adapter1, adapter2))
                }
            assertTrue(ex.message?.contains("Multiple runtime HttpAdapter") == true)
        }

    @Test
    fun dispatchReturns500WhenEndpointsCollide() =
        runTest {
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

            val catalog = createCatalog(providers)

            val dispatcher = createDispatcher(catalog, setOf(adapter1, adapter2))
            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/api/items"))

            assertEquals(500, response.statusCode)
            assertTrue(response.body?.contains("Ambiguous adapter match") == true, "unexpected body: ${response.body}")
        }

    @Test
    fun dispatchFailsLoudlyWhenDescriptorHasNoRuntimeAdapter() =
        runTest {
            // Catalog has a descriptor but no runtime adapter with that id. That is a wiring
            // defect, not an unserved route: it answers 500 and names the descriptor, so it can
            // never be mistaken for an ordinary route-not-found.
            val provider =
                TestDescriptorProvider(
                    id = "MISSING_ADAPTER",
                    mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/missing"),
                    endpoints = listOf(HttpEndpointDescriptor(HttpMethod.GET, "/missing")),
                )

            val catalog = createCatalog(setOf(provider))

            val dispatcher = createDispatcher(catalog, emptySet())
            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/api/missing"))

            assertEquals(500, response.statusCode)
            assertTrue(response.body?.contains("MISSING_ADAPTER") == true, "unexpected body: ${response.body}")
        }

    @Test
    fun dispatchReturns404WhenAdapterHasNoDescriptor() =
        runTest {
            // The silent-404 case this guard exists to kill: an adapter is contributed to
            // Set<HttpAdapter> but its AppScope descriptor provider was forgotten, so the catalog
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

            val dispatcher = createDispatcher(catalog, setOf(withDescriptor, orphanAdapter))
            val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/api/b"))

            assertEquals(404, response.statusCode)
            assertTrue(response.body?.contains("Not found") == true, "unexpected body: ${response.body}")
        }
}
