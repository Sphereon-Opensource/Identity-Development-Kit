/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.core.api.http.util

/**
 * Framework-agnostic HTTP response utilities.
 *
 * Companion to [RequestUtils] for the response side: safe construction of
 * header values that embed caller- or storage-supplied data.
 */
object ResponseUtils {
    private const val HEX_DIGITS = "0123456789ABCDEF"

    // RFC 5987 `attr-char`: the set that may appear unencoded in a `filename*` value.
    private const val RFC5987_UNRESERVED =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!#$&+-.^_`|~"

    /**
     * Build a safe `Content-Disposition` header value for a download.
     *
     * The [filename] is typically derived from a stored object key or a
     * user-supplied name, which may contain characters that break the header:
     * a double-quote terminates the `filename="…"` quoted-string early (letting
     * a download masquerade under a different name), and control characters are
     * rejected outright by strict HTTP layers (turning a download into a 500).
     * Rather than interpolating the raw name, emit an RFC 6266 value:
     *  - the ASCII `filename` fallback drops control chars and escapes `\` and `"`;
     *  - `filename*=UTF-8''…` carries the exact (percent-encoded) name for clients
     *    that honour RFC 5987, covering non-ASCII names losslessly.
     *
     * @param filename the desired download filename (a single path segment)
     * @param inline when true emit `inline` (browser preview) instead of `attachment`
     */
    fun contentDisposition(
        filename: String,
        inline: Boolean = false
    ): String {
        val disposition = if (inline) "inline" else "attachment"
        val asciiFallback =
            buildString {
                for (c in filename) {
                    when {
                        c.code < 0x20 || c.code == 0x7F -> Unit

                        // strip control chars (strict layers reject them)
                        c == '\\' || c == '"' -> append('\\').append(c)

                        // escape quoted-string metachars
                        c.code > 0x7E -> append('_')

                        // non-ASCII placeholder; exact name travels in filename*
                        else -> append(c)
                    }
                }
            }.ifEmpty { "download" }
        val extended =
            buildString {
                for (b in filename.encodeToByteArray()) {
                    val v = b.toInt() and 0xFF
                    if (v < 0x80 && RFC5987_UNRESERVED.indexOf(v.toChar()) >= 0) {
                        append(v.toChar())
                    } else {
                        append('%').append(HEX_DIGITS[v shr 4]).append(HEX_DIGITS[v and 0x0F])
                    }
                }
            }
        return "$disposition; filename=\"$asciiFallback\"; filename*=UTF-8''$extended"
    }
}
