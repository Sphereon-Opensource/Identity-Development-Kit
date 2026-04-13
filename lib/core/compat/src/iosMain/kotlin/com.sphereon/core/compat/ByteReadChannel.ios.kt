package com.sphereon.core.compat

import io.ktor.utils.io.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val CHUNK_SIZE = 5 * 1024 * 1024 // 5MB chunks

@OptIn(ExperimentalForeignApi::class)
actual suspend fun openFileChannel(path: String): ByteReadChannel = suspendCancellableCoroutine { continuation ->
    try {
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(path)) {
            continuation.resumeWithException(Exception("File not found at path: $path"))
            return@suspendCancellableCoroutine
        }

        val attributes = fileManager.attributesOfItemAtPath(path, null)
        val fileSize = (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L

        if (fileSize > CHUNK_SIZE) {
            readFileInChunks(path, continuation)
        } else {
            // For small files, read directly
            val data = NSData.dataWithContentsOfFile(path)
                ?: throw Exception("Could not read file at path: $path")

            val byteArray = ByteArray(data.length.toInt())
            data.getBytes(byteArray.pin().addressOf(0), data.length.toULong())

            val channel = ByteChannel()
            GlobalScope.launch {
                channel.writeByteArray(byteArray)
                channel.close()
            }
            continuation.resume(channel)
        }
    } catch (e: Throwable) {
        continuation.resumeWithException(e)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun readFileInChunks(path: String, continuation: kotlinx.coroutines.CancellableContinuation<ByteReadChannel>) {
    val channel = ByteChannel()
    val dataChannel = Channel<ByteArray>(Channel.UNLIMITED)

    // Launch coroutine to handle writing to channel
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

    val inputStream = NSInputStream.inputStreamWithFileAtPath(path)

    if (inputStream == null) {
        continuation.resumeWithException(Exception("Could not create input stream for file: $path"))
        return
    }

    inputStream.open()

    continuation.invokeOnCancellation {
        inputStream.close()
        dataChannel.close()
    }

    fun readNextChunk() {
        val buffer = UByteArray(CHUNK_SIZE)
        memScoped {
            val bytesRead = inputStream.read(buffer.pin().addressOf(0), CHUNK_SIZE.toULong())

            when {
                bytesRead > 0 -> {
                    val chunk = buffer.copyOf(bytesRead.toInt()).asByteArray()
                    dataChannel.trySend(chunk)
                    readNextChunk()
                }
                bytesRead == 0L -> {
                    inputStream.close()
                    dataChannel.close()
                }
                else -> {
                    inputStream.close()
                    dataChannel.close()
                }
            }
        }
    }

    readNextChunk()
    continuation.resume(channel)
}
