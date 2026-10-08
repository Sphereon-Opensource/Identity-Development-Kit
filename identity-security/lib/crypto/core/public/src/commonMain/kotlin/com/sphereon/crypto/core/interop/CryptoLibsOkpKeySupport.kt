/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.crypto.core.interop

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.jose.generateJwkThumbprint
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EdDSA
import dev.whyoleg.cryptography.algorithms.XDH

/*
 * OKP (Octet Key Pair, RFC 8037) key support: bridges between IDK [Jwk] and
 * cryptography-kotlin's [EdDSA] / [XDH] key types.
 *
 * - EdDSA covers signing on Ed25519 / Ed448 (RFC 8032).
 * - XDH covers Diffie-Hellman key agreement on X25519 / X448 (RFC 7748). Note
 *   that cryptography-kotlin's `ECDH` algorithm is for Weierstrass curves
 *   (P-256/P-384/P-521); Montgomery curves use `XDH`.
 *
 * RFC 8032 / RFC 7748 byte lengths:
 * - Ed25519 / X25519 public + private: 32 bytes.
 * - Ed448 public + private (seed): 57 bytes.
 * - X448 public + private: 56 bytes.
 */

internal const val ED25519_KEY_BYTES: Int = 32
internal const val X25519_KEY_BYTES: Int = 32
internal const val ED448_KEY_BYTES: Int = 57
internal const val X448_KEY_BYTES: Int = 56

private val OKP_SIGNING_CURVES = arrayOf(JwaCurve.Ed25519, JwaCurve.Ed448)
private val OKP_KEY_AGREEMENT_CURVES = arrayOf(JwaCurve.X25519, JwaCurve.X448)
private val OKP_ALL_CURVES = OKP_SIGNING_CURVES + OKP_KEY_AGREEMENT_CURVES

/**
 * Resolves the IDK [Curve] to cryptography-kotlin's [EdDSA.Curve] for signing
 * (Ed25519 / Ed448).
 */
fun resolveEdDsaKmpCurve(curve: Curve): EdDSA.Curve =
    when (curve) {
        is Curve.Ed25519 -> EdDSA.Curve.Ed25519
        is Curve.Ed448 -> EdDSA.Curve.Ed448
        else -> throw IllegalArgumentException("Curve $curve is not an EdDSA signing curve (expected Ed25519 or Ed448)")
    }

/**
 * Resolves the IDK [Curve] to cryptography-kotlin's [XDH.Curve] for key
 * agreement (X25519 / X448).
 */
fun resolveXdhKmpCurve(curve: Curve): XDH.Curve =
    when (curve) {
        is Curve.X25519 -> XDH.Curve.X25519
        is Curve.X448 -> XDH.Curve.X448
        else -> throw IllegalArgumentException("Curve $curve is not an XDH key-agreement curve (expected X25519 or X448)")
    }

/** Expected raw byte length for OKP keys on the given curve. */
fun expectedOkpKeyByteLength(curve: Curve): Int =
    when (curve) {
        is Curve.Ed25519 -> ED25519_KEY_BYTES
        is Curve.Ed448 -> ED448_KEY_BYTES
        is Curve.X25519 -> X25519_KEY_BYTES
        is Curve.X448 -> X448_KEY_BYTES
        else -> throw IllegalArgumentException("Curve $curve is not an OKP curve")
    }

internal fun assertValidOkpJwk(key: Jwk) {
    require(key.kty == JwaKeyType.OKP) { "Key type (converted to JWA) ${key.kty} is not of type ${JwaKeyType.OKP}" }
    require(key.crv == null || OKP_ALL_CURVES.contains(key.crv)) {
        "Key crv (converted to JWA) ${key.crv} is not supported for key type ${JwaKeyType.OKP}"
    }
}

/**
 * Decodes the OKP public key from the JWK's `x` parameter (base64url) and
 * validates the resulting byte length matches [curve].
 */
fun Jwk.toRawOkpPublicKeyBytes(curve: Curve): ByteArray {
    assertValidOkpJwk(this)
    val xValue = x ?: throw IllegalArgumentException("OKP JWK is missing required 'x' parameter")
    val raw = xValue.decodeFrom(Encoding.BASE64URL)
    val expected = expectedOkpKeyByteLength(curve)
    require(raw.size == expected) {
        "OKP public key length ${raw.size} does not match expected $expected for curve $curve"
    }
    return raw
}

/**
 * Decodes the OKP private key from the JWK's `d` parameter (base64url) and
 * validates the resulting byte length matches [curve]. Throws if `d` is null.
 */
fun Jwk.toRawOkpPrivateKeyBytes(curve: Curve): ByteArray {
    assertValidOkpJwk(this)
    val dValue = d ?: throw IllegalArgumentException("OKP JWK is missing 'd'; not a private key")
    val raw = dValue.decodeFrom(Encoding.BASE64URL)
    val expected = expectedOkpKeyByteLength(curve)
    require(raw.size == expected) {
        "OKP private key length ${raw.size} does not match expected $expected for curve $curve"
    }
    return raw
}

/**
 * Builds an OKP JWK from raw public key bytes, optionally including the
 * private-key bytes in `d`. The `kid` is set to the RFC 7638 thumbprint when
 * not supplied; `use` and `key_ops` default to signing for Ed25519/Ed448 and
 * key-agreement for X25519/X448.
 */
@Suppress("LongParameterList")
fun okpRawToJwk(
    rawPublic: ByteArray,
    rawPrivate: ByteArray?,
    curve: Curve,
    kid: String? = null,
    use: JwkUse? = null,
    keyOps: Array<out KeyOperations>? = null,
): Jwk {
    val expected = expectedOkpKeyByteLength(curve)
    require(rawPublic.size == expected) {
        "OKP public key length ${rawPublic.size} does not match expected $expected for curve $curve"
    }
    if (rawPrivate != null) {
        require(rawPrivate.size == expected) {
            "OKP private key length ${rawPrivate.size} does not match expected $expected for curve $curve"
        }
    }
    val isSigning = curve is Curve.Ed25519 || curve is Curve.Ed448
    val resolvedUse =
        use?.value ?: if (isSigning) {
            JwkUse.sig.value
        } else {
            JwkUse.enc.value
        }
    val resolvedAlg =
        if (isSigning) {
            JwaAlgorithm.EdDSA
        } else {
            null
        }
    val resolvedKeyOps =
        keyOps?.map { op -> op.jose }?.toTypedArray()
            ?: if (isSigning) {
                arrayOf(KeyOperations.SIGN.jose)
            } else {
                arrayOf(KeyOperations.DERIVE_KEY.jose, KeyOperations.DERIVE_BITS.jose)
            }
    val jwk =
        Jwk(
            kty = JwaKeyType.OKP,
            crv = curve.jose,
            x = rawPublic.encodeToBase64Url(),
            d = rawPrivate?.encodeToBase64Url(),
            alg = resolvedAlg,
            use = resolvedUse,
            key_ops = resolvedKeyOps,
        )
    return jwk.copy(kid = kid ?: generateJwkThumbprint(jwk))
}

/** EdDSA private key wrapped from the JWK using cryptography-kotlin. */
suspend fun Jwk.toEdDsaPrivateKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    curve: Curve,
): EdDSA.PrivateKey {
    val raw = toRawOkpPrivateKeyBytes(curve)
    val edDsa = provider.get(EdDSA)
    return edDsa.privateKeyDecoder(resolveEdDsaKmpCurve(curve)).decodeFromByteArray(EdDSA.PrivateKey.Format.RAW, raw)
}

/** EdDSA public key wrapped from the JWK using cryptography-kotlin. */
suspend fun Jwk.toEdDsaPublicKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    curve: Curve,
): EdDSA.PublicKey {
    val raw = toRawOkpPublicKeyBytes(curve)
    val edDsa = provider.get(EdDSA)
    return edDsa.publicKeyDecoder(resolveEdDsaKmpCurve(curve)).decodeFromByteArray(EdDSA.PublicKey.Format.RAW, raw)
}

/** XDH private key wrapped from the JWK using cryptography-kotlin. */
suspend fun Jwk.toXdhPrivateKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    curve: Curve,
): XDH.PrivateKey {
    val raw = toRawOkpPrivateKeyBytes(curve)
    val xdh = provider.get(XDH)
    return xdh.privateKeyDecoder(resolveXdhKmpCurve(curve)).decodeFromByteArray(XDH.PrivateKey.Format.RAW, raw)
}

/** XDH public key wrapped from the JWK using cryptography-kotlin. */
suspend fun Jwk.toXdhPublicKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    curve: Curve,
): XDH.PublicKey {
    val raw = toRawOkpPublicKeyBytes(curve)
    val xdh = provider.get(XDH)
    return xdh.publicKeyDecoder(resolveXdhKmpCurve(curve)).decodeFromByteArray(XDH.PublicKey.Format.RAW, raw)
}

/** Convenience predicate: is the given curve an OKP curve handled by [EdDSA] or [XDH]? */
fun isOkpCurve(curve: Curve): Boolean = curve is Curve.Ed25519 || curve is Curve.Ed448 || curve is Curve.X25519 || curve is Curve.X448
