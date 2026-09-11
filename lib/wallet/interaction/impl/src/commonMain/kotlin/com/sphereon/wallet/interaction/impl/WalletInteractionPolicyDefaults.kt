/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.wallet.interaction.WalletCounterpartyTrustSummary
import com.sphereon.wallet.interaction.WalletSecurityChallenge
import com.sphereon.wallet.interaction.WalletSecurityChallengeKind
import com.sphereon.wallet.interaction.WalletSecurityGate
import com.sphereon.wallet.interaction.WalletSecurityGateRequest
import com.sphereon.wallet.interaction.WalletSecurityGateResult
import com.sphereon.wallet.interaction.WalletSecurityGrant
import com.sphereon.wallet.interaction.WalletTrustPolicy
import com.sphereon.wallet.interaction.WalletTrustPolicyAction
import com.sphereon.wallet.interaction.WalletTrustPolicyDecision
import com.sphereon.wallet.interaction.WalletTrustPolicyMode
import com.sphereon.wallet.interaction.WalletTrustStatus

class ConfigurableWalletTrustPolicy(
    private val mode: WalletTrustPolicyMode = WalletTrustPolicyMode.WARN,
) : WalletTrustPolicy {
    override suspend fun evaluate(summary: WalletCounterpartyTrustSummary): WalletTrustPolicyDecision {
        if (summary.status == WalletTrustStatus.TRUSTED) return WalletTrustPolicyDecision(WalletTrustPolicyAction.ALLOW)
        if (summary.status == WalletTrustStatus.BLOCKED) return WalletTrustPolicyDecision(WalletTrustPolicyAction.BLOCK)

        return when (mode) {
            WalletTrustPolicyMode.WARN -> {
                WalletTrustPolicyDecision(WalletTrustPolicyAction.WARN)
            }

            WalletTrustPolicyMode.BLOCK -> {
                WalletTrustPolicyDecision(WalletTrustPolicyAction.BLOCK)
            }

            WalletTrustPolicyMode.ASK_USER -> {
                WalletTrustPolicyDecision(WalletTrustPolicyAction.ASK_USER)
            }

            WalletTrustPolicyMode.FIRST_CONTACT_PROMPT -> {
                WalletTrustPolicyDecision(WalletTrustPolicyAction.FIRST_CONTACT_PROMPT)
            }

            WalletTrustPolicyMode.REMEMBERED_USER_DECISIONS -> {
                if (summary.rememberedDecision) {
                    WalletTrustPolicyDecision(WalletTrustPolicyAction.ALLOW)
                } else {
                    WalletTrustPolicyDecision(WalletTrustPolicyAction.FIRST_CONTACT_PROMPT)
                }
            }
        }
    }
}

class StaticWalletSecurityGate(
    private val requireChallenge: Boolean = false,
) : WalletSecurityGate {
    override suspend fun authorize(request: WalletSecurityGateRequest): WalletSecurityGateResult =
        if (requireChallenge) {
            WalletSecurityGateResult.ChallengeRequired(
                WalletSecurityChallenge(
                    challengeId = request.operationId,
                    kind = WalletSecurityChallengeKind.WALLET_UNIT_POLICY,
                    reasonKey = "wallet.interaction.security.wallet_unit_policy",
                    arguments =
                        buildMap {
                            put("operation", request.operation.name)
                            request.operationBinding?.takeIf { it.isNotBlank() }?.let { put("operation_binding", it) }
                            request.operationHash?.takeIf { it.isNotBlank() }?.let { put("operationHash", it) }
                            request.walletUnitId?.let { put("wallet_unit_id", it) }
                            request.audience?.let { put("audience", it) }
                        },
                    requiredAssurance = request.requiredAssurance,
                ),
            )
        } else {
            WalletSecurityGateResult.Authorized(
                WalletSecurityGrant(
                    grantId = request.operationId,
                    assurance = request.requiredAssurance,
                ),
            )
        }
}
