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
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.jose.generateJwkThumbprint
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyAlgorithmId
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.Digest
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA384
import dev.whyoleg.cryptography.algorithms.SHA512

/**
 * These functions serve as conversions and interop between our crypto implementation and external libraries:
 * - A-SIT Plus awesn1 (ASN.1 structural types)
 * - cryptography-kotlin (RSA operations)
 */

@OptIn(ExperimentalStdlibApi::class)
fun keyInfoToRSADerKmpContext(
    keyInfo: KeyInfoType<*>,
    resolver: ((info: KeyInfoType<*>) -> KeyInfoType<*>?)? = null,
): DerKmpKeyInfoContext {
    val keyInfoJwk =
        toKeyInfoJwk(
            if (keyInfo.keyVisibility === KeyVisibility.PRIVATE && keyInfo.key !== null) {
                keyInfo
            } else {
                (
                    resolver?.let { resolveFunc ->
                        resolveFunc(keyInfo)
                    } ?: keyInfo
                )
            },
        )

    val key =
        keyInfoJwk.key
            ?: throw IllegalArgumentException("Either we need to get the private key from a private key store or it needs to be passed in for this provider")

    assertValidRSAJwk(key)
    val publicKeyBytes = toDerRSAPublicKeyBytes(key)
    val privateKeyBytes =
        key.d?.let {
            try {
                toDerRSAPrivateKeyBytes(provider = CryptographyProvider.Default, key = key)
            } catch (_: Throwable) {
                null
            }
        }
    val curveImpl = null
    val algImpl = key.alg?.let { resolveRSAKmpDigest(SignatureAlgorithm.fromJose(it)) } ?: SHA256

    return DerKmpKeyInfoContext(key, publicKeyBytes, privateKeyBytes, curveImpl, algImpl)
}

fun assertValidRSAJwk(key: Jwk) {
    require(key.kty == JwaKeyType.RSA) { "Key type (converted to JWA) ${key.kty} is not of type ${JwaKeyType.RSA}" }
    val algs =
        arrayOf(
            JwaAlgorithm.PS256,
            JwaAlgorithm.PS384,
            JwaAlgorithm.PS512,
            JwaAlgorithm.RS256,
            JwaAlgorithm.RS384,
            JwaAlgorithm.RS512,
            JwaAlgorithm.RSA1_5,
        )
    require(
        key.alg == null ||
            algs.contains(key.alg),
    ) { "Key alg (converted to JWA) ${key.alg} is not supported for key type ${JwaKeyType.RSA}. Allowed ${algs.joinToString { it.value }}" }
    require(key.crv == null) { "Key crv (converted to JWA) ${key.crv} is not supported for key type ${JwaKeyType.RSA}" }
}

/**
 * Converts DER-encoded RSA key bytes to a JWK.
 */
fun convertDerRSAKeyBytesToJwk(
    publicKeyBytes: ByteArray,
    privateKeyBytes: ByteArray?,
    use: JwkUse = JwkUse.sig,
    keyOperations: Array<out KeyOperations> = arrayOf(KeyOperations.SIGN),
    alg: SignatureAlgorithm = SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
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
    return jwk.copy(kid = jwk.kid ?: kid, use = use, alg = jwk.alg ?: alg.jose, key_ops = key_ops)
}

fun resolveRSAKmpDigest(alg: SignatureAlgorithm): CryptographyAlgorithmId<Digest> =
    when (alg) {
        is SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1, SignatureAlgorithm.RSA_SHA256, SignatureAlgorithm.RSA_RAW -> SHA256
        is SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1, SignatureAlgorithm.RSA_SHA384 -> SHA384
        is SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1, SignatureAlgorithm.RSA_SHA512 -> SHA512
        else -> throw IllegalArgumentException("Algorithm $alg not supported")
    }

fun resolvePSSSaltSize(alg: SignatureAlgorithm): dev.whyoleg.cryptography.BinarySize =
    when (alg) {
        SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1 -> 32.bytes
        SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1 -> 48.bytes
        SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1 -> 64.bytes
        SignatureAlgorithm.RSA_SSA_PSS_RAW_MGF1 -> 32.bytes
        else -> throw IllegalArgumentException("Algorithm $alg is not an RSA-PSS algorithm")
    }

@Suppress("NON_EXPORTABLE_TYPE")
data class RSADerKmpKeyInfoContext(
    val key: Jwk,
    val publicKeyBytes: ByteArray,
    val privateKeyBytes: ByteArray?,
    val algImpl: CryptographyAlgorithmId<Digest>,
)

/**
 * Converts an RSA public key JWK to SubjectPublicKeyInfo DER bytes.
 */
@OptIn(ExperimentalStdlibApi::class)
fun toDerRSAPublicKeyBytes(key: JwkType): ByteArray {
    val jwk = Jwk.from(key)
    return jwk.toSubjectPublicKeyInfo().encodeToTlv().derEncoded
}

/**
 * Converts an RSA private key JWK to PKCS#8 DER bytes.
 */
fun toDerRSAPrivateKeyBytes(
    provider: CryptographyProvider = CryptographyProvider.Default,
    key: KeyType,
): ByteArray {
    require(key.kty == JwaKeyType.RSA) { "Key type (converted to JWA) ${key.kty} is not of type ${JwaKeyType.RSA}" }
    require(key.d != null) { "Cannot convert to private key bytes if the input key is not a private key jwk (missing d param)" }

    val jwk = CoseJoseKeyMappingService.toJoseJwk(key)
    return jwk.toPkcs8PrivateKeyInfo().encodeToTlv().derEncoded
}

@DelicateCryptographyApi
suspend fun toDerRSAPublicKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    keyInfo: ResolvedKeyInfoType<*>,
): RSA.PublicKey {
    val jwkInfo = CoseJoseKeyMappingService.toResolvedJwkKeyInfo(keyInfo)
    val pubKeyBytes = toDerRSAPublicKeyBytes(jwkInfo.key)
    val digest = resolveRSAKmpDigest(SignatureAlgorithm.fromJose(jwkInfo.key.alg))
    val rsa = provider.get(RSA.RAW)
    return rsa.publicKeyDecoder(digest).decodeFromByteArray(RSA.PublicKey.Format.DER, pubKeyBytes)
}

@OptIn(DelicateCryptographyApi::class)
suspend fun toDerRSAPrivateKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    keyInfo: ResolvedKeyInfoType<*>,
): RSA.PrivateKey<*> {
    val jwkInfo = CoseJoseKeyMappingService.toResolvedJwkKeyInfo(keyInfo)
    val digest = resolveRSAKmpDigest(SignatureAlgorithm.fromJose(jwkInfo.key.alg))
    val privKeyBytes = toDerRSAPrivateKeyBytes(provider, jwkInfo.key)
    val rsa = provider.get(RSA.RAW)
    return rsa.privateKeyDecoder(digest).decodeFromByteArray(RSA.PrivateKey.Format.DER, privKeyBytes)
}
