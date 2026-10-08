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

package com.sphereon.oauth2.server.authorization.impl.log

import com.sphereon.core.api.http.percentDecode

private const val REDACTED = "[redacted]"
private const val MAX_NESTED_URL_DEPTH = 3

private val SECRET_QUERY_KEYS =
    setOf(
        "code",
        "access_token",
        "id_token",
        "refresh_token",
        "token",
        "client_secret",
        "code_verifier",
        "password",
        "assertion",
        "client_assertion",
        "id_token_hint",
        "request",
    )

private val NESTED_URL_QUERY_KEYS =
    setOf(
        "return_url",
        "redirect_uri",
        "redirect_url",
    )

/**
 * Keep scheme/host/path and non-secret query names so authorize/federation hops stay
 * diagnosable, while stripping authorization codes, tokens, and secrets.
 *
 * Nested `return_url` / `redirect_uri` values are decoded and sanitized the same way.
 */
fun sanitizeLogUrl(url: String): String = sanitizeLogUrl(url, depth = 0)

private fun sanitizeLogUrl(
    url: String,
    depth: Int,
): String {
    if (url.isBlank() || depth > MAX_NESTED_URL_DEPTH) return url
    val hashIdx = url.indexOf('#')
    val queryIdx = url.indexOf('?')
    if (queryIdx < 0 && hashIdx < 0) return url

    val baseEnd =
        when {
            queryIdx >= 0 -> queryIdx
            else -> hashIdx
        }
    val queryEnd = if (hashIdx > queryIdx && queryIdx >= 0) hashIdx else url.length
    val query = if (queryIdx >= 0) url.substring(queryIdx + 1, queryEnd) else null
    val fragment = if (hashIdx >= 0) url.substring(hashIdx + 1) else null

    return buildString {
        append(url.substring(0, baseEnd))
        if (query != null) {
            append('?')
            append(sanitizeQuery(query, depth))
        }
        if (fragment != null) {
            append('#')
            append(sanitizeQuery(fragment, depth))
        }
    }
}

private fun sanitizeQuery(
    query: String,
    depth: Int,
): String {
    if (query.isEmpty()) return query
    return query.split('&').joinToString("&") { pair ->
        if (pair.isEmpty()) return@joinToString pair
        val eq = pair.indexOf('=')
        if (eq < 0) return@joinToString pair
        val rawKey = pair.substring(0, eq)
        val rawValue = pair.substring(eq + 1)
        val key = rawKey.percentDecode(plusAsSpace = true).lowercase()
        val value = rawValue.percentDecode(plusAsSpace = true)
        val sanitized =
            when {
                key in SECRET_QUERY_KEYS -> REDACTED
                key in NESTED_URL_QUERY_KEYS -> sanitizeLogUrl(value, depth + 1)
                else -> value
            }
        "$rawKey=$sanitized"
    }
}
