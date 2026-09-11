/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.interaction

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Resolves the protocol identifier to the wallet's stable Party identity and records the encounter.
 *
 * Local and managed wallets provide different implementations of this port. Protocol adapters only
 * depend on this contract and therefore never depend on SQLite, VDX, or a concrete Party repository.
 */
interface WalletCounterpartyEncounterRegistry {
    suspend fun encounter(request: WalletCounterpartyEncounterRequest): WalletCounterpartyEncounterResult

    suspend fun resolveAssociation(request: WalletCounterpartyAssociationRequest): WalletCounterpartyEncounterResult

    companion object {
        /**
         * Neutral default for protocol runners which do not host a wallet Party store. It deliberately
         * does not claim that an unresolved counterparty is a first interaction.
         */
        val none: WalletCounterpartyEncounterRegistry =
            object : WalletCounterpartyEncounterRegistry {
                override suspend fun encounter(request: WalletCounterpartyEncounterRequest): WalletCounterpartyEncounterResult =
                    WalletCounterpartyEncounterResult.unresolved(request.counterparty)

                override suspend fun resolveAssociation(request: WalletCounterpartyAssociationRequest): WalletCounterpartyEncounterResult =
                    WalletCounterpartyEncounterResult.unresolved(request.encounter.counterparty)
            }
    }
}

@Serializable
data class WalletCounterpartyAssociationRequest(
    val walletUnitId: String,
    val encounter: WalletCounterpartyEncounterResult,
    val decision: WalletCounterpartyAssociationDecision,
)

@Serializable
sealed interface WalletCounterpartyAssociationDecision {
    @Serializable
    @SerialName("keep_separate")
    data class KeepSeparate(val displayName: String) : WalletCounterpartyAssociationDecision {
        init {
            require(displayName.isNotBlank()) { "wallet_counterparty_display_name_blank" }
        }
    }

    @Serializable
    @SerialName("associate_existing")
    data class AssociateExisting(val partyId: String) : WalletCounterpartyAssociationDecision {
        init {
            require(partyId.isNotBlank()) { "wallet_counterparty_association_target_blank" }
        }
    }
}

@Serializable
data class WalletCounterpartyEncounterRequest(
    val walletUnitId: String,
    val protocol: WalletProtocol,
    val counterparty: WalletCounterpartySummary,
) {
    init {
        require(walletUnitId.isNotBlank()) { "wallet_counterparty_encounter_wallet_unit_id_blank" }
    }
}

/**
 * Serializable encounter evidence snapshotted into the interaction state.
 *
 * [previousInteractionCount] and [lastInteractionAtEpochSeconds] describe state before the current
 * encounter. This keeps the first-interaction ceremony deterministic while the registry records the
 * current encounter exactly once.
 */
@Serializable
data class WalletCounterpartyEncounterResult(
    val counterparty: WalletCounterpartySummary,
    val resolved: Boolean,
    /** True only when this encounter created a new Organization Party in the authority. */
    val organizationCreated: Boolean,
    val firstInteraction: Boolean,
    val previousInteractionCount: Long = 0,
    val lastInteractionAtEpochSeconds: Long? = null,
    val associationCandidates: List<WalletCounterpartyAssociationCandidate> = emptyList(),
) {
    init {
        require(previousInteractionCount >= 0) { "wallet_counterparty_encounter_count_negative" }
        if (organizationCreated) {
            require(resolved) { "wallet_counterparty_created_organization_unresolved" }
            require(firstInteraction) { "wallet_counterparty_created_organization_not_first_interaction" }
        }
        if (firstInteraction) {
            require(resolved) { "wallet_counterparty_first_interaction_unresolved" }
            require(previousInteractionCount == 0L) { "wallet_counterparty_first_interaction_count_invalid" }
            require(lastInteractionAtEpochSeconds == null) { "wallet_counterparty_first_interaction_last_at_invalid" }
        }
        if (!resolved) {
            require(counterparty.partyId == null) { "wallet_counterparty_unresolved_party_id_present" }
            require(previousInteractionCount == 0L) { "wallet_counterparty_unresolved_count_present" }
            require(lastInteractionAtEpochSeconds == null) { "wallet_counterparty_unresolved_last_at_present" }
            require(associationCandidates.isEmpty()) { "wallet_counterparty_unresolved_association_candidates_present" }
        } else {
            require(counterparty.partyId != null) { "wallet_counterparty_resolved_party_id_missing" }
            require(firstInteraction == (previousInteractionCount == 0L)) {
                "wallet_counterparty_encounter_first_interaction_inconsistent"
            }
            require((previousInteractionCount == 0L) == (lastInteractionAtEpochSeconds == null)) {
                "wallet_counterparty_encounter_last_at_inconsistent"
            }
        }
    }

    companion object {
        fun unresolved(counterparty: WalletCounterpartySummary): WalletCounterpartyEncounterResult =
            WalletCounterpartyEncounterResult(
                counterparty = counterparty.copy(partyId = null),
                resolved = false,
                organizationCreated = false,
                firstInteraction = false,
            )
    }
}

@Serializable
data class WalletCounterpartyAssociationCandidate(
    val partyId: String,
    val displayName: String,
    val relatedHosts: List<String>,
) {
    init {
        require(partyId.isNotBlank()) { "wallet_counterparty_association_party_id_blank" }
        require(displayName.isNotBlank()) { "wallet_counterparty_association_name_blank" }
        require(relatedHosts.isNotEmpty()) { "wallet_counterparty_association_hosts_empty" }
    }
}
