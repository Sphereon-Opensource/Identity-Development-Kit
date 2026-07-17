/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.interaction.CancelWalletInteractionArgs
import com.sphereon.wallet.interaction.CancelWalletInteractionCommand
import com.sphereon.wallet.interaction.CancelWalletInteractionResult
import com.sphereon.wallet.interaction.GetWalletInteractionEventsArgs
import com.sphereon.wallet.interaction.GetWalletInteractionEventsCommand
import com.sphereon.wallet.interaction.GetWalletInteractionEventsResult
import com.sphereon.wallet.interaction.GetWalletInteractionStateArgs
import com.sphereon.wallet.interaction.GetWalletInteractionStateCommand
import com.sphereon.wallet.interaction.ObserveWalletInteractionEventsCommand
import com.sphereon.wallet.interaction.ResumeWalletInteractionArgs
import com.sphereon.wallet.interaction.ResumeWalletInteractionCommand
import com.sphereon.wallet.interaction.StartWalletInteractionCommand
import com.sphereon.wallet.interaction.SubmitWalletInteractionActionArgs
import com.sphereon.wallet.interaction.SubmitWalletInteractionActionCommand
import com.sphereon.wallet.interaction.WalletInteractionClient
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionSession
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStateEvent
import com.sphereon.wallet.interaction.WalletInteractionStateEventSource
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

@Inject
@SingleIn(SessionScope::class)
class StartWalletInteractionCommandImpl(
    execution: SessionExecution,
    private val client: WalletInteractionClient,
) : TypedServiceCommandAdapter<WalletInteractionInput, WalletInteractionSession, IdkError>(
        commandId = StartWalletInteractionCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<WalletInteractionInput>(),
        outputTypeToken = typeToken<WalletInteractionSession>(),
    ),
    StartWalletInteractionCommand {
    override val commandId: String get() = StartWalletInteractionCommand.COMMAND_ID

    override suspend fun doExecute(
        args: WalletInteractionInput,
        applyDuring: (WalletInteractionInput) -> WalletInteractionInput,
    ): IdkResult<WalletInteractionSession, IdkError> =
        walletInteractionCommand {
            val input = applyDuring(args)
            client.start(input).also { session -> session.state.requireWallet(input.walletUnitId) }
        }
}

@Inject
@SingleIn(SessionScope::class)
class ResumeWalletInteractionCommandImpl(
    execution: SessionExecution,
    private val client: WalletInteractionClient,
) : TypedServiceCommandAdapter<ResumeWalletInteractionArgs, WalletInteractionSession, IdkError>(
        commandId = ResumeWalletInteractionCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ResumeWalletInteractionArgs>(),
        outputTypeToken = typeToken<WalletInteractionSession>(),
    ),
    ResumeWalletInteractionCommand {
    override val commandId: String get() = ResumeWalletInteractionCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ResumeWalletInteractionArgs,
        applyDuring: (ResumeWalletInteractionArgs) -> ResumeWalletInteractionArgs,
    ): IdkResult<WalletInteractionSession, IdkError> =
        walletInteractionCommand {
            val input = applyDuring(args)
            client.resume(input.sessionId).also { session -> session.state.requireWallet(input.walletUnitId) }
        }
}

@Inject
@SingleIn(SessionScope::class)
class SubmitWalletInteractionActionCommandImpl(
    execution: SessionExecution,
    private val client: WalletInteractionClient,
) : TypedServiceCommandAdapter<SubmitWalletInteractionActionArgs, WalletInteractionState, IdkError>(
        commandId = SubmitWalletInteractionActionCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<SubmitWalletInteractionActionArgs>(),
        outputTypeToken = typeToken<WalletInteractionState>(),
    ),
    SubmitWalletInteractionActionCommand {
    override val commandId: String get() = SubmitWalletInteractionActionCommand.COMMAND_ID

    override suspend fun doExecute(
        args: SubmitWalletInteractionActionArgs,
        applyDuring: (SubmitWalletInteractionActionArgs) -> SubmitWalletInteractionActionArgs,
    ): IdkResult<WalletInteractionState, IdkError> =
        walletInteractionCommand {
            val input = applyDuring(args)
            client.resume(input.sessionId).state.requireWallet(input.walletUnitId)
            client.dispatch(input.sessionId, input.action)
            client.resume(input.sessionId).state.also { state -> state.requireWallet(input.walletUnitId) }
        }
}

@Inject
@SingleIn(SessionScope::class)
class CancelWalletInteractionCommandImpl(
    execution: SessionExecution,
    private val client: WalletInteractionClient,
) : TypedServiceCommandAdapter<CancelWalletInteractionArgs, CancelWalletInteractionResult, IdkError>(
        commandId = CancelWalletInteractionCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CancelWalletInteractionArgs>(),
        outputTypeToken = typeToken<CancelWalletInteractionResult>(),
    ),
    CancelWalletInteractionCommand {
    override val commandId: String get() = CancelWalletInteractionCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CancelWalletInteractionArgs,
        applyDuring: (CancelWalletInteractionArgs) -> CancelWalletInteractionArgs,
    ): IdkResult<CancelWalletInteractionResult, IdkError> =
        walletInteractionCommand {
            val input = applyDuring(args)
            client.resume(input.sessionId).state.requireWallet(input.walletUnitId)
            client.cancel(input.sessionId)
            CancelWalletInteractionResult(
                sessionId = input.sessionId,
                state = client.resume(input.sessionId).state.also { state -> state.requireWallet(input.walletUnitId) },
            )
        }
}

@Inject
@SingleIn(SessionScope::class)
class GetWalletInteractionStateCommandImpl(
    execution: SessionExecution,
    private val client: WalletInteractionClient,
) : TypedServiceCommandAdapter<GetWalletInteractionStateArgs, WalletInteractionState, IdkError>(
        commandId = GetWalletInteractionStateCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetWalletInteractionStateArgs>(),
        outputTypeToken = typeToken<WalletInteractionState>(),
    ),
    GetWalletInteractionStateCommand {
    override val commandId: String get() = GetWalletInteractionStateCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetWalletInteractionStateArgs,
        applyDuring: (GetWalletInteractionStateArgs) -> GetWalletInteractionStateArgs,
    ): IdkResult<WalletInteractionState, IdkError> =
        walletInteractionCommand {
            val input = applyDuring(args)
            client.resume(input.sessionId).state.also { state -> state.requireWallet(input.walletUnitId) }
        }
}

@Inject
@SingleIn(SessionScope::class)
class GetWalletInteractionEventsCommandImpl(
    execution: SessionExecution,
    private val client: WalletInteractionClient,
) : TypedServiceCommandAdapter<GetWalletInteractionEventsArgs, GetWalletInteractionEventsResult, IdkError>(
        commandId = GetWalletInteractionEventsCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetWalletInteractionEventsArgs>(),
        outputTypeToken = typeToken<GetWalletInteractionEventsResult>(),
    ),
    GetWalletInteractionEventsCommand {
    override val commandId: String get() = GetWalletInteractionEventsCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetWalletInteractionEventsArgs,
        applyDuring: (GetWalletInteractionEventsArgs) -> GetWalletInteractionEventsArgs,
    ): IdkResult<GetWalletInteractionEventsResult, IdkError> =
        walletInteractionCommand {
            val input = applyDuring(args)
            val current = client.resume(input.sessionId).state.also { state -> state.requireWallet(input.walletUnitId) }
            val afterRevision = input.afterRevision
            val events =
                if (client is WalletInteractionStateEventSource) {
                    client
                        .events(input.sessionId, afterRevision)
                        .also { replay -> replay.forEach { event -> event.state.requireWallet(input.walletUnitId) } }
                } else if (afterRevision == null || current.revision > afterRevision) {
                    listOf(current.toEvent())
                } else {
                    emptyList()
                }
            GetWalletInteractionEventsResult(events)
        }
}

@Inject
@SingleIn(SessionScope::class)
class ObserveWalletInteractionEventsCommandImpl(
    execution: SessionExecution,
    private val client: WalletInteractionClient,
) : TypedServiceCommandAdapter<GetWalletInteractionEventsArgs, WalletInteractionStateEvent, IdkError>(
        commandId = ObserveWalletInteractionEventsCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetWalletInteractionEventsArgs>(),
        outputTypeToken = typeToken<WalletInteractionStateEvent>(),
    ),
    ObserveWalletInteractionEventsCommand {
    override val commandId: String get() = ObserveWalletInteractionEventsCommand.COMMAND_ID

    override suspend fun execute(args: GetWalletInteractionEventsArgs): IdkResult<WalletInteractionStateEvent, IdkError> =
        Err(
            IdkError.UNSUPPORTED_OPERATION_ERROR(
                operation = commandId,
                reason = "wallet_interaction_observe_events_requires_streaming_transport",
            ),
        )

    override suspend fun doExecute(
        args: GetWalletInteractionEventsArgs,
        applyDuring: (GetWalletInteractionEventsArgs) -> GetWalletInteractionEventsArgs,
    ): IdkResult<WalletInteractionStateEvent, IdkError> =
        Err(
            IdkError.UNSUPPORTED_OPERATION_ERROR(
                operation = commandId,
                reason = "wallet_interaction_observe_events_requires_streaming_transport",
            ),
        )

    override suspend fun executeStream(args: GetWalletInteractionEventsArgs): IdkResult<Flow<WalletInteractionStateEvent>, IdkError> =
        walletInteractionCommand {
            client.resume(args.sessionId).state.requireWallet(args.walletUnitId)
            val source =
                client as? WalletInteractionStateEventSource
                    ?: throw UnsupportedOperationException("wallet_interaction_live_event_source_unavailable")

            source
                .observeEvents(args.sessionId, args.afterRevision)
                .onEach { event -> event.state.requireWallet(args.walletUnitId) }
        }
}

@ContributesTo(SessionScope::class)
interface WalletInteractionCommandDescriptors {
    @Provides
    @IntoMap
    @StringKey(StartWalletInteractionCommand.COMMAND_ID)
    fun startWalletInteraction(impl: StartWalletInteractionCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(ResumeWalletInteractionCommand.COMMAND_ID)
    fun resumeWalletInteraction(impl: ResumeWalletInteractionCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(SubmitWalletInteractionActionCommand.COMMAND_ID)
    fun submitWalletInteractionAction(impl: SubmitWalletInteractionActionCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(CancelWalletInteractionCommand.COMMAND_ID)
    fun cancelWalletInteraction(impl: CancelWalletInteractionCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(GetWalletInteractionStateCommand.COMMAND_ID)
    fun getWalletInteractionState(impl: GetWalletInteractionStateCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(GetWalletInteractionEventsCommand.COMMAND_ID)
    fun getWalletInteractionEvents(impl: GetWalletInteractionEventsCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(ObserveWalletInteractionEventsCommand.COMMAND_ID)
    fun observeWalletInteractionEvents(impl: ObserveWalletInteractionEventsCommandImpl): ServiceCommand<*, *, *> = impl
}

private suspend fun <T : Any> walletInteractionCommand(block: suspend () -> T): IdkResult<T, IdkError> =
    try {
        Ok(block())
    } catch (expected: IllegalArgumentException) {
        Err(
            IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = expected.message ?: "wallet_interaction_command_invalid",
                throwable = expected,
            ),
        )
    } catch (expected: NoSuchElementException) {
        Err(
            IdkError.NOT_FOUND_ERROR(
                message = expected.message ?: "wallet_interaction_session_unknown",
                throwable = expected,
            ),
        )
    } catch (expected: UnsupportedOperationException) {
        Err(
            IdkError.UNSUPPORTED_OPERATION_ERROR(
                operation = "wallet.interaction",
                reason = expected.message,
                throwable = expected,
            ),
        )
    }

private fun WalletInteractionState.requireWallet(walletUnitId: String) {
    require(this.walletUnitId == walletUnitId) {
        "wallet_interaction_session_wallet_mismatch"
    }
}

private fun WalletInteractionState.toEvent(): WalletInteractionStateEvent =
    WalletInteractionStateEvent(
        sessionId = sessionId,
        revision = revision,
        state = this,
    )
