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

package com.sphereon.core.api.codec

import com.sphereon.core.compat.JsExportCompat

/**
 * Determines codec selection based on Content-Type and Accept headers.
 *
 * This interface provides consistent media type negotiation across
 * HTTP and gRPC transports.
 *
 * **HTTP Negotiation:**
 * - Request codec determined by Content-Type header
 * - Response codec determined by Accept header (client preference)
 *
 * **gRPC Negotiation:**
 * - Uses gRPC-specific content types (application/grpc+proto, application/grpc+json)
 *
 * **Usage:**
 * ```kotlin
 * val negotiator = DefaultMediaTypeNegotiator(codecRegistry)
 *
 * // Select codec for request body
 * val requestCodec = negotiator.selectRequestCodec("application/json")
 *
 * // Select codec for response based on client Accept header
 * val responseCodec = negotiator.selectResponseCodec(
 *     accept = listOf("application/protobuf", "application/json"),
 *     supported = setOf("application/json", "application/protobuf")
 * )
 * ```
 */
@JsExportCompat
interface MediaTypeNegotiator {
    /**
     * Default codec when negotiation fails or no preference given.
     */
    val defaultCodec: StreamingCodec

    /**
     * Select codec for decoding request body.
     *
     * @param contentType The Content-Type header value (may include charset)
     * @return The codec for the content type, or default if not matched
     */
    fun selectRequestCodec(contentType: String?): StreamingCodec

    /**
     * Select codec for encoding response body.
     *
     * @param accept Client's Accept header values (ordered by preference)
     * @param supported Server's supported content types
     * @return The negotiated codec
     */
    fun selectResponseCodec(
        accept: List<String>,
        supported: Set<String>,
    ): StreamingCodec
}

/**
 * Default implementation of MediaTypeNegotiator.
 *
 * @property codecRegistry Registry of available codecs
 */
@JsExportCompat
class DefaultMediaTypeNegotiator(
    private val codecRegistry: StreamingCodecRegistry,
) : MediaTypeNegotiator {
    override val defaultCodec: StreamingCodec
        get() = codecRegistry.defaultCodec

    override fun selectRequestCodec(contentType: String?): StreamingCodec {
        if (contentType == null) {
            return defaultCodec
        }

        // Normalize content-type (strip parameters like charset)
        val normalized = contentType.substringBefore(';').trim().lowercase()

        // Handle gRPC content types
        val httpContentType = GrpcMediaTypes.toHttpContentType(normalized) ?: normalized

        return codecRegistry.codecFor(httpContentType) ?: defaultCodec
    }

    override fun selectResponseCodec(
        accept: List<String>,
        supported: Set<String>,
    ): StreamingCodec {
        if (accept.isEmpty()) {
            return defaultCodec
        }

        // Parse accept header with quality values
        val preferences =
            accept
                .flatMap { parseAcceptHeader(it) }
                .sortedByDescending { it.quality }

        // Find first acceptable type that we support
        for (preference in preferences) {
            val normalized = preference.mediaType.lowercase()

            // Handle wildcard
            if (normalized == "*/*") {
                return defaultCodec
            }

            // Handle type wildcard (e.g., "application/*")
            if (normalized.endsWith("/*")) {
                val typePrefix = normalized.removeSuffix("/*")
                val matching = supported.find { it.lowercase().startsWith(typePrefix) }
                if (matching != null) {
                    codecRegistry.codecFor(matching)?.let { return it }
                }
                continue
            }

            // Exact match
            if (normalized in supported.map { it.lowercase() }) {
                codecRegistry.codecFor(normalized)?.let { return it }
            }
        }

        return defaultCodec
    }

    /**
     * Parses Accept header value into media type preferences.
     */
    private fun parseAcceptHeader(accept: String): List<MediaTypePreference> {
        return accept.split(',').mapNotNull { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) {
                return@mapNotNull null
            }

            val mediaType = trimmed.substringBefore(';').trim()
            val quality =
                trimmed
                    .substringAfter("q=", "1.0")
                    .substringBefore(',')
                    .substringBefore(';')
                    .toDoubleOrNull() ?: 1.0

            MediaTypePreference(mediaType, quality)
        }
    }

    private data class MediaTypePreference(
        val mediaType: String,
        val quality: Double,
    )
}

/**
 * gRPC-specific media types.
 *
 * gRPC uses different content-types that map to standard formats:
 * - application/grpc+proto -> application/protobuf
 * - application/grpc+json -> application/json
 */
object GrpcMediaTypes {
    const val PROTO = "application/grpc+proto"
    const val JSON = "application/grpc+json"

    /**
     * Converts gRPC content type to HTTP content type.
     *
     * @return The HTTP content type, or null if not a gRPC type
     */
    fun toHttpContentType(grpcType: String): String? =
        when (grpcType.lowercase()) {
            PROTO, "application/grpc" -> ContentTypes.PROTOBUF
            JSON -> ContentTypes.JSON
            else -> null
        }

    /**
     * Converts HTTP content type to gRPC content type.
     *
     * @return The gRPC content type, or null if not mappable
     */
    fun fromHttpContentType(httpType: String): String? =
        when (httpType.lowercase()) {
            ContentTypes.PROTOBUF -> PROTO
            ContentTypes.JSON -> JSON
            else -> null
        }

    /**
     * Checks if the content type is a gRPC type.
     */
    fun isGrpcType(contentType: String): Boolean {
        val normalized = contentType.lowercase()
        return normalized.startsWith("application/grpc")
    }
}
