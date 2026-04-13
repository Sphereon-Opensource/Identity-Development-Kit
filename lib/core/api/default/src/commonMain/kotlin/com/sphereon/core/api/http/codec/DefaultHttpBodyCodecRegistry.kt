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

package com.sphereon.core.api.http.codec

import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.di.HasOrder
import com.sphereon.di.Order
import com.sphereon.di.sortedByOrderAscending
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default implementation of [HttpBodyCodecRegistry].
 *
 * This registry:
 * - Aggregates all contributed [HttpBodyCodec] implementations via multibinding
 * - Selects codecs by media type matching, preferring higher-priority codecs (lower [HasOrder.getOrder] value)
 * - Falls back to the default JSON codec when no specific codec matches
 *
 * **EDK Replacement:**
 * EDK can replace this registry by providing a higher-priority implementation
 * (e.g., one that supports Protobuf/CBOR codecs or has additional features).
 *
 * **Usage:**
 * ```kotlin
 * @Inject
 * class MyAdapter(
 *     private val codecRegistry: HttpBodyCodecRegistry
 * ) {
 *     suspend fun handleRequest(request: GenericHttpRequest): GenericHttpResponse {
 *         val mediaType = MediaType.parse(request.contentType)
 *         val jsonElement = codecRegistry.decode<JsonElement>(request.bodyContent, mediaType)
 *         // ...
 *     }
 * }
 * ```
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<HttpBodyCodecRegistry>())
class DefaultHttpBodyCodecRegistry(
    allCodecs: Set<HttpBodyCodec>,
) : HttpBodyCodecRegistry,
    HasOrder {
    /**
     * All registered codecs, sorted by priority (highest priority first).
     */
    override val codecs: Set<HttpBodyCodec> =
        allCodecs
            .sortedByOrderAscending()
            .toSet()

    /**
     * The default codec (JSON) used when no specific codec matches.
     *
     * Selects the highest-priority codec that supports [MediaType.ApplicationJson].
     * If no JSON codec is found (unusual), creates a fallback instance.
     */
    override val defaultCodec: HttpBodyCodec by lazy {
        codecs
            .sortedByOrderAscending()
            .firstOrNull { it.supports(MediaType.ApplicationJson) }
            ?: JsonHttpBodyCodec()
    }

    override fun getOrder(): Int = Order.MEDIUM.orderValue

    /**
     * Find the highest-priority codec that supports the given media type.
     *
     * @param mediaType The media type to match, or null to use the default codec
     * @return The matching codec, or [defaultCodec] if no specific match
     */
    override fun codecFor(mediaType: MediaType?): HttpBodyCodec {
        if (mediaType == null) {
            return defaultCodec
        }

        // Find all codecs that support this media type
        val matchingCodecs =
            codecs
                .sortedByOrderAscending()
                .filter { it.supports(mediaType) }

        // Return highest priority match, or default
        return matchingCodecs.firstOrNull() ?: defaultCodec
    }
}
