/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.http

import com.sphereon.core.compat.JsExportCompat

/**
 * Percent-decoding for URL components.
 *
 * Multi-byte UTF-8 sequences (e.g. `%E2%82%AC` = €) are handled correctly: consecutive `%HH`
 * bytes are accumulated into a [ByteArray] and decoded as UTF-8 at the next non-`%` boundary
 * via [ByteArray.decodeToString]. A simpler `code.toChar()` approach (used by every
 * pre-existing helper in this codebase before this one) only produces correct output for the
 * ASCII subset (≤ 127) and silently corrupts everything above it.
 *
 * @param plusAsSpace `application/x-www-form-urlencoded` semantics (`+` → space) when `true`;
 *                    RFC 3986 path / query-component semantics (`+` is literal) when `false`.
 *                    Defaults to `false` because that's the correct behaviour for path segments,
 *                    and the form-encoded case is the conservative explicit opt-in. For an HTTP
 *                    request body of `application/x-www-form-urlencoded` callers should pass
 *                    `true`.
 *
 * Malformed `%H` or `%HX` (truncated) and `%HZ` (non-hex) sequences are passed through
 * unchanged rather than rejected, mirroring browser leniency. This is intentional: the helper
 * is a wire-shape normaliser, not a validator.
 *
 * Examples:
 * ```
 * "did%3Ajwk%3AeyJ".percentDecode()                    -> "did:jwk:eyJ"
 * "hello%20world".percentDecode()                       -> "hello world"
 * "hello+world".percentDecode()                         -> "hello+world"     // path/query-component
 * "hello+world".percentDecode(plusAsSpace = true)       -> "hello world"     // form-encoded
 * "%E2%82%AC".percentDecode()                           -> "€"               // UTF-8 multi-byte
 * "%ZZ".percentDecode()                                 -> "%ZZ"             // lenient: malformed passes through
 * ```
 */
@JsExportCompat
fun String.percentDecode(plusAsSpace: Boolean = false): String {
    // Fast path: nothing to decode.
    if (indexOf('%') < 0 && (!plusAsSpace || indexOf('+') < 0)) {
        return this
    }

    val sb = StringBuilder(length)
    val pendingBytes = mutableListOf<Byte>()

    fun flush() {
        if (pendingBytes.isNotEmpty()) {
            sb.append(pendingBytes.toByteArray().decodeToString())
            pendingBytes.clear()
        }
    }

    var i = 0
    while (i < length) {
        val c = this[i]
        when {
            c == '%' && i + 2 < length -> {
                val hi = this[i + 1].digitToIntOrNull(16)
                val lo = this[i + 2].digitToIntOrNull(16)
                if (hi != null && lo != null) {
                    pendingBytes += ((hi shl 4) or lo).toByte()
                    i += 3
                } else {
                    flush()
                    sb.append(c)
                    i++
                }
            }

            c == '+' && plusAsSpace -> {
                flush()
                sb.append(' ')
                i++
            }

            else -> {
                flush()
                sb.append(c)
                i++
            }
        }
    }
    flush()
    return sb.toString()
}
