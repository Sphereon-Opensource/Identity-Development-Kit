/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.core.api.cache

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class DefaultCacheManagerTest {

    private fun createManager(): DefaultCacheManager {
        val manager = DefaultCacheManager()
        manager.registerBackend(KacheCacheBackend())
        return manager
    }

    @Test
    fun registerBackendAddsBackend() {
        val manager = DefaultCacheManager()
        val backend = KacheCacheBackend()

        assertEquals(0, manager.getBackends().size)

        manager.registerBackend(backend)

        assertEquals(1, manager.getBackends().size)
        assertEquals("kache", manager.getBackends().first().id)
    }

    @Test
    fun getBackendReturnsRegisteredBackend() {
        val manager = createManager()

        val backend = manager.getBackend("kache")

        assertNotNull(backend)
        assertEquals("kache", backend.id)
    }

    @Test
    fun getBackendReturnsNullForMissingBackend() {
        val manager = createManager()

        assertNull(manager.getBackend("redis"))
    }

    @Test
    fun hasLocalBackendReturnsTrueWithKache() {
        val manager = createManager()

        assertTrue(manager.hasLocalBackend())
    }

    @Test
    fun hasDistributedBackendReturnsFalseWithOnlyKache() {
        val manager = createManager()

        assertFalse(manager.hasDistributedBackend())
    }

    @Test
    fun registerNamespaceStoresRequirements() {
        val manager = createManager()
        val requirements = CacheRequirements.localOnly("config")

        manager.registerNamespace(requirements)

        val stored = manager.getRequirements("config")
        assertNotNull(stored)
        assertEquals("config", stored.namespace)
        assertEquals(CacheLocality.LOCAL_ONLY, stored.locality)
    }

    @Test
    fun getNamespacesReturnsRegisteredNamespaces() {
        val manager = createManager()

        manager.registerNamespace(CacheRequirements.localOnly("config"))
        manager.registerNamespace(CacheRequirements.localOnly("tokens"))

        val namespaces = manager.getNamespaces()

        assertTrue(namespaces.contains("config"))
        assertTrue(namespaces.contains("tokens"))
    }

    @Test
    fun createCacheReturnsWorkingCache() = runTest {
        val manager = createManager()
        val requirements = CacheRequirements.localOnly("test")

        val cache = manager.createCache(
            requirements,
            CacheSerializers.string,
            CacheSerializers.string
        )

        assertNotNull(cache)
        assertEquals("test", cache.namespace)

        // Test basic operations
        cache.putApp("key1", "value1")
        assertEquals("value1", cache.getApp("key1"))
    }

    @Test
    fun createCacheReturnsSameCacheForSameNamespace() = runTest {
        val manager = createManager()
        val requirements = CacheRequirements.localOnly("test")

        val cache1 = manager.createCache(requirements, CacheSerializers.string, CacheSerializers.string)
        val cache2 = manager.createCache(requirements, CacheSerializers.string, CacheSerializers.string)

        // Should return the same instance
        assertTrue(cache1 === cache2)
    }

    @Test
    fun getCacheReturnsCreatedCache() = runTest {
        val manager = createManager()
        val requirements = CacheRequirements.localOnly("test")

        manager.createCache(requirements, CacheSerializers.string, CacheSerializers.string)

        val cache = manager.getCache<String, String>("test")

        assertNotNull(cache)
        assertEquals("test", cache.namespace)
    }

    @Test
    fun getCacheReturnsNullForUnregisteredNamespace() = runTest {
        val manager = createManager()

        assertNull(manager.getCache<String, String>("unregistered"))
    }

    @Test
    fun getCacheCreatesLazilyFromRegisteredRequirements() = runTest {
        val manager = createManager()

        // Register requirements without creating cache
        manager.registerNamespace(CacheRequirements.localOnly("lazy"))

        // Get should create the cache
        val cache = manager.getCache<String, String>("lazy")

        assertNotNull(cache)
        assertEquals("lazy", cache.namespace)
    }

    @Test
    fun getAllCachesReturnsCreatedCaches() = runTest {
        val manager = createManager()

        manager.createCache(CacheRequirements.localOnly("cache1"), CacheSerializers.string, CacheSerializers.string)
        manager.createCache(CacheRequirements.localOnly("cache2"), CacheSerializers.string, CacheSerializers.string)

        val caches = manager.getAllCaches()

        assertEquals(2, caches.size)
        assertTrue(caches.any { it.namespace == "cache1" })
        assertTrue(caches.any { it.namespace == "cache2" })
    }

    @Test
    fun invalidateTenantInvalidatesAllCaches() = runTest {
        val manager = createManager()

        val cache1 = manager.createCache(CacheRequirements.localOnly("cache1"), CacheSerializers.string, CacheSerializers.string)
        val cache2 = manager.createCache(CacheRequirements.localOnly("cache2"), CacheSerializers.string, CacheSerializers.string)

        // Add tenant-scoped entries
        cache1.putTenant("tenant-a", "key1", "value1")
        cache2.putTenant("tenant-a", "key2", "value2")

        // Also add entries for another tenant
        cache1.putTenant("tenant-b", "key3", "value3")

        // Invalidate tenant-a
        manager.invalidateTenant("tenant-a")

        // tenant-a entries should be gone
        assertNull(cache1.getTenant("tenant-a", "key1"))
        assertNull(cache2.getTenant("tenant-a", "key2"))

        // tenant-b entries should still exist
        assertNotNull(cache1.getTenant("tenant-b", "key3"))
    }

    @Test
    fun clearAllClearsAllCaches() = runTest {
        val manager = createManager()

        val cache1 = manager.createCache(CacheRequirements.localOnly("cache1"), CacheSerializers.string, CacheSerializers.string)
        val cache2 = manager.createCache(CacheRequirements.localOnly("cache2"), CacheSerializers.string, CacheSerializers.string)

        cache1.putApp("key1", "value1")
        cache2.putApp("key2", "value2")

        manager.clearAll()

        assertNull(cache1.getApp("key1"))
        assertNull(cache2.getApp("key2"))
    }

    @Test
    fun isHealthyReturnsTrueWhenAllBackendsHealthy() = runTest {
        val manager = createManager()

        assertTrue(manager.isHealthy())
    }

    @Test
    fun aggregateStatsReturnsStatsPerCache() = runTest {
        val manager = createManager()

        val cache1 = manager.createCache(CacheRequirements.localOnly("cache1"), CacheSerializers.string, CacheSerializers.string)
        val cache2 = manager.createCache(CacheRequirements.localOnly("cache2"), CacheSerializers.string, CacheSerializers.string)

        // Generate some hits/misses
        cache1.getApp("missing") // miss
        cache1.putApp("key", "value")
        cache1.getApp("key") // hit

        val stats = manager.aggregateStats()

        assertEquals(2, stats.size)
        assertTrue(stats.containsKey("cache1"))
        assertTrue(stats.containsKey("cache2"))
    }
}
