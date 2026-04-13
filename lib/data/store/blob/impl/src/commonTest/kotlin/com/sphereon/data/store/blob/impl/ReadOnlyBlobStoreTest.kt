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

import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.BlobStoreBackends
import com.sphereon.data.store.blob.BlobStoreConfig
import com.sphereon.data.store.blob.BlobStoreSchemes
import com.sphereon.data.store.blob.BlobStoreScopeBinding
import com.sphereon.data.store.blob.memory.InMemoryBlobBackingStorageImpl
import com.sphereon.data.store.blob.memory.InMemoryBlobStoreFactoryImpl
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ReadOnlyBlobStoreTest {
    private lateinit var rawStore: BlobStore
    private lateinit var readOnlyStore: ReadOnlyBlobStore

    @BeforeTest
    fun setup() {
        val backingStorage = InMemoryBlobBackingStorageImpl()
        val factory = InMemoryBlobStoreFactoryImpl(backingStorage)
        val config = BlobStoreConfig(id = "test", scopeBinding = BlobStoreScopeBinding.APP, backendId = BlobStoreBackends.MEMORY)
        rawStore = factory.create(config)
        readOnlyStore = ReadOnlyBlobStore(rawStore)
    }

    private fun info(path: String) = BlobInfo(storeId = BlobStoreSchemes.MEMORY, path = path)

    @Test
    fun readOperationsSucceed() =
        runTest {
            rawStore.put(info("readable.txt"), "hello".encodeToByteArray())

            val getResult = readOnlyStore.get(info("readable.txt"))
            assertTrue(getResult.isOk)

            val existsResult = readOnlyStore.exists(info("readable.txt"))
            assertTrue(existsResult.isOk && existsResult.value)

            val statResult = readOnlyStore.stat(info("readable.txt"))
            assertTrue(statResult.isOk)

            val listResult = readOnlyStore.list(info(""))
            assertTrue(listResult.isOk)
        }

    @Test
    fun putIsRejected() =
        runTest {
            val result = readOnlyStore.put(info("new.txt"), "data".encodeToByteArray())
            assertTrue(result.isErr, "put should be rejected on read-only store")
        }

    @Test
    fun deleteIsRejected() =
        runTest {
            val result = readOnlyStore.delete(info("any.txt"))
            assertTrue(result.isErr, "delete should be rejected on read-only store")
        }

    @Test
    fun copyIsRejected() =
        runTest {
            val result = readOnlyStore.copy(info("src.txt"), info("dst.txt"))
            assertTrue(result.isErr, "copy should be rejected on read-only store")
        }

    @Test
    fun moveIsRejected() =
        runTest {
            val result = readOnlyStore.move(info("src.txt"), info("dst.txt"))
            assertTrue(result.isErr, "move should be rejected on read-only store")
        }

    @Test
    fun putIfAbsentIsRejected() =
        runTest {
            val result = readOnlyStore.putIfAbsent(info("new.txt"), "data".encodeToByteArray())
            assertTrue(result.isErr, "putIfAbsent should be rejected on read-only store")
        }

    @Test
    fun deletePrefixIsRejected() =
        runTest {
            val result = readOnlyStore.deletePrefix(info("some-prefix"))
            assertTrue(result.isErr, "deletePrefix should be rejected on read-only store")
        }
}
