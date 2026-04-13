/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.blob.memory

import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobStoreBackends
import com.sphereon.data.store.blob.BlobStoreConfig
import com.sphereon.data.store.blob.BlobStoreScopeBinding
import com.sphereon.data.store.blob.InMemoryBlobStoreConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InMemoryBlobStoreFactoryTest {
    private lateinit var factory: InMemoryBlobStoreFactoryImpl
    private lateinit var backingStorage: InMemoryBlobBackingStorageImpl

    @BeforeTest
    fun setup() {
        backingStorage = InMemoryBlobBackingStorageImpl()
        factory = InMemoryBlobStoreFactoryImpl(backingStorage)
    }

    @Test
    fun backendIdIsMemory() {
        assertEquals(BlobStoreBackends.MEMORY, factory.backendId)
    }

    @Test
    fun createAppScopedStore() {
        val config =
            BlobStoreConfig(
                id = "app-scoped",
                scopeBinding = BlobStoreScopeBinding.APP,
                backendId = BlobStoreBackends.MEMORY,
            )
        val store = factory.create(config)
        assertNotNull(store)
        assertEquals("memory", store.storeId)
    }

    @Test
    fun createTenantScopedWithoutExecutionThrows() {
        val config =
            BlobStoreConfig(
                id = "tenant-scoped",
                scopeBinding = BlobStoreScopeBinding.TENANT,
                backendId = BlobStoreBackends.MEMORY,
            )
        assertFailsWith<IllegalStateException> {
            factory.create(config, null)
        }
    }

    @Test
    fun createWithWrongBackendIdThrows() {
        val config =
            BlobStoreConfig(
                id = "wrong",
                backendId = "filesystem",
            )
        assertFailsWith<IllegalArgumentException> {
            factory.create(config)
        }
    }

    @Test
    fun appScopedStoresSharePartition() =
        runTest {
            val config =
                BlobStoreConfig(
                    id = "shared",
                    scopeBinding = BlobStoreScopeBinding.APP,
                    backendId = BlobStoreBackends.MEMORY,
                )
            val store1 = factory.create(config)
            val store2 = factory.create(config)

            val info = BlobInfo(storeId = "memory", path = "shared.txt")
            store1.put(info, "hello".encodeToByteArray())

            val result = store2.get(info)
            assertTrue(result.isOk, "APP-scoped stores with same ID should share data")
            assertEquals("hello", result.value.data.decodeToString())
        }

    @Test
    fun differentStoreIdsHaveSeparatePartitions() =
        runTest {
            val config1 = BlobStoreConfig(id = "store-a", scopeBinding = BlobStoreScopeBinding.APP, backendId = BlobStoreBackends.MEMORY)
            val config2 = BlobStoreConfig(id = "store-b", scopeBinding = BlobStoreScopeBinding.APP, backendId = BlobStoreBackends.MEMORY)

            val storeA = factory.create(config1)
            val storeB = factory.create(config2)

            val info = BlobInfo(storeId = "memory", path = "isolated.txt")
            storeA.put(info, "only in A".encodeToByteArray())

            val result = storeB.exists(info)
            assertTrue(result.isOk && !result.value, "Different store IDs should have separate data")
        }

    @Test
    fun createWithTypedConfigMaxEntries() {
        val config =
            InMemoryBlobStoreConfig(
                id = "with-max",
                scopeBinding = BlobStoreScopeBinding.APP,
                maxEntries = 5000,
            )
        val store = factory.create(config)
        assertNotNull(store, "Factory should accept typed config with maxEntries")
    }

    @Test
    fun caseInsensitiveBackendIdMatching() {
        val config =
            BlobStoreConfig(
                id = "case-test",
                scopeBinding = BlobStoreScopeBinding.APP,
                backendId = "MEMORY",
            )
        val store = factory.create(config)
        assertNotNull(store)
    }
}
