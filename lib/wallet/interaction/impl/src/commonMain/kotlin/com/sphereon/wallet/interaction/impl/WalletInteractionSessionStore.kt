/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStateEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

@Serializable
data class WalletInteractionStoredSession(
    val sessionId: WalletInteractionSessionId,
    val input: WalletInteractionInput,
    val adapterId: String?,
    val state: WalletInteractionState,
)

interface WalletInteractionSessionStore {
    suspend fun save(session: WalletInteractionStoredSession)

    suspend fun load(sessionId: WalletInteractionSessionId): WalletInteractionStoredSession?

    suspend fun events(
        sessionId: WalletInteractionSessionId,
        afterRevision: Long? = null,
    ): List<WalletInteractionStateEvent>

    fun observeEvents(
        sessionId: WalletInteractionSessionId,
        afterRevision: Long? = null,
    ): Flow<WalletInteractionStateEvent>

    suspend fun remove(sessionId: WalletInteractionSessionId)
}

interface WalletInteractionLiveEventBus {
    suspend fun publish(event: WalletInteractionStateEvent)

    fun observeEvents(
        sessionId: WalletInteractionSessionId,
        afterRevision: Long? = null,
    ): Flow<WalletInteractionStateEvent>
}

class ProcessLocalWalletInteractionLiveEventBus(
    extraBufferCapacity: Int = 64,
) : WalletInteractionLiveEventBus {
    private val liveEvents = MutableSharedFlow<WalletInteractionStateEvent>(extraBufferCapacity = extraBufferCapacity)

    override suspend fun publish(event: WalletInteractionStateEvent) {
        liveEvents.emit(event)
    }

    override fun observeEvents(
        sessionId: WalletInteractionSessionId,
        afterRevision: Long?,
    ): Flow<WalletInteractionStateEvent> =
        liveEvents.filter { event ->
            event.sessionId == sessionId && (afterRevision == null || event.revision > afterRevision)
        }
}

class InMemoryWalletInteractionSessionStore(
    private val liveEventBus: WalletInteractionLiveEventBus = ProcessLocalWalletInteractionLiveEventBus(),
) : WalletInteractionSessionStore {
    private val sessions = mutableMapOf<WalletInteractionSessionId, WalletInteractionStoredSession>()
    private val eventHistory = mutableMapOf<WalletInteractionSessionId, MutableList<WalletInteractionStateEvent>>()

    override suspend fun save(session: WalletInteractionStoredSession) {
        sessions[session.sessionId] = session
        val event = session.state.toEvent()
        val events = eventHistory.getOrPut(session.sessionId) { mutableListOf() }
        if (events.lastOrNull()?.revision != event.revision || events.lastOrNull()?.state != event.state) {
            events.add(event)
            liveEventBus.publish(event)
        }
    }

    override suspend fun load(sessionId: WalletInteractionSessionId): WalletInteractionStoredSession? = sessions[sessionId]

    override suspend fun events(
        sessionId: WalletInteractionSessionId,
        afterRevision: Long?,
    ): List<WalletInteractionStateEvent> =
        eventHistory[sessionId]
            .orEmpty()
            .filter { event -> afterRevision == null || event.revision > afterRevision }

    override fun observeEvents(
        sessionId: WalletInteractionSessionId,
        afterRevision: Long?,
    ): Flow<WalletInteractionStateEvent> =
        flow {
            events(sessionId, afterRevision).forEach { event -> emit(event) }
            emitAll(liveEventBus.observeEvents(sessionId, afterRevision))
        }

    override suspend fun remove(sessionId: WalletInteractionSessionId) {
        sessions.remove(sessionId)
        eventHistory.remove(sessionId)
    }

    private fun WalletInteractionState.toEvent(): WalletInteractionStateEvent =
        WalletInteractionStateEvent(
            sessionId = sessionId,
            revision = revision,
            state = this,
        )
}
