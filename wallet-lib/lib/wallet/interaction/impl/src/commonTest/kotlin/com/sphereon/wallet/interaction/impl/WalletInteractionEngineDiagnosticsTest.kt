/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.impl

import com.sphereon.core.api.Ok
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionDiagnostics
import com.sphereon.wallet.interaction.WalletInteractionFailureCodes
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionLaunchAuthority
import com.sphereon.wallet.interaction.WalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.classifiedWalletInteractionError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalletInteractionEngineDiagnosticsTest {
    private class RecordingDiagnostics : WalletInteractionDiagnostics {
        val events = mutableListOf<Triple<String, WalletInteractionSessionId?, Map<String, String>>>()

        override fun warn(
            event: String,
            sessionId: WalletInteractionSessionId?,
            details: Map<String, String>,
        ) {
            events += Triple(event, sessionId, details)
        }
    }

    private class FailingAdapter(
        private val failure: () -> com.sphereon.wallet.interaction.WalletInteractionError,
        private val delegate: WalletInteractionProtocolAdapter = StaticWalletInteractionProtocolAdapter.oid4vci(),
    ) : WalletInteractionProtocolAdapter by delegate {
        override suspend fun handle(
            context: WalletInteractionContext,
            sessionState: WalletInteractionState,
            action: WalletInteractionAction,
        ): WalletInteractionState =
            sessionState.next(status = WalletInteractionStatus.Failed, terminal = false, error = failure())
    }

    private fun engine(
        diagnostics: WalletInteractionDiagnostics,
        adapter: WalletInteractionProtocolAdapter,
    ): DefaultWalletInteractionEngine {
        val privateSessions = InMemoryWalletInteractionPrivateSessionStore()
        return DefaultWalletInteractionEngine(
            adapters = listOf(adapter),
            sensitiveInputAuthority = StoreBackedWalletInteractionSensitiveInputAuthority(privateSessions),
            privateSessionStore = privateSessions,
            sessionStore = InMemoryWalletInteractionSessionStore(),
            launchAuthorities = setOf(WalletInteractionLaunchAuthority { Ok(null) }),
            diagnostics = diagnostics,
        )
    }

    private fun input() = WalletInteractionInput("wallet-a", WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x"))

    @Test
    fun nonTerminalFailureIsLoggedWithCodeAndArguments() = runTest {
        val diagnostics = RecordingDiagnostics()
        val engine =
            engine(
                diagnostics,
                FailingAdapter({
                    classifiedWalletInteractionError(
                        code = WalletInteractionFailureCodes.OID4VCI_OPTIONS_UNAVAILABLE,
                        messageKey = "wallet.interaction.error.oid4vci_options_unavailable",
                        arguments = mapOf("causeType" to "IllegalStateException", "cause" to "atomic store required"),
                    )
                }),
            )
        val session = engine.start(input())

        engine.dispatch(session.sessionId, WalletInteractionAction.continueFlow())

        val logged = diagnostics.events.single()
        assertEquals("interaction.failed", logged.first)
        assertEquals(session.sessionId, logged.second)
        assertEquals(WalletInteractionFailureCodes.OID4VCI_OPTIONS_UNAVAILABLE, logged.third["code"])
        assertEquals("false", logged.third["terminal"])
        assertEquals("REPEATABLE", logged.third["disposition"])
        assertEquals("IllegalStateException", logged.third["arg.causeType"])
        assertEquals("atomic store required", logged.third["arg.cause"])
    }

    @Test
    fun theSameFailureIsNotLoggedTwice() = runTest {
        val diagnostics = RecordingDiagnostics()
        val engine =
            engine(
                diagnostics,
                FailingAdapter({
                    classifiedWalletInteractionError(
                        code = WalletInteractionFailureCodes.OID4VCI_OPTIONS_UNAVAILABLE,
                        messageKey = "wallet.interaction.error.oid4vci_options_unavailable",
                    )
                }),
            )
        val session = engine.start(input())

        engine.dispatch(session.sessionId, WalletInteractionAction.continueFlow())
        engine.dispatch(session.sessionId, WalletInteractionAction.continueFlow())

        assertEquals(1, diagnostics.events.size)
    }

    @Test
    fun interactionsWithoutAFailureLogNothing() = runTest {
        val diagnostics = RecordingDiagnostics()
        val engine = engine(diagnostics, StaticWalletInteractionProtocolAdapter.oid4vci())
        val session = engine.start(input())

        engine.dispatch(session.sessionId, WalletInteractionAction.continueFlow())

        assertTrue(diagnostics.events.isEmpty())
    }
}
