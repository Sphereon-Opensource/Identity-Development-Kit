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

package com.sphereon.core.api.http

import com.sphereon.core.compat.JsExportCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlin.jvm.JvmStatic

/**
 * Sealed class representing HTTP request/response body content.
 *
 * This abstraction supports both text-based (JSON) and binary (Protobuf, CBOR) content types
 * without requiring binary dependencies in the OSS core.
 *
 * **Design:**
 * - OSS uses [Text] for JSON payloads (default)
 * - EDK can use [Bytes] for binary formats (Protobuf, CBOR, etc.)
 * - Framework adapters can provide lazy variants ([LazyText], [LazyBytes]) to defer reading
 * - [TextStream] streams text chunks (SSE / chunked responses) without buffering the full body
 *
 * **Usage:**
 * ```kotlin
 * when (val body = request.bodyContent) {
 *     is GenericHttpBody.Text -> processJson(body.value)
 *     is GenericHttpBody.Bytes -> processBinary(body.value)
 *     is GenericHttpBody.Empty -> handleNoBody()
 *     is GenericHttpBody.LazyText -> processJson(body.value) // auto-evaluated
 *     is GenericHttpBody.LazyBytes -> processBinary(body.value) // auto-evaluated
 *     is GenericHttpBody.TextStream -> body.flow.collect { writeChunk(it) }
 * }
 * ```
 */
@JsExportCompat
sealed class GenericHttpBody {
    /**
     * Indicates whether this body contains any content.
     */
    abstract val isEmpty: Boolean

    /**
     * Returns the content as a String (for text) or Base64-encoded string (for bytes).
     * Returns null if body is empty.
     */
    abstract fun asTextOrNull(): String?

    /**
     * Returns the content as a ByteArray.
     * For text content, encodes using UTF-8.
     * Returns null if body is empty.
     */
    abstract fun asBytesOrNull(): ByteArray?

    /**
     * Represents no body content.
     */
    data object Empty : GenericHttpBody() {
        override val isEmpty: Boolean = true

        override fun asTextOrNull(): String? = null

        override fun asBytesOrNull(): ByteArray? = null
    }

    /**
     * Text-based body content (JSON, XML, form data, etc.).
     *
     * @property value The text content
     * @property charset The character encoding (defaults to UTF-8)
     */
    data class Text(
        val value: String,
        val charset: String = "utf-8",
    ) : GenericHttpBody() {
        override val isEmpty: Boolean = value.isEmpty()

        override fun asTextOrNull(): String = value

        override fun asBytesOrNull(): ByteArray = value.encodeToByteArray()
    }

    /**
     * Binary body content (Protobuf, CBOR, raw bytes, etc.).
     *
     * @property value The binary content
     */
    data class Bytes(
        val value: ByteArray,
    ) : GenericHttpBody() {
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
     * Lazily-evaluated text body.
     *
     * The supplier is only invoked when [value] is first accessed.
     * This is useful for frameworks that support lazy body reading (e.g., Spring).
     *
     * @property supplier The lazy supplier that produces the text content
     * @property charset The character encoding (defaults to UTF-8)
     */
    class LazyText(
        private val supplier: () -> String?,
        val charset: String = "utf-8",
    ) : GenericHttpBody() {
        /**
         * The lazily-evaluated text value.
         */
        val value: String? by lazy { supplier() }

        override val isEmpty: Boolean get() = value.isNullOrEmpty()

        override fun asTextOrNull(): String? = value

        override fun asBytesOrNull(): ByteArray? = value?.encodeToByteArray()
    }

    /**
     * Lazily-evaluated binary body.
     *
     * The supplier is only invoked when [value] is first accessed.
     *
     * @property supplier The lazy supplier that produces the binary content
     */
    class LazyBytes(
        private val supplier: () -> ByteArray?,
    ) : GenericHttpBody() {
        /**
         * The lazily-evaluated binary value.
         */
        val value: ByteArray? by lazy { supplier() }

        override val isEmpty: Boolean get() = value?.isEmpty() ?: true

        override fun asTextOrNull(): String? = value?.decodeToString()

        override fun asBytesOrNull(): ByteArray? = value
    }

    /**
     * Streaming text body backed by a [Flow] of UTF-8 text chunks.
     *
     * Used for Server-Sent Events and other chunked responses where the full body
     * must not be buffered before the first byte is written. Synchronous accessors
     * return null — collect [flow] (or [collectToText]) instead.
     *
     * @property flow Chunks as they become available (each chunk is typically one SSE frame)
     * @property charset Character encoding advertised to adapters (defaults to UTF-8)
     */
    class TextStream(
        val flow: Flow<String>,
        val charset: String = "utf-8",
    ) : GenericHttpBody() {
        override val isEmpty: Boolean = false

        override fun asTextOrNull(): String? = null

        override fun asBytesOrNull(): ByteArray? = null

        /** Collects every chunk into one string. Prefer incremental [flow] collection for large streams. */
        suspend fun collectToText(): String = flow.toList().joinToString(separator = "")
    }

    companion object {
        /**
         * Creates a [GenericHttpBody] from a nullable String.
         * Returns [Empty] if the string is null or blank.
         */
        @JvmStatic
        fun ofText(value: String?): GenericHttpBody =
            when {
                value.isNullOrBlank() -> Empty
                else -> Text(value)
            }

        /**
         * Creates a [GenericHttpBody] from a nullable ByteArray.
         * Returns [Empty] if the array is null or empty.
         */
        @JvmStatic
        fun ofBytes(value: ByteArray?): GenericHttpBody =
            when {
                value == null || value.isEmpty() -> Empty
                else -> Bytes(value)
            }

        /**
         * Creates a lazily-evaluated text body.
         */
        @JvmStatic
        fun ofLazyText(
            supplier: () -> String?,
            charset: String = "utf-8",
        ): GenericHttpBody = LazyText(supplier, charset)

        /**
         * Creates a lazily-evaluated binary body.
         */
        @JvmStatic
        fun ofLazyBytes(supplier: () -> ByteArray?): GenericHttpBody = LazyBytes(supplier)

        /**
         * Creates a streaming text body from a flow of chunks.
         */
        @JvmStatic
        fun ofTextStream(
            flow: Flow<String>,
            charset: String = "utf-8",
        ): GenericHttpBody = TextStream(flow, charset)
    }
}
