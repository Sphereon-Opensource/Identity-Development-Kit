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

import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm

/**
 * Which layer actually holds a [SoftwareWscd]-provisioned key's private material.
 *
 * - [Kms]: the software KMS ([com.sphereon.crypto.core.kms.KeyManagerService]) holds the key;
 *   [SoftwareWscd] signs by handing it a [KeyInfoType] lookup. This is the ONLY custody kind on
 *   the JVM, and the ONLY custody kind on any platform for algorithms the browser-custody seam
 *   below does not harden.
 * - [BrowserWebCrypto]: a non-extractable WebCrypto `CryptoKey`, generated and held entirely
 *   inside the js-actual [tryGenerateBrowserWscdKeyPair] / [tryBrowserWscdSign] implementations.
 *   [SoftwareWscd] never sees, and the KMS never touches, the private key material for these keys.
 */
internal sealed interface KeyCustody {
    data class Kms(
        val signingKeyInfo: KeyInfoType<*>,
    ) : KeyCustody

    data object BrowserWebCrypto : KeyCustody
}

/**
 * Extra [com.sphereon.wallet.wscd.WscdKeyEvidence.evidence] entries for [custody]. Empty for
 * [KeyCustody.Kms]: the software profile makes no platform attestation claim for KMS-held keys
 * (unchanged, pre-existing behavior). Never contains an ISO 18045 (hardware assurance) claim for
 * either custody kind - the software profile ceiling applies regardless of which layer holds the
 * key.
 */
internal fun wscdCustodyEvidence(custody: KeyCustody): Map<String, String> =
    when (custody) {
        is KeyCustody.BrowserWebCrypto ->
            mapOf(
                "custody" to "browser-webcrypto",
                "non_exportable" to "true",
                // CryptoKey handles live in module memory only; a page reload loses them.
                "custody_durability" to "session",
            )
        is KeyCustody.Kms -> emptyMap()
    }

/** Public-key material for a hardened browser-custody key; the private key never leaves the js-actual [tryGenerateBrowserWscdKeyPair]. */
internal data class BrowserWscdKeyPair(
    val publicKeyJwk: String,
    val keyId: String,
)

/**
 * Platform-specific seam for hardened, non-extractable browser signing-key custody, mirroring the
 * expect/actual split the software KMS provider uses for its own native key generation/signing
 * (`generateKeyPairNative` / `signWithNativeKey` in `lib-crypto-kms-provider-software`): the JVM
 * actual is a permanent no-op (JVM always falls back to [SoftwareWscd]'s existing KMS-backed
 * provisioning path, so JVM behavior is unchanged by this seam); the JS/browser actual generates
 * ECDSA signing keys directly via WebCrypto with `extractable = false` and signs exclusively
 * through `subtle.sign`, so the private scalar never round-trips through
 * [com.sphereon.crypto.core.kms.KeyManagerService] or any JWK/JSON representation.
 *
 * Returning `null` means "this platform/algorithm has no hardened browser custody";
 * [SoftwareWscd] falls back to its existing KMS-backed provisioning for that alias, exactly like
 * `generateKeyPairNative` falling back to software generation when it returns `null`.
 */
internal expect suspend fun tryGenerateBrowserWscdKeyPair(
    alias: String,
    algorithm: SignatureAlgorithm,
): BrowserWscdKeyPair?

/**
 * Signs [digest] with the browser-custody private key registered under [alias] by a prior
 * [tryGenerateBrowserWscdKeyPair] call. Returns `null` when [alias] has no browser-custody key
 * (including on platforms, like the JVM, that never register one), signalling [SoftwareWscd] to
 * fall back to its KMS-backed signing path.
 */
internal expect suspend fun tryBrowserWscdSign(
    alias: String,
    digest: ByteArray,
): ByteArray?

/** Removes any browser-custody key registered under [alias]. No-op if none exists. */
internal expect fun forgetBrowserWscdKey(alias: String)
