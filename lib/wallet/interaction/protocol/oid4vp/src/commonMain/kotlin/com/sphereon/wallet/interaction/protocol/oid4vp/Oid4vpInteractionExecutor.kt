/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionFailureCodes
import com.sphereon.wallet.interaction.WalletInteractionState
import kotlinx.serialization.json.JsonObject

interface Oid4vpPresentationExecutor {
    suspend fun submitPresentation(
        context: WalletInteractionContext,
        state: WalletInteractionState,
    ): Oid4vpPresentationExecutionResult

    companion object {
        val notConfigured: Oid4vpPresentationExecutor =
            object : Oid4vpPresentationExecutor {
                override suspend fun submitPresentation(
                    context: WalletInteractionContext,
                    state: WalletInteractionState,
                ): Oid4vpPresentationExecutionResult =
                    Oid4vpPresentationExecutionResult.Failed(
                        code = WalletInteractionFailureCodes.OID4VP_EXECUTION_NOT_CONFIGURED,
                        messageKey = "wallet.interaction.error.oid4vp_execution_not_configured",
                        retryable = true,
                    )
            }
    }
}

sealed class Oid4vpPresentationExecutionResult {
    data class Submitted(
        val titleKey: String = "wallet.interaction.status.presentation_shared",
    ) : Oid4vpPresentationExecutionResult()

    data class Sharing(
        val titleKey: String = "wallet.interaction.status.sharing",
    ) : Oid4vpPresentationExecutionResult()

    data class RedirectRequired(
        val redirectUri: String,
    ) : Oid4vpPresentationExecutionResult()

    data class DigitalCredentialResponse(
        val data: JsonObject,
    ) : Oid4vpPresentationExecutionResult()

    data class Failed(
        val code: String,
        val messageKey: String,
        val retryable: Boolean = false,
        val arguments: Map<String, String> = emptyMap(),
    ) : Oid4vpPresentationExecutionResult()
}
