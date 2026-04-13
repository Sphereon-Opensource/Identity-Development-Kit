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
import at.asitplus.signum.indispensable.josef.JsonWebKey
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyAlgorithmId
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.Digest
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA384
import dev.whyoleg.cryptography.algorithms.SHA512
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.jose.JwaAlgorithm
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
fun keyInfoToRSADerKmpContext(keyInfo: KeyInfoType<*>, resolver: ((info: KeyInfoType<*>) -> KeyInfoType<*>?)? = null): DerKmpKeyInfoContext {
    val keyInfoJwk =
        toKeyInfoJwk(if (keyInfo.keyVisibility === KeyVisibility.PRIVATE && keyInfo.key !== null) keyInfo else (resolver?.let { resolveFunc ->
            resolveFunc(keyInfo)
        } ?: keyInfo))

    val key = keyInfoJwk.key
        ?: throw IllegalArgumentException("Either we need to get the private key from a private key store or it needs to be passed in for this provider")

    assertValidRSAJwk(key)
    val publicKeyBytes = toDerRSAPublicKeyBytes(key)
    val privateKeyBytes = key.d?.let { toDerRSAPrivateKeyBytes(provider = CryptographyProvider.Default, key = key) }
    val curveImpl = null
    val algImpl = key.alg?.let { resolveRSAKmpDigest(SignatureAlgorithm.fromJose(it))} ?: SHA256

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
fun assertValidRSAJwk(key: Jwk) {
    require(key.kty == JwaKeyType.RSA) { "Key type (converted to JWA) ${key.kty} is not of type ${JwaKeyType.RSA}" }
    val algs = arrayOf(
        JwaAlgorithm.PS256, JwaAlgorithm.PS384, JwaAlgorithm.PS512, JwaAlgorithm.RS256, JwaAlgorithm.RS384, JwaAlgorithm.RS512, JwaAlgorithm.RSA1_5
    )
    require(
        key.alg == null ||
        algs.contains(key.alg)
    ) { "Key alg (converted to JWA) ${key.alg} is not supported for key type ${JwaKeyType.RSA}. Allowed ${algs.joinToString { it.value }}" }
    require(key.crv == null) { "Key crv (converted to JWA) ${key.crv} is not supported for key type ${JwaKeyType.RSA}" }

}


/**
 * Converts the provided key bytes to a JSON Web Key (JWK).
 *
 * @param publicKeyBytes The bytes representing the key to be converted.
 * @return The equivalent JWK representation of the provided key bytes.
 */
fun convertDerRSAKeyBytesToJwk(
    publicKeyBytes: ByteArray,
    privateKeyBytes: ByteArray?,
    use: JwkUse = JwkUse.sig,
    keyOperations: Array<out KeyOperations> = arrayOf(KeyOperations.SIGN),
    alg: SignatureAlgorithm = SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1
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
    return jwk.copy(kid = jwk.kid ?: kid, use = use, alg = jwk.alg ?: alg.jose, key_ops = key_ops)
}

/**
 * Resolves the given algorithm mapping to its corresponding digest identifier.
 *
 * @param alg The algorithm mapping to resolve.
 * @return The corresponding CryptographyAlgorithmId for the given digest.
 */
fun resolveRSAKmpDigest(alg: SignatureAlgorithm): CryptographyAlgorithmId<Digest> {
    return when (alg) {
        is SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1, SignatureAlgorithm.RSA_SHA256, SignatureAlgorithm.RSA_RAW -> SHA256
        is SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1, SignatureAlgorithm.RSA_SHA384 -> SHA384
        is SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1, SignatureAlgorithm.RSA_SHA512 -> SHA512
        else -> throw IllegalArgumentException("Algorithm $alg not supported")
    }
}

/**
 * Resolves the PSS salt size for the given signature algorithm.
 * Per RFC 7518, the salt length must equal the hash output length.
 *
 * @param alg The RSA-PSS signature algorithm.
 * @return The salt size in BinarySize format.
 */
fun resolvePSSSaltSize(alg: SignatureAlgorithm): dev.whyoleg.cryptography.BinarySize {
    return when (alg) {
        SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1 -> 32.bytes // SHA-256 = 32 bytes
        SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1 -> 48.bytes // SHA-384 = 48 bytes
        SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1 -> 64.bytes // SHA-512 = 64 bytes
        SignatureAlgorithm.RSA_SSA_PSS_RAW_MGF1 -> 32.bytes // Default to SHA-256
        else -> throw IllegalArgumentException("Algorithm $alg is not an RSA-PSS algorithm")
    }
}

/**
 * Represents the context information needed for key operations in ECDSA cryptography.
 *
 * @property key The JSON Web Key (JWK) representation of the cryptographic key.
 * @property publicKeyBytes The byte-array representation of the cryptographic key.
 * @property curveImpl The elliptic curve implementation used for cryptographic operations.
 * @property algImpl The cryptography algorithm identifier tied to a specific digest.
 */
@Suppress("NON_EXPORTABLE_TYPE")
data class RSADerKmpKeyInfoContext(
    val key: Jwk, val publicKeyBytes: ByteArray, val privateKeyBytes: ByteArray?, val algImpl: CryptographyAlgorithmId<Digest>
)


/**
 * Converts an ECDSA public key represented as an `JwkType` to its raw byte array format.
 * The resulting byte array contains the uncompressed public key coordinates.
 *
 * @param key The JSON Web Key (JWK) containing the ECDSA public key information.
 * @return The raw byte array format of the ECDSA public key.
 */
@OptIn(ExperimentalStdlibApi::class)
fun toDerRSAPublicKeyBytes(key: JwkType): ByteArray {
    return JsonWebKey.deserialize(Jwk.from(key).toJsonString()).getOrThrow().toCryptoPublicKey().getOrThrow().encodeToDer()
}


/**
 * Converts the private key component of a given JwkType to a raw ECDSA private key byte array.
 *
 * @param key The input JWK from which the private key bytes will be extracted. The key must have
 *            a non-null 'd' parameter, which represents the private or secret part of the cryptographic key.
 * @return A byte array representing the JWK RSA private key.
 * @throws IllegalArgumentException if the 'd' parameter of the input key is null, indicating that
 *         the key is not a private key JWK.
 */
fun toDerRSAPrivateKeyBytes(provider: CryptographyProvider = CryptographyProvider.Default, key: KeyType): ByteArray {

    require(key.kty == JwaKeyType.RSA) { "Key type (converted to JWA) ${key.kty} is not of type ${JwaKeyType.RSA}" }
    require(key.d != null) { "Cannot convert to private key bytes if the input key is not a private key jwk (missing d param)" }

    val jwk = CoseJoseKeyMappingService.toJoseJwk(key)
    val signumPrivateKey = jwk.toSignumPrivateKey()
    return signumPrivateKey.encodeToDer()

}

/**
 * Converts a resolved ECDSA public key to its raw byte representation.
 *
 * @param provider The cryptographic provider to use for the conversion. Defaults to `CryptographyProvider.Default`.
 * @param keyInfo An instance of `ResolvedKeyInfo` containing the resolved RSA key information.
 * @return An instance of `RSA.PublicKey` decoded from the raw byte representation.
 */
@DelicateCryptographyApi
suspend fun toDerRSAPublicKey(provider: CryptographyProvider = CryptographyProvider.Default, keyInfo: ResolvedKeyInfoType<*>): RSA.PublicKey {
    val jwkInfo = CoseJoseKeyMappingService.toResolvedJwkKeyInfo(keyInfo)
    val pubKeyBytes = toDerEcdsaPublicKeyBytes(jwkInfo.key)
    val digest = resolveRSAKmpDigest(SignatureAlgorithm.fromJose(jwkInfo.key.alg))
    val rsa = provider.get(RSA.RAW)
    return rsa.publicKeyDecoder(digest).decodeFromByteArray(RSA.PublicKey.Format.DER, pubKeyBytes)
}


/**
 * Converts the given resolved ECDSA key information to a raw ECDSA private key.
 *
 * @param provider The cryptography provider to use for the conversion. Defaults to CryptographyProvider.Default.
 * @param keyInfo The resolved key information of the RSA key to be converted.
 * @return The decoded raw RSA private key.
 */
@OptIn(DelicateCryptographyApi::class)
suspend fun toDerRSAPrivateKey(provider: CryptographyProvider = CryptographyProvider.Default, keyInfo: ResolvedKeyInfoType<*>): RSA.PrivateKey {
    val jwkInfo = CoseJoseKeyMappingService.toResolvedJwkKeyInfo(keyInfo)
    val digest = resolveRSAKmpDigest(SignatureAlgorithm.fromJose(jwkInfo.key.alg))
    val privKeyBytes = toDerRSAPrivateKeyBytes(provider, jwkInfo.key)
    val rsa = provider.get(RSA.RAW)
    return rsa.privateKeyDecoder(digest).decodeFromByteArray(RSA.PrivateKey.Format.DER, privKeyBytes)
}

