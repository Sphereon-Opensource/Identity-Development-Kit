/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.trust.core.resolver

import com.sphereon.core.api.cache.HttpCacheExpiryPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class HttpCacheExpiryPolicyTest {
    private val now = Instant.parse("2026-08-20T12:00:00Z")

    @Test
    fun responseCacheDirectivesBoundLocalCacheTtl() {
        assertEquals(
            30_000L,
            HttpCacheExpiryPolicy.ttlMillis(
                cacheControl = "public, max-age=30",
                localTtlMillis = 60_000L,
                now = now,
            ),
        )
        assertEquals(
            0L,
            HttpCacheExpiryPolicy.ttlMillis(
                cacheControl = "no-cache",
                localTtlMillis = 60_000L,
                now = now,
            ),
        )
        assertEquals(
            0L,
            HttpCacheExpiryPolicy.ttlMillis(
                cacheControl = "no-store",
                localTtlMillis = 60_000L,
                now = now,
            ),
        )
    }

    @Test
    fun effectiveTtlUsesTheMostRestrictivePositiveLimit() {
        assertEquals(
            10_000L,
            HttpCacheExpiryPolicy.ttlMillis(
                cacheControl = "public, max-age=90",
                localTtlMillis = 60_000L,
                expiresAt = now + 25.seconds,
                signedNextUpdate = now + 10.seconds,
                now = now,
            ),
        )
    }

    @Test
    fun expiresAndNextUpdateAreMeasuredFromTheSuppliedNow() {
        assertEquals(
            20_000L,
            HttpCacheExpiryPolicy.ttlMillis(
                cacheControl = null,
                localTtlMillis = 60_000L,
                expiresAt = now + 20.seconds,
                signedNextUpdate = now + 40.seconds,
                now = now,
            ),
        )
    }

    @Test
    fun expiredSignedOrHttpLimitFailsClosed() {
        assertEquals(
            0L,
            HttpCacheExpiryPolicy.ttlMillis(
                cacheControl = null,
                localTtlMillis = 60_000L,
                expiresAt = now,
                signedNextUpdate = now + 30.seconds,
                now = now,
            ),
        )
        assertEquals(
            0L,
            HttpCacheExpiryPolicy.ttlMillis(
                cacheControl = null,
                localTtlMillis = 60_000L,
                expiresAt = now + 30.seconds,
                signedNextUpdate = now - 1.seconds,
                now = now,
            ),
        )
    }

    @Test
    fun invalidLocalTtlAndMalformedMaxAgeFailClosed() {
        assertEquals(
            0L,
            HttpCacheExpiryPolicy.ttlMillis(
                cacheControl = null,
                localTtlMillis = 0L,
                now = now,
            ),
        )
        assertEquals(
            0L,
            HttpCacheExpiryPolicy.ttlMillis(
                cacheControl = null,
                localTtlMillis = -1L,
                now = now,
            ),
        )
        assertEquals(
            0L,
            HttpCacheExpiryPolicy.ttlMillis(
                cacheControl = "public, max-age=invalid",
                localTtlMillis = 60_000L,
                now = now,
            ),
        )
    }
}
