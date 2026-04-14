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

package com.sphereon.ktor.http.client.provider

import com.sphereon.core.compat.JsExportCompat
import io.ktor.http.Url

/**
 * Policy for validating request URLs before they are sent.
 *
 * Install via [HttpClientOptions.urlValidation] to enforce SSRF protection
 * at the HTTP client infrastructure level, rather than in individual consumers.
 *
 * When a request URL violates the policy, the client throws [UrlValidationException]
 * before the request is sent.
 */
@JsExportCompat
data class UrlValidationPolicy(
    /** Allow only these schemes (e.g., "https", "http"). Empty = all allowed. */
    val allowedSchemes: Set<String> = setOf("https", "http"),
    /** Block URLs containing userinfo (user:pass@host). Prevents auth-based SSRF bypasses. */
    val blockUserInfo: Boolean = true,
    /** Block requests to private/internal network addresses (loopback, link-local, metadata endpoints). */
    val blockPrivateNetworks: Boolean = true,
    /** Block RFC 1918 private ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16). */
    val blockRfc1918: Boolean = true,
    /** Block carrier-grade NAT (100.64.0.0/10) and benchmark (198.18.0.0/15) ranges. */
    val blockSharedNetworks: Boolean = true,
    /** Additional hostnames to block (exact match, lowercase). */
    val blockedHosts: Set<String> = emptySet(),
    /** Additional hostname suffixes to block (e.g., ".internal", ".local"). */
    val blockedHostSuffixes: Set<String> = emptySet(),
) {
    /**
     * Validate a URL against this policy.
     * @throws UrlValidationException if the URL violates the policy.
     */
    fun validate(url: Url) {
        // Scheme check
        if (allowedSchemes.isNotEmpty() && url.protocol.name.lowercase() !in allowedSchemes) {
            throw UrlValidationException("Scheme '${url.protocol.name}' is not allowed. Allowed: $allowedSchemes")
        }

        // Userinfo check
        if (blockUserInfo && (url.user != null || url.password != null)) {
            throw UrlValidationException("URLs with userinfo are not allowed")
        }

        val host = url.host.lowercase()
        if (host.isEmpty()) {
            throw UrlValidationException("URL has no host")
        }

        // Exact host block
        if (host in blockedHosts) {
            throw UrlValidationException("Host '$host' is blocked")
        }

        // Suffix block
        for (suffix in blockedHostSuffixes) {
            if (host.endsWith(suffix)) {
                throw UrlValidationException("Host '$host' is blocked (suffix: $suffix)")
            }
        }

        // Network range checks (pass policy flags)
        val ipCheckResult = checkIpRanges(host, blockPrivateNetworks, blockRfc1918, blockSharedNetworks)
        if (ipCheckResult != null) {
            throw UrlValidationException(ipCheckResult)
        }
    }

    companion object {
        /** No URL validation — allows all requests. */
        val NONE =
            UrlValidationPolicy(
                allowedSchemes = emptySet(),
                blockUserInfo = false,
                blockPrivateNetworks = false,
                blockRfc1918 = false,
                blockSharedNetworks = false,
            )

        /** Standard SSRF protection for external-facing HTTP clients. */
        val BLOCK_PRIVATE =
            UrlValidationPolicy(
                allowedSchemes = setOf("https", "http"),
                blockUserInfo = true,
                blockPrivateNetworks = true,
                blockRfc1918 = true,
                blockSharedNetworks = true,
                blockedHosts =
                    setOf(
                        "localhost",
                        "127.0.0.1",
                        "::1",
                        "0.0.0.0",
                        "metadata.google.internal",
                        "169.254.169.254",
                    ),
                blockedHostSuffixes =
                    setOf(
                        ".local",
                        ".internal",
                        ".localhost",
                        ".corp",
                        ".home.arpa",
                    ),
            )
    }
}

/**
 * Thrown when a request URL violates the configured [UrlValidationPolicy].
 */
@JsExportCompat
class UrlValidationException(
    message: String,
) : IllegalArgumentException(message)

/**
 * Check whether a hostname falls in blocked IP ranges.
 * Returns an error message if blocked, null if allowed.
 *
 * Handles IPv4 (including octal/hex-encoded octets), IPv6 loopback and link-local.
 */
internal fun checkIpRanges(
    host: String,
    blockPrivateNetworks: Boolean,
    blockRfc1918: Boolean,
    blockSharedNetworks: Boolean,
): String? {
    val normalized = host.removeSurrounding("[", "]")

    // IPv4
    val parts = normalized.split(".")
    if (parts.size == IPV4_OCTET_COUNT) {
        val octets = parts.mapNotNull { parseIpOctet(it) }
        if (octets.size == IPV4_OCTET_COUNT) {
            val (a, b, _, _) = octets

            if (blockPrivateNetworks) {
                if (a == IP_LOOPBACK) {
                    return "Host '$host' is in loopback range (127.0.0.0/8)"
                }
                if (a == 0) {
                    return "Host '$host' is in reserved range (0.0.0.0/8)"
                }
                if (a == IP_LINK_LOCAL_A && b == IP_LINK_LOCAL_B) {
                    return "Host '$host' is in link-local range (169.254.0.0/16)"
                }
            }

            if (blockRfc1918) {
                if (a == IP_PRIVATE_CLASS_A) {
                    return "Host '$host' is in private range (10.0.0.0/8)"
                }
                if (a == IP_PRIVATE_CLASS_B && b in IP_PRIVATE_B_RANGE) {
                    return "Host '$host' is in private range (172.16.0.0/12)"
                }
                if (a == IP_PRIVATE_CLASS_C && b == IP_PRIVATE_C_SUBNET) {
                    return "Host '$host' is in private range (192.168.0.0/16)"
                }
            }

            if (blockSharedNetworks) {
                if (a == IP_CGNAT_A && b in IP_CGNAT_B_RANGE) {
                    return "Host '$host' is in carrier-grade NAT range (100.64.0.0/10)"
                }
                if (a == IP_BENCHMARK_A && b in IP_BENCHMARK_B_RANGE) {
                    return "Host '$host' is in benchmark range (198.18.0.0/15)"
                }
            }
        }
    }

    // IPv6 checks (always if blockPrivateNetworks)
    if (blockPrivateNetworks) {
        val lower = normalized.lowercase()
        if (lower == "::1") {
            return "Host '$host' is IPv6 loopback"
        }
        if (lower.startsWith("fe80:")) {
            return "Host '$host' is IPv6 link-local"
        }
        if (lower.startsWith("fc") || lower.startsWith("fd")) {
            return "Host '$host' is IPv6 unique-local"
        }
    }

    return null
}

// IPv4 address range constants
private const val IPV4_OCTET_COUNT = 4
private const val IP_LOOPBACK = 127
private const val IP_LINK_LOCAL_A = 169
private const val IP_LINK_LOCAL_B = 254
private const val IP_PRIVATE_CLASS_A = 10
private const val IP_PRIVATE_CLASS_B = 172
private val IP_PRIVATE_B_RANGE = 16..31
private const val IP_PRIVATE_CLASS_C = 192
private const val IP_PRIVATE_C_SUBNET = 168
private const val IP_CGNAT_A = 100
private val IP_CGNAT_B_RANGE = 64..127
private const val IP_BENCHMARK_A = 198
private val IP_BENCHMARK_B_RANGE = 18..19
private const val HEX_RADIX = 16
private const val OCTAL_RADIX = 8
private const val MAX_OCTET_VALUE = 255
private const val HEX_PREFIX_LENGTH = 2

/** Parse an IP octet supporting decimal, 0x hex, and 0-prefixed octal. */
private fun parseIpOctet(s: String): Int? =
    try {
        when {
            s.startsWith("0x", ignoreCase = true) -> s.substring(HEX_PREFIX_LENGTH).toInt(HEX_RADIX)
            s.startsWith("0") && s.length > 1 -> s.toInt(OCTAL_RADIX)
            else -> s.toInt()
        }.takeIf { it in 0..MAX_OCTET_VALUE }
    } catch (_: NumberFormatException) {
        null
    }
