package com.sphereon.core.compat

import io.ktor.utils.io.*
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.posix.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val CHUNK_SIZE = 5 * 1024 * 1024 // 5MB chunks

@OptIn(ExperimentalForeignApi::class)
actual suspend fun openFileChannel(path: String): ByteReadChannel = suspendCancellableCoroutine { continuation ->
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
    } catch (e: Throwable) {
        continuation.resumeWithException(e)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun readFileInChunks(file: CPointer<FILE>, continuation: CancellableContinuation<ByteReadChannel>) {
    val channel = ByteChannel()
    val dataChannel = Channel<ByteArray>(Channel.UNLIMITED)

    GlobalScope.launch {
        try {
            for (chunk in dataChannel) {
                channel.writeByteArray(chunk)
            }
            channel.close()
        } catch (e: Exception) {
            channel.close(e)
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
