/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStateEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration

/**
 * KV-backed interaction session store for backend and web-wallet runtimes.
 *
 * Persisted events contain the same UI-safe [WalletInteractionState] sent to clients.
 * Protocol secrets belong in [KvWalletInteractionPrivateSessionStore], not in these
 * replayable UI state events.
 */
class KvWalletInteractionSessionStore(
    private val kv: KvStore,
    private val ttl: Duration = Duration.INFINITE,
    private val liveEventBus: WalletInteractionLiveEventBus = ProcessLocalWalletInteractionLiveEventBus(),
    json: Json = walletInteractionStoreJson,
) : WalletInteractionSessionStore {
    private val sessionNamespace =
        KvNamespace(
            name = SESSION_NAMESPACE,
            codec = KotlinxSerializationJsonKvCodec(json, WalletInteractionStoredSession.serializer()),
        )
    private val eventNamespace =
        KvNamespace(
            name = EVENT_NAMESPACE,
            codec = KotlinxSerializationJsonKvCodec(json, KvWalletInteractionEventHistory.serializer()),
        )

    override suspend fun save(session: WalletInteractionStoredSession) {
        kv.put(sessionNamespace, session.sessionId.value, session, ttl).getOrThrow()

        val event = session.state.toEvent()
        val history = eventHistory(session.sessionId)
        if (history.events.lastOrNull()?.revision != event.revision || history.events.lastOrNull()?.state != event.state) {
            kv.put(eventNamespace, session.sessionId.value, history.copy(events = history.events + event), ttl).getOrThrow()
            liveEventBus.publish(event)
        }
    }

    override suspend fun load(sessionId: WalletInteractionSessionId): WalletInteractionStoredSession? = kv.get(sessionNamespace, sessionId.value).getOrThrow()

    override suspend fun events(
        sessionId: WalletInteractionSessionId,
        afterRevision: Long?,
    ): List<WalletInteractionStateEvent> =
        eventHistory(sessionId)
            .events
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
        kv.delete(sessionNamespace, sessionId.value).getOrThrow()
        kv.delete(eventNamespace, sessionId.value).getOrThrow()
    }

    private suspend fun eventHistory(sessionId: WalletInteractionSessionId): KvWalletInteractionEventHistory = kv.get(eventNamespace, sessionId.value).getOrThrow() ?: KvWalletInteractionEventHistory()

    private fun WalletInteractionState.toEvent(): WalletInteractionStateEvent =
        WalletInteractionStateEvent(
            sessionId = sessionId,
            revision = revision,
            state = this,
        )

    private companion object {
        const val SESSION_NAMESPACE = "wallet-interaction-sessions"
        const val EVENT_NAMESPACE = "wallet-interaction-events"
    }
}

@Serializable
private data class KvWalletInteractionEventHistory(
    val events: List<WalletInteractionStateEvent> = emptyList(),
)

internal val walletInteractionStoreJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
