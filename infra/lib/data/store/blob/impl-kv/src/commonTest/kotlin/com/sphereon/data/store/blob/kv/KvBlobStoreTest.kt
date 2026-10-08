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

import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.testing.BlobStoreContract
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.memory.InMemoryKvBackingStorageImpl
import com.sphereon.data.store.kv.memory.InMemoryKvStoreFactoryImpl
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KvBlobStoreTest {
    private lateinit var store: KvBlobStore

    @BeforeTest
    fun setup() {
        val kvBackingStorage = InMemoryKvBackingStorageImpl()
        val kvFactory = InMemoryKvStoreFactoryImpl(kvBackingStorage)
        val kvConfig = InMemoryKvStoreConfig(id = "blob-kv", scopeBinding = KvStoreScopeBinding.APP)
        val kvStore = kvFactory.create(kvConfig)
        store = KvBlobStore(kvStore = kvStore)
    }

    private fun info(path: String) = BlobInfo(storeId = KvBlobStoreConfig.BACKEND_ID, path = path)

    @Test
    fun putAndGetRoundtrip() =
        runTest {
            val data = "hello kvstore blob".encodeToByteArray()
            val r = info("test/hello.txt").copy(contentType = "text/plain")

            val putResult = store.put(r, data)
            assertTrue(putResult.isOk, "put should succeed")
            assertEquals(data.size.toLong(), putResult.value.sizeBytes)

            val getResult = store.get(r)
            assertTrue(getResult.isOk, "get should succeed")
            assertTrue(data.contentEquals(getResult.value.data))
            assertEquals("text/plain", getResult.value.descriptor.contentType)
        }

    @Test
    fun getNotFoundReturnsError() =
        runTest {
            val result = store.get(info("nonexistent.txt"))
            assertTrue(result.isErr)
        }

    @Test
    fun deleteExistingReturnsTrue() =
        runTest {
            val r = info("delete-me.txt")
            store.put(r, "data".encodeToByteArray())

            val deleteResult = store.delete(r)
            assertTrue(deleteResult.isOk)
            assertTrue(deleteResult.value)

            val existsResult = store.exists(r)
            assertTrue(existsResult.isOk)
            assertFalse(existsResult.value)
        }

    @Test
    fun statReturnsDescriptor() =
        runTest {
            val data = "stat test".encodeToByteArray()
            val r = info("stat.json")
            store.put(r.copy(contentType = "application/json"), data)

            val result = store.stat(r)
            assertTrue(result.isOk)
            assertEquals(data.size.toLong(), result.value.sizeBytes)
            assertEquals("application/json", result.value.contentType)
            assertEquals("stat.json", result.value.filename)
            assertNotNull(result.value.createdAt)
        }

    @Test
    fun listReturnsStoredBlobs() =
        runTest {
            store.put(info("list/a.txt"), "a".encodeToByteArray())
            store.put(info("list/b.txt"), "b".encodeToByteArray())
            store.put(info("other/c.txt"), "c".encodeToByteArray())

            val result = store.list(info("list/"), ListOptions(prefix = "list/"))
            assertTrue(result.isOk)
            assertEquals(2, result.value.descriptors.size)
        }

    @Test
    fun putNoOverwriteFails() =
        runTest {
            val r = info("no-ow.txt")
            store.put(r, "first".encodeToByteArray())

            val result = store.put(r, "second".encodeToByteArray(), options = PutOptions.NO_OVERWRITE)
            assertTrue(result.isErr)
        }

    @Test
    fun copyWorks() =
        runTest {
            val src = info("copy-src.txt")
            val dst = info("copy-dst.txt")
            store.put(src, "copy me".encodeToByteArray())

            val result = store.copy(src, dst)
            assertTrue(result.isOk)

            assertTrue(store.exists(src).let { it.isOk && it.value })
            assertEquals(
                "copy me",
                store
                    .get(dst)
                    .value.data
                    .decodeToString(),
            )
        }

    @Test
    fun moveWorks() =
        runTest {
            val src = info("move-src.txt")
            val dst = info("move-dst.txt")
            store.put(src, "move me".encodeToByteArray())

            val result = store.move(src, dst)
            assertTrue(result.isOk)

            assertTrue(store.exists(src).let { it.isOk && !it.value })
            assertEquals(
                "move me",
                store
                    .get(dst)
                    .value.data
                    .decodeToString(),
            )
        }

    @Test
    fun deletePrefixWorks() =
        runTest {
            store.put(info("prefix/a.txt"), "a".encodeToByteArray())
            store.put(info("prefix/b.txt"), "b".encodeToByteArray())
            store.put(info("other/c.txt"), "c".encodeToByteArray())

            val result = store.deletePrefix(info("prefix/"))
            assertTrue(result.isOk)
            assertEquals(2, result.value)

            assertTrue(store.exists(info("other/c.txt")).let { it.isOk && it.value })
        }

    @Test
    fun metadataPreserved() =
        runTest {
            val metaInfo =
                info("meta.png").copy(
                    contentType = "image/png",
                    metadata = mapOf("tag" to "photo"),
                )
            store.put(metaInfo, byteArrayOf(1, 2, 3))

            val stat = store.stat(info("meta.png"))
            assertTrue(stat.isOk)
            assertEquals("image/png", stat.value.contentType)
            assertEquals("photo", stat.value.metadata.custom["tag"])
        }

    @Test
    fun createdAtPreservedOnOverwrite() =
        runTest {
            val r = info("overwrite-created.txt")
            store.put(r, "first".encodeToByteArray())

            val firstStat = store.stat(r)
            val originalCreatedAt = firstStat.value.createdAt

            store.put(r, "second".encodeToByteArray())

            val secondStat = store.stat(r)
            assertEquals(originalCreatedAt, secondStat.value.createdAt)
        }

    @Test
    fun maxBlobSizeEnforced() =
        runTest {
            val smallStore =
                KvBlobStore(
                    kvStore =
                        run {
                            val bs = InMemoryKvBackingStorageImpl()
                            val f = InMemoryKvStoreFactoryImpl(bs)
                            f.create(InMemoryKvStoreConfig(id = "small", scopeBinding = KvStoreScopeBinding.APP))
                        },
                    maxBlobSizeBytes = 100,
                )

            val smallResult = smallStore.put(info("small.txt"), ByteArray(50))
            assertTrue(smallResult.isOk, "50 bytes should fit in 100 byte limit")

            val bigResult = smallStore.put(info("big.txt"), ByteArray(200))
            assertTrue(bigResult.isErr, "200 bytes should exceed 100 byte limit")
        }

    @Test
    fun binaryDataIntegrity() =
        runTest {
            val data = byteArrayOf(0x00, 0xFF.toByte(), 0x7F, 0x80.toByte(), 0x01, 0xFE.toByte())
            store.put(info("binary.bin"), data)

            val getResult = store.get(info("binary.bin"))
            assertTrue(getResult.isOk)
            assertTrue(data.contentEquals(getResult.value.data), "Binary data should survive KvStore roundtrip")
        }

    @Test
    fun serverAssignedPutGeneratesUniqueIds() =
        runTest {
            val r1 = store.put(BlobInfo(storeId = KvBlobStoreConfig.BACKEND_ID), "data1".encodeToByteArray())
            val r2 = store.put(BlobInfo(storeId = KvBlobStoreConfig.BACKEND_ID), "data2".encodeToByteArray())
            assertTrue(r1.isOk)
            assertTrue(r2.isOk)
            assertTrue(r1.value.path != r2.value.path, "Server-assigned paths should be unique")
        }

    @Test
    fun movePreservesCreatedAt() =
        runTest {
            val src = info("move-ts-src.txt")
            store.put(src, "data".encodeToByteArray())
            val srcStat = store.stat(src)
            assertTrue(srcStat.isOk)
            val originalCreated = srcStat.value.createdAt

            val dst = info("move-ts-dst.txt")
            store.move(src, dst)

            val dstStat = store.stat(dst)
            assertTrue(dstStat.isOk)
            assertEquals(originalCreated, dstStat.value.createdAt, "Move should preserve createdAt")
        }

    @Test
    fun existsReturnsFalseForNonExistent() =
        runTest {
            val result = store.exists(info("nope.txt"))
            assertTrue(result.isOk)
            assertFalse(result.value)
        }

    @Test
    fun existsReturnsTrueForExisting() =
        runTest {
            store.put(info("exists.txt"), "data".encodeToByteArray())
            val result = store.exists(info("exists.txt"))
            assertTrue(result.isOk)
            assertTrue(result.value)
        }

    @Test
    fun deleteNonExistentReturnsFalse() =
        runTest {
            val result = store.delete(info("no-such.txt"))
            assertTrue(result.isOk)
            assertFalse(result.value)
        }

    @Test
    fun putOverwriteUpdatesLastModified() =
        runTest {
            val r = info("overwrite-ts.txt")
            store.put(r, "v1".encodeToByteArray())
            val firstStat = store.stat(r)

            store.put(r, "v2".encodeToByteArray())
            val secondStat = store.stat(r)

            assertTrue(firstStat.isOk && secondStat.isOk)
            // lastModified should be >= first (timestamps may be same if fast)
            assertTrue(secondStat.value.lastModified!! >= firstStat.value.lastModified!!)
        }

    @Test
    fun listWithMaxResultsAndPagination() =
        runTest {
            for (i in 1..5) store.put(info("page/item$i.txt"), "data$i".encodeToByteArray())
            val result = store.list(info("page/"), ListOptions(prefix = "page/", maxResults = 3))
            assertTrue(result.isOk)
            assertEquals(3, result.value.descriptors.size)
            assertTrue(result.value.hasMore, "Should have more results")
        }

    @Test
    fun createTempUrlReturnsUnsupported() =
        runTest {
            store.put(info("temp.txt"), "data".encodeToByteArray())
            val result = store.createTempUrl(info("temp.txt"))
            assertTrue(result.isErr, "KvBlobStore should not support temp URLs")
        }
}

class KvBlobStoreContractTest : BlobStoreContract() {
    override fun createStore(): BlobStore {
        val backing = InMemoryKvBackingStorageImpl()
        val factory = InMemoryKvStoreFactoryImpl(backing)
        return KvBlobStore(factory.create(InMemoryKvStoreConfig(id = "contract", scopeBinding = KvStoreScopeBinding.APP)))
    }
}
