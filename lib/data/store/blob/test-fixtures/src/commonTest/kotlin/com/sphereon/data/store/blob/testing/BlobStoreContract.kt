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

package com.sphereon.data.store.blob.testing

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.BlobByteSource
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobReadRange
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.ByteArrayBlobSource
import com.sphereon.data.store.blob.DeleteOptions
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.cas.ContentAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reusable backend contract. Concrete backend modules provide an isolated store per test. */
abstract class BlobStoreContract {
    protected abstract fun createStore(): BlobStore

    protected open fun info(path: String): BlobInfo = BlobInfo(path = path)

    @Test
    fun sharedCrudListCopyMoveContract() =
        runTest {
            val store = createStore()
            val source = info("contract/source.bin")
            val data = ByteArray(1024) { (it % 251).toByte() }
            assertTrue(store.put(source, data).isOk)
            assertContentEquals(data, store.get(source).value.data)
            assertEquals(data.size.toLong(), store.stat(source).value.sizeBytes)
            assertTrue(
                store
                    .list(info("contract"), ListOptions(prefix = "contract/", recursive = true))
                    .value.descriptors
                    .isNotEmpty(),
            )
            if (store.capabilities.supportsCopy) {
                val copy = info("contract/copy.bin")
                assertTrue(store.copy(source, copy).isOk)
                assertContentEquals(data, store.get(copy).value.data)
            }
            if (store.capabilities.supportsMove) {
                val moved = info("contract/moved.bin")
                assertTrue(store.move(source, moved).isOk)
                assertFalse(store.exists(source).value)
                assertContentEquals(data, store.get(moved).value.data)
            }
        }

    @Test
    fun sharedRangeAndIntegrityContract() =
        runTest {
            val store = createStore()
            val target = info("contract/range.bin")
            val data = "0123456789abcdef".encodeToByteArray()
            store.put(target, data)
            val expected = ContentAddress.compute(data)
            if (store.capabilities.supportsIntegrityVerification) {
                assertTrue(store.verifyIntegrity(target, expected).value)
                val wrong = ContentAddress.compute("wrong".encodeToByteArray())
                assertFalse(store.verifyIntegrity(target, wrong).value)
            }
            if (store.capabilities.supportsRangeReads) {
                val opened = store.openReadRange(target, BlobReadRange(3, 9))
                assertTrue(opened.isOk)
                assertContentEquals("345678".encodeToByteArray(), readAll(opened.value.source))
            } else {
                assertTrue(store.openReadRange(target, BlobReadRange(3, 9)).isErr)
            }
        }

    @Test
    fun sharedStreamingCancellationContract() =
        runTest {
            val store = createStore()
            val target = info("contract/cancelled.bin")
            if (!store.capabilities.supportsStreamingWrite) {
                assertTrue(store.putStream(target, ByteArrayBlobSource(byteArrayOf(1))).isErr)
                return@runTest
            }
            val source = CancellingSource()
            var cancelled = false
            try {
                store.putStream(target, source)
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled, "CancellationException must propagate")
            assertTrue(source.closed, "Source must be closed after cancellation")
            assertFalse(store.exists(target).value, "A cancelled stream must not publish a partial blob")
        }

    @Test
    fun sharedCapabilityAndConditionalTruthfulnessContract() =
        runTest {
            val store = createStore()
            val target = info("contract/conditions.bin")
            val initial = store.put(target, "one".encodeToByteArray()).value

            if (store.capabilities.supportsEtag) assertTrue(initial.etag != null)
            if (store.capabilities.supportsRevisions) assertTrue(initial.revision != null)

            val condition =
                when {
                    store.capabilities.supportsEtag -> PutOptions(ifMatch = initial.etag)
                    store.capabilities.supportsRevisions -> PutOptions(expectedRevision = initial.revision)
                    else -> PutOptions(ifMatch = "unsupported-token")
                }
            val update = store.put(target, "two".encodeToByteArray(), condition)
            if (store.capabilities.supportsConditionalWrites) {
                assertTrue(
                    store.capabilities.supportsEtag || store.capabilities.supportsRevisions,
                    "Conditional writes require an advertised version token",
                )
                assertTrue(update.isOk)
                val stale = store.put(target, "stale".encodeToByteArray(), condition)
                assertTrue(stale.isErr, "Stale conditional update must fail")

                val raceTarget = info("contract/conditional-race.bin")
                val raceInitial = store.put(raceTarget, "initial".encodeToByteArray()).value
                val raceCondition =
                    if (store.capabilities.supportsEtag) {
                        PutOptions(ifMatch = raceInitial.etag)
                    } else {
                        PutOptions(expectedRevision = raceInitial.revision)
                    }
                val raceResults =
                    listOf(
                        async { store.put(raceTarget, "first".encodeToByteArray(), raceCondition) },
                        async { store.put(raceTarget, "second".encodeToByteArray(), raceCondition) },
                    ).awaitAll()
                assertEquals(1, raceResults.count { it.isOk }, "Exactly one concurrent conditional write must win")
            } else {
                assertTrue(update.isErr, "A backend must reject unadvertised conditional writes")
            }

            val current = store.stat(target).value
            val deleteOptions =
                if (store.capabilities.supportsEtag) DeleteOptions(ifMatch = current.etag) else DeleteOptions(expectedRevision = current.revision ?: 1)
            val deletion = store.deleteConditional(target, deleteOptions)
            if (store.capabilities.supportsConditionalDelete) {
                assertTrue(deletion.isOk && deletion.value)
                assertTrue(store.deleteConditional(target, deleteOptions).isErr, "Stale conditional delete must fail")
            } else {
                assertTrue(deletion.isErr, "A backend must reject unadvertised conditional deletes")
            }

            val readTarget = info("contract/stream-read.bin")
            store.put(readTarget, "stream".encodeToByteArray())
            val readResult = store.openRead(readTarget)
            assertEquals(store.capabilities.supportsStreamingRead, readResult.isOk)
            if (readResult.isOk) readResult.value.source.close()
            val writeResult = store.putStream(info("contract/stream-write.bin"), ByteArrayBlobSource("stream".encodeToByteArray()))
            assertEquals(store.capabilities.supportsStreamingWrite, writeResult.isOk)
        }

    private suspend fun readAll(source: BlobByteSource): ByteArray {
        val chunks = mutableListOf<ByteArray>()
        var size = 0
        try {
            while (true) {
                val chunk = source.read(4).value ?: break
                chunks += chunk
                size += chunk.size
            }
        } finally {
            source.close()
        }
        val result = ByteArray(size)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }
}

private class CancellingSource : BlobByteSource {
    private var reads = 0
    var closed = false

    override suspend fun read(maxBytes: Int): IdkResult<ByteArray?, IdkError> {
        reads++
        if (reads > 1) throw CancellationException("contract cancellation")
        return Ok(ByteArray(minOf(maxBytes, 32)) { 7 })
    }

    override suspend fun close() {
        closed = true
    }
}
