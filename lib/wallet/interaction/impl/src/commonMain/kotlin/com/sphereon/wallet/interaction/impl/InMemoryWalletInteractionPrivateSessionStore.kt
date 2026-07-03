/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.WalletInteractionSessionId

class InMemoryWalletInteractionPrivateSessionStore : WalletInteractionPrivateSessionStore {
    private val sessions = mutableMapOf<WalletInteractionSessionId, MutableMap<String, WalletInteractionPrivateSessionData>>()

    override suspend fun put(
        sessionId: WalletInteractionSessionId,
        data: WalletInteractionPrivateSessionData,
    ) {
        sessions.getOrPut(sessionId) { linkedMapOf() }[data.namespace] = data
    }

    override suspend fun get(
        sessionId: WalletInteractionSessionId,
        namespace: String,
    ): WalletInteractionPrivateSessionData? = sessions[sessionId]?.get(namespace)

    override suspend fun remove(
        sessionId: WalletInteractionSessionId,
        namespace: String,
    ) {
        sessions[sessionId]?.remove(namespace)
        if (sessions[sessionId]?.isEmpty() == true) {
            sessions.remove(sessionId)
        }
    }

    override suspend fun removeSession(sessionId: WalletInteractionSessionId) {
        sessions.remove(sessionId)
    }
}
