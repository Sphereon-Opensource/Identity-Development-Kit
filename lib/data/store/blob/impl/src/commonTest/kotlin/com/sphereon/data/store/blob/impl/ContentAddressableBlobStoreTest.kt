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
import com.sphereon.data.store.blob.BlobMetadata
import com.sphereon.data.store.blob.BlobStoreBackends
import com.sphereon.data.store.blob.BlobStoreConfig
import com.sphereon.data.store.blob.BlobStoreSchemes
import com.sphereon.data.store.blob.BlobStoreScopeBinding
import com.sphereon.data.store.blob.cas.ContentAddress
import com.sphereon.data.store.blob.impl.cas.DefaultContentAddressableBlobStore
import com.sphereon.data.store.blob.memory.InMemoryBlobBackingStorageImpl
import com.sphereon.data.store.blob.memory.InMemoryBlobStoreFactoryImpl
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContentAddressableBlobStoreTest {
    private lateinit var cas: DefaultContentAddressableBlobStore

    @BeforeTest
    fun setup() {
        val backingStorage = InMemoryBlobBackingStorageImpl()
        val factory = InMemoryBlobStoreFactoryImpl(backingStorage)
        val config = BlobStoreConfig(id = "cas-test", scopeBinding = BlobStoreScopeBinding.APP, backendId = BlobStoreBackends.MEMORY)
        val blobStore = factory.create(config)
        cas = DefaultContentAddressableBlobStore(blobStore = blobStore, storeId = BlobStoreSchemes.MEMORY)
    }

    @Test
    fun storeAndRetrieve() =
        runTest {
            val data = "hello CAS".encodeToByteArray()
            val storeResult = cas.store(data)
            assertTrue(storeResult.isOk, "store should succeed")

            val address = storeResult.value.contentAddress
            val retrieveResult = cas.retrieve(address)
            assertTrue(retrieveResult.isOk, "retrieve should succeed")
            assertTrue(data.contentEquals(retrieveResult.value.data))
        }

    @Test
    fun deduplication() =
        runTest {
            val data = "dedup content".encodeToByteArray()
            val r1 = cas.store(data)
            val r2 = cas.store(data)

            assertTrue(r1.isOk && r2.isOk)
            assertEquals(r1.value.contentAddress, r2.value.contentAddress, "Same data should produce same address")
        }

    @Test
    fun containsReturnsTrueForStored() =
        runTest {
            val data = "exists check".encodeToByteArray()
            val storeResult = cas.store(data)
            assertTrue(storeResult.isOk)

            val containsResult = cas.contains(storeResult.value.contentAddress)
            assertTrue(containsResult.isOk && containsResult.value)
        }

    @Test
    fun containsReturnsFalseForMissing() =
        runTest {
            val address = ContentAddress.compute("missing data".encodeToByteArray())
            val result = cas.contains(address)
            assertTrue(result.isOk && !result.value)
        }

    @Test
    fun removeDeletesContent() =
        runTest {
            val data = "remove me".encodeToByteArray()
            val storeResult = cas.store(data)
            assertTrue(storeResult.isOk)

            val removeResult = cas.remove(storeResult.value.contentAddress)
            assertTrue(removeResult.isOk && removeResult.value)

            val containsResult = cas.contains(storeResult.value.contentAddress)
            assertTrue(containsResult.isOk && !containsResult.value)
        }

    @Test
    fun verifyReturnsTrueForValidContent() =
        runTest {
            val data = "verify integrity".encodeToByteArray()
            val storeResult = cas.store(data)
            assertTrue(storeResult.isOk)

            val verifyResult = cas.verify(storeResult.value.contentAddress)
            assertTrue(verifyResult.isOk && verifyResult.value)
        }

    @Test
    fun storeWithSha512() =
        runTest {
            val data = "sha512 content".encodeToByteArray()
            val storeResult = cas.store(data, algorithm = DigestAlg.SHA512)
            assertTrue(storeResult.isOk)
            assertEquals(DigestAlg.SHA512, storeResult.value.contentAddress.algorithm)

            val retrieveResult = cas.retrieve(storeResult.value.contentAddress)
            assertTrue(retrieveResult.isOk)
            assertTrue(data.contentEquals(retrieveResult.value.data))
        }

    @Test
    fun retrieveNonExistentReturnsError() =
        runTest {
            val address = ContentAddress.compute("does not exist".encodeToByteArray())
            val result = cas.retrieve(address)
            assertTrue(result.isErr, "retrieve of non-existent content should return error")
        }

    @Test
    fun removeNonExistentReturnsFalse() =
        runTest {
            val address = ContentAddress.compute("never stored".encodeToByteArray())
            val result = cas.remove(address)
            assertTrue(result.isOk, "remove of non-existent should not error")
            assertFalse(result.value, "remove of non-existent should return false")
        }

    @Test
    fun verifyNonExistentReturnsError() =
        runTest {
            val address = ContentAddress.compute("not here".encodeToByteArray())
            val result = cas.verify(address)
            assertTrue(result.isErr, "verify of non-existent content should return error")
        }

    @Test
    fun storePreservesMetadata() =
        runTest {
            val data = "metadata content".encodeToByteArray()
            val metadata = BlobMetadata(contentType = "text/plain", custom = mapOf("source" to "test"))
            val storeResult = cas.store(data, metadata = metadata)
            assertTrue(storeResult.isOk)

            val descriptor = storeResult.value.descriptor
            assertNotNull(descriptor.contentHash, "contentHash should be set on CAS store")
        }

    @Test
    fun storeWithSha384() =
        runTest {
            val data = "sha384 content".encodeToByteArray()
            val storeResult = cas.store(data, algorithm = DigestAlg.SHA384)
            assertTrue(storeResult.isOk)
            assertEquals(DigestAlg.SHA384, storeResult.value.contentAddress.algorithm)
            assertEquals(48, storeResult.value.contentAddress.digest.size)

            val retrieveResult = cas.retrieve(storeResult.value.contentAddress)
            assertTrue(retrieveResult.isOk)
            assertTrue(data.contentEquals(retrieveResult.value.data))
        }
}
