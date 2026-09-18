/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Recipient-safe authorization decision summary for wallet activity history. */
@Serializable
data class WalletAuthorizationDecisionProjection(
    val decisionId: String,
    val caseId: String,
    val tenantId: String,
    val outcome: String,
    val action: String,
    val principal: String,
    val representedParty: String? = null,
    val resource: String? = null,
    val policy: String,
    val explanation: String? = null,
    val recordedAt: String,
    val evidenceCount: Int,
    val availability: String,
    val adl: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(decisionId.isNotBlank()) { "wallet_authorization_decision_id_blank" }
        require(caseId.isNotBlank()) { "wallet_authorization_decision_case_blank" }
        require(tenantId.isNotBlank()) { "wallet_authorization_decision_tenant_blank" }
        require(outcome in setOf("PERMIT", "DENY", "INDETERMINATE")) { "wallet_authorization_decision_outcome_invalid" }
        require(action.isNotBlank()) { "wallet_authorization_decision_action_blank" }
        require(principal.isNotBlank()) { "wallet_authorization_decision_principal_blank" }
        require(policy.isNotBlank()) { "wallet_authorization_decision_policy_blank" }
        require(recordedAt.isNotBlank()) { "wallet_authorization_decision_recorded_at_blank" }
        require(evidenceCount >= 0) { "wallet_authorization_decision_evidence_count_invalid" }
        require(availability in setOf("available", "unavailable")) { "wallet_authorization_decision_availability_invalid" }
    }
}

@Serializable
data class WalletInteractionActivityProjection(
    val sequence: Long,
    val recordedAtEpochSeconds: Long,
    val sessionId: String,
    val walletUnitId: String,
    val flowKind: WalletInteractionFlowKind?,
    val status: WalletInteractionStatus,
    val counterparty: WalletCounterpartySummary? = null,
    val credentialRecordIds: Set<String> = emptySet(),
    val authorizationDecision: WalletAuthorizationDecisionProjection? = null,
)

@Serializable
data class ListWalletInteractionActivityArgs(
    val walletUnitId: String,
    val afterSequence: Long? = null,
    val limit: Int = 100,
) {
    init {
        require(walletUnitId.isNotBlank()) { "wallet_interaction_activity_wallet_unit_blank" }
        require(afterSequence == null || afterSequence >= 0) { "wallet_interaction_activity_sequence_invalid" }
        require(limit in 1..500) { "wallet_interaction_activity_limit_invalid" }
    }
}

@Serializable
data class ListWalletInteractionActivityResult(
    val entries: List<WalletInteractionActivityProjection>,
    val nextSequence: Long? = entries.lastOrNull()?.sequence,
)

interface ListWalletInteractionActivityCommand :
    ServiceCommand<ListWalletInteractionActivityArgs, ListWalletInteractionActivityResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ

    companion object { const val COMMAND_ID = "wallet.interaction.list-activity" }
}

@ContributesTo(SessionScope::class)
interface WalletInteractionActivityCommandBindings {
    @Provides
    fun listWalletInteractionActivity(registry: SessionScopedCommandRegistry): ListWalletInteractionActivityCommand =
        registry.get(ListWalletInteractionActivityCommand.COMMAND_ID) as? ListWalletInteractionActivityCommand
            ?: error("No binding for ${ListWalletInteractionActivityCommand.COMMAND_ID}")
}
