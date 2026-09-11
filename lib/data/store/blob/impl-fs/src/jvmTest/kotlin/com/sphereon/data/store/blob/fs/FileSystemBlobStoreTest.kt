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

package com.sphereon.data.store.blob.fs

import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.ByteArrayBlobSource
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.testing.BlobStoreContract
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileSystemBlobStoreTest {
    private lateinit var fakeFs: FakeFileSystem
    private lateinit var store: FileSystemBlobStore
    private val rootDir = "/blob-test"

    @BeforeTest
    fun setup() {
        fakeFs = FakeFileSystem()
        fakeFs.createDirectories(rootDir.toPath())
        store =
            FileSystemBlobStore(
                rootDir = rootDir,
                autoCreateDirs = true,
                fileSystem = fakeFs,
            )
    }

    @AfterTest
    fun teardown() {
        fakeFs.checkNoOpenFiles()
    }

    private fun info(path: String) = BlobInfo(storeId = "filesystem", path = path)

    @Test
    fun putAndGetRoundtrip() =
        runTest {
            val data = "hello fs".encodeToByteArray()
            val r = info("test/hello.txt").copy(contentType = "text/plain")
            val putResult = store.put(r, data)
            assertTrue(putResult.isOk, "put should succeed")

            val getResult = store.get(r)
            assertTrue(getResult.isOk, "get should succeed")
            assertTrue(data.contentEquals(getResult.value.data))
        }

    @Test
    fun capabilitiesAdvertiseRevisionAndConditionalWriteSupport() {
        assertTrue(store.capabilities.supportsStreamingRead)
        assertTrue(store.capabilities.supportsStreamingWrite)
        assertTrue(store.capabilities.supportsEtag)
        assertTrue(store.capabilities.supportsRevisions)
        assertTrue(store.capabilities.supportsConditionalWrites)
        assertFalse(store.capabilities.supportsConditionalDelete)
    }

    @Test
    fun streamWriteAndBoundedReadRoundtrip() =
        runTest {
            val target = info("stream/large.bin").copy(contentType = "application/octet-stream")
            val data = ByteArray(300_000) { (it % 251).toByte() }

            val stored = store.putStream(target, ByteArrayBlobSource(data))
            assertTrue(stored.isOk)
            assertEquals(data.size.toLong(), stored.value.sizeBytes)

            val opened = store.openRead(target)
            assertTrue(opened.isOk)
            val chunks = mutableListOf<ByteArray>()
            try {
                while (true) {
                    val chunk =
                        opened.value.source
                            .read(11_000)
                            .value ?: break
                    assertTrue(chunk.size <= 11_000)
                    chunks += chunk
                }
            } finally {
                opened.value.source.close()
            }
            assertTrue(chunks.size > 1)
            val roundtrip = ByteArray(data.size)
            var offset = 0
            chunks.forEach { chunk ->
                chunk.copyInto(roundtrip, offset)
                offset += chunk.size
            }
            assertTrue(data.contentEquals(roundtrip))
        }

    @Test
    fun conditionalWriteUsesRevisionAndRejectsStaleUpdates() =
        runTest {
            val target = info("conditional.txt")
            val initial = store.put(target, "original".encodeToByteArray()).value

            val result = store.put(target, "changed".encodeToByteArray(), PutOptions(expectedRevision = initial.revision))

            assertTrue(result.isOk)
            assertEquals(2L, result.value.revision)
            val stale = store.put(target, "stale".encodeToByteArray(), PutOptions(expectedRevision = initial.revision))
            assertTrue(stale.isErr)
            assertEquals("BLOB_PRECONDITION_FAILED", stale.error.code)
            assertEquals(
                "changed",
                store
                    .get(target)
                    .value.data
                    .decodeToString(),
            )
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

            val result = store.delete(r)
            assertTrue(result.isOk && result.value)

            val exists = store.exists(r)
            assertTrue(exists.isOk && !exists.value)
        }

    @Test
    fun existsWorks() =
        runTest {
            val r = info("exists.txt")
            store.put(r, "data".encodeToByteArray())

            assertTrue(store.exists(r).let { it.isOk && it.value })
            assertTrue(store.exists(info("nope.txt")).let { it.isOk && !it.value })
        }

    @Test
    fun statReturnsDescriptor() =
        runTest {
            val data = "stat data".encodeToByteArray()
            val r = info("stat.json")
            store.put(r.copy(contentType = "application/json"), data)

            val result = store.stat(r)
            assertTrue(result.isOk)
            assertEquals(data.size.toLong(), result.value.sizeBytes)
            assertEquals("application/json", result.value.contentType)
        }

    @Test
    fun sidecarMetadataStoresContentType() =
        runTest {
            val r = info("sidecar-test.json")
            store.put(r.copy(contentType = "application/json"), """{"key":"value"}""".encodeToByteArray())

            val metaPath = "$rootDir/sidecar-test.json.meta.json".toPath()
            assertTrue(fakeFs.exists(metaPath), "Sidecar metadata file should exist")

            val statResult = store.stat(r)
            assertTrue(statResult.isOk)
            assertEquals("application/json", statResult.value.contentType)
        }

    @Test
    fun deleteCleansUpSidecar() =
        runTest {
            val r = info("delete-sidecar.txt")
            store.put(r.copy(contentType = "text/plain"), "data".encodeToByteArray())

            store.delete(r)

            assertFalse(fakeFs.exists("$rootDir/delete-sidecar.txt".toPath()))
            assertFalse(fakeFs.exists("$rootDir/delete-sidecar.txt.meta.json".toPath()))
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
            store.put(src, "copy data".encodeToByteArray())

            val result = store.copy(src, dst)
            assertTrue(result.isOk)

            assertTrue(store.exists(src).let { it.isOk && it.value })
            assertEquals(
                "copy data",
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
            store.put(src, "move data".encodeToByteArray())

            val result = store.move(src, dst)
            assertTrue(result.isOk)

            assertTrue(store.exists(src).let { it.isOk && !it.value })
            assertEquals(
                "move data",
                store
                    .get(dst)
                    .value.data
                    .decodeToString(),
            )
        }

    @Test
    fun autoCreateDirectories() =
        runTest {
            val r = info("deep/nested/dir/file.txt")
            val result = store.put(r, "deep".encodeToByteArray())
            assertTrue(result.isOk)
            assertTrue(fakeFs.exists("$rootDir/deep/nested/dir/file.txt".toPath()))
        }

    @Test
    fun putIfAbsentSucceedsForNewBlob() =
        runTest {
            val r = info("put-if-absent-new.txt")
            val result = store.putIfAbsent(r, "data".encodeToByteArray())
            assertTrue(result.isOk, "putIfAbsent should succeed for new blob")

            val getResult = store.get(r)
            assertTrue(getResult.isOk)
            assertEquals("data", getResult.value.data.decodeToString())
        }

    @Test
    fun putIfAbsentFailsForExistingBlob() =
        runTest {
            val r = info("put-if-absent-existing.txt")
            store.put(r, "original".encodeToByteArray())

            val result = store.putIfAbsent(r, "duplicate".encodeToByteArray())
            assertTrue(result.isErr, "putIfAbsent should fail when blob already exists")

            val getResult = store.get(r)
            assertTrue(getResult.isOk)
            assertEquals("original", getResult.value.data.decodeToString())
        }

    @Test
    fun deletePrefixRemovesMatchingFiles() =
        runTest {
            store.put(info("prefix-del/a.txt"), "a".encodeToByteArray())
            store.put(info("prefix-del/b.txt"), "b".encodeToByteArray())
            store.put(info("other-dir/c.txt"), "c".encodeToByteArray())

            val result = store.deletePrefix(info("prefix-del"))
            assertTrue(result.isOk)
            assertTrue(result.value >= 2, "should delete at least the 2 matching files")

            val existsA = store.exists(info("prefix-del/a.txt"))
            assertTrue(existsA.isOk && !existsA.value)

            val existsC = store.exists(info("other-dir/c.txt"))
            assertTrue(existsC.isOk && existsC.value)
        }

    @Test
    fun deletePrefixReturnsZeroForNoMatches() =
        runTest {
            store.put(info("keep/a.txt"), "a".encodeToByteArray())

            val result = store.deletePrefix(info("nonexistent-prefix"))
            assertTrue(result.isOk)
            assertEquals(0, result.value)
        }

    @Test
    fun listWithEmptyStoreReturnsEmptyList() =
        runTest {
            val result = store.list(BlobInfo())
            assertTrue(result.isOk)
            assertTrue(result.value.descriptors.isEmpty())
        }

    @Test
    fun statForNonExistentReturnsError() =
        runTest {
            val result = store.stat(info("does-not-exist.txt"))
            assertTrue(result.isErr, "stat on non-existent blob should return error")
        }

    @Test
    fun copyFromNonExistentReturnsError() =
        runTest {
            val result = store.copy(info("no-source.txt"), info("dst.txt"))
            assertTrue(result.isErr, "copy from non-existent source should return error")
        }

    @Test
    fun moveFromNonExistentReturnsError() =
        runTest {
            val result = store.move(info("no-source.txt"), info("dst.txt"))
            assertTrue(result.isErr, "move from non-existent source should return error")
        }

    @Test
    fun copyPreservesSidecarMetadata() =
        runTest {
            val src = info("copy-meta-src.json")
            val dst = info("copy-meta-dst.json")
            store.put(src.copy(contentType = "application/json"), """{"a":1}""".encodeToByteArray())

            val copyResult = store.copy(src, dst)
            assertTrue(copyResult.isOk)

            val srcMeta = "$rootDir/copy-meta-src.json.meta.json".toPath()
            val dstMeta = "$rootDir/copy-meta-dst.json.meta.json".toPath()
            assertTrue(fakeFs.exists(srcMeta), "source sidecar should still exist")
            assertTrue(fakeFs.exists(dstMeta), "destination sidecar should exist after copy")

            val statResult = store.stat(dst)
            assertTrue(statResult.isOk)
            assertEquals("application/json", statResult.value.contentType)
        }

    @Test
    fun binaryDataIntegrity() =
        runTest {
            val r = info("binary-test.png")
            // Simulate PNG-like header bytes
            val data =
                byteArrayOf(
                    0x89.toByte(),
                    0x50,
                    0x4E,
                    0x47,
                    0x0D,
                    0x0A,
                    0x1A,
                    0x0A,
                    0x00,
                    0xFF.toByte(),
                    0xFE.toByte(),
                    0x01,
                    0x7F,
                    0x80.toByte(),
                )

            val putResult = store.put(r, data)
            assertTrue(putResult.isOk)

            val getResult = store.get(r)
            assertTrue(getResult.isOk)
            assertTrue(data.contentEquals(getResult.value.data), "binary data should survive roundtrip")
        }

    @Test
    fun nestedDirectoryCreationOnCopy() =
        runTest {
            val src = info("flat-file.txt")
            val dst = info("deep/nested/dir/copied-file.txt")
            store.put(src, "nested copy".encodeToByteArray())

            val result = store.copy(src, dst)
            assertTrue(result.isOk)
            assertTrue(fakeFs.exists("$rootDir/deep/nested/dir/copied-file.txt".toPath()))
        }

    @Test
    fun putWithoutContentTypePersistsRevisionSidecar() =
        runTest {
            val r = info("no-sidecar.txt")
            store.put(r, "data".encodeToByteArray())

            val metaPath = "$rootDir/no-sidecar.txt.meta.json".toPath()
            assertTrue(fakeFs.exists(metaPath), "revision sidecar is required for conditional writes")
        }

    @Test
    fun overwriteExistingBlobChangesContent() =
        runTest {
            val r = info("overwrite-test.txt")
            store.put(r, "first".encodeToByteArray())

            val overwriteResult = store.put(r, "second".encodeToByteArray())
            assertTrue(overwriteResult.isOk)

            val getResult = store.get(r)
            assertTrue(getResult.isOk)
            assertEquals("second", getResult.value.data.decodeToString())
        }

    @Test
    fun emptyByteArrayPutAndGet() =
        runTest {
            val r = info("empty.bin")
            val data = ByteArray(0)

            val putResult = store.put(r, data)
            assertTrue(putResult.isOk)

            val getResult = store.get(r)
            assertTrue(getResult.isOk)
            assertEquals(0, getResult.value.data.size)
        }
}

class FileSystemBlobStoreContractTest : BlobStoreContract() {
    override fun createStore(): BlobStore {
        val fs = FakeFileSystem()
        fs.createDirectories("/contract".toPath())
        return FileSystemBlobStore("/contract", fileSystem = fs)
    }
}
