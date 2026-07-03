/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.wallet.interaction.WalletCounterpartyRole
import com.sphereon.wallet.interaction.WalletCounterpartySummary
import com.sphereon.wallet.interaction.WalletCounterpartyTrustSummary
import com.sphereon.wallet.interaction.WalletSecurityGateRequest
import com.sphereon.wallet.interaction.WalletSecurityGateResult
import com.sphereon.wallet.interaction.WalletSecurityOperation
import com.sphereon.wallet.interaction.WalletTrustPolicyAction
import com.sphereon.wallet.interaction.WalletTrustPolicyMode
import com.sphereon.wallet.interaction.WalletTrustStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletInteractionPolicyDefaultsTest {
    @Test
    fun trustPolicyBlocksBlockedCounterpartiesRegardlessOfMode() =
        runTest {
            val policy = ConfigurableWalletTrustPolicy(WalletTrustPolicyMode.WARN)

            val decision =
                policy.evaluate(
                    WalletCounterpartyTrustSummary(
                        counterparty =
                            WalletCounterpartySummary(
                                role = WalletCounterpartyRole.ISSUER,
                                identifier = "issuer.example",
                            ),
                        status = WalletTrustStatus.BLOCKED,
                    ),
                )

            assertEquals(WalletTrustPolicyAction.BLOCK, decision.action)
        }

    @Test
    fun securityGateChallengeUsesLocalizationKeyAndStringArguments() =
        runTest {
            val gate = StaticWalletSecurityGate(requireChallenge = true)

            val result =
                gate.authorize(
                    WalletSecurityGateRequest(
                        operationId = "op-1",
                        operation = WalletSecurityOperation.PRESENTATION_SHARING,
                    ),
                )

            val challenge = (result as WalletSecurityGateResult.ChallengeRequired).challenge
            assertEquals("wallet.interaction.security.wallet_unit_policy", challenge.reasonKey)
            assertEquals("PRESENTATION_SHARING", challenge.arguments["operation"])
        }
}
