/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.jose.jws.command

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.interop.resolveEcdsaKmpCurve
import com.sphereon.crypto.core.interop.resolveEcdsaKmpDigest
import com.sphereon.crypto.core.interop.resolveRSAKmpDigest
import com.sphereon.crypto.core.interop.toEcdsaPublicKey
import com.sphereon.crypto.core.interop.toEdDsaPublicKey
import com.sphereon.crypto.core.interop.toKeyInfoJwk
import com.sphereon.crypto.core.interop.toRsaPkcs1PublicKey
import com.sphereon.crypto.core.interop.toRsaPssPublicKey
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.sign.keyCompatibilityFailure
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.ECDSA

/**
 * Public verification is a local cryptographic operation over already-resolved key material. It
 * must not require a tenant KMS provider: KMS/WSCD selects and protects private wallet keys, while
 * an X.509/JWKS/DID trust resolver supplies the verifier's public key directly.
 */
fun KeyInfoType<*>.hasResolvedPublicJwkVerificationMaterial(): Boolean {
    val key = key as? Jwk ?: return false
    return when (key.kty) {
        JwaKeyType.OKP -> key.x != null
        JwaKeyType.EC -> key.x != null && key.y != null
        JwaKeyType.RSA -> key.n != null && key.e != null
        else -> false
    }
}

/**
 * Checks the JOSE algorithm against the already-resolved verification key.
 *
 * This is deliberately performed before dispatching to either the local public-key verifier or
 * the provider-backed signature service.  A caller-supplied provider/kid is a key selector, not an
 * algorithm selector: accepting the protected-header algorithm in place of the resolved key's
 * constraints permits classic RSA/EC algorithm-confusion and PSS/PKCS#1 substitution attacks.
 * The same rule applies to JWK, X.509-derived, DID-resolved, and managed/provider key results.
 */
internal fun KeyInfoType<*>.jwsAlgorithmCompatibilityFailure(headerAlg: String?): String? =
    jwsAlgorithmCompatibilityFailure(headerAlg, KeyOperations.VERIFY)

internal fun KeyInfoType<*>.jwsSigningAlgorithmCompatibilityFailure(headerAlg: String?): String? =
    jwsAlgorithmCompatibilityFailure(headerAlg, KeyOperations.SIGN)

private fun KeyInfoType<*>.jwsAlgorithmCompatibilityFailure(headerAlg: String?, operation: KeyOperations): String? {
    // Raw-signature callers have no JOSE protected header.  They still receive all key
    // constraints, while the JWS command separately rejects a missing/unknown header alg.
    val algorithm = headerAlg?.let { JwaAlgorithm.fromValue(it) }
        ?: return if (headerAlg == null) null else "JWS protected header alg is missing or unsupported"
    if (algorithm.type != com.sphereon.crypto.core.jose.AlgorithmType.SIGNATURE) {
        return "JWS protected header alg '$headerAlg' is not a signature algorithm"
    }

    val requested = SignatureAlgorithm.tryFromJoseForKey(algorithm, this)
    if (requested.isErr) {
        return requested.error.message.defaultMessage
            ?: "JWS protected header alg '$headerAlg' is not a supported signature algorithm"
    }
    return this.keyCompatibilityFailure(requested.value, operation)
}

suspend fun verifyResolvedPublicJwkSignature(
    keyInfo: KeyInfoType<*>,
    headerAlg: String?,
    input: ByteArray,
    signature: ByteArray,
): Boolean {
    keyInfo.jwsAlgorithmCompatibilityFailure(headerAlg)?.let { throw IllegalArgumentException(it) }
    return verifyResolvedJwkSignatureMathematically(keyInfo, headerAlg, input, signature)
}

/** Post-sign consistency check. SIGN policy is retained; no VERIFY grant is required or added. */
internal suspend fun verifyManagedJwsSigningResult(
    keyInfo: KeyInfoType<*>,
    headerAlg: String?,
    input: ByteArray,
    signature: ByteArray,
): Boolean {
    keyInfo.jwsSigningAlgorithmCompatibilityFailure(headerAlg)?.let { throw IllegalArgumentException(it) }
    return verifyResolvedJwkSignatureMathematically(keyInfo, headerAlg, input, signature)
}

private suspend fun verifyResolvedJwkSignatureMathematically(
    keyInfo: KeyInfoType<*>,
    headerAlg: String?,
    input: ByteArray,
    signature: ByteArray,
): Boolean {
    val effectiveKeyInfo = keyInfo.withHeaderSignatureAlgorithm(headerAlg)
    val key = toKeyInfoJwk(effectiveKeyInfo).key
        ?: throw IllegalArgumentException("Resolved public key is missing JWK material")
    val cryptoProvider = CryptographyProvider.Default

    return when {
        key.kty == JwaKeyType.OKP && key.x != null -> {
            val curve = Curve.fromJose(key.crv ?: throw IllegalArgumentException("OKP verification key is missing 'crv'"))
            require(curve is Curve.Ed25519 || curve is Curve.Ed448) {
                "OKP verification curve must be Ed25519 or Ed448, was $curve"
            }
            key.toEdDsaPublicKey(provider = cryptoProvider, curve = curve)
                .signatureVerifier()
                .tryVerifySignature(input, signature)
        }

        key.kty == JwaKeyType.EC && key.x != null && key.y != null -> {
            val curve = resolveEcdsaKmpCurve(Curve.fromJose(key.crv ?: JwaCurve.P_256))
            val algorithm = effectiveKeyInfo.signatureAlgorithm ?: key.getSignatureAlgorithm() ?: SignatureAlgorithm.ECDSA_SHA256
            key.toEcdsaPublicKey(provider = cryptoProvider, curve = curve)
                .signatureVerifier(digest = resolveEcdsaKmpDigest(algorithm), format = ECDSA.SignatureFormat.RAW)
                .tryVerifySignature(input, signature)
        }

        key.kty == JwaKeyType.RSA && key.n != null && key.e != null -> {
            val algorithm = effectiveKeyInfo.signatureAlgorithm ?: key.getSignatureAlgorithm() ?: SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1
            val digest = resolveRSAKmpDigest(algorithm)
            if (algorithm.isRsaPss()) {
                key.toRsaPssPublicKey(provider = cryptoProvider, digest = digest)
                    .signatureVerifier()
                    .tryVerifySignature(input, signature)
            } else {
                key.toRsaPkcs1PublicKey(provider = cryptoProvider, digest = digest)
                    .signatureVerifier()
                    .tryVerifySignature(input, signature)
            }
        }

        else -> throw IllegalArgumentException("Unsupported JWK verification key type: ${key.kty}")
    }
}

@Suppress("UNCHECKED_CAST")
private fun KeyInfoType<*>.withHeaderSignatureAlgorithm(headerAlg: String?): KeyInfoType<*> {
    val algorithm = headerAlg?.let {
        JwaAlgorithm.fromValue(it)?.let { jwa -> SignatureAlgorithm.tryFromJoseForKey(jwa, this).getOrNull() }
    }
    if (algorithm == null) return this
    // See jwsAlgorithmCompatibilityFailure: an omitted RSA JWK alg is unconstrained, and the
    // inferred RSA_SHA256 value from ResolvedKeyInfo must not force PKCS#1 for a PS header.
    val resolvedKey = key
    if (resolvedKey is Jwk && resolvedKey.alg == null) {
        @Suppress("UNCHECKED_CAST")
        return KeyInfo.fromDTO(this as KeyInfoType<KeyType>).copy(signatureAlgorithm = algorithm)
    }
    if (signatureAlgorithm != null) return this
    return KeyInfo.fromDTO(this as KeyInfoType<KeyType>).copy(signatureAlgorithm = algorithm)
}

private fun SignatureAlgorithm.isRsaPss(): Boolean =
    this == SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1 ||
        this == SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1 ||
        this == SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1 ||
        this == SignatureAlgorithm.RSA_SSA_PSS_RAW_MGF1
