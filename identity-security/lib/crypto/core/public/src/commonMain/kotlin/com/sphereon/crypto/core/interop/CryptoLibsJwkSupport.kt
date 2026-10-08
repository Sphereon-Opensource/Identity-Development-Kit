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

import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.RSA
import kotlinx.serialization.json.Json

/**
 * Lenient JSON parser for decoding JWK from cryptography-kotlin.
 * Ignores unknown keys that cryptography-kotlin might include (e.g. "ext" from WebCrypto).
 */
private val jwkLenientJson =
    Json {
        ignoreUnknownKeys = true
    }

// ============================================================================
// Encoding: cryptography-kotlin key → Sphereon Jwk
// ============================================================================

/**
 * Encodes an ECDSA public key to a Sphereon [Jwk] using cryptography-kotlin's native JWK format.
 */
suspend fun ECDSA.PublicKey.toSphereonJwk(): Jwk {
    val jwkBytes = encodeToByteArray(EC.PublicKey.Format.JWK)
    return jwkLenientJson.decodeFromString(Jwk.serializer(), jwkBytes.decodeToString())
}

/**
 * Encodes an ECDSA private key to a Sphereon [Jwk] using cryptography-kotlin's native JWK format.
 */
suspend fun ECDSA.PrivateKey.toSphereonJwk(): Jwk {
    val jwkBytes = encodeToByteArray(EC.PrivateKey.Format.JWK)
    return jwkLenientJson.decodeFromString(Jwk.serializer(), jwkBytes.decodeToString())
}

/**
 * Encodes an ECDH public key to a Sphereon [Jwk] using cryptography-kotlin's native JWK format.
 */
suspend fun ECDH.PublicKey.toSphereonJwk(): Jwk {
    val jwkBytes = encodeToByteArray(EC.PublicKey.Format.JWK)
    return jwkLenientJson.decodeFromString(Jwk.serializer(), jwkBytes.decodeToString())
}

/**
 * Encodes an ECDH private key to a Sphereon [Jwk] using cryptography-kotlin's native JWK format.
 */
suspend fun ECDH.PrivateKey.toSphereonJwk(): Jwk {
    val jwkBytes = encodeToByteArray(EC.PrivateKey.Format.JWK)
    return jwkLenientJson.decodeFromString(Jwk.serializer(), jwkBytes.decodeToString())
}

/**
 * Encodes an RSA PSS public key to a Sphereon [Jwk] using cryptography-kotlin's native JWK format.
 */
suspend fun RSA.PSS.PublicKey.toSphereonJwk(): Jwk {
    val jwkBytes = encodeToByteArray(RSA.PublicKey.Format.JWK)
    return jwkLenientJson.decodeFromString(Jwk.serializer(), jwkBytes.decodeToString())
}

/**
 * Encodes an RSA PSS private key to a Sphereon [Jwk] using cryptography-kotlin's native JWK format.
 */
suspend fun RSA.PSS.PrivateKey.toSphereonJwk(): Jwk {
    val jwkBytes = encodeToByteArray(RSA.PrivateKey.Format.JWK)
    return jwkLenientJson.decodeFromString(Jwk.serializer(), jwkBytes.decodeToString())
}

/**
 * Encodes an RSA PKCS1 public key to a Sphereon [Jwk] using cryptography-kotlin's native JWK format.
 */
suspend fun RSA.PKCS1.PublicKey.toSphereonJwk(): Jwk {
    val jwkBytes = encodeToByteArray(RSA.PublicKey.Format.JWK)
    return jwkLenientJson.decodeFromString(Jwk.serializer(), jwkBytes.decodeToString())
}

/**
 * Encodes an RSA PKCS1 private key to a Sphereon [Jwk] using cryptography-kotlin's native JWK format.
 */
suspend fun RSA.PKCS1.PrivateKey.toSphereonJwk(): Jwk {
    val jwkBytes = encodeToByteArray(RSA.PrivateKey.Format.JWK)
    return jwkLenientJson.decodeFromString(Jwk.serializer(), jwkBytes.decodeToString())
}

// ============================================================================
// Decoding: Sphereon Jwk → cryptography-kotlin key
// ============================================================================

/**
 * Serializes a [JwkType] to JSON bytes suitable for cryptography-kotlin's JWK format decoder.
 *
 * The `alg`, `key_ops`, and `use` fields are stripped because cryptography-kotlin (backed by
 * WebCrypto on JS/wasmJs) validates them against the target algorithm and key usages. For example,
 * a key with `alg=RS256` would be rejected by an OAEP decoder, and a key with `key_ops=["sign"]`
 * would be rejected when imported for verification or encryption. Since these are usage hints,
 * not key material, it is safe to omit them.
 */
private fun JwkType.toJwkJsonBytes(): ByteArray {
    // Strip alg, key_ops, and use to avoid usage/algorithm validation failures in cryptography-kotlin
    return Jwk
        .from(this)
        .copy(alg = null, key_ops = null, use = null)
        .toJsonString()
        .encodeToByteArray()
}

/**
 * Serializes a [JwkType] to JSON bytes suitable for importing as a **public** key in
 * cryptography-kotlin.
 *
 * In addition to stripping `alg`, `key_ops`, and `use` (see [toJwkJsonBytes]), this also removes
 * private key fields (`d`, `p`, `q`, `dp`, `dq`, `qi`). This is critical for the WebCrypto
 * provider on JS/Node: when a JWK contains the `d` field, WebCrypto classifies it as a private
 * key and rejects public-key usages (e.g., `["verify"]` for ECDSA, `["encrypt"]` for RSA-OAEP).
 * Stripping private fields ensures the JWK is treated as a public key regardless of what was
 * passed in.
 */
private fun JwkType.toPublicJwkJsonBytes(): ByteArray =
    Jwk
        .from(this)
        .copy(
            alg = null,
            key_ops = null,
            use = null,
            d = null,
            p = null,
            q = null,
            dP = null,
            dQ = null,
            qInv = null,
        ).toJsonString()
        .encodeToByteArray()

/**
 * Decodes a Sphereon [JwkType] into an ECDSA public key.
 */
suspend fun JwkType.toEcdsaPublicKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    curve: EC.Curve,
): ECDSA.PublicKey = provider.get(ECDSA).publicKeyDecoder(curve).decodeFromByteArray(EC.PublicKey.Format.JWK, toPublicJwkJsonBytes())

/**
 * Decodes a Sphereon [JwkType] into an ECDSA private key.
 */
suspend fun JwkType.toEcdsaPrivateKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    curve: EC.Curve,
): ECDSA.PrivateKey = provider.get(ECDSA).privateKeyDecoder(curve).decodeFromByteArray(EC.PrivateKey.Format.JWK, toJwkJsonBytes())

/**
 * Decodes a Sphereon [JwkType] into an ECDH public key.
 */
suspend fun JwkType.toEcdhPublicKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    curve: EC.Curve,
): ECDH.PublicKey = provider.get(ECDH).publicKeyDecoder(curve).decodeFromByteArray(EC.PublicKey.Format.JWK, toPublicJwkJsonBytes())

/**
 * Decodes a Sphereon [JwkType] into an ECDH private key.
 */
suspend fun JwkType.toEcdhPrivateKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    curve: EC.Curve,
): ECDH.PrivateKey = provider.get(ECDH).privateKeyDecoder(curve).decodeFromByteArray(EC.PrivateKey.Format.JWK, toJwkJsonBytes())

/**
 * Decodes a Sphereon [JwkType] into an RSA PSS public key.
 */
suspend fun JwkType.toRsaPssPublicKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    digest: dev.whyoleg.cryptography.CryptographyAlgorithmId<dev.whyoleg.cryptography.algorithms.Digest>,
): RSA.PSS.PublicKey = provider.get(RSA.PSS).publicKeyDecoder(digest).decodeFromByteArray(RSA.PublicKey.Format.JWK, toPublicJwkJsonBytes())

/**
 * Decodes a Sphereon [JwkType] into an RSA PSS private key.
 */
suspend fun JwkType.toRsaPssPrivateKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    digest: dev.whyoleg.cryptography.CryptographyAlgorithmId<dev.whyoleg.cryptography.algorithms.Digest>,
): RSA.PSS.PrivateKey = provider.get(RSA.PSS).privateKeyDecoder(digest).decodeFromByteArray(RSA.PrivateKey.Format.JWK, toJwkJsonBytes())

/**
 * Decodes a Sphereon [JwkType] into an RSA PKCS1 public key.
 */
suspend fun JwkType.toRsaPkcs1PublicKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    digest: dev.whyoleg.cryptography.CryptographyAlgorithmId<dev.whyoleg.cryptography.algorithms.Digest>,
): RSA.PKCS1.PublicKey = provider.get(RSA.PKCS1).publicKeyDecoder(digest).decodeFromByteArray(RSA.PublicKey.Format.JWK, toPublicJwkJsonBytes())

/**
 * Decodes a Sphereon [JwkType] into an RSA PKCS1 private key.
 */
suspend fun JwkType.toRsaPkcs1PrivateKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    digest: dev.whyoleg.cryptography.CryptographyAlgorithmId<dev.whyoleg.cryptography.algorithms.Digest>,
): RSA.PKCS1.PrivateKey = provider.get(RSA.PKCS1).privateKeyDecoder(digest).decodeFromByteArray(RSA.PrivateKey.Format.JWK, toJwkJsonBytes())

/**
 * Decodes a Sphereon [JwkType] into an RSA OAEP public key (for encryption/key wrapping).
 */
suspend fun JwkType.toRsaOaepPublicKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    digest: dev.whyoleg.cryptography.CryptographyAlgorithmId<dev.whyoleg.cryptography.algorithms.Digest>,
): RSA.OAEP.PublicKey = provider.get(RSA.OAEP).publicKeyDecoder(digest).decodeFromByteArray(RSA.PublicKey.Format.JWK, toPublicJwkJsonBytes())

/**
 * Decodes a Sphereon [JwkType] into an RSA OAEP private key (for decryption/key unwrapping).
 */
suspend fun JwkType.toRsaOaepPrivateKey(
    provider: CryptographyProvider = CryptographyProvider.Default,
    digest: dev.whyoleg.cryptography.CryptographyAlgorithmId<dev.whyoleg.cryptography.algorithms.Digest>,
): RSA.OAEP.PrivateKey = provider.get(RSA.OAEP).privateKeyDecoder(digest).decodeFromByteArray(RSA.PrivateKey.Format.JWK, toJwkJsonBytes())
