/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.cache

import kotlin.time.Instant

/**
 * Computes the maximum time for which an HTTP response may be reused.
 *
 * This policy is pure. Callers supply already parsed HTTP and application
 * freshness limits and decide how to apply the returned TTL.
 */
object HttpCacheExpiryPolicy {
    fun isNoStore(cacheControl: String?): Boolean = hasDirective(cacheControl, "no-store")

    fun requiresRevalidation(cacheControl: String?): Boolean = hasDirective(cacheControl, "no-cache")

    fun ttlMillis(
        cacheControl: String?,
        localTtlMillis: Long,
        expiresAt: Instant? = null,
        signedNextUpdate: Instant? = null,
        now: Instant,
    ): Long {
        if (localTtlMillis <= 0L) {
            return 0L
        }

        val cacheControlTtl = parseCacheControlTtl(cacheControl) ?: localTtlMillis
        if (cacheControlTtl == 0L) {
            return 0L
        }

        val limits = mutableListOf(localTtlMillis, cacheControlTtl)
        expiresAt?.let { limits += remainingMillis(it, now) }
        signedNextUpdate?.let { limits += remainingMillis(it, now) }

        return limits.minOrNull() ?: 0L
    }

    private fun parseCacheControlTtl(cacheControl: String?): Long? {
        if (cacheControl == null) {
            return null
        }

        val directives = cacheControl.split(',').map { it.trim() }
        if (directives.any { it.equals("no-store", ignoreCase = true) || it.equals("no-cache", ignoreCase = true) }) {
            return 0L
        }

        val maxAgeValues =
            directives
                .filter { it.substringBefore('=').trim().equals("max-age", ignoreCase = true) }
                .map { it.substringAfter('=', missingDelimiterValue = "").trim().removeSurrounding("\"") }

        if (maxAgeValues.size > 1) {
            return 0L
        }

        val maxAgeSeconds = maxAgeValues.singleOrNull()?.toLongOrNull() ?: return if (maxAgeValues.isEmpty()) null else 0L
        if (maxAgeSeconds < 0L) {
            return 0L
        }
        return if (maxAgeSeconds > Long.MAX_VALUE / 1000L) {
            Long.MAX_VALUE
        } else {
            maxAgeSeconds * 1000L
        }
    }

    private fun hasDirective(
        cacheControl: String?,
        directive: String,
    ): Boolean =
        cacheControl
            ?.split(',')
            ?.any { it.trim().substringBefore('=').equals(directive, ignoreCase = true) }
            ?: false

    private fun remainingMillis(
        limit: Instant,
        now: Instant,
    ): Long =
        if (limit <= now) {
            0L
        } else {
            (limit - now).inWholeMilliseconds
        }
}
