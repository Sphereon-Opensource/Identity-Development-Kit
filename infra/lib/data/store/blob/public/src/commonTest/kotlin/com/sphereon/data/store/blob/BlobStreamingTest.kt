/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.data.store.blob

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.cas.ContentAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlobStreamingTest {
    @Test
    fun byteArraySourceHonorsRequestedChunkSizeAndSignalsEof() =
        runTest {
            val source = ByteArrayBlobSource("abcdefgh".encodeToByteArray())

            val first = source.read(3)
            val second = source.read(3)
            val third = source.read(3)
            val eof = source.read(3)

            assertTrue(first.isOk && second.isOk && third.isOk && eof.isOk)
            assertEquals("abc", first.value!!.decodeToString())
            assertEquals("def", second.value!!.decodeToString())
            assertEquals("gh", third.value!!.decodeToString())
            assertNull(eof.value)
        }

    @Test
    fun integrityVerificationReadsLargeSourcesInBoundedChunks() =
        runTest {
            val data = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
            val source = TrackingSource(data)

            val result = verifyBlobSourceIntegrity(source, ContentAddress.compute(data))

            assertTrue(result.isOk && result.value)
            assertTrue(source.maxRequested <= DEFAULT_BLOB_STREAM_CHUNK_SIZE)
            assertTrue(source.readCount > 1)
            assertTrue(source.closed)
        }

    @Test
    fun cancellationIsNotMaskedByThrowingClose() =
        runTest {
            val source = CancellingAndThrowingCloseSource()
            var cancellation: CancellationException? = null

            try {
                verifyBlobSourceIntegrity(source, ContentAddress.compute(byteArrayOf(1)))
            } catch (expected: CancellationException) {
                cancellation = expected
            }

            assertEquals("primary cancellation", cancellation?.message)
            assertTrue(source.closeAttempted)
        }
}

private class TrackingSource(
    private val data: ByteArray,
) : BlobByteSource {
    private var offset = 0
    var maxRequested = 0
    var readCount = 0
    var closed = false

    override suspend fun read(maxBytes: Int): IdkResult<ByteArray?, IdkError> {
        maxRequested = maxOf(maxRequested, maxBytes)
        readCount++
        if (offset == data.size) return Ok(null)
        val end = offset + minOf(maxBytes, data.size - offset)
        return Ok(data.copyOfRange(offset, end).also { offset = end })
    }

    override suspend fun close() {
        closed = true
    }
}

private class CancellingAndThrowingCloseSource : BlobByteSource {
    var closeAttempted = false

    override suspend fun read(maxBytes: Int): IdkResult<ByteArray?, IdkError> = throw CancellationException("primary cancellation")

    override suspend fun close() {
        closeAttempted = true
        error("secondary close failure")
    }
}
