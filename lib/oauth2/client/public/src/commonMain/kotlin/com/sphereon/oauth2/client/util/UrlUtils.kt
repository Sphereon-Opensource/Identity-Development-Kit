/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.client.util

import com.sphereon.core.api.conf.Env
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLQueryComponent

/**
 * Whether HTTP is allowed for non-localhost URLs. Set the environment variable
 * `OAUTH2_CLIENT_ALLOW_INSECURE_HTTP=true` to enable (e.g. for Docker-internal hostnames).
 */
private val allowInsecureHttp: Boolean by lazy {
    Env.get("OAUTH2_CLIENT_ALLOW_INSECURE_HTTP")?.toBoolean() == true
}

/**
 * Checks if a URL is secure: HTTPS required, HTTP allowed for localhost/127.0.0.1/[::1].
 * Use this for all endpoint URL validation to avoid duplicating the localhost exception logic.
 *
 * Localhost matching requires the host portion to end at a port separator (:), path separator (/), or end of string
 * to prevent matching domains like "http://localhostnotreally.com".
 *
 * HTTP is also allowed for all hosts when `OAUTH2_CLIENT_ALLOW_INSECURE_HTTP=true` is set,
 * which is needed for Docker-internal hostnames (e.g. `http://keycloak:8080`).
 */
fun isSecureUrl(url: String): Boolean {
    if (url.startsWith("https://")) {
        return true
    }
    if (allowInsecureHttp && url.startsWith("http://")) {
        return true
    }
    val localPrefixes = listOf("http://localhost", "http://127.0.0.1", "http://[::1]")
    return localPrefixes.any { prefix ->
        url.startsWith(prefix) && (url.length == prefix.length || url[prefix.length] in listOf(':', '/', '?'))
    }
}

/**
 * Encodes a map of parameters into a URL query string
 *
 * Example: mapOf("foo" to "bar", "baz" to "qux") -> "foo=bar&baz=qux"
 *
 * @param parameters Map of parameter names to values. Null values are omitted.
 * @return Encoded query string without leading "?"
 */
fun encodeQueryParameters(parameters: Map<String, String?>): String =
    parameters
        .filterValues { it != null }
        .map { (key, value) ->
            "${key.encodeURLQueryComponent()}=${value!!.encodeURLQueryComponent()}"
        }.joinToString("&")

/**
 * Decodes a URL query string into a map of parameters
 *
 * Example: "foo=bar&baz=qux" -> mapOf("foo" to "bar", "baz" to "qux")
 *
 * @param queryString Query string without leading "?" (or with it - will be stripped)
 * @return Map of decoded parameter names to values
 */
fun decodeQueryParameters(queryString: String): Map<String, String> {
    val normalized = queryString.trimStart('?')
    if (normalized.isEmpty()) {
        return emptyMap()
    }

    return normalized
        .split("&")
        .mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                parts[0].decodeURLQueryComponent() to parts[1].decodeURLQueryComponent()
            } else if (parts.size == 1 && parts[0].isNotEmpty()) {
                // Handle parameter without value (e.g., "?foo&bar=baz")
                parts[0].decodeURLQueryComponent() to ""
            } else {
                null
            }
        }.toMap()
}

/**
 * Extracts query parameters from a full URL
 *
 * Example: "https://example.com/callback?code=abc&state=xyz" -> mapOf("code" to "abc", "state" to "xyz")
 *
 * @param url Full URL with query parameters
 * @return Map of decoded parameter names to values
 */
fun extractQueryParameters(url: String): Map<String, String> {
    val queryStart = url.indexOf('?')
    if (queryStart == -1) {
        return emptyMap()
    }

    val fragmentStart = url.indexOf('#', queryStart)
    val queryString =
        if (fragmentStart != -1) {
            url.substring(queryStart + 1, fragmentStart)
        } else {
            url.substring(queryStart + 1)
        }

    return decodeQueryParameters(queryString)
}

/**
 * Builds a URL with query parameters
 *
 * @param baseUrl Base URL (with or without existing query parameters)
 * @param parameters Additional parameters to append
 * @return Complete URL with all parameters
 */
fun buildUrl(
    baseUrl: String,
    parameters: Map<String, String?>,
): String {
    val encodedParams = encodeQueryParameters(parameters)
    if (encodedParams.isEmpty()) {
        return baseUrl
    }

    val separator =
        if (baseUrl.contains('?')) {
            '&'
        } else {
            '?'
        }
    return "$baseUrl$separator$encodedParams"
}
