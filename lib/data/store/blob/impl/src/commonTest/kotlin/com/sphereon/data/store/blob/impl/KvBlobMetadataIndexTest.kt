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

import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobMetadata
import com.sphereon.data.store.blob.BlobStoreSchemes
import com.sphereon.data.store.blob.MetadataSearchQuery
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KvBlobMetadataIndexTest {
    private lateinit var metadataIndex: KvBlobMetadataIndex

    @BeforeTest
    fun setup() {
        val kvBackingStorage = InMemoryKvBackingStorageImpl()
        val kvFactory = InMemoryKvStoreFactoryImpl(kvBackingStorage)
        val kvConfig = InMemoryKvStoreConfig(id = KvBlobMetadataIndex.STORE_ID, scopeBinding = KvStoreScopeBinding.APP)
        val kvStore = kvFactory.create(kvConfig)
        val kvStoreService = TestKvStoreService(kvStore)
        metadataIndex = KvBlobMetadataIndex(kvStoreService)
    }

    private fun makeInfo(path: String): BlobInfo = BlobInfo(storeId = BlobStoreSchemes.MEMORY, path = path)

    private fun makeDescriptor(
        path: String,
        sizeBytes: Long = 100,
        contentType: String? = null,
        contentHash: String? = null,
        custom: Map<String, String> = emptyMap(),
    ): BlobDescriptor =
        BlobDescriptor(
            path = path,
            storeId = BlobStoreSchemes.MEMORY,
            sizeBytes = sizeBytes,
            contentType = contentType,
            contentHash = contentHash,
            metadata = BlobMetadata(contentType = contentType, custom = custom),
        )

    // --- index / getIndexed round-trip ---

    @Test
    fun indexAndGetIndexedRoundTrip() =
        runTest {
            val descriptor = makeDescriptor("docs/readme.txt", contentType = "text/plain")

            val indexResult = metadataIndex.index(descriptor)
            assertTrue(indexResult.isOk, "index should succeed")

            val getResult = metadataIndex.getIndexed(makeInfo("docs/readme.txt"))
            assertTrue(getResult.isOk, "getIndexed should succeed")
            assertNotNull(getResult.value)
            assertEquals(descriptor, getResult.value)
        }

    // --- getIndexed returns null for non-existent ---

    @Test
    fun getIndexedReturnsNullForNonExistent() =
        runTest {
            val info = makeInfo("does/not/exist.txt")
            val result = metadataIndex.getIndexed(info)
            assertTrue(result.isOk, "getIndexed should succeed even for missing entry")
            assertNull(result.value)
        }

    // --- deindex removes entries ---

    @Test
    fun deindexRemovesEntry() =
        runTest {
            val descriptor = makeDescriptor("remove-me.txt")
            metadataIndex.index(descriptor)

            val deindexResult = metadataIndex.deindex(makeInfo("remove-me.txt"))
            assertTrue(deindexResult.isOk, "deindex should succeed")
            assertTrue(deindexResult.value, "deindex should return true for existing entry")

            val getResult = metadataIndex.getIndexed(makeInfo("remove-me.txt"))
            assertTrue(getResult.isOk)
            assertNull(getResult.value, "entry should be gone after deindex")
        }

    // --- deindex returns false for non-existent ---

    @Test
    fun deindexReturnsFalseForNonExistent() =
        runTest {
            val info = makeInfo("never-indexed.txt")
            val result = metadataIndex.deindex(info)
            assertTrue(result.isOk, "deindex should succeed")
            assertFalse(result.value, "deindex should return false for non-existent entry")
        }

    // --- index overwrites existing entries ---

    @Test
    fun indexOverwritesExistingEntry() =
        runTest {
            val original = makeDescriptor("overwrite.txt", sizeBytes = 100, contentType = "text/plain")
            metadataIndex.index(original)

            val updated = makeDescriptor("overwrite.txt", sizeBytes = 999, contentType = "application/json")
            metadataIndex.index(updated)

            val getResult = metadataIndex.getIndexed(makeInfo("overwrite.txt"))
            assertTrue(getResult.isOk)
            val retrieved = getResult.value
            assertNotNull(retrieved)
            assertEquals(999, retrieved.sizeBytes)
            assertEquals("application/json", retrieved.contentType)
        }

    // --- search with contentType filter ---

    @Test
    fun searchByContentType() =
        runTest {
            metadataIndex.index(makeDescriptor("a.png", contentType = "image/png"))
            metadataIndex.index(makeDescriptor("b.pdf", contentType = "application/pdf"))
            metadataIndex.index(makeDescriptor("c.png", contentType = "image/png"))

            val result = metadataIndex.search(MetadataSearchQuery(contentType = "image/png"))
            assertTrue(result.isOk)
            assertEquals(2, result.value.size)
            assertTrue(result.value.all { it.contentType == "image/png" })
        }

    // --- search with custom metadata filter ---

    @Test
    fun searchByCustomMetadata() =
        runTest {
            metadataIndex.index(makeDescriptor("tagged.txt", custom = mapOf("env" to "prod", "team" to "alpha")))
            metadataIndex.index(makeDescriptor("other.txt", custom = mapOf("env" to "dev")))
            metadataIndex.index(makeDescriptor("plain.txt"))

            val result = metadataIndex.search(MetadataSearchQuery(customMetadata = mapOf("env" to "prod")))
            assertTrue(result.isOk)
            assertEquals(1, result.value.size)
            assertEquals("tagged.txt", result.value.first().path)
        }

    // --- search with pathPrefix filter ---

    @Test
    fun searchByPathPrefix() =
        runTest {
            metadataIndex.index(makeDescriptor("docs/readme.txt"))
            metadataIndex.index(makeDescriptor("docs/guide.txt"))
            metadataIndex.index(makeDescriptor("images/logo.png"))

            val result = metadataIndex.search(MetadataSearchQuery(pathPrefix = "docs/"))
            assertTrue(result.isOk)
            assertEquals(2, result.value.size)
            assertTrue(result.value.all { it.path.startsWith("docs/") })
        }

    // --- search with combined filters ---

    @Test
    fun searchWithCombinedFilters() =
        runTest {
            metadataIndex.index(makeDescriptor("docs/a.pdf", contentType = "application/pdf", custom = mapOf("status" to "final")))
            metadataIndex.index(makeDescriptor("docs/b.pdf", contentType = "application/pdf", custom = mapOf("status" to "draft")))
            metadataIndex.index(makeDescriptor("docs/c.txt", contentType = "text/plain", custom = mapOf("status" to "final")))
            metadataIndex.index(makeDescriptor("images/d.pdf", contentType = "application/pdf", custom = mapOf("status" to "final")))

            val result =
                metadataIndex.search(
                    MetadataSearchQuery(
                        contentType = "application/pdf",
                        pathPrefix = "docs/",
                        customMetadata = mapOf("status" to "final"),
                    ),
                )
            assertTrue(result.isOk)
            assertEquals(1, result.value.size)
            assertEquals("docs/a.pdf", result.value.first().path)
        }

    // --- search with no results ---

    @Test
    fun searchReturnsEmptyWhenNothingMatches() =
        runTest {
            metadataIndex.index(makeDescriptor("a.txt", contentType = "text/plain"))

            val result = metadataIndex.search(MetadataSearchQuery(contentType = "video/mp4"))
            assertTrue(result.isOk)
            assertTrue(result.value.isEmpty())
        }

    // --- search respects maxResults ---

    @Test
    fun searchRespectsMaxResults() =
        runTest {
            for (i in 1..10) {
                metadataIndex.index(makeDescriptor("file$i.txt", contentType = "text/plain"))
            }

            val result = metadataIndex.search(MetadataSearchQuery(contentType = "text/plain", maxResults = 3))
            assertTrue(result.isOk)
            assertEquals(3, result.value.size)
        }

    // --- search with empty query returns all ---

    @Test
    fun searchWithEmptyQueryReturnsAll() =
        runTest {
            metadataIndex.index(makeDescriptor("a.txt"))
            metadataIndex.index(makeDescriptor("b.txt"))
            metadataIndex.index(makeDescriptor("c.txt"))

            val result = metadataIndex.search(MetadataSearchQuery())
            assertTrue(result.isOk)
            assertEquals(3, result.value.size)
        }

    // --- findByContentHash with matches ---

    @Test
    fun findByContentHashReturnsMatches() =
        runTest {
            val hash = "sha256:abc123"
            metadataIndex.index(makeDescriptor("file1.txt", contentHash = hash))
            metadataIndex.index(makeDescriptor("file2.txt", contentHash = "sha256:other"))

            val result = metadataIndex.findByContentHash(hash)
            assertTrue(result.isOk)
            assertEquals(1, result.value.size)
            assertEquals("file1.txt", result.value.first().path)
        }

    // --- findByContentHash with no matches ---

    @Test
    fun findByContentHashReturnsEmptyWhenNoMatch() =
        runTest {
            metadataIndex.index(makeDescriptor("file.txt", contentHash = "sha256:abc"))

            val result = metadataIndex.findByContentHash("sha256:nonexistent")
            assertTrue(result.isOk)
            assertTrue(result.value.isEmpty())
        }

    // --- findByContentHash with multiple matches ---

    @Test
    fun findByContentHashReturnsMultipleMatches() =
        runTest {
            val hash = "sha256:duplicate"
            metadataIndex.index(makeDescriptor("copy1.txt", contentHash = hash))
            metadataIndex.index(makeDescriptor("copy2.txt", contentHash = hash))
            metadataIndex.index(makeDescriptor("copy3.txt", contentHash = hash))

            val result = metadataIndex.findByContentHash(hash)
            assertTrue(result.isOk)
            assertEquals(3, result.value.size)
        }

    // --- search with multiple custom metadata keys ---

    @Test
    fun searchRequiresAllCustomMetadataKeysToMatch() =
        runTest {
            metadataIndex.index(makeDescriptor("partial.txt", custom = mapOf("a" to "1", "b" to "2")))
            metadataIndex.index(makeDescriptor("full.txt", custom = mapOf("a" to "1", "b" to "2", "c" to "3")))
            metadataIndex.index(makeDescriptor("miss.txt", custom = mapOf("a" to "1")))

            val result = metadataIndex.search(MetadataSearchQuery(customMetadata = mapOf("a" to "1", "b" to "2")))
            assertTrue(result.isOk)
            assertEquals(2, result.value.size)
        }

    // --- custom metadata value mismatch ---

    @Test
    fun searchExcludesWhenCustomMetadataValueDiffers() =
        runTest {
            metadataIndex.index(makeDescriptor("wrong-value.txt", custom = mapOf("key" to "wrong")))
            metadataIndex.index(makeDescriptor("right-value.txt", custom = mapOf("key" to "right")))

            val result = metadataIndex.search(MetadataSearchQuery(customMetadata = mapOf("key" to "right")))
            assertTrue(result.isOk)
            assertEquals(1, result.value.size)
            assertEquals("right-value.txt", result.value.first().path)
        }
}
