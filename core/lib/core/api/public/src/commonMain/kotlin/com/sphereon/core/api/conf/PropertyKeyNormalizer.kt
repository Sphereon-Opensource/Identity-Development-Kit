/*
 * Â© 2026 Sphereon International B.V.
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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.session.BaseCommand
import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Provides functionality to normalize property keys by applying standardized formatting rules.
 * This normalization is useful for consistent representations of property keys
 * across different contexts or environments where they may vary in format.
 */
fun interface PropertyKeyNormalizer {
    fun normalize(key: String): String
}

/**
 * Provides functionality to denormalize property keys back to a target format.
 * This is the inverse operation of PropertyKeyNormalizer.
 */
fun interface PropertyKeyDenormalizer {
    fun denormalize(normalizedKey: String): String
}

/**
 * Denormalizes dot-separated lowercase keys back to camelCase.
 *
 * Example: "expose.private.keys" -> "exposePrivateKeys"
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CamelCaseKeyDenormalizerImpl", exact = true)
class CamelCaseKeyDenormalizerImpl(
    private val delimiter: String = PROPERTY_KEY_DELIMITER,
) : PropertyKeyDenormalizer {
    override fun denormalize(normalizedKey: String): String {
        if (!normalizedKey.contains(delimiter)) {
            return normalizedKey
        }

        return buildString {
            var i = 0
            var segmentIndex = 0
            while (i < normalizedKey.length) {
                // Bracket-quoted literal: pass through verbatim
                if (normalizedKey[i] == '[') {
                    val closeBracket = normalizedKey.indexOf(']', i + 1)
                    if (closeBracket > i) {
                        append(normalizedKey, i, closeBracket + 1)
                        i = closeBracket + 1
                        // Skip trailing delimiter
                        if (i < normalizedKey.length && normalizedKey.substring(i).startsWith(delimiter)) {
                            i += delimiter.length
                        }
                        segmentIndex++
                        continue
                    }
                }

                // Find next delimiter or bracket
                val nextDelim = normalizedKey.indexOf(delimiter, i)
                val nextBracket = normalizedKey.indexOf('[', i)
                val segmentEnd =
                    when {
                        nextDelim < 0 && nextBracket < 0 -> normalizedKey.length
                        nextDelim < 0 -> nextBracket
                        nextBracket < 0 -> nextDelim
                        else -> minOf(nextDelim, nextBracket)
                    }

                val segment = normalizedKey.substring(i, segmentEnd)
                if (segmentIndex == 0) {
                    append(segment)
                } else {
                    append(segment.replaceFirstChar { it.uppercaseChar() })
                }
                segmentIndex++

                i = segmentEnd
                if (i < normalizedKey.length && normalizedKey.substring(i).startsWith(delimiter)) {
                    i += delimiter.length
                }
            }
        }
    }
}

/**
 * A no-op denormalizer that returns keys unchanged.
 * Use this to preserve backward-compatible behavior.
 */
object NoOpKeyDenormalizer : PropertyKeyDenormalizer {
    override fun denormalize(normalizedKey: String): String = normalizedKey
}

/**
 * Provides functionality to normalize property keys by applying standardized formatting rules.
 * This normalization is useful for consistent representations of property keys
 * across different contexts or environments where they may vary in format.
 *
 * @property delimiter The delimiter used to separate parts of the normalized key.
 *                      Defaults to the globally defined `PROPERTY_KEY_DELIMITER`.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertyKeyNormalizerImpl", exact = true)
class PropertyKeyNormalizerImpl(
    val delimiter: String = PROPERTY_KEY_DELIMITER,
) : PropertyKeyNormalizer {
    /**
     * Normalizes a given string key by converting it into a standardized format.
     * The normalization process transforms camelCase into camel<delimiter>case,
     * replaces special characters (spaces, underscores, hyphens, periods) with the specified delimiter,
     * and ensures no sequential delimiters exist in the output.
     *
     * Bracket-quoted segments (`[...]`) are treated as **literal identifiers** and preserved
     * verbatim — no case conversion, no delimiter insertion. This supports config map keys
     * that are external identifiers (e.g., credential configuration IDs, provider IDs).
     *
     * Examples:
     * - `signingKeyAlias` → `signing.key.alias`
     * - `credentials.[TestCredential].format` → `credentials.[TestCredential].format`
     * - `credentials.[PID].signingKeyAlias` → `credentials.[PID].signing.key.alias`
     *
     * @param key the string key to normalize
     * @return the normalized string with applied transformations
     */
    override fun normalize(key: String): String {
        if (key.isEmpty()) {
            return key
        }

        val sb = StringBuilder(key.length + NORMALIZE_CAPACITY_PADDING)
        var i = 0
        var lastWasDelimiter = false

        while (i < key.length) {
            // Bracket-quoted literal segment: preserve verbatim
            if (key[i] == '[') {
                val closeBracket = key.indexOf(']', i + 1)
                if (closeBracket > i) {
                    if (sb.isNotEmpty() && !lastWasDelimiter) {
                        sb.append(delimiter)
                    }
                    sb.append(key, i, closeBracket + 1)
                    lastWasDelimiter = false
                    i = closeBracket + 1
                    continue
                }
            }

            val currentChar = key[i]
            when {
                // Any special existing delimiters are replaced by the configured delimiter.
                currentChar == ' ' || currentChar == '_' || currentChar == '-' || currentChar == '.' -> {
                    if (!lastWasDelimiter) {
                        sb.append(delimiter)
                        lastWasDelimiter = true
                    }
                }

                // Any camelCase is transformed into camel<delimiter>case
                currentChar.isUpperCase() -> {
                    if (i > 0 && !lastWasDelimiter) {
                        sb.append(delimiter)
                    }
                    sb.append(currentChar.lowercaseChar())
                    lastWasDelimiter = false
                }

                else -> {
                    sb.append(currentChar)
                    lastWasDelimiter = false
                }
            }
            i++
        }
        return sb.toString()
    }

    companion object {
        private const val NORMALIZE_CAPACITY_PADDING = 4
        val Default: PropertyKeyNormalizerImpl = PropertyKeyNormalizerImpl()
    }
}

/**
 * A command that normalizes property keys using a standardized formatting mechanism.
 * This command leverages the `PropertyKeyNormalizerImpl` class for normalization, ensuring
 * consistent formatting of keys by replacing special characters and enforcing a unified delimiter.
 *
 * @constructor Initializes a new instance of the command with a specified delimiter for normalization.
 * The default delimiter is the value of the global constant `PROPERTY_KEY_DELIMITER`.
 *
 * @param delimiter The delimiter used to separate parts of the normalized key.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertyKeyNormalizerCommandImpl", exact = true)
class PropertyKeyNormalizerCommandImpl(
    delimiter: String = PROPERTY_KEY_DELIMITER,
) : BaseCommand<String, String, IdkErrorType> {
    /**
     * An instance of `PropertyKeyNormalizerImpl` used for normalizing property keys.
     *
     * @see PropertyKeyNormalizerImpl
     */
    val normalizer =
        if (delimiter == PROPERTY_KEY_DELIMITER) {
            PropertyKeyNormalizerImpl.Default
        } else {
            PropertyKeyNormalizerImpl(delimiter)
        }

    /**
     * Determines whether the given argument is supported by the command.
     *
     * @param args the argument to evaluate for compatibility, can be of any type
     * @return true if the argument is a string, false otherwise
     */
    override suspend fun supports(args: Any): Boolean = args is String

    /**
     * Executes the normalization of a given string key using the specified execution context.
     *
     * @param args the string key to be normalized
     * @param sessionContext an implementation of ISureExecutionContext, providing additional context for execution
     * @return a IdkResult containing either the normalized string as a successful outcome or an instance of SureError in case of failure
     */
    override suspend fun execute(args: String): IdkResult<String, IdkErrorType> = IdkResult.ok(normalizer.normalize(args))
}
