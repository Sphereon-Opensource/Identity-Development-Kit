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

import com.sphereon.crypto.core.generic.CryptoAlg
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.JwkUse
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import kotlin.js.Promise

/**
 * Browser/js custody: ECDSA signing keys are generated directly via WebCrypto with
 * `extractable = false` and signed exclusively through `subtle.sign`. The private `CryptoKey`
 * never leaves this file - not as a JWK, not through
 * [com.sphereon.crypto.core.kms.KeyManagerService], not anywhere [SoftwareWscd] (or its
 * KMS-backed path) can see it. Only the exported PUBLIC JWK crosses back into commonMain, via
 * [BrowserWscdKeyPair.publicKeyJwk].
 *
 * [browserKeyRegistry] holds custody for the lifetime of this browser module instance (a browser
 * tab / page load) only: there is no IndexedDB (or other
 * structured-clone-capable store) wiring here, so a page reload loses custody of every key
 * registered here. This is a deliberate, honestly-evidenced scope decision for this lane, not a
 * silent regression. Node is deliberately excluded and uses its configured durable encrypted
 * KMS. Wiring durable, structured-clone-based IndexedDB custody is required before a standalone
 * browser authority may retain credentials across page loads;
 * `internal` visibility here (rather than `private`) is deliberate too, so tests can assert
 * non-extractability against the actual registered `CryptoKey` rather than a parallel one.
 */
internal class BrowserWscdRegistryEntry(
    val privateKey: dynamic,
    val hashName: String,
    val publicKeyJwk: String,
    val keyId: String,
)

internal val browserKeyRegistry = mutableMapOf<String, BrowserWscdRegistryEntry>()

private val hasBrowserSubtleCrypto: Boolean =
    js(
        "typeof window !== 'undefined' && typeof window.crypto !== 'undefined' && typeof window.crypto.subtle !== 'undefined'",
    ) as Boolean

private class WebCryptoEcdsaParams(
    val namedCurve: String,
    val hashName: String,
)

/**
 * Maps [algorithm] to WebCrypto ECDSA generateKey/sign parameters, or `null` if [algorithm] is
 * not an ECDSA algorithm this seam hardens. Non-ECDSA algorithms (RSA, EdDSA) fall back to
 * [SoftwareWscd]'s existing KMS-backed path unchanged - this seam only ever narrows custody for
 * the algorithms it explicitly hardens, it never removes an algorithm the KMS-backed path already
 * supported.
 */
private fun webCryptoEcdsaParams(algorithm: SignatureAlgorithm): WebCryptoEcdsaParams? {
    if (algorithm.cryptoAlgorithm != CryptoAlg.ECDSA) return null
    val namedCurve =
        when (algorithm.curve) {
            Curve.P_256 -> "P-256"
            Curve.P_384 -> "P-384"
            Curve.P_521 -> "P-521"
            else -> return null
        }
    val hashName =
        when (algorithm.digestAlgorithm) {
            DigestAlg.SHA256 -> "SHA-256"
            DigestAlg.SHA384 -> "SHA-384"
            DigestAlg.SHA512 -> "SHA-512"
            else -> return null
        }
    return WebCryptoEcdsaParams(namedCurve = namedCurve, hashName = hashName)
}

internal actual suspend fun tryGenerateBrowserWscdKeyPair(
    alias: String,
    algorithm: SignatureAlgorithm,
): BrowserWscdKeyPair? {
    // Node also exposes `globalThis.crypto.subtle`, but the Node wallet authority has a durable,
    // encrypted software-KMS keystore. It must use that backend instead of this page-lifetime
    // browser registry. Requiring `window` keeps WebCrypto custody browser-only.
    if (!hasBrowserSubtleCrypto) return null
    val params = webCryptoEcdsaParams(algorithm) ?: return null
    val jwaCurve = algorithm.curve?.jose ?: return null

    browserKeyRegistry[alias]?.let { existing ->
        return BrowserWscdKeyPair(
            publicKeyJwk = existing.publicKeyJwk,
            keyId = existing.keyId,
        )
    }

    val subtle = js("globalThis.crypto.subtle")
    val genAlgorithm: dynamic = js("({})")
    genAlgorithm.name = "ECDSA"
    genAlgorithm.namedCurve = params.namedCurve

    val keyPair =
        (
            subtle.generateKey(genAlgorithm, false, js("['sign','verify']")) as Promise<dynamic>
        ).await()

    // Public keys are always exportable in WebCrypto, regardless of the private key's
    // extractable flag: only the private key in keyPair.privateKey is non-extractable.
    val publicJwkAny =
        (
            subtle.exportKey("jwk", keyPair.publicKey) as Promise<dynamic>
        ).await()

    val publicJwk =
        Jwk(
            generateKid = true,
            kty = JwaKeyType.EC,
            crv = jwaCurve,
            x = publicJwkAny.x as String,
            y = publicJwkAny.y as String,
            use = JwkUse.sig.value,
            alg = algorithm.jose,
        )

    val publicKeyJwk = publicJwk.toJsonString()
    val keyId = publicJwk.kid ?: alias
    browserKeyRegistry[alias] =
        BrowserWscdRegistryEntry(
            privateKey = keyPair.privateKey,
            hashName = params.hashName,
            publicKeyJwk = publicKeyJwk,
            keyId = keyId,
        )

    return BrowserWscdKeyPair(publicKeyJwk = publicKeyJwk, keyId = keyId)
}

internal actual suspend fun tryBrowserWscdSign(
    alias: String,
    digest: ByteArray,
): ByteArray? {
    val entry = browserKeyRegistry[alias] ?: return null
    val subtle = js("globalThis.crypto.subtle")

    val hashParam: dynamic = js("({})")
    hashParam.name = entry.hashName
    val signAlgorithm: dynamic = js("({})")
    signAlgorithm.name = "ECDSA"
    signAlgorithm.hash = hashParam

    val signatureBuffer =
        (
            subtle.sign(signAlgorithm, entry.privateKey, digest.toJsInt8Array().buffer) as Promise<dynamic>
        ).await()

    return (signatureBuffer as ArrayBuffer).toKotlinByteArray()
}

internal actual fun forgetBrowserWscdKey(alias: String) {
    browserKeyRegistry.remove(alias)
}

private fun ByteArray.toJsInt8Array(): Int8Array {
    val result = Int8Array(this.size)
    for (i in indices) {
        result.asDynamic()[i] = this[i]
    }
    return result
}

private fun ArrayBuffer.toKotlinByteArray(): ByteArray {
    val view = Int8Array(this)
    return ByteArray(view.length) { view[it] }
}
