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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WalletInteractionActionTest {
    @Test
    fun clientMayRegisterOnlyClientSuppliedSensitiveInputs() {
        val sessionId = WalletInteractionSessionId("session-1")
        val allowed =
            listOf(
                WalletInteractionSensitiveInputPurpose.OID4VCI_TRANSACTION_CODE,
                WalletInteractionSensitiveInputPurpose.OID4VCI_AUTHORIZATION_CALLBACK,
                WalletInteractionSensitiveInputPurpose.INTERACTION_SECURITY_GRANT,
            )

        allowed.forEach { purpose ->
            RegisterWalletInteractionSensitiveInputArgs(
                walletUnitId = "wallet-unit-1",
                sessionId = sessionId,
                purpose = purpose,
                value = "secret",
            )
        }

        listOf(
            WalletInteractionSensitiveInputPurpose.OID4VCI_AUTHORIZATION_HANDOFF,
            WalletInteractionSensitiveInputPurpose.PROTOCOL_COMPLETION_HANDOFF,
        ).forEach { purpose ->
            assertFailsWith<IllegalArgumentException> {
                RegisterWalletInteractionSensitiveInputArgs(
                    walletUnitId = "wallet-unit-1",
                    sessionId = sessionId,
                    purpose = purpose,
                    value = "secret",
                )
            }
        }
    }

    @Test
    fun sensitiveInputRegistrationDoesNotExposeItsValueInLogs() {
        val args =
            RegisterWalletInteractionSensitiveInputArgs(
                walletUnitId = "wallet-unit-1",
                sessionId = WalletInteractionSessionId("session-1"),
                purpose = WalletInteractionSensitiveInputPurpose.INTERACTION_SECURITY_GRANT,
                value = "grant-secret",
            )

        assertTrue(!args.toString().contains("grant-secret"))
        assertTrue(args.toString().contains("[redacted]"))
    }

    @Test
    fun companionCoversCoreActionSet() {
        val selection = WalletCredentialSelection(mapOf("identity" to listOf("cred-1")))
        val sensitiveRef = WalletInteractionSensitiveInputRef("opaque-ref")

        val actions =
            listOf(
                WalletInteractionAction.resolveCounterpartyContact(
                    WalletCounterpartyAssociationDecision.KeepSeparate("OIDF counterparty"),
                ),
                WalletInteractionAction.continueFlow(),
                WalletInteractionAction.decline(),
                WalletInteractionAction.submitTxCode(sensitiveRef),
                WalletInteractionAction.authCallback(sensitiveRef),
                WalletInteractionAction.chooseImplementation("oid4vci"),
                WalletInteractionAction.selectCredentials(selection),
                WalletInteractionAction.revealClaimValues(),
                WalletInteractionAction.approveSecurityChallenge(sensitiveRef),
                WalletInteractionAction.retryDeferredRetrieval(),
                WalletInteractionAction.resumeDeferredRetrieval(),
                WalletInteractionAction.cancel(),
            )

        assertEquals(WalletInteractionActionType.entries.toSet(), actions.map { it.type }.toSet())
        assertEquals(selection, actions.single { it.type == WalletInteractionActionType.SELECT_CREDENTIALS }.selection)
        assertEquals(sensitiveRef, actions.single { it.type == WalletInteractionActionType.APPROVE_SECURITY_CHALLENGE }.sensitiveInputRef)
    }

    @Test
    fun payloadBearingActionsRejectMissingPayloads() {
        assertFailsWith<IllegalArgumentException> { WalletInteractionAction(WalletInteractionActionType.RESOLVE_COUNTERPARTY_CONTACT) }
        assertFailsWith<IllegalArgumentException> { WalletInteractionAction(WalletInteractionActionType.SUBMIT_TX_CODE) }
        assertFailsWith<IllegalArgumentException> { WalletInteractionAction(WalletInteractionActionType.AUTH_CALLBACK) }
        assertFailsWith<IllegalArgumentException> { WalletInteractionAction(WalletInteractionActionType.CHOOSE_IMPLEMENTATION) }
        assertFailsWith<IllegalArgumentException> { WalletInteractionAction(WalletInteractionActionType.CHOOSE_IMPLEMENTATION, implementationId = "") }
        assertFailsWith<IllegalArgumentException> { WalletInteractionAction(WalletInteractionActionType.SELECT_CREDENTIALS) }
        assertFailsWith<IllegalArgumentException> { WalletInteractionAction(WalletInteractionActionType.APPROVE_SECURITY_CHALLENGE) }
    }

    @Test
    fun serializedSensitiveActionsContainOnlyOpaqueReferences() {
        val forbidden = listOf("482913", "authorization-code-secret", "grant-secret", "REMOTE_AUTHORIZED")
        val actions =
            listOf(
                WalletInteractionAction.submitTxCode(WalletInteractionSensitiveInputRef("tx-ref")),
                WalletInteractionAction.authCallback(WalletInteractionSensitiveInputRef("callback-ref")),
                WalletInteractionAction.approveSecurityChallenge(WalletInteractionSensitiveInputRef("grant-ref")),
            )

        val encoded = Json.encodeToString(actions)

        forbidden.forEach { assertTrue(!encoded.contains(it)) }
        assertTrue(encoded.contains("tx-ref"))
        assertTrue(encoded.contains("callback-ref"))
        assertTrue(encoded.contains("grant-ref"))
    }
}
