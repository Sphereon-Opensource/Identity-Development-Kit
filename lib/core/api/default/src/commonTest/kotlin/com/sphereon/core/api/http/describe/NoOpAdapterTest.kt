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

package com.sphereon.core.api.http.describe

import com.sphereon.core.api.http.dispatch.NoOpHttpAdapter
import com.sphereon.core.api.testutil.createCoreApiTestAppGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NoOpAdapterTest {
    private fun createAppGraph() =
        createCoreApiTestAppGraph(
            this,
            "noop-adapter-test",
            "test-profile",
            "0.0.1-TEST",
        )

    // ========== NoOpHttpAdapter Tests ==========

    @Test
    fun noOpHttpAdapterHasCorrectId() {
        val appGraph = createAppGraph()
        try {
            val adapter = NoOpHttpAdapter()
            assertEquals(NoOpHttpAdapter.ID, adapter.id)
            assertEquals("__noop__", adapter.id)
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
    fun noOpHttpAdapterDescriptionHasEmptyMount() {
        val appGraph = createAppGraph()
        try {
            val adapter = NoOpHttpAdapter()
            val description = adapter.describe()
            assertEquals("", description.mount.serverPrefix)
            assertEquals("", description.mount.adapterBasePath)
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
            assertTrue(description.endpoints.isEmpty(), "NoOpHttpAdapter should have no endpoints")
        } finally {
            appGraph.destroy()
        }
    }

    // ========== HttpAdapterMount Tests ==========

    @Test
    fun httpAdapterMountDefaultValues() {
        val appGraph = createAppGraph()
        try {
            val mount =
                HttpAdapterMount(
                    serverPrefix = "/api",
                    adapterBasePath = "/items",
                )
            assertEquals("/api", mount.serverPrefix)
            assertEquals("/items", mount.adapterBasePath)
            assertEquals(TenantPathMode.OFF, mount.tenantPathMode)
            assertEquals("/t/{tenantId}", mount.tenantSegmentPattern)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun httpAdapterMountWithTenantPathMode() {
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
    fun httpAdapterMountWithCustomTenantSegment() {
        val appGraph = createAppGraph()
        try {
            val mount =
                HttpAdapterMount(
                    serverPrefix = "/api",
                    adapterBasePath = "/items",
                    tenantSegmentPattern = "/tenant/{tenantId}",
                )
            assertEquals("/tenant/{tenantId}", mount.tenantSegmentPattern)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== TenantPathMode Tests ==========

    @Test
    fun tenantPathModeValuesExist() {
        val appGraph = createAppGraph()
        try {
            assertNotNull(TenantPathMode.OFF)
            assertNotNull(TenantPathMode.BEFORE_SERVER_PREFIX)
            assertNotNull(TenantPathMode.AFTER_SERVER_PREFIX)
            assertNotNull(TenantPathMode.BOTH)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== HttpRoute Builder Tests ==========

    @Test
    fun httpRoutesBuilderCreatesEmptyRoutes() {
        val appGraph = createAppGraph()
        try {
            val routes = httpRoutes { }
            assertTrue(routes.isEmpty())
        } finally {
            appGraph.destroy()
        }
    }

    // ========== MediaType Tests ==========

    @Test
    fun mediaTypeApplicationJsonExists() {
        val appGraph = createAppGraph()
        try {
            assertNotNull(MediaType.ApplicationJson)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun mediaTypeCustomCanBeCreated() {
        val appGraph = createAppGraph()
        try {
            val customType = MediaType.Custom("application/vnd.custom+json")
            assertNotNull(customType)
            assertEquals("application/vnd.custom+json", customType.value)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun mediaTypeMatchesWorks() {
        val appGraph = createAppGraph()
        try {
            val jsonWithCharset = MediaType.Custom("application/json; charset=utf-8")
            assertTrue(MediaType.ApplicationJson.matches(jsonWithCharset))
        } finally {
            appGraph.destroy()
        }
    }
}
