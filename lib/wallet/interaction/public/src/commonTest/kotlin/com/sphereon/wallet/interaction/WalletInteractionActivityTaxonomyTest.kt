/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalletInteractionActivityTaxonomyTest {
    @Test
    fun loginActivityCanBeRepresentedOnInteractionState() {
        val counterparty =
            WalletCounterpartySummary(
                role = WalletCounterpartyRole.VERIFIER,
                identifier = "https://rp.example",
                displayName = "Example RP",
                metadata = mapOf("interaction_context" to "login"),
            )
        val state =
            WalletInteractionState(
                sessionId = WalletInteractionSessionId("s1"),
                walletUnitId = "wallet",
                status = WalletInteractionStatus.DisclosureConsent,
                flowKind = WalletInteractionFlowKind.CredentialPresent,
                protocol = WalletProtocol.OID4VP,
                activity =
                    WalletInteractionActivitySummary(
                        type = WalletInteractionActivityType.LOGIN,
                        counterparty = counterparty,
                        metadata = mapOf("interaction_context" to "login"),
                    ),
                counterparty = counterparty,
            )

        val encoded = Json.encodeToString(state)

        assertEquals(WalletInteractionActivityType.LOGIN, state.activity?.type)
        assertEquals(WalletCounterpartyRole.VERIFIER, state.activity?.counterparty?.role)
        assertEquals("login", state.counterparty?.metadata?.get("interaction_context"))
        assertTrue(encoded.contains("LOGIN"))
    }
}
