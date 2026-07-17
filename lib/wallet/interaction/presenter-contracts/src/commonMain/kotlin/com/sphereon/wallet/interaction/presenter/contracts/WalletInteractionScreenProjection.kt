/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.presenter.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Safe projection with no protocol payloads, authorization URLs, tokens or hidden values. */
@Serializable
sealed interface WalletInteractionScreenProjection {
    @Serializable
    @SerialName("unavailable")
    data class Unavailable(val status: WalletInteractionStatusPresentation, val code: String) : WalletInteractionScreenProjection

    @Serializable
    @SerialName("progress")
    data class Progress(val status: WalletInteractionStatusPresentation) : WalletInteractionScreenProjection

    @Serializable
    @SerialName("implementation-choice")
    data class ImplementationChoice(val choices: List<WalletImplementationChoicePresentation>) : WalletInteractionScreenProjection

    @Serializable
    @SerialName("party-review")
    data class PartyReview(
        val party: WalletPartyPresentation?,
        val trust: WalletTrustPresentation,
        val encounter: WalletCounterpartyEncounterPresentation?,
        val issuanceOffer: WalletCredentialOfferPresentation?,
    ) : WalletInteractionScreenProjection

    @Serializable
    @SerialName("offer-review")
    data class OfferReview(
        val offer: WalletCredentialOfferPresentation,
        val trust: WalletTrustPresentation,
        val encounter: WalletCounterpartyEncounterPresentation?,
    ) : WalletInteractionScreenProjection

    @Serializable
    @SerialName("authorization-handoff")
    data class AuthorizationHandoff(
        val party: WalletPartyPresentation?,
        val handoffRef: WalletSensitiveInputRefPresentation,
    ) : WalletInteractionScreenProjection

    @Serializable
    @SerialName("transaction-code")
    data class TransactionCode(
        val inputMode: WalletTransactionCodeInputModePresentation,
        val expectedLength: Int?,
        val descriptionKey: String?,
        val descriptionArguments: Map<String, String>,
    ) : WalletInteractionScreenProjection

    @Serializable
    @SerialName("credential-review")
    data class CredentialReview(val credentials: List<WalletCredentialReviewPresentation>) : WalletInteractionScreenProjection

    @Serializable
    @SerialName("credential-selection")
    data class CredentialSelection(
        val groups: List<WalletCredentialSelectionGroupPresentation>,
        val satisfiable: Boolean,
    ) : WalletInteractionScreenProjection

    @Serializable
    @SerialName("disclosure")
    data class Disclosure(
        val verifier: WalletPartyPresentation?,
        val info: List<WalletInfoDescriptorPresentation>,
        val selectedCredentialIds: List<String>,
        val privacy: WalletPrivacyPresentation,
    ) : WalletInteractionScreenProjection

    @Serializable
    @SerialName("attended-authorization")
    data class AttendedAuthorization(val authorization: WalletAttendedAuthorizationPresentation) : WalletInteractionScreenProjection

    @Serializable
    @SerialName("deferred")
    data class Deferred(val intervalSeconds: Int?, val attempt: Int?, val resumable: Boolean) : WalletInteractionScreenProjection

    @Serializable
    @SerialName("result")
    data class Result(
        val status: WalletInteractionStatusPresentation,
        val error: WalletInteractionErrorPresentation?,
        val receivedCredentials: List<WalletCredentialReviewPresentation>,
        val completionHandoffRef: WalletSensitiveInputRefPresentation? = null,
    ) : WalletInteractionScreenProjection
}

/** Safe encounter facts; no Party repository or backend type crosses the presenter boundary. */
@Serializable
data class WalletCounterpartyEncounterPresentation(
    val previousInteractionCount: Long,
    val lastInteractionAtEpochSeconds: Long?,
    /** URL-derived suggestions only. DID subject matches are resolved before this boundary. */
    val associationCandidates: List<WalletCounterpartyAssociationCandidatePresentation> = emptyList(),
) {
    init {
        require(previousInteractionCount >= 0) { "wallet_counterparty_encounter_count_invalid" }
        require((previousInteractionCount == 0L) == (lastInteractionAtEpochSeconds == null)) {
            "wallet_counterparty_encounter_last_interaction_inconsistent"
        }
    }

    val firstInteraction: Boolean get() = previousInteractionCount == 0L
}

/** Safe candidate summary. No identifier or Party repository model crosses the presenter boundary. */
@Serializable
data class WalletCounterpartyAssociationCandidatePresentation(
    val partyId: String,
    val displayName: String,
    val relatedHosts: List<String>,
) {
    init {
        require(partyId.isNotBlank()) { "wallet_counterparty_association_candidate_party_blank" }
        require(displayName.isNotBlank()) { "wallet_counterparty_association_candidate_name_blank" }
        require(relatedHosts.isNotEmpty() && relatedHosts.none(String::isBlank)) {
            "wallet_counterparty_association_candidate_hosts_invalid"
        }
    }
}

@Serializable
data class WalletImplementationChoicePresentation(
    val implementationId: String,
    val labelKey: String,
    val arguments: Map<String, String>,
)

@Serializable
enum class WalletIssuanceAuthorizationPresentation {
    PRE_AUTHORIZED,
    PRE_AUTHORIZED_TRANSACTION_CODE,
    AUTHORIZATION_CODE,
    INTERACTIVE_PRESENTATION,
    NONE,
}

@Serializable
data class WalletCredentialOfferPresentation(
    val issuer: WalletPartyPresentation?,
    val offeredCredentials: List<WalletOfferedCredentialPresentation>,
    val authorizationKind: WalletIssuanceAuthorizationPresentation,
    val selectedCredentialConfigurationIds: List<String>,
)

@Serializable
data class WalletOfferedCredentialPresentation(
    val configurationId: String,
    val face: WalletCredentialFacePresentation,
    val info: List<WalletCredentialOfferInfoPresentation>,
)

@Serializable
data class WalletCredentialOfferInfoPresentation(
    val infoId: String,
    val path: List<String>,
    val displayName: String,
)

@Serializable
enum class WalletTransactionCodeInputModePresentation { NUMERIC, TEXT }

@Serializable
data class WalletCredentialReviewPresentation(
    val credentialId: String,
    val face: WalletCredentialFacePresentation,
    val info: List<WalletInfoDescriptorPresentation>,
)

@Serializable
data class WalletInfoDescriptorPresentation(
    val path: List<String>,
    val labelKey: String?,
    val intentToRetain: Boolean?,
    val valueAvailable: Boolean,
)

@Serializable
data class WalletCredentialSelectionGroupPresentation(
    val groupId: String,
    val required: Boolean,
    val options: List<WalletCredentialSelectionOptionPresentation>,
)

@Serializable
data class WalletCredentialSelectionOptionPresentation(
    val requirementIds: List<String>,
    val satisfiable: Boolean,
)

@Serializable
data class WalletInteractionErrorPresentation(
    val code: String,
    val messageKey: String?,
    val retryable: Boolean,
    val arguments: Map<String, String>,
)
