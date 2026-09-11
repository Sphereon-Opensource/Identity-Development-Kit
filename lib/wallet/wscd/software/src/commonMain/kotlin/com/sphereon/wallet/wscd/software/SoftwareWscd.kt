/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wscd.software

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.wscd.ActivationProof
import com.sphereon.wallet.wscd.ActivationProofKind
import com.sphereon.wallet.wscd.Wscd
import com.sphereon.wallet.wscd.WscdKeyEvidence
import com.sphereon.wallet.wscd.WscdKeyHandle
import com.sphereon.wallet.wscd.WscdKeySpec
import com.sphereon.wallet.wscd.WscdProfile
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Custody-only [Wscd] implementation over the software KMS ([KeyManagerService]).
 *
 * This is the software-profile half of the WSCA/WSCD split: it owns key material, the
 * alias-to-key identity map and all KMS access. It has NO knowledge of DPoP, client
 * attestation, key attestation or any other policy/protocol concern; those live in the WSCA
 * layer (`LocalWsca`), which signs exclusively through this interface.
 *
 * Key material is generated in, and signatures are produced by, the software KMS - EXCEPT on the
 * js/browser target for ECDSA keys, where [tryGenerateBrowserWscdKeyPair] hardens custody: the
 * private key is a non-extractable WebCrypto `CryptoKey` that never round-trips through the KMS
 * (see [KeyCustody] and the browser-custody seam in `WscdKeyCustody.kt`/`WscdKeyCustody.js.kt`).
 * Both custody kinds share this class's [Wscd] contract and alias/idempotency semantics
 * identically; only [provisionKey]/[signDigest]/[deleteKey]/[keyEvidence]'s internal dispatch
 * differs.
 *
 * Because the software profile has no external Signature Activation Module (SAM, ETSI TS 119
 * 431-1 / EN 419 241), [signDigest] only accepts activation proofs of kind [ActivationProofKind.LOCAL_USER_AUTH]
 * (the WSCA's local user-auth token) or [ActivationProofKind.NONE_DEV_ONLY] (unauthenticated,
 * development use only); [ActivationProofKind.REMOTE_ACTIVATION_DECISION] is rejected with a
 * clear [IdkError] since there is no remote SAM to have made that decision.
 *
 * KMS wiring: [providerBootstrap] lazily ensures the session's software KMS
 * provider is registered on first [generateKey]/[generateFreshKey] call. Product/runner bootstraps
 * never touch KMS themselves; whichever session-scoped consumer signs first triggers the
 * (session-shared, exactly-once) registration. See [SoftwareKmsProviderRegistrar] for why the
 * registrar itself is session-scoped rather than owned per-consumer.
 *
 * [providerBootstrap] is a REQUIRED [KmsProviderBootstrap]: Metro injects the session-scoped
 * [SoftwareKmsProviderRegistrar] (defaulted or nullable injected params are Metro traps:
 * defaults are skipped by codegen, nullable types need an explicitly nullable binding).
 * Pure-unit tests with a fake/pre-wired [KeyManagerService] pass a no-op lambda:
 * `KmsProviderBootstrap {}`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Wscd>())
class SoftwareWscd(
    private val keyManagerService: KeyManagerService,
    private val providerBootstrap: KmsProviderBootstrap,
) : Wscd {
    override val profile: WscdProfile get() = WscdProfile.Software

    /**
     * Session-scoped cache of resolved secure-component keys, keyed by the effective WSCD key
     * alias. The cache is not the authority for KMS-held keys: [generateKey] rehydrates an
     * existing key from the configured KMS before it provisions anything, preserving the WSCD
     * contract that the same durable alias resolves to the same key across wallet sessions.
     */
    private val provisionedKeys = mutableMapOf<String, ProvisionedSecureComponentKey>()
    private val provisioningMutex = Mutex()

    override suspend fun generateKey(spec: WscdKeySpec): IdkResult<WscdKeyHandle, IdkError> {
        providerBootstrap.ensureRegistered()
        // A caller-supplied alias is honored verbatim as the key identity; otherwise the secure
        // component derives a single stable identity per (wallet unit, usage, algorithm).
        val alias = spec.alias?.takeIf { it.isNotBlank() } ?: deriveDefaultAlias(spec.walletUnitId, spec.usage, spec.algorithm)
        return provisioningMutex.withLock {
            // The cache is only an optimization for browser custody. Durable KMS aliases must be
            // revalidated against the KMS reference authority on every lookup; otherwise a caller
            // can relabel a cached handle by supplying the same alias from another wallet unit.
            when (val recovered = rehydrateKmsKey(alias = alias, spec = spec)) {
                null -> Unit
                is Ok -> {
                    provisionedKeys[alias] = recovered.value
                    return@withLock Ok(recovered.value.handle)
                }
                is Err -> return@withLock recovered
            }
            provisionedKeys[alias]?.let { cached ->
                if (cached.handle.walletUnitId != spec.walletUnitId) {
                    return@withLock Err(ownerMismatchError(alias, spec.walletUnitId, cached.handle.walletUnitId))
                }
                return@withLock Ok(cached.handle)
            }
            provisionKey(alias = alias, spec = spec)
        }
    }

    /**
     * Resolves durable KMS custody for [alias]. A missing alias is not an error here because the
     * caller will provision it while holding [provisioningMutex]. Browser WebCrypto keys never
     * enter the KMS and therefore continue to be resolved by the browser registry in
     * [provisionKey].
     */
    private suspend fun rehydrateKmsKey(
        alias: String,
        spec: WscdKeySpec,
    ): IdkResult<ProvisionedSecureComponentKey, IdkError>? {
        // Request PRIVATE explicitly: KeyInfo defaults to PUBLIC, which makes software
        // keystores strip `d`/`k`. Softwares createRawSignature treats inline key material as
        // authoritative, so a public-only rehydrate would poison the session cache and make
        // every later signDigest fail closed even though the KMS still holds the private key.
        val managed =
            keyManagerService
                .getKeyResult(KeyInfo<Nothing>(alias = alias, keyVisibility = KeyVisibility.PRIVATE))
                .getOrNull()
                ?.key
                ?: return null
        val jwk =
            managed.key as? Jwk
                ?: return Err(
                    IdkError.fromString(
                        code = "WALLET_WSCD_KEY_REHYDRATION_FAILED",
                        message = "Durable key '$alias' did not resolve to a JWK",
                    ),
                )
        if (jwk.d == null && jwk.k == null) {
            return Err(
                IdkError.fromString(
                    code = "WALLET_WSCD_KEY_REHYDRATION_FAILED",
                    message =
                        "Durable key '$alias' rehydrated without private key material; " +
                            "refusing to cache a public-only signing handle",
                ),
            )
        }
        val reference =
            keyManagerService.findRegisteredKeyReference(aliasOrKid = alias, providerId = managed.providerId)
                ?: return Err(ownerMetadataMissingError(alias))
        val owner = reference.walletUnitId ?: return Err(ownerMetadataMissingError(alias))
        if (owner != spec.walletUnitId) {
            return Err(ownerMismatchError(alias, spec.walletUnitId, owner))
        }
        val publicJwk = jwk.toPublicKey().copy(kid = jwk.kid ?: managed.kid ?: alias)
        val handle =
            WscdKeyHandle(
                keyRef = alias,
                profile = WscdProfile.Software,
                walletUnitId = owner,
                publicKeyJwk = publicJwk.toJsonString(),
                keyId = publicJwk.kid ?: managed.kid ?: alias,
                providerId = managed.providerId,
            )
        return Ok(ProvisionedSecureComponentKey(handle = handle, custody = KeyCustody.Kms(managed)))
    }

    override suspend fun generateFreshKey(spec: WscdKeySpec): IdkResult<WscdKeyHandle, IdkError> {
        providerBootstrap.ensureRegistered()
        // Every call gets its own unique, WSCD-generated alias: there is no identity for a
        // caller to reuse, so unlike generateKey there is no pre-lock cache check here; a fresh
        // key is minted unconditionally on every call.
        val alias = deriveFreshAlias(spec.walletUnitId)
        return provisioningMutex.withLock {
            provisionKey(alias = alias, spec = spec)
        }
    }

    /**
     * Shared generate-and-wrap body for both [generateKey] and [generateFreshKey]: generates
     * fresh key material for [alias] via the KMS and registers it in [provisionedKeys] so a
     * later [signDigest] call can resolve it. Callers MUST hold [provisioningMutex] before
     * invoking this.
     */
    private suspend fun provisionKey(
        alias: String,
        spec: WscdKeySpec,
    ): IdkResult<WscdKeyHandle, IdkError> {
        tryGenerateBrowserWscdKeyPair(alias = alias, algorithm = spec.algorithm)?.let { browserKeyPair ->
            val handle =
                WscdKeyHandle(
                    keyRef = alias,
                    profile = WscdProfile.Software,
                    walletUnitId = spec.walletUnitId,
                    publicKeyJwk = browserKeyPair.publicKeyJwk,
                    keyId = browserKeyPair.keyId,
                    providerId = BROWSER_CUSTODY_PROVIDER_ID,
                )
            provisionedKeys[alias] = ProvisionedSecureComponentKey(handle = handle, custody = KeyCustody.BrowserWebCrypto)
            return Ok(handle)
        }

        val generated =
            keyManagerService
                .generateKeyResult(
                    alias = alias,
                    use = JwkUse.sig,
                    alg = spec.algorithm,
                    keyVisibility = KeyVisibility.PRIVATE,
                    walletUnitId = spec.walletUnitId,
                ).getOrElse { return Err(it) }

        val keyPair =
            generated.keyPair
                ?: return Err(
                    IdkError.fromString(
                        code = "WALLET_WSCD_KEY_PROVISIONING_FAILED",
                        message = "Software WSCD did not return key material for wallet unit '${spec.walletUnitId}'",
                    ),
                )

        val signingKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val reference =
            keyManagerService.findRegisteredKeyReference(aliasOrKid = alias, providerId = keyPair.providerId)
                ?: return Err(ownerMetadataMissingError(alias))
        val owner = reference.walletUnitId ?: return Err(ownerMetadataMissingError(alias))
        if (owner != spec.walletUnitId) {
            return Err(ownerMismatchError(alias, spec.walletUnitId, owner))
        }
        val handle =
            WscdKeyHandle(
                keyRef = alias,
                profile = WscdProfile.Software,
                walletUnitId = owner,
                publicKeyJwk = keyPair.jose.publicJwk.toJsonString(),
                keyId = keyPair.kid ?: alias,
                providerId = keyPair.providerId,
            )

        provisionedKeys[alias] = ProvisionedSecureComponentKey(handle = handle, custody = KeyCustody.Kms(signingKeyInfo))
        return Ok(handle)
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
                            "Software WSCD has no remote Signature Activation Module (SAM); " +
                                "REMOTE_ACTIVATION_DECISION activation proofs are not supported. " +
                                "Use LOCAL_USER_AUTH or NONE_DEV_ONLY.",
                    ),
                )
            ActivationProofKind.LOCAL_USER_AUTH, ActivationProofKind.NONE_DEV_ONLY -> Unit
        }

        val provisioned = resolveProvisionedKey(handle).getOrElse { return Err(it) }

        val signature =
            when (val custody = provisioned.custody) {
                is KeyCustody.BrowserWebCrypto ->
                    tryBrowserWscdSign(alias = handle.keyRef, digest = digest)
                        ?: return Err(
                            IdkError.fromString(
                                code = "WALLET_WSCD_BROWSER_CUSTODY_KEY_MISSING",
                                message =
                                    "Browser-custody key for reference '${handle.keyRef}' (wallet unit " +
                                        "'${handle.walletUnitId}') was provisioned but is no longer held by " +
                                        "the platform's WebCrypto custody registry",
                            ),
                        )

                is KeyCustody.Kms ->
                    keyManagerService
                        .createRawSignatureResult(keyInfo = custody.signingKeyInfo, input = digest)
                        .getOrElse { return Err(it) }
                        .signature
            }
        return Ok(signature)
    }

    override suspend fun deleteKey(handle: WscdKeyHandle): IdkResult<Unit, IdkError> {
        val provisioned = resolveProvisionedKey(handle).getOrElse { return Err(it) }
        when (val custody = provisioned.custody) {
            is KeyCustody.BrowserWebCrypto -> forgetBrowserWscdKey(handle.keyRef)
            is KeyCustody.Kms -> keyManagerService.deleteKeyResult(custody.signingKeyInfo).getOrElse { return Err(it) }
        }
        provisionedKeys.remove(handle.keyRef)
        return Ok(Unit)
    }

    override suspend fun keyEvidence(handle: WscdKeyHandle): IdkResult<WscdKeyEvidence, IdkError> {
        val provisioned = resolveProvisionedKey(handle).getOrElse { return Err(it) }
        // Honest custody: neither custody kind ever makes an ISO 18045 (hardware assurance)
        // claim - the software profile ceiling applies regardless. KMS-held keys sit in an
        // unprotected software KMS and get no evidence entries at all; browser-custody keys are
        // non-extractable WebCrypto CryptoKeys and say so (see wscdCustodyEvidence).
        return Ok(WscdKeyEvidence(profile = WscdProfile.Software, evidence = wscdCustodyEvidence(provisioned.custody)))
    }

    /**
     * Resolve a cached handle only after checking its owner against authoritative metadata.
     * KMS-held aliases are never authorized from the in-memory cache alone.
     */
    private suspend fun resolveProvisionedKey(handle: WscdKeyHandle): IdkResult<ProvisionedSecureComponentKey, IdkError> {
        val provisioned =
            provisionedKeys[handle.keyRef]
                ?: return Err(
                    IdkError.fromString(
                        code = "WALLET_WSCD_KEY_NOT_PROVISIONED",
                        message =
                            "No secure-component-held key provisioned for reference '${handle.keyRef}' " +
                                "(wallet unit '${handle.walletUnitId}')",
                    ),
                )
        when (val custody = provisioned.custody) {
            KeyCustody.BrowserWebCrypto -> {
                if (provisioned.handle.walletUnitId != handle.walletUnitId) {
                    return Err(ownerMismatchError(handle.keyRef, handle.walletUnitId, provisioned.handle.walletUnitId))
                }
            }
            is KeyCustody.Kms -> {
                val reference =
                    keyManagerService.findRegisteredKeyReference(
                        aliasOrKid = handle.keyRef,
                        providerId = custody.signingKeyInfo.providerId,
                    ) ?: return Err(ownerMetadataMissingError(handle.keyRef))
                val owner = reference.walletUnitId ?: return Err(ownerMetadataMissingError(handle.keyRef))
                if (owner != handle.walletUnitId) {
                    return Err(ownerMismatchError(handle.keyRef, handle.walletUnitId, owner))
                }
            }
        }
        return Ok(provisioned)
    }

    private fun ownerMetadataMissingError(alias: String): IdkError =
        IdkError.fromString(
            code = "WALLET_WSCD_KEY_OWNER_METADATA_MISSING",
            message = "Durable key '$alias' has no authoritative wallet-unit owner metadata",
        )

    private fun ownerMismatchError(alias: String, requestedOwner: String, actualOwner: String?): IdkError =
        IdkError.fromString(
            code = "WALLET_WSCD_KEY_OWNER_MISMATCH",
            message =
                "Durable key '$alias' belongs to wallet unit '${actualOwner ?: "unknown"}', " +
                    "not '$requestedOwner'",
        )

    private fun deriveDefaultAlias(
        walletUnitId: String,
        usage: SecureComponentUsage,
        algorithm: SignatureAlgorithm,
    ): String = "$KEY_NAMESPACE/$walletUnitId/${usage.name.lowercase()}/${joseAlgorithm(algorithm).lowercase()}"

    /**
     * Fresh, WSCD-generated alias for [generateFreshKey]: unlike [deriveDefaultAlias] this is
     * never derived deterministically, each call gets a unique suffix so the KMS always mints
     * new key material for it.
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
        val custody: KeyCustody,
    )

    private companion object {
        const val KEY_NAMESPACE: String = "wallet-units"
        const val BROWSER_CUSTODY_PROVIDER_ID: String = "browser-webcrypto"
    }
}
