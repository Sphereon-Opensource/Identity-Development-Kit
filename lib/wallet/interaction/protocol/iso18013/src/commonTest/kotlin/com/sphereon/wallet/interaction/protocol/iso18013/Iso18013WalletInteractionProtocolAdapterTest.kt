/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.iso18013

import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionExecutionMode
import com.sphereon.wallet.interaction.WalletInteractionFlowKind
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletProtocol
import com.sphereon.wallet.interaction.WalletProtocolMatchStrength
import com.sphereon.wallet.interaction.WalletSecurityAssurance
import com.sphereon.wallet.interaction.WalletSecurityContextAttributes
import com.sphereon.wallet.interaction.WalletSecurityGate
import com.sphereon.wallet.interaction.WalletSecurityGateRequest
import com.sphereon.wallet.interaction.WalletSecurityGateResult
import com.sphereon.wallet.interaction.WalletSecurityGrant
import com.sphereon.wallet.interaction.WalletSecurityOperation
import com.sphereon.wallet.interaction.impl.DefaultWalletInteractionEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Iso18013WalletInteractionProtocolAdapterTest {
    @Test
    fun mdocUriFormsMapToStrongMatches() =
        runTest {
            val adapter = Iso18013WalletInteractionProtocolAdapter()

            assertEquals(WalletProtocolMatchStrength.STRONG, adapter.canHandle(WalletEntryPoint.rawQr("mdoc:abc")).strength)
            assertEquals(WalletProtocolMatchStrength.STRONG, adapter.canHandle(WalletEntryPoint.rawQr("mdoc://abc")).strength)
            assertEquals(WalletProtocolMatchStrength.STRONG, adapter.canHandle(WalletEntryPoint.rawQr("mdoc-openid4vp://?client_id=x")).strength)
        }

    @Test
    fun nfcAndBleHandoverEntryPointsMapToStrongMatches() =
        runTest {
            val adapter = Iso18013WalletInteractionProtocolAdapter()

            assertEquals(WalletProtocolMatchStrength.STRONG, adapter.canHandle(WalletEntryPoint.nfc(byteArrayOf(1, 2, 3))).strength)
            assertEquals(WalletProtocolMatchStrength.STRONG, adapter.canHandle(WalletEntryPoint.ble(byteArrayOf(4, 5, 6))).strength)
        }

    @Test
    fun nfcAndBleHandoverStartAsDisclosureConsentWithoutLeakingPayloads() =
        runTest {
            val adapter = Iso18013WalletInteractionProtocolAdapter()
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                )

            val nfc = adapter.start(context, WalletEntryPoint.nfc("nfc-private-payload".encodeToByteArray()))
            val ble = adapter.start(context, WalletEntryPoint.ble("ble-private-payload".encodeToByteArray()))
            val encodedNfc = Json.encodeToString(nfc.state)
            val encodedBle = Json.encodeToString(ble.state)

            assertEquals(WalletInteractionStatus.DisclosureConsent, nfc.state.status)
            assertEquals(WalletInteractionStatus.DisclosureConsent, ble.state.status)
            assertEquals(WalletProtocol.ISO18013, nfc.state.protocol)
            assertEquals(WalletProtocol.ISO18013, ble.state.protocol)
            assertFalse(encodedNfc.contains("nfc-private-payload"))
            assertFalse(encodedBle.contains("ble-private-payload"))
        }

    @Test
    fun wifiAwareEntryPointIsExplicitlyTransportUnavailable() =
        runTest {
            val adapter = Iso18013WalletInteractionProtocolAdapter()
            val session =
                adapter.start(
                    WalletInteractionContext(
                        sessionId = WalletInteractionSessionId("s1"),
                        walletInstanceId = "wallet",
                        executionMode = WalletInteractionExecutionMode.LOCAL,
                    ),
                    WalletEntryPoint.wifiAware(byteArrayOf(1, 2, 3)),
                )

            assertEquals(WalletInteractionStatus.UnsupportedEntryPoint, session.state.status)
            assertEquals(true, session.state.terminal)
        }

    @Test
    fun continueWithoutDisclosureExecutorDoesNotPretendDeviceResponseWasSent() =
        runTest {
            val adapter = Iso18013WalletInteractionProtocolAdapter()
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("mdoc:abc"))

            val next = adapter.handle(context, session.state, WalletInteractionAction.continueFlow())

            assertEquals(WalletInteractionStatus.Failed, next.status)
            assertEquals("iso18013.execution_not_configured", next.error?.code)
            assertEquals(true, next.error?.retryable)
            assertFalse(next.terminal)
        }

    @Test
    fun disclosureExecutorSentResultCompletesAfterFinalApproval() =
        runTest {
            val executor = RecordingDisclosureExecutor(Iso18013DisclosureExecutionResult.Sent())
            val adapter = Iso18013WalletInteractionProtocolAdapter(disclosureExecutor = executor)
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("mdoc:abc"))

            val next = adapter.handle(context, session.state, WalletInteractionAction.continueFlow())

            assertEquals(WalletInteractionStatus.Completed, next.status)
            assertEquals(true, next.terminal)
            assertEquals("wallet.interaction.status.mdoc_shared", next.message?.titleKey)
            assertEquals(1, executor.sendCalls)
        }

    @Test
    fun mdocDisclosureAuthorizationCarriesWalletUnitSecurityContext() =
        runTest {
            val executor = RecordingDisclosureExecutor(Iso18013DisclosureExecutionResult.Sent())
            val securityGate = RecordingSecurityGate()
            val adapter = Iso18013WalletInteractionProtocolAdapter(disclosureExecutor = executor)
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.SPLIT,
                    securityGate = securityGate,
                    attributes =
                        mapOf(
                            WalletSecurityContextAttributes.KEY_REF to "mdoc-key",
                            WalletSecurityContextAttributes.WALLET_UNIT_ID to "wallet-unit-mdoc",
                            WalletSecurityContextAttributes.WALLET_ACCOUNT_ID to "wallet-account-mdoc",
                            WalletSecurityContextAttributes.ACTIVATION_DECISION_ID to "activation-mdoc",
                            WalletSecurityContextAttributes.OPERATION_TYPE to "wallet.mdoc-disclosure",
                            WalletSecurityContextAttributes.OPERATION_HASH to "sha256:mdoc-disclosure",
                            WalletSecurityContextAttributes.NONCE to "nonce-mdoc",
                        ),
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("mdoc:abc"))

            val next = adapter.handle(context, session.state, WalletInteractionAction.continueFlow())

            assertEquals(WalletInteractionStatus.Completed, next.status)
            assertEquals(WalletSecurityOperation.PRESENTATION_SHARING, securityGate.lastRequest?.operation)
            assertEquals("mdoc-key", securityGate.lastRequest?.keyRef)
            assertEquals("wallet-unit-mdoc", securityGate.lastRequest?.walletUnitId)
            assertEquals("wallet-account-mdoc", securityGate.lastRequest?.walletAccountId)
            assertEquals("activation-mdoc", securityGate.lastRequest?.activationDecisionId)
            assertEquals("wallet.mdoc-disclosure", securityGate.lastRequest?.operationType)
            assertEquals("sha256:mdoc-disclosure", securityGate.lastRequest?.operationHash)
            assertEquals("nonce-mdoc", securityGate.lastRequest?.nonce)
        }

    @Test
    fun neutralInteractionEngineRunsIso18013DisclosureEndToEnd() =
        runTest {
            val executor = RecordingDisclosureExecutor(Iso18013DisclosureExecutionResult.Sent())
            val engine =
                DefaultWalletInteractionEngine(
                    adapters = listOf(Iso18013WalletInteractionProtocolAdapter(disclosureExecutor = executor)),
                )

            val session =
                engine.start(
                    WalletInteractionInput(
                        walletInstanceId = "wallet",
                        entryPoint = WalletEntryPoint.rawQr("mdoc:abc"),
                    ),
                )

            assertEquals(WalletProtocol.ISO18013, session.state.protocol)
            assertEquals(WalletInteractionStatus.DisclosureConsent, session.state.status)

            engine.dispatch(session.sessionId, WalletInteractionAction.continueFlow())
            val completed = engine.observe(session.sessionId).value

            assertEquals(WalletInteractionStatus.Completed, completed.status)
            assertTrue(completed.terminal)
            assertEquals(1, executor.sendCalls)
        }

    @Test
    fun approvingSecurityChallengeSendsDeviceResponseWithoutLeakingGrant() =
        runTest {
            val privateStore = RecordingPrivateSessionStore()
            val executor = RecordingDisclosureExecutor(Iso18013DisclosureExecutionResult.Sent())
            val adapter = Iso18013WalletInteractionProtocolAdapter(disclosureExecutor = executor)
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                    privateSessionStore = privateStore,
                )
            val state =
                WalletInteractionState(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    status = WalletInteractionStatus.SecurityUnlockRequired,
                    flowKind = WalletInteractionFlowKind.AttendedPresent,
                    protocol = WalletProtocol.ISO18013,
                    adapterId = Iso18013WalletInteractionProtocolAdapter.ADAPTER_ID,
                )

            val next =
                adapter.handle(
                    context,
                    state,
                    WalletInteractionAction.approveSecurityChallenge(
                        WalletSecurityGrant(
                            grantId = "grant-secret",
                            assurance = WalletSecurityAssurance.USER_PRESENT,
                        ),
                    ),
                )
            val encoded = Json.encodeToString(next)

            assertEquals(WalletInteractionStatus.Completed, next.status)
            assertEquals("grant-secret", privateStore.get(next.sessionId, Iso18013WalletInteractionProtocolAdapter.ADAPTER_ID)?.values?.get("security_grant_id"))
            assertFalse(encoded.contains("grant-secret"))
        }

    private class RecordingDisclosureExecutor(
        private val result: Iso18013DisclosureExecutionResult,
    ) : Iso18013DisclosureExecutor {
        var sendCalls: Int = 0

        override suspend fun sendDeviceResponse(
            context: WalletInteractionContext,
            state: WalletInteractionState,
        ): Iso18013DisclosureExecutionResult {
            sendCalls += 1
            return result
        }
    }

    private class RecordingPrivateSessionStore : WalletInteractionPrivateSessionStore {
        private val records = mutableMapOf<Pair<WalletInteractionSessionId, String>, WalletInteractionPrivateSessionData>()

        override suspend fun put(
            sessionId: WalletInteractionSessionId,
            data: WalletInteractionPrivateSessionData,
        ) {
            records[sessionId to data.namespace] = data
        }

        override suspend fun get(
            sessionId: WalletInteractionSessionId,
            namespace: String,
        ): WalletInteractionPrivateSessionData? = records[sessionId to namespace]

        override suspend fun remove(
            sessionId: WalletInteractionSessionId,
            namespace: String,
        ) {
            records.remove(sessionId to namespace)
        }

        override suspend fun removeSession(sessionId: WalletInteractionSessionId) {
            val keys = records.keys.filter { it.first == sessionId }
            keys.forEach { records.remove(it) }
        }
    }

    private class RecordingSecurityGate : WalletSecurityGate {
        var lastRequest: WalletSecurityGateRequest? = null

        override suspend fun authorize(request: WalletSecurityGateRequest): WalletSecurityGateResult {
            lastRequest = request
            return WalletSecurityGateResult.Authorized(
                WalletSecurityGrant(
                    grantId = request.operationId,
                    assurance = request.requiredAssurance,
                ),
            )
        }
    }
}
