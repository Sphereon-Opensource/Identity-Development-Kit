/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlin.test.Test
import kotlin.test.assertEquals

class WalletInteractionContextTest {
    @Test
    fun baseStatePreservesBackendExecutionOwner() {
        val context =
            WalletInteractionContext(
                sessionId = WalletInteractionSessionId("session-1"),
                walletUnitId = "wallet-unit-1",
                executionOwner = ProtocolExecutionOwner.WALLET_BACKEND,
                sensitiveInputAuthority = unusedSensitiveInputAuthority,
            )

        val state =
            context.baseState(
                status = WalletInteractionStatus.ResolvingEntryPoint,
                flowKind = WalletInteractionFlowKind.CredentialReceive,
                protocol = WalletProtocol.OID4VCI,
                adapterId = "oid4vci",
                entryPoint = WalletEntryPoint.link("https://wallet.example.test/request"),
            )

        assertEquals(ProtocolExecutionOwner.WALLET_BACKEND, state.executionOwner)
    }
}

private val unusedSensitiveInputAuthority =
    object : WalletInteractionSensitiveInputAuthority {
        override suspend fun register(
            sessionId: WalletInteractionSessionId,
            purpose: WalletInteractionSensitiveInputPurpose,
            value: String,
        ): WalletInteractionSensitiveInputRef = error("unused")

        override suspend fun consume(
            sessionId: WalletInteractionSessionId,
            purpose: WalletInteractionSensitiveInputPurpose,
            ref: WalletInteractionSensitiveInputRef,
        ): String? = error("unused")

        override suspend fun registerSecurityGrant(
            sessionId: WalletInteractionSessionId,
            grant: WalletSecurityGrant,
        ): WalletInteractionSensitiveInputRef = error("unused")

        override suspend fun consumeSecurityGrant(
            sessionId: WalletInteractionSessionId,
            ref: WalletInteractionSensitiveInputRef,
        ): WalletSecurityGrant? = error("unused")

        override suspend fun clear(sessionId: WalletInteractionSessionId) = Unit
    }
