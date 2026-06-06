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

package com.sphereon.data.store.blob.impl

import com.sphereon.data.store.blob.BlobStoreBackends
import com.sphereon.data.store.blob.BlobStoreConfig
import com.sphereon.data.store.blob.BlobStoreConfigBase
import com.sphereon.data.store.blob.BlobStoreFactory
import com.sphereon.data.store.blob.BlobStoreSchemes
import com.sphereon.data.store.blob.BlobStoreScopeBinding
import com.sphereon.data.store.blob.InMemoryBlobStoreConfig
import com.sphereon.data.store.blob.memory.InMemoryBlobBackingStorageImpl
import com.sphereon.data.store.blob.memory.InMemoryBlobStoreFactoryImpl
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BlobStoreManagerTest {
    private lateinit var manager: BlobStoreManagerImpl
    private lateinit var memoryFactory: InMemoryBlobStoreFactoryImpl

    @BeforeTest
    fun setup() {
        val backingStorage = InMemoryBlobBackingStorageImpl()
        memoryFactory = InMemoryBlobStoreFactoryImpl(backingStorage)

        val configBinder =
            object : BlobStoreConfigBinder {
                override fun getBlobStoreIds(configService: com.sphereon.core.api.conf.ConfigService): Array<String> = emptyArray()

                override fun getBlobStoreConfig(
                    configService: com.sphereon.core.api.conf.ConfigService,
                    storeId: String,
                ): BlobStoreConfigBase = throw UnsupportedOperationException("Not used in this test")

                override fun getBlobStoreConfigs(configService: com.sphereon.core.api.conf.ConfigService): Array<BlobStoreConfigBase> = emptyArray()
            }

        val factories: Set<BlobStoreFactory> = setOf(memoryFactory, NoOpBlobStoreFactoryImpl())
        manager = BlobStoreManagerImpl(factories, configBinder)
    }

    @Test
    fun createFromTypedConfig() {
        val config = InMemoryBlobStoreConfig(id = "test-mem", scopeBinding = BlobStoreScopeBinding.APP)
        val store = manager.createFromBlobStoreConfig(config)
        assertNotNull(store)
        assertEquals(BlobStoreSchemes.MEMORY, store.schemeId)
    }

    @Test
    fun createFromGenericConfig() {
        val config =
            BlobStoreConfig(
                id = "test-generic",
                scopeBinding = BlobStoreScopeBinding.APP,
                backendId = BlobStoreBackends.MEMORY,
            )
        val store = manager.createFromBlobStoreConfig(config)
        assertNotNull(store)
    }

    @Test
    fun createFromConfigCaseInsensitiveBackendId() {
        val config =
            BlobStoreConfig(
                id = "test-upper",
                scopeBinding = BlobStoreScopeBinding.APP,
                backendId = "MEMORY",
            )
        val store = manager.createFromBlobStoreConfig(config)
        assertNotNull(store)
    }

    @Test
    fun createFromConfigUnknownBackendThrows() {
        val config =
            BlobStoreConfig(
                id = "test-unknown",
                backendId = "nonexistent-backend",
            )
        assertFailsWith<IllegalArgumentException> {
            manager.createFromBlobStoreConfig(config)
        }
    }

    @Test
    fun availableBackendsFiltersNoOp() {
        val backends = manager.availableBackends()
        assertTrue(backends.contains(BlobStoreBackends.MEMORY))
        assertTrue(!backends.contains(NoOpBlobStoreFactoryImpl.BACKEND_ID))
    }

    @Test
    fun resolveFromRegistryInterface() {
        val config = InMemoryBlobStoreConfig(id = "resolve-test", scopeBinding = BlobStoreScopeBinding.APP)
        val store = manager.resolve(config)
        assertNotNull(store)
        assertEquals(BlobStoreSchemes.MEMORY, store.schemeId)
    }

    @Test
    fun typedConfigWithMaxEntries() {
        val config =
            InMemoryBlobStoreConfig(
                id = "with-max",
                scopeBinding = BlobStoreScopeBinding.APP,
                maxEntries = 5000,
            )
        val store = manager.createFromBlobStoreConfig(config)
        assertNotNull(store)
    }
}
