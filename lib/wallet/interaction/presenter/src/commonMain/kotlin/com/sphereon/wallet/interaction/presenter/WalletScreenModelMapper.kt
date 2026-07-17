/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.presenter

import com.sphereon.wallet.interaction.WalletInteractionActivityType
import com.sphereon.wallet.interaction.WalletCounterpartyRole
import com.sphereon.wallet.interaction.WalletCounterpartySummary
import com.sphereon.wallet.interaction.WalletCredentialBranding
import com.sphereon.wallet.interaction.WalletCredentialPreview
import com.sphereon.wallet.interaction.WalletSecurityChallengeKind
import com.sphereon.wallet.interaction.WalletTrustPolicyAction
import com.sphereon.wallet.interaction.WalletTrustSourceType
import com.sphereon.wallet.interaction.WalletTrustStatus
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletInteractionFlowKind
import com.sphereon.wallet.interaction.presenter.contracts.*

/**
 * Pure mapping from [WalletInteractionState] to the framework-free [WalletScreenModel] UI
 * contract. Holds the per-status decision table for title/subtitle localization keys and
 * primary/secondary screen actions across the receive, present, proximity and unlock flows. No
 * client, no coroutine, no UI framework dependency - a plain function of its input.
 */
object WalletScreenModelMapper {
    fun map(state: WalletInteractionState): WalletScreenModel {
        val displayMessage = state.message
        val displayError = state.error
        val loginPresentation = state.activity?.type == WalletInteractionActivityType.LOGIN
        val titleKey = displayMessage?.titleKey ?: state.status.screenTitleKey(loginPresentation)
        val titleArguments = displayMessage?.arguments?.takeIf { displayMessage.titleKey != null } ?: state.screenTitleArguments()
        val subtitleKey = displayMessage?.textKey ?: displayError?.messageKey
        val subtitleArguments =
            when {
                displayMessage?.textKey != null -> displayMessage.arguments
                displayError?.messageKey != null -> displayError.arguments
                else -> emptyMap()
            }

        return WalletScreenModel(
            sessionId = state.sessionId.value,
            revision = state.revision,
            flowKind = state.flowKind?.toPresentation(),
            status = state.status.toPresentation(),
            titleKey = titleKey,
            titleArguments = titleArguments,
            subtitleKey = subtitleKey,
            subtitleArguments = subtitleArguments,
            primaryAction = primaryAction(state, loginPresentation),
            secondaryAction = secondaryAction(state),
            projection = projection(state),
        )
    }

    private fun primaryAction(
        state: WalletInteractionState,
        loginPresentation: Boolean,
    ): WalletScreenAction? =
        when (state.status) {
            WalletInteractionStatus.ImplementationChoiceRequired -> {
                null
            }

            WalletInteractionStatus.TxCodeRequired -> {
                null
            }

            WalletInteractionStatus.CredentialOfferReview -> {
                null
            }

            WalletInteractionStatus.AuthorizationRequired -> {
                null
            }

            WalletInteractionStatus.CredentialSelection -> {
                null
            }

            WalletInteractionStatus.SecurityUnlockRequired -> {
                null
            }

            WalletInteractionStatus.DisclosureConsent -> {
                WalletScreenAction(
                    if (loginPresentation) {
                        "wallet.interaction.action.sign_in"
                    } else {
                        "wallet.interaction.action.share"
                    },
                    WalletScreenIntent.CONTINUE,
                )
            }

            WalletInteractionStatus.DeferredRetrievalPending -> {
                WalletScreenAction("wallet.interaction.action.retry_deferred", WalletScreenIntent.RETRY_DEFERRED_RETRIEVAL)
            }

            WalletInteractionStatus.Completed,
            WalletInteractionStatus.Cancelled,
            WalletInteractionStatus.Failed,
            WalletInteractionStatus.UnsupportedEntryPoint,
            -> {
                null
            }

            else -> {
                WalletScreenAction("wallet.interaction.action.continue", WalletScreenIntent.CONTINUE)
            }
        }

    private fun secondaryAction(state: WalletInteractionState): WalletScreenAction? =
        when {
            state.terminal || state.status == WalletInteractionStatus.ResolvingEntryPoint -> {
                null
            }

            else -> {
                WalletScreenAction("wallet.interaction.action.decline", WalletScreenIntent.DECLINE)
            }
        }
}

private fun projection(state: WalletInteractionState): WalletInteractionScreenProjection =
    when (state.status) {
        WalletInteractionStatus.ImplementationChoiceRequired ->
            WalletInteractionScreenProjection.ImplementationChoice(
                state.implementationChoices.map {
                    WalletImplementationChoicePresentation(
                        implementationId = it.adapterId,
                        labelKey = it.labelKey,
                        arguments = it.arguments,
                    )
                },
            )
        WalletInteractionStatus.CounterpartyNotice,
        WalletInteractionStatus.TrustReview,
        -> WalletInteractionScreenProjection.PartyReview(
            state.counterparty.toPresentation(),
            state.trust.toPresentation(),
            state.counterpartyEncounter.toPresentation(),
            state.offerPresentation(),
        )
        WalletInteractionStatus.CredentialOfferReview -> {
            state.offerPresentation()?.let { offer ->
                WalletInteractionScreenProjection.OfferReview(
                    offer = offer,
                    trust = state.trust.toPresentation(),
                    encounter = state.counterpartyEncounter.toPresentation(),
                )
            } ?: WalletInteractionScreenProjection.Unavailable(state.status.toPresentation(), "wallet_interaction_offer_projection_missing")
        }
        WalletInteractionStatus.AuthorizationRequired ->
            state.authorizationHandoffRef?.let { ref ->
                WalletInteractionScreenProjection.AuthorizationHandoff(
                    state.counterparty.toPresentation(),
                    WalletSensitiveInputRefPresentation(ref.value),
                )
            } ?: WalletInteractionScreenProjection.Unavailable(state.status.toPresentation(), "wallet_interaction_authorization_handoff_ref_missing")
        WalletInteractionStatus.TxCodeRequired ->
            WalletInteractionScreenProjection.TransactionCode(
                inputMode =
                    if (state.txCode?.inputMode.equals("numeric", ignoreCase = true)) {
                        WalletTransactionCodeInputModePresentation.NUMERIC
                    } else {
                        WalletTransactionCodeInputModePresentation.TEXT
                    },
                expectedLength = state.txCode?.length,
                descriptionKey = state.txCode?.descriptionKey,
                descriptionArguments = state.txCode?.arguments.orEmpty(),
            )
        WalletInteractionStatus.CredentialPreview -> WalletInteractionScreenProjection.CredentialReview(state.credentialPreview.map { it.toPresentation() })
        WalletInteractionStatus.CredentialSelection ->
            WalletInteractionScreenProjection.CredentialSelection(
                groups =
                    state.credentialSelection?.credentialSets.orEmpty().map { group ->
                        WalletCredentialSelectionGroupPresentation(
                            groupId = group.id,
                            required = group.required,
                            options =
                                group.options.map { option ->
                                    WalletCredentialSelectionOptionPresentation(
                                        requirementIds = option.requirementIds,
                                        satisfiable = option.satisfiable,
                                    )
                                },
                        )
                    },
                satisfiable = state.credentialSelection?.satisfiable == true,
            )
        WalletInteractionStatus.DisclosureConsent -> {
            val disclosure = state.disclosure
            WalletInteractionScreenProjection.Disclosure(
                verifier = disclosure?.verifier.toPresentation(),
                info = disclosure?.requestedClaims.orEmpty().map { it.toPresentation() },
                selectedCredentialIds = disclosure?.selectedCredentialIds.orEmpty(),
                privacy =
                    WalletPrivacyPresentation(
                        scopeId = state.sessionId.value,
                        visibility =
                            if (disclosure?.claimValuesRevealed == true) {
                                WalletInfoVisibilityPresentation.REVEALED
                            } else {
                                WalletInfoVisibilityPresentation.HIDDEN
                            },
                        defaultVisibility = WalletInfoVisibilityPresentation.HIDDEN,
                        policyForcesHiddenDefault = false,
                        valuesWillBeDisclosedAfterApproval = true,
                    ),
            )
        }
        WalletInteractionStatus.SecurityUnlockRequired -> {
            val challenge = state.securityChallenge
            if (challenge == null) {
                WalletInteractionScreenProjection.Unavailable(state.status.toPresentation(), "wallet_interaction_security_challenge_missing")
            } else {
                val method =
                    when (challenge.kind) {
                        WalletSecurityChallengeKind.PASSKEY -> WalletAttendedAuthorizationMethodPresentation.PASSKEY
                        WalletSecurityChallengeKind.PIN -> WalletAttendedAuthorizationMethodPresentation.PIN
                        WalletSecurityChallengeKind.BIOMETRIC -> WalletAttendedAuthorizationMethodPresentation.BIOMETRIC
                        else -> null
                    }
                if (method == null) {
                    WalletInteractionScreenProjection.Unavailable(state.status.toPresentation(), "wallet_interaction_security_method_unresolved")
                } else {
                    WalletInteractionScreenProjection.AttendedAuthorization(
                        WalletAttendedAuthorizationPresentation(
                            operationRef = state.sessionId.value,
                            challengeRef = challenge.challengeId,
                            allowedMethods = listOf(method),
                            defaultMethod = method,
                            stage = WalletAttendedAuthorizationStagePresentation.READY,
                            reasonKey = challenge.reasonKey,
                            reasonArguments = challenge.arguments,
                        ),
                    )
                }
            }
        }
        WalletInteractionStatus.DeferredRetrievalPending ->
            WalletInteractionScreenProjection.Deferred(
                intervalSeconds = state.deferred?.intervalSeconds,
                attempt = state.deferred?.attempt,
                resumable = state.deferred?.resumable == true,
            )
        WalletInteractionStatus.Completed,
        WalletInteractionStatus.Cancelled,
        WalletInteractionStatus.Failed,
        WalletInteractionStatus.UnsupportedEntryPoint,
        ->
            WalletInteractionScreenProjection.Result(
                status = state.status.toPresentation(),
                error =
                    state.error?.let {
                        WalletInteractionErrorPresentation(
                            code = it.code,
                            messageKey = it.messageKey,
                            retryable = it.retryable,
                            arguments = it.arguments,
                        )
                    },
                receivedCredentials = state.receivedCredentialPreview.map { it.toPresentation() },
                completionHandoffRef = state.completionHandoffRef?.let { WalletSensitiveInputRefPresentation(it.value) },
            )
        WalletInteractionStatus.ResolvingEntryPoint,
        WalletInteractionStatus.Sharing,
        -> WalletInteractionScreenProjection.Progress(state.status.toPresentation())
    }

private fun WalletInteractionFlowKind.toPresentation(): WalletInteractionFlowKindPresentation =
    when (this) {
        WalletInteractionFlowKind.CredentialReceive -> WalletInteractionFlowKindPresentation.CREDENTIAL_RECEIVE
        WalletInteractionFlowKind.CredentialPresent -> WalletInteractionFlowKindPresentation.CREDENTIAL_PRESENT
        WalletInteractionFlowKind.AttendedPresent -> WalletInteractionFlowKindPresentation.ATTENDED_PRESENT
    }

private fun WalletInteractionStatus.toPresentation(): WalletInteractionStatusPresentation =
    when (this) {
        WalletInteractionStatus.ResolvingEntryPoint -> WalletInteractionStatusPresentation.RESOLVING_ENTRY_POINT
        WalletInteractionStatus.ImplementationChoiceRequired -> WalletInteractionStatusPresentation.IMPLEMENTATION_CHOICE_REQUIRED
        WalletInteractionStatus.UnsupportedEntryPoint -> WalletInteractionStatusPresentation.UNSUPPORTED_ENTRY_POINT
        WalletInteractionStatus.CounterpartyNotice -> WalletInteractionStatusPresentation.COUNTERPARTY_NOTICE
        WalletInteractionStatus.TrustReview -> WalletInteractionStatusPresentation.TRUST_REVIEW
        WalletInteractionStatus.CredentialOfferReview -> WalletInteractionStatusPresentation.CREDENTIAL_OFFER_REVIEW
        WalletInteractionStatus.AuthorizationRequired -> WalletInteractionStatusPresentation.AUTHORIZATION_REQUIRED
        WalletInteractionStatus.TxCodeRequired -> WalletInteractionStatusPresentation.TRANSACTION_CODE_REQUIRED
        WalletInteractionStatus.CredentialPreview -> WalletInteractionStatusPresentation.CREDENTIAL_PREVIEW
        WalletInteractionStatus.CredentialSelection -> WalletInteractionStatusPresentation.CREDENTIAL_SELECTION
        WalletInteractionStatus.DisclosureConsent -> WalletInteractionStatusPresentation.DISCLOSURE_CONSENT
        WalletInteractionStatus.SecurityUnlockRequired -> WalletInteractionStatusPresentation.SECURITY_UNLOCK_REQUIRED
        WalletInteractionStatus.DeferredRetrievalPending -> WalletInteractionStatusPresentation.DEFERRED_RETRIEVAL_PENDING
        WalletInteractionStatus.Sharing -> WalletInteractionStatusPresentation.SHARING
        WalletInteractionStatus.Completed -> WalletInteractionStatusPresentation.COMPLETED
        WalletInteractionStatus.Cancelled -> WalletInteractionStatusPresentation.CANCELLED
        WalletInteractionStatus.Failed -> WalletInteractionStatusPresentation.FAILED
    }

private fun WalletCounterpartySummary?.toPresentation(): WalletPartyPresentation? =
    this?.partyId?.let { stablePartyId ->
        WalletPartyPresentation(
            partyId = stablePartyId,
            role =
                when (this.role) {
                    WalletCounterpartyRole.ISSUER -> WalletPartyRolePresentation.ISSUER
                    WalletCounterpartyRole.VERIFIER -> WalletPartyRolePresentation.VERIFIER
                    WalletCounterpartyRole.MDOC_READER -> WalletPartyRolePresentation.MDOC_READER
                },
            displayName = this.displayName,
            legalName = null,
            domain = null,
            logoUri = this.logoUri,
            purposeKey = null,
            purposeArguments = emptyMap(),
            retentionKey = null,
            retentionArguments = emptyMap(),
        )
    }

private fun com.sphereon.wallet.interaction.WalletCounterpartyEncounterResult?.toPresentation(): WalletCounterpartyEncounterPresentation? =
    this
        ?.takeIf { it.resolved }
        ?.let {
            WalletCounterpartyEncounterPresentation(
                previousInteractionCount = it.previousInteractionCount,
                lastInteractionAtEpochSeconds = it.lastInteractionAtEpochSeconds,
                associationCandidates =
                    it.associationCandidates.map { candidate ->
                        WalletCounterpartyAssociationCandidatePresentation(
                            partyId = candidate.partyId,
                            displayName = candidate.displayName,
                            relatedHosts = candidate.relatedHosts,
                        )
                    },
            )
        }

private fun com.sphereon.wallet.interaction.WalletCounterpartyTrustSummary?.toPresentation(): WalletTrustPresentation {
    val sources = this?.sources.orEmpty()
    val hasEtsi = sources.any { it.type == WalletTrustSourceType.EUDI_TRUSTED_LIST }
    val hasFederation = sources.any { it.type == WalletTrustSourceType.OPENID_FEDERATION }
    require(!(hasEtsi && hasFederation)) { "wallet_trust_multiple_primary_mechanisms" }
    return WalletTrustPresentation(
        status =
            when (this?.status) {
                WalletTrustStatus.TRUSTED -> WalletTrustStatusPresentation.TRUSTED
                WalletTrustStatus.WARNING -> WalletTrustStatusPresentation.WARNING
                WalletTrustStatus.BLOCKED -> WalletTrustStatusPresentation.BLOCKED
                else -> WalletTrustStatusPresentation.UNKNOWN
            },
        action =
            when (this?.policyAction) {
                WalletTrustPolicyAction.ALLOW -> WalletTrustActionPresentation.ALLOW
                WalletTrustPolicyAction.BLOCK -> WalletTrustActionPresentation.BLOCK
                WalletTrustPolicyAction.ASK_USER -> WalletTrustActionPresentation.ASK_USER
                WalletTrustPolicyAction.FIRST_CONTACT_PROMPT -> WalletTrustActionPresentation.FIRST_CONTACT_PROMPT
                else -> WalletTrustActionPresentation.WARN
            },
        mechanism =
            when {
                hasEtsi -> WalletTrustMechanismPresentation.ETSI_TRUSTED_LIST
                hasFederation -> WalletTrustMechanismPresentation.OPENID_FEDERATION
                else -> WalletTrustMechanismPresentation.NONE
            },
        signals =
            sources.map {
                WalletTrustSignalPresentation(
                    signalId = it.identifier,
                    level =
                        when (this?.status) {
                            WalletTrustStatus.TRUSTED -> WalletTrustSignalLevelPresentation.POSITIVE
                            WalletTrustStatus.BLOCKED -> WalletTrustSignalLevelPresentation.BLOCKING
                            WalletTrustStatus.WARNING, WalletTrustStatus.UNKNOWN -> WalletTrustSignalLevelPresentation.WARNING
                            null -> WalletTrustSignalLevelPresentation.INFORMATION
                        },
                    labelKey = it.labelKey,
                    arguments = emptyMap(),
                )
            },
        markedTrustedByUser = this?.rememberedDecision == true,
    )
}

private fun WalletCredentialBranding.toFace(issuer: WalletCounterpartySummary?): WalletCredentialFacePresentation =
    WalletCredentialFacePresentation(
        credentialId = null,
        configurationId = credentialConfigurationId,
        name = name,
        issuerName = issuer?.displayName,
        description = description,
        logoUri = logoUri,
        backgroundImageUri = backgroundImageUri,
        backgroundColor = backgroundColor,
        textColor = textColor,
        status = null,
    )

private fun WalletInteractionState.offerPresentation(): WalletCredentialOfferPresentation? {
    val offer = credentialOffer ?: return null
    return WalletCredentialOfferPresentation(
        issuer = offer.issuer.toPresentation(),
        offeredCredentials =
            offer.branding.map { branding ->
                WalletOfferedCredentialPresentation(
                    configurationId = branding.credentialConfigurationId,
                    face = branding.toFace(offer.issuer),
                    info =
                        branding.info.map { descriptor ->
                            WalletCredentialOfferInfoPresentation(
                                infoId = "${branding.credentialConfigurationId}:${descriptor.path.joinToString("/")}",
                                path = descriptor.path,
                                displayName = descriptor.displayName,
                            )
                        },
                )
            },
        authorizationKind =
            when {
                offer.txCodeRequired -> WalletIssuanceAuthorizationPresentation.PRE_AUTHORIZED_TRANSACTION_CODE
                offer.authorizationCodeAvailable -> WalletIssuanceAuthorizationPresentation.AUTHORIZATION_CODE
                offer.preAuthorizedCodeAvailable -> WalletIssuanceAuthorizationPresentation.PRE_AUTHORIZED
                else -> WalletIssuanceAuthorizationPresentation.NONE
            },
        selectedCredentialConfigurationIds = selectedCredentialConfigurationIds,
    )
}

private fun WalletCredentialPreview.toPresentation(): WalletCredentialReviewPresentation =
    WalletCredentialReviewPresentation(
        credentialId = id,
        face =
            WalletCredentialFacePresentation(
                credentialId = id,
                configurationId = branding?.credentialConfigurationId,
                name = branding?.name ?: name,
                issuerName = issuer?.displayName,
                description = branding?.description,
                logoUri = branding?.logoUri ?: if (branding == null) issuer?.logoUri else null,
                backgroundImageUri = branding?.backgroundImageUri,
                backgroundColor = branding?.backgroundColor,
                textColor = branding?.textColor,
                status = null,
            ),
        info = claims.map { it.toPresentation() },
    )

private fun com.sphereon.wallet.interaction.WalletClaimDescriptor.toPresentation() =
    WalletInfoDescriptorPresentation(
        path = path,
        labelKey = labelKey,
        intentToRetain = intentToRetain,
        valueAvailable = valueAvailable,
    )

private fun WalletInteractionStatus.screenTitleKey(loginPresentation: Boolean): String {
    if (loginPresentation) {
        when (this) {
            WalletInteractionStatus.TrustReview -> return "wallet.interaction.login.status.trust_review"
            WalletInteractionStatus.CredentialSelection -> return "wallet.interaction.login.status.credential_selection"
            WalletInteractionStatus.DisclosureConsent -> return "wallet.interaction.login.status.disclosure_consent"
            WalletInteractionStatus.SecurityUnlockRequired -> return "wallet.interaction.login.status.security_unlock_required"
            WalletInteractionStatus.AuthorizationRequired -> return "wallet.interaction.login.status.authorization_required"
            WalletInteractionStatus.Sharing -> return "wallet.interaction.login.status.signing_in"
            WalletInteractionStatus.Completed -> return "wallet.interaction.login.status.completed"
            else -> Unit
        }
    }

    return when (this) {
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
        WalletInteractionStatus.Sharing -> "wallet.interaction.status.sharing"
        WalletInteractionStatus.Completed -> "wallet.interaction.status.completed"
        WalletInteractionStatus.Cancelled -> "wallet.interaction.status.cancelled"
        WalletInteractionStatus.Failed -> "wallet.interaction.status.failed"
    }
}

private fun WalletInteractionState.screenTitleArguments(): Map<String, String> =
    buildMap {
        counterparty?.displayName?.let { put("counterpartyDisplayName", it) }
        credentialOffer?.issuer?.displayName?.let { put("issuerDisplayName", it) }
        adapterId?.let { put("adapterId", it) }
        protocol?.name?.let { put("protocol", it) }
    }
