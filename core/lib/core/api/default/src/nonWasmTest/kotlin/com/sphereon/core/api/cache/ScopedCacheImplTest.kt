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

package com.sphereon.core.api.cache

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ScopedCacheImplTest {
    private fun createCache(namespace: String = "test"): ScopedCacheImpl<String, String> =
        ScopedCacheImpl(
            namespace = namespace,
            backend = KacheCacheBackend(),
            keySerializer = CacheSerializers.string,
            valueSerializer = CacheSerializers.string,
            ttlConfig = CacheTtlConfig.DEFAULT,
        )

    // ========== Basic Operations ==========

    @Test
    fun putAndGetAppScopeWorks() =
        runTest {
            val cache = createCache()

            cache.putApp("key1", "value1")
            val result = cache.getApp("key1")

            assertEquals("value1", result)
        }

    @Test
    fun putAndGetTenantScopeWorks() =
        runTest {
            val cache = createCache()

            cache.putTenant("tenant-a", "key1", "value1")
            val result = cache.getTenant("tenant-a", "key1")

            assertEquals("value1", result)
        }

    @Test
    fun putAndGetPrincipalScopeWorks() =
        runTest {
            val cache = createCache()

            cache.putPrincipal("tenant-a", "user-1", "key1", "value1")
            val result = cache.getPrincipal("tenant-a", "user-1", "key1")

            assertEquals("value1", result)
        }

    @Test
    fun scopesAreIsolated() =
        runTest {
            val cache = createCache()

            cache.putApp("key", "app-value")
            cache.putTenant("tenant-a", "key", "tenant-value")
            cache.putPrincipal("tenant-a", "user-1", "key", "principal-value")

            // Each scope should have its own value
            assertEquals("app-value", cache.getApp("key"))
            assertEquals("tenant-value", cache.getTenant("tenant-a", "key"))
            assertEquals("principal-value", cache.getPrincipal("tenant-a", "user-1", "key"))
        }

    @Test
    fun tenantsAreIsolated() =
        runTest {
            val cache = createCache()

            cache.putTenant("tenant-a", "key", "value-a")
            cache.putTenant("tenant-b", "key", "value-b")

            assertEquals("value-a", cache.getTenant("tenant-a", "key"))
            assertEquals("value-b", cache.getTenant("tenant-b", "key"))
        }

    @Test
    fun principalsAreIsolated() =
        runTest {
            val cache = createCache()

            cache.putPrincipal("tenant-a", "user-1", "key", "value-1")
            cache.putPrincipal("tenant-a", "user-2", "key", "value-2")

            assertEquals("value-1", cache.getPrincipal("tenant-a", "user-1", "key"))
            assertEquals("value-2", cache.getPrincipal("tenant-a", "user-2", "key"))
        }

    // ========== Remove Operations ==========

    @Test
    fun removeAppWorks() =
        runTest {
            val cache = createCache()

            cache.putApp("key", "value")
            assertTrue(cache.containsApp("key"))

            val removed = cache.removeApp("key")
            assertTrue(removed)
            assertFalse(cache.containsApp("key"))
        }

    @Test
    fun removeTenantWorks() =
        runTest {
            val cache = createCache()

            cache.putTenant("tenant-a", "key", "value")
            assertTrue(cache.containsTenant("tenant-a", "key"))

            val removed = cache.removeTenant("tenant-a", "key")
            assertTrue(removed)
            assertFalse(cache.containsTenant("tenant-a", "key"))
        }

    @Test
    fun removePrincipalWorks() =
        runTest {
            val cache = createCache()

            cache.putPrincipal("tenant-a", "user-1", "key", "value")
            assertTrue(cache.containsPrincipal("tenant-a", "user-1", "key"))

            val removed = cache.removePrincipal("tenant-a", "user-1", "key")
            assertTrue(removed)
            assertFalse(cache.containsPrincipal("tenant-a", "user-1", "key"))
        }

    // ========== Contains Operations ==========

    @Test
    fun containsReturnsFalseForMissingKey() =
        runTest {
            val cache = createCache()

            assertFalse(cache.containsApp("missing"))
            assertFalse(cache.containsTenant("tenant-a", "missing"))
            assertFalse(cache.containsPrincipal("tenant-a", "user-1", "missing"))
        }

    // ========== Bulk Invalidation ==========

    @Test
    fun invalidateTenantRemovesTenantAndPrincipalEntries() =
        runTest {
            val cache = createCache()

            cache.putApp("key", "app-value")
            cache.putTenant("tenant-a", "key", "tenant-a-value")
            cache.putPrincipal("tenant-a", "user-1", "key", "principal-value")
            cache.putTenant("tenant-b", "key", "tenant-b-value")

            cache.invalidateTenant("tenant-a")

            // App should still exist
            assertEquals("app-value", cache.getApp("key"))
            // Tenant-a entries should be gone
            assertNull(cache.getTenant("tenant-a", "key"))
            assertNull(cache.getPrincipal("tenant-a", "user-1", "key"))
            // Tenant-b should still exist
            assertEquals("tenant-b-value", cache.getTenant("tenant-b", "key"))
        }

    @Test
    fun invalidatePrincipalRemovesOnlyPrincipalEntries() =
        runTest {
            val cache = createCache()

            cache.putTenant("tenant-a", "key", "tenant-value")
            cache.putPrincipal("tenant-a", "user-1", "key", "user-1-value")
            cache.putPrincipal("tenant-a", "user-2", "key", "user-2-value")

            cache.invalidatePrincipal("tenant-a", "user-1")

            // Tenant should still exist
            assertEquals("tenant-value", cache.getTenant("tenant-a", "key"))
            // User-1 should be gone
            assertNull(cache.getPrincipal("tenant-a", "user-1", "key"))
            // User-2 should still exist
            assertEquals("user-2-value", cache.getPrincipal("tenant-a", "user-2", "key"))
        }

    @Test
    fun clearRemovesAllEntries() =
        runTest {
            val cache = createCache()

            cache.putApp("key1", "value1")
            cache.putTenant("tenant-a", "key2", "value2")
            cache.putPrincipal("tenant-a", "user-1", "key3", "value3")

            cache.clear()

            assertNull(cache.getApp("key1"))
            assertNull(cache.getTenant("tenant-a", "key2"))
            assertNull(cache.getPrincipal("tenant-a", "user-1", "key3"))
        }

    // ========== Batch Operations ==========

    @Test
    fun getManyReturnsMultipleValues() =
        runTest {
            val cache = createCache()

            val key1 = ScopedKey.app("test", "key1")
            val key2 = ScopedKey.app("test", "key2")
            val key3 = ScopedKey.app("test", "key3")

            cache.put(key1, "value1")
            cache.put(key2, "value2")

            val results = cache.getMany(listOf(key1, key2, key3))

            assertEquals(2, results.size)
            assertEquals("value1", results[key1])
            assertEquals("value2", results[key2])
            assertNull(results[key3])
        }

    @Test
    fun putManyStoresMultipleValues() =
        runTest {
            val cache = createCache()

            val key1 = ScopedKey.app("test", "key1")
            val key2 = ScopedKey.app("test", "key2")

            cache.putMany(
                mapOf(
                    key1 to "value1",
                    key2 to "value2",
                ),
            )

            assertEquals("value1", cache.get(key1))
            assertEquals("value2", cache.get(key2))
        }

    @Test
    fun removeManyRemovesMultipleValues() =
        runTest {
            val cache = createCache()

            val key1 = ScopedKey.app("test", "key1")
            val key2 = ScopedKey.app("test", "key2")
            val key3 = ScopedKey.app("test", "key3")

            cache.put(key1, "value1")
            cache.put(key2, "value2")

            val removed = cache.removeMany(listOf(key1, key2, key3))

            assertEquals(2, removed)
            assertNull(cache.get(key1))
            assertNull(cache.get(key2))
        }

    // ========== Statistics ==========

    @Test
    fun statsTrackHitsAndMisses() =
        runTest {
            val cache = createCache()

            cache.putApp("exists", "value")

            cache.getApp("exists") // hit
            cache.getApp("missing") // miss

            val stats = cache.stats()

            assertEquals(1L, stats.hits)
            assertEquals(1L, stats.misses)
        }

    // ========== Namespace ==========

    @Test
    fun namespaceIsCorrect() {
        val cache = createCache("my-namespace")

        assertEquals("my-namespace", cache.namespace)
    }

    @Test
    fun backendIdIsCorrect() {
        val cache = createCache()

        assertEquals("kache", cache.backendId)
    }
}
