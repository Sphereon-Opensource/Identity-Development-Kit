package com.sphereon.data.store.blob.fs

import com.sphereon.data.store.blob.BlobStoreBackends
import com.sphereon.data.store.blob.BlobStoreConfig
import com.sphereon.data.store.blob.BlobStoreScopeBinding
import com.sphereon.data.store.blob.BlobStoreSchemes
import com.sphereon.data.store.blob.FileSystemBlobStoreConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class FileSystemBlobStoreFactoryTest {

    private val factory = FileSystemBlobStoreFactoryImpl()

    @Test
    fun createWithTypedConfig() {
        val config = FileSystemBlobStoreConfig(
            id = "fs-typed",
            scopeBinding = BlobStoreScopeBinding.APP,
            rootDir = "/tmp/blobs",
        )
        val store = factory.create(config)
        assertNotNull(store)
        assertEquals(BlobStoreSchemes.FILESYSTEM, store.storeId)
    }

    @Test
    fun createWithAutoCreateDirsFalse() {
        val config = FileSystemBlobStoreConfig(
            id = "fs-no-autocreate",
            rootDir = "/tmp/blobs",
            autoCreateDirs = false,
        )
        val store = factory.create(config)
        assertNotNull(store)
    }

    @Test
    fun createWithBlankRootDirThrows() {
        val config = FileSystemBlobStoreConfig(
            id = "fs-no-root",
            rootDir = "",
        )
        assertFailsWith<IllegalArgumentException> {
            factory.create(config)
        }
    }

    @Test
    fun createWithGenericConfigThrows() {
        val config = BlobStoreConfig(
            id = "wrong-type",
            backendId = BlobStoreBackends.FILESYSTEM,
        )
        assertFailsWith<IllegalArgumentException> {
            factory.create(config)
        }
    }

    @Test
    fun createWithWrongBackendIdThrows() {
        val config = FileSystemBlobStoreConfig(
            id = "wrong-backend",
            rootDir = "/tmp",
        )
        // The backendId is hardcoded to "filesystem" in FileSystemBlobStoreConfig,
        // but if someone passes a generic config with wrong backendId it should fail
        val genericConfig = BlobStoreConfig(id = "wrong", backendId = "memory")
        assertFailsWith<IllegalArgumentException> {
            factory.create(genericConfig)
        }
    }

    @Test
    fun backendIdIsFilesystem() {
        assertEquals(BlobStoreBackends.FILESYSTEM, factory.backendId)
    }

    @Test
    fun defaultAutoCreateDirsIsTrue() {
        val config = FileSystemBlobStoreConfig(id = "defaults", rootDir = "/tmp/blobs")
        assertEquals(true, config.autoCreateDirs)
    }
}
