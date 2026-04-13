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
 */

package com.sphereon.ktor.http.client

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.session.Command
import io.ktor.http.Parameters
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

// ============================================================================
// Parameters Extension Functions
// ============================================================================

/**
 * Get a required parameter from Parameters, returning an error if missing or blank.
 *
 * @param name Parameter name
 * @param parameterType Description of parameter type for error message (e.g., "client_id", "redirect_uri")
 * @return IdkResult with the parameter value, or error if missing/blank
 */
fun Parameters.getRequired(name: String, parameterType: String = name): IdkResult<String, IdkError> {
    val value = this[name]?.takeIf { it.isNotBlank() }
    return if (value != null) {
        Ok(value)
    } else {
        Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing required parameter: $parameterType"))
    }
}

/**
 * Get an optional parameter from Parameters, returning null if missing or blank.
 *
 * @param name Parameter name
 * @return Parameter value or null if missing/blank
 */
fun Parameters.getOptional(name: String): String? {
    return this[name]?.takeIf { it.isNotBlank() }
}

/**
 * Get an optional parameter from Parameters with a default value.
 *
 * @param name Parameter name
 * @param default Default value to use if parameter is missing or blank
 * @return Parameter value or default
 */
fun Parameters.getOrDefault(name: String, default: String): String {
    return this[name]?.takeIf { it.isNotBlank() } ?: default
}

/**
 * Result of parsing a URI into its components.
 *
 * Uses Ktor's [Parameters] for query parameter handling, which properly supports:
 * - Multiple values per key
 * - URL decoding
 * - Empty values
 * - Special characters
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParsedUri", exact = true)
@JsExportCompat
data class ParsedUri(
    /**
     * Full original URI as provided
     */
    val uri: String,

    /**
     * URI scheme (e.g., "openid4vp", "https"), or null if no scheme present
     */
    val scheme: String?,

    /**
     * Hostname extracted from URI (e.g., "example.com"), or null if not applicable
     */
    val host: String?,

    /**
     * Path component (e.g., "/auth/callback"), or empty if none
     */
    val path: String,

    /**
     * Query parameters as Ktor Parameters.
     *
     * Supports multiple values per key via `getAll(name)`.
     * Use `get(name)` for first value or `contains(name)` to check presence.
     *
     * Examples:
     * - `foo=bar` → parameters["foo"] = "bar"
     * - `foo=bar&foo=baz` → parameters.getAll("foo") = ["bar", "baz"]
     * - `foo=` → parameters["foo"] = ""
     */
    val queryParameters: Parameters
) {
    /**
     * Alias for queryParameters to maintain compatibility
     */
    val parameters: Parameters
        get() = queryParameters

    /**
     * Get the first value for a parameter, or null if not present
     */
    fun getFirst(name: String): String? = queryParameters[name]

    /**
     * Get all values for a parameter
     */
    fun getAll(name: String): List<String> = queryParameters.getAll(name) ?: emptyList()

    /**
     * Get the first value for a required parameter, returning an error if missing or blank
     */
    fun getFirstRequired(name: String, parameterType: String = name): IdkResult<String, IdkError> {
        val value = getFirst(name)?.takeIf { it.isNotBlank() }
        return if (value != null) {
            Ok(value)
        } else {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing required parameter: $parameterType"))
        }
    }

    /**
     * Get all values for a required parameter, returning an error if missing or empty
     */
    fun getAllRequired(name: String, parameterType: String = name): IdkResult<List<String>, IdkError> {
        val values = getAll(name).filter { it.isNotBlank() }
        return if (values.isNotEmpty()) {
            Ok(values)
        } else {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing required parameter: $parameterType"))
        }
    }
}

/**
 * Command for parsing a URI into its components.
 *
 * Uses Ktor's built-in `parseQueryString` to properly handle:
 * - URL decoding
 * - Multiple values per key
 * - Empty values
 * - Special characters (=, &, ?, #, +, etc.)
 * - UTF-8 encoding
 *
 * Example:
 * ```
 * Input: "openid4vp://?client_id=foo&state=bar&scope=a&scope=b"
 * Output: ParsedUri(
 *   uri = "openid4vp://?client_id=foo&state=bar&scope=a&scope=b",
 *   scheme = "openid4vp",
 *   host = null,
 *   path = "//",
 *   queryParameters = Parameters with:
 *     - client_id: ["foo"]
 *     - state: ["bar"]
 *     - scope: ["a", "b"]
 * )
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParseUriQueryCommand", exact = true)
@JsExportCompat
interface ParseUriQueryCommand : Command<String, ParsedUri, IdkError> {
    companion object {
        const val COMMAND_ID = "http.uri.parse"
    }
}

/**
 * Command service interface for parsing URIs
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParseUriQueryCommandService", exact = true)
interface ParseUriQueryCommandService {
    suspend fun parseUriQuery(uri: String): IdkResult<ParsedUri, IdkError>
}
