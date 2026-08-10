/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */
package com.sphereon.wallet.interaction

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.serialization.Serializable

@Serializable
data class WalletInteractionControllerLease(
    val leaseId: String,
    val walletUnitId: String,
    val sessionId: WalletInteractionSessionId,
    val appRegistrationId: String,
    val acquiredAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
    val processRevision: Long,
    val leaseRevision: Long,
) {
    init {
        require(leaseId.isNotBlank() && walletUnitId.isNotBlank() && appRegistrationId.isNotBlank()) {
            "wallet_interaction_controller_lease_binding_invalid"
        }
        require(expiresAtEpochSeconds > acquiredAtEpochSeconds && processRevision >= 0 && leaseRevision > 0) {
            "wallet_interaction_controller_lease_state_invalid"
        }
    }
}

@Serializable
enum class WalletInteractionControllerTransition { ACQUIRED, RENEWED, TAKEN_OVER, RELEASED }

@Serializable
enum class WalletInteractionControllerTakeoverReason { EXPIRED, REGISTRATION_REVOKED, USER_CONFIRMED }

@Serializable
data class WalletInteractionControllerLeaseResult(
    val transition: WalletInteractionControllerTransition,
    val lease: WalletInteractionControllerLease,
    val previousControllerAppRegistrationId: String? = null,
)

@Serializable
data class AcquireWalletInteractionControllerArgs(
    val walletUnitId: String,
    val sessionId: WalletInteractionSessionId,
    val appRegistrationId: String,
    val expectedProcessRevision: Long,
    val idempotencyKey: String,
) {
    init {
        require(walletUnitId.isNotBlank() && appRegistrationId.isNotBlank() && idempotencyKey.isNotBlank()) {
            "wallet_interaction_controller_acquire_binding_invalid"
        }
        require(expectedProcessRevision >= 0) { "wallet_interaction_controller_acquire_revision_invalid" }
    }
}

@Serializable
data class RenewWalletInteractionControllerArgs(
    val walletUnitId: String,
    val sessionId: WalletInteractionSessionId,
    val appRegistrationId: String,
    val controllerLeaseId: String,
    val expectedLeaseRevision: Long,
    val idempotencyKey: String,
) {
    init {
        require(walletUnitId.isNotBlank() && appRegistrationId.isNotBlank() && controllerLeaseId.isNotBlank() && idempotencyKey.isNotBlank()) {
            "wallet_interaction_controller_renew_binding_invalid"
        }
        require(expectedLeaseRevision > 0) { "wallet_interaction_controller_renew_revision_invalid" }
    }
}

@Serializable
data class TakeoverWalletInteractionControllerArgs(
    val walletUnitId: String,
    val sessionId: WalletInteractionSessionId,
    val appRegistrationId: String,
    val expectedProcessRevision: Long,
    val idempotencyKey: String,
    val reason: WalletInteractionControllerTakeoverReason,
) {
    init {
        require(walletUnitId.isNotBlank() && appRegistrationId.isNotBlank() && idempotencyKey.isNotBlank()) {
            "wallet_interaction_controller_takeover_binding_invalid"
        }
        require(expectedProcessRevision >= 0) { "wallet_interaction_controller_takeover_revision_invalid" }
    }
}

@Serializable
data class ReleaseWalletInteractionControllerArgs(
    val walletUnitId: String,
    val sessionId: WalletInteractionSessionId,
    val appRegistrationId: String,
    val controllerLeaseId: String,
    val expectedLeaseRevision: Long,
    val idempotencyKey: String,
) {
    init {
        require(walletUnitId.isNotBlank() && appRegistrationId.isNotBlank() && controllerLeaseId.isNotBlank() && idempotencyKey.isNotBlank()) {
            "wallet_interaction_controller_release_binding_invalid"
        }
        require(expectedLeaseRevision > 0) { "wallet_interaction_controller_release_revision_invalid" }
    }
}

@Serializable
data class GetWalletInteractionControllerArgs(
    val walletUnitId: String,
    val sessionId: WalletInteractionSessionId,
) {
    init { require(walletUnitId.isNotBlank()) { "wallet_interaction_controller_get_wallet_unit_blank" } }
}

@Serializable
data class GetWalletInteractionControllerResult(val lease: WalletInteractionControllerLease?)

interface AcquireWalletInteractionControllerCommand :
    ServiceCommand<AcquireWalletInteractionControllerArgs, WalletInteractionControllerLeaseResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
    companion object { const val COMMAND_ID = "wallet.interaction.controller-acquire" }
}

interface RenewWalletInteractionControllerCommand :
    ServiceCommand<RenewWalletInteractionControllerArgs, WalletInteractionControllerLeaseResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.UPDATE
    companion object { const val COMMAND_ID = "wallet.interaction.controller-renew" }
}

interface TakeoverWalletInteractionControllerCommand :
    ServiceCommand<TakeoverWalletInteractionControllerArgs, WalletInteractionControllerLeaseResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.UPDATE
    companion object { const val COMMAND_ID = "wallet.interaction.controller-takeover" }
}

interface ReleaseWalletInteractionControllerCommand :
    ServiceCommand<ReleaseWalletInteractionControllerArgs, WalletInteractionControllerLeaseResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.DELETE
    companion object { const val COMMAND_ID = "wallet.interaction.controller-release" }
}

interface GetWalletInteractionControllerCommand :
    ServiceCommand<GetWalletInteractionControllerArgs, GetWalletInteractionControllerResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ
    companion object { const val COMMAND_ID = "wallet.interaction.controller-get" }
}

/** Narrow action gate selected from SessionScope contributions by priority. */
interface WalletInteractionActionAuthority {
    val priority: Int

    suspend fun authorize(
        args: SubmitWalletInteractionActionArgs,
        currentState: WalletInteractionState,
    ): IdkResult<WalletInteractionActionPermit, IdkError>

    suspend fun complete(
        permit: WalletInteractionActionPermit.Proceed,
        state: WalletInteractionState,
    ): IdkResult<Unit, IdkError>

    suspend fun abort(permit: WalletInteractionActionPermit.Proceed): IdkResult<Unit, IdkError>
}

sealed interface WalletInteractionActionPermit {
    data class Proceed(val authorizationRef: String) : WalletInteractionActionPermit
    data class Replay(val state: WalletInteractionState) : WalletInteractionActionPermit
}

/** Optional controller surface exposed by managed backend interaction clients. */
interface WalletInteractionControllerClient {
    suspend fun acquireController(sessionId: WalletInteractionSessionId): WalletInteractionControllerLease
    suspend fun renewController(sessionId: WalletInteractionSessionId): WalletInteractionControllerLease
    suspend fun takeoverController(
        sessionId: WalletInteractionSessionId,
        reason: WalletInteractionControllerTakeoverReason,
    ): WalletInteractionControllerLeaseResult
    suspend fun releaseController(sessionId: WalletInteractionSessionId): WalletInteractionControllerLeaseResult
    suspend fun currentController(sessionId: WalletInteractionSessionId): WalletInteractionControllerLease?
}

@ContributesTo(SessionScope::class)
interface WalletInteractionControllerCommandBindings {
    @Provides
    fun acquireController(registry: SessionScopedCommandRegistry): AcquireWalletInteractionControllerCommand =
        registry.get(AcquireWalletInteractionControllerCommand.COMMAND_ID) as? AcquireWalletInteractionControllerCommand
            ?: error("No binding for ${AcquireWalletInteractionControllerCommand.COMMAND_ID}")

    @Provides
    fun renewController(registry: SessionScopedCommandRegistry): RenewWalletInteractionControllerCommand =
        registry.get(RenewWalletInteractionControllerCommand.COMMAND_ID) as? RenewWalletInteractionControllerCommand
            ?: error("No binding for ${RenewWalletInteractionControllerCommand.COMMAND_ID}")

    @Provides
    fun takeoverController(registry: SessionScopedCommandRegistry): TakeoverWalletInteractionControllerCommand =
        registry.get(TakeoverWalletInteractionControllerCommand.COMMAND_ID) as? TakeoverWalletInteractionControllerCommand
            ?: error("No binding for ${TakeoverWalletInteractionControllerCommand.COMMAND_ID}")

    @Provides
    fun releaseController(registry: SessionScopedCommandRegistry): ReleaseWalletInteractionControllerCommand =
        registry.get(ReleaseWalletInteractionControllerCommand.COMMAND_ID) as? ReleaseWalletInteractionControllerCommand
            ?: error("No binding for ${ReleaseWalletInteractionControllerCommand.COMMAND_ID}")

    @Provides
    fun getController(registry: SessionScopedCommandRegistry): GetWalletInteractionControllerCommand =
        registry.get(GetWalletInteractionControllerCommand.COMMAND_ID) as? GetWalletInteractionControllerCommand
            ?: error("No binding for ${GetWalletInteractionControllerCommand.COMMAND_ID}")
}
