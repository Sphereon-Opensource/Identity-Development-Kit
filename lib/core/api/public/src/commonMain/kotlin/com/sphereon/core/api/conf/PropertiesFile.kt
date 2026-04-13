/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.core.api.conf

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readLine

/**
 * Reads a properties file from the given path and returns its contents as a map of key-value pairs.
 * Lines starting with certain comment prefixes (#, !, ;, :) or empty lines are ignored.
 * Keys and values are trimmed of whitespace and surrounding quotation marks.
 *
 * @param source the file path to the properties file to be read
 * @return a map containing key-value pairs representing the properties in the file,
 *         or an empty map if the file does not exist or is empty
 */
fun readPropertiesFromPath(source: Path): Map<String, Any> {
    if (!SystemFileSystem.exists(source)) {
        return emptyMap()
    }

    val properties = mutableMapOf<String, Any>()

    // Comment prefixes need to be ignored
    val commentPrefixes = listOf("#", "!", ";", ":")

    fun isValidLine(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.isNotEmpty() && commentPrefixes.none { trimmed.startsWith(it) }
    }

    fun findSeparatorIndex(line: String): Int {
        var escaped = false
        for (i in line.indices) {
            val ch = line[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\') {
                escaped = true
                continue
            }
            if (ch == '=' || ch == ':' || ch.isWhitespace()) {
                return i
            }
        }
        return -1
    }

    fun unescapeProperty(input: String): String {
        val result = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (ch != '\\' || i == input.lastIndex) {
                result.append(ch)
                i++
                continue
            }

            val next = input[i + 1]
            when (next) {
                't' -> result.append('\t')
                'r' -> result.append('\r')
                'n' -> result.append('\n')
                'f' -> result.append('\u000c')
                'u' -> {
                    if (i + 5 < input.length) {
                        val hex = input.substring(i + 2, i + 6)
                        val unicode = hex.toIntOrNull(16)
                        if (unicode != null) {
                            result.append(unicode.toChar())
                            i += 6
                            continue
                        }
                    }
                    result.append(next)
                }
                else -> result.append(next)
            }
            i += 2
        }
        return result.toString()
    }

    fun parseKeyValuePair(line: String): Pair<String, String>? {
        val trimmed = line.trim()
        val separatorIndex = findSeparatorIndex(trimmed)
        if (separatorIndex <= 0) return null

        val keyPart = trimmed.substring(0, separatorIndex).trimEnd()
        var valueStart = separatorIndex + 1

        if (separatorIndex < trimmed.length && trimmed[separatorIndex].isWhitespace()) {
            while (valueStart < trimmed.length && trimmed[valueStart].isWhitespace()) {
                valueStart++
            }
            if (valueStart < trimmed.length && (trimmed[valueStart] == '=' || trimmed[valueStart] == ':')) {
                valueStart++
            }
        }

        while (valueStart < trimmed.length && trimmed[valueStart].isWhitespace()) {
            valueStart++
        }

        val valuePart = if (valueStart >= trimmed.length) "" else trimmed.substring(valueStart)
        val key = unescapeProperty(keyPart).trim(' ', '"', '\'')
        val value = unescapeProperty(valuePart).trim(' ', '"', '\'')

        if (key.isEmpty()) return null
        return key to value
    }

    SystemFileSystem.source(source).buffered().use { fileSource ->
        while (true) {
            val line = fileSource.readLine() ?: break
            if (isValidLine(line)) {
                parseKeyValuePair(line)?.let { (key, value) ->
                    properties[key] = value
                }
            }
        }
    }

    return properties
}
