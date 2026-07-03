/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WalletInteractionActionTest {
    @Test
    fun companionCoversCoreActionSet() {
        val selection = WalletCredentialSelection(mapOf("identity" to listOf("cred-1")))
        val grant = WalletSecurityGrant("grant-1", WalletSecurityAssurance.USER_PRESENT)

        val actions =
            listOf(
                WalletInteractionAction.continueFlow(),
                WalletInteractionAction.decline(),
                WalletInteractionAction.submitTxCode("123456"),
                WalletInteractionAction.authCallback("wallet://callback?code=code"),
                WalletInteractionAction.chooseImplementation("oid4vci"),
                WalletInteractionAction.selectCredentials(selection),
                WalletInteractionAction.revealClaimValues(),
                WalletInteractionAction.approveSecurityChallenge(grant),
                WalletInteractionAction.acceptReceivedCredential(),
                WalletInteractionAction.declineReceivedCredential(),
                WalletInteractionAction.retryDeferredRetrieval(),
                WalletInteractionAction.resumeDeferredRetrieval(),
                WalletInteractionAction.cancel(),
            )

        assertEquals(WalletInteractionActionType.entries.toSet(), actions.map { it.type }.toSet())
        assertEquals(selection, actions.single { it.type == WalletInteractionActionType.SELECT_CREDENTIALS }.selection)
        assertEquals(grant, actions.single { it.type == WalletInteractionActionType.APPROVE_SECURITY_CHALLENGE }.securityGrant)
    }

    @Test
    fun payloadBearingActionsRejectMissingPayloads() {
        assertFailsWith<IllegalArgumentException> { WalletInteractionAction(WalletInteractionActionType.SUBMIT_TX_CODE) }
        assertFailsWith<IllegalArgumentException> { WalletInteractionAction(WalletInteractionActionType.SUBMIT_TX_CODE, value = "") }
        assertFailsWith<IllegalArgumentException> { WalletInteractionAction(WalletInteractionActionType.AUTH_CALLBACK) }
        assertFailsWith<IllegalArgumentException> { WalletInteractionAction(WalletInteractionActionType.AUTH_CALLBACK, authorizationCallback = "") }
        assertFailsWith<IllegalArgumentException> { WalletInteractionAction(WalletInteractionActionType.CHOOSE_IMPLEMENTATION) }
        assertFailsWith<IllegalArgumentException> { WalletInteractionAction(WalletInteractionActionType.CHOOSE_IMPLEMENTATION, implementationId = "") }
        assertFailsWith<IllegalArgumentException> { WalletInteractionAction(WalletInteractionActionType.SELECT_CREDENTIALS) }
        assertFailsWith<IllegalArgumentException> { WalletInteractionAction(WalletInteractionActionType.APPROVE_SECURITY_CHALLENGE) }
    }
}
