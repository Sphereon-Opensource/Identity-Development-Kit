/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.credential.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.credential.ReplayWalletOperationsArgs
import com.sphereon.wallet.credential.ReplayWalletOperationsCommand
import com.sphereon.wallet.credential.WalletOperationReplayResult
import com.sphereon.wallet.credential.WalletOperationSyncService
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey

@Inject
@SingleIn(SessionScope::class)
class ReplayWalletOperationsCommandImpl(
    execution: SessionExecution,
    private val syncService: WalletOperationSyncService,
) : TypedServiceCommandAdapter<ReplayWalletOperationsArgs, WalletOperationReplayResult, IdkError>(
        commandId = ReplayWalletOperationsCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ReplayWalletOperationsArgs>(),
        outputTypeToken = typeToken<WalletOperationReplayResult>(),
    ), ReplayWalletOperationsCommand {
    override val commandId: String get() = ReplayWalletOperationsCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ReplayWalletOperationsArgs,
        applyDuring: (ReplayWalletOperationsArgs) -> ReplayWalletOperationsArgs,
    ): IdkResult<WalletOperationReplayResult, IdkError> {
        val input = applyDuring(args)
        return syncService.replayPending(input.walletUnitId)
    }
}

@ContributesTo(SessionScope::class)
interface WalletOperationSyncCommandDescriptors {
    @Provides
    @IntoMap
    @StringKey(ReplayWalletOperationsCommand.COMMAND_ID)
    fun replayWalletOperations(command: ReplayWalletOperationsCommandImpl): ServiceCommand<*, *, *> = command
}
