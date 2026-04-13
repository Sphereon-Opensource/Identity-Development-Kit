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

package com.sphereon.core.api.http.dispatch

import com.sphereon.core.api.http.describe.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpAdapterCatalogDiagnosticsTest {

    private fun endpoint(method: HttpMethod, pattern: String) = HttpEndpointDescriptor(
        method = method,
        pathPattern = pattern
    )

    private fun description(
        id: String,
        serverPrefix: String = "",
        adapterBasePath: String = "/",
        tenantPathMode: TenantPathMode = TenantPathMode.OFF,
        vararg endpoints: HttpEndpointDescriptor
    ) = HttpAdapterDescription(
        id = id,
        mount = HttpAdapterMount(
            serverPrefix = serverPrefix,
            adapterBasePath = adapterBasePath,
            tenantPathMode = tenantPathMode
        ),
        endpoints = endpoints.toList()
    )

    @Test
    fun detectsDuplicateAdapterIds() {
        val descriptions = listOf(
            description("ADAPTER_A", endpoints = arrayOf(endpoint(HttpMethod.GET, "/foo"))),
            description("ADAPTER_A", endpoints = arrayOf(endpoint(HttpMethod.POST, "/bar"))),
            description("ADAPTER_B", endpoints = arrayOf(endpoint(HttpMethod.GET, "/baz")))
        )

        val diagnostics = HttpAdapterCatalogDiagnostics.from(descriptions)

        assertEquals(1, diagnostics.collisions.size)
        val collision = diagnostics.collisions.single()
        assertEquals(HttpAdapterCatalogCollisionType.DUPLICATE_ADAPTER_ID, collision.type)
        assertTrue(collision.message.contains("ADAPTER_A"))
        assertTrue(collision.message.contains("2 times"))
    }

    @Test
    fun detectsTriplicateAdapterIds() {
        val descriptions = listOf(
            description("ADAPTER_A"),
            description("ADAPTER_A"),
            description("ADAPTER_A")
        )

        val diagnostics = HttpAdapterCatalogDiagnostics.from(descriptions)

        assertEquals(1, diagnostics.collisions.size)
        val collision = diagnostics.collisions.single()
        assertEquals(HttpAdapterCatalogCollisionType.DUPLICATE_ADAPTER_ID, collision.type)
        assertTrue(collision.message.contains("3 times"))
    }

    @Test
    fun detectsOverlappingEndpointsOnSameMount() {
        val descriptions = listOf(
            description(
                "ADAPTER_A",
                serverPrefix = "/api",
                adapterBasePath = "/items",
                endpoints = arrayOf(endpoint(HttpMethod.GET, "/items/{id}"))
            ),
            description(
                "ADAPTER_B",
                serverPrefix = "/api",
                adapterBasePath = "/items",
                endpoints = arrayOf(endpoint(HttpMethod.GET, "/items/{id}"))
            )
        )

        val diagnostics = HttpAdapterCatalogDiagnostics.from(descriptions)

        assertEquals(1, diagnostics.collisions.size)
        val collision = diagnostics.collisions.single()
        assertEquals(HttpAdapterCatalogCollisionType.OVERLAPPING_ENDPOINT, collision.type)
        assertTrue(collision.message.contains("GET"))
        assertTrue(collision.message.contains("/items/{id}"))
        assertTrue(collision.message.contains("ADAPTER_A"))
        assertTrue(collision.message.contains("ADAPTER_B"))
    }

    @Test
    fun doesNotReportOverlappingWhenMountsDiffer() {
        val descriptions = listOf(
            description(
                "ADAPTER_A",
                serverPrefix = "/api/v1",
                adapterBasePath = "/items",
                endpoints = arrayOf(endpoint(HttpMethod.GET, "/items/{id}"))
            ),
            description(
                "ADAPTER_B",
                serverPrefix = "/api/v2",
                adapterBasePath = "/items",
                endpoints = arrayOf(endpoint(HttpMethod.GET, "/items/{id}"))
            )
        )

        val diagnostics = HttpAdapterCatalogDiagnostics.from(descriptions)

        assertTrue(diagnostics.collisions.isEmpty(), "No collisions expected when serverPrefixes differ")
    }

    @Test
    fun doesNotReportOverlappingWhenBasePathsDiffer() {
        val descriptions = listOf(
            description(
                "ADAPTER_A",
                serverPrefix = "/api",
                adapterBasePath = "/keys",
                endpoints = arrayOf(endpoint(HttpMethod.GET, "/keys/{id}"))
            ),
            description(
                "ADAPTER_B",
                serverPrefix = "/api",
                adapterBasePath = "/certs",
                endpoints = arrayOf(endpoint(HttpMethod.GET, "/certs/{id}"))
            )
        )

        val diagnostics = HttpAdapterCatalogDiagnostics.from(descriptions)

        assertTrue(diagnostics.collisions.isEmpty(), "No collisions expected when basePaths differ")
    }

    @Test
    fun doesNotReportOverlappingWhenTenantModesDiffer() {
        val descriptions = listOf(
            description(
                "ADAPTER_A",
                serverPrefix = "/api",
                adapterBasePath = "/items",
                tenantPathMode = TenantPathMode.OFF,
                endpoints = arrayOf(endpoint(HttpMethod.GET, "/items/{id}"))
            ),
            description(
                "ADAPTER_B",
                serverPrefix = "/api",
                adapterBasePath = "/items",
                tenantPathMode = TenantPathMode.BEFORE_SERVER_PREFIX,
                endpoints = arrayOf(endpoint(HttpMethod.GET, "/items/{id}"))
            )
        )

        val diagnostics = HttpAdapterCatalogDiagnostics.from(descriptions)

        assertTrue(diagnostics.collisions.isEmpty(), "No collisions expected when tenant modes differ")
    }

    @Test
    fun doesNotReportOverlappingWhenMethodsDiffer() {
        val descriptions = listOf(
            description(
                "ADAPTER_A",
                serverPrefix = "/api",
                adapterBasePath = "/items",
                endpoints = arrayOf(endpoint(HttpMethod.GET, "/items/{id}"))
            ),
            description(
                "ADAPTER_B",
                serverPrefix = "/api",
                adapterBasePath = "/items",
                endpoints = arrayOf(endpoint(HttpMethod.POST, "/items/{id}"))
            )
        )

        val diagnostics = HttpAdapterCatalogDiagnostics.from(descriptions)

        assertTrue(diagnostics.collisions.isEmpty(), "No collisions expected when methods differ")
    }

    @Test
    fun detectsDuplicateEndpointsWithinSingleAdapter() {
        val descriptions = listOf(
            description(
                "ADAPTER_A",
                endpoints = arrayOf(
                    endpoint(HttpMethod.GET, "/items"),
                    endpoint(HttpMethod.GET, "/items") // duplicate
                )
            )
        )

        val diagnostics = HttpAdapterCatalogDiagnostics.from(descriptions)

        assertEquals(1, diagnostics.collisions.size)
        val collision = diagnostics.collisions.single()
        assertEquals(HttpAdapterCatalogCollisionType.DUPLICATE_ENDPOINT_IN_ADAPTER, collision.type)
        assertTrue(collision.message.contains("ADAPTER_A"))
        assertTrue(collision.message.contains("GET"))
        assertTrue(collision.message.contains("/items"))
    }

    @Test
    fun detectsMultipleDuplicateEndpointsWithinSingleAdapter() {
        val descriptions = listOf(
            description(
                "ADAPTER_A",
                endpoints = arrayOf(
                    endpoint(HttpMethod.GET, "/items"),
                    endpoint(HttpMethod.GET, "/items"),
                    endpoint(HttpMethod.POST, "/items"),
                    endpoint(HttpMethod.POST, "/items")
                )
            )
        )

        val diagnostics = HttpAdapterCatalogDiagnostics.from(descriptions)

        assertEquals(2, diagnostics.collisions.size)
        assertTrue(diagnostics.collisions.all { it.type == HttpAdapterCatalogCollisionType.DUPLICATE_ENDPOINT_IN_ADAPTER })
    }

    @Test
    fun reportsNoCollisionsForCleanDescriptions() {
        val descriptions = listOf(
            description(
                "KMS_KEYS",
                serverPrefix = "/api/kms",
                adapterBasePath = "/keys",
                endpoints = arrayOf(
                    endpoint(HttpMethod.GET, "/keys"),
                    endpoint(HttpMethod.GET, "/keys/{keyId}"),
                    endpoint(HttpMethod.POST, "/keys")
                )
            ),
            description(
                "KMS_PROVIDERS",
                serverPrefix = "/api/kms",
                adapterBasePath = "/providers",
                endpoints = arrayOf(
                    endpoint(HttpMethod.GET, "/providers"),
                    endpoint(HttpMethod.GET, "/providers/{providerId}")
                )
            ),
            description(
                "OAUTH2_AS",
                serverPrefix = "/oauth2",
                adapterBasePath = "/",
                endpoints = arrayOf(
                    endpoint(HttpMethod.GET, "/.well-known/openid-configuration"),
                    endpoint(HttpMethod.POST, "/token")
                )
            )
        )

        val diagnostics = HttpAdapterCatalogDiagnostics.from(descriptions)

        assertTrue(diagnostics.collisions.isEmpty())
    }

    @Test
    fun detectsMultipleCollisionTypesSimultaneously() {
        val descriptions = listOf(
            // Duplicate adapter ID
            description("ADAPTER_A", serverPrefix = "/api", adapterBasePath = "/items"),
            description("ADAPTER_A", serverPrefix = "/api", adapterBasePath = "/other"),
            // Overlapping endpoints (different adapters, same mount)
            description(
                "ADAPTER_B",
                serverPrefix = "/api/v1",
                adapterBasePath = "/shared",
                endpoints = arrayOf(endpoint(HttpMethod.GET, "/shared/data"))
            ),
            description(
                "ADAPTER_C",
                serverPrefix = "/api/v1",
                adapterBasePath = "/shared",
                endpoints = arrayOf(endpoint(HttpMethod.GET, "/shared/data"))
            ),
            // Duplicate endpoint within adapter
            description(
                "ADAPTER_D",
                endpoints = arrayOf(
                    endpoint(HttpMethod.POST, "/create"),
                    endpoint(HttpMethod.POST, "/create")
                )
            )
        )

        val diagnostics = HttpAdapterCatalogDiagnostics.from(descriptions)

        assertEquals(3, diagnostics.collisions.size)
        assertTrue(diagnostics.collisions.any { it.type == HttpAdapterCatalogCollisionType.DUPLICATE_ADAPTER_ID })
        assertTrue(diagnostics.collisions.any { it.type == HttpAdapterCatalogCollisionType.OVERLAPPING_ENDPOINT })
        assertTrue(diagnostics.collisions.any { it.type == HttpAdapterCatalogCollisionType.DUPLICATE_ENDPOINT_IN_ADAPTER })
    }

    @Test
    fun normalizesPathPrefixesCorrectly() {
        // Test that paths with/without leading slashes and trailing slashes are treated equivalently
        val descriptions = listOf(
            description(
                "ADAPTER_A",
                serverPrefix = "/api/",  // trailing slash
                adapterBasePath = "items",  // no leading slash
                endpoints = arrayOf(endpoint(HttpMethod.GET, "/items/{id}"))
            ),
            description(
                "ADAPTER_B",
                serverPrefix = "api",  // no leading slash
                adapterBasePath = "/items/",  // trailing slash
                endpoints = arrayOf(endpoint(HttpMethod.GET, "/items/{id}"))
            )
        )

        val diagnostics = HttpAdapterCatalogDiagnostics.from(descriptions)

        // These should be treated as overlapping since normalized paths are the same
        assertEquals(1, diagnostics.collisions.size)
        assertEquals(HttpAdapterCatalogCollisionType.OVERLAPPING_ENDPOINT, diagnostics.collisions.single().type)
    }
}
