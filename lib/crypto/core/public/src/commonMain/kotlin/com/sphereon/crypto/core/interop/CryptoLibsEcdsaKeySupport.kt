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

import at.asitplus.awesn1.Asn1Element
import at.asitplus.awesn1.Asn1Sequence
import at.asitplus.awesn1.crypto.Pkcs8PrivateKeyInfo
import at.asitplus.awesn1.crypto.SubjectPublicKeyInfo
import at.asitplus.awesn1.encoding.parse
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.jose.generateJwkThumbprint
import dev.whyoleg.cryptography.CryptographyAlgorithmId
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.Digest
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA384
import dev.whyoleg.cryptography.algorithms.SHA512

/*
 * These functions serve as conversions and interop between our crypto implementation and external libraries:
 * - A-SIT Plus awesn1 (ASN.1 structural types)
 * - cryptography-kotlin (key generation, signing)
 */

/**
 * Prepares key information context by converting the provided `KeyInfo` to a JWK format
 * and serializing its key into bytes, then resolving curve and algorithm implementations.
 */
@OptIn(ExperimentalStdlibApi::class)
fun keyInfoToEcdsaDerKmpContext(
    keyInfo: KeyInfoType<*>,
    resolvedKey: ResolvedKeyInfoType<*>?,
): DerKmpKeyInfoContext {
    val keyInfoJwk =
        toKeyInfoJwk(
            if (keyInfo.keyVisibility === KeyVisibility.PRIVATE && keyInfo.key !== null) {
                keyInfo
            } else {
                resolvedKey ?: keyInfo
            },
        )

    val key =
        keyInfoJwk.key
            ?: throw IllegalArgumentException("Either we need to get the private key from a private key store or it needs to be passed in for this provider")
    assertValidECJwk(key)
    val publicKeyBytes = toDerEcdsaPublicKeyBytes(key)
    val privateKeyBytes = key.d?.let { toDerEcdsaPrivateKeyBytes(key = key) }
    val curveImpl = resolveEcdsaKmpCurve(Curve.fromJose(key.crv ?: JwaCurve.P_256))

    val jwaAlg =
        key.alg ?: when (curveImpl) {
            EC.Curve.P384 -> JwaAlgorithm.ES384
            EC.Curve.P521 -> JwaAlgorithm.ES512
            else -> JwaAlgorithm.ES256
        }
    val algImpl = resolveEcdsaKmpDigest(SignatureAlgorithm.fromJose(jwaAlg))

    return DerKmpKeyInfoContext(key, publicKeyBytes, privateKeyBytes, curveImpl, algImpl)
}

internal fun assertValidECJwk(key: Jwk) {
    require(key.kty == JwaKeyType.EC) { "Key type (converted to JWA) ${key.kty} is not of type ${JwaKeyType.EC}" }
    require(
        key.alg == null ||
            arrayOf(
                JwaAlgorithm.ES256,
                JwaAlgorithm.ES384,
                JwaAlgorithm.ES512,
            ).contains(key.alg),
    ) { "Key alg (converted to JWA) ${key.alg} is not supported for key type ${JwaKeyType.EC}" }
    require(
        key.crv == null ||
            arrayOf(
                JwaCurve.P_256,
                JwaCurve.P_384,
                JwaCurve.P_521,
            ).contains(key.crv),
    ) { "Key crv (converted to JWA) ${key.crv} is not supported for key type ${JwaKeyType.EC}" }
}

/**
 * Converts DER-encoded EC key bytes to a JWK.
 */
fun convertDerECKeyBytesToJwk(
    publicKeyBytes: ByteArray,
    privateKeyBytes: ByteArray?,
    use: JwkUse = JwkUse.sig,
    keyOperations: Array<out KeyOperations> = arrayOf(KeyOperations.SIGN),
    curve: Curve = Curve.P_256,
    alg: SignatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
): Jwk {
    val jwk: Jwk
    if (privateKeyBytes != null) {
        val seq = Asn1Element.parse(privateKeyBytes) as Asn1Sequence
        val pkcs8 = Pkcs8PrivateKeyInfo.decodeFromTlv(seq)
        jwk = pkcs8.toJwk()
    } else {
        val seq = Asn1Element.parse(publicKeyBytes) as Asn1Sequence
        val spki = SubjectPublicKeyInfo.decodeFromTlv(seq)
        jwk = spki.toJwk()
    }
    val use = use.value
    val key_ops = keyOperations.map { it.jose }.toTypedArray()
    val kid = generateJwkThumbprint(jwk)
    return jwk.copy(kid = jwk.kid ?: kid, use = use, alg = jwk.alg ?: alg.jose, crv = jwk.crv ?: curve.jose, key_ops = key_ops)
}

fun checkSupportedEcdsaCurve(curve: Curve) {
    require(arrayOf(Curve.P_256, Curve.P_384, Curve.P_521).contains(curve)) { "Curve ${curve.jose.name} not supported for EcDSA" }
}

fun resolveEcdsaKmpCurve(curve: Curve): EC.Curve =
    when (curve) {
        is Curve.P_256 -> EC.Curve.P256
        is Curve.P_384 -> EC.Curve.P384
        is Curve.P_521 -> EC.Curve.P521
        else -> throw IllegalArgumentException("Curve $curve not supported")
    }

fun resolveEcdsaKmpDigest(alg: SignatureAlgorithm): CryptographyAlgorithmId<Digest> =
    when (alg) {
        is SignatureAlgorithm.ECDSA_SHA256 -> SHA256
        is SignatureAlgorithm.ECDSA_SHA384 -> SHA384
        is SignatureAlgorithm.ECDSA_SHA512 -> SHA512
        else -> throw IllegalArgumentException("Algorithm $alg not supported")
    }

/**
 * Converts an ECDSA public key JWK to its SubjectPublicKeyInfo DER bytes.
 */
fun toDerEcdsaPublicKeyBytes(key: JwkType): ByteArray {
    val jwk = Jwk.from(key)
    return jwk.toSubjectPublicKeyInfo().encodeToTlv().derEncoded
}

/**
 * Converts an ECDSA private key JWK to PKCS#8 DER bytes.
 */
fun toDerEcdsaPrivateKeyBytes(key: JwkType): ByteArray {
    require(key.kty == JwaKeyType.EC) { "Key type (converted to JWA) ${key.kty} is not of type ${JwaKeyType.EC}" }
    require(key.d != null) { "Cannot convert to private key bytes if the input key is not a private key jwk (missing d param)" }

    val jwk = CoseJoseKeyMappingService.toJoseJwk(key)
    return jwk.toPkcs8PrivateKeyInfo().encodeToTlv().derEncoded
}

suspend fun toDerEcdsaPublicKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    keyInfo: ResolvedKeyInfoType<*>,
): ECDSA.PublicKey {
    val jwkInfo = CoseJoseKeyMappingService.toResolvedJwkKeyInfo(keyInfo)
    val pubKeyBytes = toDerEcdsaPublicKeyBytes(jwkInfo.key)
    val ecdsa = provider.get(ECDSA)
    val curveImpl = resolveEcdsaKmpCurve(Curve.fromJose(jwkInfo.key.crv))
    return ecdsa.publicKeyDecoder(curveImpl).decodeFromByteArray(EC.PublicKey.Format.DER, pubKeyBytes)
}

suspend fun toDerEcdhPublicKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    keyInfo: ResolvedKeyInfoType<*>,
): ECDH.PublicKey {
    val jwkInfo = CoseJoseKeyMappingService.toResolvedJwkKeyInfo(keyInfo)
    val pubKeyBytes = toDerEcdsaPublicKeyBytes(jwkInfo.key)
    val ecdh = provider.get(ECDH)
    val curveImpl = resolveEcdsaKmpCurve(Curve.fromJose(jwkInfo.key.crv))
    return ecdh.publicKeyDecoder(curveImpl).decodeFromByteArray(EC.PublicKey.Format.DER, pubKeyBytes)
}

suspend fun toDerEcdsaPrivateKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    keyInfo: ResolvedKeyInfoType<*>,
): ECDSA.PrivateKey {
    val jwkInfo = CoseJoseKeyMappingService.toResolvedJwkKeyInfo(keyInfo)
    val privKeyBytes = toDerEcdsaPrivateKeyBytes(jwkInfo.key)
    val ecdsa = provider.get(ECDSA)
    val curveImpl = resolveEcdsaKmpCurve(Curve.fromJose(jwkInfo.key.crv))
    return ecdsa.privateKeyDecoder(curveImpl).decodeFromByteArray(EC.PrivateKey.Format.DER, privKeyBytes)
}

suspend fun toDerEcdhPrivateKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    keyInfo: ResolvedKeyInfoType<*>,
): ECDH.PrivateKey {
    val jwkInfo = CoseJoseKeyMappingService.toResolvedJwkKeyInfo(keyInfo)
    val privKeyBytes = toDerEcdsaPrivateKeyBytes(jwkInfo.key)
    val ecdh = provider.get(ECDH)
    val curveImpl = resolveEcdsaKmpCurve(Curve.fromJose(jwkInfo.key.crv))
    return ecdh.privateKeyDecoder(curveImpl).decodeFromByteArray(EC.PrivateKey.Format.DER, privKeyBytes)
}
