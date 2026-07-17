/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.presenter

import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionClient
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.presenter.contracts.WalletInteractionPresentationActionResult
import com.sphereon.wallet.interaction.presenter.contracts.WalletInteractionPresentationFailure
import com.sphereon.wallet.interaction.presenter.contracts.WalletInteractionScreenProjection
import com.sphereon.wallet.interaction.presenter.contracts.WalletInteractionScreenSource
import com.sphereon.wallet.interaction.presenter.contracts.WalletScreenIntent
import com.sphereon.wallet.interaction.presenter.contracts.WalletScreenModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Coroutine-based, framework-free screen source: projects a [WalletInteractionClient] session's
 * state into a [StateFlow] of [WalletScreenModel] via [WalletScreenModelMapper] and forwards
 * screen intents back to the client. No Compose, no Molecule, no app-platform presenter types -
 * headless integrators and every UI kit (Compose, React/Next.js, ...) consume this same contract.
 */
class DefaultWalletInteractionScreenSource(
    private val client: WalletInteractionClient,
    private val scope: CoroutineScope,
) : WalletInteractionScreenSource {
    override fun models(sessionId: String): StateFlow<WalletScreenModel> {
        val observed = client.observe(WalletInteractionSessionId(sessionId))
        return observed
            .map { WalletScreenModelMapper.map(it) }
            .stateIn(scope, SharingStarted.WhileSubscribed(), WalletScreenModelMapper.map(observed.value))
    }

    override suspend fun dispatch(
        sessionId: String,
        intent: WalletScreenIntent,
    ): WalletInteractionPresentationActionResult =
        try {
            client.dispatch(WalletInteractionSessionId(sessionId), intent.toInteractionAction())
            WalletInteractionPresentationActionResult.Accepted
        } catch (expected: Exception) {
            WalletInteractionPresentationActionResult.Rejected(
                WalletInteractionPresentationFailure(
                    code = "wallet_interaction_action_rejected",
                    messageKey = "wallet.interaction.action.rejected",
                ),
            )
        }
}

internal fun WalletScreenIntent.toInteractionAction(): WalletInteractionAction =
    when (this) {
        WalletScreenIntent.CONTINUE -> WalletInteractionAction.continueFlow()
        WalletScreenIntent.DECLINE -> WalletInteractionAction.decline()
        WalletScreenIntent.RETRY_DEFERRED_RETRIEVAL -> WalletInteractionAction.retryDeferredRetrieval()
    }
