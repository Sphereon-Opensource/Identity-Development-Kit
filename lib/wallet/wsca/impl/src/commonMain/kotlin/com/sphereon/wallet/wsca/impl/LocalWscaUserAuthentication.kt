/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wsca.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.wallet.wsca.WscaUserAuthRequest
import com.sphereon.wallet.wsca.WscaUserAuthState
import com.sphereon.wallet.wsca.WscaUserAuthentication
import com.sphereon.wallet.wsca.WscaUserAuthenticationStatus
import com.sphereon.wallet.wsca.WscaBiometricEnrollmentRequest
import com.sphereon.wallet.wsca.WscaPinEnrollmentRequest
import com.sphereon.wallet.wsca.WscaPasskeyEnrollmentRequest
import com.sphereon.wallet.wsca.WscaUserEnrollmentCapabilities
import com.sphereon.wallet.wsca.WscaUserEnrollmentFailure
import com.sphereon.wallet.wsca.WscaUserEnrollmentResult
import com.sphereon.wallet.wscd.ActivationProof
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [LocalWsca]'s [WscaUserAuthentication]: tracks ceremony state around calls into the injected
 * [WalletUserAuthenticator] seam. Constructed once per [LocalWsca] instance (itself session-scoped)
 * so [state] is meaningfully shared across every [LocalWsca] call that gates on this ceremony.
 */
class LocalWscaUserAuthentication(
    private val authenticator: WalletUserAuthenticator,
) : WscaUserAuthentication {
    private val stateFlow = MutableStateFlow(WscaUserAuthState(WscaUserAuthenticationStatus.IDLE))
    override val state: StateFlow<WscaUserAuthState> = stateFlow.asStateFlow()
    override val enrollmentCapabilities: StateFlow<WscaUserEnrollmentCapabilities> = authenticator.enrollmentCapabilities

    override suspend fun primaryFactor(walletUnitId: String) = authenticator.primaryFactor(walletUnitId)

    override suspend fun enrollPasskey(request: WscaPasskeyEnrollmentRequest): IdkResult<WscaUserEnrollmentResult, WscaUserEnrollmentFailure> =
        authenticator.enrollPasskey(request)

    override suspend fun enrollPin(request: WscaPinEnrollmentRequest): IdkResult<WscaUserEnrollmentResult, WscaUserEnrollmentFailure> =
        authenticator.enrollPin(request)

    override suspend fun enableBiometrics(request: WscaBiometricEnrollmentRequest): IdkResult<WscaUserEnrollmentResult, WscaUserEnrollmentFailure> =
        authenticator.enableBiometrics(request)

    override suspend fun rollbackEnrollment(walletUnitId: String): IdkResult<Unit, WscaUserEnrollmentFailure> =
        authenticator.rollbackEnrollment(walletUnitId)

    override suspend fun authenticate(request: WscaUserAuthRequest): IdkResult<ActivationProof, IdkError> {
        stateFlow.value = WscaUserAuthState(WscaUserAuthenticationStatus.AUTHENTICATING)
        val result = authenticator.authenticate(request)
        stateFlow.value =
            if (result.isOk) {
                WscaUserAuthState(WscaUserAuthenticationStatus.AUTHENTICATED, lastActivationKind = result.value.kind)
            } else {
                WscaUserAuthState(WscaUserAuthenticationStatus.FAILED, lastError = result.error)
            }
        return result
    }
}
