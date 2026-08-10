/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CredentialIssuanceTimeTest {
    @Test
    fun appliesClockSkewThenRoundsDownToTheHour() {
        val now = 1_700_000_123L
        val skewAdjusted = now - 60L
        val rounded = roundedCredentialIssuanceEpochSeconds(now, issuanceClockSkewInSeconds = 60L)

        assertEquals(0L, rounded % CREDENTIAL_TIME_ROUNDING_SECONDS)
        assertTrue(rounded <= skewAdjusted)
        assertTrue(rounded > skewAdjusted - CREDENTIAL_TIME_ROUNDING_SECONDS)
    }

    @Test
    fun preservesAnExactHourBoundaryAfterClockSkew() {
        val boundary = 1_699_999_200L

        assertEquals(
            boundary,
            roundedCredentialIssuanceEpochSeconds(
                nowEpochSeconds = boundary + 60L,
                issuanceClockSkewInSeconds = 60L,
            ),
        )
    }

    @Test
    fun wholeDayExpiryRemainsOnAnHourBoundary() {
        val issuedAt = roundedCredentialIssuanceEpochSeconds(1_700_000_123L, 60L)
        val expiresAt = issuedAt + (365L * 24L * 60L * 60L)

        assertEquals(0L, expiresAt % CREDENTIAL_TIME_ROUNDING_SECONDS)
    }
}
