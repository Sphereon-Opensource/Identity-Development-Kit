/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.presenter

import androidx.compose.runtime.Composable
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionClient
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import software.amazon.app.platform.presenter.BaseModel
import software.amazon.app.platform.presenter.Presenter
import software.amazon.app.platform.presenter.molecule.MoleculePresenter
import software.amazon.app.platform.presenter.stateInPresenter

data class WalletInteractionScreenModel(
    val sessionId: WalletInteractionSessionId,
    val titleKey: String,
    val titleArguments: Map<String, String> = emptyMap(),
    val subtitleKey: String? = null,
    val subtitleArguments: Map<String, String> = emptyMap(),
    val primaryAction: WalletInteractionScreenAction? = null,
    val secondaryAction: WalletInteractionScreenAction? = null,
    val state: WalletInteractionState,
) : BaseModel {
    init {
        requirePresenterLocalizationKey("titleKey", titleKey)
        requirePresenterLocalizationKey("subtitleKey", subtitleKey)
    }
}

data class WalletInteractionScreenAction(
    val labelKey: String,
    val action: WalletInteractionAction,
    val arguments: Map<String, String> = emptyMap(),
) {
    init {
        requirePresenterLocalizationKey("labelKey", labelKey)
    }
}

class WalletInteractionPresenter(
    private val client: WalletInteractionClient,
    private val scope: CoroutineScope,
) {
    fun models(sessionId: WalletInteractionSessionId): StateFlow<WalletInteractionScreenModel> = WalletInteractionSessionPresenter(client, scope, sessionId).model

    suspend fun dispatch(
        sessionId: WalletInteractionSessionId,
        action: WalletInteractionAction,
    ) {
        client.dispatch(sessionId, action)
    }
}

class WalletInteractionSessionPresenter(
    private val client: WalletInteractionClient,
    private val scope: CoroutineScope,
    private val sessionId: WalletInteractionSessionId,
) : Presenter<WalletInteractionScreenModel> {
    override val model: StateFlow<WalletInteractionScreenModel> =
        client
            .observe(sessionId)
            .map { it.toScreenModel() }
            .stateInPresenter(scope) { client.observe(sessionId).value.toScreenModel() }

    suspend fun dispatch(action: WalletInteractionAction) {
        client.dispatch(sessionId, action)
    }
}

class WalletInteractionMoleculePresenter : MoleculePresenter<WalletInteractionState, WalletInteractionScreenModel> {
    @Composable
    override fun present(input: WalletInteractionState): WalletInteractionScreenModel = input.toScreenModel()
}

fun WalletInteractionState.toScreenModel(): WalletInteractionScreenModel {
    val displayMessage = message
    val displayError = error
    val titleKey = displayMessage?.titleKey ?: status.titleKey()
    val titleArguments = displayMessage?.arguments?.takeIf { displayMessage.titleKey != null } ?: titleArguments()
    val subtitleKey = displayMessage?.textKey ?: displayError?.messageKey
    val subtitleArguments =
        when {
            displayMessage?.textKey != null -> displayMessage.arguments
            displayError?.messageKey != null -> displayError.arguments
            else -> emptyMap()
        }

    val primary =
        when (status) {
            WalletInteractionStatus.ImplementationChoiceRequired -> {
                null
            }

            WalletInteractionStatus.TxCodeRequired -> {
                null
            }

            WalletInteractionStatus.CredentialSelection -> {
                null
            }

            WalletInteractionStatus.SecurityUnlockRequired -> {
                null
            }

            WalletInteractionStatus.DisclosureConsent -> {
                WalletInteractionScreenAction("wallet.interaction.action.share", WalletInteractionAction.continueFlow())
            }

            WalletInteractionStatus.DeferredRetrievalPending -> {
                WalletInteractionScreenAction("wallet.interaction.action.retry_deferred", WalletInteractionAction.retryDeferredRetrieval())
            }

            WalletInteractionStatus.ReceivedCredentialReview -> {
                WalletInteractionScreenAction("wallet.interaction.action.accept_credential", WalletInteractionAction.acceptReceivedCredential())
            }

            WalletInteractionStatus.Completed,
            WalletInteractionStatus.Cancelled,
            WalletInteractionStatus.Failed,
            WalletInteractionStatus.UnsupportedEntryPoint,
            -> {
                null
            }

            else -> {
                WalletInteractionScreenAction("wallet.interaction.action.continue", WalletInteractionAction.continueFlow())
            }
        }

    val secondary =
        when {
            terminal || status == WalletInteractionStatus.ResolvingEntryPoint -> {
                null
            }

            status == WalletInteractionStatus.ReceivedCredentialReview -> {
                WalletInteractionScreenAction("wallet.interaction.action.decline_credential", WalletInteractionAction.declineReceivedCredential())
            }

            else -> {
                WalletInteractionScreenAction("wallet.interaction.action.decline", WalletInteractionAction.decline())
            }
        }

    return WalletInteractionScreenModel(
        sessionId = sessionId,
        titleKey = titleKey,
        titleArguments = titleArguments,
        subtitleKey = subtitleKey,
        subtitleArguments = subtitleArguments,
        primaryAction = primary,
        secondaryAction = secondary,
        state = this,
    )
}

private fun WalletInteractionStatus.titleKey(): String =
    when (this) {
        WalletInteractionStatus.ResolvingEntryPoint -> "wallet.interaction.status.resolving_entry_point"
        WalletInteractionStatus.ImplementationChoiceRequired -> "wallet.interaction.status.implementation_choice_required"
        WalletInteractionStatus.UnsupportedEntryPoint -> "wallet.interaction.status.unsupported_entry_point"
        WalletInteractionStatus.CounterpartyNotice -> "wallet.interaction.status.counterparty_notice"
        WalletInteractionStatus.TrustReview -> "wallet.interaction.status.trust_review"
        WalletInteractionStatus.CredentialOfferReview -> "wallet.interaction.status.credential_offer_review"
        WalletInteractionStatus.AuthorizationRequired -> "wallet.interaction.status.authorization_required"
        WalletInteractionStatus.TxCodeRequired -> "wallet.interaction.status.tx_code_required"
        WalletInteractionStatus.CredentialPreview -> "wallet.interaction.status.credential_preview"
        WalletInteractionStatus.CredentialSelection -> "wallet.interaction.status.credential_selection"
        WalletInteractionStatus.DisclosureConsent -> "wallet.interaction.status.disclosure_consent"
        WalletInteractionStatus.SecurityUnlockRequired -> "wallet.interaction.status.security_unlock_required"
        WalletInteractionStatus.DeferredRetrievalPending -> "wallet.interaction.status.deferred_retrieval_pending"
        WalletInteractionStatus.ReceivedCredentialReview -> "wallet.interaction.status.received_credential_review"
        WalletInteractionStatus.Sharing -> "wallet.interaction.status.sharing"
        WalletInteractionStatus.Completed -> "wallet.interaction.status.completed"
        WalletInteractionStatus.Cancelled -> "wallet.interaction.status.cancelled"
        WalletInteractionStatus.Failed -> "wallet.interaction.status.failed"
    }

private fun WalletInteractionState.titleArguments(): Map<String, String> =
    buildMap {
        counterparty?.displayName?.let { put("counterpartyDisplayName", it) }
        credentialOffer?.issuer?.displayName?.let { put("issuerDisplayName", it) }
        adapterId?.let { put("adapterId", it) }
        protocol?.name?.let { put("protocol", it) }
    }

private fun requirePresenterLocalizationKey(
    fieldName: String,
    value: String?,
) {
    if (value == null) return
    require(presenterLocalizationKeyPattern.matches(value)) {
        "wallet_interaction_presenter_localization_key_invalid"
    }
}

private val presenterLocalizationKeyPattern = Regex("""[a-z][a-z0-9_-]*(\.[a-z0-9_-]+)+""")
