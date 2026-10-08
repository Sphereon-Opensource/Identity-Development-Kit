/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.redirect

import com.sphereon.core.compat.JsExportCompat

/**
 * Whether a requested redirect URI is covered by a client's registered redirect URIs.
 *
 * This lives in the public module so every surface that hands a user to a redirect target checks
 * it the same way. The authorize endpoint uses it for `redirect_uri`; account-action links use it
 * for their post-completion destination. A second implementation of this rule would be a place
 * for an open redirect to appear.
 *
 * Fragment components are forbidden on a redirect URI by RFC 6749 §3.1.2 and are rejected at the
 * client registry, so no case is made for them here.
 */
@JsExportCompat
object RedirectUriMatching {
    /**
     * Two-tier match:
     *
     * 1. Byte-equal against a registered entry, which FAPI2-SP §5.3.2.2 requires.
     * 2. Otherwise scheme, authority and path must match a registered entry that itself carries
     *    no query, which is the RFC 6749 §3.1.2.2 allowance for a client passing per-request
     *    state through query parameters.
     *
     * Returns false for an empty [registered] set: an unregistered client redirects nowhere.
     */
    fun matches(requested: String, registered: Collection<String>): Boolean {
        if (registered.isEmpty()) return false
        if (requested in registered) return true
        val requestedParts = split(requested) ?: return false
        return registered.any { entry ->
            val entryParts = split(entry) ?: return@any false
            entryParts.query.isEmpty() &&
                entryParts.scheme.equals(requestedParts.scheme, ignoreCase = true) &&
                entryParts.authority.equals(requestedParts.authority, ignoreCase = true) &&
                entryParts.path == requestedParts.path
        }
    }

    private data class UriParts(
        val scheme: String,
        val authority: String,
        val path: String,
        val query: String,
    )

    private fun split(uri: String): UriParts? {
        val schemeIdx = uri.indexOf("://")
        if (schemeIdx <= 0) return null
        val scheme = uri.substring(0, schemeIdx)
        val rest = uri.substring(schemeIdx + 3)
        val pathStart = rest.indexOf('/').let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, pathStart)
        val pathAndQuery = rest.substring(pathStart)
        val queryIdx = pathAndQuery.indexOf('?')
        val path = if (queryIdx < 0) pathAndQuery else pathAndQuery.substring(0, queryIdx)
        val query = if (queryIdx < 0) "" else pathAndQuery.substring(queryIdx + 1)
        return UriParts(scheme = scheme, authority = authority, path = path, query = query)
    }
}
