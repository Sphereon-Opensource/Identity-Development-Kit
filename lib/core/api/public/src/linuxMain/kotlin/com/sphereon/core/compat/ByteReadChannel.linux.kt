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

package com.sphereon.core.compat

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeByteArray
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.posix.FILE
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.ferror
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val CHUNK_SIZE = 5 * 1024 * 1024 // 5MB chunks

@OptIn(ExperimentalForeignApi::class)
actual suspend fun openFileChannel(path: String): ByteReadChannel =
    suspendCancellableCoroutine { continuation ->
        try {
            val file = fopen(path, "rb")
            if (file == null) {
                continuation.resumeWithException(Exception("Could not open file at path: $path"))
                return@suspendCancellableCoroutine
            }

            // Get file size
            fseek(file, 0, SEEK_END)
            val fileSize = ftell(file).toLong()
            fseek(file, 0, SEEK_SET)

            if (fileSize > CHUNK_SIZE) {
                readFileInChunks(file, continuation)
            } else {
                // For small files, read directly
                val buffer = ByteArray(fileSize.toInt())
                val bytesRead = fread(buffer.refTo(0), 1u, fileSize.toULong(), file)
                fclose(file)

                if (bytesRead != fileSize.toULong()) {
                    continuation.resumeWithException(Exception("Could not read entire file"))
                    return@suspendCancellableCoroutine
                }

                val channel = ByteChannel()
                GlobalScope.launch {
                    channel.writeByteArray(buffer)
                    channel.close()
                }
                continuation.resume(channel)
            }
        } catch (expected: Throwable) {
            continuation.resumeWithException(expected)
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun readFileInChunks(
    file: CPointer<FILE>,
    continuation: CancellableContinuation<ByteReadChannel>,
) {
    val channel = ByteChannel()
    val dataChannel = Channel<ByteArray>(Channel.UNLIMITED)

    GlobalScope.launch {
        try {
            for (chunk in dataChannel) {
                channel.writeByteArray(chunk)
            }
            channel.close()
        } catch (expected: Exception) {
            channel.close(expected)
        }
    }

    continuation.invokeOnCancellation {
        fclose(file)
        dataChannel.close()
    }

    fun readNextChunk() {
        val buffer = ByteArray(CHUNK_SIZE)
        val bytesRead = fread(buffer.refTo(0), 1u, CHUNK_SIZE.toULong(), file)

        when {
            bytesRead > 0u -> {
                val chunk = buffer.copyOf(bytesRead.toInt())
                dataChannel.trySend(chunk)
                readNextChunk()
            }

            else -> {
                // End of file or error
                fclose(file)
                if (ferror(file) != 0) {
                    dataChannel.close()
                } else {
                    dataChannel.close()
                }
            }
        }
    }

    readNextChunk()
    continuation.resume(channel)
}
