/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.presenter

import androidx.compose.runtime.Composable
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.presenter.contracts.WalletInteractionScreenSource
import com.sphereon.wallet.interaction.presenter.contracts.WalletScreenModel
import com.sphereon.wallet.interaction.presenter.WalletScreenModelMapper
import com.sphereon.wallet.interaction.presenter.contracts.WalletScreenIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import software.amazon.app.platform.presenter.BaseModel
import software.amazon.app.platform.presenter.Presenter
import software.amazon.app.platform.presenter.molecule.MoleculePresenter
import software.amazon.app.platform.presenter.stateInPresenter

/**
 * Compose/Molecule adapter over the framework-free presenter contracts
 * ([WalletScreenModel], [WalletScreenModelMapper], [WalletInteractionScreenSource]). This type
 * carries no decision logic of its own - it is a thin [BaseModel] wrapper required by the
 * app-platform [Presenter] and [MoleculePresenter] bounds, which both require their model type to
 * implement [BaseModel].
 */
data class WalletInteractionScreenModel(
    val screen: WalletScreenModel,
) : BaseModel

/**
 * Adapts [WalletInteractionScreenSource] to the app-platform [Presenter] contract for a single
 * session: exposes its [WalletScreenModel] stream as a [BaseModel]-wrapped [StateFlow] and
 * forwards dispatched actions to the underlying screen source.
 */
class WalletInteractionSessionPresenter(
    private val screenSource: WalletInteractionScreenSource,
    scope: CoroutineScope,
    private val sessionId: WalletInteractionSessionId,
) : Presenter<WalletInteractionScreenModel> {
    private val screenModels: StateFlow<WalletScreenModel> = screenSource.models(sessionId.value)

    override val model: StateFlow<WalletInteractionScreenModel> =
        screenModels
            .map { WalletInteractionScreenModel(it) }
            .stateInPresenter(scope) { WalletInteractionScreenModel(screenModels.value) }

    suspend fun dispatch(intent: WalletScreenIntent) {
        screenSource.dispatch(sessionId.value, intent)
    }
}

/**
 * Molecule presenter that projects a [WalletInteractionState] snapshot into a
 * [WalletInteractionScreenModel] by delegating entirely to [WalletScreenModelMapper]. Suitable for
 * embedding within a wider Molecule composition that already owns the state stream.
 */
class WalletInteractionMoleculePresenter : MoleculePresenter<WalletInteractionState, WalletInteractionScreenModel> {
    @Composable
    override fun present(input: WalletInteractionState): WalletInteractionScreenModel = WalletInteractionScreenModel(WalletScreenModelMapper.map(input))
}
