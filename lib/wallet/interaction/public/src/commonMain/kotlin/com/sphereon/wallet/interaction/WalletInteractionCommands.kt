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

interface StartCapturedWalletInteractionCommand : ServiceCommand<CapturedInteractionInput, WalletInteractionSession, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE

    companion object {
        const val COMMAND_ID: String = "wallet.interaction.start-captured"
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
    val walletUnitId: String,
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
    val walletUnitId: String,
    val sessionId: WalletInteractionSessionId,
    val action: WalletInteractionAction,
    /** Required by managed backends; omitted only by local in-process wallet runtimes. */
    val expectedProcessRevision: Long? = null,
    /** Required by managed backends and scoped to the interaction plus app registration. */
    val idempotencyKey: String? = null,
    /** Independently revocable controller registration selected by the authoritative runtime plan. */
    val appRegistrationId: String? = null,
    /** Current controller lease returned by an acquire, renew, or takeover command. */
    val controllerLeaseId: String? = null,
) {
    init {
        require(expectedProcessRevision == null || expectedProcessRevision >= 0) {
            "wallet_interaction_expected_process_revision_invalid"
        }
        require(idempotencyKey == null || idempotencyKey.isNotBlank()) {
            "wallet_interaction_action_idempotency_key_blank"
        }
        require(appRegistrationId == null || appRegistrationId.isNotBlank()) {
            "wallet_interaction_action_app_registration_blank"
        }
        require(controllerLeaseId == null || controllerLeaseId.isNotBlank()) {
            "wallet_interaction_action_controller_lease_blank"
        }
    }
}

interface CancelWalletInteractionCommand : ServiceCommand<CancelWalletInteractionArgs, CancelWalletInteractionResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE

    companion object {
        const val COMMAND_ID: String = "wallet.interaction.cancel"
    }
}

@Serializable
data class CancelWalletInteractionArgs(
    val walletUnitId: String,
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
    val walletUnitId: String,
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
    val walletUnitId: String,
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

/**
 * Registers client-supplied protocol input without putting the value in public interaction state.
 * Implementations accept only purposes whose value originates at the wallet client.
 */
interface RegisterWalletInteractionSensitiveInputCommand :
    ServiceCommand<RegisterWalletInteractionSensitiveInputArgs, RegisterWalletInteractionSensitiveInputResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE

    companion object {
        const val COMMAND_ID: String = "wallet.interaction.register-sensitive-input"
    }
}

@Serializable
data class RegisterWalletInteractionSensitiveInputArgs(
    val walletUnitId: String,
    val sessionId: WalletInteractionSessionId,
    val purpose: WalletInteractionSensitiveInputPurpose,
    val value: String,
) {
    init {
        require(walletUnitId.isNotBlank()) { "wallet_interaction_wallet_unit_id_blank" }
        require(value.isNotBlank()) { "wallet_interaction_sensitive_input_blank" }
        require(
            purpose == WalletInteractionSensitiveInputPurpose.OID4VCI_TRANSACTION_CODE ||
                purpose == WalletInteractionSensitiveInputPurpose.OID4VCI_AUTHORIZATION_CALLBACK ||
                purpose == WalletInteractionSensitiveInputPurpose.INTERACTION_SECURITY_GRANT,
        ) { "wallet_interaction_sensitive_input_purpose_not_client_supplied" }
    }

    override fun toString(): String =
        "RegisterWalletInteractionSensitiveInputArgs(walletUnitId=$walletUnitId, sessionId=$sessionId, purpose=$purpose, value=[redacted])"
}

@Serializable
data class RegisterWalletInteractionSensitiveInputResult(
    val ref: WalletInteractionSensitiveInputRef,
)

interface ConsumeWalletInteractionAuthorizationHandoffCommand :
    ServiceCommand<ConsumeWalletInteractionHandoffArgs, ConsumeWalletInteractionHandoffResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE

    companion object {
        const val COMMAND_ID: String = "wallet.interaction.consume-authorization-handoff"
    }
}

interface ConsumeWalletInteractionCompletionHandoffCommand :
    ServiceCommand<ConsumeWalletInteractionHandoffArgs, ConsumeWalletInteractionHandoffResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE

    companion object {
        const val COMMAND_ID: String = "wallet.interaction.consume-completion-handoff"
    }
}

@Serializable
data class ConsumeWalletInteractionHandoffArgs(
    val walletUnitId: String,
    val sessionId: WalletInteractionSessionId,
    val ref: WalletInteractionSensitiveInputRef,
) {
    init {
        require(walletUnitId.isNotBlank()) { "wallet_interaction_wallet_unit_id_blank" }
    }
}

@Serializable
data class ConsumeWalletInteractionHandoffResult(
    val value: String,
) {
    override fun toString(): String = "ConsumeWalletInteractionHandoffResult(value=[redacted])"
}

@ContributesTo(SessionScope::class)
interface WalletInteractionCommandBindings {
    @Provides
    fun ensureWalletClientRegistrationKey(registry: SessionScopedCommandRegistry): EnsureWalletClientRegistrationKeyCommand =
        registry.get(EnsureWalletClientRegistrationKeyCommand.COMMAND_ID) as? EnsureWalletClientRegistrationKeyCommand
            ?: error("No binding for ${EnsureWalletClientRegistrationKeyCommand.COMMAND_ID}")

    @Provides
    fun startWalletInteraction(registry: SessionScopedCommandRegistry): StartWalletInteractionCommand =
        registry.get(StartWalletInteractionCommand.COMMAND_ID) as? StartWalletInteractionCommand
            ?: error("No binding for ${StartWalletInteractionCommand.COMMAND_ID}")

    @Provides
    fun startCapturedWalletInteraction(registry: SessionScopedCommandRegistry): StartCapturedWalletInteractionCommand =
        registry.get(StartCapturedWalletInteractionCommand.COMMAND_ID) as? StartCapturedWalletInteractionCommand
            ?: error("No binding for ${StartCapturedWalletInteractionCommand.COMMAND_ID}")

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

    @Provides
    fun registerWalletInteractionSensitiveInput(registry: SessionScopedCommandRegistry): RegisterWalletInteractionSensitiveInputCommand =
        registry.get(RegisterWalletInteractionSensitiveInputCommand.COMMAND_ID) as? RegisterWalletInteractionSensitiveInputCommand
            ?: error("No binding for ${RegisterWalletInteractionSensitiveInputCommand.COMMAND_ID}")

    @Provides
    fun consumeWalletInteractionAuthorizationHandoff(registry: SessionScopedCommandRegistry): ConsumeWalletInteractionAuthorizationHandoffCommand =
        registry.get(ConsumeWalletInteractionAuthorizationHandoffCommand.COMMAND_ID) as? ConsumeWalletInteractionAuthorizationHandoffCommand
            ?: error("No binding for ${ConsumeWalletInteractionAuthorizationHandoffCommand.COMMAND_ID}")

    @Provides
    fun consumeWalletInteractionCompletionHandoff(registry: SessionScopedCommandRegistry): ConsumeWalletInteractionCompletionHandoffCommand =
        registry.get(ConsumeWalletInteractionCompletionHandoffCommand.COMMAND_ID) as? ConsumeWalletInteractionCompletionHandoffCommand
            ?: error("No binding for ${ConsumeWalletInteractionCompletionHandoffCommand.COMMAND_ID}")
}
