/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletFailureDisposition
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionError
import com.sphereon.wallet.interaction.WalletInteractionFlowKind
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import com.sphereon.wallet.interaction.WalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.WalletInteractionSession
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletProtocol
import com.sphereon.wallet.interaction.WalletProtocolCapability
import com.sphereon.wallet.interaction.WalletProtocolMatch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WalletInteractionResumeTest {
    @Test
    fun `an interaction that failed resumably can be resumed`() =
        runTest {
            val engine = resumeTestEngine(WalletFailureDisposition.RESUMABLE)
            val session = engine.interactionFailedWith(WalletFailureDisposition.RESUMABLE)
            val resumed = engine.resume(session.sessionId)
            assertNotEquals(WalletInteractionStatus.Failed, resumed.state.status, "a resumable failure must actually resume")
            assertEquals(WalletInteractionStatus.CounterpartyNotice, resumed.state.status)
            assertEquals(null, resumed.state.error)
            assertEquals(false, resumed.state.terminal)
            assertEquals(listOf("waiting-config"), resumed.state.selectedCredentialConfigurationIds)
        }

    @Test
    fun `load of a resumable failure does not resume it`() =
        runTest {
            val engine = resumeTestEngine(WalletFailureDisposition.RESUMABLE)
            val session = engine.interactionFailedWith(WalletFailureDisposition.RESUMABLE)
            val loaded = engine.load(session.sessionId)
            assertEquals(WalletInteractionStatus.Failed, loaded.state.status)
            assertEquals(WalletFailureDisposition.RESUMABLE, loaded.state.error?.disposition)
            assertEquals(true, loaded.state.terminal)
            assertEquals(emptyList(), loaded.state.selectedCredentialConfigurationIds)
        }

    @Test
    fun `an interaction that failed terminally cannot be resumed`() =
        runTest {
            val engine = resumeTestEngine(WalletFailureDisposition.TERMINAL)
            val session = engine.interactionFailedWith(WalletFailureDisposition.TERMINAL)
            assertFailsWith<IllegalStateException> { engine.resume(session.sessionId) }
        }

    @Test
    fun `an interaction that failed as repeatable cannot be resumed`() =
        runTest {
            val engine = resumeTestEngine(WalletFailureDisposition.REPEATABLE)
            val session = engine.interactionFailedWith(WalletFailureDisposition.REPEATABLE)
            assertFailsWith<IllegalStateException> { engine.resume(session.sessionId) }
        }

    @Test
    fun `resuming a resumable failure keeps private session state`() =
        runTest {
            val privateStore = InMemoryWalletInteractionPrivateSessionStore()
            val engine =
                testWalletInteractionEngine(
                    adapters = listOf(FailingDispositionAdapter(WalletFailureDisposition.RESUMABLE)),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    privateSessionStore = privateStore,
                )
            val session = engine.interactionFailedWith(WalletFailureDisposition.RESUMABLE)
            assertNotNull(privateStore.get(session.sessionId, "failing-disposition"))
            engine.resume(session.sessionId)
            assertNotNull(privateStore.get(session.sessionId, "failing-disposition"))
        }

    @Test
    fun `a terminal failure still wipes private session state`() =
        runTest {
            val privateStore = InMemoryWalletInteractionPrivateSessionStore()
            val engine =
                testWalletInteractionEngine(
                    adapters = listOf(FailingDispositionAdapter(WalletFailureDisposition.TERMINAL)),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    privateSessionStore = privateStore,
                )
            val session = engine.interactionFailedWith(WalletFailureDisposition.TERMINAL)
            assertNull(privateStore.get(session.sessionId, "failing-disposition"))
        }
}

private fun resumeTestEngine(disposition: WalletFailureDisposition): DefaultWalletInteractionEngine =
    testWalletInteractionEngine(
        adapters = listOf(FailingDispositionAdapter(disposition)),
        sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
    )

private suspend fun DefaultWalletInteractionEngine.interactionFailedWith(
    disposition: WalletFailureDisposition,
): WalletInteractionSession {
    val started = start(WalletInteractionInput("wallet", WalletEntryPoint.rawQr("fail-resume")))
    dispatch(started.sessionId, WalletInteractionAction.continueFlow())
    val failed = observe(started.sessionId).value
    require(failed.status == WalletInteractionStatus.Failed) { "interactionFailedWith must project Failed" }
    require(failed.error?.disposition == disposition) { "interactionFailedWith disposition mismatch" }
    return WalletInteractionSession(started.sessionId, failed)
}

private class FailingDispositionAdapter(
    private val disposition: WalletFailureDisposition,
) : WalletInteractionProtocolAdapter {
    override val capability: WalletProtocolCapability =
        WalletProtocolCapability(
            adapterId = "failing-disposition",
            protocol = WalletProtocol.CUSTOM,
            flowKinds = listOf(WalletInteractionFlowKind.CredentialReceive),
        )

    override suspend fun canHandle(entryPoint: WalletEntryPoint): WalletProtocolMatch = WalletProtocolMatch.strong()

    override suspend fun start(
        context: WalletInteractionContext,
        entryPoint: WalletEntryPoint,
    ): WalletInteractionSession {
        context.privateSessionStore.put(
            context.sessionId,
            WalletInteractionPrivateSessionData(
                namespace = capability.adapterId,
                values = mapOf("waiting" to "kept"),
            ),
        )
        return WalletInteractionSession(
            context.sessionId,
            context
                .baseState(
                    status = WalletInteractionStatus.CounterpartyNotice,
                    flowKind = WalletInteractionFlowKind.CredentialReceive,
                    protocol = WalletProtocol.CUSTOM,
                    adapterId = capability.adapterId,
                    entryPoint = entryPoint,
                ).copy(selectedCredentialConfigurationIds = listOf("waiting-config")),
        )
    }

    override suspend fun handle(
        context: WalletInteractionContext,
        sessionState: WalletInteractionState,
        action: WalletInteractionAction,
    ): WalletInteractionState =
        sessionState.copy(
            status = WalletInteractionStatus.Failed,
            revision = sessionState.revision + 1,
            terminal = true,
            error =
                WalletInteractionError(
                    code = "test.resume_failed",
                    disposition = disposition,
                ),
            selectedCredentialConfigurationIds = emptyList(),
        )
}
