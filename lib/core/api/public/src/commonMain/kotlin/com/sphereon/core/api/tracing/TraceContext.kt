/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.core.api.tracing

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * W3C Trace Context representation.
 *
 * Holds the identifiers needed for distributed tracing propagation
 * according to the W3C Trace Context specification.
 *
 * @property traceId 32-character hex-encoded trace identifier
 * @property spanId 16-character hex-encoded span identifier
 * @property parentSpanId Optional parent span identifier for nested spans
 * @property traceFlags Trace flags (e.g., 0x01 = sampled)
 */
@JsExportCompat
@Serializable
data class TraceContext
    @JvmOverloads
    constructor(
        val traceId: String,
        val spanId: String,
        val parentSpanId: String? = null,
        val traceFlags: Int = 0,
    ) {
        /**
         * Serializes to W3C traceparent header format.
         * Format: "00-{traceId}-{spanId}-{traceFlags}"
         */
        fun toW3CTraceparent(): String = "00-$traceId-$spanId-${traceFlags.toString(HEX_RADIX).padStart(2, '0')}"

        companion object {
            private const val HEX_RADIX = 16
            private val TRACEPARENT_REGEX = Regex("^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$")

            /**
             * Parses a W3C traceparent header value into a [TraceContext].
             *
             * @param header The traceparent header value
             * @return Parsed [TraceContext] or null if the header is invalid
             */
            @JvmStatic
            fun fromW3CTraceparent(header: String): TraceContext? {
                val match = TRACEPARENT_REGEX.matchEntire(header.trim().lowercase()) ?: return null
                val (_, traceId, spanId, flags) = match.destructured
                // Version "ff" is invalid per spec
                if (match.groupValues[1] == "ff") {
                    return null
                }
                return TraceContext(
                    traceId = traceId,
                    spanId = spanId,
                    traceFlags = flags.toInt(HEX_RADIX),
                )
            }
        }
    }
