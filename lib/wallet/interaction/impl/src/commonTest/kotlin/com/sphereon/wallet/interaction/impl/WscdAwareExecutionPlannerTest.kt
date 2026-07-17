/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.wallet.interaction.WalletInteractionExecutionMode
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletProtocol
import com.sphereon.wallet.interaction.WalletProtocolExecutionPlacement
import com.sphereon.wallet.interaction.WalletProtocolExecutionRequest
import com.sphereon.wallet.interaction.WalletProtocolExecutor
import com.sphereon.wallet.interaction.WalletSecurityAssurance
import com.sphereon.wallet.interaction.WalletSecurityOperation
import com.sphereon.wallet.wscd.WscdProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WscdAwareExecutionPlannerTest {
    @Test
    fun localNativeProfileRequiresHardwareAssuranceAndLocalUnlock() =
        runTest {
            val executor =
                WscdAwareExecutionPlanner(
                    profileSource = WscdExecutionProfileSource { WscdProfile.LocalNative },
                )

            val decision = executor.plan(request(keyRef = "holder-key"))

            assertEquals(WalletInteractionExecutionMode.SPLIT, decision.executionMode)
            assertEquals(WalletProtocolExecutionPlacement.SPLIT_LOCAL_SECURITY, decision.placement)
            assertEquals(WalletSecurityOperation.LOCAL_HSM_UNLOCK, decision.securityOperation)
            assertEquals(WalletSecurityAssurance.HARDWARE_BACKED, decision.requiredAssurance)
            assertEquals("holder-key", decision.keyRef)
        }

    @Test
    fun localExternalAndLocalInternalProfilesAlsoRequireHardwareAssuranceAndLocalUnlock() =
        runTest {
            for (profile in listOf(WscdProfile.LocalExternal, WscdProfile.LocalInternal)) {
                val executor =
                    WscdAwareExecutionPlanner(
                        profileSource = WscdExecutionProfileSource { profile },
                    )

                val decision = executor.plan(request(keyRef = "holder-key"))

                assertEquals(WalletSecurityOperation.LOCAL_HSM_UNLOCK, decision.securityOperation, "profile=$profile")
                assertEquals(WalletSecurityAssurance.HARDWARE_BACKED, decision.requiredAssurance, "profile=$profile")
            }
        }

    @Test
    fun remoteProfileRequiresRemoteAuthorizationWithoutChangingUiStateShape() =
        runTest {
            val executor =
                WscdAwareExecutionPlanner(
                    profileSource = WscdExecutionProfileSource { WscdProfile.Remote },
                )

            val decision = executor.plan(request(keyRef = "backend-key", walletUnitId = "unit-1", operationHash = "hash-1"))

            assertEquals(WalletSecurityOperation.REMOTE_KEY_AUTHORIZATION, decision.securityOperation)
            assertEquals(WalletSecurityAssurance.REMOTE_AUTHORIZED, decision.requiredAssurance)
            assertEquals("backend-key", decision.keyRef)
            assertEquals("unit-1", decision.walletUnitId)
            assertEquals("hash-1", decision.operationHash)
        }

    @Test
    fun softwareProfileAppliesNoGateOverlayAndLeavesDelegateDecisionUnchanged() =
        runTest {
            val delegate = WalletProtocolExecutor.split
            val executor =
                WscdAwareExecutionPlanner(
                    profileSource = WscdExecutionProfileSource { WscdProfile.Software },
                    delegate = delegate,
                )
            val req = request(keyRef = "software-key")

            val decision = executor.plan(req)
            val delegateDecision = delegate.plan(req)

            assertEquals(delegateDecision, decision)
        }

    @Test
    fun unknownProfileFallsBackToDefaultSplitDecision() =
        runTest {
            var resolverCalls = 0
            val executor =
                WscdAwareExecutionPlanner(
                    profileSource =
                        WscdExecutionProfileSource {
                            resolverCalls += 1
                            null
                        },
                )

            val decision = executor.plan(request(keyRef = null))

            assertEquals(WalletProtocolExecutionPlacement.SPLIT_LOCAL_SECURITY, decision.placement)
            assertEquals(WalletSecurityOperation.PRESENTATION_SHARING, decision.securityOperation)
            assertEquals(WalletSecurityAssurance.USER_PRESENT, decision.requiredAssurance)
            assertEquals(1, resolverCalls)
        }

    private fun request(
        keyRef: String?,
        walletUnitId: String? = null,
        operationHash: String? = null,
    ): WalletProtocolExecutionRequest =
        WalletProtocolExecutionRequest(
            operationId = "op-1",
            sessionId = WalletInteractionSessionId("s1"),
            sessionWalletUnitId = "wallet",
            protocol = WalletProtocol.OID4VP,
            operation = WalletSecurityOperation.PRESENTATION_SHARING,
            audience = "verifier",
            keyRef = keyRef,
            walletUnitId = walletUnitId,
            operationHash = operationHash,
        )
}
