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

package com.sphereon.data.store.blob.kv

import com.sphereon.data.store.blob.BlobStoreScopeBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KvBlobStoreConfigTest {
    @Test
    fun defaultConfig() {
        val config = KvBlobStoreConfig()
        assertEquals("kvstore", config.backendId)
        assertEquals("kvstore", config.id)
        assertEquals(BlobStoreScopeBinding.TENANT, config.scopeBinding)
        assertTrue(config.enabled)
        assertEquals("blob-kv-store", config.kvStoreId)
        assertEquals(10 * 1024 * 1024, config.maxBlobSizeBytes)
    }

    @Test
    fun customConfig() {
        val config =
            KvBlobStoreConfig(
                id = "small-docs",
                kvStoreId = "persistent-kv",
                maxBlobSizeBytes = 1024,
                scopeBinding = BlobStoreScopeBinding.APP,
            )
        assertEquals("small-docs", config.id)
        assertEquals("persistent-kv", config.kvStoreId)
        assertEquals(1024, config.maxBlobSizeBytes)
        assertEquals(BlobStoreScopeBinding.APP, config.scopeBinding)
    }
}
