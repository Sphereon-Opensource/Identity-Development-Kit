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
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.session.ExecutionScopedCommandAdapter

import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import io.ktor.http.Parameters
import io.ktor.http.parseQueryString
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of ParseUriQueryCommand using Ktor's built-in URL parsing.
 *
 * Leverages `io.ktor.http.parseQueryString` for standards-compliant query parameter parsing.
 * Returns Ktor's [Parameters] directly for optimal query parameter handling.
 *
 * Supported URI formats:
 * - `scheme://host/path?key=value&key2=value2`
 * - `scheme://host?key=value` (no path)
 * - `scheme://?key=value` (no host)
 * - `/path?key=value` (relative URI)
 * - `?key=value` (query only)
 * - Query parameters with multiple values: `?foo=bar&foo=baz`
 * - Empty values: `?foo=&bar=baz`
 * - URL-encoded values: `?name=John%20Doe`
 * - Plus as space: `?message=Hello+World`
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ParseUriQueryCommand>())
class ParseUriQueryCommandImpl(
    execution: SessionExecution,
) : ExecutionScopedCommandAdapter<String, ParsedUri, IdkError>(
    id = ParseUriQueryCommand.COMMAND_ID,
    execution = execution,
), ParseUriQueryCommand, ParseUriQueryCommandService {

    override suspend fun parseUriQuery(uri: String): IdkResult<ParsedUri, IdkError> {
        return execute(uri)
    }

    override suspend fun doExecute(
        args: String,
        applyDuring: (String) -> String
    ): IdkResult<ParsedUri, IdkError> {
        val processedUri = applyDuring(args)

        // Blank URIs are not valid
        if (processedUri.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "URI cannot be blank"))
        }

        // Split by first '?' to separate base from query string
        val queryIndex = processedUri.indexOf('?')
        val baseUri = if (queryIndex >= 0) processedUri.substring(0, queryIndex) else processedUri
        val queryString = if (queryIndex >= 0 && queryIndex < processedUri.length - 1) {
            processedUri.substring(queryIndex + 1)
        } else {
            null
        }

        // Extract scheme if present (format: scheme://...)
        val scheme = if (baseUri.contains("://")) {
            baseUri.substringBefore("://")
        } else {
            null
        }

        // Extract host and path
        val afterScheme = if (scheme != null) {
            baseUri.substringAfter("://")
        } else {
            baseUri
        }

        val (host, path) = when {
            // Has scheme and contains forward slash after authority: https://example.com/path
            scheme != null && afterScheme.isNotEmpty() && afterScheme.contains("/") -> {
                val hostPart = afterScheme.substringBefore("/")
                val pathPart = "/" + afterScheme.substringAfter("/")
                hostPart to pathPart
            }
            // Has scheme but empty after "://" WITH query string: openid4vp://?...
            scheme != null && afterScheme.isEmpty() && queryString != null -> {
                null to "//"
            }
            // Has scheme but empty after "://" WITHOUT query string: openid4vp://
            scheme != null && afterScheme.isEmpty() -> {
                null to ""
            }
            // Has scheme with host but no path: https://example.com
            scheme != null && afterScheme.isNotEmpty() -> {
                afterScheme to ""
            }
            // No scheme - just path
            else -> {
                null to afterScheme
            }
        }

        // Parse query parameters using Ktor's parseQueryString
        val queryParameters: Parameters = if (queryString != null && queryString.isNotBlank()) {
            parseQueryString(queryString)
        } else {
            Parameters.Empty
        }

        return Ok(
            ParsedUri(
                uri = processedUri,
                scheme = scheme,
                host = host,
                path = path,
                queryParameters = queryParameters
            )
        )
    }
}
