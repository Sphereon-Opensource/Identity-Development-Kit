/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.memory.InMemoryKvBackingStorageImpl
import com.sphereon.data.store.kv.memory.InMemoryKvStoreFactoryImpl
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionExecutionMode
import com.sphereon.wallet.interaction.WalletInteractionFlowKind
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import com.sphereon.wallet.interaction.WalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.WalletInteractionSession
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletProtocol
import com.sphereon.wallet.interaction.WalletProtocolCapability
import com.sphereon.wallet.interaction.WalletProtocolExecutionDecision
import com.sphereon.wallet.interaction.WalletProtocolExecutionPlacement
import com.sphereon.wallet.interaction.WalletProtocolExecutionRequest
import com.sphereon.wallet.interaction.WalletProtocolExecutor
import com.sphereon.wallet.interaction.WalletProtocolMatch
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DefaultWalletInteractionEngineTest {
    @Test
    fun selectsSingleStrongAdapterAutomatically() =
        runTest {
            val engine =
                DefaultWalletInteractionEngine(
                    adapters =
                        listOf(
                            StaticWalletInteractionProtocolAdapter.oid4vci(),
                        ),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                )

            val session = engine.start(WalletInteractionInput("wallet", WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x")))

            assertEquals(WalletInteractionStatus.CounterpartyNotice, session.state.status)
            assertEquals(WalletProtocol.OID4VCI, session.state.protocol)
        }

    @Test
    fun emitsImplementationChoiceWhenStrongMatchesTie() =
        runTest {
            val engine =
                DefaultWalletInteractionEngine(
                    adapters =
                        listOf(
                            StaticWalletInteractionProtocolAdapter.oid4vci(adapterId = "a", match = WalletProtocolMatch.strong()),
                            StaticWalletInteractionProtocolAdapter.oid4vci(adapterId = "b", match = WalletProtocolMatch.strong()),
                        ),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                )

            val session = engine.start(WalletInteractionInput("wallet", WalletEntryPoint.rawQr("ambiguous")))

            assertEquals(WalletInteractionStatus.ImplementationChoiceRequired, session.state.status)
            assertEquals(listOf("a", "b"), session.state.implementationChoices.map { it.adapterId })
        }

    @Test
    fun chooseImplementationStartsSelectedAdapter() =
        runTest {
            val engine =
                DefaultWalletInteractionEngine(
                    adapters =
                        listOf(
                            StaticWalletInteractionProtocolAdapter.oid4vci(adapterId = "a", match = WalletProtocolMatch.strong()),
                            StaticWalletInteractionProtocolAdapter(
                                capability =
                                    WalletProtocolCapability(
                                        adapterId = "b",
                                        protocol = WalletProtocol.OID4VP,
                                        flowKinds = listOf(WalletInteractionFlowKind.CredentialPresent),
                                    ),
                                match = WalletProtocolMatch.strong(),
                            ),
                        ),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                )

            val session = engine.start(WalletInteractionInput("wallet", WalletEntryPoint.rawQr("ambiguous")))
            engine.dispatch(session.sessionId, WalletInteractionAction.chooseImplementation("b"))

            assertEquals(WalletProtocol.OID4VP, engine.observe(session.sessionId).value.protocol)
        }

    @Test
    fun unsupportedEntryPointIsTerminal() =
        runTest {
            val engine = DefaultWalletInteractionEngine(sessionIdGenerator = FixedWalletInteractionSessionIdGenerator())

            val session = engine.start(WalletInteractionInput("wallet", WalletEntryPoint.rawQr("not-a-wallet-link")))

            assertEquals(WalletInteractionStatus.UnsupportedEntryPoint, session.state.status)
            assertEquals(true, session.state.terminal)
        }

    @Test
    fun terminalSessionsClearPrivateProtocolState() =
        runTest {
            val privateStore = InMemoryWalletInteractionPrivateSessionStore()
            val engine =
                DefaultWalletInteractionEngine(
                    adapters = listOf(PrivateWritingAdapter()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    privateSessionStore = privateStore,
                )

            val session = engine.start(WalletInteractionInput("wallet", WalletEntryPoint.rawQr("private")))

            assertNotNull(privateStore.get(session.sessionId, "private-test"))
            engine.dispatch(session.sessionId, WalletInteractionAction.decline())
            assertNull(privateStore.get(session.sessionId, "private-test"))
        }

    @Test
    fun contextProtocolExecutorFollowsInputExecutionMode() =
        runTest {
            val executor = RecordingProtocolExecutor(WalletInteractionExecutionMode.LOCAL)
            val adapter = ContextCapturingAdapter()
            val engine =
                DefaultWalletInteractionEngine(
                    adapters = listOf(adapter),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    protocolExecutor = executor,
                )

            engine.start(
                WalletInteractionInput(
                    walletUnitId = "wallet",
                    entryPoint = WalletEntryPoint.rawQr("capture"),
                    executionMode = WalletInteractionExecutionMode.SPLIT,
                ),
            )

            assertEquals(WalletInteractionExecutionMode.SPLIT, adapter.lastContext?.executionMode)
            assertEquals(WalletInteractionExecutionMode.SPLIT, adapter.lastContext?.protocolExecutor?.executionMode)
        }

    @Test
    fun resumesSessionFromSharedSessionStoreAfterEngineRestart() =
        runTest {
            val sessionStore = InMemoryWalletInteractionSessionStore()
            val engine =
                DefaultWalletInteractionEngine(
                    adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sessionStore = sessionStore,
                )
            val started = engine.start(WalletInteractionInput("wallet", WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x")))
            val restartedEngine =
                DefaultWalletInteractionEngine(
                    adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sessionStore = sessionStore,
                )

            val resumed = restartedEngine.resume(started.sessionId)
            restartedEngine.dispatch(started.sessionId, WalletInteractionAction.continueFlow())

            assertEquals(started.state, resumed.state)
            assertEquals(WalletInteractionStatus.Completed, restartedEngine.observe(started.sessionId).value.status)
        }

    @Test
    fun dispatchRestoresSessionFromSharedSessionStoreAfterEngineRestart() =
        runTest {
            val sessionStore = InMemoryWalletInteractionSessionStore()
            val engine =
                DefaultWalletInteractionEngine(
                    adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sessionStore = sessionStore,
                )
            val started = engine.start(WalletInteractionInput("wallet", WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x")))
            val restartedEngine =
                DefaultWalletInteractionEngine(
                    adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sessionStore = sessionStore,
                )

            restartedEngine.dispatch(started.sessionId, WalletInteractionAction.continueFlow())

            assertEquals(WalletInteractionStatus.Completed, restartedEngine.observe(started.sessionId).value.status)
        }

    @Test
    fun eventSourceReplaysStoredStateEventsAfterRevision() =
        runTest {
            val sessionStore = InMemoryWalletInteractionSessionStore()
            val engine =
                DefaultWalletInteractionEngine(
                    adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sessionStore = sessionStore,
                )
            val started = engine.start(WalletInteractionInput("wallet", WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x")))
            engine.dispatch(started.sessionId, WalletInteractionAction.continueFlow())

            val restartedEngine =
                DefaultWalletInteractionEngine(
                    adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sessionStore = sessionStore,
                )
            val events = restartedEngine.observeEvents(started.sessionId, afterRevision = 0).take(2).toList()

            assertEquals(listOf(1L, 2L), events.map { it.revision })
            assertEquals(WalletInteractionStatus.CounterpartyNotice, events.first().state.status)
            assertEquals(WalletInteractionStatus.Completed, events.last().state.status)
        }

    @Test
    fun kvSessionStoreResumesAndReplaysEventsAfterStoreRecreation() =
        runTest {
            val backingStorage = InMemoryKvBackingStorageImpl()
            val firstStore = KvWalletInteractionSessionStore(createInteractionTestKvStore(backingStorage))
            val engine =
                DefaultWalletInteractionEngine(
                    adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sessionStore = firstStore,
                )
            val started = engine.start(WalletInteractionInput("wallet", WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x")))
            engine.dispatch(started.sessionId, WalletInteractionAction.continueFlow())

            val recreatedStore = KvWalletInteractionSessionStore(createInteractionTestKvStore(backingStorage))
            val restartedEngine =
                DefaultWalletInteractionEngine(
                    adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sessionStore = recreatedStore,
                )
            val resumed = restartedEngine.resume(started.sessionId)
            val events = restartedEngine.observeEvents(started.sessionId, afterRevision = 0).take(2).toList()

            assertEquals(WalletInteractionStatus.Completed, resumed.state.status)
            assertEquals(listOf(1L, 2L), events.map { it.revision })
            assertEquals(WalletInteractionStatus.CounterpartyNotice, events.first().state.status)
            assertEquals(WalletInteractionStatus.Completed, events.last().state.status)
        }

    @Test
    fun kvSessionStorePublishesLiveEventsAcrossStoreInstancesWhenEventBusIsShared() =
        runTest {
            val backingStorage = InMemoryKvBackingStorageImpl()
            val liveEventBus = ProcessLocalWalletInteractionLiveEventBus()
            val startingEngine =
                DefaultWalletInteractionEngine(
                    adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sessionStore = KvWalletInteractionSessionStore(createInteractionTestKvStore(backingStorage), liveEventBus = liveEventBus),
                )
            val started = startingEngine.start(WalletInteractionInput("wallet", WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x")))
            val observingEngine =
                DefaultWalletInteractionEngine(
                    adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sessionStore = KvWalletInteractionSessionStore(createInteractionTestKvStore(backingStorage), liveEventBus = liveEventBus),
                )
            val mutatingEngine =
                DefaultWalletInteractionEngine(
                    adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sessionStore = KvWalletInteractionSessionStore(createInteractionTestKvStore(backingStorage), liveEventBus = liveEventBus),
                )

            val pendingLiveEvent =
                async {
                    observingEngine
                        .observeEvents(started.sessionId, afterRevision = started.state.revision)
                        .take(1)
                        .toList()
                        .single()
                }
            yield()

            mutatingEngine.dispatch(started.sessionId, WalletInteractionAction.continueFlow())

            val event = withTimeout(1_000) { pendingLiveEvent.await() }
            assertEquals(started.state.revision + 1, event.revision)
            assertEquals(WalletInteractionStatus.Completed, event.state.status)
        }

    @Test
    fun kvPrivateSessionStorePersistsAndRemovesAllNamespacesForSession() =
        runTest {
            val backingStorage = InMemoryKvBackingStorageImpl()
            val sessionId = WalletInteractionSessionId("session-private")
            val firstStore = KvWalletInteractionPrivateSessionStore(createInteractionTestKvStore(backingStorage))
            firstStore.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = "oid4vci",
                    values = mapOf("access_token" to "secret-token"),
                ),
            )
            firstStore.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = "oid4vp",
                    values = mapOf("request_object" to "secret-request"),
                ),
            )

            val recreatedStore = KvWalletInteractionPrivateSessionStore(createInteractionTestKvStore(backingStorage))
            assertEquals("secret-token", recreatedStore.get(sessionId, "oid4vci")?.values?.get("access_token"))
            assertEquals("secret-request", recreatedStore.get(sessionId, "oid4vp")?.values?.get("request_object"))

            recreatedStore.removeSession(sessionId)
            val afterCleanup = KvWalletInteractionPrivateSessionStore(createInteractionTestKvStore(backingStorage))
            assertNull(afterCleanup.get(sessionId, "oid4vci"))
            assertNull(afterCleanup.get(sessionId, "oid4vp"))
        }

    @Test
    fun kvSessionStoreReplayedEventsDoNotExposeRawEntryPointSecrets() =
        runTest {
            val store = KvWalletInteractionSessionStore(createInteractionTestKvStore())
            val sessionId = WalletInteractionSessionId("session-privacy")
            val state =
                WalletInteractionState(
                    sessionId = sessionId,
                    walletUnitId = "wallet",
                    status = WalletInteractionStatus.CredentialOfferReview,
                    revision = 1,
                    entryPoint = WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=pre-authorized_code-secret").summary(),
                )
            store.save(
                WalletInteractionStoredSession(
                    sessionId = sessionId,
                    input =
                        WalletInteractionInput(
                            walletUnitId = "wallet",
                            entryPoint = WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=pre-authorized_code-secret"),
                            executionMode = WalletInteractionExecutionMode.BACKEND,
                        ),
                    adapterId = "oid4vci",
                    state = state,
                ),
            )

            val encodedEvents = Json.encodeToString(store.events(sessionId))

            assertFalse(encodedEvents.contains("pre-authorized_code-secret"), "Replayed UI event leaked raw entry point")
            assertFalse(encodedEvents.contains("credential_offer=pre-authorized"), "Replayed UI event leaked credential offer")
        }

    @Test
    fun sessionStorePersistsOnlyRedactedLaunchInput() =
        runTest {
            val sessionStore = InMemoryWalletInteractionSessionStore()
            val privateStore = InMemoryWalletInteractionPrivateSessionStore()
            val engine =
                DefaultWalletInteractionEngine(
                    adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sessionStore = sessionStore,
                    privateSessionStore = privateStore,
                )
            val rawOffer = "openid-credential-offer://?credential_offer=pre-authorized_code-secret"

            val started = engine.start(WalletInteractionInput("wallet", WalletEntryPoint.rawQr(rawOffer)))
            val stored = sessionStore.load(started.sessionId)
            val encodedStored = Json.encodeToString(stored)
            val privateData = privateStore.get(started.sessionId, "wallet-interaction-core")

            assertNotNull(stored)
            assertNull(stored.input.entryPoint.raw)
            assertNull(stored.input.entryPoint.bytes)
            assertNull(stored.input.entryPoint.parsed)
            assertFalse(encodedStored.contains("pre-authorized_code-secret"), "Durable session store leaked raw launch input")
            assertEquals(true, privateData?.values?.get("launch_input")?.contains("pre-authorized_code-secret"))
        }

    @Test
    fun restoredImplementationChoiceUsesPrivateLaunchInputWithoutPersistingIt() =
        runTest {
            val sessionStore = InMemoryWalletInteractionSessionStore()
            val privateStore = InMemoryWalletInteractionPrivateSessionStore()
            val selectedAdapter = EntryPointCapturingAdapter()
            val engine =
                DefaultWalletInteractionEngine(
                    adapters =
                        listOf(
                            StaticWalletInteractionProtocolAdapter.oid4vci(adapterId = "a", match = WalletProtocolMatch.strong()),
                            selectedAdapter,
                        ),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sessionStore = sessionStore,
                    privateSessionStore = privateStore,
                )
            val rawOffer = "openid-credential-offer://?credential_offer=private-choice-secret"
            val started = engine.start(WalletInteractionInput("wallet", WalletEntryPoint.rawQr(rawOffer)))
            val storedBeforeRestart = sessionStore.load(started.sessionId)

            val restartedAdapter = EntryPointCapturingAdapter()
            val restartedEngine =
                DefaultWalletInteractionEngine(
                    adapters =
                        listOf(
                            StaticWalletInteractionProtocolAdapter.oid4vci(adapterId = "a", match = WalletProtocolMatch.strong()),
                            restartedAdapter,
                        ),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                    sessionStore = sessionStore,
                    privateSessionStore = privateStore,
                )
            restartedEngine.resume(started.sessionId)
            restartedEngine.dispatch(started.sessionId, WalletInteractionAction.chooseImplementation("capture"))

            assertEquals(WalletInteractionStatus.ImplementationChoiceRequired, started.state.status)
            assertNull(storedBeforeRestart?.input?.entryPoint?.raw)
            assertEquals(rawOffer, restartedAdapter.lastRawEntryPoint)
            assertFalse(Json.encodeToString(sessionStore.load(started.sessionId)).contains("private-choice-secret"))
        }
}

private fun createInteractionTestKvStore(backingStorage: InMemoryKvBackingStorageImpl = InMemoryKvBackingStorageImpl(),): KvStore =
    InMemoryKvStoreFactoryImpl(backingStorage).create(
        InMemoryKvStoreConfig(id = "wallet-interaction-test", scopeBinding = KvStoreScopeBinding.APP),
        execution = null,
    )

private class RecordingProtocolExecutor(
    override val executionMode: WalletInteractionExecutionMode,
) : WalletProtocolExecutor {
    override fun withExecutionMode(mode: WalletInteractionExecutionMode): WalletProtocolExecutor = RecordingProtocolExecutor(mode)

    override suspend fun plan(request: WalletProtocolExecutionRequest): WalletProtocolExecutionDecision =
        WalletProtocolExecutionDecision(
            executionMode = executionMode,
            placement =
                if (executionMode == WalletInteractionExecutionMode.SPLIT) {
                    WalletProtocolExecutionPlacement.SPLIT_LOCAL_SECURITY
                } else {
                    WalletProtocolExecutionPlacement.LOCAL
                },
        )
}

private class ContextCapturingAdapter : WalletInteractionProtocolAdapter {
    var lastContext: WalletInteractionContext? = null

    override val capability: WalletProtocolCapability =
        WalletProtocolCapability(
            adapterId = "context-capture",
            protocol = WalletProtocol.CUSTOM,
            flowKinds = listOf(WalletInteractionFlowKind.CredentialReceive),
        )

    override suspend fun canHandle(entryPoint: WalletEntryPoint): WalletProtocolMatch = WalletProtocolMatch.strong()

    override suspend fun start(
        context: WalletInteractionContext,
        entryPoint: WalletEntryPoint,
    ): WalletInteractionSession {
        lastContext = context
        return WalletInteractionSession(
            context.sessionId,
            context.baseState(
                status = WalletInteractionStatus.CounterpartyNotice,
                flowKind = WalletInteractionFlowKind.CredentialReceive,
                protocol = WalletProtocol.CUSTOM,
                adapterId = capability.adapterId,
                entryPoint = entryPoint,
            ),
        )
    }

    override suspend fun handle(
        context: WalletInteractionContext,
        sessionState: com.sphereon.wallet.interaction.WalletInteractionState,
        action: WalletInteractionAction,
    ): com.sphereon.wallet.interaction.WalletInteractionState = sessionState.next()
}

private class PrivateWritingAdapter : WalletInteractionProtocolAdapter {
    override val capability: WalletProtocolCapability =
        WalletProtocolCapability(
            adapterId = "private-test",
            protocol = WalletProtocol.CUSTOM,
            flowKinds = listOf(WalletInteractionFlowKind.CredentialReceive),
        )

    override suspend fun canHandle(entryPoint: WalletEntryPoint): WalletProtocolMatch = WalletProtocolMatch.strong()

    override suspend fun start(
        context: WalletInteractionContext,
        entryPoint: WalletEntryPoint,
    ): WalletInteractionSession {
        context.privateSessionStore.put(
            context.sessionId,
            WalletInteractionPrivateSessionData(
                namespace = capability.adapterId,
                values = mapOf("raw" to entryPoint.raw.orEmpty()),
            ),
        )
        return WalletInteractionSession(
            context.sessionId,
            context.baseState(
                status = WalletInteractionStatus.CounterpartyNotice,
                flowKind = WalletInteractionFlowKind.CredentialReceive,
                protocol = WalletProtocol.CUSTOM,
                adapterId = capability.adapterId,
                entryPoint = entryPoint,
            ),
        )
    }

    override suspend fun handle(
        context: WalletInteractionContext,
        sessionState: com.sphereon.wallet.interaction.WalletInteractionState,
        action: WalletInteractionAction,
    ): com.sphereon.wallet.interaction.WalletInteractionState =
        when (action.type) {
            com.sphereon.wallet.interaction.WalletInteractionActionType.DECLINE -> sessionState.next(WalletInteractionStatus.Cancelled, terminal = true)
            else -> sessionState.next()
        }
}

private class EntryPointCapturingAdapter : WalletInteractionProtocolAdapter {
    var lastRawEntryPoint: String? = null

    override val capability: WalletProtocolCapability =
        WalletProtocolCapability(
            adapterId = "capture",
            protocol = WalletProtocol.CUSTOM,
            flowKinds = listOf(WalletInteractionFlowKind.CredentialReceive),
        )

    override suspend fun canHandle(entryPoint: WalletEntryPoint): WalletProtocolMatch = WalletProtocolMatch.strong()

    override suspend fun start(
        context: WalletInteractionContext,
        entryPoint: WalletEntryPoint,
    ): WalletInteractionSession {
        lastRawEntryPoint = entryPoint.raw
        return WalletInteractionSession(
            context.sessionId,
            context.baseState(
                status = WalletInteractionStatus.CounterpartyNotice,
                flowKind = WalletInteractionFlowKind.CredentialReceive,
                protocol = WalletProtocol.CUSTOM,
                adapterId = capability.adapterId,
                entryPoint = entryPoint,
            ),
        )
    }

    override suspend fun handle(
        context: WalletInteractionContext,
        sessionState: WalletInteractionState,
        action: WalletInteractionAction,
    ): WalletInteractionState = sessionState.next()
}
