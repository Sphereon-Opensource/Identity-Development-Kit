/*
 * © 2025 Sphereon International B.V.
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

import at.asitplus.signum.indispensable.CryptoPrivateKey
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.josef.JsonWebKey
import dev.whyoleg.cryptography.CryptographyAlgorithmId
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.Digest
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA384
import dev.whyoleg.cryptography.algorithms.SHA512
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.jose.generateJwkThumbprint


/**
 * These functions mainly serve as conversions and interop between our crypto implementation and 2 external projects being used for the actual low level crypto:
 * - A-sit plus signum
 * - kmp-crypto
 */
/**
 * Prepares key information context by converting the provided `KeyInfo` to a JWK format
 * and serializing its key into bytes, then resolving curve and algorithm implementations.
 *
 * @param keyInfo The key information to be prepared, implementing the `KeyInfo` interface.
 * @return A `KeyInfoContext` containing the key, its byte representation, the curve, and the algorithm.
 */
@OptIn(ExperimentalStdlibApi::class)
fun keyInfoToEcdsaDerKmpContext(keyInfo: KeyInfoType<*>, resolvedKey: ResolvedKeyInfoType<*>?): DerKmpKeyInfoContext {
    // Use either the resolved key or the original keyInfo
    val keyInfoJwk = toKeyInfoJwk(
        if (keyInfo.keyVisibility === KeyVisibility.PRIVATE && keyInfo.key !== null)
            keyInfo
        else
            resolvedKey ?: keyInfo
    )

    val key = keyInfoJwk.key
        ?: throw IllegalArgumentException("Either we need to get the private key from a private key store or it needs to be passed in for this provider")
    assertValidECJwk(key)
    val publicKeyBytes = toDerEcdsaPublicKeyBytes(key)
    val privateKeyBytes = key.d?.let { toDerEcdsaPrivateKeyBytes(key = key) }
    val curveImpl = resolveEcdsaKmpCurve(Curve.fromJose(key.crv ?: JwaCurve.P_256))

    val jwaAlg = key.alg ?: when (curveImpl) {
        EC.Curve.P384 -> JwaAlgorithm.ES384
        EC.Curve.P521 -> JwaAlgorithm.ES512
        else -> JwaAlgorithm.ES256
    }
    val algImpl = resolveEcdsaKmpDigest(SignatureAlgorithm.fromJose(jwaAlg))

    return DerKmpKeyInfoContext(key, publicKeyBytes, privateKeyBytes, curveImpl, algImpl)
}


/**
 * Validates the given JWK (JSON Web Key) to ensure it meets specific criteria for
 * Elliptic Curve Digital Signature Algorithm (ECDSA) keys.
 *
 * @param key the JWK to validate. It should have a key type of EC, a supported
 *            algorithm (ES256, ES384, or ES512), and a supported curve (P-256, P-384, or P-521).
 * @throws IllegalArgumentException if any of the validation checks fail.
 */
internal fun assertValidECJwk(key: Jwk) {
    require(key.kty == JwaKeyType.EC) { "Key type (converted to JWA) ${key.kty} is not of type ${JwaKeyType.EC}" }
    require(
        key.alg == null ||
                arrayOf(
                    JwaAlgorithm.ES256,
                    JwaAlgorithm.ES384,
                    JwaAlgorithm.ES512
                ).contains(key.alg)
    ) { "Key alg (converted to JWA) ${key.alg} is not supported for key type ${JwaKeyType.EC}" }
    require(
        key.crv == null ||
                arrayOf(
                    JwaCurve.P_256,
                    JwaCurve.P_384,
                    JwaCurve.P_521
                ).contains(key.crv)
    ) { "Key crv (converted to JWA) ${key.crv} is not supported for key type ${JwaKeyType.EC}" }
}


/**
 * Converts the provided key bytes to a JSON Web Key (JWK).
 *
 * @param publicKeyBytes The bytes representing the key to be converted.
 * @return The equivalent JWK representation of the provided key bytes.
 */
fun convertDerECKeyBytesToJwk(
    publicKeyBytes: ByteArray,
    privateKeyBytes: ByteArray?,
    use: JwkUse = JwkUse.sig,
    keyOperations: Array<out KeyOperations> = arrayOf(KeyOperations.SIGN),
    curve: Curve = Curve.P_256,
    alg: SignatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
): Jwk {
    val jwk: Jwk
    if (privateKeyBytes != null) {
        val privateKey = CryptoPrivateKey.decodeFromDer(privateKeyBytes)
        jwk = privateKey.toJwk()
    } else {
        val publicKey = CryptoPublicKey.decodeFromDer(publicKeyBytes)
        jwk = publicKey.toJwk()
    }
    val use = use.value
    val key_ops = keyOperations.map { it.jose }.toTypedArray()
    val kid = generateJwkThumbprint(jwk)
    return jwk.copy(kid = jwk.kid ?: kid, use = use, alg = jwk.alg ?: alg.jose, crv = jwk.crv ?: curve.jose, key_ops = key_ops)
}

/**
 * Checks whether the provided curve is supported for EcDSA and throws an
 * IllegalArgumentException if it is not supported.
 *
 * @param curve The curve to be checked for support.
 */
fun checkSupportedEcdsaCurve(curve: Curve) {
    if (!arrayOf(Curve.P_256, Curve.P_384, Curve.P_521).contains(curve)) {
        throw IllegalArgumentException("Curve ${curve.jose.name} not supported for EcDSA")
    }
}

/**
 * Resolves the given curve mapping to an elliptic curve.
 *
 * @param curve The curve mapping to be resolved.
 * @return The corresponding elliptic curve.
 * @throws IllegalArgumentException If the provided curve is not supported.
 */
fun resolveEcdsaKmpCurve(curve: Curve): EC.Curve {
    return when (curve) {
        is Curve.P_256 -> EC.Curve.P256
        is Curve.P_384 -> EC.Curve.P384
        is Curve.P_521 -> EC.Curve.P521
        else -> throw IllegalArgumentException("Curve $curve not supported")
    }
}

fun resolveEcdsaSignumCurve(curve: Curve): ECCurve {
    return when (curve) {
        Curve.P_256 -> ECCurve.SECP_256_R_1
        Curve.P_384 -> ECCurve.SECP_384_R_1
        Curve.P_521 -> ECCurve.SECP_521_R_1
        else -> throw IllegalArgumentException("Curve $curve not supported")
    }
}

/**
 * Resolves the given algorithm mapping to its corresponding digest identifier.
 *
 * @param alg The algorithm mapping to resolve.
 * @return The corresponding CryptographyAlgorithmId for the given digest.
 */
fun resolveEcdsaKmpDigest(alg: SignatureAlgorithm): CryptographyAlgorithmId<Digest> {
    return when (alg) {
        is SignatureAlgorithm.ECDSA_SHA256 -> SHA256
        is SignatureAlgorithm.ECDSA_SHA384 -> SHA384
        is SignatureAlgorithm.ECDSA_SHA512 -> SHA512
        else -> throw IllegalArgumentException("Algorithm $alg not supported")
    }
}


/**
 * Converts an ECDSA public key represented as an `JwkType` to its raw byte array format.
 * The resulting byte array contains the uncompressed public key coordinates.
 *
 * @param key The JSON Web Key (JWK) containing the ECDSA public key information.
 * @return The raw byte array format of the ECDSA public key.
 */
fun toDerEcdsaPublicKeyBytes(key: JwkType): ByteArray {
    val x = key.x
    val y = key.y
    requireNotNull(x) { "Cannot convert public key bytes if the input EC key does not have an X coordinate "}
    requireNotNull(y) { "Cannot convert public key bytes if the input EC key does not have an Y coordinate "}
    return JsonWebKey.deserialize(Jwk.from(key).toJsonString()).getOrThrow().toCryptoPublicKey().getOrThrow().encodeToDer()
}

/**
 * Converts the private key component of a given JwkType to a raw ECDSA private key byte array.
 *
 * @param key The input JWK from which the private key bytes will be extracted. The key must have
 *            a non-null 'd' parameter, which represents the private or secret part of the cryptographic key.
 * @return A byte array representing the raw ECDSA private key.
 * @throws IllegalArgumentException if the 'd' parameter of the input key is null, indicating that
 *         the key is not a private key JWK.
 */
fun toDerEcdsaPrivateKeyBytes(key: JwkType): ByteArray {
    require(key.kty == JwaKeyType.EC) { "Key type (converted to JWA) ${key.kty} is not of type ${JwaKeyType.EC}" }
    require(key.d != null) { "Cannot convert to private key bytes if the input key is not a private key jwk (missing d param)" }

    val jwk = CoseJoseKeyMappingService.toJoseJwk(key)
    val signumPrivateKey = jwk.toSignumPrivateKey()
    return signumPrivateKey.encodeToDer()

}

/**
 * Converts a resolved ECDSA public key to its raw byte representation.
 *
 * @param provider The cryptographic provider to use for the conversion. Defaults to `CryptographyProvider.Default`.
 * @param keyInfo An instance of `ResolvedKeyInfo` containing the resolved ECDSA key information.
 * @return An instance of `ECDSA.PublicKey` decoded from the raw byte representation.
 */
suspend fun toDerEcdsaPublicKey(provider: CryptographyProvider = CryptographyProvider.Default, keyInfo: ResolvedKeyInfoType<*>): ECDSA.PublicKey {
    val jwkInfo = CoseJoseKeyMappingService.toResolvedJwkKeyInfo(keyInfo)
    val pubKeyBytes = toDerEcdsaPublicKeyBytes(jwkInfo.key)
    val ecdsa = provider.get(ECDSA)
    val curveImpl = resolveEcdsaKmpCurve(Curve.fromJose(jwkInfo.key.crv))
    return ecdsa.publicKeyDecoder(curveImpl).decodeFromByteArray(EC.PublicKey.Format.DER, pubKeyBytes)
}


suspend fun toDerEcdhPublicKey(provider: CryptographyProvider = CryptographyProvider.Default, keyInfo: ResolvedKeyInfoType<*>): ECDH.PublicKey {
    val jwkInfo = CoseJoseKeyMappingService.toResolvedJwkKeyInfo(keyInfo)
    val pubKeyBytes = toDerEcdsaPublicKeyBytes(jwkInfo.key)
    val ecdh = provider.get(ECDH)
    val curveImpl = resolveEcdsaKmpCurve(Curve.fromJose(jwkInfo.key.crv))
    return ecdh.publicKeyDecoder(curveImpl).decodeFromByteArray(EC.PublicKey.Format.DER, pubKeyBytes)
}


/**
 * Converts the given resolved ECDSA key information to a raw ECDSA private key.
 *
 * @param provider The cryptography provider to use for the conversion. Defaults to CryptographyProvider.Default.
 * @param keyInfo The resolved key information of the ECDSA key to be converted.
 * @return The decoded raw ECDSA private key.
 */
suspend fun toDerEcdsaPrivateKey(provider: CryptographyProvider = CryptographyProvider.Default, keyInfo: ResolvedKeyInfoType<*>): ECDSA.PrivateKey {
    val jwkInfo = CoseJoseKeyMappingService.toResolvedJwkKeyInfo(keyInfo)
    val privKeyBytes = toDerEcdsaPrivateKeyBytes(jwkInfo.key)
    val ecdsa = provider.get(ECDSA)
    val curveImpl = resolveEcdsaKmpCurve(Curve.fromJose(jwkInfo.key.crv))
    return ecdsa.privateKeyDecoder(curveImpl).decodeFromByteArray(EC.PrivateKey.Format.DER, privKeyBytes)
}


suspend fun toDerEcdhPrivateKey(provider: CryptographyProvider = CryptographyProvider.Default, keyInfo: ResolvedKeyInfoType<*>): ECDH.PrivateKey {
    val jwkInfo = CoseJoseKeyMappingService.toResolvedJwkKeyInfo(keyInfo)
    val privKeyBytes = toDerEcdsaPrivateKeyBytes(jwkInfo.key)
    val ecdh = provider.get(ECDH)
    val curveImpl = resolveEcdsaKmpCurve(Curve.fromJose(jwkInfo.key.crv))
    return ecdh.privateKeyDecoder(curveImpl).decodeFromByteArray(EC.PrivateKey.Format.DER, privKeyBytes)
}
