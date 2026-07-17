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

package com.sphereon.data.store.blob

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.getDigest
import com.sphereon.data.store.blob.cas.ContentAddress
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** Half-open byte range `[startInclusive, endExclusive)` for bounded reads. */
@Serializable
data class BlobReadRange(
    val startInclusive: Long = 0,
    val endExclusive: Long? = null,
) {
    init {
        require(startInclusive >= 0) { "startInclusive must be non-negative" }
        require(endExclusive == null || endExclusive > startInclusive) { "endExclusive must be greater than startInclusive" }
    }

    val length: Long?
        get() = endExclusive?.minus(startInclusive)

    companion object {
        val ALL = BlobReadRange()
    }
}

/**
 * Platform-neutral, pull-based byte source for streaming blob content in common Kotlin code.
 *
 * A source is consumed sequentially. [read] returns `null` at end-of-stream and must never return
 * an empty array before end-of-stream. Implementations may own native resources; callers must
 * invoke [close] when they stop consuming the source.
 */
interface BlobByteSource {
    suspend fun read(maxBytes: Int = DEFAULT_BLOB_STREAM_CHUNK_SIZE): IdkResult<ByteArray?, IdkError>

    suspend fun close() {}
}

/** A descriptor paired with a lazily consumed blob body. */
data class BlobReadStream(
    val descriptor: BlobDescriptor,
    val source: BlobByteSource,
)

/** Common-code source used by clients, tests, and adapters that already have a byte array. */
class ByteArrayBlobSource(
    data: ByteArray,
    range: BlobReadRange = BlobReadRange.ALL,
) : BlobByteSource {
    private val content = data.copyOf()
    private var offset = range.startInclusive.coerceAtMost(content.size.toLong()).toInt()
    private val limit = (range.endExclusive ?: content.size.toLong()).coerceAtMost(content.size.toLong()).toInt()

    override suspend fun read(maxBytes: Int): IdkResult<ByteArray?, IdkError> {
        require(maxBytes > 0) { "maxBytes must be greater than zero" }
        if (offset >= limit) {
            return Ok(null)
        }
        val end = offset + minOf(maxBytes, limit - offset)
        val chunk = content.copyOfRange(offset, end)
        offset = end
        return Ok(chunk)
    }
}

const val DEFAULT_BLOB_STREAM_CHUNK_SIZE: Int = 64 * 1024

/** Verifies a source incrementally and always closes it, including on cancellation. */
suspend fun verifyBlobSourceIntegrity(
    source: BlobByteSource,
    expected: ContentAddress,
): IdkResult<Boolean, IdkError> {
    var outcome: IdkResult<Boolean, IdkError>? = null
    var primaryFailure: Throwable? = null
    var closeFailure: Throwable? = null
    try {
        val digest = getDigest(expected.algorithm)
        while (outcome == null) {
            val read = source.read(DEFAULT_BLOB_STREAM_CHUNK_SIZE)
            if (read.isErr) {
                outcome = Err(read.error)
            } else {
                val chunk = read.value
                when {
                    chunk == null -> outcome = Ok(digest.digest().contentEquals(expected.digest))
                    chunk.isEmpty() -> outcome = Err(BlobStoreError.BackendError("Blob stream returned an empty chunk before EOF").toIdkError())
                    else -> digest.update(chunk)
                }
            }
        }
    } catch (failure: Throwable) {
        primaryFailure = failure
    } finally {
        try {
            withContext(NonCancellable) { source.close() }
        } catch (failure: Throwable) {
            closeFailure = failure
        }
    }
    primaryFailure?.let { throw it }
    if (outcome?.isErr == true) return outcome!!
    closeFailure?.let { failure ->
        return Err(BlobStoreError.BackendError("Failed to close blob source: ${failure.message}", failure).toIdkError())
    }
    return requireNotNull(outcome)
}
