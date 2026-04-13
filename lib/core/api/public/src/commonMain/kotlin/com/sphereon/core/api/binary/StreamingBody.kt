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

import com.sphereon.core.api.http.GenericHttpBody
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

/**
 * Transport-neutral body abstraction for streaming and binary content.
 *
 * This sealed class provides a unified way to represent request/response bodies
 * across different transports (HTTP, gRPC) and content types (JSON, Protobuf, CBOR).
 *
 * **Design:**
 * - [Empty] - No body content
 * - [Text] - Text-based content (JSON, XML, etc.)
 * - [Bytes] - Binary content (Protobuf, CBOR, raw bytes)
 * - [LazyBytes] - Lazily-evaluated binary content for deferred reading
 *
 * **Platform Support:**
 * - JVM: Full support including streaming channels
 * - Native: Full support for bytes-based operations
 * - JS: Limited - no ByteReadChannel support (falls back to Bytes)
 *
 * **Relationship to GenericHttpBody:**
 * StreamingBody is a transport-neutral superset of GenericHttpBody.
 * Use [toGenericHttpBody] and [fromGenericHttpBody] for conversion.
 */
sealed class StreamingBody {
    /**
     * Indicates whether this body contains any content.
     */
    abstract val isEmpty: Boolean

    /**
     * Returns the content as text if possible.
     * For binary content, attempts UTF-8 decoding.
     */
    abstract fun asTextOrNull(): String?

    /**
     * Returns the content as a ByteArray.
     * For text content, encodes using UTF-8.
     */
    abstract fun asBytesOrNull(): ByteArray?

    /**
     * Represents no body content.
     */
    data object Empty : StreamingBody() {
        override val isEmpty: Boolean = true

        override fun asTextOrNull(): String? = null

        override fun asBytesOrNull(): ByteArray? = null
    }

    /**
     * Text-based body content.
     *
     * @property value The text content
     * @property charset The character encoding (defaults to UTF-8)
     */
    data class Text(
        val value: String,
        val charset: String = "UTF-8",
    ) : StreamingBody() {
        override val isEmpty: Boolean = value.isEmpty()

        override fun asTextOrNull(): String = value

        override fun asBytesOrNull(): ByteArray = value.encodeToByteArray()
    }

    /**
     * Binary body content.
     *
     * @property value The binary content
     */
    data class Bytes(
        val value: ByteArray,
    ) : StreamingBody() {
        override val isEmpty: Boolean = value.isEmpty()

        override fun asTextOrNull(): String = value.decodeToString()

        override fun asBytesOrNull(): ByteArray = value

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is Bytes) {
                return false
            }
            return value.contentEquals(other.value)
        }

        override fun hashCode(): Int = value.contentHashCode()
    }

    /**
     * Lazily-evaluated binary body.
     *
     * The supplier is only invoked when [value] is first accessed.
     * This is useful for deferred body reading, reducing memory usage
     * when the body is never accessed (e.g., for GET requests).
     *
     * @property supplier The lazy supplier that produces the binary content
     */
    class LazyBytes(
        private val supplier: () -> ByteArray?,
    ) : StreamingBody() {
        /**
         * The lazily-evaluated binary value.
         */
        val value: ByteArray? by lazy { supplier() }

        override val isEmpty: Boolean get() = value?.isEmpty() ?: true

        override fun asTextOrNull(): String? = value?.decodeToString()

        override fun asBytesOrNull(): ByteArray? = value
    }

    /**
     * Streaming binary body backed by a [Flow] of byte chunks.
     *
     * This variant supports streaming large payloads without loading
     * the entire content into memory at once. The flow can be collected
     * incrementally or accumulated into a complete byte array.
     *
     * **Platform Support:**
     * - JVM: Full support, can be converted to/from ByteReadChannel
     * - Native: Full support for flow-based operations
     * - JS: Full support for flow-based operations
     *
     * **Usage:**
     * ```kotlin
     * // Create from a flow
     * val body = StreamingBody.ByteStream(myFlow)
     *
     * // Collect all bytes
     * val bytes = body.collect()
     *
     * // Collect as text
     * val text = body.collectToText()
     * ```
     *
     * @property flow The flow of byte array chunks
     * @property contentLength Optional known content length (for progress tracking)
     */
    class ByteStream(
        val flow: Flow<ByteArray>,
        val contentLength: Long? = null,
    ) : StreamingBody() {
        override val isEmpty: Boolean = false

        /**
         * Returns null - streaming body must be collected asynchronously.
         * Use [collect] or [collectToText] instead.
         */
        override fun asTextOrNull(): String? = null

        /**
         * Returns null - streaming body must be collected asynchronously.
         * Use [collect] instead.
         */
        override fun asBytesOrNull(): ByteArray? = null

        /**
         * Collects all chunks from the flow into a single byte array.
         *
         * **Warning:** This loads the entire stream into memory. For large
         * streams, consider processing chunks incrementally via [flow].
         *
         * @return The complete byte array content
         */
        suspend fun collect(): ByteArray {
            val chunks = flow.toList()
            if (chunks.isEmpty()) {
                return ByteArray(0)
            }
            if (chunks.size == 1) {
                return chunks[0]
            }

            val buffer = Buffer()
            for (chunk in chunks) {
                buffer.write(chunk)
            }
            return buffer.readByteArray()
        }

        /**
         * Collects all chunks and decodes to a string.
         *
         * **Warning:** This loads the entire stream into memory. For large
         * streams, consider processing chunks incrementally via [flow].
         *
         * @return The complete text content decoded as UTF-8
         */
        suspend fun collectToText(): String = collect().decodeToString()
    }

    /**
     * Converts this StreamingBody to a GenericHttpBody.
     *
     * **Note:** [ByteStream] bodies cannot be converted synchronously.
     * Use [toGenericHttpBodySuspend] for ByteStream bodies.
     *
     * @throws UnsupportedOperationException if this is a ByteStream
     */
    fun toGenericHttpBody(): GenericHttpBody =
        when (this) {
            is Empty -> {
                GenericHttpBody.Empty
            }

            is Text -> {
                GenericHttpBody.Text(value, charset)
            }

            is Bytes -> {
                GenericHttpBody.Bytes(value)
            }

            is LazyBytes -> {
                val bytes = value
                if (bytes == null || bytes.isEmpty()) {
                    GenericHttpBody.Empty
                } else {
                    GenericHttpBody.Bytes(bytes)
                }
            }

            is ByteStream -> {
                throw UnsupportedOperationException(
                    "ByteStream cannot be converted synchronously. Use toGenericHttpBodySuspend() instead.",
                )
            }
        }

    /**
     * Converts this StreamingBody to a GenericHttpBody, suspending if necessary.
     *
     * This method handles [ByteStream] bodies by collecting the flow.
     */
    suspend fun toGenericHttpBodySuspend(): GenericHttpBody =
        when (this) {
            is Empty -> {
                GenericHttpBody.Empty
            }

            is Text -> {
                GenericHttpBody.Text(value, charset)
            }

            is Bytes -> {
                GenericHttpBody.Bytes(value)
            }

            is LazyBytes -> {
                val bytes = value
                if (bytes == null || bytes.isEmpty()) {
                    GenericHttpBody.Empty
                } else {
                    GenericHttpBody.Bytes(bytes)
                }
            }

            is ByteStream -> {
                val bytes = collect()
                if (bytes.isEmpty()) {
                    GenericHttpBody.Empty
                } else {
                    GenericHttpBody.Bytes(bytes)
                }
            }
        }

    companion object {
        /**
         * Creates a StreamingBody from text content.
         */
        fun ofText(
            value: String?,
            charset: String = "UTF-8",
        ): StreamingBody =
            when {
                value.isNullOrEmpty() -> Empty
                else -> Text(value, charset)
            }

        /**
         * Creates a StreamingBody from binary content.
         */
        fun ofBytes(value: ByteArray?): StreamingBody =
            when {
                value == null || value.isEmpty() -> Empty
                else -> Bytes(value)
            }

        /**
         * Creates a lazily-evaluated binary body.
         */
        fun ofLazyBytes(supplier: () -> ByteArray?): StreamingBody = LazyBytes(supplier)

        /**
         * Creates a streaming body from a flow of byte chunks.
         *
         * @param flow The flow of byte array chunks
         * @param contentLength Optional known content length
         */
        fun ofFlow(
            flow: Flow<ByteArray>,
            contentLength: Long? = null,
        ): StreamingBody = ByteStream(flow, contentLength)

        /**
         * Converts a GenericHttpBody to StreamingBody.
         */
        fun fromGenericHttpBody(body: GenericHttpBody): StreamingBody =
            when (body) {
                is GenericHttpBody.Empty -> {
                    Empty
                }

                is GenericHttpBody.Text -> {
                    Text(body.value, body.charset)
                }

                is GenericHttpBody.Bytes -> {
                    Bytes(body.value)
                }

                is GenericHttpBody.LazyText -> {
                    val text = body.value
                    if (text.isNullOrEmpty()) {
                        Empty
                    } else {
                        Text(text, body.charset)
                    }
                }

                is GenericHttpBody.LazyBytes -> {
                    val bytes = body.value
                    if (bytes == null || bytes.isEmpty()) {
                        Empty
                    } else {
                        Bytes(bytes)
                    }
                }
            }
    }
}
