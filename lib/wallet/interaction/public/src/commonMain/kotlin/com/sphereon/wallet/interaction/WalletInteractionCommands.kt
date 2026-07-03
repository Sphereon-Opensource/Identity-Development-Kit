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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServerStreamingServiceCommand
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.serialization.Serializable

interface StartWalletInteractionCommand : ServiceCommand<WalletInteractionInput, WalletInteractionSession, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE

    companion object {
        const val COMMAND_ID: String = "wallet.interaction.start"
    }
}

interface ResumeWalletInteractionCommand : ServiceCommand<ResumeWalletInteractionArgs, WalletInteractionSession, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ

    companion object {
        const val COMMAND_ID: String = "wallet.interaction.resume"
    }
}

@Serializable
data class ResumeWalletInteractionArgs(
    val walletInstanceId: String,
    val sessionId: WalletInteractionSessionId,
)

interface SubmitWalletInteractionActionCommand : ServiceCommand<SubmitWalletInteractionActionArgs, WalletInteractionState, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE

    companion object {
        const val COMMAND_ID: String = "wallet.interaction.submit-action"
    }
}

@Serializable
data class SubmitWalletInteractionActionArgs(
    val walletInstanceId: String,
    val sessionId: WalletInteractionSessionId,
    val action: WalletInteractionAction,
)

interface CancelWalletInteractionCommand : ServiceCommand<CancelWalletInteractionArgs, CancelWalletInteractionResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE

    companion object {
        const val COMMAND_ID: String = "wallet.interaction.cancel"
    }
}

@Serializable
data class CancelWalletInteractionArgs(
    val walletInstanceId: String,
    val sessionId: WalletInteractionSessionId,
)

interface GetWalletInteractionStateCommand : ServiceCommand<GetWalletInteractionStateArgs, WalletInteractionState, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ

    companion object {
        const val COMMAND_ID: String = "wallet.interaction.get-state"
    }
}

@Serializable
data class GetWalletInteractionStateArgs(
    val walletInstanceId: String,
    val sessionId: WalletInteractionSessionId,
)

interface GetWalletInteractionEventsCommand : ServiceCommand<GetWalletInteractionEventsArgs, GetWalletInteractionEventsResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ

    companion object {
        const val COMMAND_ID: String = "wallet.interaction.get-events"
    }
}

@Serializable
data class GetWalletInteractionEventsArgs(
    val walletInstanceId: String,
    val sessionId: WalletInteractionSessionId,
    val afterRevision: Long? = null,
)

@Serializable
data class GetWalletInteractionEventsResult(
    val events: List<WalletInteractionStateEvent>,
)

interface ObserveWalletInteractionEventsCommand : ServerStreamingServiceCommand<GetWalletInteractionEventsArgs, WalletInteractionStateEvent, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ

    override suspend fun execute(args: GetWalletInteractionEventsArgs): IdkResult<WalletInteractionStateEvent, IdkError> =
        Err(
            IdkError.UNSUPPORTED_OPERATION_ERROR(
                operation = COMMAND_ID,
                reason = "wallet_interaction_observe_events_requires_streaming_transport",
            ),
        )

    companion object {
        const val COMMAND_ID: String = "wallet.interaction.observe-events"
    }
}

@ContributesTo(SessionScope::class)
interface WalletInteractionCommandBindings {
    @Provides
    fun startWalletInteraction(registry: SessionScopedCommandRegistry): StartWalletInteractionCommand =
        registry.get(StartWalletInteractionCommand.COMMAND_ID) as? StartWalletInteractionCommand
            ?: error("No binding for ${StartWalletInteractionCommand.COMMAND_ID}")

    @Provides
    fun resumeWalletInteraction(registry: SessionScopedCommandRegistry): ResumeWalletInteractionCommand =
        registry.get(ResumeWalletInteractionCommand.COMMAND_ID) as? ResumeWalletInteractionCommand
            ?: error("No binding for ${ResumeWalletInteractionCommand.COMMAND_ID}")

    @Provides
    fun submitWalletInteractionAction(registry: SessionScopedCommandRegistry): SubmitWalletInteractionActionCommand =
        registry.get(SubmitWalletInteractionActionCommand.COMMAND_ID) as? SubmitWalletInteractionActionCommand
            ?: error("No binding for ${SubmitWalletInteractionActionCommand.COMMAND_ID}")

    @Provides
    fun cancelWalletInteraction(registry: SessionScopedCommandRegistry): CancelWalletInteractionCommand =
        registry.get(CancelWalletInteractionCommand.COMMAND_ID) as? CancelWalletInteractionCommand
            ?: error("No binding for ${CancelWalletInteractionCommand.COMMAND_ID}")

    @Provides
    fun getWalletInteractionState(registry: SessionScopedCommandRegistry): GetWalletInteractionStateCommand =
        registry.get(GetWalletInteractionStateCommand.COMMAND_ID) as? GetWalletInteractionStateCommand
            ?: error("No binding for ${GetWalletInteractionStateCommand.COMMAND_ID}")

    @Provides
    fun getWalletInteractionEvents(registry: SessionScopedCommandRegistry): GetWalletInteractionEventsCommand =
        registry.get(GetWalletInteractionEventsCommand.COMMAND_ID) as? GetWalletInteractionEventsCommand
            ?: error("No binding for ${GetWalletInteractionEventsCommand.COMMAND_ID}")

    @Provides
    fun observeWalletInteractionEvents(registry: SessionScopedCommandRegistry): ObserveWalletInteractionEventsCommand =
        registry.get(ObserveWalletInteractionEventsCommand.COMMAND_ID) as? ObserveWalletInteractionEventsCommand
            ?: error("No binding for ${ObserveWalletInteractionEventsCommand.COMMAND_ID}")
}
