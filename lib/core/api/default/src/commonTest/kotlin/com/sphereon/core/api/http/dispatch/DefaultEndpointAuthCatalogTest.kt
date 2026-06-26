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

import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.config.UniversalHttpConfig
import com.sphereon.core.api.http.describe.EndpointAuthPolicy
import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultEndpointAuthCatalogTest {
    private class FixedProvider(
        override val id: String,
        private val description: HttpAdapterDescription,
    ) : HttpAdapterDescriptorProvider {
        override fun describe(): HttpAdapterDescription = description
    }

    private fun catalog(vararg descriptions: HttpAdapterDescription): DefaultEndpointAuthCatalog =
        DefaultEndpointAuthCatalog(
            descriptions.map { FixedProvider(it.id, it) }.toSet(),
            UniversalHttpConfig.DEFAULT,
        )

    @Test
    fun publicEndpointIsPublic() {
        val cat =
            catalog(
                HttpAdapterDescription(
                    id = "discovery",
                    mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/.well-known"),
                    endpoints =
                        listOf(
                            HttpEndpointDescriptor(
                                method = HttpMethod.GET,
                                pathPattern = "/.well-known/openid-configuration",
                                authPolicy = EndpointAuthPolicy.PUBLIC,
                            ),
                        ),
                ),
            )
        assertTrue(cat.isPublic("GET", "/.well-known/openid-configuration"))
    }

    @Test
    fun protectedEndpointIsNotPublic() {
        val cat =
            catalog(
                HttpAdapterDescription(
                    id = "tenants",
                    mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/api/platform/admin/v1/tenants"),
                    endpoints =
                        listOf(
                            // No authPolicy -> defaults to PROTECTED.
                            HttpEndpointDescriptor(
                                method = HttpMethod.GET,
                                pathPattern = "/api/platform/admin/v1/tenants",
                            ),
                        ),
                ),
            )
        assertFalse(cat.isPublic("GET", "/api/platform/admin/v1/tenants"))
    }

    @Test
    fun unmatchedPathFailsClosed() {
        val cat =
            catalog(
                HttpAdapterDescription(
                    id = "discovery",
                    mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/.well-known"),
                    endpoints =
                        listOf(
                            HttpEndpointDescriptor(
                                method = HttpMethod.GET,
                                pathPattern = "/.well-known/openid-configuration",
                                authPolicy = EndpointAuthPolicy.PUBLIC,
                            ),
                        ),
                ),
            )
        assertFalse(cat.isPublic("GET", "/api/platform/admin/v1/tenants"))
        // Method mismatch is also not public.
        assertFalse(cat.isPublic("POST", "/.well-known/openid-configuration"))
    }

    @Test
    fun methodIsMatchedCaseInsensitively() {
        val cat =
            catalog(
                HttpAdapterDescription(
                    id = "discovery",
                    mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/.well-known"),
                    endpoints =
                        listOf(
                            HttpEndpointDescriptor(
                                method = HttpMethod.GET,
                                pathPattern = "/.well-known/openid-configuration",
                                authPolicy = EndpointAuthPolicy.PUBLIC,
                            ),
                        ),
                ),
            )
        assertTrue(cat.isPublic("get", "/.well-known/openid-configuration"))
    }

    @Test
    fun publicRouteStaysPublicUnderLeadingSlugTenant() {
        val cat =
            catalog(
                HttpAdapterDescription(
                    id = "oid4vp",
                    mount =
                        HttpAdapterMount(
                            serverPrefix = "",
                            adapterBasePath = "/oid4vp",
                            tenantPathPolicy = TenantPathPolicy.LeadingSlug(maxDepth = 1),
                        ),
                    endpoints =
                        listOf(
                            HttpEndpointDescriptor(
                                method = HttpMethod.GET,
                                pathPattern = "/oid4vp/request-uri/{id}",
                                authPolicy = EndpointAuthPolicy.PUBLIC,
                            ),
                        ),
                ),
            )
        // Tenant-scoped URL: the leading "acme" slug is peeled before matching.
        assertTrue(cat.isPublic("GET", "/acme/oid4vp/request-uri/123"))
        // And the non-slugged form still matches.
        assertTrue(cat.isPublic("GET", "/oid4vp/request-uri/123"))
    }
}
