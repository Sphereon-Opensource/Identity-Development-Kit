/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.wallet.wsca.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.Uuid
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.wsca.WscaBiometricEnrollmentRequest
import com.sphereon.wallet.wsca.WscaPasskeyCredential
import com.sphereon.wallet.wsca.WscaPasskeyEnrollmentRequest
import com.sphereon.wallet.wsca.WscaPinEnrollmentRequest
import com.sphereon.wallet.wsca.WscaUserAuthRequest
import com.sphereon.wallet.wsca.WscaUserAuthenticationFactor
import com.sphereon.wallet.wsca.WscaUserAuthenticationMethodCapability
import com.sphereon.wallet.wsca.WscaUserAuthenticationUnavailableReason
import com.sphereon.wallet.wsca.WscaUserEnrollmentCapabilities
import com.sphereon.wallet.wsca.WscaUserEnrollmentFailure
import com.sphereon.wallet.wsca.WscaUserEnrollmentFailureCode
import com.sphereon.wallet.wsca.WscaUserEnrollmentResult
import com.sphereon.wallet.wscd.ActivationProof
import com.sphereon.wallet.wscd.ActivationProofKind
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Platform-owned attended input boundary. Implementations may use Android BiometricPrompt/device
 * credential, iOS LocalAuthentication/Keychain, or an equally protected native host callback.
 * PIN arrays never leave this WSCA implementation and are wiped immediately after use.
 */
interface WalletUserAuthenticationPlatform {
    val capabilities: StateFlow<WscaUserEnrollmentCapabilities>

    /** Registers and verifies a WebAuthn credential without exposing authenticator private material. */
    suspend fun enrollPasskey(request: WscaPasskeyEnrollmentRequest): IdkResult<WscaPasskeyCredential, WscaUserEnrollmentFailure> =
        Err(failure(WscaUserEnrollmentFailureCode.PASSKEY_UNAVAILABLE, false))

    /** Verifies one WebAuthn assertion and returns the credential's advanced signature counter. */
    suspend fun authenticateWithPasskey(
        request: WscaUserAuthRequest,
        credential: WscaPasskeyCredential,
    ): IdkResult<WscaPasskeyCredential, WscaUserEnrollmentFailure> =
        Err(failure(WscaUserEnrollmentFailureCode.PASSKEY_UNAVAILABLE, false))

    /** Captures and confirms a new PIN inside platform-owned UI. */
    suspend fun captureNewPin(request: WscaPinEnrollmentRequest): IdkResult<CharArray, WscaUserEnrollmentFailure>

    /** Captures the existing PIN inside platform-owned UI. */
    suspend fun capturePin(walletUnitId: String): IdkResult<CharArray, WscaUserEnrollmentFailure>

    /** Enrolls device biometrics as a convenience over the existing PIN. */
    suspend fun enrollBiometrics(request: WscaBiometricEnrollmentRequest): IdkResult<Unit, WscaUserEnrollmentFailure>

    /** Performs the platform biometric ceremony. */
    suspend fun authenticateBiometrically(request: WscaUserAuthRequest): IdkResult<Unit, WscaUserEnrollmentFailure>

    fun cancelActiveCapture()
}

/** Durable, platform-protected storage for the derived verifier and lockout policy. */
interface WalletUserAuthenticationPolicyStore {
    val durable: Boolean

    suspend fun put(walletUnitId: String, encodedPolicy: ByteArray): IdkResult<Unit, IdkError>

    suspend fun get(walletUnitId: String): IdkResult<ByteArray?, IdkError>

    suspend fun remove(walletUnitId: String): IdkResult<Unit, IdkError>
}

/** Internal WSCA seam used by [LocalWscaUserAuthentication]. */
fun interface WalletUserAuthenticator {
    val enrollmentCapabilities: StateFlow<WscaUserEnrollmentCapabilities>
        get() = unavailableEnrollmentCapabilities

    suspend fun primaryFactor(walletUnitId: String): IdkResult<WscaUserAuthenticationFactor?, IdkError> = Ok(null)

    suspend fun enrollPasskey(request: WscaPasskeyEnrollmentRequest): IdkResult<WscaUserEnrollmentResult, WscaUserEnrollmentFailure> =
        Err(platformUnavailable())

    suspend fun enrollPin(request: WscaPinEnrollmentRequest): IdkResult<WscaUserEnrollmentResult, WscaUserEnrollmentFailure> =
        Err(platformUnavailable())

    suspend fun enableBiometrics(request: WscaBiometricEnrollmentRequest): IdkResult<WscaUserEnrollmentResult, WscaUserEnrollmentFailure> =
        Err(platformUnavailable())

    suspend fun rollbackEnrollment(walletUnitId: String): IdkResult<Unit, WscaUserEnrollmentFailure> = Ok(Unit)

    fun cancelActiveCapture(walletUnitId: String) = Unit

    suspend fun authenticate(request: WscaUserAuthRequest): IdkResult<ActivationProof, IdkError>
}

/**
 * Real local PIN policy: PBKDF2-HMAC-SHA256 verifier, durable protected storage, lockout, and
 * biometric-first authentication with PIN fallback. Hosts provide only secure capture/storage.
 */
class LocalPolicyWalletUserAuthenticator(
    private val platform: WalletUserAuthenticationPlatform,
    private val store: WalletUserAuthenticationPolicyStore,
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val pinKeyDerivation: WalletPinKeyDerivation = PlatformWalletPinKeyDerivation,
) : WalletUserAuthenticator {
    private val enrollmentMutation = Mutex()
    private val cancelledEnrollments = mutableSetOf<String>()
    override val enrollmentCapabilities: StateFlow<WscaUserEnrollmentCapabilities> = platform.capabilities

    override suspend fun primaryFactor(walletUnitId: String): IdkResult<WscaUserAuthenticationFactor?, IdkError> =
        load(walletUnitId).mapError { it.toIdkError() }.map { it?.primaryFactor }

    override suspend fun enrollPasskey(request: WscaPasskeyEnrollmentRequest): IdkResult<WscaUserEnrollmentResult, WscaUserEnrollmentFailure> {
        if (!store.durable) return Err(failure(WscaUserEnrollmentFailureCode.SECURE_STORAGE_UNAVAILABLE, false))
        if (!platform.capabilities.value.passkey.available) return Err(failure(WscaUserEnrollmentFailureCode.PASSKEY_UNAVAILABLE, false))
        enrollmentMutation.withLock { cancelledEnrollments.remove(request.walletUnitId) }
        if (load(request.walletUnitId).getOrElse { return Err(it) } != null) {
            return Err(failure(WscaUserEnrollmentFailureCode.ENROLLMENT_CONFLICT, false))
        }
        val credential = platform.enrollPasskey(request).getOrElse { return Err(it) }
        val policy =
            StoredLocalUserAuthenticationPolicy(
                primaryFactor = WscaUserAuthenticationFactor.PASSKEY,
                passkey = credential,
            )
        enrollmentMutation.withLock {
            if (cancelledEnrollments.remove(request.walletUnitId)) {
                return Err(failure(WscaUserEnrollmentFailureCode.CANCELLED, true))
            }
            save(request.walletUnitId, policy).getOrElse { return Err(it) }
        }
        return Ok(policy.toResult(request.walletUnitId))
    }

    override suspend fun enrollPin(request: WscaPinEnrollmentRequest): IdkResult<WscaUserEnrollmentResult, WscaUserEnrollmentFailure> {
        if (!store.durable) return Err(failure(WscaUserEnrollmentFailureCode.SECURE_STORAGE_UNAVAILABLE, false))
        if (!platform.capabilities.value.pin.available) return Err(failure(WscaUserEnrollmentFailureCode.PLATFORM_UNAVAILABLE, false))
        enrollmentMutation.withLock { cancelledEnrollments.remove(request.walletUnitId) }
        val existing = load(request.walletUnitId).getOrElse { return Err(it) }
        if (existing != null) return Err(failure(WscaUserEnrollmentFailureCode.ENROLLMENT_CONFLICT, false))
        val pin = platform.captureNewPin(request).getOrElse { return Err(it) }
        try {
            if (pin.size != request.requiredLength || pin.any { !it.isDigit() }) {
                return Err(failure(WscaUserEnrollmentFailureCode.PIN_POLICY_REJECTED, true))
            }
            val salt = CryptographyRandom.nextBytes(SALT_BYTES)
            val verifier = pinKeyDerivation.derive(pin, salt, iterations, DERIVED_KEY_BYTES)
            val policy =
                StoredLocalUserAuthenticationPolicy(
                    primaryFactor = WscaUserAuthenticationFactor.PIN,
                    pinSalt = salt.encodeToBase64(),
                    pinVerifier = verifier.encodeToBase64(),
                    pinIterations = iterations,
                )
            enrollmentMutation.withLock {
                if (cancelledEnrollments.remove(request.walletUnitId)) {
                    return Err(failure(WscaUserEnrollmentFailureCode.CANCELLED, true))
                }
                save(request.walletUnitId, policy).getOrElse { return Err(it) }
            }
            return Ok(policy.toResult(request.walletUnitId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return Err(failure(WscaUserEnrollmentFailureCode.INTERNAL, false))
        } finally {
            pin.fill('\u0000')
        }
    }

    override suspend fun enableBiometrics(request: WscaBiometricEnrollmentRequest): IdkResult<WscaUserEnrollmentResult, WscaUserEnrollmentFailure> {
        val policy = load(request.walletUnitId).getOrElse { return Err(it) }
            ?: return Err(failure(WscaUserEnrollmentFailureCode.PIN_NOT_ENROLLED, false))
        if (policy.primaryFactor != WscaUserAuthenticationFactor.PIN) {
            return Err(failure(WscaUserEnrollmentFailureCode.PIN_NOT_ENROLLED, false))
        }
        if (!platform.capabilities.value.biometric.available) {
            return Err(failure(WscaUserEnrollmentFailureCode.BIOMETRIC_UNAVAILABLE, false))
        }
        platform.enrollBiometrics(request).getOrElse { return Err(it) }
        val updated = policy.copy(biometricEnabled = true)
        save(request.walletUnitId, updated).getOrElse { return Err(it) }
        return Ok(updated.toResult(request.walletUnitId))
    }

    override suspend fun rollbackEnrollment(walletUnitId: String): IdkResult<Unit, WscaUserEnrollmentFailure> =
        enrollmentMutation.withLock {
            cancelledEnrollments.add(walletUnitId)
            store.remove(walletUnitId).mapError { failure(WscaUserEnrollmentFailureCode.SECURE_STORAGE_UNAVAILABLE, false) }
        }

    override fun cancelActiveCapture(walletUnitId: String) {
        platform.cancelActiveCapture()
    }

    override suspend fun authenticate(request: WscaUserAuthRequest): IdkResult<ActivationProof, IdkError> {
        val policy = load(request.walletUnitId).getOrElse { return Err(it.toIdkError()) }
            ?: return Err(authError("WALLET_USER_AUTH_ENROLLMENT_REQUIRED", "wallet.user_auth.enrollment_required"))
        val now = Clock.System.now().epochSeconds
        if (policy.lockedUntilEpochSeconds?.let { it > now } == true) {
            return Err(authError("WALLET_USER_AUTH_LOCKED", "wallet.user_auth.locked"))
        }
        if (policy.primaryFactor == WscaUserAuthenticationFactor.PASSKEY) {
            val credential = policy.passkey
                ?: return Err(authError("WALLET_USER_AUTH_PASSKEY_REQUIRED", "wallet.user_auth.passkey_required"))
            val verified = platform.authenticateWithPasskey(request, credential).getOrElse { return Err(it.toIdkError()) }
            save(request.walletUnitId, policy.copy(passkey = verified)).getOrElse { return Err(it.toIdkError()) }
            return Ok(proof(request, "passkey"))
        }
        if (policy.biometricEnabled && platform.capabilities.value.biometric.available) {
            val biometric = platform.authenticateBiometrically(request)
            if (biometric.isOk) {
                resetFailures(request.walletUnitId, policy)
                return Ok(proof(request, "biometric"))
            }
            if (biometric.error.code == WscaUserEnrollmentFailureCode.CANCELLED) {
                return Err(biometric.error.toIdkError())
            }
            // Biometric failure/unavailability intentionally falls through to the PIN recovery factor.
        }
        val pin = platform.capturePin(request.walletUnitId).getOrElse { return Err(it.toIdkError()) }
        try {
            val matches = verify(pin, policy)
            if (!matches) {
                recordFailure(request.walletUnitId, policy)
                return Err(authError("WALLET_USER_AUTH_PIN_INVALID", "wallet.user_auth.pin_invalid"))
            }
            resetFailures(request.walletUnitId, policy)
            return Ok(proof(request, "pin"))
        } finally {
            pin.fill('\u0000')
        }
    }

    private fun proof(request: WscaUserAuthRequest, factor: String): ActivationProof =
        ActivationProof(
            kind = ActivationProofKind.LOCAL_USER_AUTH,
            token = "local-user-auth:${Uuid.v4String()}",
            digestBinding = request.digestBinding,
            nonce = request.nonce,
            evidence =
                mapOf(
                    "factor" to factor,
                    "operation_binding" to request.operationBinding,
                ),
        )

    private suspend fun verify(pin: CharArray, policy: StoredLocalUserAuthenticationPolicy): Boolean {
        val salt = requireNotNull(policy.pinSalt) { "wallet_user_authentication_pin_salt_missing" }
        val verifier = requireNotNull(policy.pinVerifier) { "wallet_user_authentication_pin_verifier_missing" }
        val iterations = requireNotNull(policy.pinIterations) { "wallet_user_authentication_pin_iterations_missing" }
        val actual = pinKeyDerivation.derive(pin, salt.decodeFromBase64(), iterations, DERIVED_KEY_BYTES)
        val expected = verifier.decodeFromBase64()
        var diff = actual.size xor expected.size
        for (index in 0 until minOf(actual.size, expected.size)) diff = diff or (actual[index].toInt() xor expected[index].toInt())
        actual.fill(0)
        expected.fill(0)
        return diff == 0
    }

    private suspend fun recordFailure(walletUnitId: String, policy: StoredLocalUserAuthenticationPolicy) {
        val attempts = policy.failedAttempts + 1
        val lockedUntil = if (attempts >= MAX_ATTEMPTS) Clock.System.now().epochSeconds + LOCKOUT_SECONDS else null
        save(walletUnitId, policy.copy(failedAttempts = attempts, lockedUntilEpochSeconds = lockedUntil))
    }

    private suspend fun resetFailures(walletUnitId: String, policy: StoredLocalUserAuthenticationPolicy) {
        if (policy.failedAttempts != 0 || policy.lockedUntilEpochSeconds != null) {
            save(walletUnitId, policy.copy(failedAttempts = 0, lockedUntilEpochSeconds = null))
        }
    }

    private suspend fun load(walletUnitId: String): IdkResult<StoredLocalUserAuthenticationPolicy?, WscaUserEnrollmentFailure> =
        store.get(walletUnitId).mapError { failure(WscaUserEnrollmentFailureCode.SECURE_STORAGE_UNAVAILABLE, false) }.map { encoded ->
            encoded?.let { runCatching { json.decodeFromString<StoredLocalUserAuthenticationPolicy>(it.decodeToString()) }.getOrNull() }
        }

    private suspend fun save(walletUnitId: String, policy: StoredLocalUserAuthenticationPolicy): IdkResult<Unit, WscaUserEnrollmentFailure> =
        store.put(walletUnitId, json.encodeToString(StoredLocalUserAuthenticationPolicy.serializer(), policy).encodeToByteArray())
            .mapError { failure(WscaUserEnrollmentFailureCode.SECURE_STORAGE_UNAVAILABLE, false) }

    private companion object {
        const val DEFAULT_ITERATIONS: Int = 600_000
        const val SALT_BYTES: Int = 16
        const val DERIVED_KEY_BYTES: Int = 32
        const val MAX_ATTEMPTS: Int = 5
        const val LOCKOUT_SECONDS: Long = 30
        val json = Json { encodeDefaults = true }
    }
}

/**
 * WSCA-owned password-key-derivation seam. The default implementation delegates to the
 * multiplatform cryptography provider, which selects JCA, CryptoKit, WebCrypto, or the native
 * provider for the active target. This keeps one policy and work factor without running hundreds
 * of thousands of HMAC rounds in Kotlin code on the event loop.
 */
fun interface WalletPinKeyDerivation {
    suspend fun derive(
        pin: CharArray,
        salt: ByteArray,
        iterations: Int,
        length: Int,
    ): ByteArray
}

object PlatformWalletPinKeyDerivation : WalletPinKeyDerivation {
    private val pbkdf2 = CryptographyProvider.Default.get(PBKDF2)

    override suspend fun derive(
        pin: CharArray,
        salt: ByteArray,
        iterations: Int,
        length: Int,
    ): ByteArray {
        require(iterations > 0) { "wallet_pin_kdf_iterations_invalid" }
        require(length > 0) { "wallet_pin_kdf_length_invalid" }
        val password = ByteArray(pin.size) { index -> pin[index].code.toByte() }
        return try {
            pbkdf2.secretDerivation(
                digest = SHA256,
                iterations = iterations,
                outputSize = length.bytes,
                salt = salt,
            ).deriveSecretToByteArray(password)
        } finally {
            password.fill(0)
        }
    }
}

/** No adapter means no ceremony and therefore no authorization. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletUserAuthenticator>())
class UnavailableWalletUserAuthenticator : WalletUserAuthenticator {
    override val enrollmentCapabilities: StateFlow<WscaUserEnrollmentCapabilities> = unavailableEnrollmentCapabilities

    override suspend fun primaryFactor(walletUnitId: String): IdkResult<WscaUserAuthenticationFactor?, IdkError> =
        Err(authError("WALLET_USER_AUTH_PLATFORM_UNAVAILABLE", "wallet.user_auth.platform_unavailable"))

    override suspend fun enrollPasskey(request: WscaPasskeyEnrollmentRequest) = Err(platformUnavailable())

    override suspend fun enrollPin(request: WscaPinEnrollmentRequest) = Err(platformUnavailable())

    override suspend fun enableBiometrics(request: WscaBiometricEnrollmentRequest) = Err(platformUnavailable())

    override suspend fun rollbackEnrollment(walletUnitId: String): IdkResult<Unit, WscaUserEnrollmentFailure> = Ok(Unit)

    override fun cancelActiveCapture(walletUnitId: String) = Unit

    override suspend fun authenticate(request: WscaUserAuthRequest): IdkResult<ActivationProof, IdkError> =
        Err(authError("WALLET_USER_AUTH_PLATFORM_UNAVAILABLE", "wallet.user_auth.platform_unavailable"))
}

@Serializable
private data class StoredLocalUserAuthenticationPolicy(
    val primaryFactor: WscaUserAuthenticationFactor,
    val passkey: WscaPasskeyCredential? = null,
    val pinSalt: String? = null,
    val pinVerifier: String? = null,
    val pinIterations: Int? = null,
    val biometricEnabled: Boolean = false,
    val failedAttempts: Int = 0,
    val lockedUntilEpochSeconds: Long? = null,
) {
    fun toResult(walletUnitId: String) =
        WscaUserEnrollmentResult(
            walletUnitId = walletUnitId,
            primaryFactor = primaryFactor,
            biometricEnabled = biometricEnabled,
            policyRef = "local-policy:$walletUnitId",
        )
}

private fun failure(code: WscaUserEnrollmentFailureCode, retryable: Boolean) =
    WscaUserEnrollmentFailure(code, "wallet.user_enrollment.${code.name.lowercase()}", retryable)

private fun platformUnavailable() = failure(WscaUserEnrollmentFailureCode.PLATFORM_UNAVAILABLE, false)

private fun unavailable(factor: WscaUserAuthenticationFactor) =
    WscaUserAuthenticationMethodCapability(factor, false, WscaUserAuthenticationUnavailableReason.PLATFORM_ADAPTER_ABSENT)

private val unavailableEnrollmentCapabilities: StateFlow<WscaUserEnrollmentCapabilities> =
    MutableStateFlow(
        WscaUserEnrollmentCapabilities(
            passkey = unavailable(WscaUserAuthenticationFactor.PASSKEY),
            pin = unavailable(WscaUserAuthenticationFactor.PIN),
            biometric = unavailable(WscaUserAuthenticationFactor.BIOMETRIC),
        ),
    )

private fun WscaUserEnrollmentFailure.toIdkError(): IdkError = authError("WALLET_USER_AUTH_${code.name}", messageKey)

private fun authError(code: String, messageKey: String): IdkError =
    IdkError.fromString(code = code, category = ErrorCategory.UNAUTHORIZED, message = messageKey)
