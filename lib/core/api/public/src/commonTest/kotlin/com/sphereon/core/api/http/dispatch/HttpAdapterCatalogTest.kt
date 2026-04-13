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

package com.sphereon.core.api.http.dispatch

import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.TenantPathMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpAdapterCatalogCollisionTypeTest {
    @Test
    fun enumHasThreeValues() {
        assertEquals(3, HttpAdapterCatalogCollisionType.entries.size)
    }

    @Test
    fun duplicateAdapterIdExists() {
        val type = HttpAdapterCatalogCollisionType.DUPLICATE_ADAPTER_ID
        assertEquals("DUPLICATE_ADAPTER_ID", type.name)
    }

    @Test
    fun overlappingEndpointExists() {
        val type = HttpAdapterCatalogCollisionType.OVERLAPPING_ENDPOINT
        assertEquals("OVERLAPPING_ENDPOINT", type.name)
    }

    @Test
    fun duplicateEndpointInAdapterExists() {
        val type = HttpAdapterCatalogCollisionType.DUPLICATE_ENDPOINT_IN_ADAPTER
        assertEquals("DUPLICATE_ENDPOINT_IN_ADAPTER", type.name)
    }
}

class HttpAdapterCatalogCollisionTest {
    @Test
    fun collisionHasType() {
        val collision =
            HttpAdapterCatalogCollision(
                type = HttpAdapterCatalogCollisionType.DUPLICATE_ADAPTER_ID,
                message = "test message",
            )
        assertEquals(HttpAdapterCatalogCollisionType.DUPLICATE_ADAPTER_ID, collision.type)
    }

    @Test
    fun collisionHasMessage() {
        val collision =
            HttpAdapterCatalogCollision(
                type = HttpAdapterCatalogCollisionType.DUPLICATE_ADAPTER_ID,
                message = "test message",
            )
        assertEquals("test message", collision.message)
    }

    @Test
    fun collisionsAreEqual() {
        val collision1 =
            HttpAdapterCatalogCollision(
                type = HttpAdapterCatalogCollisionType.DUPLICATE_ADAPTER_ID,
                message = "test",
            )
        val collision2 =
            HttpAdapterCatalogCollision(
                type = HttpAdapterCatalogCollisionType.DUPLICATE_ADAPTER_ID,
                message = "test",
            )
        assertEquals(collision1, collision2)
    }
}

class HttpAdapterCatalogDiagnosticsTest {
    @Test
    fun emptyDescriptionsHasNoCollisions() {
        val diagnostics = HttpAdapterCatalogDiagnostics.from(emptyList())
        assertTrue(diagnostics.collisions.isEmpty())
    }

    @Test
    fun singleDescriptionHasNoCollisions() {
        val description = createTestDescription("adapter-1", "/api/v1")
        val diagnostics = HttpAdapterCatalogDiagnostics.from(listOf(description))
        assertTrue(diagnostics.collisions.isEmpty())
    }

    @Test
    fun duplicateAdapterIdsDetected() {
        val desc1 = createTestDescription("adapter-1", "/api/v1")
        val desc2 = createTestDescription("adapter-1", "/api/v2")
        val diagnostics = HttpAdapterCatalogDiagnostics.from(listOf(desc1, desc2))

        val duplicateIdCollisions =
            diagnostics.collisions.filter {
                it.type == HttpAdapterCatalogCollisionType.DUPLICATE_ADAPTER_ID
            }
        assertEquals(1, duplicateIdCollisions.size)
        assertTrue(duplicateIdCollisions[0].message.contains("adapter-1"))
    }

    @Test
    fun overlappingEndpointsDetected() {
        val endpoint =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/users/{id}",
            )
        val mount =
            HttpAdapterMount(
                serverPrefix = "",
                tenantPathMode = TenantPathMode.OFF,
                adapterBasePath = "/api",
            )
        val desc1 =
            HttpAdapterDescription(
                id = "adapter-1",
                mount = mount,
                endpoints = listOf(endpoint),
            )
        val desc2 =
            HttpAdapterDescription(
                id = "adapter-2",
                mount = mount,
                endpoints = listOf(endpoint),
            )
        val diagnostics = HttpAdapterCatalogDiagnostics.from(listOf(desc1, desc2))

        val overlappingCollisions =
            diagnostics.collisions.filter {
                it.type == HttpAdapterCatalogCollisionType.OVERLAPPING_ENDPOINT
            }
        assertEquals(1, overlappingCollisions.size)
    }

    @Test
    fun duplicateEndpointInSameAdapterDetected() {
        val endpoint =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/users/{id}",
            )
        val mount =
            HttpAdapterMount(
                serverPrefix = "",
                tenantPathMode = TenantPathMode.OFF,
                adapterBasePath = "/api",
            )
        val description =
            HttpAdapterDescription(
                id = "adapter-1",
                mount = mount,
                endpoints = listOf(endpoint, endpoint),
            )
        val diagnostics = HttpAdapterCatalogDiagnostics.from(listOf(description))

        val duplicateInAdapterCollisions =
            diagnostics.collisions.filter {
                it.type == HttpAdapterCatalogCollisionType.DUPLICATE_ENDPOINT_IN_ADAPTER
            }
        assertEquals(1, duplicateInAdapterCollisions.size)
        assertTrue(duplicateInAdapterCollisions[0].message.contains("adapter-1"))
    }

    @Test
    fun differentEndpointsNoCollisions() {
        val endpoint1 =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/users/{id}",
            )
        val endpoint2 =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/users",
            )
        val mount =
            HttpAdapterMount(
                serverPrefix = "",
                tenantPathMode = TenantPathMode.OFF,
                adapterBasePath = "/api",
            )
        val desc1 =
            HttpAdapterDescription(
                id = "adapter-1",
                mount = mount,
                endpoints = listOf(endpoint1),
            )
        val desc2 =
            HttpAdapterDescription(
                id = "adapter-2",
                mount = mount,
                endpoints = listOf(endpoint2),
            )
        val diagnostics = HttpAdapterCatalogDiagnostics.from(listOf(desc1, desc2))

        val overlappingCollisions =
            diagnostics.collisions.filter {
                it.type == HttpAdapterCatalogCollisionType.OVERLAPPING_ENDPOINT
            }
        assertTrue(overlappingCollisions.isEmpty())
    }

    private fun createTestDescription(
        id: String,
        basePath: String,
    ): HttpAdapterDescription {
        val endpoint =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/test",
            )
        val mount =
            HttpAdapterMount(
                serverPrefix = "",
                tenantPathMode = TenantPathMode.OFF,
                adapterBasePath = basePath,
            )
        return HttpAdapterDescription(
            id = id,
            mount = mount,
            endpoints = listOf(endpoint),
        )
    }
}

class NormalizePathPrefixTest {
    @Test
    fun emptyStringReturnsEmpty() {
        assertEquals("", normalizePathPrefix(""))
    }

    @Test
    fun whitespaceOnlyReturnsEmpty() {
        assertEquals("", normalizePathPrefix("   "))
    }

    @Test
    fun addsLeadingSlash() {
        assertEquals("/api", normalizePathPrefix("api"))
    }

    @Test
    fun preservesLeadingSlash() {
        assertEquals("/api", normalizePathPrefix("/api"))
    }

    @Test
    fun removesTrailingSlash() {
        assertEquals("/api", normalizePathPrefix("/api/"))
    }

    @Test
    fun removesTrailingSlashWithoutLeading() {
        assertEquals("/api", normalizePathPrefix("api/"))
    }

    @Test
    fun handlesMultipleSegments() {
        assertEquals("/api/v1", normalizePathPrefix("/api/v1"))
    }

    @Test
    fun handlesMultipleSegmentsWithTrailingSlash() {
        assertEquals("/api/v1", normalizePathPrefix("/api/v1/"))
    }

    @Test
    fun trimsWhitespace() {
        assertEquals("/api", normalizePathPrefix("  /api  "))
    }

    @Test
    fun singleSlashReturnsEmpty() {
        assertEquals("", normalizePathPrefix("/"))
    }
}
