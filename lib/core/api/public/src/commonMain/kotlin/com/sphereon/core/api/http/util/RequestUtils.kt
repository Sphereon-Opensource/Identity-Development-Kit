/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.core.api.http.util

import com.sphereon.core.api.auth.AuthHeaders
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter

/**
 * Framework-agnostic HTTP request utilities.
 *
 * Provides proxy-aware header extraction and URL construction for services
 * running behind reverse proxies like Traefik, nginx, or cloud load balancers.
 *
 * All header lookups are case-insensitive (per HTTP/1.1 RFC 7230 §3.2).
 */
object RequestUtils {
    private const val HTTP_SCHEME = "http"
    private const val HTTPS_SCHEME = "https"

    // ========================================
    // Header extraction
    // ========================================

    /**
     * Extract a header value with case-insensitive lookup.
     * Tries direct key access first (fast path), then iterates entries for a case-insensitive match.
     */
    fun extractHeaderValue(
        headers: Map<String, String>,
        name: String
    ): String? {
        headers[name]?.let { return it }
        val lowerName = name.lowercase()
        return headers.entries.firstOrNull { it.key.lowercase() == lowerName }?.value
    }

    /**
     * Extract the resolved base tenant ID from request headers.
     *
     * Reads `__sphereon_internal_base_tenant__` (the in-process header stamped
     * by the Layer 1 tenant-resolution pipeline). `X-Tenant-Id` from the wire
     * is **never** consulted here — it is information-only and must never
     * influence auth/tenant decisions. The Layer 1 pipeline resolves tenant
     * exclusively from the validated JWT, the Host header, or the configured
     * fallback; the result is propagated as the internal header by the
     * REST adapter when it builds the [GenericHttpRequest].
     */
    fun extractTenantId(headers: Map<String, String>): String? =
        extractHeaderValue(headers, CommandBackedHttpAdapter.INTERNAL_BASE_TENANT_HEADER)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    // ========================================
    // Proxy-aware request properties
    // ========================================

    /**
     * Resolve the request scheme (protocol), preferring the proxy-forwarded value.
     *
     * Resolution order:
     * 1. `X-Forwarded-Proto` header (set by reverse proxies), accepting only http/https
     *    and normalizing ws/wss to their HTTP equivalents
     * 2. Port-based inference from `Host` header (443 → https)
     * 3. [defaultScheme] fallback when valid, otherwise http
     */
    fun getScheme(
        headers: Map<String, String>,
        defaultScheme: String = "http"
    ): String {
        extractHeaderValue(headers, AuthHeaders.X_FORWARDED_PROTO)
            ?.firstHeaderValue()
            ?.let(::normalizeScheme)
            ?.let { return it }

        // Infer from Host port when no forwarding header is present
        val host = extractHeaderValue(headers, AuthHeaders.HOST)?.firstHeaderValue()
        if (host != null) {
            val port = host.substringAfter(":", "")
            if (port.endsWith("443")) return HTTPS_SCHEME
        }

        return normalizeScheme(defaultScheme) ?: HTTP_SCHEME
    }

    /**
     * Resolve the request host, preferring the proxy-forwarded value.
     *
     * Checks `X-Forwarded-Host` first, then falls back to the `Host` header.
     * Returns "localhost" if neither is present.
     */
    fun getHostWithPort(headers: Map<String, String>): String =
        extractHeaderValue(headers, AuthHeaders.X_FORWARDED_HOST)
            ?.firstHeaderValue()
            ?: extractHeaderValue(headers, AuthHeaders.HOST)?.firstHeaderValue()
            ?: "localhost"

    /**
     * Resolve the request hostname without any port suffix.
     *
     * This strips `:port` from regular host headers and unwraps bracketed IPv6
     * host values such as `[2001:db8::1]:8443`.
     */
    fun getHostname(headers: Map<String, String>): String = parseHostHeader(getHostWithPort(headers)).hostname

    /**
     * Resolve the forwarded port, if any.
     *
     * Returns null when no `X-Forwarded-Port` header is present, or when
     * the port is the default for the scheme (443 for https, 80 for http).
     */
    fun getPort(headers: Map<String, String>): String? {
        val port =
            extractHeaderValue(headers, AuthHeaders.X_FORWARDED_PORT)
                ?.firstHeaderValue()
                ?: return null
        val scheme = getScheme(headers)
        // Suppress default ports
        if (scheme == "https" && port == "443") return null
        if (scheme == "http" && port == "80") return null
        return port
    }

    /**
     * Resolve the forwarded path prefix, if any.
     *
     * When a reverse proxy strips or rewrites a path prefix, it sets
     * `X-Forwarded-Prefix` so the backend can reconstruct the original URL.
     * The returned value has no trailing slash.
     */
    fun getPrefix(headers: Map<String, String>): String =
        extractHeaderValue(headers, AuthHeaders.X_FORWARDED_PREFIX)
            ?.firstHeaderValue()
            ?.let {
                val trimmed = it.trim().trimEnd('/')
                when {
                    trimmed.isEmpty() -> ""
                    trimmed.startsWith("/") -> trimmed
                    else -> "/$trimmed"
                }
            }
            ?: ""

    /**
     * Resolve the client IP address from proxy headers.
     *
     * Takes the first address from `X-Forwarded-For` (the original client),
     * ignoring any intermediate proxies in the chain.
     */
    fun getClientIp(headers: Map<String, String>): String? =
        extractHeaderValue(headers, AuthHeaders.X_FORWARDED_FOR)
            ?.split(",")
            ?.firstOrNull()
            ?.trim()

    // ========================================
    // URL construction
    // ========================================

    /**
     * Build the host authority (host + non-default port) from proxy-aware headers.
     *
     * Examples:
     * - `X-Forwarded-Host: example.com`, scheme=https → `example.com`
     * - `X-Forwarded-Host: example.com`, `X-Forwarded-Port: 8443`, scheme=https → `example.com:8443`
     * - `Host: service-sts:8092` (no forwarding headers) → `service-sts:8092`
     */
    fun getAuthority(headers: Map<String, String>): String {
        val hostHeader = getHostWithPort(headers)
        val parsedHost = parseHostHeader(hostHeader)
        val forwardedPort = getPort(headers)
        return if (forwardedPort != null) {
            formatAuthority(parsedHost.hostname, forwardedPort)
        } else {
            hostHeader
        }
    }

    /**
     * Build a proxy-aware base URL (scheme + authority + prefix, no trailing slash).
     *
     * This is the external-facing origin of the service as seen by the client.
     * Equivalent to the TypeScript pattern:
     * ```
     * `${protocol}://${host}${forwardedPrefix}${baseUrl}`
     * ```
     *
     * @param headers Request headers (case-insensitive lookup)
     * @param basePath Optional application base path appended after the prefix (e.g. "/api/v1")
     */
    fun buildBaseUrl(
        headers: Map<String, String>,
        basePath: String = ""
    ): String {
        val scheme = getScheme(headers)
        val authority = getAuthority(headers)
        val prefix = getPrefix(headers)
        val normalizedBase = basePath.trimEnd('/')
        return "$scheme://$authority$prefix$normalizedBase"
    }

    /**
     * Build a proxy-aware full URL for the given request path.
     *
     * Combines [buildBaseUrl] with the request path to reconstruct the
     * URL as the client originally requested it (before the proxy rewrote it).
     *
     * @param headers Request headers
     * @param path The request path (e.g. "/oauth2/token")
     * @param basePath Optional application base path
     */
    fun buildFullUrl(
        headers: Map<String, String>,
        path: String,
        basePath: String = ""
    ): String {
        val base = buildBaseUrl(headers, basePath)
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return "$base$normalizedPath"
    }
}

private fun normalizeScheme(scheme: String): String? =
    when (scheme.lowercase().trim()) {
        "http", "ws" -> "http"
        "https", "wss" -> "https"
        else -> null
    }

private fun String.firstHeaderValue(): String? = split(",").firstOrNull()?.trim()?.takeIf { it.isNotBlank() }

private data class ParsedHostHeader(
    val hostname: String,
    val hasExplicitPort: Boolean
)

private fun parseHostHeader(hostHeader: String): ParsedHostHeader {
    val value = hostHeader.trim()
    if (value.startsWith("[")) {
        val endBracket = value.indexOf(']')
        if (endBracket > 0) {
            val hostname = value.substring(1, endBracket)
            val hasExplicitPort = value.drop(endBracket + 1).startsWith(":")
            return ParsedHostHeader(hostname = hostname, hasExplicitPort = hasExplicitPort)
        }
    }

    val colonCount = value.count { it == ':' }
    if (colonCount == 1) {
        val separator = value.lastIndexOf(':')
        val suffix = value.substring(separator + 1)
        if (suffix.all { it.isDigit() }) {
            return ParsedHostHeader(
                hostname = value.substring(0, separator),
                hasExplicitPort = true
            )
        }
    }

    return ParsedHostHeader(hostname = value, hasExplicitPort = false)
}

private fun formatAuthority(
    hostname: String,
    port: String
): String =
    if (hostname.contains(':') && !hostname.startsWith("[")) {
        "[$hostname]:$port"
    } else {
        "$hostname:$port"
    }
