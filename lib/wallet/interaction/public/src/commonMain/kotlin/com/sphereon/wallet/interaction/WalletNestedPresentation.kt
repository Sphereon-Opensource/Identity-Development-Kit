/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlinx.serialization.json.JsonObject

data class WalletNestedPresentationRequest(
    val protocol: WalletProtocol,
    val interactionType: String? = null,
    val requestObject: JsonObject? = null,
    val requestUri: String? = null,
) {
    init {
        require(requestObject != null || !requestUri.isNullOrBlank()) {
            "wallet_interaction_nested_presentation_request_missing"
        }
    }
}

data class WalletNestedPresentationChallenge(
    val credentialSelection: WalletCredentialSelectionRequest? = null,
    val disclosure: WalletDisclosureSummary? = null,
    val expiresInSeconds: Int? = null,
) {
    val status: WalletInteractionStatus
        get() =
            if (credentialSelection?.requirements.orEmpty().isEmpty()) {
                WalletInteractionStatus.DisclosureConsent
            } else {
                WalletInteractionStatus.CredentialSelection
            }
}

data class WalletNestedPresentationResponse(
    val response: JsonObject,
)

interface WalletNestedPresentationExecutor {
    suspend fun preparePresentation(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        request: WalletNestedPresentationRequest,
    ): WalletNestedPresentationExecutionResult<WalletNestedPresentationChallenge>

    suspend fun createPresentationResponse(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        action: WalletInteractionAction,
    ): WalletNestedPresentationExecutionResult<WalletNestedPresentationResponse>

    companion object {
        val notConfigured: WalletNestedPresentationExecutor =
            object : WalletNestedPresentationExecutor {
                override suspend fun preparePresentation(
                    context: WalletInteractionContext,
                    state: WalletInteractionState,
                    request: WalletNestedPresentationRequest,
                ): WalletNestedPresentationExecutionResult<WalletNestedPresentationChallenge> =
                    WalletNestedPresentationExecutionResult.Failed(
                        code = "wallet_nested_presentation.not_configured",
                        messageKey = "wallet.interaction.error.nested_presentation_not_configured",
                        retryable = true,
                    )

                override suspend fun createPresentationResponse(
                    context: WalletInteractionContext,
                    state: WalletInteractionState,
                    action: WalletInteractionAction,
                ): WalletNestedPresentationExecutionResult<WalletNestedPresentationResponse> =
                    WalletNestedPresentationExecutionResult.Failed(
                        code = "wallet_nested_presentation.not_configured",
                        messageKey = "wallet.interaction.error.nested_presentation_not_configured",
                        retryable = true,
                    )
            }
    }
}

sealed class WalletNestedPresentationExecutionResult<out T> {
    data class Success<T>(
        val value: T,
    ) : WalletNestedPresentationExecutionResult<T>()

    data class Failed(
        val code: String,
        val messageKey: String,
        val retryable: Boolean = false,
        val arguments: Map<String, String> = emptyMap(),
    ) : WalletNestedPresentationExecutionResult<Nothing>()
}
