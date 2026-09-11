/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.presenter

import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionClient
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionSession
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.presenter.DefaultWalletInteractionScreenSource
import com.sphereon.wallet.interaction.presenter.WalletScreenModelMapper
import com.sphereon.wallet.interaction.presenter.contracts.WalletScreenIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import software.amazon.app.platform.presenter.BaseModel
import software.amazon.app.platform.presenter.Presenter
import software.amazon.app.platform.presenter.molecule.MoleculePresenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WalletInteractionPresenterTest {
    @Test
    fun screenModelIsAThinBaseModelWrapperOverTheContractsMapper() {
        val state =
            WalletInteractionState(
                sessionId = WalletInteractionSessionId("s1"),
                walletUnitId = "wallet",
                status = WalletInteractionStatus.CredentialOfferReview,
            )
        val model = WalletInteractionScreenModel(WalletScreenModelMapper.map(state))

        assertEquals("wallet.interaction.status.credential_offer_review", model.screen.titleKey)
        // The generic interaction presenter remains a thin, protocol-neutral wrapper. The typed
        // receive presenter owns offer acceptance and its selected-credential invariants.
        assertNull(model.screen.primaryAction)
        assertEquals("wallet.interaction.action.decline", model.screen.secondaryAction?.labelKey)
        assertIs<BaseModel>(model)
    }

    @Test
    fun appPlatformSessionPresenterExposesScreenModelAndDispatchesActions() =
        runTest {
            val sessionId = WalletInteractionSessionId("s1")
            val state =
                WalletInteractionState(
                    sessionId = sessionId,
                    walletUnitId = "wallet",
                    status = WalletInteractionStatus.DisclosureConsent,
                )
            val client = RecordingWalletInteractionClient(state)
            val screenSource = DefaultWalletInteractionScreenSource(client, backgroundScope)
            val sessionPresenter = WalletInteractionSessionPresenter(screenSource, backgroundScope, sessionId)
            val presenter: Presenter<WalletInteractionScreenModel> = sessionPresenter

            assertEquals("wallet.interaction.status.disclosure_consent", presenter.model.value.screen.titleKey)

            sessionPresenter.dispatch(WalletScreenIntent.CONTINUE)

            assertEquals(WalletInteractionAction.continueFlow(), client.dispatched.single())
        }

    @Test
    fun moleculePresenterUsesWalletInteractionStateAsInputModelProjection() {
        val molecule: MoleculePresenter<WalletInteractionState, WalletInteractionScreenModel> = WalletInteractionMoleculePresenter()

        assertIs<WalletInteractionMoleculePresenter>(molecule)
    }

    private class RecordingWalletInteractionClient(
        initialState: WalletInteractionState,
    ) : WalletInteractionClient {
        private val state = MutableStateFlow(initialState)
        val dispatched = mutableListOf<WalletInteractionAction>()

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
