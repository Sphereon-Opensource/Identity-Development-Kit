/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.presenter.contracts

import kotlinx.serialization.Serializable

/**
 * Framework-free UI view-state for a wallet interaction session. Pure Kotlin, no Compose, no
 * Molecule, no app-platform presenter types - every UI kit (Compose, React/Next.js, and any
 * future frontend) consumes this same contract through [WalletInteractionScreenSource]. Domain
 * mapping belongs to the presenter implementation artifact.
 */
@Serializable
data class WalletScreenModel(
    val sessionId: String,
    val revision: Long,
    val flowKind: WalletInteractionFlowKindPresentation?,
    val status: WalletInteractionStatusPresentation,
    val titleKey: String,
    val titleArguments: Map<String, String> = emptyMap(),
    val subtitleKey: String? = null,
    val subtitleArguments: Map<String, String> = emptyMap(),
    val primaryAction: WalletScreenAction? = null,
    val secondaryAction: WalletScreenAction? = null,
    val projection: WalletInteractionScreenProjection,
) {
    init {
        requireScreenLocalizationKey("titleKey", titleKey)
        requireScreenLocalizationKey("subtitleKey", subtitleKey)
    }
}

@Serializable
enum class WalletInteractionFlowKindPresentation { CREDENTIAL_RECEIVE, CREDENTIAL_PRESENT, ATTENDED_PRESENT }

@Serializable
enum class WalletInteractionStatusPresentation {
    RESOLVING_ENTRY_POINT,
    IMPLEMENTATION_CHOICE_REQUIRED,
    UNSUPPORTED_ENTRY_POINT,
    COUNTERPARTY_NOTICE,
    TRUST_REVIEW,
    CREDENTIAL_OFFER_REVIEW,
    AUTHORIZATION_REQUIRED,
    TRANSACTION_CODE_REQUIRED,
    CREDENTIAL_PREVIEW,
    CREDENTIAL_SELECTION,
    DISCLOSURE_CONSENT,
    SECURITY_UNLOCK_REQUIRED,
    DEFERRED_RETRIEVAL_PENDING,
    SHARING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

@Serializable
data class WalletSensitiveInputRefPresentation(val value: String) {
    init {
        require(value.isNotBlank()) { "wallet_sensitive_input_ref_blank" }
    }

    override fun toString(): String = "WalletSensitiveInputRefPresentation([redacted])"
}

/**
 * A user intent surfaced as a screen action: a localized label plus a safe
 * [WalletScreenIntent] to dispatch back through [WalletInteractionScreenSource.dispatch]
 * when the user activates it.
 */
@Serializable
data class WalletScreenAction(
    val labelKey: String,
    val intent: WalletScreenIntent,
    val arguments: Map<String, String> = emptyMap(),
) {
    init {
        requireScreenLocalizationKey("labelKey", labelKey)
    }
}

@Serializable
enum class WalletScreenIntent {
    CONTINUE,
    DECLINE,
    RETRY_DEFERRED_RETRIEVAL,
}

internal fun requireScreenLocalizationKey(
    fieldName: String,
    value: String?,
) {
    if (value == null) return
    require(screenLocalizationKeyPattern.matches(value)) {
        "wallet_screen_localization_key_invalid"
    }
}

private val screenLocalizationKeyPattern = Regex("""[a-z][a-z0-9_-]*(\.[a-z0-9_-]+)+""")
