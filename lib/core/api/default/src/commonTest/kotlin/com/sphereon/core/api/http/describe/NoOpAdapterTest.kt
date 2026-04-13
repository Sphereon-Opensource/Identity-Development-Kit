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

package com.sphereon.core.api.http.describe

import com.sphereon.core.api.testutil.createCoreApiTestAppComponent
import com.sphereon.core.api.http.dispatch.NoOpHttpAdapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NoOpAdapterTest {

    private fun createAppComponent() = createCoreApiTestAppComponent(
        this, "noop-adapter-test", "test-profile", "0.0.1-TEST"
    )

    // ========== NoOpHttpAdapter Tests ==========

    @Test
    fun noOpHttpAdapterHasCorrectId() {
        val appComponent = createAppComponent()
        try {
            val adapter = NoOpHttpAdapter()
            assertEquals(NoOpHttpAdapter.ID, adapter.id)
            assertEquals("__noop__", adapter.id)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun noOpHttpAdapterCanDescribe() {
        val appComponent = createAppComponent()
        try {
            val adapter = NoOpHttpAdapter()
            val description = adapter.describe()
            assertNotNull(description)
            assertEquals(NoOpHttpAdapter.ID, description.id)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun noOpHttpAdapterDescriptionHasEmptyMount() {
        val appComponent = createAppComponent()
        try {
            val adapter = NoOpHttpAdapter()
            val description = adapter.describe()
            assertEquals("", description.mount.serverPrefix)
            assertEquals("", description.mount.adapterBasePath)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun noOpHttpAdapterDescriptionHasEmptyEndpoints() {
        val appComponent = createAppComponent()
        try {
            val adapter = NoOpHttpAdapter()
            val description = adapter.describe()
            assertTrue(description.endpoints.isEmpty(), "NoOpHttpAdapter should have no endpoints")
        } finally {
            appComponent.destroy()
        }
    }

    // ========== NoOpAdapterDescriptorProvider Tests ==========

    @Test
    fun noOpAdapterDescriptorProviderHasCorrectId() {
        val appComponent = createAppComponent()
        try {
            val provider = NoOpAdapterDescriptorProvider()
            assertEquals(NoOpAdapterDescriptorProvider.ID, provider.id)
            assertEquals("__NO_OP__", provider.id)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun noOpAdapterDescriptorProviderCanDescribe() {
        val appComponent = createAppComponent()
        try {
            val provider = NoOpAdapterDescriptorProvider()
            val description = provider.describe()
            assertNotNull(description)
            assertEquals(NoOpAdapterDescriptorProvider.ID, description.id)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun noOpAdapterDescriptorProviderDescriptionHasEmptyMount() {
        val appComponent = createAppComponent()
        try {
            val provider = NoOpAdapterDescriptorProvider()
            val description = provider.describe()
            assertEquals("", description.mount.serverPrefix)
            assertEquals("", description.mount.adapterBasePath)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun noOpAdapterDescriptorProviderDescriptionHasEmptyEndpoints() {
        val appComponent = createAppComponent()
        try {
            val provider = NoOpAdapterDescriptorProvider()
            val description = provider.describe()
            assertTrue(description.endpoints.isEmpty(), "NoOpAdapterDescriptorProvider should have no endpoints")
        } finally {
            appComponent.destroy()
        }
    }

    // ========== HttpAdapterMount Tests ==========

    @Test
    fun httpAdapterMountDefaultValues() {
        val appComponent = createAppComponent()
        try {
            val mount = HttpAdapterMount(
                serverPrefix = "/api",
                adapterBasePath = "/items"
            )
            assertEquals("/api", mount.serverPrefix)
            assertEquals("/items", mount.adapterBasePath)
            assertEquals(TenantPathMode.OFF, mount.tenantPathMode)
            assertEquals(TenantResolutionPriority.HEADER_THEN_PATH, mount.tenantResolutionPriority)
            assertEquals("/t/{tenantId}", mount.tenantSegmentPattern)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun httpAdapterMountWithTenantPathMode() {
        val appComponent = createAppComponent()
        try {
            val mount = HttpAdapterMount(
                serverPrefix = "/api",
                adapterBasePath = "/items",
                tenantPathMode = TenantPathMode.BEFORE_SERVER_PREFIX
            )
            assertEquals(TenantPathMode.BEFORE_SERVER_PREFIX, mount.tenantPathMode)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun httpAdapterMountWithCustomTenantSegment() {
        val appComponent = createAppComponent()
        try {
            val mount = HttpAdapterMount(
                serverPrefix = "/api",
                adapterBasePath = "/items",
                tenantSegmentPattern = "/tenant/{tenantId}"
            )
            assertEquals("/tenant/{tenantId}", mount.tenantSegmentPattern)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== TenantPathMode Tests ==========

    @Test
    fun tenantPathModeValuesExist() {
        val appComponent = createAppComponent()
        try {
            assertNotNull(TenantPathMode.OFF)
            assertNotNull(TenantPathMode.BEFORE_SERVER_PREFIX)
            assertNotNull(TenantPathMode.AFTER_SERVER_PREFIX)
            assertNotNull(TenantPathMode.BOTH)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== TenantResolutionPriority Tests ==========

    @Test
    fun tenantResolutionPriorityValuesExist() {
        val appComponent = createAppComponent()
        try {
            assertNotNull(TenantResolutionPriority.HEADER_THEN_PATH)
            assertNotNull(TenantResolutionPriority.PATH_THEN_HEADER)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== HttpRoute Builder Tests ==========

    @Test
    fun httpRoutesBuilderCreatesEmptyRoutes() {
        val appComponent = createAppComponent()
        try {
            val routes = httpRoutes { }
            assertTrue(routes.isEmpty())
        } finally {
            appComponent.destroy()
        }
    }

    // ========== MediaType Tests ==========

    @Test
    fun mediaTypeApplicationJsonExists() {
        val appComponent = createAppComponent()
        try {
            assertNotNull(MediaType.ApplicationJson)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun mediaTypeCustomCanBeCreated() {
        val appComponent = createAppComponent()
        try {
            val customType = MediaType.Custom("application/vnd.custom+json")
            assertNotNull(customType)
            assertEquals("application/vnd.custom+json", customType.value)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun mediaTypeMatchesWorks() {
        val appComponent = createAppComponent()
        try {
            val jsonWithCharset = MediaType.Custom("application/json; charset=utf-8")
            assertTrue(MediaType.ApplicationJson.matches(jsonWithCharset))
        } finally {
            appComponent.destroy()
        }
    }
}
