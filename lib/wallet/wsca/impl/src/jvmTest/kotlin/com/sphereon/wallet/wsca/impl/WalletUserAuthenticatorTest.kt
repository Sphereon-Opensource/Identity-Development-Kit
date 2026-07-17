/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.wsca.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.wallet.wsca.WscaBiometricEnrollmentRequest
import com.sphereon.wallet.wsca.WscaPinEnrollmentRequest
import com.sphereon.wallet.wsca.WscaPasskeyCredential
import com.sphereon.wallet.wsca.WscaPasskeyEnrollmentRequest
import com.sphereon.wallet.wsca.WscaUserAuthRequest
import com.sphereon.wallet.wsca.WscaUserAuthenticationFactor
import com.sphereon.wallet.wsca.WscaUserAuthenticationMethodCapability
import com.sphereon.wallet.wsca.WscaUserAuthenticationUnavailableReason
import com.sphereon.wallet.wsca.WscaUserAuthenticationStatus
import com.sphereon.wallet.wsca.WscaUserEnrollmentCapabilities
import com.sphereon.wallet.wsca.WscaUserEnrollmentCeremonyRef
import com.sphereon.wallet.wsca.WscaUserEnrollmentFailure
import com.sphereon.wallet.wsca.WscaUserEnrollmentFailureCode
import com.sphereon.wallet.wscd.ActivationProof
import com.sphereon.wallet.wscd.ActivationProofKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WalletUserAuthenticatorTest {
    @Test
    fun productionDefaultFailsClosedWithoutAPlatformAdapter() =
        runTest {
            val authenticator = UnavailableWalletUserAuthenticator()
            assertFalse(authenticator.enrollmentCapabilities.value.pin.available)
            assertTrue(authenticator.enrollPin(pinRequest()).isErr)
            val result = authenticator.authenticate(authRequest())
            assertTrue(result.isErr)
            assertEquals("WALLET_USER_AUTH_PLATFORM_UNAVAILABLE", result.error.code)
        }

    @Test
    fun localPolicyEnrollsAndVerifiesPinWithoutPersistingRawPin() =
        runTest {
            val platform = RecordingPlatform(newPin = "123456", authenticationPins = ArrayDeque(listOf("123456")))
            val store = RecordingPolicyStore()
            val authenticator = LocalPolicyWalletUserAuthenticator(platform, store, iterations = 1_000)

            val enrolled = authenticator.enrollPin(pinRequest())
            assertTrue(enrolled.isOk)
            assertTrue(store.value != null)
            assertFalse(store.value!!.decodeToString().contains("123456"))

            val authenticated = authenticator.authenticate(authRequest())
            assertTrue(authenticated.isOk)
            assertEquals(ActivationProofKind.LOCAL_USER_AUTH, authenticated.value.kind)
            assertEquals("sha256:operation", authenticated.value.digestBinding)
            assertEquals("pin", authenticated.value.evidence["factor"])
        }

    @Test
    fun passkeyIsPersistedAsTheExclusivePrimaryFactorAndItsCounterAdvances() =
        runTest {
            val platform = RecordingPasskeyPlatform()
            val store = RecordingPolicyStore()
            val authenticator = LocalPolicyWalletUserAuthenticator(platform, store, iterations = 1_000)

            val enrolled = authenticator.enrollPasskey(passkeyRequest())

            assertTrue(enrolled.isOk)
            assertEquals(WscaUserAuthenticationFactor.PASSKEY, enrolled.value.primaryFactor)
            assertFalse(enrolled.value.biometricEnabled)
            assertEquals(WscaUserAuthenticationFactor.PASSKEY, authenticator.primaryFactor("wu-personal").value)
            val authenticated = authenticator.authenticate(authRequest())

            assertTrue(authenticated.isOk)
            assertEquals("passkey", authenticated.value.evidence["factor"])
            assertEquals("test:wallet-operation", authenticated.value.evidence["operation_binding"])
            assertEquals(1, platform.authenticationCalls)
            assertEquals(listOf("test:wallet-operation"), platform.operationBindings)
            assertTrue(store.value!!.decodeToString().contains("\"signatureCounter\":8"))
            assertEquals(WscaUserEnrollmentFailureCode.ENROLLMENT_CONFLICT, authenticator.enrollPin(pinRequest()).error.code)
        }

    @Test
    fun biometricIsOptionalOverPinAndUsedFirst() =
        runTest {
            val platform = RecordingPlatform(newPin = "123456", authenticationPins = ArrayDeque())
            val store = RecordingPolicyStore()
            val authenticator = LocalPolicyWalletUserAuthenticator(platform, store, iterations = 1_000)
            assertTrue(authenticator.enrollPin(pinRequest()).isOk)
            assertTrue(authenticator.enableBiometrics(biometricRequest()).isOk)

            val authenticated = authenticator.authenticate(authRequest())
            assertTrue(authenticated.isOk)
            assertEquals("biometric", authenticated.value.evidence["factor"])
            assertEquals(1, platform.biometricAuthenticationCalls)
            assertEquals(0, platform.pinAuthenticationCalls)
        }

    @Test
    fun wrongPinLocksAfterFiveAttempts() =
        runTest {
            val platform = RecordingPlatform(newPin = "123456", authenticationPins = ArrayDeque(List(5) { "000000" }))
            val authenticator = LocalPolicyWalletUserAuthenticator(platform, RecordingPolicyStore(), iterations = 100)
            assertTrue(authenticator.enrollPin(pinRequest()).isOk)
            repeat(5) { assertTrue(authenticator.authenticate(authRequest()).isErr) }
            val locked = authenticator.authenticate(authRequest())
            assertTrue(locked.isErr)
            assertEquals("WALLET_USER_AUTH_LOCKED", locked.error.code)
        }

    @Test
    fun cancellingActiveEnrollmentReleasesCaptureAndLeavesNoPolicy() =
        runTest {
            val platform = BlockingEnrollmentPlatform()
            val store = RecordingPolicyStore()
            val authenticator = LocalPolicyWalletUserAuthenticator(platform, store, iterations = 100)
            val enrollment = async { authenticator.enrollPin(pinRequest()) }
            runCurrent()

            authenticator.cancelActiveCapture("wu-personal")
            authenticator.rollbackEnrollment("wu-personal")
            runCurrent()

            assertEquals(WscaUserEnrollmentFailureCode.CANCELLED, enrollment.await().error.code)
            assertTrue(platform.cancelled)
            assertNull(store.value)
        }

    @Test
    fun localWscaUserAuthenticationTracksAuthenticatedAndFailedStates() =
        runTest {
            val success =
                LocalWscaUserAuthentication(
                    WalletUserAuthenticator { request ->
                        Ok(
                            ActivationProof(
                                ActivationProofKind.LOCAL_USER_AUTH,
                                "test-token",
                                request.digestBinding,
                                request.nonce,
                                mapOf("factor" to "pin"),
                            ),
                        )
                    },
                )
            assertEquals(WscaUserAuthenticationStatus.IDLE, success.state.value.status)
            assertTrue(success.authenticate(authRequest()).isOk)
            assertEquals(WscaUserAuthenticationStatus.AUTHENTICATED, success.state.value.status)

            val failure = IdkError.fromString(code = "test.denied", message = "denied")
            val failed = LocalWscaUserAuthentication(WalletUserAuthenticator { Err(failure) })
            assertTrue(failed.authenticate(authRequest()).isErr)
            assertEquals(WscaUserAuthenticationStatus.FAILED, failed.state.value.status)
            assertEquals("test.denied", failed.state.value.lastError?.code)
            assertNull(failed.state.value.lastActivationKind)
        }

    private fun pinRequest() =
        WscaPinEnrollmentRequest("wu-personal", WscaUserEnrollmentCeremonyRef("ceremony-pin"))

    private fun passkeyRequest() =
        WscaPasskeyEnrollmentRequest("wu-personal", WscaUserEnrollmentCeremonyRef("ceremony-passkey"), "Personal wallet")

    private fun biometricRequest() =
        WscaBiometricEnrollmentRequest("wu-personal", WscaUserEnrollmentCeremonyRef("ceremony-biometric"))

    private fun authRequest() =
        WscaUserAuthRequest(
            walletUnitId = "wu-personal",
            operationType = "wallet.test",
            operationBinding = "test:wallet-operation",
            digestBinding = "sha256:operation",
            nonce = "nonce",
        )
}

private class RecordingPasskeyPlatform : WalletUserAuthenticationPlatform {
    private val credential = WscaPasskeyCredential(
        credentialId = "credential-1",
        publicKeyBase64Url = "cHVibGljLWtleQ",
        signatureCounter = 7,
        transports = listOf("internal"),
        backedUp = true,
    )
    override val capabilities: StateFlow<WscaUserEnrollmentCapabilities> =
        MutableStateFlow(
            WscaUserEnrollmentCapabilities(
                passkey = WscaUserAuthenticationMethodCapability(WscaUserAuthenticationFactor.PASSKEY, true),
                pin = WscaUserAuthenticationMethodCapability(WscaUserAuthenticationFactor.PIN, true),
                biometric = WscaUserAuthenticationMethodCapability(
                    WscaUserAuthenticationFactor.BIOMETRIC,
                    false,
                    WscaUserAuthenticationUnavailableReason.UNSUPPORTED_PLATFORM,
                ),
            ),
        )
    var authenticationCalls = 0
    val operationBindings = mutableListOf<String>()

    override suspend fun enrollPasskey(request: WscaPasskeyEnrollmentRequest) = Ok(credential)

    override suspend fun authenticateWithPasskey(
        request: WscaUserAuthRequest,
        credential: WscaPasskeyCredential,
    ): IdkResult<WscaPasskeyCredential, WscaUserEnrollmentFailure> {
        authenticationCalls++
        operationBindings += request.operationBinding
        return Ok(credential.copy(signatureCounter = credential.signatureCounter + 1))
    }

    override suspend fun captureNewPin(request: WscaPinEnrollmentRequest) = Err(cancelledFailure())
    override suspend fun capturePin(walletUnitId: String) = Err(cancelledFailure())
    override suspend fun enrollBiometrics(request: WscaBiometricEnrollmentRequest): IdkResult<Unit, WscaUserEnrollmentFailure> = Err(cancelledFailure())
    override suspend fun authenticateBiometrically(request: WscaUserAuthRequest): IdkResult<Unit, WscaUserEnrollmentFailure> = Err(cancelledFailure())
    override fun cancelActiveCapture() = Unit
}

private class BlockingEnrollmentPlatform : WalletUserAuthenticationPlatform {
    private val capture = CompletableDeferred<IdkResult<CharArray, WscaUserEnrollmentFailure>>()
    var cancelled = false
    override val capabilities =
        MutableStateFlow(
            WscaUserEnrollmentCapabilities(
                passkey = unavailablePasskeyCapability(),
                pin = WscaUserAuthenticationMethodCapability(WscaUserAuthenticationFactor.PIN, true),
                biometric = WscaUserAuthenticationMethodCapability(WscaUserAuthenticationFactor.BIOMETRIC, true),
            ),
        )

    override suspend fun captureNewPin(request: WscaPinEnrollmentRequest) = capture.await()
    override suspend fun capturePin(walletUnitId: String) = Err(cancelledFailure())
    override suspend fun enrollBiometrics(request: WscaBiometricEnrollmentRequest): IdkResult<Unit, WscaUserEnrollmentFailure> = Err(cancelledFailure())
    override suspend fun authenticateBiometrically(request: WscaUserAuthRequest): IdkResult<Unit, WscaUserEnrollmentFailure> = Err(cancelledFailure())
    override fun cancelActiveCapture() {
        cancelled = true
        capture.complete(Err(cancelledFailure()))
    }
}

private fun cancelledFailure() =
    WscaUserEnrollmentFailure(WscaUserEnrollmentFailureCode.CANCELLED, "wallet.user_enrollment.cancelled", true)

private class RecordingPolicyStore : WalletUserAuthenticationPolicyStore {
    override val durable: Boolean = true
    var value: ByteArray? = null

    override suspend fun put(walletUnitId: String, encodedPolicy: ByteArray): IdkResult<Unit, IdkError> {
        value = encodedPolicy.copyOf()
        return Ok(Unit)
    }

    override suspend fun get(walletUnitId: String): IdkResult<ByteArray?, IdkError> = Ok(value?.copyOf())

    override suspend fun remove(walletUnitId: String): IdkResult<Unit, IdkError> {
        value?.fill(0)
        value = null
        return Ok(Unit)
    }
}

private class RecordingPlatform(
    private val newPin: String,
    private val authenticationPins: ArrayDeque<String>,
) : WalletUserAuthenticationPlatform {
    override val capabilities: StateFlow<WscaUserEnrollmentCapabilities> =
        MutableStateFlow(
            WscaUserEnrollmentCapabilities(
                passkey = unavailablePasskeyCapability(),
                pin = WscaUserAuthenticationMethodCapability(WscaUserAuthenticationFactor.PIN, true),
                biometric = WscaUserAuthenticationMethodCapability(WscaUserAuthenticationFactor.BIOMETRIC, true),
            ),
        )
    var biometricAuthenticationCalls: Int = 0
    var pinAuthenticationCalls: Int = 0

    override suspend fun captureNewPin(request: WscaPinEnrollmentRequest): IdkResult<CharArray, WscaUserEnrollmentFailure> =
        Ok(newPin.toCharArray())

    override suspend fun capturePin(walletUnitId: String): IdkResult<CharArray, WscaUserEnrollmentFailure> {
        pinAuthenticationCalls++
        val pin = authenticationPins.removeFirstOrNull()
            ?: return Err(WscaUserEnrollmentFailure(WscaUserEnrollmentFailureCode.CANCELLED, "wallet.user_enrollment.cancelled", false))
        return Ok(pin.toCharArray())
    }

    override suspend fun enrollBiometrics(request: WscaBiometricEnrollmentRequest): IdkResult<Unit, WscaUserEnrollmentFailure> = Ok(Unit)

    override suspend fun authenticateBiometrically(request: WscaUserAuthRequest): IdkResult<Unit, WscaUserEnrollmentFailure> {
        biometricAuthenticationCalls++
        return Ok(Unit)
    }

    override fun cancelActiveCapture() = Unit
}

private fun unavailablePasskeyCapability() =
    WscaUserAuthenticationMethodCapability(
        WscaUserAuthenticationFactor.PASSKEY,
        false,
        WscaUserAuthenticationUnavailableReason.UNSUPPORTED_PLATFORM,
    )
