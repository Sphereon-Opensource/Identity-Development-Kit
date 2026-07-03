/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionActionType
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionFlowKind
import com.sphereon.wallet.interaction.WalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.WalletInteractionSession
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletProtocol
import com.sphereon.wallet.interaction.WalletProtocolCapability
import com.sphereon.wallet.interaction.WalletProtocolMatch

class StaticWalletInteractionProtocolAdapter(
    override val capability: WalletProtocolCapability,
    private val match: WalletProtocolMatch,
    private val startStatus: WalletInteractionStatus = WalletInteractionStatus.CounterpartyNotice,
) : WalletInteractionProtocolAdapter {
    override suspend fun canHandle(entryPoint: com.sphereon.wallet.interaction.WalletEntryPoint): WalletProtocolMatch = match

    override suspend fun start(
        context: WalletInteractionContext,
        entryPoint: com.sphereon.wallet.interaction.WalletEntryPoint,
    ): WalletInteractionSession {
        val state =
            context.baseState(
                status = startStatus,
                flowKind = capability.flowKinds.firstOrNull(),
                protocol = capability.protocol,
                adapterId = capability.adapterId,
                entryPoint = entryPoint,
            )
        return WalletInteractionSession(context.sessionId, state)
    }

    override suspend fun handle(
        context: WalletInteractionContext,
        sessionState: WalletInteractionState,
        action: WalletInteractionAction,
    ): WalletInteractionState =
        when (action.type) {
            WalletInteractionActionType.DECLINE -> sessionState.next(WalletInteractionStatus.Cancelled, terminal = true)
            WalletInteractionActionType.CONTINUE -> sessionState.next(WalletInteractionStatus.Completed, terminal = true)
            else -> sessionState.next()
        }

    companion object {
        fun oid4vci(
            adapterId: String = "test-oid4vci",
            match: WalletProtocolMatch = WalletProtocolMatch.strong(),
        ): StaticWalletInteractionProtocolAdapter =
            StaticWalletInteractionProtocolAdapter(
                capability =
                    WalletProtocolCapability(
                        adapterId = adapterId,
                        protocol = WalletProtocol.OID4VCI,
                        flowKinds = listOf(WalletInteractionFlowKind.CredentialReceive),
                    ),
                match = match,
            )
    }
}
