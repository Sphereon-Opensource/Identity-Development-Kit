/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.presenter.contracts

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Pure renderer port. Domain clients and protocol actions are adapted in the presenter module. */
interface WalletInteractionScreenSource {
    fun models(sessionId: String): StateFlow<WalletScreenModel>

    suspend fun dispatch(sessionId: String, intent: WalletScreenIntent): WalletInteractionPresentationActionResult
}

@Serializable
data class WalletInteractionPresentationFailure(
    val code: String,
    val messageKey: String,
    val retryable: Boolean = false,
) {
    init {
        require(code.isNotBlank()) { "wallet_interaction_presentation_failure_code_blank" }
        requireScreenLocalizationKey("messageKey", messageKey)
    }
}

@Serializable
sealed interface WalletInteractionPresentationActionResult {
    @Serializable
    @SerialName("accepted")
    data object Accepted : WalletInteractionPresentationActionResult

    @Serializable
    @SerialName("rejected")
    data class Rejected(val failure: WalletInteractionPresentationFailure) : WalletInteractionPresentationActionResult
}
