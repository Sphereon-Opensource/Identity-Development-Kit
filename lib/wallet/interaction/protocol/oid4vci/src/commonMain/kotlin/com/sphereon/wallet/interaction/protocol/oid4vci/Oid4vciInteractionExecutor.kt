/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.wallet.interaction.WalletCredentialPreview
import com.sphereon.wallet.interaction.WalletCredentialSelectionRequest
import com.sphereon.wallet.interaction.WalletDisclosureSummary
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionState

interface Oid4vciIssuanceExecutor {
    suspend fun requestCredential(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        action: WalletInteractionAction? = null,
    ): Oid4vciIssuanceExecutionResult

    /**
     * Wallet-initiated credential refresh/reissuance: re-requests [credentialRecordId] from its
     * issuer using a stored OAuth2 refresh token, rather than an offer the wallet is currently
     * receiving. Reuses the SAME [Oid4vciIssuanceExecutionResult] vocabulary as [requestCredential]
     * (`Received` / `Failed`); a conforming implementation never returns
     * `Deferred`/`AuthorizationRequired`/`NestedPresentationRequired` from this entry point.
     *
     * Defaults to a typed "not configured" failure so implementations that do not support refresh
     * (including [notConfigured] and any pre-existing test double) keep compiling and degrade
     * gracefully instead of crashing.
     */
    suspend fun refreshCredential(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        credentialRecordId: String,
    ): Oid4vciIssuanceExecutionResult =
        Oid4vciIssuanceExecutionResult.Failed(
            code = "oid4vci.execution_not_configured",
            messageKey = "wallet.interaction.error.oid4vci_execution_not_configured",
            retryable = true,
        )

    suspend fun notifyCredentialAccepted(
        context: WalletInteractionContext,
        state: WalletInteractionState,
    ): Oid4vciIssuerNotificationResult = Oid4vciIssuerNotificationResult.NotSupported

    suspend fun notifyCredentialDeclined(
        context: WalletInteractionContext,
        state: WalletInteractionState,
    ): Oid4vciIssuerNotificationResult = Oid4vciIssuerNotificationResult.NotSupported

    companion object {
        val notConfigured: Oid4vciIssuanceExecutor =
            object : Oid4vciIssuanceExecutor {
                override suspend fun requestCredential(
                    context: WalletInteractionContext,
                    state: WalletInteractionState,
                    action: WalletInteractionAction?,
                ): Oid4vciIssuanceExecutionResult =
                    Oid4vciIssuanceExecutionResult.Failed(
                        code = "oid4vci.execution_not_configured",
                        messageKey = "wallet.interaction.error.oid4vci_execution_not_configured",
                        retryable = true,
                    )
            }
    }
}

sealed class Oid4vciIssuanceExecutionResult {
    data class Received(
        val credentialPreview: List<WalletCredentialPreview> = emptyList(),
        val titleKey: String = "wallet.interaction.status.credential_received",
    ) : Oid4vciIssuanceExecutionResult()

    data class Deferred(
        val intervalSeconds: Int? = null,
        val attempt: Int? = null,
        val resumable: Boolean = true,
    ) : Oid4vciIssuanceExecutionResult()

    data class AuthorizationRequired(
        val authorizationUrl: String,
    ) : Oid4vciIssuanceExecutionResult()

    data class NestedPresentationRequired(
        val credentialSelection: WalletCredentialSelectionRequest? = null,
        val disclosure: WalletDisclosureSummary? = null,
        val expiresInSeconds: Int? = null,
    ) : Oid4vciIssuanceExecutionResult()

    data class Failed(
        val code: String,
        val messageKey: String,
        val retryable: Boolean = false,
        val arguments: Map<String, String> = emptyMap(),
    ) : Oid4vciIssuanceExecutionResult()
}

sealed class Oid4vciIssuerNotificationResult {
    data object Sent : Oid4vciIssuerNotificationResult()

    data object NotSupported : Oid4vciIssuerNotificationResult()

    data class Failed(
        val code: String,
        val messageKey: String,
        val retryable: Boolean = true,
        val arguments: Map<String, String> = emptyMap(),
    ) : Oid4vciIssuerNotificationResult()
}
