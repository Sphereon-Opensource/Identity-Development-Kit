/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.trust.core.resolver

import com.sphereon.trust.core.resolver.ResolutionOptions
import com.sphereon.trust.core.resolver.TrustListCacheMetadata
import com.sphereon.trust.core.resolver.TrustListData
import kotlin.test.Test
import kotlin.test.assertEquals

class TrustListResolutionContractTest {
    @Test
    fun resolutionOptionsCarryTheExplicitSignedNextUpdateBound() {
        assertEquals(
            1_900_000_000_000L,
            ResolutionOptions(signedNextUpdateEpochMillis = 1_900_000_000_000L).signedNextUpdateEpochMillis,
        )
    }

    @Test
    fun resolvedDataCarriesTheEffectiveResponseCacheMetadata() {
        val metadata =
            TrustListCacheMetadata(
                cacheControl = "public, max-age=30",
                expiresAtEpochMillis = 1_900_000_030_000L,
                effectiveTtlMs = 30_000L,
            )

        assertEquals(
            metadata,
            TrustListData(
                data = byteArrayOf(1),
                sourceUri = "https://example.com/list.xml",
                retrievedAt = 1_900_000_000_000L,
                cacheMetadata = metadata,
            ).cacheMetadata,
        )
    }
}
