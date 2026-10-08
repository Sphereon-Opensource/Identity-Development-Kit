/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wscd.mobile

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.kms.provider.mobile.MobileKmsProvider
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.wscd.ActivationProof
import com.sphereon.wallet.wscd.ActivationProofKind
import com.sphereon.wallet.wscd.Wscd
import com.sphereon.wallet.wscd.WscdKeyEvidence
import com.sphereon.wallet.wscd.WscdKeyHandle
import com.sphereon.wallet.wscd.WscdKeySpec
import com.sphereon.wallet.wscd.WscdProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Custody-only [Wscd] implementation over the mobile platform keystore ([MobileKmsProvider]):
 * Android StrongBox / iOS Secure Enclave via signum-supreme, reached through the OS keystore API.
 *
 * This is the [WscdProfile.LocalNative] half of the WSCA/WSCD split, structurally
 * mirroring [com.sphereon.wallet.wscd.software.SoftwareWscd] (custody-only; NO knowledge of DPoP,
 * client attestation or any other policy/protocol concern - those live in the WSCA layer, which
 * signs exclusively through this interface). Unlike [com.sphereon.wallet.wscd.software.SoftwareWscd],
 * this class wraps a [MobileKmsProvider] instance directly rather than the session's shared
 * [com.sphereon.crypto.core.kms.KeyManagerService]: hardware-backed keys live in the platform
 * keystore, and [MobileKmsProvider] already talks to signum-supreme (the OS keystore bridge)
 * itself, so there is no session-shared KMS provider registry to lazily register into (contrast
 * [com.sphereon.wallet.wscd.software.SoftwareKmsProviderRegistrar]) - [provider] is fully
 * constructed by [LocalNativeWscdFactory] before this class ever sees it.
 *
 * NOT `@ContributesBinding<Wscd>`: unlike [com.sphereon.wallet.wscd.software.SoftwareWscd] (the
 * session's single direct `Wscd` binding), this class is factory-created only, via
 * [LocalNativeWscdFactory] resolving the contributed `Set<WscdFactory>` for a
 * `WscdConfig.LocalNative`. Wiring WHICH `WscdConfig` a given session/bootstrap selects (software
 * vs. local-native vs. remote) is out of scope for this class; it only implements the LocalNative
 * custody half once selected.
 *
 * Key material is generated in, and signatures are produced by, the platform keystore via
 * [MobileKmsProvider]. Because the local-native profile has no external Signature Activation
 * Module (SAM, ETSI TS 119 431-1 / EN 419 241) either, [signDigest] accepts the same activation
 * proof kinds as [com.sphereon.wallet.wscd.software.SoftwareWscd]:
 * [ActivationProofKind.LOCAL_USER_AUTH] (the WSCA's local user-auth token - on real devices this is
 * where the OS biometric/device-unlock prompt for the key would surface) or
 * [ActivationProofKind.NONE_DEV_ONLY] (unauthenticated, development use only);
 * [ActivationProofKind.REMOTE_ACTIVATION_DECISION] is rejected with a clear [IdkError] since there
 * is no remote SAM to have made that decision.
 *
 * Signing note: [signDigest]'s `digest` parameter is the WSCA-assembled SIGNING INPUT (e.g. a
 * compact JWS header.payload string), NOT a pre-hashed digest, matching
 * [com.sphereon.wallet.wscd.software.SoftwareWscd]'s established contract (see `LocalWsca`, which
 * passes raw signing input here). This class therefore calls
 * [MobileKmsProvider.createRawSignature] (hash-then-sign over the given bytes), NOT
 * [MobileKmsProvider.signDigest] (a DIFFERENT, no-rehash operation over an already-hashed digest) -
 * despite the name collision with this method, using the latter here would produce signatures over
 * the wrong bytes.
 *
 * requireStrongBox (`WscdConfig.LocalNative.requireStrongBox`): [MobileKmsProviderImpl] honors a
 * per-provider-instance `REQUIRED` hardware-backing feature preference instead of defaulting to
 * `PREFERRED` (via `MobileKmsProviderConfig.defaultConfigValues[MOBILE_KMS_HARDWARE_BACKING_KEY]`,
 * set by [LocalNativeWscdFactory] when [requireStrongBox] is true). With `REQUIRED`, signum-supreme's
 * platform actuals refuse to create the key AT ALL when hardware-backed storage is unavailable
 * (verified on the JVM actual: `JKSProvider` throws `UnsupportedCryptoException("Hardware storage
 * is unsupported on the JVM")`) rather than silently falling back to software storage. [provisionKey]
 * surfaces ANY [MobileKmsProvider.generateKeyAsync] failure on a `requireStrongBox` provider as
 * `WALLET_WSCD_HARDWARE_REQUIRED` (fail CLOSED, no key is ever created/persisted in that case - a
 * strictly safer property than "create the key, then verify, then delete it"), preserving the
 * original exception for diagnosis. This is a coarser classification than inspecting the failure
 * cause (LocalNativeWscd deliberately does not depend on signum types directly, staying scoped to
 * [MobileKmsProvider]) but is accurate for the dominant real-world case, since `REQUIRED` hardware
 * backing is the only WSCD-introduced failure mode a `requireStrongBox` provider instance has.
 *
 * Key attestation (G10): partially open. signum-supreme 0.15.0 exposes platform key attestation
 * (`hardware { attestation { challenge = ... } }`, `Signer.Attestable<AttestationT>.attestation`),
 * but [MobileKmsProvider]/`MobileKmsProviderImpl` - the interface this WSCD is scoped to wrap - does
 * not plumb a caller-supplied attestation challenge through `generateKeyAsync`, nor surface
 * `Signer.Attestable` on the returned key pair. [keyEvidence] therefore reports
 * `"key_attestation" to "unavailable-pending-signum"` until that plumbing exists.
 */
class LocalNativeWscd(
    private val provider: MobileKmsProvider,
    private val requireStrongBox: Boolean,
) : Wscd {
    override val profile: WscdProfile get() = WscdProfile.LocalNative

    /**
     * Session-scoped cache of provisioned secure-component keys, keyed by the effective WSCD key
     * alias. Provides idempotency (same alias -> same key) and holds the [KeyInfoType] used to
     * look the key back up in the platform keystore, so [signDigest]/[deleteKey] never re-derive
     * key material. Uses [KeyVisibility.PUBLIC] (unlike
     * [com.sphereon.wallet.wscd.software.SoftwareWscd]'s [KeyVisibility.PRIVATE]): the platform
     * keystore never exposes private key material to this layer, alias-based lookup is all
     * [MobileKmsProvider] needs.
     */
    private val provisionedKeys = mutableMapOf<String, ProvisionedSecureComponentKey>()
    private val provisioningMutex = Mutex()

    override suspend fun generateKey(spec: WscdKeySpec): IdkResult<WscdKeyHandle, IdkError> {
        // A caller-supplied alias is honored verbatim as the key identity; otherwise the secure
        // component derives a single stable identity per (wallet unit, usage, algorithm) - IDENTICAL
        // scheme to SoftwareWscd (binding requirement: alias derivation must match).
        val alias = spec.alias?.takeIf { it.isNotBlank() } ?: deriveDefaultAlias(spec.walletUnitId, spec.usage, spec.algorithm)
        provisionedKeys[alias]?.let { return Ok(it.handle) }
        return provisioningMutex.withLock {
            provisionedKeys[alias]?.let { return@withLock Ok(it.handle) }
            provisionKey(alias = alias, spec = spec)
        }
    }

    override suspend fun generateFreshKey(spec: WscdKeySpec): IdkResult<WscdKeyHandle, IdkError> {
        // Every call gets its own unique, WSCD-generated alias: there is no identity for a caller
        // to reuse, so unlike generateKey there is no pre-lock cache check here.
        val alias = deriveFreshAlias(spec.walletUnitId)
        return provisioningMutex.withLock {
            provisionKey(alias = alias, spec = spec)
        }
    }

    /**
     * Shared generate-and-wrap body for both [generateKey] and [generateFreshKey]: generates fresh
     * key material for [alias] via [provider] and registers it in [provisionedKeys] so a later
     * [signDigest]/[deleteKey] call can resolve it. Callers MUST hold [provisioningMutex] before
     * invoking this.
     */
    private suspend fun provisionKey(
        alias: String,
        spec: WscdKeySpec,
    ): IdkResult<WscdKeyHandle, IdkError> {
        val managedKeyPair =
            try {
                provider.generateKeyAsync(alias = alias, use = JwkUse.sig, alg = spec.algorithm)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return Err(provisioningFailure(spec.walletUnitId, e))
            }

        val signingKeyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
        val handle =
            WscdKeyHandle(
                keyRef = alias,
                profile = WscdProfile.LocalNative,
                walletUnitId = spec.walletUnitId,
                publicKeyJwk = managedKeyPair.jose.publicJwk.toJsonString(),
                keyId = managedKeyPair.kid ?: alias,
                providerId = managedKeyPair.providerId,
            )

        provisionedKeys[alias] = ProvisionedSecureComponentKey(handle = handle, keyInfo = signingKeyInfo)
        return Ok(handle)
    }

    /**
     * Classifies a [MobileKmsProvider.generateKeyAsync] failure. When [requireStrongBox] is set,
     * this provider instance was configured to request `REQUIRED` hardware backing (see the class
     * KDoc): the platform refusing key creation is the ONLY WSCD-introduced failure mode at this
     * call site, so the failure is reported as `WALLET_WSCD_HARDWARE_REQUIRED` (fail closed). The
     * original exception is preserved in [IdkError.exception] either way for diagnosis.
     */
    private fun provisioningFailure(
        walletUnitId: String,
        cause: Throwable,
    ): IdkError =
        if (requireStrongBox) {
            providerError(
                code = "WALLET_WSCD_HARDWARE_REQUIRED",
                message =
                    "LocalNative WSCD could not provision a hardware-backed key for wallet unit '$walletUnitId': " +
                        "${cause.message}. requireStrongBox=true requests REQUIRED hardware backing from the " +
                        "platform keystore; the platform refused key creation rather than silently falling back " +
                        "to software storage.",
                cause = cause,
            )
        } else {
            providerError(
                code = "WALLET_WSCD_KEY_PROVISIONING_FAILED",
                message = "LocalNative WSCD did not provision a key for wallet unit '$walletUnitId': ${cause.message}",
                cause = cause,
            )
        }

    override suspend fun signDigest(
        handle: WscdKeyHandle,
        digest: ByteArray,
        activation: ActivationProof,
    ): IdkResult<ByteArray, IdkError> {
        when (activation.kind) {
            ActivationProofKind.REMOTE_ACTIVATION_DECISION ->
                return Err(
                    IdkError.fromString(
                        code = "WALLET_WSCD_ACTIVATION_UNSUPPORTED",
                        message =
                            "LocalNative WSCD has no remote Signature Activation Module (SAM); " +
                                "REMOTE_ACTIVATION_DECISION activation proofs are not supported. " +
                                "Use LOCAL_USER_AUTH or NONE_DEV_ONLY.",
                    ),
                )
            ActivationProofKind.LOCAL_USER_AUTH, ActivationProofKind.NONE_DEV_ONLY -> Unit
        }

        val provisioned = provisionedKeys[handle.keyRef] ?: return Err(notProvisionedError(handle))

        // createRawSignature (hash-then-sign over the given bytes), NOT MobileKmsProvider.signDigest
        // (no-rehash over an already-hashed digest) - see the class KDoc "Signing note".
        val signature =
            try {
                provider.createRawSignature(keyInfo = provisioned.keyInfo, input = digest, requireX5Chain = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return Err(
                    providerError(
                        code = "WALLET_WSCD_SIGN_FAILED",
                        message = "LocalNative WSCD failed to sign for key reference '${handle.keyRef}': ${e.message}",
                        cause = e,
                    ),
                )
            }
        return Ok(signature)
    }

    override suspend fun deleteKey(handle: WscdKeyHandle): IdkResult<Unit, IdkError> {
        val provisioned = provisionedKeys[handle.keyRef] ?: return Err(notProvisionedError(handle))

        val deleted =
            try {
                provider.deleteKey(provisioned.keyInfo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return Err(
                    providerError(
                        code = "WALLET_WSCD_KEY_DELETE_FAILED",
                        message = "LocalNative WSCD failed to delete key reference '${handle.keyRef}': ${e.message}",
                        cause = e,
                    ),
                )
            }
        if (!deleted) {
            return Err(
                providerError(
                    code = "WALLET_WSCD_KEY_DELETE_FAILED",
                    message = "LocalNative WSCD could not delete key reference '${handle.keyRef}' (wallet unit '${handle.walletUnitId}')",
                ),
            )
        }
        provisionedKeys.remove(handle.keyRef)
        return Ok(Unit)
    }

    override suspend fun keyEvidence(handle: WscdKeyHandle): IdkResult<WscdKeyEvidence, IdkError> {
        if (!provisionedKeys.containsKey(handle.keyRef)) {
            return Err(notProvisionedError(handle))
        }
        // Honest custody (EUDI TS03 v1.5.2 key_storage / key_attestation claim keys, per WscdProfile's
        // KDoc): key_storage reflects the device keystore path this profile always takes;
        // hardware_backing_preference reflects what THIS key's provisioning requested (see the class
        // KDoc "requireStrongBox"); key_attestation stays "unavailable-pending-signum" until
        // MobileKmsProvider plumbs a caller-supplied attestation challenge through generateKeyAsync
        // (G10, partially open - see class KDoc).
        val evidence =
            mapOf(
                "key_storage" to "device_keystore",
                "hardware_backing_preference" to if (requireStrongBox) "required" else "preferred",
                "key_attestation" to "unavailable-pending-signum",
            )
        return Ok(WscdKeyEvidence(profile = WscdProfile.LocalNative, evidence = evidence))
    }

    private fun notProvisionedError(handle: WscdKeyHandle): IdkError =
        IdkError.fromString(
            code = "WALLET_WSCD_KEY_NOT_PROVISIONED",
            message =
                "No secure-component-held key provisioned for reference '${handle.keyRef}' " +
                    "(wallet unit '${handle.walletUnitId}'); call generateKey or generateFreshKey first",
        )

    private fun providerError(
        code: String,
        message: String,
        cause: Throwable? = null,
    ): IdkError = IdkError(code = code, message = IdkError.Message(i18nKey = code, defaultMessage = message), exception = cause)

    private fun deriveDefaultAlias(
        walletUnitId: String,
        usage: SecureComponentUsage,
        algorithm: SignatureAlgorithm,
    ): String = "$KEY_NAMESPACE/$walletUnitId/${usage.name.lowercase()}/${joseAlgorithm(algorithm).lowercase()}"

    /**
     * Fresh, WSCD-generated alias for [generateFreshKey]: unlike [deriveDefaultAlias] this is never
     * derived deterministically, each call gets a unique suffix so the keystore always mints new key
     * material for it.
     */
    private fun deriveFreshAlias(walletUnitId: String): String = "$KEY_NAMESPACE/$walletUnitId/credential/${Uuid.v4String()}"

    private fun joseAlgorithm(algorithm: SignatureAlgorithm): String =
        when (algorithm) {
            SignatureAlgorithm.ECDSA_SHA256 -> "ES256"
            SignatureAlgorithm.ECDSA_SHA384 -> "ES384"
            SignatureAlgorithm.ECDSA_SHA512 -> "ES512"
            SignatureAlgorithm.ED25519, SignatureAlgorithm.ED448 -> "EdDSA"
            else -> algorithm.toString()
        }

    private data class ProvisionedSecureComponentKey(
        val handle: WscdKeyHandle,
        val keyInfo: KeyInfoType<*>,
    )

    private companion object {
        const val KEY_NAMESPACE: String = "wallet-units"
    }
}
