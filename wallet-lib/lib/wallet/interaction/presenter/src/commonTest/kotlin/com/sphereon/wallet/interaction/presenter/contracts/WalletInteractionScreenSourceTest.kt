/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.presenter.contracts

import com.sphereon.wallet.interaction.presenter.DefaultWalletInteractionScreenSource
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionClient
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionSession
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletInteractionScreenSourceTest {
    @Test
    fun modelsProjectsClientStateUpdatesThroughTheMapperForAnActiveSubscriber() =
        runTest {
            val sessionId = WalletInteractionSessionId("s1")
            val client =
                FakeWalletInteractionClient(
                    WalletInteractionState(sessionId = sessionId, walletUnitId = "wallet", status = WalletInteractionStatus.CredentialOfferReview),
                )
            val source = DefaultWalletInteractionScreenSource(client, backgroundScope)
            val collected = mutableListOf<WalletScreenModel>()
            val job = backgroundScope.launch { source.models(sessionId.value).collect { collected += it } }
            runCurrent()

            assertEquals("wallet.interaction.status.credential_offer_review", collected.last().titleKey)

            client.push(WalletInteractionState(sessionId = sessionId, walletUnitId = "wallet", status = WalletInteractionStatus.DisclosureConsent))
            runCurrent()

            assertEquals("wallet.interaction.status.disclosure_consent", collected.last().titleKey)
            job.cancel()
        }

    @Test
    fun dispatchForwardsScreenActionsToTheClient() =
        runTest {
            val sessionId = WalletInteractionSessionId("s1")
            val client =
                FakeWalletInteractionClient(
                    WalletInteractionState(sessionId = sessionId, walletUnitId = "wallet", status = WalletInteractionStatus.DisclosureConsent),
                )
            val source = DefaultWalletInteractionScreenSource(client, backgroundScope)

            source.dispatch(sessionId.value, WalletScreenIntent.CONTINUE)

            assertEquals(WalletInteractionAction.continueFlow(), client.dispatched.single())
        }

    private class FakeWalletInteractionClient(
        initialState: WalletInteractionState,
    ) : WalletInteractionClient {
        private val state = MutableStateFlow(initialState)
        val dispatched = mutableListOf<WalletInteractionAction>()

        fun push(next: WalletInteractionState) {
            state.value = next
        }

        override suspend fun start(input: WalletInteractionInput): WalletInteractionSession = WalletInteractionSession(state.value.sessionId, state.value)

        override suspend fun load(sessionId: WalletInteractionSessionId): WalletInteractionSession = WalletInteractionSession(sessionId, state.value)

        override suspend fun dispatch(
            sessionId: WalletInteractionSessionId,
            action: WalletInteractionAction,
        ) {
            dispatched += action
        }

        override suspend fun cancel(sessionId: WalletInteractionSessionId) {
            dispatched += WalletInteractionAction.cancel()
        }

        override fun observe(sessionId: WalletInteractionSessionId): StateFlow<WalletInteractionState> = state.asStateFlow()
    }
}
