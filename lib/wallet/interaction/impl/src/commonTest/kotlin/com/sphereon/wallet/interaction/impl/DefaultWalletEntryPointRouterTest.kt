/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionClient
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionFlowKind
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.WalletInteractionSession
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletProtocol
import com.sphereon.wallet.interaction.WalletProtocolCapability
import com.sphereon.wallet.interaction.WalletProtocolMatch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultWalletEntryPointRouterTest {
    @Test
    fun routesCredentialOfferUriToMatchingAdapter() =
        runTest {
            val oid4vciAdapter = SchemeMatchingWalletInteractionProtocolAdapter.oid4vci()
            val engine =
                testWalletInteractionEngine(
                    adapters = listOf(oid4vciAdapter, SchemeMatchingWalletInteractionProtocolAdapter.oid4vp()),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                )
            val router = DefaultWalletEntryPointRouter(engine)

            val result = router.route("openid-credential-offer://issuer.example.com?credential_offer=abc", "wi")

            assertTrue(result.isOk, "route should succeed: ${if (result.isErr) result.error else ""}")
            assertEquals(1, oid4vciAdapter.startedEntryPoints.size)
            assertEquals(WalletProtocol.OID4VCI, engine.observe(result.value).value.protocol)
        }

    @Test
    fun routesPresentationRequestUriToMatchingAdapter() =
        runTest {
            val oid4vpAdapter = SchemeMatchingWalletInteractionProtocolAdapter.oid4vp()
            val engine =
                testWalletInteractionEngine(
                    adapters = listOf(SchemeMatchingWalletInteractionProtocolAdapter.oid4vci(), oid4vpAdapter),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                )
            val router = DefaultWalletEntryPointRouter(engine)

            val result = router.route("openid4vp://verifier.example.com?request_uri=https://verifier.example.com/requests/1", "wi")

            assertTrue(result.isOk, "route should succeed: ${if (result.isErr) result.error else ""}")
            assertEquals(1, oid4vpAdapter.startedEntryPoints.size)
            assertEquals(WalletProtocol.OID4VP, engine.observe(result.value).value.protocol)
        }

    @Test
    fun rejectsUriWithNoMatchingAdapter() =
        runTest {
            val engine =
                testWalletInteractionEngine(
                    adapters =
                        listOf(
                            SchemeMatchingWalletInteractionProtocolAdapter.oid4vci(),
                            SchemeMatchingWalletInteractionProtocolAdapter.oid4vp(),
                        ),
                    sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
                )
            val router = DefaultWalletEntryPointRouter(engine)

            val result = router.route("weird-scheme://whatever", "wi")

            assertTrue(result.isErr)
            assertEquals("WALLET_INTERACTION_UNSUPPORTED_ENTRY_POINT", result.error.code)
        }

    @Test
    fun fastFailsOnBlankUriWithoutStartingClient() =
        runTest {
            val recordingClient = RecordingWalletInteractionClient()
            val router = DefaultWalletEntryPointRouter(recordingClient)

            val result = router.route("   ", "wi")

            assertTrue(result.isErr)
            assertEquals("WALLET_INTERACTION_INVALID_ENTRY_POINT_URI", result.error.code)
            assertEquals(0, recordingClient.startCallCount)
        }

    @Test
    fun fastFailsOnNonUriInputWithoutStartingClient() =
        runTest {
            val recordingClient = RecordingWalletInteractionClient()
            val router = DefaultWalletEntryPointRouter(recordingClient)

            val result = router.route("not a uri", "wi")

            assertTrue(result.isErr)
            assertEquals("WALLET_INTERACTION_INVALID_ENTRY_POINT_URI", result.error.code)
            assertEquals(0, recordingClient.startCallCount)
        }
}

/**
 * Minimal test-only adapter that matches strictly on a raw-string scheme prefix.
 * Unlike [StaticWalletInteractionProtocolAdapter] (which returns a fixed match
 * regardless of the entry point), this exercises the router's delegation to the
 * engine's real per-adapter classification.
 */
private class SchemeMatchingWalletInteractionProtocolAdapter(
    private val scheme: String,
    override val capability: WalletProtocolCapability,
) : WalletInteractionProtocolAdapter {
    val startedEntryPoints = mutableListOf<WalletEntryPoint>()

    override suspend fun canHandle(entryPoint: WalletEntryPoint): WalletProtocolMatch =
        if (entryPoint.raw?.startsWith(scheme) == true) {
            WalletProtocolMatch.strong()
        } else {
            WalletProtocolMatch.none
        }

    override suspend fun start(
        context: WalletInteractionContext,
        entryPoint: WalletEntryPoint,
    ): WalletInteractionSession {
        startedEntryPoints.add(entryPoint)
        val state =
            context.baseState(
                status = WalletInteractionStatus.CredentialOfferReview,
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
    ): WalletInteractionState = sessionState

    companion object {
        fun oid4vci(): SchemeMatchingWalletInteractionProtocolAdapter =
            SchemeMatchingWalletInteractionProtocolAdapter(
                scheme = "openid-credential-offer://",
                capability =
                    WalletProtocolCapability(
                        adapterId = "test-router-oid4vci",
                        protocol = WalletProtocol.OID4VCI,
                        flowKinds = listOf(WalletInteractionFlowKind.CredentialReceive),
                    ),
            )

        fun oid4vp(): SchemeMatchingWalletInteractionProtocolAdapter =
            SchemeMatchingWalletInteractionProtocolAdapter(
                scheme = "openid4vp://",
                capability =
                    WalletProtocolCapability(
                        adapterId = "test-router-oid4vp",
                        protocol = WalletProtocol.OID4VP,
                        flowKinds = listOf(WalletInteractionFlowKind.CredentialPresent),
                    ),
            )
    }
}

/**
 * Records whether [start] was ever invoked, so fast-fail validation paths in
 * [DefaultWalletEntryPointRouter] can be asserted to never reach the client.
 */
private class RecordingWalletInteractionClient : WalletInteractionClient {
    var startCallCount: Int = 0
        private set

    override suspend fun start(input: WalletInteractionInput): WalletInteractionSession {
        startCallCount += 1
        throw AssertionError("start should not be invoked for invalid entry point input")
    }

    override suspend fun load(sessionId: WalletInteractionSessionId): WalletInteractionSession = throw UnsupportedOperationException()

    override suspend fun dispatch(
        sessionId: WalletInteractionSessionId,
        action: WalletInteractionAction,
    ) {
        throw UnsupportedOperationException()
    }

    override suspend fun cancel(sessionId: WalletInteractionSessionId) {
        throw UnsupportedOperationException()
    }

    override fun observe(sessionId: WalletInteractionSessionId): StateFlow<WalletInteractionState> = throw UnsupportedOperationException()
}
