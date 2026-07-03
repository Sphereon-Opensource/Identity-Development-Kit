/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletSecurityContextAttributes

data class Oid4vpPresentationSecurityContext(
    val keyRef: String? = null,
    val walletUnitId: String? = null,
    val walletAccountId: String? = null,
    val activationDecisionId: String? = null,
    val operationType: String? = null,
    val operationHash: String? = null,
    val nonce: String? = null,
)

interface Oid4vpPresentationSecurityContextResolver {
    suspend fun resolve(
        context: WalletInteractionContext,
        state: WalletInteractionState,
    ): Oid4vpPresentationSecurityContext

    companion object {
        val none: Oid4vpPresentationSecurityContextResolver =
            object : Oid4vpPresentationSecurityContextResolver {
                override suspend fun resolve(
                    context: WalletInteractionContext,
                    state: WalletInteractionState,
                ): Oid4vpPresentationSecurityContext = Oid4vpPresentationSecurityContext()
            }
    }
}

object Oid4vpPresentationSecurityAttributes {
    const val KEY_REF: String = WalletSecurityContextAttributes.KEY_REF
    const val WALLET_UNIT_ID: String = WalletSecurityContextAttributes.WALLET_UNIT_ID
    const val WALLET_ACCOUNT_ID: String = WalletSecurityContextAttributes.WALLET_ACCOUNT_ID
    const val ACTIVATION_DECISION_ID: String = WalletSecurityContextAttributes.ACTIVATION_DECISION_ID
    const val OPERATION_TYPE: String = WalletSecurityContextAttributes.OPERATION_TYPE
    const val OPERATION_HASH: String = WalletSecurityContextAttributes.OPERATION_HASH
    const val NONCE: String = WalletSecurityContextAttributes.NONCE
}
