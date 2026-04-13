/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.core.api.binary

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.flow

private const val DEFAULT_BUFFER_SIZE = 8192

/**
 * Creates a [StreamingBody] from a Ktor [ByteReadChannel].
 *
 * The channel is converted to a [StreamingBody.ByteStream] backed by a flow
 * that reads chunks from the channel.
 *
 * @param channel Must be a Ktor [ByteReadChannel]
 * @param contentLength Optional known content length
 * @return A StreamingBody.ByteStream wrapping the channel
 * @throws IllegalArgumentException if channel is not a ByteReadChannel
 */
actual fun StreamingBody.Companion.ofChannel(
    channel: Any,
    contentLength: Long?,
): StreamingBody {
    require(channel is ByteReadChannel) {
        "Expected io.ktor.utils.io.ByteReadChannel, got ${channel::class.simpleName}"
    }

    val byteFlow =
        flow {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (!channel.isClosedForRead) {
                val bytesRead = channel.readAvailable(buffer)
                if (bytesRead > 0) {
                    emit(buffer.copyOf(bytesRead))
                } else if (bytesRead == -1) {
                    break
                }
            }
        }

    return StreamingBody.ByteStream(byteFlow, contentLength)
}

/**
 * JVM supports channel-based streaming via Ktor's ByteReadChannel.
 */
actual fun StreamingBody.Companion.supportsChannels(): Boolean = true

/**
 * Creates a [StreamingBody.ByteStream] from a Ktor [ByteReadChannel] with full type safety.
 *
 * This is the preferred JVM-specific API for creating streaming bodies from Ktor responses.
 * Unlike [ofChannel], this method provides compile-time type checking.
 *
 * **Usage:**
 * ```kotlin
 * val response = httpClient.get("https://example.com/large-file")
 * val body = StreamingBody.ofByteReadChannel(response.bodyAsChannel())
 * ```
 *
 * @param channel The Ktor ByteReadChannel to read from
 * @param contentLength Optional known content length for progress tracking
 * @return A StreamingBody.ByteStream backed by a flow reading from the channel
 */
fun StreamingBody.Companion.ofByteReadChannel(
    channel: ByteReadChannel,
    contentLength: Long? = null,
): StreamingBody.ByteStream {
    val byteFlow =
        flow {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (!channel.isClosedForRead) {
                val bytesRead = channel.readAvailable(buffer)
                if (bytesRead > 0) {
                    emit(buffer.copyOf(bytesRead))
                } else if (bytesRead == -1) {
                    break
                }
            }
        }
    return StreamingBody.ByteStream(byteFlow, contentLength)
}
