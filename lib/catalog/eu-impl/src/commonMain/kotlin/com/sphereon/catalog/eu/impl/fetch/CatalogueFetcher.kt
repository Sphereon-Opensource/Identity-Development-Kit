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

package com.sphereon.catalog.eu.impl.fetch

import com.sphereon.catalog.eu.error.CatalogError
import com.sphereon.catalog.eu.error.CatalogErrorCode
import com.sphereon.core.api.IdkErrorResult
import com.sphereon.core.api.IdkOkResult
import com.sphereon.core.api.IdkResult

/**
 * Transport SPI for the catalogue fetcher. An implementation issues an HTTP GET and returns the exact response
 * bytes; it must not decode, normalise or re-encode them, because digests and signatures are computed over them.
 * Non-2xx responses and transport failures are reported as [CatalogErrorCode.FETCH_FAILED].
 */
interface CatalogueHttpClient {
    suspend fun get(url: String): IdkResult<ByteArray, CatalogError>
}

class FetchedFile(
    val url: String,
    val bytes: ByteArray,
)

/**
 * Fetches catalogue main files and their entry files. Entry files are addressed by the relative `ds:Reference` URI of
 * the index, resolved against the URL of the main file after percent-encoding each path segment.
 */
class CatalogueFetcher(
    private val http: CatalogueHttpClient,
    private val maxBytes: Int = MAX_FILE_BYTES,
) {
    suspend fun fetch(url: String): IdkResult<FetchedFile, CatalogError> {
        if (!isHttpUrl(url)) {
            return IdkErrorResult(CatalogError(CatalogErrorCode.FETCH_FAILED, "Only http and https catalogue URLs can be fetched", url))
        }
        val result = http.get(url)
        if (result.isErr) {
            return IdkErrorResult(result.error)
        }
        val bytes = result.value
        if (bytes.size > maxBytes) {
            return IdkErrorResult(CatalogError(CatalogErrorCode.FETCH_FAILED, "The response exceeds the $maxBytes byte limit", url))
        }
        return IdkOkResult(FetchedFile(url, bytes))
    }

    suspend fun fetchEntry(
        mainFileUrl: String,
        entryUri: String,
    ): IdkResult<FetchedFile, CatalogError> {
        val resolved = resolveEntryUrl(mainFileUrl, entryUri)
        if (resolved.isErr) {
            return IdkErrorResult(resolved.error)
        }
        return fetch(resolved.value)
    }

    companion object {
        const val MAX_FILE_BYTES = 16 * 1024 * 1024

        private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
        private const val HEX = "0123456789ABCDEF"

        /**
         * Resolves [entryUri] relative to the directory of [mainFileUrl]. Absolute URIs, network-path references,
         * query strings, fragments and `..` segments are refused, so an entry can never point outside the directory
         * tree of its main file.
         */
        fun resolveEntryUrl(
            mainFileUrl: String,
            entryUri: String,
        ): IdkResult<String, CatalogError> {
            fun invalid(reason: String): IdkResult<String, CatalogError> = IdkErrorResult(CatalogError(CatalogErrorCode.INVALID_ENTRY_PATH, reason, entryUri))

            if (!isHttpUrl(mainFileUrl)) return invalid("The main file URL is not an http or https URL")
            val path = entryUri.trim()
            if (path.isEmpty()) return invalid("The entry path is empty")
            if (path.startsWith("/") || path.startsWith("\\") || SCHEME.containsMatchIn(path)) return invalid("The entry path must be relative to the main file")
            if (path.contains('?') || path.contains('#') || path.contains('\\')) return invalid("The entry path must not contain a query, fragment or backslash")

            val segments = path.split('/')
            val encoded = mutableListOf<String>()
            for (raw in segments) {
                // Decode first, so %2e%2e, %2F and %5C cannot smuggle a traversal past the checks below.
                val segment = percentDecode(raw) ?: return invalid("The entry path contains an invalid percent-encoded sequence")
                when {
                    segment == "." -> continue
                    segment == ".." -> return invalid("The entry path must not contain '..' segments")
                    segment.isEmpty() -> return invalid("The entry path must not contain empty segments")
                    segment.contains('/') || segment.contains('\\') -> return invalid("The entry path must not contain an encoded path separator")
                    segment.any { it.code < 0x20 || it.code == 0x7F } -> return invalid("The entry path must not contain control characters")
                    else -> encoded.add(encodeSegment(segment))
                }
            }
            if (encoded.isEmpty()) return invalid("The entry path is empty")

            val withoutSuffix = mainFileUrl.substringBefore('#').substringBefore('?')
            val directory = withoutSuffix.substring(0, withoutSuffix.lastIndexOf('/') + 1)
            if (directory.length <= withoutSuffix.indexOf("://") + 3) return invalid("The main file URL has no path")
            return IdkOkResult(directory + encoded.joinToString("/"))
        }

        private fun isHttpUrl(url: String): Boolean {
            val lower = url.trim().lowercase()
            return lower.startsWith("https://") || lower.startsWith("http://")
        }

        /** Percent-decodes [segment] as UTF-8; null when an escape is truncated, non-hex, or the bytes are not valid UTF-8. */
        private fun percentDecode(segment: String): String? {
            if (!segment.contains('%')) return segment
            val input = segment.encodeToByteArray()
            val out = ByteArray(input.size)
            var n = 0
            var i = 0
            while (i < input.size) {
                val b = input[i].toInt() and 0xFF
                if (b == '%'.code) {
                    if (i + 2 >= input.size) return null
                    if (!isHex(input[i + 1]) || !isHex(input[i + 2])) return null
                    out[n++] = ((hexValue(input[i + 1]) shl 4) or hexValue(input[i + 2])).toByte()
                    i += 3
                } else {
                    out[n++] = input[i]
                    i++
                }
            }
            return try {
                out.copyOf(n).decodeToString(throwOnInvalidSequence = true)
            } catch (e: Exception) {
                null
            }
        }

        private fun hexValue(b: Byte): Int {
            val c = (b.toInt() and 0xFF).toChar()
            return when (c) {
                in '0'..'9' -> c - '0'
                in 'a'..'f' -> c - 'a' + 10
                else -> c - 'A' + 10
            }
        }

        /** Percent-encodes everything except unreserved characters. */
        private fun encodeSegment(segment: String): String {
            val out = StringBuilder()
            for (byte in segment.encodeToByteArray()) {
                val b = byte.toInt() and 0xFF
                val c = b.toChar()
                val isUnreserved = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '.' || c == '_' || c == '~'
                if (isUnreserved) out.append(c) else out.append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
            }
            return out.toString()
        }

        private fun isHex(b: Byte): Boolean {
            val c = (b.toInt() and 0xFF).toChar()
            return c in '0'..'9' || c in 'A'..'F' || c in 'a'..'f'
        }
    }
}
