/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.presenter.contracts

import com.sphereon.wallet.interaction.presenter.WalletScreenModelMapper
import com.sphereon.wallet.interaction.WalletCounterpartyRole
import com.sphereon.wallet.interaction.WalletCounterpartySummary
import com.sphereon.wallet.interaction.WalletCounterpartyTrustSummary
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletTrustPolicyAction
import com.sphereon.wallet.interaction.WalletTrustSource
import com.sphereon.wallet.interaction.WalletTrustSourceType
import com.sphereon.wallet.interaction.WalletTrustStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class WalletSafePresentationModelsTest {
    @Test
    fun trustMechanismsAreExactlyTheSettledMutuallyExclusiveSet() {
        assertEquals(
            listOf("ETSI_TRUSTED_LIST", "OPENID_FEDERATION", "NONE"),
            WalletTrustMechanismPresentation.entries.map { it.name },
        )
    }

    @Test
    fun mapperFailsClosedWhenTwoPrimaryTrustMechanismsAreSupplied() {
        val party = WalletCounterpartySummary(WalletCounterpartyRole.VERIFIER, "party-1")
        val state =
            WalletInteractionState(
                sessionId = WalletInteractionSessionId("session-1"),
                walletUnitId = "wallet-unit-1",
                status = WalletInteractionStatus.TrustReview,
                counterparty = party,
                trust =
                    WalletCounterpartyTrustSummary(
                        counterparty = party,
                        status = WalletTrustStatus.TRUSTED,
                        policyAction = WalletTrustPolicyAction.ALLOW,
                        sources =
                            listOf(
                                WalletTrustSource(WalletTrustSourceType.EUDI_TRUSTED_LIST, "etsi"),
                                WalletTrustSource(WalletTrustSourceType.OPENID_FEDERATION, "federation"),
                            ),
                    ),
            )

        assertFailsWith<IllegalArgumentException> { WalletScreenModelMapper.map(state) }
    }

    @Test
    fun hiddenInfoSerializesWithoutAValue() {
        val node =
            WalletInfoNodePresentation.Leaf(
                nodeId = "degree",
                labelKey = "wallet.info.degree",
                labelArguments = emptyMap(),
                value = null,
            )

        val encoded = Json.encodeToString<WalletInfoNodePresentation>(node)

        assertFalse("MSc Computer Science" in encoded)
        assertFalse("claim" in encoded.lowercase())
    }

    @Test
    fun opaqueCeremonyReferencesAreRedactedFromLogs() {
        val ref = WalletSecureCeremonyRef("one-use-secret-reference")

        assertEquals("WalletSecureCeremonyRef([redacted])", ref.toString())
    }
}
