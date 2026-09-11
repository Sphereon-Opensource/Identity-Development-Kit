/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.interaction.protocol.iso18013

import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionFailureCodes
import com.sphereon.wallet.interaction.WalletInteractionState

/**
 * Public ISO 18013 disclosure-execution contract.
 *
 * Lives in interaction-public so API-only holder-wiring can multibind executors without
 * depending on the iso18013 protocol implementation module.
 */
interface Iso18013DisclosureExecutor {
    suspend fun sendDeviceResponse(
        context: WalletInteractionContext,
        state: WalletInteractionState,
    ): Iso18013DisclosureExecutionResult

    companion object {
        val notConfigured: Iso18013DisclosureExecutor =
            object : Iso18013DisclosureExecutor {
                override suspend fun sendDeviceResponse(
                    context: WalletInteractionContext,
                    state: WalletInteractionState,
                ): Iso18013DisclosureExecutionResult =
                    Iso18013DisclosureExecutionResult.Failed(
                        code = WalletInteractionFailureCodes.ISO18013_EXECUTION_NOT_CONFIGURED,
                        messageKey = "wallet.interaction.error.iso18013_execution_not_configured",
                        retryable = true,
                    )
            }
    }
}

sealed class Iso18013DisclosureExecutionResult {
    data class Sent(
        val titleKey: String = "wallet.interaction.status.mdoc_shared",
    ) : Iso18013DisclosureExecutionResult()

    data class Sharing(
        val titleKey: String = "wallet.interaction.status.sharing",
    ) : Iso18013DisclosureExecutionResult()

    data class RedirectRequired(
        val redirectUri: String,
    ) : Iso18013DisclosureExecutionResult()

    data class Failed(
        val code: String,
        val messageKey: String,
        val retryable: Boolean = false,
        val arguments: Map<String, String> = emptyMap(),
    ) : Iso18013DisclosureExecutionResult()
}
