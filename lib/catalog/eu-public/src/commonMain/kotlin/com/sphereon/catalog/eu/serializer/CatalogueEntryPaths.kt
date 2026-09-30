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

package com.sphereon.catalog.eu.serializer

/**
 * The relative paths of entry files next to their main file: `attributes/{ns}/{id}.xml` for a catalogue of
 * attributes and `schemes/{name}.xml` for a catalogue of schemes, each segment percent-encoded. The same paths are the
 * `ds:Reference` URIs of the index and the URL suffixes an authored catalogue is served under.
 */
object CatalogueEntryPaths {
    const val ATTRIBUTES_DIRECTORY = "attributes"
    const val SCHEMES_DIRECTORY = "schemes"

    fun attributeEntry(
        namespace: String,
        attributeIdentifier: String,
    ): String = "$ATTRIBUTES_DIRECTORY/${encodeSegment(namespace)}/${encodeSegment(attributeIdentifier)}.xml"

    fun schemeEntry(schemeName: String): String = "$SCHEMES_DIRECTORY/${encodeSegment(schemeName)}.xml"

    /**
     * A segment that survives percent-encoding as a plain name. `.` and `..` are unreserved characters, so they would
     * otherwise be published as path traversal segments; an empty segment would collapse the path.
     */
    fun isSafeSegment(segment: String): Boolean = segment.isNotEmpty() && segment != "." && segment != ".."

    /** Percent-encodes every byte that is not an RFC 3986 unreserved character. */
    fun encodeSegment(segment: String): String {
        val out = StringBuilder()
        for (byte in segment.encodeToByteArray()) {
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            val unreserved = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '.' || c == '_' || c == '~'
            if (unreserved) out.append(c) else out.append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
        }
        return out.toString()
    }

    /** Inverse of [encodeSegment]; returns null for a malformed escape. */
    fun decodeSegment(segment: String): String? {
        val bytes = ArrayList<Byte>(segment.length)
        val source = segment.encodeToByteArray()
        var i = 0
        while (i < source.size) {
            val b = source[i]
            if (b == '%'.code.toByte()) {
                if (i + 2 > source.lastIndex) return null
                val hi = hexValue(source[i + 1])
                val lo = hexValue(source[i + 2])
                if (hi < 0 || lo < 0) return null
                bytes.add(((hi shl 4) or lo).toByte())
                i += 3
            } else {
                bytes.add(b)
                i++
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun hexValue(b: Byte): Int =
        when (val c = (b.toInt() and 0xFF).toChar()) {
            in '0'..'9' -> c - '0'
            in 'A'..'F' -> c - 'A' + 10
            in 'a'..'f' -> c - 'a' + 10
            else -> -1
        }

    private const val HEX = "0123456789ABCDEF"
}
