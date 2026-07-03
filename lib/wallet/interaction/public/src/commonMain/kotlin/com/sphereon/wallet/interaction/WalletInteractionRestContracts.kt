/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlinx.serialization.Serializable

/**
 * Public REST projection for wallet interaction sessions.
 *
 * These endpoint commands adapt the neutral wallet interaction commands to HTTP
 * resources. They are not the internal backend command transport. `/events`
 * is a bounded replay/SSE projection; live updates use
 * [WalletInteractionApiConstants.Commands.OBSERVE_EVENTS] over command
 * streaming.
 */
object WalletInteractionApiConstants {
    const val BASE_PATH: String = "/api/wallet/interaction/v1"

    object Paths {
        const val INTERACTIONS: String = "/wallets/{walletInstanceId}/interactions"
        const val INTERACTION: String = "/wallets/{walletInstanceId}/interactions/{sessionId}"
        const val RESUME: String = "/wallets/{walletInstanceId}/interactions/{sessionId}/resume"
        const val ACTIONS: String = "/wallets/{walletInstanceId}/interactions/{sessionId}/actions"
        const val STATE: String = "/wallets/{walletInstanceId}/interactions/{sessionId}/state"
        const val EVENTS: String = "/wallets/{walletInstanceId}/interactions/{sessionId}/events"
        const val FRAMES: String = "/wallets/{walletInstanceId}/interactions/{sessionId}/frames"
    }

    object Commands {
        const val START: String = "wallet.interaction.start"
        const val RESUME: String = "wallet.interaction.resume"
        const val DISPATCH_ACTION: String = "wallet.interaction.submit-action"
        const val CANCEL: String = "wallet.interaction.cancel"
        const val GET_STATE: String = "wallet.interaction.get-state"
        const val GET_EVENTS: String = "wallet.interaction.get-events"
        const val OBSERVE_EVENTS: String = "wallet.interaction.observe-events"
    }

    object EndpointCommands {
        const val START: String = "wallet.interaction.rest.start"
        const val RESUME: String = "wallet.interaction.rest.resume"
        const val DISPATCH_ACTION: String = "wallet.interaction.rest.submit-action"
        const val CANCEL: String = "wallet.interaction.rest.cancel"
        const val GET_STATE: String = "wallet.interaction.rest.get-state"
        const val GET_EVENTS: String = "wallet.interaction.rest.get-events"
        const val FRAME: String = "wallet.interaction.rest.frame"
    }

    object Sse {
        const val EVENT_STATE: String = "wallet-interaction-state"
        const val LAST_EVENT_ID_HEADER: String = "Last-Event-ID"
    }

    object Frame {
        const val HEADER_LAST_REVISION: String = "X-Wallet-Interaction-Last-Revision"
    }
}

@Serializable
data class StartWalletInteractionBody(
    val input: WalletInteractionInput,
)

@Serializable
data class DispatchWalletInteractionActionBody(
    val action: WalletInteractionAction,
)

@Serializable
data class WalletInteractionSessionEnvelope(
    val session: WalletInteractionSession,
)

@Serializable
data class WalletInteractionStateEnvelope(
    val state: WalletInteractionState,
)

@Serializable
data class CancelWalletInteractionResult(
    val sessionId: WalletInteractionSessionId,
    val state: WalletInteractionState,
)

@Serializable
data class WalletInteractionClientFrame(
    val type: WalletInteractionClientFrameType,
    val sessionId: WalletInteractionSessionId,
    val action: WalletInteractionAction? = null,
    val lastRevision: Long? = null,
)

@Serializable
enum class WalletInteractionClientFrameType {
    RESUME,
    DISPATCH_ACTION,
    CANCEL,
    PING,
}

@Serializable
data class WalletInteractionServerFrame(
    val type: WalletInteractionServerFrameType,
    val sessionId: WalletInteractionSessionId,
    val revision: Long? = null,
    val state: WalletInteractionState? = null,
    val error: WalletInteractionError? = null,
)

@Serializable
enum class WalletInteractionServerFrameType {
    STATE,
    ACK,
    ERROR,
    PONG,
}

fun WalletInteractionApiConstants.interactionsPath(walletInstanceId: String): String = "${WalletInteractionApiConstants.BASE_PATH}/wallets/$walletInstanceId/interactions"

fun WalletInteractionApiConstants.interactionPath(
    walletInstanceId: String,
    sessionId: WalletInteractionSessionId,
): String = "${interactionsPath(walletInstanceId)}/${sessionId.value}"

fun WalletInteractionApiConstants.resumePath(
    walletInstanceId: String,
    sessionId: WalletInteractionSessionId,
): String = "${interactionPath(walletInstanceId, sessionId)}/resume"

fun WalletInteractionApiConstants.actionsPath(
    walletInstanceId: String,
    sessionId: WalletInteractionSessionId,
): String = "${interactionPath(walletInstanceId, sessionId)}/actions"

fun WalletInteractionApiConstants.statePath(
    walletInstanceId: String,
    sessionId: WalletInteractionSessionId,
): String = "${interactionPath(walletInstanceId, sessionId)}/state"

fun WalletInteractionApiConstants.eventsPath(
    walletInstanceId: String,
    sessionId: WalletInteractionSessionId,
): String = "${interactionPath(walletInstanceId, sessionId)}/events"

fun WalletInteractionApiConstants.framePath(
    walletInstanceId: String,
    sessionId: WalletInteractionSessionId,
): String = "${interactionPath(walletInstanceId, sessionId)}/frames"
