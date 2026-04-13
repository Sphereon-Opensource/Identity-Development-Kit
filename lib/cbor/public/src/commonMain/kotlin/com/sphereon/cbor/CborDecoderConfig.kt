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
 *
 */

package com.sphereon.cbor

import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic

/**
 * Configuration for the CBOR decoder with security limits.
 *
 * These limits protect against denial-of-service attacks that exploit:
 * - Deep nesting (stack overflow via recursive decoding)
 * - Large item counts (memory exhaustion via indefinite-length collections)
 * - Large strings (memory exhaustion via huge byte/text strings)
 *
 * @property maxDepth Maximum nesting depth for arrays, maps, and tagged items.
 *                    Default: 64 (sufficient for most legitimate use cases)
 * @property maxItems Maximum total number of items that can be decoded.
 *                    Default: 1,000,000 (prevents memory exhaustion)
 * @property maxStringLength Maximum length for byte strings and text strings.
 *                           Default: 10,000,000 bytes (~10MB)
 */
@JsExportCompat
data class CborDecoderConfig(
    val maxDepth: Int = DEFAULT_MAX_DEPTH,
    val maxItems: Int = DEFAULT_MAX_ITEMS,
    val maxStringLength: Int = DEFAULT_MAX_STRING_LENGTH,
) {
    init {
        require(maxDepth > 0) { "maxDepth must be positive" }
        require(maxItems > 0) { "maxItems must be positive" }
        require(maxStringLength > 0) { "maxStringLength must be positive" }
    }

    companion object {
        /**
         * Default maximum nesting depth.
         * 64 levels is sufficient for virtually all legitimate use cases while
         * preventing stack overflow from maliciously nested structures.
         */
        const val DEFAULT_MAX_DEPTH = 64

        /**
         * Default maximum item count.
         * 1 million items provides ample room for legitimate data while
         * preventing memory exhaustion attacks.
         */
        const val DEFAULT_MAX_ITEMS = 1_000_000

        /**
         * Default maximum string length (10MB).
         * Large enough for most legitimate uses (embedded images, documents)
         * while preventing memory exhaustion from single huge strings.
         */
        const val DEFAULT_MAX_STRING_LENGTH = 10_000_000

        /**
         * Default configuration with standard security limits.
         */
        @JsStatic
        val DEFAULT = CborDecoderConfig()

        /**
         * Permissive configuration for trusted input.
         * Use only when you control the input source and need to process
         * very large or deeply nested structures.
         */
        @JsStatic
        val PERMISSIVE =
            CborDecoderConfig(
                maxDepth = Int.MAX_VALUE,
                maxItems = Int.MAX_VALUE,
                maxStringLength = Int.MAX_VALUE,
            )

        /**
         * Strict configuration for untrusted input.
         * Lower limits for processing potentially malicious data.
         */
        @JsStatic
        val STRICT =
            CborDecoderConfig(
                maxDepth = 32,
                maxItems = 100_000,
                maxStringLength = 1_000_000,
            )
    }
}

/**
 * Internal state tracker for CBOR decoding operations.
 *
 * This class tracks the current decoding state to enforce security limits
 * and detect potential attacks during the decode process.
 */
internal class CborDecodeState(
    val config: CborDecoderConfig,
) {
    /** Current nesting depth (arrays, maps, tagged items) */
    var currentDepth: Int = 0
        private set

    /** Total number of items decoded so far */
    var itemCount: Int = 0
        private set

    /**
     * Enter a container (array, map, or tagged item).
     * Increments depth and checks against the configured limit.
     *
     * @return true if the depth is within limits, false if exceeded
     */
    fun enterContainer(): Boolean {
        currentDepth++
        return currentDepth <= config.maxDepth
    }

    /**
     * Exit a container.
     * Decrements depth.
     */
    fun exitContainer() {
        currentDepth--
    }

    /**
     * Increment the item count.
     *
     * @return true if the count is within limits, false if exceeded
     */
    fun incrementItemCount(): Boolean {
        itemCount++
        return itemCount <= config.maxItems
    }

    /**
     * Check if a string length is within limits.
     *
     * @param length The string length to check
     * @return true if within limits, false if exceeded
     */
    fun checkStringLength(length: Int): Boolean = length <= config.maxStringLength

    /**
     * Reset the state for reuse.
     */
    fun reset() {
        currentDepth = 0
        itemCount = 0
    }
}
