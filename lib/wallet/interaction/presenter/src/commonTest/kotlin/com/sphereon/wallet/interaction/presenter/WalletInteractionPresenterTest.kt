/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.presenter

import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionActionType
import com.sphereon.wallet.interaction.WalletInteractionClient
import com.sphereon.wallet.interaction.WalletInteractionExecutionMode
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionSession
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import software.amazon.app.platform.presenter.BaseModel
import software.amazon.app.platform.presenter.Presenter
import software.amazon.app.platform.presenter.molecule.MoleculePresenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class WalletInteractionPresenterTest {
    @Test
    fun screenModelUsesLocalizationKeysForTitlesAndActions() {
        val model =
            WalletInteractionState(
                sessionId = WalletInteractionSessionId("s1"),
                walletInstanceId = "wallet",
                status = WalletInteractionStatus.CredentialOfferReview,
            ).toScreenModel()

        assertEquals("wallet.interaction.status.credential_offer_review", model.titleKey)
        assertEquals("wallet.interaction.action.continue", model.primaryAction?.labelKey)
        assertEquals("wallet.interaction.action.decline", model.secondaryAction?.labelKey)
        assertIs<BaseModel>(model)
    }

    @Test
    fun screenModelUsesStateSpecificDecisionActions() {
        val received =
            WalletInteractionState(
                sessionId = WalletInteractionSessionId("s1"),
                walletInstanceId = "wallet",
                status = WalletInteractionStatus.ReceivedCredentialReview,
            ).toScreenModel()
        val disclosure =
            WalletInteractionState(
                sessionId = WalletInteractionSessionId("s2"),
                walletInstanceId = "wallet",
                status = WalletInteractionStatus.DisclosureConsent,
            ).toScreenModel()
        val selection =
            WalletInteractionState(
                sessionId = WalletInteractionSessionId("s3"),
                walletInstanceId = "wallet",
                status = WalletInteractionStatus.CredentialSelection,
            ).toScreenModel()

        assertEquals("wallet.interaction.action.accept_credential", received.primaryAction?.labelKey)
        assertEquals(WalletInteractionActionType.ACCEPT_RECEIVED_CREDENTIAL, received.primaryAction?.action?.type)
        assertEquals("wallet.interaction.action.decline_credential", received.secondaryAction?.labelKey)
        assertEquals(WalletInteractionActionType.DECLINE_RECEIVED_CREDENTIAL, received.secondaryAction?.action?.type)
        assertEquals("wallet.interaction.action.share", disclosure.primaryAction?.labelKey)
        assertEquals(WalletInteractionActionType.CONTINUE, disclosure.primaryAction?.action?.type)
        assertEquals(null, selection.primaryAction)
    }

    @Test
    fun screenModelRejectsHardcodedDisplayTextForUiChrome() {
        val state =
            WalletInteractionState(
                sessionId = WalletInteractionSessionId("s1"),
                walletInstanceId = "wallet",
                status = WalletInteractionStatus.CredentialOfferReview,
            )

        assertFailsWith<IllegalArgumentException> {
            WalletInteractionScreenModel(
                sessionId = WalletInteractionSessionId("s1"),
                titleKey = "invalid_title",
                state = state,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WalletInteractionScreenAction(
                labelKey = "invalid_label",
                action = WalletInteractionAction.continueFlow(),
            )
        }
    }

    @Test
    fun appPlatformSessionPresenterExposesScreenModelAndDispatchesActions() =
        runTest {
            val sessionId = WalletInteractionSessionId("s1")
            val state =
                WalletInteractionState(
                    sessionId = sessionId,
                    walletInstanceId = "wallet",
                    status = WalletInteractionStatus.DisclosureConsent,
                )
            val client = RecordingWalletInteractionClient(state)
            val sessionPresenter = WalletInteractionSessionPresenter(client, backgroundScope, sessionId)
            val presenter: Presenter<WalletInteractionScreenModel> = sessionPresenter

            assertEquals("wallet.interaction.status.disclosure_consent", presenter.model.value.titleKey)

            sessionPresenter.dispatch(WalletInteractionAction.continueFlow())

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

        override suspend fun resume(sessionId: WalletInteractionSessionId): WalletInteractionSession = WalletInteractionSession(sessionId, state.value)

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
