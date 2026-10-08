/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.revocation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RevocationCheckerInterfaceTest {
    @Test
    fun revocationReasonFromCode() {
        assertEquals(RevocationReason.UNSPECIFIED, RevocationReason.fromCode(0))
        assertEquals(RevocationReason.KEY_COMPROMISE, RevocationReason.fromCode(1))
        assertEquals(RevocationReason.CA_COMPROMISE, RevocationReason.fromCode(2))
        assertEquals(RevocationReason.AFFILIATION_CHANGED, RevocationReason.fromCode(3))
        assertEquals(RevocationReason.SUPERSEDED, RevocationReason.fromCode(4))
        assertEquals(RevocationReason.CESSATION_OF_OPERATION, RevocationReason.fromCode(5))
        assertEquals(RevocationReason.CERTIFICATE_HOLD, RevocationReason.fromCode(6))
        assertEquals(RevocationReason.REMOVE_FROM_CRL, RevocationReason.fromCode(8))
        assertEquals(RevocationReason.PRIVILEGE_WITHDRAWN, RevocationReason.fromCode(9))
        assertEquals(RevocationReason.AA_COMPROMISE, RevocationReason.fromCode(10))
        assertNull(RevocationReason.fromCode(7))
        assertNull(RevocationReason.fromCode(99))
    }

    @Test
    fun defaultRevocationCheckOptions() {
        val options = RevocationCheckOptions()
        assertEquals(true, options.checkOCSP)
        assertEquals(true, options.checkCRL)
        assertEquals(true, options.preferOCSP)
        assertEquals(10000L, options.timeoutMs)
        assertEquals(true, options.useCache)
        assertEquals(3600000L, options.maxCacheAgeMs)
        assertNull(options.ocspResponderUrl)
        assertNull(options.crlDistributionPoint)
        assertEquals(false, options.failOnUnknown)
    }

    @Test
    fun revocationCheckResultDefaults() {
        val result =
            RevocationCheckResult(
                status = RevocationStatus.GOOD,
                method = RevocationCheckMethod.CRL,
                checkedAt = 1000L,
            )
        assertEquals(false, result.fromCache)
        assertNull(result.revocationTime)
        assertNull(result.revocationReason)
        assertNull(result.errorMessage)
        assertEquals(emptyMap(), result.details)
    }
}
