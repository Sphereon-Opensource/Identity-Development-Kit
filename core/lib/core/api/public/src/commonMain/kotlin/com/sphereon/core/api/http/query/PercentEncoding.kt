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

package com.sphereon.core.api.http.query

private val HEX =
    charArrayOf(
        '0',
        '1',
        '2',
        '3',
        '4',
        '5',
        '6',
        '7',
        '8',
        '9',
        'A',
        'B',
        'C',
        'D',
        'E',
        'F',
    )

/**
 * Percent-encode [value] for use as a single application/x-www-form-urlencoded or `?key=value`
 * query-component value, per RFC 3986 §2.1 and §3.4.
 *
 * Unreserved characters per RFC 3986 §2.3 (`A-Z`, `a-z`, `0-9`, `-`, `.`, `_`, `~`) pass through.
 * Every other character is encoded as `%HH` over its UTF-8 byte sequence, including reserved
 * delimiters (`&`, `=`, `?`, `#`, `+`, `/`, `:`, `@`, etc.), spaces, and any non-ASCII Unicode.
 *
 * The input is assumed UNencoded; passing `%20` in produces `%2520` (no double-encoding heuristic).
 *
 * KMP-common helper: callers in `lib-oauth2-server-authorization-impl` and
 * `services-oauth2-as-rest` share this implementation so the AS's `/authorize` redirects and
 * `/login` redirects produce byte-identical query components.
 */
public fun percentEncodeQueryComponent(value: String): String {
    val sb = StringBuilder(value.length)
    for (ch in value) {
        if (ch.isUnreserved()) {
            sb.append(ch)
        } else {
            for (b in ch.toString().encodeToByteArray()) {
                val unsigned = b.toInt() and 0xFF
                sb.append('%')
                sb.append(HEX[(unsigned shr 4) and 0x0F])
                sb.append(HEX[unsigned and 0x0F])
            }
        }
    }
    return sb.toString()
}

private fun Char.isUnreserved(): Boolean =
    this in 'A'..'Z' ||
        this in 'a'..'z' ||
        this in '0'..'9' ||
        this == '-' ||
        this == '.' ||
        this == '_' ||
        this == '~'
