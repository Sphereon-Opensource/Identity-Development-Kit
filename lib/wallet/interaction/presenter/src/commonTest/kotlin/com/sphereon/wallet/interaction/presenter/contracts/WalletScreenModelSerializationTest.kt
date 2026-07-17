/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.presenter.contracts

import com.sphereon.wallet.interaction.presenter.WalletScreenModelMapper
import com.sphereon.wallet.interaction.WalletCounterpartyRole
import com.sphereon.wallet.interaction.WalletCounterpartySummary
import com.sphereon.wallet.interaction.WalletInteractionFlowKind
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputRef
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WalletScreenModelSerializationTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun screenTranscriptRoundTripsForComposeAndReactConsumers() {
        val state =
            WalletInteractionState(
                sessionId = WalletInteractionSessionId("session-42"),
                walletUnitId = "wallet-unit-7",
                revision = 12,
                status = WalletInteractionStatus.DisclosureConsent,
                flowKind = WalletInteractionFlowKind.CredentialPresent,
                authorizationHandoffRef = WalletInteractionSensitiveInputRef("opaque-handoff-ref"),
                counterparty =
                    WalletCounterpartySummary(
                        role = WalletCounterpartyRole.VERIFIER,
                        identifier = "verifier-9",
                        displayName = "Example verifier",
                        metadata = mapOf("protocol_token" to "secret-protocol-token"),
                    ),
            )
        val expected = WalletScreenModelMapper.map(state)

        val encoded = json.encodeToString(expected)
        val decoded = json.decodeFromString<WalletScreenModel>(encoded)

        assertEquals(expected, decoded)
        assertEquals("wallet.interaction.action.share", decoded.primaryAction?.labelKey)
        assertFalse("accessToken" in encoded)
        assertFalse("refreshToken" in encoded)
        assertFalse("privateKey" in encoded)
        assertFalse("claimValue" in encoded)
        assertFalse("secret-token" in encoded)
        assertFalse("secret-protocol-token" in encoded)
        assertFalse("authorizationUrl" in encoded)
        assertFalse("walletUnitId" in encoded)
        assertFalse("482913" in encoded)
    }
}
