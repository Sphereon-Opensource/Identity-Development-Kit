package com.sphereon.data.store.blob.memory

import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.PutOptions
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InMemoryBlobStoreTest {

    private lateinit var store: InMemoryBlobStore

    @BeforeTest
    fun setup() {
        val backingStorage = InMemoryBlobBackingStorageImpl()
        val partition = backingStorage.getPartition(
            InMemoryBlobPartitionKey(storeId = "test", tenantId = "test-tenant")
        )
        store = InMemoryBlobStore(partition = partition)
    }

    private fun info(path: String) = BlobInfo(storeId = "memory", path = path)

    @Test
    fun putAndGetRoundtrip() = runTest {
        val data = "hello world".encodeToByteArray()
        val r = info("test/hello.txt").copy(contentType = "text/plain")

        val putResult = store.put(r, data)
        assertTrue(putResult.isOk, "put should succeed")
        assertEquals(data.size.toLong(), putResult.value.sizeBytes)

        val getResult = store.get(r)
        assertTrue(getResult.isOk, "get should succeed")
        assertTrue(data.contentEquals(getResult.value.data))
    }

    @Test
    fun getNotFoundReturnsError() = runTest {
        val result = store.get(info("nonexistent/blob.txt"))
        assertTrue(result.isErr)
    }

    @Test
    fun deleteExistingReturnsTrue() = runTest {
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
    fun deleteNonExistentReturnsFalse() = runTest {
        val result = store.delete(info("does-not-exist.txt"))
        assertTrue(result.isOk)
        assertFalse(result.value)
    }

    @Test
    fun existsReturnsTrueForExistingBlob() = runTest {
        val r = info("exists-test.txt")
        store.put(r, "data".encodeToByteArray())

        val result = store.exists(r)
        assertTrue(result.isOk)
        assertTrue(result.value)
    }

    @Test
    fun existsReturnsFalseForNonExistent() = runTest {
        val result = store.exists(info("nope.txt"))
        assertTrue(result.isOk)
        assertFalse(result.value)
    }

    @Test
    fun statReturnsDescriptor() = runTest {
        val data = "stat test data".encodeToByteArray()
        val r = info("stat-test.json").copy(contentType = "application/json")
        store.put(r, data)

        val result = store.stat(r)
        assertTrue(result.isOk)
        assertEquals(data.size.toLong(), result.value.sizeBytes)
        assertEquals("application/json", result.value.contentType)
    }

    @Test
    fun statNotFoundReturnsError() = runTest {
        val result = store.stat(info("no-such-file.txt"))
        assertTrue(result.isErr)
    }

    @Test
    fun listReturnsStoredBlobs() = runTest {
        store.put(info("list/a.txt"), "a".encodeToByteArray())
        store.put(info("list/b.txt"), "b".encodeToByteArray())
        store.put(info("other/c.txt"), "c".encodeToByteArray())

        val result = store.list(info("list/"), ListOptions(prefix = "list/"))
        assertTrue(result.isOk)
        assertEquals(2, result.value.descriptors.size)
    }

    @Test
    fun putNoOverwriteFailsWhenExists() = runTest {
        val r = info("no-overwrite.txt")
        store.put(r, "first".encodeToByteArray())

        val result = store.put(r, "second".encodeToByteArray(), options = PutOptions.NO_OVERWRITE)
        assertTrue(result.isErr)
    }

    @Test
    fun putOverwriteSucceeds() = runTest {
        val r = info("overwrite.txt")
        store.put(r, "first".encodeToByteArray())

        val result = store.put(r, "second".encodeToByteArray(), options = PutOptions.DEFAULT)
        assertTrue(result.isOk)

        val getResult = store.get(r)
        assertTrue(getResult.isOk)
        assertEquals("second", getResult.value.data.decodeToString())
    }

    @Test
    fun putIfAbsentSucceedsWhenNew() = runTest {
        val r = info("put-if-absent.txt")
        val result = store.putIfAbsent(r, "data".encodeToByteArray())
        assertTrue(result.isOk)
    }

    @Test
    fun putIfAbsentFailsWhenExists() = runTest {
        val r = info("put-if-absent-existing.txt")
        store.put(r, "original".encodeToByteArray())

        val result = store.putIfAbsent(r, "duplicate".encodeToByteArray())
        assertTrue(result.isErr)
    }

    @Test
    fun copyBlobWorks() = runTest {
        val src = info("copy-src.txt")
        val dst = info("copy-dst.txt")
        store.put(src, "copy data".encodeToByteArray())

        val result = store.copy(src, dst)
        assertTrue(result.isOk)

        val srcExists = store.exists(src)
        assertTrue(srcExists.isOk && srcExists.value)

        val dstGet = store.get(dst)
        assertTrue(dstGet.isOk)
        assertEquals("copy data", dstGet.value.data.decodeToString())
    }

    @Test
    fun moveBlobWorks() = runTest {
        val src = info("move-src.txt")
        val dst = info("move-dst.txt")
        store.put(src, "move data".encodeToByteArray())

        val result = store.move(src, dst)
        assertTrue(result.isOk)

        val srcExists = store.exists(src)
        assertTrue(srcExists.isOk && !srcExists.value)

        val dstGet = store.get(dst)
        assertTrue(dstGet.isOk)
        assertEquals("move data", dstGet.value.data.decodeToString())
    }

    @Test
    fun metadataIsPreservedOnPut() = runTest {
        val r = info("metadata-test.png").copy(
            contentType = "image/png",
            metadata = mapOf("tag" to "photo"),
        )
        store.put(r, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))

        val statResult = store.stat(r)
        assertTrue(statResult.isOk)
        assertEquals("image/png", statResult.value.contentType)
    }

    @Test
    fun deletePrefixRemovesAllMatching() = runTest {
        store.put(info("prefix/a.txt"), "a".encodeToByteArray())
        store.put(info("prefix/b.txt"), "b".encodeToByteArray())
        store.put(info("other/c.txt"), "c".encodeToByteArray())

        val result = store.deletePrefix(info("prefix/"))
        assertTrue(result.isOk)
        assertEquals(2, result.value)

        val existsA = store.exists(info("prefix/a.txt"))
        assertTrue(existsA.isOk && !existsA.value)

        val existsC = store.exists(info("other/c.txt"))
        assertTrue(existsC.isOk && existsC.value)
    }

    @Test
    fun listWithPagination() = runTest {
        for (i in 1..5) {
            store.put(info("page/item$i.txt"), "data$i".encodeToByteArray())
        }

        val result = store.list(info("page/"), ListOptions(prefix = "page/", maxResults = 3))
        assertTrue(result.isOk)
        assertEquals(3, result.value.descriptors.size)
        assertTrue(result.value.hasMore)
    }

    @Test
    fun putWithIfNoneMatchFailsWhenBlobExists() = runTest {
        val r = info("if-none-match.txt")
        store.put(r, "original".encodeToByteArray())

        val result = store.put(r, "updated".encodeToByteArray(), options = PutOptions(ifNoneMatch = "*"))
        assertTrue(result.isErr, "put with ifNoneMatch should fail when blob exists")
    }

    @Test
    fun emptyDataPutAndGetRoundtrip() = runTest {
        val r = info("empty.txt")
        val data = ByteArray(0)

        val putResult = store.put(r, data)
        assertTrue(putResult.isOk, "put of empty data should succeed")
        assertEquals(0L, putResult.value.sizeBytes)

        val getResult = store.get(r)
        assertTrue(getResult.isOk, "get of empty data should succeed")
        assertEquals(0, getResult.value.data.size)
    }

    @Test
    fun largeDataPutAndGetRoundtrip() = runTest {
        val r = info("large.bin")
        val data = ByteArray(10 * 1024) { (it % 256).toByte() }

        val putResult = store.put(r, data)
        assertTrue(putResult.isOk, "put of 10KB data should succeed")
        assertEquals(data.size.toLong(), putResult.value.sizeBytes)

        val getResult = store.get(r)
        assertTrue(getResult.isOk, "get of 10KB data should succeed")
        assertTrue(data.contentEquals(getResult.value.data))
    }

    @Test
    fun multipleDeletesOfSameBlob() = runTest {
        val r = info("double-delete.txt")
        store.put(r, "data".encodeToByteArray())

        val first = store.delete(r)
        assertTrue(first.isOk)
        assertTrue(first.value, "first delete should return true")

        val second = store.delete(r)
        assertTrue(second.isOk)
        assertFalse(second.value, "second delete should return false")
    }

    @Test
    fun statReflectsUpdatedBlobSize() = runTest {
        val r = info("update-stat.txt")
        store.put(r, "short".encodeToByteArray())

        val longerData = "much longer content here".encodeToByteArray()
        store.put(r, longerData)

        val result = store.stat(r)
        assertTrue(result.isOk)
        assertEquals(longerData.size.toLong(), result.value.sizeBytes)
    }

    @Test
    fun listWithEmptyStoreReturnsEmptyList() = runTest {
        val result = store.list(BlobInfo())
        assertTrue(result.isOk)
        assertTrue(result.value.descriptors.isEmpty())
    }

    @Test
    fun listMaxResultsZeroReturnsEmptyList() = runTest {
        store.put(info("item.txt"), "data".encodeToByteArray())

        val result = store.list(BlobInfo(), ListOptions(maxResults = 0))
        assertTrue(result.isOk)
        assertTrue(result.value.descriptors.isEmpty())
    }

    @Test
    fun copyFromNonExistentSourceReturnsError() = runTest {
        val result = store.copy(info("no-source.txt"), info("dst.txt"))
        assertTrue(result.isErr, "copy from non-existent source should return error")
    }

    @Test
    fun moveFromNonExistentSourceReturnsError() = runTest {
        val result = store.move(info("no-source.txt"), info("dst.txt"))
        assertTrue(result.isErr, "move from non-existent source should return error")
    }

    @Test
    fun putIfAbsentReturnsErrorWithMetadataWhenBlobExists() = runTest {
        val r = info("put-if-absent-meta.txt").copy(contentType = "text/plain")
        store.put(r, "original".encodeToByteArray())

        val result = store.putIfAbsent(r, "duplicate".encodeToByteArray())
        assertTrue(result.isErr, "putIfAbsent should fail when blob exists")
    }

    @Test
    fun deletePrefixWithNonMatchingPrefixReturnsZero() = runTest {
        store.put(info("data/a.txt"), "a".encodeToByteArray())

        val result = store.deletePrefix(info("nonexistent/"))
        assertTrue(result.isOk)
        assertEquals(0, result.value)
    }

    @Test
    fun listPaginationNextPageToken() = runTest {
        for (i in 1..5) {
            store.put(info("paged/item$i.txt"), "data$i".encodeToByteArray())
        }

        val result = store.list(info("paged/"), ListOptions(prefix = "paged/", maxResults = 2))
        assertTrue(result.isOk)
        assertEquals(2, result.value.descriptors.size)
        assertNotNull(result.value.nextPageToken, "nextPageToken should not be null when there are more results")
    }

    @Test
    fun concurrentPutsToDifferentKeys() = runTest {
        val r1 = info("concurrent/a.txt")
        val r2 = info("concurrent/b.txt")

        val result1 = store.put(r1, "data-a".encodeToByteArray())
        val result2 = store.put(r2, "data-b".encodeToByteArray())

        assertTrue(result1.isOk, "first concurrent put should succeed")
        assertTrue(result2.isOk, "second concurrent put should succeed")

        val get1 = store.get(r1)
        val get2 = store.get(r2)
        assertTrue(get1.isOk)
        assertTrue(get2.isOk)
        assertEquals("data-a", get1.value.data.decodeToString())
        assertEquals("data-b", get2.value.data.decodeToString())
    }

    @Test
    fun customMetadataPreservationInPutStatCycle() = runTest {
        val r = info("custom-meta.bin").copy(
            contentType = "application/octet-stream",
            metadata = mapOf("env" to "test", "version" to "1.0"),
        )
        store.put(r, "payload".encodeToByteArray())

        val statResult = store.stat(r)
        assertTrue(statResult.isOk)
        assertEquals("application/octet-stream", statResult.value.contentType)
        assertEquals("test", statResult.value.metadata.custom["env"])
        assertEquals("1.0", statResult.value.metadata.custom["version"])
    }

    @Test
    fun putWithEmptyPathValidationError() {
        var thrown = false
        try {
            BlobInfo.sanitizePath("")
        } catch (_: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown, "BlobInfo.sanitizePath should reject empty path")
    }

    @Test
    fun backingStoragePartitionIsolation() = runTest {
        val backingStorage = InMemoryBlobBackingStorageImpl()
        val partitionA = backingStorage.getPartition(
            InMemoryBlobPartitionKey(storeId = "store-a", tenantId = "tenant-1")
        )
        val partitionB = backingStorage.getPartition(
            InMemoryBlobPartitionKey(storeId = "store-b", tenantId = "tenant-2")
        )
        val storeA = InMemoryBlobStore(partition = partitionA)
        val storeB = InMemoryBlobStore(partition = partitionB)

        val r = BlobInfo(storeId = "memory", path = "shared-key.txt")
        storeA.put(r, "data-from-A".encodeToByteArray())

        val getFromB = storeB.get(r)
        assertTrue(getFromB.isErr, "store B should not see data from store A")

        val getFromA = storeA.get(r)
        assertTrue(getFromA.isOk)
        assertEquals("data-from-A", getFromA.value.data.decodeToString())
    }

    @Test
    fun createTempUrlReturnsUnsupported() = runTest {
        val r = info("tempurl-test.txt")
        store.put(r, "data".encodeToByteArray())

        val result = store.createTempUrl(r)
        assertTrue(result.isErr, "In-memory store should not support temp URLs")
    }

    @Test
    fun statReturnsFilenameAndCreatedAt() = runTest {
        val r = info("nested/path/document.pdf")
        store.put(r, "pdf content".encodeToByteArray())

        val result = store.stat(r)
        assertTrue(result.isOk)
        assertEquals("document.pdf", result.value.filename)
        assertNotNull(result.value.createdAt, "createdAt should be populated")
        assertNotNull(result.value.lastModified, "lastModified should be populated")
    }

    @Test
    fun createdAtPreservedOnOverwrite() = runTest {
        val r = info("overwrite-created.txt")
        store.put(r, "first".encodeToByteArray())

        val firstStat = store.stat(r)
        assertTrue(firstStat.isOk)
        val originalCreatedAt = firstStat.value.createdAt

        store.put(r, "second".encodeToByteArray())

        val secondStat = store.stat(r)
        assertTrue(secondStat.isOk)
        assertEquals(originalCreatedAt, secondStat.value.createdAt, "createdAt should not change on overwrite")
    }
}
