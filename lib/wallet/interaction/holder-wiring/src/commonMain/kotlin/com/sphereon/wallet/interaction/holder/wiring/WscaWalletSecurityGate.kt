/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.wallet.interaction.holder.wiring

import com.sphereon.wallet.interaction.WalletSecurityAssurance
import com.sphereon.wallet.interaction.WalletSecurityGate
import com.sphereon.wallet.interaction.WalletSecurityGateRequest
import com.sphereon.wallet.interaction.WalletSecurityGateResult
import com.sphereon.wallet.interaction.WalletSecurityChallenge
import com.sphereon.wallet.interaction.WalletSecurityChallengeKind
import com.sphereon.wallet.wsca.Wsca
import com.sphereon.wallet.wscd.WscdProfile
import com.sphereon.wallet.wsca.WscaUserAuthenticationFactor

/**
 * Production local interaction gate. It declares the exact attended challenge required by WSCA;
 * the presenter/platform ceremony captures the factor and returns a one-use operation-bound grant.
 * This gate never captures secrets and never starts a second, hidden authentication ceremony.
 */
class WscaWalletSecurityGate(
    private val wsca: Wsca,
) : WalletSecurityGate {
    override suspend fun authorize(request: WalletSecurityGateRequest): WalletSecurityGateResult {
        val walletUnitId =
            request.walletUnitId
                ?: return denied("wallet.interaction.security.wallet_unit_missing")
        val operationBinding = request.operationHash ?: "operation:${request.operationId}"
        val primaryFactor = wsca.userAuthentication.primaryFactor(walletUnitId).getOrElse {
            return denied("wallet.interaction.security.enrollment_unavailable")
        } ?: return denied("wallet.interaction.security.enrollment_required")
        val kind =
            when (request.requiredAssurance) {
                WalletSecurityAssurance.NONE,
                WalletSecurityAssurance.USER_PRESENT,
                WalletSecurityAssurance.PASSKEY,
                WalletSecurityAssurance.PIN,
                WalletSecurityAssurance.BIOMETRIC,
                -> when (primaryFactor) {
                    WscaUserAuthenticationFactor.PASSKEY -> WalletSecurityChallengeKind.PASSKEY
                    WscaUserAuthenticationFactor.PIN ->
                        if (request.requiredAssurance == WalletSecurityAssurance.BIOMETRIC && wsca.userAuthentication.enrollmentCapabilities.value.biometric.available) {
                            WalletSecurityChallengeKind.BIOMETRIC
                        } else {
                            WalletSecurityChallengeKind.PIN
                        }
                    WscaUserAuthenticationFactor.BIOMETRIC -> return denied("wallet.interaction.security.primary_factor_invalid")
                }
                WalletSecurityAssurance.HARDWARE_BACKED ->
                    if (wsca.wscdProfile == WscdProfile.LocalNative) WalletSecurityChallengeKind.LOCAL_TEE_UNLOCK
                    else return denied("wallet.interaction.security.hardware_assurance_unavailable")
                WalletSecurityAssurance.REMOTE_AUTHORIZED -> return denied("wallet.interaction.security.remote_authorization_required")
            }
        val arguments =
            buildMap {
                put("operation", request.operation.name)
                put("operation_binding", operationBinding)
                put("wallet_unit_id", walletUnitId)
                request.audience?.let { put("audience", it) }
            }
        return WalletSecurityGateResult.ChallengeRequired(
            WalletSecurityChallenge(
                challengeId = request.operationId,
                kind = kind,
                reasonKey = "wallet.interaction.security.authorization_required",
                arguments = arguments,
                requiredAssurance = request.requiredAssurance,
            ),
        )
    }

    private fun denied(reasonKey: String): WalletSecurityGateResult = WalletSecurityGateResult.Denied(reasonKey)

}
