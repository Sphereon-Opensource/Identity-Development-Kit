/*
 * © 2025 Sphereon International B.V.
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
 *
 */

package com.sphereon.core.compat

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@JsModule("node:fs")
external val nodeFs: dynamic

private const val CHUNK_SIZE = 5 * 1024 * 1024 // When files are larger than 5MB, read them in chunks to avoid memory issues

/**
 * JavaScript implementation for opening a file as a ByteReadChannel
 */
actual suspend fun openFileChannel(path: String): ByteReadChannel = suspendCancellableCoroutine { continuation ->
    when {
        // Browser environment - not supported
        js("typeof window !== 'undefined'") -> {
            continuation.resumeWithException(UnsupportedOperationException("File loading is not supported in browser environment"))
        }

        // Node.js environment - using streams
        else -> {
            try {
                val fs = nodeFs

                val stats = fs.statSync(path)
                val fileSize = stats.size.unsafeCast<Double>().toLong()
                if (fileSize > CHUNK_SIZE) {
                    readFileInChunks(fs, path, continuation)
                } else {
                    // For files smaller than one chunk, read normally
                    val buffer = fs.readFileSync(path)
                    val byteArray = buffer.unsafeCast<ByteArray>()

                    val channel = ByteChannel()
                    writeToChannel(channel, byteArray)
                    closeChannel(channel, null)
                    continuation.resume(channel)
                }
            } catch (e: Throwable) {
                continuation.resumeWithException(e)
            }
        }
    }
}

/**
 * Helper function to read large files in chunks
 */
private fun readFileInChunks(fs: dynamic, path: String, continuation: kotlinx.coroutines.CancellableContinuation<ByteReadChannel>) {
    val channel = ByteChannel()
    val options = js("{ highWaterMark: $CHUNK_SIZE }")
    val readStream = fs.createReadStream(path, options)

    // cancel -> destroy underlying stream
    continuation.invokeOnCancellation {
        readStream.destroy()
    }

    readStream.on("data") { chunk ->
        val byteArray = chunk.unsafeCast<ByteArray>()
        writeToChannel(channel, byteArray)
    }

    readStream.on("end") {
        closeChannel(channel, null)
    }

    readStream.on("error") { error ->
        val errorMessage = error.toString()
        val exception = Exception("Error reading file: $errorMessage")
        closeChannel(channel, exception)
    }

    continuation.resume(channel)
}

/**
 * Helper function to write to channel in JavaScript
 */
@JsName("writeToChannel")
private fun writeToChannel(channel: ByteChannel, byteArray: ByteArray) {
    js("channel.writeByteArray(byteArray)")
}

/**
 * Helper function to close the channel in JavaScript
 */
@JsName("closeChannel")
private fun closeChannel(channel: ByteChannel, error: Throwable?) {
    if (error != null) {
        js("channel.close(error)")
    } else {
        js("channel.close()")
    }
}