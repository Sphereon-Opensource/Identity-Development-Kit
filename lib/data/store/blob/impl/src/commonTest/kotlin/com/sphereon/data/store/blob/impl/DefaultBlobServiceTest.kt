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

import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobMetadata
import com.sphereon.data.store.blob.MetadataSearchQuery
import com.sphereon.data.store.blob.PutOptions
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultBlobServiceTest {
    private lateinit var blobService: DefaultBlobService

    @BeforeTest
    fun setup() {
        blobService = createTestBlobService()
    }

    @Test
    fun storeAndGetBlob() =
        runTest {
            val data = "hello blob service".encodeToByteArray()
            val result =
                blobService.storeBlob(
                    target =
                        BlobInfo(
                            tenantId = "t1",
                            path = "docs/readme.txt",
                            contentType = "text/plain",
                        ),
                    data = data,
                )
            assertTrue(result.isOk, "storeBlob should succeed: ${if (result.isErr) result.error else ""}")

            val getResult = blobService.getBlob(info = BlobInfo(tenantId = "t1", path = "docs/readme.txt"))
            assertTrue(getResult.isOk, "getBlob should succeed: ${if (getResult.isErr) getResult.error else ""}")
            assertEquals("hello blob service", getResult.value.data.decodeToString())
        }

    @Test
    fun getBlobInfoMergesMetadata() =
        runTest {
            blobService.storeBlob(
                target =
                    BlobInfo(
                        tenantId = "t1",
                        path = "report.pdf",
                        contentType = "application/pdf",
                        metadata = mapOf("category" to "report"),
                    ),
                data = byteArrayOf(1, 2, 3),
            )

            val infoResult = blobService.getBlobInfo(info = BlobInfo(tenantId = "t1", path = "report.pdf"))
            assertTrue(infoResult.isOk)
            assertEquals("application/pdf", infoResult.value.contentType)
            assertEquals("report", infoResult.value.metadata.custom["category"])
        }

    @Test
    fun deleteBlobRemovesFromIndex() =
        runTest {
            blobService.storeBlob(
                target = BlobInfo(tenantId = "t1", path = "delete-me.txt"),
                data = byteArrayOf(1),
            )

            val deleteResult = blobService.deleteBlob(info = BlobInfo(tenantId = "t1", path = "delete-me.txt"))
            assertTrue(deleteResult.isOk)
            assertTrue(deleteResult.value)

            val getResult = blobService.getBlob(info = BlobInfo(tenantId = "t1", path = "delete-me.txt"))
            assertTrue(getResult.isErr, "blob should not be found after delete")
        }

    @Test
    fun listBlobsReturnsTenantScoped() =
        runTest {
            blobService.storeBlob(target = BlobInfo(tenantId = "t1", path = "a.txt"), data = byteArrayOf(1))
            blobService.storeBlob(target = BlobInfo(tenantId = "t1", path = "b.txt"), data = byteArrayOf(2))
            blobService.storeBlob(target = BlobInfo(tenantId = "t2", path = "c.txt"), data = byteArrayOf(3))

            val result = blobService.listBlobs(info = BlobInfo(tenantId = "t1"))
            assertTrue(result.isOk)
            assertEquals(2, result.value.descriptors.size)
        }

    @Test
    fun storeWithDigestAlgorithmComputesHash() =
        runTest {
            val data = "hash me".encodeToByteArray()
            val result =
                blobService.storeBlob(
                    target = BlobInfo(tenantId = "t1", path = "hashed.txt"),
                    data = data,
                    options = PutOptions(digestAlgorithm = DigestAlg.SHA256),
                )
            assertTrue(result.isOk)
            assertNotNull(result.value.contentHash, "contentHash should be set when digestAlgorithm specified")
        }

    @Test
    fun casStoreAndRetrieve() =
        runTest {
            val data = "cas content".encodeToByteArray()
            val storeResult = blobService.casStore(info = BlobInfo(tenantId = "t1"), data = data)
            assertTrue(storeResult.isOk, "casStore should succeed: ${if (storeResult.isErr) storeResult.error else ""}")

            val address = storeResult.value.contentAddress
            val getResult = blobService.casGet(info = BlobInfo(tenantId = "t1"), address = address)
            assertTrue(getResult.isOk, "casGet should succeed: ${if (getResult.isErr) getResult.error else ""}")
            assertTrue(data.contentEquals(getResult.value.data))
        }

    @Test
    fun casVerifyReturnsTrue() =
        runTest {
            val data = "verify me".encodeToByteArray()
            val storeResult = blobService.casStore(info = BlobInfo(tenantId = "t1"), data = data)
            assertTrue(storeResult.isOk)

            val verifyResult = blobService.casVerify(info = BlobInfo(tenantId = "t1"), address = storeResult.value.contentAddress)
            assertTrue(verifyResult.isOk)
            assertTrue(verifyResult.value)
        }

    @Test
    fun findByMetadataReturnsResults() =
        runTest {
            blobService.storeBlob(
                target =
                    BlobInfo(
                        tenantId = "t1",
                        path = "img1.png",
                        contentType = "image/png",
                        metadata = mapOf("tag" to "photo"),
                    ),
                data = byteArrayOf(1),
            )
            blobService.storeBlob(
                target =
                    BlobInfo(
                        tenantId = "t1",
                        path = "doc1.pdf",
                        contentType = "application/pdf",
                    ),
                data = byteArrayOf(2),
            )

            val result =
                blobService.findByMetadata(
                    info = BlobInfo(tenantId = "t1"),
                    query = MetadataSearchQuery(contentType = "image/png"),
                )
            assertTrue(result.isOk)
            assertEquals(1, result.value.size)
        }

    @Test
    fun copyBlobWorks() =
        runTest {
            blobService.storeBlob(
                target = BlobInfo(tenantId = "t1", path = "src.txt"),
                data = "copy me".encodeToByteArray(),
            )

            val result =
                blobService.copyBlob(
                    source = BlobInfo(tenantId = "t1", path = "src.txt"),
                    destination = BlobInfo(tenantId = "t1", path = "dst.txt"),
                )
            assertTrue(result.isOk, "copy should succeed: ${if (result.isErr) result.error else ""}")

            val srcGet = blobService.getBlob(info = BlobInfo(tenantId = "t1", path = "src.txt"))
            assertTrue(srcGet.isOk, "source should still exist")

            val dstGet = blobService.getBlob(info = BlobInfo(tenantId = "t1", path = "dst.txt"))
            assertTrue(dstGet.isOk, "destination should exist")
            assertEquals("copy me", dstGet.value.data.decodeToString())
        }

    @Test
    fun moveBlobWorks() =
        runTest {
            blobService.storeBlob(
                target = BlobInfo(tenantId = "t1", path = "move-src.txt"),
                data = "move me".encodeToByteArray(),
            )

            val result =
                blobService.moveBlob(
                    source = BlobInfo(tenantId = "t1", path = "move-src.txt"),
                    destination = BlobInfo(tenantId = "t1", path = "move-dst.txt"),
                )
            assertTrue(result.isOk, "move should succeed: ${if (result.isErr) result.error else ""}")

            val srcGet = blobService.getBlob(info = BlobInfo(tenantId = "t1", path = "move-src.txt"))
            assertTrue(srcGet.isErr, "source should not exist after move")

            val dstGet = blobService.getBlob(info = BlobInfo(tenantId = "t1", path = "move-dst.txt"))
            assertTrue(dstGet.isOk, "destination should exist")
            assertEquals("move me", dstGet.value.data.decodeToString())
        }

    @Test
    fun getBlobForNonExistentReturnsError() =
        runTest {
            val result = blobService.getBlob(info = BlobInfo(tenantId = "no-tenant", path = "no-file.txt"))
            assertTrue(result.isErr, "getBlob for non-existent should return error")
        }

    @Test
    fun getBlobInfoForNonExistentReturnsError() =
        runTest {
            val result = blobService.getBlobInfo(info = BlobInfo(tenantId = "no-tenant", path = "no-file.txt"))
            assertTrue(result.isErr, "getBlobInfo for non-existent should return error")
        }

    @Test
    fun deleteBlobForNonExistentReturnsFalse() =
        runTest {
            val result = blobService.deleteBlob(info = BlobInfo(tenantId = "no-tenant", path = "no-file.txt"))
            assertTrue(result.isOk, "deleteBlob for non-existent should not error")
            assertFalse(result.value, "deleteBlob for non-existent should return false")
        }

    @Test
    fun storeBlobContentTypePreservedInGetBlobInfo() =
        runTest {
            blobService.storeBlob(
                target =
                    BlobInfo(
                        tenantId = "t1",
                        path = "typed.json",
                        contentType = "application/json",
                    ),
                data = """{"key":"value"}""".encodeToByteArray(),
            )

            val infoResult = blobService.getBlobInfo(info = BlobInfo(tenantId = "t1", path = "typed.json"))
            assertTrue(infoResult.isOk)
            assertEquals("application/json", infoResult.value.contentType)
        }

    @Test
    fun findByMetadataWithCustomKeyValueMatch() =
        runTest {
            blobService.storeBlob(
                target =
                    BlobInfo(
                        tenantId = "t1",
                        path = "tagged.txt",
                        metadata = mapOf("env" to "prod"),
                    ),
                data = "tagged".encodeToByteArray(),
            )
            blobService.storeBlob(
                target = BlobInfo(tenantId = "t1", path = "untagged.txt"),
                data = "untagged".encodeToByteArray(),
            )

            val result =
                blobService.findByMetadata(
                    info = BlobInfo(tenantId = "t1"),
                    query = MetadataSearchQuery(customMetadata = mapOf("env" to "prod")),
                )
            assertTrue(result.isOk)
            assertEquals(1, result.value.size)
        }

    @Test
    fun findByMetadataWithNoMatchesReturnsEmptyList() =
        runTest {
            blobService.storeBlob(
                target =
                    BlobInfo(
                        tenantId = "t1",
                        path = "something.txt",
                        contentType = "text/plain",
                    ),
                data = "data".encodeToByteArray(),
            )

            val result =
                blobService.findByMetadata(
                    info = BlobInfo(tenantId = "t1"),
                    query = MetadataSearchQuery(contentType = "image/png"),
                )
            assertTrue(result.isOk)
            assertTrue(result.value.isEmpty(), "No matches should return empty list")
        }

    @Test
    fun casStoreDeduplication() =
        runTest {
            val data = "deduplicate me".encodeToByteArray()
            val r1 = blobService.casStore(info = BlobInfo(tenantId = "t1"), data = data)
            val r2 = blobService.casStore(info = BlobInfo(tenantId = "t1"), data = data)
            assertTrue(r1.isOk && r2.isOk)
            assertEquals(r1.value.contentAddress, r2.value.contentAddress, "Same data should produce same CAS address")
        }

    @Test
    fun storeBlobAndFindByContentType() =
        runTest {
            blobService.storeBlob(
                target =
                    BlobInfo(
                        tenantId = "t1",
                        path = "find-ct.xml",
                        contentType = "application/xml",
                    ),
                data = "<root/>".encodeToByteArray(),
            )

            val result =
                blobService.findByMetadata(
                    info = BlobInfo(tenantId = "t1"),
                    query = MetadataSearchQuery(contentType = "application/xml"),
                )
            assertTrue(result.isOk)
            assertEquals(1, result.value.size)
        }

    @Test
    fun listBlobsWithNoDataReturnsEmptyList() =
        runTest {
            val result = blobService.listBlobs(info = BlobInfo(tenantId = "empty-tenant"))
            assertTrue(result.isOk)
            assertTrue(result.value.descriptors.isEmpty(), "listBlobs on empty tenant should return empty list")
        }

    @Test
    fun deleteBlobDeniedByRetentionPolicy() =
        runTest {
            // Create a service with a retention policy that denies all deletes
            val blobBackingStorage =
                com.sphereon.data.store.blob.memory
                    .InMemoryBlobBackingStorageImpl()
            val blobFactory =
                com.sphereon.data.store.blob.memory
                    .InMemoryBlobStoreFactoryImpl(blobBackingStorage)
            val blobConfig =
                com.sphereon.data.store.blob
                    .InMemoryBlobStoreConfig(id = "memory")
            val memoryStore = blobFactory.create(blobConfig)

            val kvBackingStorage =
                com.sphereon.data.store.kv.memory
                    .InMemoryKvBackingStorageImpl()
            val kvFactory =
                com.sphereon.data.store.kv.memory
                    .InMemoryKvStoreFactoryImpl(kvBackingStorage)
            val kvConfig =
                com.sphereon.data.store.kv.InMemoryKvStoreConfig(
                    id = KvBlobMetadataIndex.STORE_ID,
                    scopeBinding = com.sphereon.data.store.kv.KvStoreScopeBinding.APP,
                )
            val kvStore = kvFactory.create(kvConfig)

            val denyDeletePolicy =
                object : com.sphereon.data.store.blob.RetentionPolicyService {
                    override suspend fun canDelete(
                        info: BlobInfo,
                        metadata: BlobMetadata,
                    ) = com.sphereon.core.api
                        .Ok(false)

                    override suspend fun applyRetention(descriptor: com.sphereon.data.store.blob.BlobDescriptor) =
                        com.sphereon.core.api
                            .Ok(descriptor)
                }

            val retainedService =
                DefaultBlobService(
                    blobStoreService = TestBlobStoreService(memoryStore),
                    metadataIndex = KvBlobMetadataIndex(TestKvStoreService(kvStore)),
                    retentionPolicyService = denyDeletePolicy,
                    tempUrlPolicy =
                        com.sphereon.data.store.blob
                            .DefaultTempUrlPolicy(),
                    eventService = TestSessionEventService(),
                    execution = TestSessionExecution(),
                )

            retainedService.storeBlob(
                target = BlobInfo(tenantId = "t1", path = "retained.txt"),
                data = "keep me".encodeToByteArray(),
            )

            val deleteResult = retainedService.deleteBlob(info = BlobInfo(tenantId = "t1", path = "retained.txt"))
            assertTrue(deleteResult.isErr, "Delete should be denied by retention policy")

            val getResult = retainedService.getBlob(info = BlobInfo(tenantId = "t1", path = "retained.txt"))
            assertTrue(getResult.isOk, "Blob should still exist after denied delete")
        }

    @Test
    fun storeBlobWithServerAssignedPath() =
        runTest {
            val data = "server-assigned content".encodeToByteArray()
            val result =
                blobService.storeBlob(
                    target =
                        BlobInfo(
                            tenantId = "t1",
                            contentType = "text/plain",
                        ),
                    data = data,
                )
            assertTrue(result.isOk, "storeBlob with server-assigned path should succeed")
            val descriptor = result.value
            assertTrue(descriptor.path.isNotBlank(), "Server-assigned path should not be blank")

            val getResult =
                blobService.getBlob(
                    info = BlobInfo(tenantId = "t1", path = descriptor.path.removePrefix("t1/")),
                )
            assertTrue(getResult.isOk, "Should be able to retrieve blob by server-assigned path")
            assertEquals("server-assigned content", getResult.value.data.decodeToString())
        }
}
