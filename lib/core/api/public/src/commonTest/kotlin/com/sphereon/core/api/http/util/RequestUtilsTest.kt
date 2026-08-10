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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// ========================================
// extractHeaderValue
// ========================================

class ExtractHeaderValueTest {
    @Test
    fun exactCaseMatch() {
        val headers = mapOf("X-Tenant-Id" to "t1")
        assertEquals("t1", RequestUtils.extractHeaderValue(headers, "X-Tenant-Id"))
    }

    @Test
    fun lowercaseKeyInMap() {
        val headers = mapOf("x-tenant-id" to "t1")
        assertEquals("t1", RequestUtils.extractHeaderValue(headers, "X-Tenant-Id"))
    }

    @Test
    fun uppercaseKeyInMap() {
        val headers = mapOf("X-TENANT-ID" to "t1")
        assertEquals("t1", RequestUtils.extractHeaderValue(headers, "X-Tenant-Id"))
    }

    @Test
    fun mixedCaseKeyInMap() {
        val headers = mapOf("x-Tenant-id" to "t1")
        assertEquals("t1", RequestUtils.extractHeaderValue(headers, "X-Tenant-Id"))
    }

    @Test
    fun returnsNullWhenMissing() {
        val headers = mapOf("Authorization" to "Bearer tok")
        assertNull(RequestUtils.extractHeaderValue(headers, "X-Tenant-Id"))
    }

    @Test
    fun returnsNullForEmptyHeaders() {
        assertNull(RequestUtils.extractHeaderValue(emptyMap(), "Host"))
    }

    @Test
    fun prefersExactCaseWhenBothPresent() {
        // Fast-path (direct key access) returns before iterating
        val headers =
            mapOf(
                "Host" to "exact",
                "host" to "lowercase"
            )
        assertEquals("exact", RequestUtils.extractHeaderValue(headers, "Host"))
    }
}

// ========================================
// getScheme
// ========================================

class GetSchemeTest {
    @Test
    fun returnsForwardedProto() {
        val headers = mapOf("X-Forwarded-Proto" to "https")
        assertEquals("https", RequestUtils.getScheme(headers))
    }

    @Test
    fun forwardedProtoCaseInsensitive() {
        val headers = mapOf("x-forwarded-proto" to "HTTPS")
        assertEquals("https", RequestUtils.getScheme(headers))
    }

    @Test
    fun normalizesWebSocketProtoToHttp() {
        val headers = mapOf("X-Forwarded-Proto" to "ws")
        assertEquals("http", RequestUtils.getScheme(headers))
    }

    @Test
    fun normalizesSecureWebSocketProtoToHttps() {
        val headers = mapOf("X-Forwarded-Proto" to "WSS")
        assertEquals("https", RequestUtils.getScheme(headers))
    }

    @Test
    fun usesFirstForwardedProtoFromCombinedHeader() {
        val headers = mapOf("X-Forwarded-Proto" to "https, http")
        assertEquals("https", RequestUtils.getScheme(headers))
    }

    @Test
    fun infersHttpsFromHostPort443() {
        val headers = mapOf("Host" to "example.com:443")
        assertEquals("https", RequestUtils.getScheme(headers))
    }

    @Test
    fun infersHttpsFromFirstCombinedHostValue() {
        val headers = mapOf("Host" to "example.com:443, internal:8080")
        assertEquals("https", RequestUtils.getScheme(headers))
    }

    @Test
    fun returnsDefaultSchemeWhenNoHeaders() {
        assertEquals("http", RequestUtils.getScheme(emptyMap()))
    }

    @Test
    fun returnsCustomDefault() {
        assertEquals("https", RequestUtils.getScheme(emptyMap(), defaultScheme = "https"))
    }

    @Test
    fun fallsBackWhenForwardedProtoIsInvalid() {
        val headers =
            mapOf(
                "X-Forwarded-Proto" to "ftp",
                "Host" to "example.com:443"
            )
        assertEquals("https", RequestUtils.getScheme(headers))
    }

    @Test
    fun fallsBackToHttpWhenDefaultSchemeIsInvalid() {
        assertEquals("http", RequestUtils.getScheme(emptyMap(), defaultScheme = "ftp"))
    }

    @Test
    fun forwardedProtoTakesPriorityOverHostPort() {
        val headers =
            mapOf(
                "X-Forwarded-Proto" to "http",
                "Host" to "example.com:443"
            )
        assertEquals("http", RequestUtils.getScheme(headers))
    }
}

// ========================================
// getHost
// ========================================

class GetHostTest {
    @Test
    fun returnsForwardedHost() {
        val headers =
            mapOf(
                "X-Forwarded-Host" to "public.example.com",
                "Host" to "internal:8080"
            )
        assertEquals("public.example.com", RequestUtils.getHostWithPort(headers))
    }

    @Test
    fun fallsBackToHostHeader() {
        val headers = mapOf("Host" to "service:8080")
        assertEquals("service:8080", RequestUtils.getHostWithPort(headers))
    }

    @Test
    fun returnsLocalhostWhenNoHeaders() {
        assertEquals("localhost", RequestUtils.getHostWithPort(emptyMap()))
    }

    @Test
    fun forwardedHostCaseInsensitive() {
        val headers = mapOf("x-forwarded-host" to "proxy.example.com")
        assertEquals("proxy.example.com", RequestUtils.getHostWithPort(headers))
    }

    @Test
    fun usesFirstForwardedHostFromCombinedHeader() {
        val headers = mapOf("X-Forwarded-Host" to "public.example.com, internal:8080")
        assertEquals("public.example.com", RequestUtils.getHostWithPort(headers))
    }

    @Test
    fun stripsPortFromHostname() {
        val headers = mapOf("Host" to "service:8080")
        assertEquals("service", RequestUtils.getHostname(headers))
    }

    @Test
    fun stripsPortAndBracketsFromIpv6Hostname() {
        val headers = mapOf("Host" to "[2001:db8::1]:8443")
        assertEquals("2001:db8::1", RequestUtils.getHostname(headers))
    }

    @Test
    fun keepsIpv6HostnameWithoutPort() {
        val headers = mapOf("Host" to "2001:db8::1")
        assertEquals("2001:db8::1", RequestUtils.getHostname(headers))
    }
}

// ========================================
// getPort
// ========================================

class GetPortTest {
    @Test
    fun returnsForwardedPort() {
        val headers =
            mapOf(
                "X-Forwarded-Port" to "8443",
                "X-Forwarded-Proto" to "https"
            )
        assertEquals("8443", RequestUtils.getPort(headers))
    }

    @Test
    fun suppressesDefaultHttpsPort() {
        val headers =
            mapOf(
                "X-Forwarded-Port" to "443",
                "X-Forwarded-Proto" to "https"
            )
        assertNull(RequestUtils.getPort(headers))
    }

    @Test
    fun suppressesDefaultHttpPort() {
        val headers =
            mapOf(
                "X-Forwarded-Port" to "80",
                "X-Forwarded-Proto" to "http"
            )
        assertNull(RequestUtils.getPort(headers))
    }

    @Test
    fun returnsNullWhenMissing() {
        assertNull(RequestUtils.getPort(emptyMap()))
    }

    @Test
    fun ignoresBlankValue() {
        val headers = mapOf("X-Forwarded-Port" to "   ")
        assertNull(RequestUtils.getPort(headers))
    }

    @Test
    fun usesFirstForwardedPortFromCombinedHeader() {
        val headers =
            mapOf(
                "X-Forwarded-Port" to "8443, 8080",
                "X-Forwarded-Proto" to "https"
            )
        assertEquals("8443", RequestUtils.getPort(headers))
    }
}

// ========================================
// getPrefix
// ========================================

class GetPrefixTest {
    @Test
    fun returnsForwardedPrefix() {
        val headers = mapOf("X-Forwarded-Prefix" to "/api/v1")
        assertEquals("/api/v1", RequestUtils.getPrefix(headers))
    }

    @Test
    fun stripsTrailingSlash() {
        val headers = mapOf("X-Forwarded-Prefix" to "/api/v1/")
        assertEquals("/api/v1", RequestUtils.getPrefix(headers))
    }

    @Test
    fun returnsEmptyWhenMissing() {
        assertEquals("", RequestUtils.getPrefix(emptyMap()))
    }

    @Test
    fun usesFirstForwardedPrefixFromCombinedHeader() {
        val headers = mapOf("X-Forwarded-Prefix" to "/api/v1, /internal")
        assertEquals("/api/v1", RequestUtils.getPrefix(headers))
    }

    @Test
    fun prependsLeadingSlashWhenMissing() {
        val headers = mapOf("X-Forwarded-Prefix" to "api/v1")
        assertEquals("/api/v1", RequestUtils.getPrefix(headers))
    }

    @Test
    fun trimsWhitespaceBeforeNormalizingPrefix() {
        val headers = mapOf("X-Forwarded-Prefix" to "  api/v1/  ")
        assertEquals("/api/v1", RequestUtils.getPrefix(headers))
    }
}

// ========================================
// getClientIp
// ========================================

class GetClientIpTest {
    @Test
    fun returnsSingleIp() {
        val headers = mapOf("X-Forwarded-For" to "203.0.113.50")
        assertEquals("203.0.113.50", RequestUtils.getClientIp(headers))
    }

    @Test
    fun returnsFirstIpFromChain() {
        val headers = mapOf("X-Forwarded-For" to "203.0.113.50, 70.41.3.18, 150.172.238.178")
        assertEquals("203.0.113.50", RequestUtils.getClientIp(headers))
    }

    @Test
    fun returnsNullWhenMissing() {
        assertNull(RequestUtils.getClientIp(emptyMap()))
    }
}

// ========================================
// getAuthority
// ========================================

class GetAuthorityTest {
    @Test
    fun hostWithForwardedPort() {
        val headers =
            mapOf(
                "X-Forwarded-Host" to "example.com",
                "X-Forwarded-Port" to "8443",
                "X-Forwarded-Proto" to "https"
            )
        assertEquals("example.com:8443", RequestUtils.getAuthority(headers))
    }

    @Test
    fun hostWithDefaultPortSuppressed() {
        val headers =
            mapOf(
                "X-Forwarded-Host" to "example.com",
                "X-Forwarded-Port" to "443",
                "X-Forwarded-Proto" to "https"
            )
        assertEquals("example.com", RequestUtils.getAuthority(headers))
    }

    @Test
    fun hostAlreadyContainsPort() {
        val headers = mapOf("Host" to "service:8092")
        assertEquals("service:8092", RequestUtils.getAuthority(headers))
    }

    @Test
    fun forwardedPortOverridesPortInHostHeader() {
        val headers =
            mapOf(
                "Host" to "internal-service:8080",
                "X-Forwarded-Port" to "8443",
                "X-Forwarded-Proto" to "https"
            )
        assertEquals("internal-service:8443", RequestUtils.getAuthority(headers))
    }

    @Test
    fun plainHostNoPort() {
        val headers = mapOf("Host" to "example.com")
        assertEquals("example.com", RequestUtils.getAuthority(headers))
    }

    @Test
    fun bracketedIpv6HostGetsForwardedPort() {
        val headers =
            mapOf(
                "Host" to "[2001:db8::1]",
                "X-Forwarded-Port" to "8443",
                "X-Forwarded-Proto" to "https"
            )
        assertEquals("[2001:db8::1]:8443", RequestUtils.getAuthority(headers))
    }

    @Test
    fun forwardedPortOverridesPortInBracketedIpv6HostHeader() {
        val headers =
            mapOf(
                "Host" to "[2001:db8::1]:8080",
                "X-Forwarded-Port" to "8443",
                "X-Forwarded-Proto" to "https"
            )
        assertEquals("[2001:db8::1]:8443", RequestUtils.getAuthority(headers))
    }
}

// ========================================
// buildBaseUrl
// ========================================

class BuildBaseUrlTest {
    @Test
    fun simpleProxy() {
        val headers =
            mapOf(
                "X-Forwarded-Proto" to "https",
                "X-Forwarded-Host" to "example.com"
            )
        assertEquals("https://example.com", RequestUtils.buildBaseUrl(headers))
    }

    @Test
    fun includesNonDefaultForwardedPort() {
        val headers =
            mapOf(
                "X-Forwarded-Proto" to "https",
                "X-Forwarded-Host" to "example.com",
                "X-Forwarded-Port" to "8443"
            )
        assertEquals("https://example.com:8443", RequestUtils.buildBaseUrl(headers))
    }

    @Test
    fun forwardedPortOverridesInternalHostPortInBaseUrl() {
        val headers =
            mapOf(
                "Host" to "internal-service:8080",
                "X-Forwarded-Proto" to "https",
                "X-Forwarded-Port" to "8443"
            )
        assertEquals("https://internal-service:8443", RequestUtils.buildBaseUrl(headers))
    }

    @Test
    fun proxyWithPrefixAndBasePath() {
        val headers =
            mapOf(
                "X-Forwarded-Proto" to "https",
                "X-Forwarded-Host" to "example.com",
                "X-Forwarded-Prefix" to "/proxy"
            )
        assertEquals("https://example.com/proxy/api/v1", RequestUtils.buildBaseUrl(headers, basePath = "/api/v1"))
    }

    @Test
    fun normalizesPrefixWithoutLeadingSlash() {
        val headers =
            mapOf(
                "X-Forwarded-Proto" to "https",
                "X-Forwarded-Host" to "example.com",
                "X-Forwarded-Prefix" to "api/v1"
            )
        assertEquals("https://example.com/api/v1", RequestUtils.buildBaseUrl(headers))
    }

    @Test
    fun noProxyHeaders() {
        val headers = mapOf("Host" to "localhost:8080")
        assertEquals("http://localhost:8080", RequestUtils.buildBaseUrl(headers))
    }

    @Test
    fun basePathTrailingSlashStripped() {
        val headers = mapOf("Host" to "localhost:8080")
        assertEquals("http://localhost:8080/api", RequestUtils.buildBaseUrl(headers, basePath = "/api/"))
    }
}

// ========================================
// buildFullUrl
// ========================================

class BuildFullUrlTest {
    @Test
    fun fullUrlBehindProxy() {
        val headers =
            mapOf(
                "X-Forwarded-Proto" to "https",
                "X-Forwarded-Host" to "example.com",
                "X-Forwarded-Prefix" to "/proxy"
            )
        assertEquals(
            "https://example.com/proxy/oauth2/token",
            RequestUtils.buildFullUrl(headers, path = "/oauth2/token")
        )
    }

    @Test
    fun pathWithoutLeadingSlash() {
        val headers = mapOf("Host" to "localhost:8080")
        assertEquals(
            "http://localhost:8080/health",
            RequestUtils.buildFullUrl(headers, path = "health")
        )
    }

    @Test
    fun fullUrlWithBasePath() {
        val headers =
            mapOf(
                "X-Forwarded-Proto" to "https",
                "X-Forwarded-Host" to "api.example.com"
            )
        assertEquals(
            "https://api.example.com/api/v1/users",
            RequestUtils.buildFullUrl(headers, path = "/users", basePath = "/api/v1")
        )
    }
}
