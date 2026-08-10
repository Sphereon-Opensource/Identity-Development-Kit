/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.credential

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.serialization.Serializable

@Serializable
data class ReplayWalletOperationsArgs(
    val walletUnitId: String,
) {
    init {
        require(walletUnitId.isNotBlank()) { "wallet_sync_wallet_unit_blank" }
    }
}

interface ReplayWalletOperationsCommand :
    ServiceCommand<ReplayWalletOperationsArgs, WalletOperationReplayResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE

    companion object {
        const val COMMAND_ID = "wallet.sync.replay-pending"
    }
}

@ContributesTo(SessionScope::class)
interface WalletOperationSyncCommandBindings {
    @Provides
    fun replayWalletOperations(registry: SessionScopedCommandRegistry): ReplayWalletOperationsCommand =
        registry.get(ReplayWalletOperationsCommand.COMMAND_ID) as? ReplayWalletOperationsCommand
            ?: error("No binding for ${ReplayWalletOperationsCommand.COMMAND_ID}")
}
