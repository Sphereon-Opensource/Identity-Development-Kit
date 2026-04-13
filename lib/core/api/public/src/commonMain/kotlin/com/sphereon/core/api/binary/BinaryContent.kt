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

/**
 * Marker type for command outputs that should be returned as raw binary bytes,
 * bypassing codec serialization in [BinaryCommandAdapter].
 *
 * When a [TypedServiceCommandAdapter] returns [BinaryContent] as its output,
 * the transport layer short-circuits JSON/Protobuf/CBOR encoding and returns
 * the raw bytes directly with the specified content type.
 *
 * This eliminates the 33% bandwidth overhead of base64-encoding binary data
 * inside a JSON envelope.
 *
 * @property data The raw binary content
 * @property contentType MIME type for the response (default: application/octet-stream)
 * @property headers Additional response headers (e.g., Content-Disposition, Content-Encoding)
 */
data class BinaryContent(
    val data: ByteArray,
    val contentType: String = "application/octet-stream",
    val headers: Map<String, String> = emptyMap(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is BinaryContent) {
            return false
        }
        return data.contentEquals(other.data) && contentType == other.contentType && headers == other.headers
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + headers.hashCode()
        return result
    }
}
