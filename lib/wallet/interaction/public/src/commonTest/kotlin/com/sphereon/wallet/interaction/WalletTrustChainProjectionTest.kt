/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WalletTrustChainProjectionTest {
    @Test
    fun unresolvedResolverLeavesChainAdmissionAndAuthorisationAbsent() =
        runTest {
            val summary =
                WalletCounterpartyTrustResolver.unresolved.resolve(
                    WalletCounterpartyTrustRequest(
                        counterparty =
                            WalletCounterpartySummary(
                                role = WalletCounterpartyRole.ISSUER,
                                identifier = "https://issuer.example",
                            ),
                        protocol = WalletProtocol.OID4VCI,
                    ),
                )
            assertNull(summary.trustChain)
            assertNull(summary.domainAdmission)
            assertNull(summary.attestationAuthorisation)
        }

    @Test
    fun aPresentChainWithZeroHopsCannotBeConstructed() {
        assertFailsWith<IllegalArgumentException> {
            WalletTrustChain(hops = emptyList(), links = WalletTrustChainLinks.VERIFIED)
        }
    }

    @Test
    fun trustAllAdmissionCannotNameADomain() {
        assertFailsWith<IllegalArgumentException> {
            WalletTrustDomainAdmission(
                posture = WalletTrustDomainPosture.TRUST_ALL,
                outcome = WalletTrustDomainAdmissionOutcome.ADMITTED_TRUST_ALL,
                admittingDomain = "https://named.example",
            )
        }
        val admission =
            WalletTrustDomainAdmission(
                posture = WalletTrustDomainPosture.TRUST_ALL,
                outcome = WalletTrustDomainAdmissionOutcome.ADMITTED_TRUST_ALL,
            )
        assertNull(admission.admittingDomain)
        assertEquals(WalletTrustDomainAdmissionOutcome.ADMITTED_TRUST_ALL, admission.outcome)
    }
}
