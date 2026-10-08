/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlinx.serialization.Serializable

@Serializable
data class WalletInteractionPrivateSessionData(
    val namespace: String,
    val values: Map<String, String> = emptyMap(),
) {
    init {
        require(namespace.isNotBlank()) { "wallet_interaction_private_session_namespace_blank" }
    }
}

interface WalletInteractionPrivateSessionStore {
    suspend fun put(
        sessionId: WalletInteractionSessionId,
        data: WalletInteractionPrivateSessionData,
    )

    suspend fun get(
        sessionId: WalletInteractionSessionId,
        namespace: String,
    ): WalletInteractionPrivateSessionData?

    suspend fun remove(
        sessionId: WalletInteractionSessionId,
        namespace: String,
    )

    suspend fun removeSession(sessionId: WalletInteractionSessionId)

    companion object {
        val none: WalletInteractionPrivateSessionStore =
            object : WalletInteractionPrivateSessionStore {
                override suspend fun put(
                    sessionId: WalletInteractionSessionId,
                    data: WalletInteractionPrivateSessionData,
                ) {
                    // Intentionally empty. Use only for stateless tests/adapters.
                }

                override suspend fun get(
                    sessionId: WalletInteractionSessionId,
                    namespace: String,
                ): WalletInteractionPrivateSessionData? = null

                override suspend fun remove(
                    sessionId: WalletInteractionSessionId,
                    namespace: String,
                ) {
                    // Intentionally empty. Use only for stateless tests/adapters.
                }

                override suspend fun removeSession(sessionId: WalletInteractionSessionId) {
                    // Intentionally empty. Use only for stateless tests/adapters.
                }
            }
    }
}
