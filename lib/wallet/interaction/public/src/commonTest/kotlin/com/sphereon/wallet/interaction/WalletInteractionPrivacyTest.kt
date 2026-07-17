/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse

class WalletInteractionPrivacyTest {
    private val json =
        Json {
            encodeDefaults = true
            explicitNulls = false
        }

    @Test
    fun uiStateDoesNotSerializeProtocolSecretsOrClaimValues() {
        val state =
            WalletInteractionState(
                sessionId = WalletInteractionSessionId("session"),
                walletUnitId = "wallet",
                status = WalletInteractionStatus.DisclosureConsent,
                disclosure =
                    WalletDisclosureSummary(
                        requestedClaims =
                            listOf(
                                WalletClaimDescriptor(path = listOf("given_name"), valueAvailable = true),
                                WalletClaimDescriptor(path = listOf("family_name"), valueAvailable = true),
                            ),
                    ),
            )

        val encoded = json.encodeToString(state)

        listOf(
            "access_token",
            "refresh_token",
            "pre-authorized_code",
            "private_key",
            "secret",
            "executionMode",
            "BACKEND",
            "SPLIT",
            "Alice",
            "Anderson",
        ).forEach { forbidden ->
            assertFalse(encoded.contains(forbidden, ignoreCase = true), "State leaked forbidden value: $forbidden")
        }
    }
}
