package com.sphereon.crypto.core.sign

import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.cose.CoseKeyDTOType
import com.sphereon.crypto.core.cose.CoseKeyJsonDTOType
import com.sphereon.crypto.core.generic.CryptoAlg
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkType

/**
 * Common signing-policy guard used by all KMS-backed signing paths.
 *
 * Key selectors (provider id, kid, alias) identify a key; they never override the algorithm
 * constraints carried by that key. Optional JWK/COSE metadata is enforced when present, while
 * key-family and algorithm/required-curve requirements are always enforced before a provider
 * signs.
 */
fun KeyInfoType<*>.signingKeyCompatibilityFailure(requestedAlgorithm: SignatureAlgorithm): String? {
    return keyCompatibilityFailure(requestedAlgorithm, KeyOperations.SIGN)
}

/** Shared algorithm/key-family/usage policy for signing and JWS verification. */
fun KeyInfoType<*>.keyCompatibilityFailure(
    requestedAlgorithm: SignatureAlgorithm,
    requiredOperation: KeyOperations,
): String? {
    val resolvedKey = key
    val declaredKeyType = keyType
    val resolvedAlgorithm = signatureAlgorithm
    val jwkAlgorithm = (resolvedKey as? JwkType)?.alg
    val resolvedAlgorithmFailure = if (resolvedAlgorithm != null && resolvedAlgorithm != requestedAlgorithm) {
        if (requiredOperation == KeyOperations.VERIFY) {
            "JWS alg '${requestedAlgorithm.jose}' does not match resolved signature algorithm '${resolvedAlgorithm.jose}'"
        } else {
            "requested signing algorithm '$requestedAlgorithm' conflicts with resolved key algorithm '$resolvedAlgorithm'"
        }
    } else {
        null
    }
    val expectedKeyType =
        when (requestedAlgorithm.cryptoAlgorithm) {
            CryptoAlg.RSA -> KeyTypeMapping.RSA
            CryptoAlg.ECDSA -> KeyTypeMapping.EC
            CryptoAlg.ED25519, CryptoAlg.ED448 -> KeyTypeMapping.OKP
            CryptoAlg.HMAC -> KeyTypeMapping.Symmetric
            else -> null
        }
    if (resolvedKey == null) {
        // Metadata-only provider results have no curve-bearing material. They may pass only when
        // both resolved classification fields are present; a caller selector with just an alg
        // hint cannot authorize the operation.
        if (declaredKeyType == null || resolvedAlgorithm == null) {
            return "metadata-only provider key requires resolved key type and signature algorithm"
        }
        if (expectedKeyType != null && declaredKeyType != null && declaredKeyType != expectedKeyType) {
            return "requested signing algorithm '$requestedAlgorithm' requires key type '$expectedKeyType', resolved '$declaredKeyType'"
        }
        resolvedAlgorithmFailure?.let { return it }
        return null
    }
    val actualKeyType = runCatching { resolvedKey.getKeyType() }.getOrNull()
    if (actualKeyType != null && declaredKeyType != null && actualKeyType != declaredKeyType) {
        return "key type metadata '$declaredKeyType' does not match resolved key type '$actualKeyType'"
    }

    if (expectedKeyType != null && actualKeyType != null && actualKeyType != expectedKeyType) {
        return "requested signing algorithm '$requestedAlgorithm' requires key type '$expectedKeyType', resolved '$actualKeyType'"
    }

    when (resolvedKey) {
        is JwkType -> {
            val jwkUse = resolvedKey.use
            val jwkKeyOperations = resolvedKey.key_ops
            val jwkCurve = resolvedKey.crv
            if (jwkAlgorithm != null) {
                val declaredAlgorithm = SignatureAlgorithm.tryFromJoseForKey(jwkAlgorithm, this).getOrNull()
                    ?: return "JWK alg '$jwkAlgorithm' is not a supported signing algorithm"
                if (declaredAlgorithm != requestedAlgorithm) {
                    return if (requiredOperation == KeyOperations.VERIFY) {
                        "JWS alg '${requestedAlgorithm.jose}' does not match resolved JWK alg '$jwkAlgorithm'"
                    } else {
                        "requested signing algorithm '$requestedAlgorithm' conflicts with JWK alg '$jwkAlgorithm'"
                    }
                }
            }
            resolvedAlgorithmFailure?.let { return it }
            if (jwkUse != null && jwkUse != "sig") {
                return "JWK use must be 'sig' for signing, was '$jwkUse'"
            }
            if (jwkKeyOperations != null && requiredOperation.jose !in jwkKeyOperations) {
                return "JWK key_ops must include '${requiredOperation.jose.value}'"
            }
            val expectedCurve = requestedAlgorithm.curve?.jose
            if (expectedCurve != null) {
                if (jwkCurve == null) {
                    return "requested signing algorithm '$requestedAlgorithm' requires curve '$expectedCurve', but resolved JWK has no 'crv'"
                }
                if (jwkCurve != expectedCurve) {
                    return "requested signing algorithm '$requestedAlgorithm' requires curve '$expectedCurve', resolved '$jwkCurve'"
                }
            }
        }
        is CoseKeyJsonDTOType -> {
            val coseAlgorithm = resolvedKey.alg
            val coseKeyOperations = resolvedKey.key_ops
            val coseCurve = resolvedKey.crv
            val declaredAlgorithm = coseAlgorithm?.let { SignatureAlgorithm.tryFromCoseForKey(it, this).getOrNull() }
            if (coseAlgorithm != null && declaredAlgorithm == null) {
                return "COSE alg '$coseAlgorithm' is not a supported signing algorithm"
            }
            if (declaredAlgorithm != null && declaredAlgorithm != requestedAlgorithm) {
                return "requested signing algorithm '$requestedAlgorithm' conflicts with COSE alg '$declaredAlgorithm'"
            }
            resolvedAlgorithmFailure?.let { return it }
            if (coseKeyOperations != null && coseKeyOperations.none { it.paramName == requiredOperation.jose.value }) {
                return "COSE key_ops must include '${requiredOperation.jose.value}'"
            }
            val expectedCurve = requestedAlgorithm.curve?.cose
            if (expectedCurve != null) {
                if (coseCurve == null) {
                    return "requested signing algorithm '$requestedAlgorithm' requires curve '$expectedCurve', but resolved COSE key has no 'crv'"
                }
                if (coseCurve != expectedCurve) {
                    return "requested signing algorithm '$requestedAlgorithm' requires curve '$expectedCurve', resolved '$coseCurve'"
                }
            }
        }
        is CoseKeyDTOType -> {
            val coseAlgorithm = resolvedKey.alg
            val coseKeyOperations = resolvedKey.key_ops
            val coseCurve = resolvedKey.crv
            val declaredAlgorithm = coseAlgorithm?.value?.toInt()?.let {
                com.sphereon.crypto.core.cose.CoseAlgorithm.fromValue(it)?.let { coseAlg ->
                    SignatureAlgorithm.tryFromCoseForKey(coseAlg, this).getOrNull()
                }
            }
            if (coseAlgorithm != null && declaredAlgorithm == null) {
                return "COSE alg '$coseAlgorithm' is not a supported signing algorithm"
            }
            if (declaredAlgorithm != null && declaredAlgorithm != requestedAlgorithm) {
                return "requested signing algorithm '$requestedAlgorithm' conflicts with COSE alg '$declaredAlgorithm'"
            }
            resolvedAlgorithmFailure?.let { return it }
            if (coseKeyOperations != null && coseKeyOperations.value.none { it.value.toInt() == requiredOperation.cose.value }) {
                return "COSE key_ops must include '${requiredOperation.jose.value}'"
            }
            val expectedCurve = requestedAlgorithm.curve?.cose
            if (expectedCurve != null) {
                if (coseCurve == null) {
                    return "requested signing algorithm '$requestedAlgorithm' requires a COSE curve, but resolved COSE key has no 'crv'"
                }
                if (coseCurve.value.toInt() != expectedCurve.value) {
                    return "requested signing algorithm '$requestedAlgorithm' has an incompatible COSE curve"
                }
            }
        }
        else -> resolvedAlgorithmFailure?.let { return it }
    }
    return null
}

fun KeyInfoType<*>.requireSigningKeyCompatible(requestedAlgorithm: SignatureAlgorithm) {
    signingKeyCompatibilityFailure(requestedAlgorithm)?.let { throw IllegalArgumentException(it) }
}
