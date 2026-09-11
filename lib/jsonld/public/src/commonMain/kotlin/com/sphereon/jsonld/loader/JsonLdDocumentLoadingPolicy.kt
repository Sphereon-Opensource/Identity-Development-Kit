/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.jsonld.loader

import com.sphereon.jsonld.Iri

/**
 * Policy consulted before resolving a JSON-LD remote document and again for
 * the document URL returned by a loader. Context documents are executable
 * processing input: an implementation must not silently trust arbitrary
 * network locations or a redirect to a different authority.
 *
 * Implementations may consult tenant configuration, an application trust
 * store, or an offline allowlist. The policy is deliberately asynchronous so
 * those implementations remain cross-platform and non-blocking.
 */
fun interface JsonLdDocumentLoadingPolicy {
    /** Returns true only when [iri] is permitted for JSON-LD processing. */
    suspend fun isAllowed(iri: String): Boolean

    companion object {
        /** A policy backed by an immutable exact-IRI allowlist. */
        fun allowOnly(iris: Set<String>): JsonLdDocumentLoadingPolicy {
            // Normalize only syntax which is case-insensitive by URI rules
            // (scheme and host). Ports, paths, queries, fragments, and
            // percent-encoding remain exact. Invalid or userinfo-bearing
            // entries are ignored and can never authorize a request.
            val allowed = iris.mapNotNull(::normalizedPolicyIri).toSet()
            return JsonLdDocumentLoadingPolicy { iri ->
                normalizedPolicyIri(iri)?.let { it in allowed } == true
            }
        }

        /** Explicit opt-in for isolated callers that already enforce policy. */
        val ALLOW_ALL: JsonLdDocumentLoadingPolicy = JsonLdDocumentLoadingPolicy { true }
    }
}

private fun normalizedPolicyIri(raw: String): String? {
    val iri = Iri.tryParse(raw) ?: return null
    if (!iri.isAbsolute || iri.authority?.contains('@') == true) return null

    val scheme = iri.scheme?.lowercase() ?: return null
    val rawAuthority = iri.authority
    val authority = if (rawAuthority == null) null else normalizeAuthority(rawAuthority) ?: return null
    val prefix = if (authority == null) "$scheme:" else "$scheme://$authority"
    return buildString {
        append(prefix)
        append(iri.path)
        iri.query?.let { append('?').append(it) }
        iri.fragment?.let { append('#').append(it) }
    }
}

private fun normalizeAuthority(authority: String): String? {
    if (authority.isEmpty() || authority.contains('@')) return null
    if (authority.startsWith('[')) {
        val end = authority.indexOf(']')
        if (end <= 1) return null
        val suffix = authority.substring(end + 1)
        if (suffix.isNotEmpty() && !suffix.startsWith(':')) return null
        return authority.substring(0, end + 1).lowercase() + suffix
    }
    val colon = authority.lastIndexOf(':')
    val hasSinglePortSeparator = colon >= 0 && authority.indexOf(':') == colon
    val host = if (hasSinglePortSeparator) authority.substring(0, colon) else authority
    val port = if (hasSinglePortSeparator) authority.substring(colon) else ""
    if (host.isEmpty()) return null
    return host.lowercase() + port
}
