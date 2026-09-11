/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.trust.core.resolver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustListAddressPolicyTest {
    @Test
    fun onlyGloballyRoutableIpv4AddressesAreAllowed() {
        val blocked =
            listOf(
                "0.0.0.0",
                "10.1.2.3",
                "100.64.0.1",
                "127.0.0.1",
                "169.254.169.254",
                "172.16.0.1",
                "192.0.2.1",
                "192.168.1.1",
                "198.18.0.1",
                "224.0.0.1",
                "255.255.255.255",
            )

        blocked.forEach { address ->
            assertFalse(TrustListAddressPolicy.isGlobalAddress(address), address)
        }
        assertTrue(TrustListAddressPolicy.isGlobalAddress("8.8.8.8"))
    }

    @Test
    fun onlyGloballyRoutableIpv6AddressesAreAllowed() {
        val blocked =
            listOf(
                "::",
                "::1",
                "fe80::1",
                "fc00::1",
                "ff02::1",
                "2001:db8::1",
                "::ffff:127.0.0.1",
                "::ffff:8.8.8.8",
            )

        blocked.forEach { address ->
            assertFalse(TrustListAddressPolicy.isGlobalAddress(address), address)
        }
        assertTrue(TrustListAddressPolicy.isGlobalAddress("2001:4860:4860::8888"))
    }

    @Test
    fun ipv6ZoneIdentifiersAndAdditionalSpecialPurposeRangesAreRejected() {
        val blocked =
            listOf(
                "fe80::1%eth0",
                "100::1",
                "2001:0::1",
                "2001:100::1",
                "2001:1::4",
                "2001:2::1",
                "2001:4:111::1",
                "64:ff9b:1::1",
                "100:0:0:1::1",
                "2002::1",
                "5f00::1",
                "3fff::1",
            )

        blocked.forEach { address ->
            assertFalse(TrustListAddressPolicy.isGlobalAddress(address), address)
        }
    }

    @Test
    fun aDialAddressNotInTheApprovedResolutionIsRejectedAsRebinding() {
        val failure =
            runCatching {
                TrustListAddressPolicy.requireApprovedDialAddress(
                    approvedAddresses = listOf("8.8.8.8"),
                    dialAddress = "1.1.1.1",
                )
            }.exceptionOrNull()

        assertEquals(
            com.sphereon.trust.core.TrustDiagnosticReasonCodes.TRUST_LIST_DNS_REBINDING,
            (failure as TrustListResolutionException).reasonCode,
        )
    }
}
