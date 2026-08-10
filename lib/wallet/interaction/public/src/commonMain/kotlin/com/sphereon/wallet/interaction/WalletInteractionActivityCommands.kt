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
