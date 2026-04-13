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

package com.sphereon.did.utils

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.native.ObjCName

/**
 * Represents a parsed DID or DID URL.
 *
 * A DID has the following structure:
 * ```
 * did:<method>:<method-specific-id>[/<path>][?<query>][#<fragment>]
 * ```
 *
 * Examples:
 * - `did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK`
 * - `did:web:example.com`
 * - `did:web:example.com:path:to:resource`
 * - `did:example:123#key-1`
 * - `did:example:123?service=hub`
 *
 * @property scheme Always "did" for valid DIDs
 * @property method The DID method (e.g., "key", "web", "jwk")
 * @property methodSpecificId The method-specific identifier
 * @property path Optional path component
 * @property query Optional query parameters as a map
 * @property fragment Optional fragment (e.g., "key-1")
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParsedDid", exact = true)
@JsExportCompat
@Serializable
data class ParsedDid(
    val scheme: String,
    val method: String,
    val methodSpecificId: String,
    val path: String? = null,
    val query: Map<String, String>? = null,
    val fragment: String? = null
) {
    companion object {
        private val DID_REGEX = Regex(
            """^did:([a-z0-9]+):([^/?#]+)(/[^?#]*)?(\?[^#]*)?(#.*)?$""",
            RegexOption.IGNORE_CASE
        )

        /**
         * Parses a DID or DID URL string.
         *
         * @param did The DID string to parse
         * @return ParsedDid object
         * @throws IllegalArgumentException if the DID is invalid
         */
        @JsStatic
        @JsExportIgnoreCompat
        fun parse(did: String): ParsedDid {
            val match = DID_REGEX.matchEntire(did)
                ?: throw IllegalArgumentException("Invalid DID format: $did")

            val method = match.groupValues[1]
            val methodSpecificId = match.groupValues[2]
            val pathPart = match.groupValues[3].takeIf { it.isNotEmpty() }?.removePrefix("/")
            val queryPart = match.groupValues[4].takeIf { it.isNotEmpty() }?.removePrefix("?")
            val fragmentPart = match.groupValues[5].takeIf { it.isNotEmpty() }?.removePrefix("#")

            val queryParams = queryPart?.let { parseQueryString(it) }

            return ParsedDid(
                scheme = "did",
                method = method,
                methodSpecificId = methodSpecificId,
                path = pathPart,
                query = queryParams,
                fragment = fragmentPart
            )
        }

        /**
         * Attempts to parse a DID string, returning null if invalid.
         *
         * @param did The DID string to parse
         * @return ParsedDid object or null if invalid
         */
        @JsStatic
        @JsExportIgnoreCompat
        fun tryParse(did: String): ParsedDid? {
            return try {
                parse(did)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        /**
         * Checks if a string is a valid DID format.
         *
         * @param did The string to check
         * @return true if the string is a valid DID format
         */
        @JsStatic
        fun isValidDid(did: String): Boolean = DID_REGEX.matches(did)

        private fun parseQueryString(query: String): Map<String, String> {
            return query.split("&")
                .filter { it.isNotEmpty() }
                .associate { param ->
                    val parts = param.split("=", limit = 2)
                    val key = parts[0]
                    val value = if (parts.size > 1) parts[1] else ""
                    key to value
                }
        }
    }

    /**
     * Returns the base DID without path, query, or fragment.
     * Example: "did:example:123#key-1" -> "did:example:123"
     */
    val did: String
        get() = "did:$method:$methodSpecificId"

    /**
     * Returns the full DID URL including all components.
     */
    val didUrl: String
        get() = buildString {
            append(did)
            path?.let { append("/$it") }
            query?.let { params ->
                append("?")
                append(params.entries.joinToString("&") { "${it.key}=${it.value}" })
            }
            fragment?.let { append("#$it") }
        }

    /**
     * Checks if this is a DID URL (has path, query, or fragment).
     */
    val isDidUrl: Boolean
        get() = path != null || query != null || fragment != null

    /**
     * Gets the value of a query parameter.
     *
     * @param key The query parameter key
     * @return The value or null if not present
     */
    fun getQueryParam(key: String): String? = query?.get(key)

    /**
     * Creates a new ParsedDid with a different fragment.
     *
     * @param newFragment The new fragment value
     * @return A new ParsedDid with the updated fragment
     */
    @JsExportIgnoreCompat
    fun withFragment(newFragment: String?): ParsedDid = copy(fragment = newFragment)

    /**
     * Creates a new ParsedDid with an additional or updated query parameter.
     *
     * @param key The query parameter key
     * @param value The query parameter value
     * @return A new ParsedDid with the updated query
     */
    @JsExportIgnoreCompat
    fun withQueryParam(key: String, value: String): ParsedDid {
        val newQuery = (query ?: emptyMap()).toMutableMap()
        newQuery[key] = value
        return copy(query = newQuery)
    }

    override fun toString(): String = didUrl
}
