/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.resolver

import com.sphereon.ktor.http.client.provider.UrlValidationException
import com.sphereon.ktor.http.client.provider.UrlValidationPolicy
import io.ktor.http.Url

/**
 * Boundary policy for externally retrieved ETSI trust lists.
 *
 * URI validation is performed before cache lookup and before every redirect.
 * DNS/address validation is performed by the resolver-owned transport before
 * the request is sent; this policy only handles URI syntax and literal URL
 * policy checks.
 */
internal object TrustListUrlPolicy {
    private val blockedInternalSuffixes =
        setOf(".internal", ".local", ".localhost", ".corp", ".home.arpa")

    fun validate(
        uri: String,
        requireHttps: Boolean = true,
    ): String {
        val url = parse(uri)

        try {
            UrlValidationPolicy(
                allowedSchemes = if (requireHttps) setOf("https") else setOf("https", "http"),
                blockUserInfo = true,
                blockPrivateNetworks = true,
                blockRfc1918 = true,
                blockSharedNetworks = true,
                blockedHosts =
                    setOf(
                        "localhost",
                        "metadata.google.internal",
                        "metadata.azure.internal",
                        "instance-data.ec2.internal",
                        "internal",
                    ),
                blockedHostSuffixes = blockedInternalSuffixes,
            ).validate(url)
        } catch (expected: UrlValidationException) {
            val reasonCode =
                if (expected.message?.contains("range", ignoreCase = true) == true ||
                    expected.message?.contains("blocked", ignoreCase = true) == true
                ) {
                    com.sphereon.trust.core.TrustDiagnosticReasonCodes.TRUST_LIST_ADDRESS_BLOCKED
                } else {
                    com.sphereon.trust.core.TrustDiagnosticReasonCodes.TRUST_LIST_URL_REJECTED
                }
            throw TrustListResolutionException("Trust-list URL was rejected", expected, reasonCode)
        }

        return canonicalUri(url)
    }

    fun normalizedCacheKey(uri: String): String {
        return canonicalUri(parse(uri))
    }

    fun resolveRedirect(
        currentUri: String,
        location: String?,
        requireHttps: Boolean,
    ): String {
        val current = parse(currentUri)
        if (location == null || location.isBlank()) {
            throw TrustListResolutionException(
                "Trust-list redirect did not provide a Location header",
                reasonCode = com.sphereon.trust.core.TrustDiagnosticReasonCodes.TRUST_LIST_REDIRECT_MISSING,
            )
        }

        val locationWithoutFragment = location.trim().substringBefore('#')
        val target =
            try {
                resolveRelativeTarget(current, locationWithoutFragment)
            } catch (expected: TrustListResolutionException) {
                throw expected
            } catch (expected: Exception) {
                throw TrustListResolutionException(
                    "Trust-list redirect location was malformed",
                    expected,
                    com.sphereon.trust.core.TrustDiagnosticReasonCodes.TRUST_LIST_REDIRECT_MALFORMED,
                )
            }

        val targetUrl =
            try {
                parse(target)
            } catch (expected: TrustListResolutionException) {
                throw TrustListResolutionException(
                    "Trust-list redirect location was malformed",
                    expected,
                    com.sphereon.trust.core.TrustDiagnosticReasonCodes.TRUST_LIST_REDIRECT_MALFORMED,
                )
            }

        if (requireHttps &&
            current.protocol.name.equals("https", ignoreCase = true) &&
            targetUrl.protocol.name.equals("http", ignoreCase = true)
        ) {
            throw TrustListResolutionException(
                "Trust-list redirect would downgrade HTTPS",
                reasonCode = com.sphereon.trust.core.TrustDiagnosticReasonCodes.TRUST_LIST_REDIRECT_DOWNGRADE,
            )
        }

        return try {
            validate(target, requireHttps)
        } catch (expected: TrustListResolutionException) {
            if (expected.reasonCode == com.sphereon.trust.core.TrustDiagnosticReasonCodes.TRUST_LIST_URL_MALFORMED) {
                throw TrustListResolutionException(
                    "Trust-list redirect location was malformed",
                    expected,
                    com.sphereon.trust.core.TrustDiagnosticReasonCodes.TRUST_LIST_REDIRECT_MALFORMED,
                )
            }
            throw expected
        }
    }

    private fun parse(uri: String): Url =
        try {
            Url(uri).also {
                require(it.host.isNotBlank() && '[' !in it.host && ']' !in it.host)
            }
        } catch (expected: Exception) {
            throw TrustListResolutionException(
                "Trust-list URL is malformed",
                expected,
                com.sphereon.trust.core.TrustDiagnosticReasonCodes.TRUST_LIST_URL_MALFORMED,
            )
        }

    private fun canonicalUri(url: Url): String {
        val scheme = url.protocol.name.lowercase()
        val defaultPort = if (scheme == "https") 443 else 80
        val host = if (url.host.contains(':')) "[${url.host.lowercase()}]" else url.host.lowercase()
        val port = if (url.port == defaultPort) "" else ":${url.port}"
        val path = url.encodedPath.ifEmpty { "/" }
        val query = url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: ""
        return "$scheme://$host$port$path$query"
    }

    private fun resolveRelativeTarget(
        current: Url,
        location: String,
    ): String {
        if (location.isEmpty()) return canonicalUri(current)
        if (location.startsWith("//")) return "${current.protocol.name}:$location"
        if (SCHEME_PREFIX.containsMatchIn(location)) return location

        val base = canonicalUri(current)
        val originEnd = base.indexOf('/', startIndex = base.indexOf("://") + 3)
        val origin = if (originEnd < 0) base else base.substring(0, originEnd)
        if (location.startsWith('?')) {
            return "$origin${current.encodedPath.ifEmpty { "/" }}$location"
        }

        val queryStart = location.indexOf('?')
        val pathPart = if (queryStart < 0) location else location.substring(0, queryStart)
        val queryPart = if (queryStart < 0) "" else location.substring(queryStart)
        val resolvedPath =
            if (pathPart.startsWith('/')) {
                normalizePath(pathPart)
            } else {
                val currentPath = current.encodedPath.ifEmpty { "/" }
                val baseDirectory = currentPath.substringBeforeLast('/', missingDelimiterValue = "") + "/"
                normalizePath(baseDirectory + pathPart)
            }
        return "$origin$resolvedPath$queryPart"
    }

    private fun normalizePath(path: String): String {
        val output = ArrayDeque<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (output.isNotEmpty()) output.removeLast()
                else -> output.addLast(segment)
            }
        }
        val normalized = "/" + output.joinToString("/")
        return if (path.endsWith('/') && normalized != "/") "$normalized/" else normalized
    }

    private val SCHEME_PREFIX = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
}
