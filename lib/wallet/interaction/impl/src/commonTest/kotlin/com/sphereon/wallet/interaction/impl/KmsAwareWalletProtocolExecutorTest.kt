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
import com.sphereon.wallet.interaction.WalletSecurityAssurance
import com.sphereon.wallet.interaction.WalletSecurityOperation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KmsAwareWalletProtocolExecutorTest {
    @Test
    fun localHardwareBackedProviderRequiresHardwareAssuranceAndLocalUnlock() =
        runTest {
            val executor =
                KmsAwareWalletProtocolExecutor(
                    capabilityResolver =
                        WalletKmsCapabilityResolver {
                            WalletKmsCapability(
                                providerId = "mobile",
                                providerType = "mobile",
                                hardwareBacked = true,
                                digestSigningSupported = true,
                            )
                        },
                )

            val decision = executor.plan(request(keyRef = "holder-key"))

            assertEquals(WalletInteractionExecutionMode.SPLIT, decision.executionMode)
            assertEquals(WalletProtocolExecutionPlacement.SPLIT_LOCAL_SECURITY, decision.placement)
            assertEquals(WalletSecurityOperation.LOCAL_HSM_UNLOCK, decision.securityOperation)
            assertEquals(WalletSecurityAssurance.HARDWARE_BACKED, decision.requiredAssurance)
            assertEquals("holder-key", decision.keyRef)
        }

    @Test
    fun remoteProviderRequiresRemoteAuthorizationWithoutChangingUiStateShape() =
        runTest {
            val executor =
                KmsAwareWalletProtocolExecutor(
                    capabilityResolver =
                        WalletKmsCapabilityResolver {
                            WalletKmsCapability(
                                providerId = "rest",
                                providerType = "rest",
                                hardwareBacked = false,
                                signingSupported = true,
                            )
                        },
                )

            val decision = executor.plan(request(keyRef = "backend-key", walletUnitId = "unit-1", operationHash = "hash-1"))

            assertEquals(WalletSecurityOperation.REMOTE_KEY_AUTHORIZATION, decision.securityOperation)
            assertEquals(WalletSecurityAssurance.REMOTE_AUTHORIZED, decision.requiredAssurance)
            assertEquals("backend-key", decision.keyRef)
            assertEquals("unit-1", decision.walletUnitId)
            assertEquals("hash-1", decision.operationHash)
        }

    @Test
    fun missingKeyRefFallsBackToDefaultSplitDecision() =
        runTest {
            var resolverCalls = 0
            val executor =
                KmsAwareWalletProtocolExecutor(
                    capabilityResolver =
                        WalletKmsCapabilityResolver {
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
            walletInstanceId = "wallet",
            protocol = WalletProtocol.OID4VP,
            operation = WalletSecurityOperation.PRESENTATION_SHARING,
            audience = "verifier",
            keyRef = keyRef,
            walletUnitId = walletUnitId,
            operationHash = operationHash,
        )
}
