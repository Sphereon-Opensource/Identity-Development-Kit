package com.sphereon.data.store.blob.impl

import com.sphereon.core.api.Ok
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobMetadata
import com.sphereon.data.store.blob.InMemoryBlobStoreConfig
import com.sphereon.data.store.blob.MetadataSearchQuery
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.RetentionPolicyService
import com.sphereon.data.store.blob.TempUrlOptions
import com.sphereon.data.store.blob.memory.InMemoryBlobBackingStorageImpl
import com.sphereon.data.store.blob.memory.InMemoryBlobStoreFactoryImpl
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.memory.InMemoryKvBackingStorageImpl
import com.sphereon.data.store.kv.memory.InMemoryKvStoreFactoryImpl
import com.sphereon.crypto.core.generic.DigestAlg
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BlobServiceIntegrationTest {

    private lateinit var blobService: DefaultBlobService

    @BeforeTest
    fun setup() {
        blobService = createTestBlobService()
    }

    @Test
    fun fullPutGetStatDeleteLifecycle() = runTest {
        val data = "lifecycle-content".encodeToByteArray()

        // Put
        val putResult = blobService.storeBlob(
            target = BlobInfo(
                tenantId = "t1",
                path = "lifecycle/doc.txt",
                contentType = "text/plain",
                metadata = mapOf("stage" to "draft"),
            ),
            data = data,
        )
        assertTrue(putResult.isOk, "storeBlob should succeed: ${if (putResult.isErr) putResult.error else ""}")
        val descriptor = putResult.value
        assertEquals("text/plain", descriptor.contentType)
        assertEquals(data.size.toLong(), descriptor.sizeBytes)

        // Get
        val getResult = blobService.getBlob(info = BlobInfo(tenantId = "t1", path = "lifecycle/doc.txt"))
        assertTrue(getResult.isOk, "getBlob should succeed")
        assertEquals("lifecycle-content", getResult.value.data.decodeToString())
        assertEquals(data.size.toLong(), getResult.value.descriptor.sizeBytes)

        // Stat (getBlobInfo)
        val statResult = blobService.getBlobInfo(info = BlobInfo(tenantId = "t1", path = "lifecycle/doc.txt"))
        assertTrue(statResult.isOk, "getBlobInfo should succeed")
        assertEquals("text/plain", statResult.value.contentType)
        assertEquals(data.size.toLong(), statResult.value.sizeBytes)
        assertEquals("draft", statResult.value.metadata.custom["stage"])

        // Delete
        val deleteResult = blobService.deleteBlob(info = BlobInfo(tenantId = "t1", path = "lifecycle/doc.txt"))
        assertTrue(deleteResult.isOk, "deleteBlob should succeed")
        assertTrue(deleteResult.value, "deleteBlob should return true for existing blob")

        // Verify get returns error after delete
        val getAfterDelete = blobService.getBlob(info = BlobInfo(tenantId = "t1", path = "lifecycle/doc.txt"))
        assertTrue(getAfterDelete.isErr, "getBlob should fail after delete")

        // Verify stat returns error after delete
        val statAfterDelete = blobService.getBlobInfo(info = BlobInfo(tenantId = "t1", path = "lifecycle/doc.txt"))
        assertTrue(statAfterDelete.isErr, "getBlobInfo should fail after delete")
    }

    @Test
    fun tenantIsolationFullCycle() = runTest {
        val dataA = "tenant-A-data".encodeToByteArray()
        val dataB = "tenant-B-data".encodeToByteArray()

        // Store blob with same path for both tenants
        val putA = blobService.storeBlob(
            target = BlobInfo(tenantId = "tenantA", path = "shared/file.txt"),
            data = dataA,
        )
        assertTrue(putA.isOk, "storeBlob for tenantA should succeed")

        val putB = blobService.storeBlob(
            target = BlobInfo(tenantId = "tenantB", path = "shared/file.txt"),
            data = dataB,
        )
        assertTrue(putB.isOk, "storeBlob for tenantB should succeed")

        // Get from A returns A's data
        val getA = blobService.getBlob(info = BlobInfo(tenantId = "tenantA", path = "shared/file.txt"))
        assertTrue(getA.isOk, "getBlob for tenantA should succeed")
        assertEquals("tenant-A-data", getA.value.data.decodeToString())

        // Get from B returns B's data
        val getB = blobService.getBlob(info = BlobInfo(tenantId = "tenantB", path = "shared/file.txt"))
        assertTrue(getB.isOk, "getBlob for tenantB should succeed")
        assertEquals("tenant-B-data", getB.value.data.decodeToString())

        // Delete from A
        val deleteA = blobService.deleteBlob(info = BlobInfo(tenantId = "tenantA", path = "shared/file.txt"))
        assertTrue(deleteA.isOk, "deleteBlob for tenantA should succeed")
        assertTrue(deleteA.value)

        // A is gone
        val getAAfterDelete = blobService.getBlob(info = BlobInfo(tenantId = "tenantA", path = "shared/file.txt"))
        assertTrue(getAAfterDelete.isErr, "tenantA blob should be gone after delete")

        // B still exists
        val getBAfterDelete = blobService.getBlob(info = BlobInfo(tenantId = "tenantB", path = "shared/file.txt"))
        assertTrue(getBAfterDelete.isOk, "tenantB blob should still exist after deleting tenantA")
        assertEquals("tenant-B-data", getBAfterDelete.value.data.decodeToString())
    }

    @Test
    fun copyAndMoveFullCycle() = runTest {
        val data = "copy-move-data".encodeToByteArray()

        // Put original
        val putResult = blobService.storeBlob(
            target = BlobInfo(tenantId = "t1", path = "original.txt"),
            data = data,
        )
        assertTrue(putResult.isOk, "storeBlob should succeed")

        // Copy to new path
        val copyResult = blobService.copyBlob(
            source = BlobInfo(tenantId = "t1", path = "original.txt"),
            destination = BlobInfo(tenantId = "t1", path = "copied.txt"),
        )
        assertTrue(copyResult.isOk, "copyBlob should succeed: ${if (copyResult.isErr) copyResult.error else ""}")

        // Verify both exist with same data
        val getOriginal = blobService.getBlob(info = BlobInfo(tenantId = "t1", path = "original.txt"))
        assertTrue(getOriginal.isOk, "original should still exist after copy")
        assertEquals("copy-move-data", getOriginal.value.data.decodeToString())

        val getCopied = blobService.getBlob(info = BlobInfo(tenantId = "t1", path = "copied.txt"))
        assertTrue(getCopied.isOk, "copy destination should exist")
        assertEquals("copy-move-data", getCopied.value.data.decodeToString())

        // Move original to third path
        val moveResult = blobService.moveBlob(
            source = BlobInfo(tenantId = "t1", path = "original.txt"),
            destination = BlobInfo(tenantId = "t1", path = "moved.txt"),
        )
        assertTrue(moveResult.isOk, "moveBlob should succeed: ${if (moveResult.isErr) moveResult.error else ""}")

        // Verify original is gone
        val getOriginalAfterMove = blobService.getBlob(info = BlobInfo(tenantId = "t1", path = "original.txt"))
        assertTrue(getOriginalAfterMove.isErr, "original should not exist after move")

        // Copy destination still exists
        val getCopiedAfterMove = blobService.getBlob(info = BlobInfo(tenantId = "t1", path = "copied.txt"))
        assertTrue(getCopiedAfterMove.isOk, "copy destination should still exist after moving original")
        assertEquals("copy-move-data", getCopiedAfterMove.value.data.decodeToString())

        // Moved blob at third path
        val getMoved = blobService.getBlob(info = BlobInfo(tenantId = "t1", path = "moved.txt"))
        assertTrue(getMoved.isOk, "moved blob should exist at new path")
        assertEquals("copy-move-data", getMoved.value.data.decodeToString())
    }

    @Test
    fun serverAssignedPathFullCycle() = runTest {
        val data = "server-assigned-content".encodeToByteArray()

        // Store without path (server-assigned)
        val putResult = blobService.storeBlob(
            target = BlobInfo(
                tenantId = "t1",
                contentType = "application/octet-stream",
            ),
            data = data,
        )
        assertTrue(putResult.isOk, "storeBlob with server-assigned path should succeed")
        val descriptor = putResult.value
        assertTrue(descriptor.path.isNotBlank(), "server-assigned path should not be blank")

        // Extract the path without tenant prefix for subsequent calls
        val assignedPath = descriptor.path.removePrefix("t1/")
        assertTrue(assignedPath.isNotBlank(), "assigned path (without tenant prefix) should not be blank")

        // Get blob by that path succeeds
        val getResult = blobService.getBlob(info = BlobInfo(tenantId = "t1", path = assignedPath))
        assertTrue(getResult.isOk, "getBlob by server-assigned path should succeed")
        assertEquals("server-assigned-content", getResult.value.data.decodeToString())

        // Stat by that path succeeds
        val statResult = blobService.getBlobInfo(info = BlobInfo(tenantId = "t1", path = assignedPath))
        assertTrue(statResult.isOk, "getBlobInfo by server-assigned path should succeed")
        assertEquals(data.size.toLong(), statResult.value.sizeBytes)

        // Delete by that path succeeds
        val deleteResult = blobService.deleteBlob(info = BlobInfo(tenantId = "t1", path = assignedPath))
        assertTrue(deleteResult.isOk, "deleteBlob by server-assigned path should succeed")
        assertTrue(deleteResult.value)

        // Verify it is gone
        val getAfterDelete = blobService.getBlob(info = BlobInfo(tenantId = "t1", path = assignedPath))
        assertTrue(getAfterDelete.isErr, "blob should not exist after delete")
    }

    @Test
    fun metadataIndexSearchAfterCrud() = runTest {
        // Store 5 blobs with different contentTypes and custom metadata
        blobService.storeBlob(
            target = BlobInfo(
                tenantId = "t1",
                path = "search/img1.png",
                contentType = "image/png",
                metadata = mapOf("category" to "photo"),
            ),
            data = byteArrayOf(1),
        )
        blobService.storeBlob(
            target = BlobInfo(
                tenantId = "t1",
                path = "search/img2.png",
                contentType = "image/png",
                metadata = mapOf("category" to "photo"),
            ),
            data = byteArrayOf(2),
        )
        blobService.storeBlob(
            target = BlobInfo(
                tenantId = "t1",
                path = "search/doc1.pdf",
                contentType = "application/pdf",
                metadata = mapOf("category" to "report"),
            ),
            data = byteArrayOf(3),
        )
        blobService.storeBlob(
            target = BlobInfo(
                tenantId = "t1",
                path = "search/doc2.pdf",
                contentType = "application/pdf",
                metadata = mapOf("category" to "invoice"),
            ),
            data = byteArrayOf(4),
        )
        blobService.storeBlob(
            target = BlobInfo(
                tenantId = "t1",
                path = "search/readme.txt",
                contentType = "text/plain",
                metadata = mapOf("category" to "docs"),
            ),
            data = byteArrayOf(5),
        )

        // Search by contentType returns correct subset
        val pngResult = blobService.findByMetadata(
            info = BlobInfo(tenantId = "t1"),
            query = MetadataSearchQuery(contentType = "image/png"),
        )
        assertTrue(pngResult.isOk, "findByMetadata for image/png should succeed")
        assertEquals(2, pngResult.value.size, "should find 2 PNG blobs")

        val pdfResult = blobService.findByMetadata(
            info = BlobInfo(tenantId = "t1"),
            query = MetadataSearchQuery(contentType = "application/pdf"),
        )
        assertTrue(pdfResult.isOk)
        assertEquals(2, pdfResult.value.size, "should find 2 PDF blobs")

        // Search by custom key/value returns correct subset
        val photoResult = blobService.findByMetadata(
            info = BlobInfo(tenantId = "t1"),
            query = MetadataSearchQuery(customMetadata = mapOf("category" to "photo")),
        )
        assertTrue(photoResult.isOk)
        assertEquals(2, photoResult.value.size, "should find 2 blobs with category=photo")

        val reportResult = blobService.findByMetadata(
            info = BlobInfo(tenantId = "t1"),
            query = MetadataSearchQuery(customMetadata = mapOf("category" to "report")),
        )
        assertTrue(reportResult.isOk)
        assertEquals(1, reportResult.value.size, "should find 1 blob with category=report")

        // Delete a blob
        val deleteResult = blobService.deleteBlob(info = BlobInfo(tenantId = "t1", path = "search/img1.png"))
        assertTrue(deleteResult.isOk)
        assertTrue(deleteResult.value)

        // Search no longer returns it
        val pngAfterDelete = blobService.findByMetadata(
            info = BlobInfo(tenantId = "t1"),
            query = MetadataSearchQuery(contentType = "image/png"),
        )
        assertTrue(pngAfterDelete.isOk)
        assertEquals(1, pngAfterDelete.value.size, "should find only 1 PNG blob after deletion")

        val photoAfterDelete = blobService.findByMetadata(
            info = BlobInfo(tenantId = "t1"),
            query = MetadataSearchQuery(customMetadata = mapOf("category" to "photo")),
        )
        assertTrue(photoAfterDelete.isOk)
        assertEquals(1, photoAfterDelete.value.size, "should find only 1 photo after deletion")
    }

    @Test
    fun casStoreRetrieveVerifyCycle() = runTest {
        val data1 = "cas-content-alpha".encodeToByteArray()
        val data2 = "cas-content-beta".encodeToByteArray()

        // CAS store data with SHA256
        val store1 = blobService.casStore(
            info = BlobInfo(tenantId = "t1"),
            data = data1,
            algorithm = DigestAlg.SHA256,
        )
        assertTrue(store1.isOk, "casStore should succeed: ${if (store1.isErr) store1.error else ""}")
        val address1 = store1.value.contentAddress

        // Retrieve by address
        val get1 = blobService.casGet(info = BlobInfo(tenantId = "t1"), address = address1)
        assertTrue(get1.isOk, "casGet should succeed")
        assertTrue(data1.contentEquals(get1.value.data), "retrieved data should match stored data")

        // Verify returns true
        val verify1 = blobService.casVerify(info = BlobInfo(tenantId = "t1"), address = address1)
        assertTrue(verify1.isOk, "casVerify should succeed")
        assertTrue(verify1.value, "casVerify should return true for existing content")

        // Store same data again (dedup - same address)
        val storeDup = blobService.casStore(
            info = BlobInfo(tenantId = "t1"),
            data = data1,
            algorithm = DigestAlg.SHA256,
        )
        assertTrue(storeDup.isOk, "casStore of duplicate should succeed")
        assertEquals(address1, storeDup.value.contentAddress, "same data should produce same CAS address")

        // Store different data (different address)
        val store2 = blobService.casStore(
            info = BlobInfo(tenantId = "t1"),
            data = data2,
            algorithm = DigestAlg.SHA256,
        )
        assertTrue(store2.isOk, "casStore of different data should succeed")
        val address2 = store2.value.contentAddress
        assertFalse(
            address1.digest.contentEquals(address2.digest),
            "different data should produce different CAS address",
        )

        // Verify second address
        val verify2 = blobService.casVerify(info = BlobInfo(tenantId = "t1"), address = address2)
        assertTrue(verify2.isOk)
        assertTrue(verify2.value)
    }

    @Test
    fun retentionPolicyBlocksDeleteButAllowsRead() = runTest {
        // Create a service with deny-delete policy
        val blobBackingStorage = InMemoryBlobBackingStorageImpl()
        val blobFactory = InMemoryBlobStoreFactoryImpl(blobBackingStorage)
        val blobConfig = InMemoryBlobStoreConfig(id = "memory")
        val memoryStore = blobFactory.create(blobConfig)

        val kvBackingStorage = InMemoryKvBackingStorageImpl()
        val kvFactory = InMemoryKvStoreFactoryImpl(kvBackingStorage)
        val kvConfig = InMemoryKvStoreConfig(
            id = KvBlobMetadataIndex.STORE_ID,
            scopeBinding = KvStoreScopeBinding.APP,
        )
        val kvStore = kvFactory.create(kvConfig)

        val denyDeletePolicy = object : RetentionPolicyService {
            override suspend fun canDelete(
                info: BlobInfo,
                metadata: BlobMetadata,
            ) = Ok(false)

            override suspend fun applyRetention(
                descriptor: BlobDescriptor,
            ) = Ok(descriptor)
        }

        val retainedService = DefaultBlobService(
            blobStoreService = TestBlobStoreService(memoryStore),
            metadataIndex = KvBlobMetadataIndex(TestKvStoreService(kvStore)),
            retentionPolicyService = denyDeletePolicy,
            tempUrlPolicy = com.sphereon.data.store.blob.DefaultTempUrlPolicy(),
            eventService = TestSessionEventService(),
            execution = TestSessionExecution(),
        )

        // Store blob
        val putResult = retainedService.storeBlob(
            target = BlobInfo(
                tenantId = "t1",
                path = "retained.txt",
                contentType = "text/plain",
            ),
            data = "retained-content".encodeToByteArray(),
        )
        assertTrue(putResult.isOk, "storeBlob should succeed")

        // Get succeeds
        val getResult = retainedService.getBlob(info = BlobInfo(tenantId = "t1", path = "retained.txt"))
        assertTrue(getResult.isOk, "getBlob should succeed despite retention policy")
        assertEquals("retained-content", getResult.value.data.decodeToString())

        // Stat succeeds
        val statResult = retainedService.getBlobInfo(info = BlobInfo(tenantId = "t1", path = "retained.txt"))
        assertTrue(statResult.isOk, "getBlobInfo should succeed despite retention policy")
        assertEquals("text/plain", statResult.value.contentType)

        // Delete is denied
        val deleteResult = retainedService.deleteBlob(info = BlobInfo(tenantId = "t1", path = "retained.txt"))
        assertTrue(deleteResult.isErr, "deleteBlob should be denied by retention policy")

        // Blob still exists
        val getAfterDeniedDelete = retainedService.getBlob(info = BlobInfo(tenantId = "t1", path = "retained.txt"))
        assertTrue(getAfterDeniedDelete.isOk, "blob should still exist after denied delete")
        assertEquals("retained-content", getAfterDeniedDelete.value.data.decodeToString())
    }

    @Test
    fun digestAlgorithmComputesContentHash() = runTest {
        val data = "hash-this-content".encodeToByteArray()

        // Store with digestAlgorithm=SHA256
        val putResult = blobService.storeBlob(
            target = BlobInfo(tenantId = "t1", path = "hashed-doc.txt"),
            data = data,
            options = PutOptions(digestAlgorithm = DigestAlg.SHA256),
        )
        assertTrue(putResult.isOk, "storeBlob with digest should succeed")
        val descriptor = putResult.value
        assertNotNull(descriptor.contentHash, "contentHash should be set when digestAlgorithm specified")
        assertTrue(descriptor.contentHash!!.isNotBlank(), "contentHash should not be blank")

        // getBlobInfo also returns the contentHash
        val infoResult = blobService.getBlobInfo(info = BlobInfo(tenantId = "t1", path = "hashed-doc.txt"))
        assertTrue(infoResult.isOk, "getBlobInfo should succeed")
        assertNotNull(infoResult.value.contentHash, "getBlobInfo should also return contentHash")
        assertEquals(
            descriptor.contentHash,
            infoResult.value.contentHash,
            "contentHash from getBlobInfo should match the one from storeBlob",
        )
    }

    @Test
    fun copyPreservesMetadata() = runTest {
        val data = """{"key":"value"}""".encodeToByteArray()

        // Store with rich metadata
        val putResult = blobService.storeBlob(
            target = BlobInfo(
                tenantId = "t1",
                path = "meta-src.json",
                contentType = "application/json",
                metadata = mapOf("env" to "production", "version" to "2.1"),
            ),
            data = data,
        )
        assertTrue(putResult.isOk, "storeBlob should succeed")

        // Copy it
        val copyResult = blobService.copyBlob(
            source = BlobInfo(tenantId = "t1", path = "meta-src.json"),
            destination = BlobInfo(tenantId = "t1", path = "meta-dst.json"),
        )
        assertTrue(copyResult.isOk, "copyBlob should succeed: ${if (copyResult.isErr) copyResult.error else ""}")

        // Stat the original
        val srcInfo = blobService.getBlobInfo(info = BlobInfo(tenantId = "t1", path = "meta-src.json"))
        assertTrue(srcInfo.isOk, "source getBlobInfo should succeed")

        // Stat the copy
        val dstInfo = blobService.getBlobInfo(info = BlobInfo(tenantId = "t1", path = "meta-dst.json"))
        assertTrue(dstInfo.isOk, "destination getBlobInfo should succeed")

        // Verify contentType matches
        assertEquals(
            srcInfo.value.contentType,
            dstInfo.value.contentType,
            "copied blob should preserve contentType",
        )

        // Verify the copy's storage-native metadata has the contentType
        assertEquals("application/json", dstInfo.value.contentType)

        // Verify data is the same
        val dstGet = blobService.getBlob(info = BlobInfo(tenantId = "t1", path = "meta-dst.json"))
        assertTrue(dstGet.isOk)
        assertEquals("""{"key":"value"}""", dstGet.value.data.decodeToString())
    }

    @Test
    fun moveDeindexesSourceAndIndexesDestination() = runTest {
        // Store blob with custom metadata
        val putResult = blobService.storeBlob(
            target = BlobInfo(
                tenantId = "t1",
                path = "move-idx/source.csv",
                contentType = "text/csv",
                metadata = mapOf("department" to "finance"),
            ),
            data = "col1,col2\na,b".encodeToByteArray(),
        )
        assertTrue(putResult.isOk, "storeBlob should succeed")

        // Verify findByMetadata returns it
        val findBefore = blobService.findByMetadata(
            info = BlobInfo(tenantId = "t1"),
            query = MetadataSearchQuery(customMetadata = mapOf("department" to "finance")),
        )
        assertTrue(findBefore.isOk)
        assertEquals(1, findBefore.value.size, "should find 1 blob with department=finance before move")
        assertTrue(
            findBefore.value[0].path.contains("move-idx/source.csv"),
            "found blob should be at source path",
        )

        // Move it
        val moveResult = blobService.moveBlob(
            source = BlobInfo(tenantId = "t1", path = "move-idx/source.csv"),
            destination = BlobInfo(tenantId = "t1", path = "move-idx/destination.csv"),
        )
        assertTrue(moveResult.isOk, "moveBlob should succeed: ${if (moveResult.isErr) moveResult.error else ""}")

        // Source path should be gone from the store
        val getSource = blobService.getBlob(info = BlobInfo(tenantId = "t1", path = "move-idx/source.csv"))
        assertTrue(getSource.isErr, "source should not exist after move")

        // findByMetadata with metadata query still finds it (at new path)
        val findAfter = blobService.findByMetadata(
            info = BlobInfo(tenantId = "t1"),
            query = MetadataSearchQuery(customMetadata = mapOf("department" to "finance")),
        )
        assertTrue(findAfter.isOk)
        assertEquals(1, findAfter.value.size, "should still find 1 blob with department=finance after move")
        assertTrue(
            findAfter.value[0].path.contains("move-idx/destination.csv"),
            "found blob should now be at destination path",
        )
    }

    @Test
    fun listBlobsWithTenantScoping() = runTest {
        // Store 3 blobs for tenant A
        blobService.storeBlob(target = BlobInfo(tenantId = "listA", path = "file1.txt"), data = byteArrayOf(1))
        blobService.storeBlob(target = BlobInfo(tenantId = "listA", path = "file2.txt"), data = byteArrayOf(2))
        blobService.storeBlob(target = BlobInfo(tenantId = "listA", path = "file3.txt"), data = byteArrayOf(3))

        // Store 2 blobs for tenant B
        blobService.storeBlob(target = BlobInfo(tenantId = "listB", path = "fileX.txt"), data = byteArrayOf(10))
        blobService.storeBlob(target = BlobInfo(tenantId = "listB", path = "fileY.txt"), data = byteArrayOf(20))

        // listBlobs for A returns 3
        val listA = blobService.listBlobs(info = BlobInfo(tenantId = "listA"))
        assertTrue(listA.isOk, "listBlobs for listA should succeed")
        assertEquals(3, listA.value.descriptors.size, "listA should return 3 blobs")

        // listBlobs for B returns 2
        val listB = blobService.listBlobs(info = BlobInfo(tenantId = "listB"))
        assertTrue(listB.isOk, "listBlobs for listB should succeed")
        assertEquals(2, listB.value.descriptors.size, "listB should return 2 blobs")

        // Verify no cross-contamination: all paths in A start with listA/
        for (desc in listA.value.descriptors) {
            assertTrue(desc.path.startsWith("listA/"), "all listA descriptors should be scoped to listA/")
        }
        for (desc in listB.value.descriptors) {
            assertTrue(desc.path.startsWith("listB/"), "all listB descriptors should be scoped to listB/")
        }
    }

    @Test
    fun createTempUrlWithPolicyApproval() = runTest {
        // Store a blob
        blobService.storeBlob(
            target = BlobInfo(tenantId = "t1", path = "tempurl-target.txt"),
            data = "temp-url-content".encodeToByteArray(),
        )

        // Call createTempUrl
        val tempUrlResult = blobService.createTempUrl(
            info = BlobInfo(tenantId = "t1", path = "tempurl-target.txt"),
            options = TempUrlOptions.DEFAULT,
        )

        // In-memory store does not support temp URLs, so this should return an error.
        // The policy approval passes (DefaultTempUrlPolicy always approves), but the
        // underlying in-memory BlobStore returns UNSUPPORTED for createTempUrl.
        assertTrue(
            tempUrlResult.isErr,
            "createTempUrl on in-memory store should return error (unsupported by backend)",
        )
    }
}
