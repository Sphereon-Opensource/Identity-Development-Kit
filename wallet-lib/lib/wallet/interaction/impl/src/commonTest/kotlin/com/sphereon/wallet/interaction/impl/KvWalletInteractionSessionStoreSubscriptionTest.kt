/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.memory.InMemoryKvBackingStorageImpl
import com.sphereon.data.store.kv.memory.InMemoryKvStoreFactoryImpl
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStateEvent
import com.sphereon.wallet.interaction.WalletInteractionStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KvWalletInteractionSessionStoreSubscriptionTest {
    @Test
    fun observesAnEventPublishedDuringHistoryReadExactlyOnce() =
        runTest {
            val backingStorage = InMemoryKvBackingStorageImpl()
            val kv = createTestKvStore(backingStorage)
            val sessionId = WalletInteractionSessionId("history-live-race")
            val liveBus = ClosingWalletInteractionLiveEventBus()
            val writer = KvWalletInteractionSessionStore(kv, liveEventBus = liveBus)
            val eventInHistory = storedSession(sessionId, revision = 1)
            val eventPublishedDuringHistoryRead = storedSession(sessionId, revision = 2)
            val hookedKv = HistoryReadHookKvStore(kv) {
                writer.save(eventPublishedDuringHistoryRead)
                liveBus.publish(
                    WalletInteractionStateEvent(
                        sessionId = sessionId,
                        revision = eventInHistory.state.revision,
                        state = eventInHistory.state,
                    ),
                )
                liveBus.close()
            }
            val observer = KvWalletInteractionSessionStore(hookedKv, liveEventBus = liveBus)

            writer.save(eventInHistory)
            val events = observer.observeEvents(sessionId).toList()

            assertEquals(listOf(1L, 2L), events.map { it.revision })
            assertEquals(1, events.count { it.revision == eventInHistory.state.revision })
            assertEquals(1, events.count { it.revision == eventPublishedDuringHistoryRead.state.revision })
        }

    private fun storedSession(sessionId: WalletInteractionSessionId, revision: Long) =
        WalletInteractionStoredSession(
            sessionId = sessionId,
            input = WalletInteractionInput("wallet", WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x")),
            adapterId = "oid4vci",
            state =
                WalletInteractionState(
                    sessionId = sessionId,
                    walletUnitId = "wallet",
                    status = WalletInteractionStatus.Sharing,
                    revision = revision,
                ),
        )

    private class HistoryReadHookKvStore(
        private val delegate: KvStore,
        private var afterHistoryRead: suspend () -> Unit,
    ) : KvStore by delegate {
        override suspend fun <V : Any> get(namespace: KvNamespace<V>, key: String): IdkResult<V?, IdkError> {
            val result = delegate.get(namespace, key)
            if (namespace.name == EVENT_NAMESPACE) {
                val hook = afterHistoryRead
                afterHistoryRead = {}
                hook()
            }
            return result
        }

        private companion object {
            const val EVENT_NAMESPACE = "wallet-interaction-events"
        }
    }

    private class ClosingWalletInteractionLiveEventBus : WalletInteractionLiveEventBus {
        private val events = Channel<WalletInteractionStateEvent>(Channel.UNLIMITED)
        private var hasObserver = false

        override suspend fun publish(event: WalletInteractionStateEvent) {
            if (hasObserver) events.send(event)
        }

        override fun observeEvents(
            sessionId: WalletInteractionSessionId,
            afterRevision: Long?,
        ): Flow<WalletInteractionStateEvent> =
            kotlinx.coroutines.flow.flow {
                hasObserver = true
                for (event in events) {
                    if (event.sessionId == sessionId && (afterRevision == null || event.revision > afterRevision)) emit(event)
                }
            }

        fun close() {
            events.close()
        }
    }
}

private fun createTestKvStore(backingStorage: InMemoryKvBackingStorageImpl): KvStore =
    InMemoryKvStoreFactoryImpl(backingStorage).create(
        InMemoryKvStoreConfig(id = "wallet-interaction-session-store-subscription-test", scopeBinding = KvStoreScopeBinding.APP),
        execution = null,
    )
