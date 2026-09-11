/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wsca

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.wallet.wscd.ActivationProof
import com.sphereon.wallet.wscd.ActivationProofKind
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Everything a [WscaUserAuthentication] ceremony needs to mint an [ActivationProof] for one
 * WSCA-gated crypto operation - the CIR (EU) 2024/2979 Art. 5(1) requirement that the WSCA
 * "performs operations linked to critical assets only after successful wallet user
 * authentication" made concrete as a caller-facing request shape.
 *
 * [walletAccountId]/[digestBinding]/[audience] are optional because they are meaningful only to
 * some ceremony backends: a local, non-networked ceremony (see
 * `com.sphereon.wallet.wsca.impl.LocalWsca`) needs none of them, while a remote/HSM-backed
 * ceremony (see `com.sphereon.wallet.unit.remote.wscd.RemoteWsca`, EDK) requires
 * [walletAccountId] and [digestBinding] to bind the resulting activation decision to a specific
 * (account, operation) pair and rejects a request missing either.
 *
 * @property walletUnitId the wallet unit the ceremony authorizes an operation for.
 * @property operationType free-form label for the crypto operation being authorized (mirrors the
 *   `OPERATION_TYPE_*` constants each [Wsca] implementation defines for its own operations, e.g.
 *   `RemoteWsca`'s `wallet.wsca.remote.sign`).
 * @property operationBinding stable identifier of the attended wallet operation. Every WSCA
 *   sub-operation covered by one passkey assertion carries this same value; another interaction
 *   cannot reuse that assertion.
 * @property operationKeyRef exact WSCD key reference authorized by this operation when the
 *   ceremony is remote and key-scoped. Local-only ceremonies may leave it absent because they do
 *   not cross a routed WSCD boundary; remote implementations fail closed when it is absent.
 * @property walletAccountId the wallet account performing the operation, when the ceremony backend
 *   is account-scoped.
 * @property digestBinding `"<digest-alg>:<hex-digest>"` of the exact bytes the resulting
 *   [ActivationProof] authorizes, when the ceremony backend binds authorization to a specific
 *   digest.
 * @property nonce one-time value the ceremony backend should bind into the resulting
 *   [ActivationProof.nonce], when applicable.
 * @property audience the intended relying party / resource for the authorized operation, when the
 *   ceremony backend requires one.
 */
data class WscaUserAuthRequest(
    val walletUnitId: String,
    val operationType: String,
    val operationBinding: String,
    val operationKeyRef: String?,
    val walletAccountId: String? = null,
    val digestBinding: String? = null,
    val nonce: String? = null,
    val audience: String? = null,
) {
    init {
        require(operationBinding.isNotBlank()) { "wallet_user_authentication_operation_binding_blank" }
        require(operationKeyRef == null || operationKeyRef.isNotBlank()) { "wallet_user_authentication_operation_key_ref_blank" }
    }
}

/** Lifecycle of a [WscaUserAuthentication] ceremony. */
enum class WscaUserAuthenticationStatus { IDLE, AUTHENTICATING, AUTHENTICATED, FAILED }

/**
 * Observable status of the most recent [WscaUserAuthentication.authenticate] call.
 *
 * @property lastActivationKind the [ActivationProofKind] of the most recently minted proof, once
 *   [status] reaches [WscaUserAuthenticationStatus.AUTHENTICATED].
 * @property lastError the failure, once [status] reaches [WscaUserAuthenticationStatus.FAILED].
 */
data class WscaUserAuthState(
    val status: WscaUserAuthenticationStatus,
    val lastActivationKind: ActivationProofKind? = null,
    val lastError: IdkError? = null,
)

/** Local factors the WSCA can enroll and invoke without exposing their secret material. */
@Serializable
enum class WscaUserAuthenticationFactor {
    PASSKEY,
    PIN,
    BIOMETRIC,
}

/** Public WebAuthn credential material retained by the local WSCA policy. */
@Serializable
data class WscaPasskeyCredential(
    val credentialId: String,
    val publicKeyBase64Url: String,
    val signatureCounter: Long,
    val transports: List<String> = emptyList(),
    val backedUp: Boolean = false,
) {
    init {
        require(credentialId.isNotBlank()) { "wallet_user_passkey_credential_id_blank" }
        require(publicKeyBase64Url.isNotBlank()) { "wallet_user_passkey_public_key_blank" }
        require(signatureCounter >= 0) { "wallet_user_passkey_counter_invalid" }
    }
}

/** Safe progress only; the corresponding digits remain exclusively in the secure capture slot. */
@Serializable
enum class WscaPinEntryStage { IDLE, PRIMARY, CONFIRMATION, DERIVING, COMPLETE }

/** Stable reason why a factor cannot be used on the current application/platform assembly. */
@Serializable
enum class WscaUserAuthenticationUnavailableReason {
    PLATFORM_ADAPTER_ABSENT,
    UNSUPPORTED_PLATFORM,
    SECURE_STORAGE_UNAVAILABLE,
    DEVICE_CREDENTIAL_NOT_CONFIGURED,
    BIOMETRIC_HARDWARE_UNAVAILABLE,
    BIOMETRIC_NOT_ENROLLED,
}

@Serializable
data class WscaUserAuthenticationMethodCapability(
    val factor: WscaUserAuthenticationFactor,
    val available: Boolean,
    val unavailableReason: WscaUserAuthenticationUnavailableReason? = null,
) {
    init {
        require(available == (unavailableReason == null)) { "wallet_user_authentication_capability_invalid" }
    }
}

/** Serializable, secret-free platform capability snapshot used by onboarding presenters. */
@Serializable
data class WscaUserEnrollmentCapabilities(
    val passkey: WscaUserAuthenticationMethodCapability,
    val pin: WscaUserAuthenticationMethodCapability,
    val biometric: WscaUserAuthenticationMethodCapability,
) {
    init {
        require(passkey.factor == WscaUserAuthenticationFactor.PASSKEY) { "wallet_user_authentication_passkey_capability_invalid" }
        require(pin.factor == WscaUserAuthenticationFactor.PIN) { "wallet_user_authentication_pin_capability_invalid" }
        require(biometric.factor == WscaUserAuthenticationFactor.BIOMETRIC) { "wallet_user_authentication_biometric_capability_invalid" }
    }
}

/** Starts platform-owned WebAuthn registration for the local Wallet Unit. */
@Serializable
data class WscaPasskeyEnrollmentRequest(
    val walletUnitId: String,
    val ceremonyRef: WscaUserEnrollmentCeremonyRef,
    val displayName: String,
) {
    init {
        require(walletUnitId.isNotBlank()) { "wallet_user_enrollment_wallet_unit_id_blank" }
        require(displayName.isNotBlank()) { "wallet_user_enrollment_passkey_display_name_blank" }
    }
}

/**
 * Opaque identifier for one platform-owned attended ceremony. It is safe in presenter state and
 * deliberately redacts itself when accidentally interpolated into logs.
 */
@Serializable
data class WscaUserEnrollmentCeremonyRef(val value: String) {
    init {
        require(value.isNotBlank()) { "wallet_user_enrollment_ceremony_ref_blank" }
    }

    override fun toString(): String = "WscaUserEnrollmentCeremonyRef([redacted])"
}

/**
 * Requests platform-owned PIN capture and confirmation. The PIN itself never crosses this
 * contract, enters serialized state, or reaches a presenter/navigation destination.
 */
@Serializable
data class WscaPinEnrollmentRequest(
    val walletUnitId: String,
    val ceremonyRef: WscaUserEnrollmentCeremonyRef,
    val requiredLength: Int = 6,
) {
    init {
        require(walletUnitId.isNotBlank()) { "wallet_user_enrollment_wallet_unit_id_blank" }
        require(requiredLength >= 6) { "wallet_user_enrollment_pin_length_too_short" }
    }
}

/** Enables device biometrics only as a convenience over an already enrolled PIN factor. */
@Serializable
data class WscaBiometricEnrollmentRequest(
    val walletUnitId: String,
    val ceremonyRef: WscaUserEnrollmentCeremonyRef,
) {
    init {
        require(walletUnitId.isNotBlank()) { "wallet_user_enrollment_wallet_unit_id_blank" }
    }
}

@Serializable
enum class WscaUserEnrollmentFailureCode {
    PLATFORM_UNAVAILABLE,
    SECURE_STORAGE_UNAVAILABLE,
    PIN_MISMATCH,
    PIN_POLICY_REJECTED,
    PIN_NOT_ENROLLED,
    PASSKEY_UNAVAILABLE,
    PASSKEY_REGISTRATION_FAILED,
    PASSKEY_AUTHENTICATION_FAILED,
    BIOMETRIC_UNAVAILABLE,
    BIOMETRIC_NOT_ENROLLED,
    CANCELLED,
    ENROLLMENT_NOT_FOUND,
    ENROLLMENT_CONFLICT,
    INTERNAL,
}

/** Typed, localization-ready failure; never carries PIN, biometric or platform-token material. */
@Serializable
data class WscaUserEnrollmentFailure(
    val code: WscaUserEnrollmentFailureCode,
    val messageKey: String,
    val retryable: Boolean,
)

@Serializable
data class WscaUserEnrollmentResult(
    val walletUnitId: String,
    val primaryFactor: WscaUserAuthenticationFactor,
    val biometricEnabled: Boolean,
    /** Opaque platform policy reference. It is not an authentication token. */
    val policyRef: String,
) {
    init {
        require(walletUnitId.isNotBlank()) { "wallet_user_enrollment_wallet_unit_id_blank" }
        require(primaryFactor == WscaUserAuthenticationFactor.PASSKEY || primaryFactor == WscaUserAuthenticationFactor.PIN) {
            "wallet_user_enrollment_primary_factor_invalid"
        }
        require(!biometricEnabled || primaryFactor == WscaUserAuthenticationFactor.PIN) {
            "wallet_user_enrollment_biometric_requires_pin"
        }
        require(policyRef.isNotBlank()) { "wallet_user_enrollment_policy_ref_blank" }
    }
}

/**
 * Tier 2 user-authentication ceremony surface (CIR (EU) 2024/2979 Art. 5(1)): the seam
 * through which a [Wsca] implementation obtains an [ActivationProof] gating a
 * crypto operation on a completed wallet-user-authentication ceremony, rather than performing
 * WSCD operations unconditionally.
 *
 * Every [Wsca] implementation exposes one via [Wsca.userAuthentication]. What "authentication"
 * concretely means is implementation-defined: `LocalWsca` (idk `lib-wallet-wsca-impl`) fails
 * closed unless its application graph supplies an explicit platform passkey/PIN/biometric component;
 * `RemoteWsca` (EDK `lib-wallet-unit-remote`) backs it with the existing ETSI TS 119 431-1
 * Signature Activation Module round trip. Primary-factor verification policy remains inside
 * WSCA and the platform component; presenters and navigation state never receive authenticator
 * secrets.
 */
interface WscaUserAuthentication {
    /** Status of the most recently completed or in-flight ceremony. */
    val state: StateFlow<WscaUserAuthState>

    /** Current platform support. Unsupported hosts report an explicit reason and fail closed. */
    val enrollmentCapabilities: StateFlow<WscaUserEnrollmentCapabilities>

    /** Returns the single enrolled primary factor for this Wallet Unit. */
    suspend fun primaryFactor(walletUnitId: String): IdkResult<WscaUserAuthenticationFactor?, IdkError>

    /** Registers a passkey as the primary and exclusive local authorization factor. */
    suspend fun enrollPasskey(request: WscaPasskeyEnrollmentRequest): IdkResult<WscaUserEnrollmentResult, WscaUserEnrollmentFailure>

    /** Runs PIN capture+confirmation inside the platform adapter and durably installs its verifier. */
    suspend fun enrollPin(request: WscaPinEnrollmentRequest): IdkResult<WscaUserEnrollmentResult, WscaUserEnrollmentFailure>

    /** Adds biometrics over an existing PIN policy; biometrics can never become the recovery factor. */
    suspend fun enableBiometrics(request: WscaBiometricEnrollmentRequest): IdkResult<WscaUserEnrollmentResult, WscaUserEnrollmentFailure>

    /** Compensates an incomplete onboarding attempt and removes its platform policy material. */
    suspend fun rollbackEnrollment(walletUnitId: String): IdkResult<Unit, WscaUserEnrollmentFailure>

    /** Runs the user-authentication ceremony for [request], returning the resulting [ActivationProof]. */
    suspend fun authenticate(request: WscaUserAuthRequest): IdkResult<ActivationProof, IdkError>
}
