/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.wallet.interaction.WalletCounterpartyTrustResolver
import com.sphereon.wallet.interaction.WalletDisplayMessage
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionActionType
import com.sphereon.wallet.interaction.WalletInteractionActivityProjection
import com.sphereon.wallet.interaction.WalletInteractionClient
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionEngine
import com.sphereon.wallet.interaction.WalletInteractionError
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.WalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.WalletInteractionSession
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStateEvent
import com.sphereon.wallet.interaction.WalletInteractionStateEventSource
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletProtocolExecutor
import com.sphereon.wallet.interaction.WalletProtocolMatchStrength
import com.sphereon.wallet.interaction.WalletSecurityGate
import com.sphereon.wallet.interaction.WalletTrustPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

class LocalWalletInteractionClient(
    private val engine: WalletInteractionEngine,
) : WalletInteractionClient by engine

class DefaultWalletInteractionEngine(
    adapters: List<WalletInteractionProtocolAdapter> = emptyList(),
    private val sessionIdGenerator: WalletInteractionSessionIdGenerator = RandomWalletInteractionSessionIdGenerator(),
    private val protocolExecutor: WalletProtocolExecutor = WalletProtocolExecutor.walletApp,
    private val counterpartyEncounterRegistry: com.sphereon.wallet.interaction.WalletCounterpartyEncounterRegistry =
        com.sphereon.wallet.interaction.WalletCounterpartyEncounterRegistry.none,
    private val trustResolver: WalletCounterpartyTrustResolver = WalletCounterpartyTrustResolver.unresolved,
    private val trustPolicy: WalletTrustPolicy = WalletTrustPolicy.warn,
    private val securityGate: WalletSecurityGate = WalletSecurityGate.deny,
    private val sensitiveInputAuthority: com.sphereon.wallet.interaction.WalletInteractionSensitiveInputAuthority,
    private val privateSessionStore: WalletInteractionPrivateSessionStore,
    private val sessionStore: WalletInteractionSessionStore,
) : WalletInteractionEngine,
    WalletInteractionStateEventSource {
    private val registry = WalletInteractionProtocolRegistry(adapters)
    private val sessions = mutableMapOf<WalletInteractionSessionId, SessionRecord>()

    fun register(adapter: WalletInteractionProtocolAdapter): DefaultWalletInteractionEngine {
        registry.register(adapter)
        return this
    }

    suspend fun listActivity(
        walletUnitId: String,
        afterSequence: Long? = null,
        limit: Int = 100,
    ): List<WalletInteractionActivityProjection> =
        sessionStore.listActivity(walletUnitId, afterSequence, limit)

    override suspend fun start(input: WalletInteractionInput): WalletInteractionSession {
        val sessionId = sessionIdGenerator.next()
        storeLaunchInput(sessionId, input)
        val resolving = WalletInteractionState.resolving(sessionId, input)
        val record = SessionRecord(input = input, state = MutableStateFlow(resolving), adapter = null)
        sessions[sessionId] = record
        persist(record)

        val matches = registry.matches(input.entryPoint)
        val selected = registry.select(matches)
        val next =
            when (selected) {
                is WalletProtocolSelection.None -> {
                    resolving.next(
                        status = WalletInteractionStatus.UnsupportedEntryPoint,
                        terminal = true,
                        message =
                            WalletDisplayMessage(
                                titleKey = "wallet.interaction.status.unsupported_entry_point",
                                textKey = "wallet.interaction.error.no_registered_adapter",
                            ),
                    )
                }

                is WalletProtocolSelection.Ambiguous -> {
                    resolving.copy(
                        status = WalletInteractionStatus.ImplementationChoiceRequired,
                        revision = resolving.revision + 1,
                        implementationChoices = selected.adapters.map { it.capability.toChoice() },
                    )
                }

                is WalletProtocolSelection.Selected -> {
                    record.adapter = selected.adapter
                    selected.adapter.start(contextFor(sessionId, input), input.entryPoint).state
                }
            }

        val updated = update(record, next)
        cleanupPrivateSessionIfTerminal(updated)
        return WalletInteractionSession(sessionId, updated)
    }

    override suspend fun resume(sessionId: WalletInteractionSessionId): WalletInteractionSession {
        val record = requireRecord(sessionId)
        return WalletInteractionSession(sessionId, record.state.value)
    }

    override suspend fun dispatch(
        sessionId: WalletInteractionSessionId,
        action: WalletInteractionAction,
    ) {
        val record = requireRecord(sessionId)
        if (record.state.value.terminal && action.type != WalletInteractionActionType.CANCEL) return

        val next =
            when (action.type) {
                WalletInteractionActionType.CHOOSE_IMPLEMENTATION -> {
                    chooseImplementation(sessionId, record, action)
                }

                WalletInteractionActionType.CANCEL -> {
                    record.state.value.next(status = WalletInteractionStatus.Cancelled, terminal = true)
                }

                else -> {
                    val adapter = record.adapter
                    if (adapter == null) {
                        record.state.value.next(
                            status = WalletInteractionStatus.Failed,
                            terminal = true,
                            error =
                                WalletInteractionError(
                                    code = "wallet_interaction.no_adapter",
                                    messageKey = "wallet.interaction.error.no_adapter",
                                ),
                        )
                    } else {
                        adapter.handle(contextFor(sessionId, record.input), record.state.value, action)
                    }
                }
            }

        val updated = update(record, next)
        cleanupPrivateSessionIfTerminal(updated)
    }

    override suspend fun cancel(sessionId: WalletInteractionSessionId) {
        dispatch(sessionId, WalletInteractionAction.cancel())
    }

    override fun observe(sessionId: WalletInteractionSessionId): StateFlow<WalletInteractionState> {
        val record = sessions[sessionId] ?: throw unknownSession(sessionId)
        return record.state.asStateFlow()
    }

    override fun observeEvents(
        sessionId: WalletInteractionSessionId,
        afterRevision: Long?,
    ): Flow<WalletInteractionStateEvent> = sessionStore.observeEvents(sessionId, afterRevision)

    override suspend fun events(
        sessionId: WalletInteractionSessionId,
        afterRevision: Long?,
    ): List<WalletInteractionStateEvent> = sessionStore.events(sessionId, afterRevision)

    private suspend fun chooseImplementation(
        sessionId: WalletInteractionSessionId,
        record: SessionRecord,
        action: WalletInteractionAction,
    ): WalletInteractionState {
        val adapterId = action.implementationId
        val adapter = adapterId?.let { registry.get(it) }
        if (adapter == null) {
            return record.state.value.next(
                status = WalletInteractionStatus.ImplementationChoiceRequired,
                error =
                    WalletInteractionError(
                        code = "wallet_interaction.unknown_adapter",
                        messageKey = "wallet.interaction.error.unknown_adapter",
                        retryable = true,
                        arguments = mapOf("adapterId" to adapterId.orEmpty()),
                    ),
            )
        }

        record.adapter = adapter
        return adapter.start(contextFor(sessionId, record.input), record.input.entryPoint).state
    }

    private fun contextFor(
        sessionId: WalletInteractionSessionId,
        input: WalletInteractionInput,
    ): WalletInteractionContext =
        WalletInteractionContext(
            sessionId = sessionId,
            walletUnitId = input.walletUnitId,
            executionOwner = input.executionOwner,
            protocolExecutor = protocolExecutor.withExecutionOwner(input.executionOwner),
            counterpartyEncounterRegistry = counterpartyEncounterRegistry,
            trustResolver = trustResolver,
            trustPolicy = trustPolicy,
            securityGate = securityGate,
            privateSessionStore = privateSessionStore,
            sensitiveInputAuthority = sensitiveInputAuthority,
            attributes = input.metadata,
        )

    private suspend fun cleanupPrivateSessionIfTerminal(state: WalletInteractionState) {
        if (state.terminal && state.completionHandoffRef == null) {
            sensitiveInputAuthority.clear(state.sessionId)
            privateSessionStore.removeSession(state.sessionId)
        }
    }

    private suspend fun update(
        record: SessionRecord,
        state: WalletInteractionState,
    ): WalletInteractionState {
        val current = record.state.value
        val updated =
            if (state.revision <= current.revision && state != current) {
                state.copy(revision = current.revision + 1)
            } else {
                state
            }
        record.state.value = updated
        persist(record)
        return updated
    }

    private suspend fun persist(record: SessionRecord) {
        val state = record.state.value
        sessionStore.save(
            WalletInteractionStoredSession(
                sessionId = state.sessionId,
                input = record.input.redactedForReplay(),
                adapterId = record.adapter?.capability?.adapterId ?: state.adapterId,
                state = state,
            ),
        )
    }

    private suspend fun restoreSession(sessionId: WalletInteractionSessionId): SessionRecord? {
        val stored = sessionStore.load(sessionId) ?: return null
        val adapter = stored.adapterId?.let { adapterId -> registry.get(adapterId) }
        val input = restoreLaunchInput(sessionId, stored.input)
        return SessionRecord(
            input = input,
            state = MutableStateFlow(stored.state),
            adapter = adapter,
        ).also { record -> sessions[sessionId] = record }
    }

    private suspend fun requireRecord(sessionId: WalletInteractionSessionId): SessionRecord = sessions[sessionId] ?: restoreSession(sessionId) ?: throw unknownSession(sessionId)

    private fun unknownSession(sessionId: WalletInteractionSessionId): IllegalArgumentException = IllegalArgumentException("wallet_interaction_session_unknown")

    private suspend fun storeLaunchInput(
        sessionId: WalletInteractionSessionId,
        input: WalletInteractionInput,
    ) {
        privateSessionStore.put(
            sessionId,
            WalletInteractionPrivateSessionData(
                namespace = CORE_PRIVATE_NAMESPACE,
                values = mapOf(LAUNCH_INPUT_KEY to engineJson.encodeToString(input)),
            ),
        )
    }

    private suspend fun restoreLaunchInput(
        sessionId: WalletInteractionSessionId,
        fallback: WalletInteractionInput,
    ): WalletInteractionInput =
        privateSessionStore
            .get(sessionId, CORE_PRIVATE_NAMESPACE)
            ?.values
            ?.get(LAUNCH_INPUT_KEY)
            ?.let { encoded -> runCatching { engineJson.decodeFromString(WalletInteractionInput.serializer(), encoded) }.getOrNull() }
            ?: fallback

    internal suspend fun storedSession(sessionId: WalletInteractionSessionId): WalletInteractionStoredSession? =
        sessionStore.load(sessionId)

    internal suspend fun saveVerifiedWalletAppOutcome(session: WalletInteractionStoredSession) {
        sessionStore.save(session)
    }
}

private fun WalletInteractionInput.redactedForReplay(): WalletInteractionInput = copy(entryPoint = entryPoint.redactedForReplay())

private fun WalletEntryPoint.redactedForReplay(): WalletEntryPoint =
    WalletEntryPoint(
        kind = kind,
        source = source,
        parsedType = parsedType,
    )

private data class SessionRecord(
    val input: WalletInteractionInput,
    val state: MutableStateFlow<WalletInteractionState>,
    var adapter: WalletInteractionProtocolAdapter?,
)

fun interface WalletInteractionSessionIdGenerator {
    fun next(): WalletInteractionSessionId
}

class RandomWalletInteractionSessionIdGenerator : WalletInteractionSessionIdGenerator {
    override fun next(): WalletInteractionSessionId = WalletInteractionSessionId("wi-${Random.nextLong().toString().replace("-", "n")}")
}

class FixedWalletInteractionSessionIdGenerator(
    private val prefix: String = "wi-test",
) : WalletInteractionSessionIdGenerator {
    private var nextValue: Long = 0

    override fun next(): WalletInteractionSessionId {
        nextValue += 1
        return WalletInteractionSessionId("$prefix-$nextValue")
    }
}

private const val CORE_PRIVATE_NAMESPACE = "wallet-interaction-core"
private const val LAUNCH_INPUT_KEY = "launch_input"

private val engineJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

private class WalletInteractionProtocolRegistry(
    adapters: List<WalletInteractionProtocolAdapter>,
) {
    private val adaptersById = linkedMapOf<String, WalletInteractionProtocolAdapter>()

    init {
        adapters.forEach(::register)
    }

    fun register(adapter: WalletInteractionProtocolAdapter) {
        adaptersById[adapter.capability.adapterId] = adapter
    }

    fun get(adapterId: String): WalletInteractionProtocolAdapter? = adaptersById[adapterId]

    suspend fun matches(entryPoint: com.sphereon.wallet.interaction.WalletEntryPoint): List<AdapterMatch> =
        adaptersById.values
            .map { adapter -> AdapterMatch(adapter, adapter.canHandle(entryPoint)) }
            .filter { it.match.canHandle }

    fun select(matches: List<AdapterMatch>): WalletProtocolSelection {
        if (matches.isEmpty()) return WalletProtocolSelection.None

        val strongestRank = matches.maxOf { it.match.strength.rank }
        val strongest = matches.filter { it.match.strength.rank == strongestRank }
        val highestPriority = strongest.maxOf { it.match.priority + it.adapter.capability.priority }
        val best = strongest.filter { it.match.priority + it.adapter.capability.priority == highestPriority }

        return when {
            best.size == 1 -> WalletProtocolSelection.Selected(best.single().adapter)
            strongestRank >= WalletProtocolMatchStrength.STRONG.rank -> WalletProtocolSelection.Ambiguous(best.map { it.adapter })
            else -> WalletProtocolSelection.Selected(best.first().adapter)
        }
    }
}

private data class AdapterMatch(
    val adapter: WalletInteractionProtocolAdapter,
    val match: com.sphereon.wallet.interaction.WalletProtocolMatch,
)

private sealed class WalletProtocolSelection {
    data object None : WalletProtocolSelection()

    data class Selected(
        val adapter: WalletInteractionProtocolAdapter,
    ) : WalletProtocolSelection()

    data class Ambiguous(
        val adapters: List<WalletInteractionProtocolAdapter>,
    ) : WalletProtocolSelection()
}

private val WalletProtocolMatchStrength.rank: Int
    get() =
        when (this) {
            WalletProtocolMatchStrength.NONE -> 0
            WalletProtocolMatchStrength.WEAK -> 1
            WalletProtocolMatchStrength.STRONG -> 2
        }
