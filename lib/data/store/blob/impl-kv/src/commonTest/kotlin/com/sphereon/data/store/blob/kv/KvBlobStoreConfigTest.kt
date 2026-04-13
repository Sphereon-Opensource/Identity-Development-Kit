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
        val config = KvBlobStoreConfig(
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
