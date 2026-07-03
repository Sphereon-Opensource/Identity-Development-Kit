/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletProtocolExecutorTest {
    @Test
    fun defaultSplitExecutorKeepsPresentationSharingAsLocalSecurityOperation() =
        runTest {
            val request =
                WalletProtocolExecutionRequest(
                    operationId = "op-1",
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    protocol = WalletProtocol.OID4VP,
                    operation = WalletSecurityOperation.PRESENTATION_SHARING,
                    audience = "verifier",
                    requiredAssurance = WalletSecurityAssurance.BIOMETRIC,
                )

            val decision = WalletProtocolExecutor.split.plan(request)

            assertEquals(WalletInteractionExecutionMode.SPLIT, decision.executionMode)
            assertEquals(WalletProtocolExecutionPlacement.SPLIT_LOCAL_SECURITY, decision.placement)
            assertEquals(true, decision.securityGateRequired)
            assertEquals(WalletSecurityOperation.PRESENTATION_SHARING, decision.securityOperation)
            assertEquals(WalletSecurityAssurance.BIOMETRIC, decision.requiredAssurance)
            assertEquals("verifier", decision.audience)
        }

    @Test
    fun contextAuthorizationProjectsExecutorDecisionIntoSecurityGateRequest() =
        runTest {
            val gate = RecordingSecurityGate()
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.SPLIT,
                    protocolExecutor =
                        StaticDecisionProtocolExecutor(
                            WalletProtocolExecutionDecision(
                                executionMode = WalletInteractionExecutionMode.SPLIT,
                                placement = WalletProtocolExecutionPlacement.SPLIT_LOCAL_SECURITY,
                                securityOperation = WalletSecurityOperation.LOCAL_HSM_UNLOCK,
                                requiredAssurance = WalletSecurityAssurance.HARDWARE_BACKED,
                                audience = "verifier-overridden",
                                keyRef = "key-1",
                                walletUnitId = "unit-1",
                                walletAccountId = "account-1",
                                activationDecisionId = "decision-1",
                                operationType = "wallet.sign",
                                operationHash = "hash-1",
                                nonce = "nonce-1",
                            ),
                        ),
                    securityGate = gate,
                )

            context.authorizeProtocolOperation(
                WalletProtocolExecutionRequest(
                    operationId = "op-1",
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    protocol = WalletProtocol.OID4VP,
                    operation = WalletSecurityOperation.PRESENTATION_SHARING,
                    audience = "verifier",
                ),
            )

            assertEquals(WalletSecurityOperation.LOCAL_HSM_UNLOCK, gate.lastRequest.operation)
            assertEquals("verifier-overridden", gate.lastRequest.audience)
            assertEquals("key-1", gate.lastRequest.keyRef)
            assertEquals("unit-1", gate.lastRequest.walletUnitId)
            assertEquals("account-1", gate.lastRequest.walletAccountId)
            assertEquals("decision-1", gate.lastRequest.activationDecisionId)
            assertEquals("wallet.sign", gate.lastRequest.operationType)
            assertEquals("hash-1", gate.lastRequest.operationHash)
            assertEquals("nonce-1", gate.lastRequest.nonce)
            assertEquals(WalletSecurityAssurance.HARDWARE_BACKED, gate.lastRequest.requiredAssurance)
        }
}

private class StaticDecisionProtocolExecutor(
    private val decision: WalletProtocolExecutionDecision,
) : WalletProtocolExecutor {
    override val executionMode: WalletInteractionExecutionMode = decision.executionMode

    override suspend fun plan(request: WalletProtocolExecutionRequest): WalletProtocolExecutionDecision = decision
}

private class RecordingSecurityGate : WalletSecurityGate {
    lateinit var lastRequest: WalletSecurityGateRequest

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
