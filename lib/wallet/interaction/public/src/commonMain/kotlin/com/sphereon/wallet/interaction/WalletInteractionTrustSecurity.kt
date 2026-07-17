/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.interaction

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface WalletCounterpartyTrustResolver {
    suspend fun resolve(input: WalletCounterpartyTrustRequest): WalletCounterpartyTrustSummary

    companion object {
        val unresolved: WalletCounterpartyTrustResolver =
            object : WalletCounterpartyTrustResolver {
                override suspend fun resolve(input: WalletCounterpartyTrustRequest): WalletCounterpartyTrustSummary =
                    WalletCounterpartyTrustSummary(
                        counterparty = input.counterparty,
                        status = WalletTrustStatus.UNKNOWN,
                        policyAction = WalletTrustPolicyAction.WARN,
                        sources = input.sources,
                    )
            }
    }
}

interface WalletTrustPolicy {
    suspend fun evaluate(summary: WalletCounterpartyTrustSummary): WalletTrustPolicyDecision

    companion object {
        val warn: WalletTrustPolicy =
            object : WalletTrustPolicy {
                override suspend fun evaluate(summary: WalletCounterpartyTrustSummary): WalletTrustPolicyDecision =
                    when (summary.status) {
                        WalletTrustStatus.TRUSTED -> WalletTrustPolicyDecision(WalletTrustPolicyAction.ALLOW)
                        WalletTrustStatus.BLOCKED -> WalletTrustPolicyDecision(WalletTrustPolicyAction.BLOCK)
                        else -> WalletTrustPolicyDecision(WalletTrustPolicyAction.WARN)
                    }
            }

        /**
         * Always ALLOW, regardless of [WalletCounterpartyTrustSummary.status] (including BLOCKED).
         * For the conformance profile only: a conformance run deliberately trusts arbitrary suite
         * issuer/verifier endpoints so the headless runner can drive OIDF conformance non-interactively.
         * Production profiles never use this; they use [warn].
         */
        val allow: WalletTrustPolicy =
            object : WalletTrustPolicy {
                override suspend fun evaluate(summary: WalletCounterpartyTrustSummary): WalletTrustPolicyDecision =
                    WalletTrustPolicyDecision(WalletTrustPolicyAction.ALLOW)
            }
    }
}

interface WalletSecurityGate {
    suspend fun authorize(request: WalletSecurityGateRequest): WalletSecurityGateResult

    companion object {
        /** Fail-closed default for incomplete application graphs and direct context construction. */
        val deny: WalletSecurityGate =
            object : WalletSecurityGate {
                override suspend fun authorize(request: WalletSecurityGateRequest): WalletSecurityGateResult =
                    WalletSecurityGateResult.Denied("wallet.interaction.security.gate_not_configured")
            }

        /** Explicit test/conformance helper; production composition must install a real gate. */
        val allow: WalletSecurityGate =
            object : WalletSecurityGate {
                override suspend fun authorize(request: WalletSecurityGateRequest): WalletSecurityGateResult =
                    WalletSecurityGateResult.Authorized(
                        WalletSecurityGrant(
                            grantId = request.operationId,
                            assurance = WalletSecurityAssurance.NONE,
                        ),
                    )
            }
    }
}

@Serializable
data class WalletCounterpartyTrustRequest(
    val counterparty: WalletCounterpartySummary,
    val protocol: WalletProtocol,
    val sources: List<WalletTrustSource> = emptyList(),
    val hints: Map<String, String> = emptyMap(),
)

@Serializable
data class WalletCounterpartyTrustSummary(
    val counterparty: WalletCounterpartySummary,
    val status: WalletTrustStatus,
    val policyAction: WalletTrustPolicyAction = WalletTrustPolicyAction.WARN,
    val sources: List<WalletTrustSource> = emptyList(),
    val diagnostics: List<String> = emptyList(),
    val firstContactKey: String? = null,
    val rememberedDecision: Boolean = false,
)

@Serializable
data class WalletTrustSource(
    val type: WalletTrustSourceType,
    val identifier: String,
    val labelKey: String? = null,
) {
    init {
        requireWalletInteractionLocalizationKey("labelKey", labelKey)
    }
}

@Serializable
enum class WalletTrustSourceType {
    EUDI_TRUSTED_LIST,
    X509,
    VICAL,
    DID,
    OPENID_FEDERATION,
    DCQL_TRUSTED_AUTHORITY,
    REMEMBERED_USER_DECISION,
}

@Serializable
enum class WalletTrustStatus {
    TRUSTED,
    UNKNOWN,
    WARNING,
    BLOCKED,
}

@Serializable
enum class WalletTrustPolicyMode {
    WARN,
    BLOCK,
    ASK_USER,
    FIRST_CONTACT_PROMPT,
    REMEMBERED_USER_DECISIONS,
}

@Serializable
data class WalletTrustPolicyDecision(
    val action: WalletTrustPolicyAction,
    val reasonCode: String? = null,
    val arguments: Map<String, String> = emptyMap(),
)

@Serializable
enum class WalletTrustPolicyAction {
    ALLOW,
    WARN,
    BLOCK,
    ASK_USER,
    FIRST_CONTACT_PROMPT,
}

@Serializable
data class WalletSecurityGateRequest(
    val operationId: String,
    val operation: WalletSecurityOperation,
    val audience: String? = null,
    val keyRef: String? = null,
    val walletUnitId: String? = null,
    val walletAccountId: String? = null,
    val activationDecisionId: String? = null,
    val operationType: String? = null,
    val operationHash: String? = null,
    val nonce: String? = null,
    val requiredAssurance: WalletSecurityAssurance = WalletSecurityAssurance.USER_PRESENT,
)

@Serializable
enum class WalletSecurityOperation {
    HOLDER_PROOF,
    CREDENTIAL_STORAGE,
    PRESENTATION_SHARING,
    LOCAL_HSM_UNLOCK,
    REMOTE_KEY_AUTHORIZATION,
}

object WalletSecurityContextAttributes {
    const val KEY_REF: String = "wallet.key.ref"
    const val WALLET_UNIT_ID: String = "wallet.unit.id"
    const val WALLET_ACCOUNT_ID: String = "wallet.account.id"
    const val ACTIVATION_DECISION_ID: String = "wallet.activation.decision.id"
    const val OPERATION_TYPE: String = "wallet.operation.type"
    const val OPERATION_HASH: String = "wallet.operation.hash"
    const val NONCE: String = "wallet.operation.nonce"
}

@Serializable
sealed class WalletSecurityGateResult {
    @Serializable
    @SerialName("authorized")
    data class Authorized(
        val grant: WalletSecurityGrant,
    ) : WalletSecurityGateResult()

    @Serializable
    @SerialName("challenge_required")
    data class ChallengeRequired(
        val challenge: WalletSecurityChallenge,
    ) : WalletSecurityGateResult()

    @Serializable
    @SerialName("denied")
    data class Denied(
        val reasonKey: String,
        val arguments: Map<String, String> = emptyMap(),
    ) : WalletSecurityGateResult() {
        init {
            requireWalletInteractionLocalizationKey("reasonKey", reasonKey)
        }
    }
}
