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

class KacheCacheBackendTest {
    private fun createBackend(maxSize: Long = 1000): KacheCacheBackend = KacheCacheBackend(maxSize)

    @Test
    fun getReturnsNullForMissingKey() =
        runTest {
            val backend = createBackend()

            assertNull(backend.get("missing-key"))
        }

    @Test
    fun setAndGetRoundTrips() =
        runTest {
            val backend = createBackend()
            val value = "test value".encodeToByteArray()

            backend.set("key1", value)
            val result = backend.get("key1")

            assertNotNull(result)
            assertEquals("test value", result.decodeToString())
        }

    @Test
    fun deleteRemovesKey() =
        runTest {
            val backend = createBackend()
            val value = "value".encodeToByteArray()

            backend.set("key1", value)
            assertTrue(backend.exists("key1"))

            val deleted = backend.delete("key1")
            assertTrue(deleted)
            assertFalse(backend.exists("key1"))
        }

    @Test
    fun deleteReturnsFalseForMissingKey() =
        runTest {
            val backend = createBackend()

            assertFalse(backend.delete("missing-key"))
        }

    @Test
    fun existsReturnsTrueForExistingKey() =
        runTest {
            val backend = createBackend()

            backend.set("key1", "value".encodeToByteArray())

            assertTrue(backend.exists("key1"))
            assertFalse(backend.exists("missing"))
        }

    @Test
    fun getManyReturnsMultipleValues() =
        runTest {
            val backend = createBackend()

            backend.set("key1", "value1".encodeToByteArray())
            backend.set("key2", "value2".encodeToByteArray())
            backend.set("key3", "value3".encodeToByteArray())

            val results = backend.getMany(listOf("key1", "key2", "missing"))

            assertEquals(2, results.size)
            assertEquals("value1", results["key1"]?.decodeToString())
            assertEquals("value2", results["key2"]?.decodeToString())
            assertNull(results["missing"])
        }

    @Test
    fun setManyStoresMultipleValues() =
        runTest {
            val backend = createBackend()

            backend.setMany(
                mapOf(
                    "key1" to "value1".encodeToByteArray(),
                    "key2" to "value2".encodeToByteArray(),
                ),
            )

            assertEquals("value1", backend.get("key1")?.decodeToString())
            assertEquals("value2", backend.get("key2")?.decodeToString())
        }

    @Test
    fun clearRemovesAllEntries() =
        runTest {
            val backend = createBackend()

            backend.set("key1", "value1".encodeToByteArray())
            backend.set("key2", "value2".encodeToByteArray())

            assertEquals(2, backend.size())

            backend.clear()

            assertEquals(0, backend.size())
        }

    @Test
    fun keysReturnsMatchingKeys() =
        runTest {
            val backend = createBackend()

            backend.set("config::APP::key1", "v1".encodeToByteArray())
            backend.set("config::APP::key2", "v2".encodeToByteArray())
            backend.set("tokens::APP::key3", "v3".encodeToByteArray())

            val configKeys = backend.keys("config::*")
            assertEquals(2, configKeys.size)
            assertTrue(configKeys.contains("config::APP::key1"))
            assertTrue(configKeys.contains("config::APP::key2"))

            val allKeys = backend.keys("*")
            assertEquals(3, allKeys.size)
        }

    @Test
    fun deleteByPatternRemovesMatchingKeys() =
        runTest {
            val backend = createBackend()

            backend.set("config::APP::key1", "v1".encodeToByteArray())
            backend.set("config::APP::key2", "v2".encodeToByteArray())
            backend.set("tokens::APP::key3", "v3".encodeToByteArray())

            val deleted = backend.deleteByPattern("config::*")

            assertEquals(2, deleted)
            assertNull(backend.get("config::APP::key1"))
            assertNull(backend.get("config::APP::key2"))
            assertNotNull(backend.get("tokens::APP::key3"))
        }

    @Test
    fun keysMatchingPatternIgnoresNullKeys() {
        val keys =
            keysMatchingPattern(
                pattern = "config::*",
                keys = listOf("config::APP::key1", null, "tokens::APP::key2"),
            )

        assertEquals(listOf("config::APP::key1"), keys)
    }

    @Test
    fun isHealthyReturnsTrue() =
        runTest {
            val backend = createBackend()

            assertTrue(backend.isHealthy())
        }

    @Test
    fun backendIdIsKache() {
        val backend = createBackend()

        assertEquals("kache", backend.id)
    }

    @Test
    fun capabilitiesAreInMemory() {
        val backend = createBackend()

        assertTrue(backend.capabilities.isLocal)
        assertFalse(backend.capabilities.isDistributed)
        assertTrue(backend.capabilities.supportsTtl)
        assertTrue(backend.capabilities.supportsPatternDelete)
        assertTrue(backend.capabilities.supportsBatchOps)
        assertFalse(backend.capabilities.isPersistent)
    }
}
