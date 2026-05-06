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

package com.sphereon.crypto.kms.provider.software

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.interop.isOkpCurve
import com.sphereon.crypto.core.interop.resolveEcdsaKmpCurve
import com.sphereon.crypto.core.interop.toEcdhPrivateKey
import com.sphereon.crypto.core.interop.toEcdhPublicKey
import com.sphereon.crypto.core.interop.toRsaOaepPrivateKey
import com.sphereon.crypto.core.interop.toRsaOaepPublicKey
import com.sphereon.crypto.core.interop.toXdhPrivateKey
import com.sphereon.crypto.core.interop.toXdhPublicKey
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.kms.EncryptionResult
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA1
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA384
import dev.whyoleg.cryptography.algorithms.SHA512
import dev.whyoleg.cryptography.random.CryptographyRandom

private const val AES_128_KEY_SIZE = 16
private const val AES_192_KEY_SIZE = 24
private const val AES_256_KEY_SIZE = 32
private const val AES_GCM_IV_SIZE = 12
private const val AES_GCM_TAG_SIZE = 16
private const val AES_GCM_TAG_BITS = 128
private const val AES_KW_BLOCK_SIZE = 8
private const val AES_KW_MIN_WRAP_SIZE = 16
private const val AES_KW_MIN_WRAPPED_SIZE = 24
private const val AES_KW_TOP_BYTE_INDEX = 7
private const val BYTE_MASK = 0xFF
private const val BYTE_SHIFT = 8

/*
 * Multiplatform encryption implementation using dev.whyoleg.cryptography library.
 * No platform-specific code needed - whyoleg handles all platform differences.
 */

/**
 * Helper function to extract raw key bytes from KeyInfo.
 * Handles JWK format (base64url-encoded k parameter) and other formats.
 */
private fun extractRawKeyBytes(keyInfo: KeyInfoType<*>): ByteArray? {
    val key = keyInfo.key ?: return null
    return when (key) {
        is JwkType -> {
            // For JWK symmetric keys, decode the k parameter
            key.k?.decodeFrom(Encoding.BASE64URL)
        }

        else -> {
            // For other key types, we'd need additional handling
            null
        }
    }
}

/**
 * Encrypts content using AEAD algorithms (AES-GCM).
 */
@OptIn(DelicateCryptographyApi::class)
internal suspend fun encryptWithNativeKey(
    keyInfo: KeyInfoType<*>,
    plaintext: ByteArray,
    algorithm: String,
    additionalAuthenticatedData: ByteArray?,
): EncryptionResult? =
    try {
        when (algorithm) {
            "A128GCM", "A192GCM", "A256GCM" -> {
                encryptAesGcm(keyInfo, plaintext, algorithm, additionalAuthenticatedData)
            }

            "A128CBC-HS256", "A192CBC-HS384", "A256CBC-HS512" -> {
                throw UnsupportedOperationException("AES-CBC-HMAC not yet implemented")
            }

            else -> {
                null
            }
        }
    } catch (expected: Exception) {
        throw IllegalArgumentException("Encryption failed for algorithm $algorithm: ${expected.message}", expected)
    }

@OptIn(DelicateCryptographyApi::class)
private suspend fun encryptAesGcm(
    keyInfo: KeyInfoType<*>,
    plaintext: ByteArray,
    algorithm: String,
    aad: ByteArray?,
): EncryptionResult {
    val keyBytes = extractRawKeyBytes(keyInfo) ?: throw IllegalArgumentException("Key must have raw bytes for encryption")

    val expectedKeySize =
        when (algorithm) {
            "A128GCM" -> AES_128_KEY_SIZE
            "A192GCM" -> AES_192_KEY_SIZE
            "A256GCM" -> AES_256_KEY_SIZE
            else -> throw IllegalArgumentException("Unsupported AES-GCM algorithm: $algorithm")
        }

    require(keyBytes.size == expectedKeySize) { "Key size ${keyBytes.size} does not match expected size $expectedKeySize for $algorithm" }

    val provider = CryptographyProvider.Default
    val aesKey = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
    val iv = CryptographyRandom.nextBytes(AES_GCM_IV_SIZE)
    val cipher = aesKey.cipher(tagSize = AES_GCM_TAG_BITS.bits)
    val combined = cipher.encryptWithIv(iv = iv, plaintext = plaintext, associatedData = aad)

    val ciphertext = combined.copyOfRange(0, combined.size - AES_GCM_TAG_SIZE)
    val authTag = combined.copyOfRange(combined.size - AES_GCM_TAG_SIZE, combined.size)

    return EncryptionResult(ciphertext = ciphertext, iv = iv, authTag = authTag)
}

/**
 * Decrypts content using AEAD algorithms (AES-GCM).
 */
@OptIn(DelicateCryptographyApi::class)
internal suspend fun decryptWithNativeKey(
    keyInfo: KeyInfoType<*>,
    ciphertext: ByteArray,
    algorithm: String,
    iv: ByteArray,
    authTag: ByteArray,
    additionalAuthenticatedData: ByteArray?,
): ByteArray? =
    try {
        when (algorithm) {
            "A128GCM", "A192GCM", "A256GCM" -> {
                decryptAesGcm(keyInfo, ciphertext, algorithm, iv, authTag, additionalAuthenticatedData)
            }

            "A128CBC-HS256", "A192CBC-HS384", "A256CBC-HS512" -> {
                throw UnsupportedOperationException("AES-CBC-HMAC not yet implemented")
            }

            else -> {
                null
            }
        }
    } catch (expected: Exception) {
        throw IllegalStateException("Decryption failed for algorithm $algorithm: ${expected.message}", expected)
    }

@OptIn(DelicateCryptographyApi::class)
private suspend fun decryptAesGcm(
    keyInfo: KeyInfoType<*>,
    ciphertext: ByteArray,
    algorithm: String,
    iv: ByteArray,
    authTag: ByteArray,
    aad: ByteArray?,
): ByteArray {
    val keyBytes = extractRawKeyBytes(keyInfo) ?: throw IllegalArgumentException("Key must have raw bytes for decryption")

    val expectedKeySize =
        when (algorithm) {
            "A128GCM" -> AES_128_KEY_SIZE
            "A192GCM" -> AES_192_KEY_SIZE
            "A256GCM" -> AES_256_KEY_SIZE
            else -> throw IllegalArgumentException("Unsupported AES-GCM algorithm: $algorithm")
        }

    require(keyBytes.size == expectedKeySize) { "Key size ${keyBytes.size} does not match expected size $expectedKeySize for $algorithm" }

    val provider = CryptographyProvider.Default
    val aesKey = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
    val combined = ciphertext + authTag
    val cipher = aesKey.cipher(tagSize = AES_GCM_TAG_BITS.bits)

    return cipher.decryptWithIv(iv = iv, ciphertext = combined, associatedData = aad)
}

/**
 * Wraps a key using RSA-OAEP or other key wrapping algorithms.
 */
@OptIn(DelicateCryptographyApi::class)
internal suspend fun wrapKeyWithNativeKey(
    wrappingKeyInfo: KeyInfoType<*>,
    keyToWrap: ByteArray,
    algorithm: String,
): ByteArray? =
    try {
        when {
            algorithm.startsWith("RSA-OAEP") -> {
                wrapKeyRsaOaep(wrappingKeyInfo, keyToWrap, algorithm)
            }

            algorithm == "RSA1_5" -> {
                throw UnsupportedOperationException("RSA1_5 not yet implemented (deprecated)")
            }

            algorithm == "A128KW" || algorithm == "A192KW" || algorithm == "A256KW" -> {
                wrapKeyAesKw(wrappingKeyInfo, keyToWrap, algorithm)
            }

            algorithm == "dir" -> {
                // Direct encryption - no key wrapping needed
                keyToWrap
            }

            else -> {
                null
            }
        }
    } catch (expected: Exception) {
        throw IllegalArgumentException("Key wrapping failed for algorithm $algorithm: ${expected.message}", expected)
    }

@OptIn(DelicateCryptographyApi::class)
private suspend fun wrapKeyRsaOaep(
    wrappingKeyInfo: KeyInfoType<*>,
    keyToWrap: ByteArray,
    algorithm: String,
): ByteArray {
    val jwk =
        wrappingKeyInfo.key as? JwkType
            ?: throw IllegalArgumentException("Wrapping key must be a JWK")

    val digest =
        when (algorithm) {
            "RSA-OAEP" -> SHA1
            "RSA-OAEP-256" -> SHA256
            "RSA-OAEP-384" -> SHA384
            "RSA-OAEP-512" -> SHA512
            else -> throw IllegalArgumentException("Unsupported RSA-OAEP variant: $algorithm")
        }

    val publicKey = jwk.toRsaOaepPublicKey(digest = digest)
    val encryptor = publicKey.encryptor()
    return encryptor.encrypt(keyToWrap)
}

/**
 * Unwraps a key using RSA-OAEP or other key unwrapping algorithms.
 */
@OptIn(DelicateCryptographyApi::class)
internal suspend fun unwrapKeyWithNativeKey(
    unwrappingKeyInfo: KeyInfoType<*>,
    wrappedKey: ByteArray,
    algorithm: String,
): ByteArray? =
    try {
        when {
            algorithm.startsWith("RSA-OAEP") -> {
                unwrapKeyRsaOaep(unwrappingKeyInfo, wrappedKey, algorithm)
            }

            algorithm == "RSA1_5" -> {
                throw UnsupportedOperationException("RSA1_5 not yet implemented (deprecated)")
            }

            algorithm == "A128KW" || algorithm == "A192KW" || algorithm == "A256KW" -> {
                unwrapKeyAesKw(unwrappingKeyInfo, wrappedKey, algorithm)
            }

            algorithm == "dir" -> {
                // Direct encryption - no key unwrapping needed
                wrappedKey
            }

            else -> {
                null
            }
        }
    } catch (expected: Exception) {
        throw IllegalStateException("Key unwrapping failed for algorithm $algorithm: ${expected.message}", expected)
    }

@OptIn(DelicateCryptographyApi::class)
private suspend fun unwrapKeyRsaOaep(
    unwrappingKeyInfo: KeyInfoType<*>,
    wrappedKey: ByteArray,
    algorithm: String,
): ByteArray {
    val jwk =
        unwrappingKeyInfo.key as? JwkType
            ?: throw IllegalArgumentException("Unwrapping key must be a JWK")

    val digest =
        when (algorithm) {
            "RSA-OAEP" -> SHA1
            "RSA-OAEP-256" -> SHA256
            "RSA-OAEP-384" -> SHA384
            "RSA-OAEP-512" -> SHA512
            else -> throw IllegalArgumentException("Unsupported RSA-OAEP variant: $algorithm")
        }

    val privateKey = jwk.toRsaOaepPrivateKey(digest = digest)
    val decryptor = privateKey.decryptor()
    return decryptor.decrypt(wrappedKey)
}

// ============================================================================
// AES Key Wrap (RFC 3394) Implementation
// ============================================================================

/**
 * Default Initial Value for AES Key Wrap (RFC 3394 Section 2.2.3.1)
 * This is used for integrity checking during unwrap.
 */
internal val AES_KW_DEFAULT_IV =
    byteArrayOf(
        0xA6.toByte(),
        0xA6.toByte(),
        0xA6.toByte(),
        0xA6.toByte(),
        0xA6.toByte(),
        0xA6.toByte(),
        0xA6.toByte(),
        0xA6.toByte(),
    )

/**
 * Wraps a key using AES Key Wrap (RFC 3394).
 *
 * @param wrappingKeyInfo The key encryption key (KEK) - must be a symmetric key
 * @param keyToWrap The key to wrap (must be a multiple of 8 bytes, minimum 16 bytes)
 * @param algorithm The algorithm identifier (A128KW, A192KW, A256KW)
 * @return The wrapped key (8 bytes larger than input due to integrity check value)
 */
@OptIn(DelicateCryptographyApi::class)
private suspend fun wrapKeyAesKw(
    wrappingKeyInfo: KeyInfoType<*>,
    keyToWrap: ByteArray,
    algorithm: String,
): ByteArray {
    val keyBytes =
        extractRawKeyBytes(wrappingKeyInfo)
            ?: throw IllegalArgumentException("Wrapping key must have raw bytes for AES-KW")

    // Validate KEK size matches algorithm
    val expectedKeySize =
        when (algorithm) {
            "A128KW" -> AES_128_KEY_SIZE
            "A192KW" -> AES_192_KEY_SIZE
            "A256KW" -> AES_256_KEY_SIZE
            else -> throw IllegalArgumentException("Unsupported AES-KW algorithm: $algorithm")
        }

    require(keyBytes.size == expectedKeySize) {
        "KEK size ${keyBytes.size} does not match expected size $expectedKeySize for $algorithm"
    }

    // Validate key to wrap (must be multiple of 8 bytes, minimum 16 bytes per RFC 3394)
    require(keyToWrap.size >= AES_KW_MIN_WRAP_SIZE) { "Key to wrap must be at least $AES_KW_MIN_WRAP_SIZE bytes, got ${keyToWrap.size}" }
    require(keyToWrap.size % AES_KW_BLOCK_SIZE == 0) { "Key to wrap must be a multiple of $AES_KW_BLOCK_SIZE bytes, got ${keyToWrap.size}" }

    return aesKeyWrap(keyBytes, keyToWrap)
}

/**
 * Unwraps a key using AES Key Wrap (RFC 3394).
 *
 * @param unwrappingKeyInfo The key encryption key (KEK) - must be a symmetric key
 * @param wrappedKey The wrapped key (must be at least 24 bytes: 8-byte IV + 16-byte minimum key)
 * @param algorithm The algorithm identifier (A128KW, A192KW, A256KW)
 * @return The unwrapped key
 * @throws IllegalStateException If integrity check fails (wrong key or corrupted data)
 */
@OptIn(DelicateCryptographyApi::class)
private suspend fun unwrapKeyAesKw(
    unwrappingKeyInfo: KeyInfoType<*>,
    wrappedKey: ByteArray,
    algorithm: String,
): ByteArray {
    val keyBytes =
        extractRawKeyBytes(unwrappingKeyInfo)
            ?: throw IllegalArgumentException("Unwrapping key must have raw bytes for AES-KW")

    // Validate KEK size matches algorithm
    val expectedKeySize =
        when (algorithm) {
            "A128KW" -> AES_128_KEY_SIZE
            "A192KW" -> AES_192_KEY_SIZE
            "A256KW" -> AES_256_KEY_SIZE
            else -> throw IllegalArgumentException("Unsupported AES-KW algorithm: $algorithm")
        }

    require(keyBytes.size == expectedKeySize) {
        "KEK size ${keyBytes.size} does not match expected size $expectedKeySize for $algorithm"
    }

    // Validate wrapped key size (must be multiple of 8 bytes, minimum 24 bytes)
    require(wrappedKey.size >= AES_KW_MIN_WRAPPED_SIZE) { "Wrapped key must be at least $AES_KW_MIN_WRAPPED_SIZE bytes, got ${wrappedKey.size}" }
    require(wrappedKey.size % AES_KW_BLOCK_SIZE == 0) { "Wrapped key must be a multiple of $AES_KW_BLOCK_SIZE bytes, got ${wrappedKey.size}" }

    return aesKeyUnwrap(keyBytes, wrappedKey)
}

/**
 * AES Key Wrap algorithm as per RFC 3394.
 * Platform-specific: uses AES-ECB on JVM/Apple, WebCrypto AES-KW on JS/wasmJs.
 */
internal expect suspend fun aesKeyWrap(
    kek: ByteArray,
    plaintext: ByteArray,
): ByteArray

/**
 * AES Key Unwrap algorithm as per RFC 3394.
 * Platform-specific: uses AES-ECB on JVM/Apple, WebCrypto AES-KW on JS/wasmJs.
 *
 * @throws IllegalStateException If integrity check fails (wrong key or corrupted data)
 */
internal expect suspend fun aesKeyUnwrap(
    kek: ByteArray,
    ciphertext: ByteArray,
): ByteArray

/**
 * XORs a byte array with a counter value (big-endian).
 * This is used for the t value in RFC 3394.
 */
internal fun xorWithCounter(
    data: ByteArray,
    counter: Long,
) {
    // XOR the counter into the last bytes of the 8-byte block (big-endian)
    var t = counter
    for (i in AES_KW_TOP_BYTE_INDEX downTo 0) {
        data[i] = (data[i].toInt() xor (t and BYTE_MASK.toLong()).toInt()).toByte()
        t = t shr BYTE_SHIFT
        if (t == 0L) {
            break
        }
    }
}

/**
 * Performs ECDH key agreement to derive a shared secret.
 *
 * This function takes a local private key and a remote public key, and computes
 * the shared secret using Elliptic Curve Diffie-Hellman key agreement.
 *
 * On iOS, if the private key doesn't have a 'd' parameter (because it's stored in
 * the iOS Keychain), native ECDH via SecKeyCreateSharedSecret will be attempted
 * using the key alias.
 *
 * @param privateKeyInfo The local party's private key (must be an EC key with kty=EC)
 * @param publicKeyInfo The remote party's public key (must be an EC key with kty=EC and x,y parameters)
 * @param algorithm The key agreement algorithm identifier (e.g., "ECDH-ES", "ECDH-ES+A128KW")
 * @return The derived shared secret as a byte array
 * @throws IllegalArgumentException If keys are not valid EC keys or curves don't match
 */
@OptIn(DelicateCryptographyApi::class)
internal suspend fun performKeyAgreementWithNativeKey(
    privateKeyInfo: KeyInfoType<*>,
    publicKeyInfo: KeyInfoType<*>,
    algorithm: String,
): ByteArray {
    // Validate algorithm is ECDH-based
    require(algorithm.startsWith("ECDH")) {
        "Algorithm must be ECDH-based, got: $algorithm"
    }

    // Extract keys as JWK
    val privateJwk =
        when (val key = privateKeyInfo.key) {
            is JwkType -> key
            else -> throw IllegalArgumentException("Private key must be a JWK for ECDH key agreement")
        }

    val publicJwk =
        when (val key = publicKeyInfo.key) {
            is JwkType -> key
            else -> throw IllegalArgumentException("Public key must be a JWK for ECDH key agreement")
        }

    // Validate key types — accept both EC (Weierstrass: P-256/P-384/P-521) and
    // OKP (Montgomery: X25519/X448). Mixing types is rejected; mixing curves
    // within a type is rejected below.
    require(privateJwk.kty == publicJwk.kty) {
        "Private and public key types must match. Private kty: ${privateJwk.kty}, Public kty: ${publicJwk.kty}"
    }
    require(privateJwk.kty == JwaKeyType.EC || privateJwk.kty == JwaKeyType.OKP) {
        "Key agreement requires EC (P-256/P-384/P-521) or OKP (X25519/X448) keys, got: ${privateJwk.kty}"
    }

    // Get curves and validate they match
    val privateCurve = privateJwk.crv?.let { Curve.fromJose(it) } ?: Curve.P_256
    val publicCurve = publicJwk.crv?.let { Curve.fromJose(it) } ?: Curve.P_256
    require(privateCurve == publicCurve) {
        "Private and public key curves must match. Private: $privateCurve, Public: $publicCurve"
    }

    return when (privateJwk.kty) {
        JwaKeyType.OKP -> performXdhKeyAgreement(privateJwk, publicJwk, privateCurve)
        else -> performEcdhKeyAgreement(privateKeyInfo, privateJwk, publicJwk, privateCurve)
    }
}

/**
 * Diffie-Hellman key agreement on Weierstrass curves (ECDH for
 * P-256/P-384/P-521). Falls back to the iOS keychain when the JWK lacks `d`
 * but a private key is held by the device's secure enclave.
 */
@OptIn(DelicateCryptographyApi::class)
private suspend fun performEcdhKeyAgreement(
    privateKeyInfo: KeyInfoType<*>,
    privateJwk: JwkType,
    publicJwk: JwkType,
    privateCurve: Curve,
): ByteArray {
    require(publicJwk.x != null && publicJwk.y != null) {
        "EC public key must have 'x' and 'y' parameters for key agreement"
    }
    if (privateJwk.d == null) {
        val alias = privateKeyInfo.alias
        if (alias != null) {
            val nativeResult =
                com.sphereon.crypto.kms
                    .performNativeKeychainEcdh(alias, publicJwk)
            if (nativeResult != null) {
                return nativeResult
            }
        }
        throw IllegalArgumentException(
            "EC private key must have 'd' parameter for key agreement, or be a native keychain key. " +
                "Alias: ${alias ?: "not set"}",
        )
    }
    val curve = resolveEcdsaKmpCurve(privateCurve)
    val ecdhPrivateKey = privateJwk.toEcdhPrivateKey(curve = curve)
    val ecdhPublicKey = publicJwk.toEcdhPublicKey(curve = curve)
    return ecdhPrivateKey.sharedSecretGenerator().generateSharedSecretToByteArray(ecdhPublicKey)
}

/**
 * Diffie-Hellman key agreement on Montgomery curves (XDH for X25519 / X448),
 * RFC 7748 §5. The IDK [Jwk]s are decoded via the OKP codec helpers
 * (`toXdhPrivateKey` / `toXdhPublicKey`) which wrap cryptography-kotlin's
 * `XDH` algorithm.
 */
@OptIn(DelicateCryptographyApi::class)
private suspend fun performXdhKeyAgreement(
    privateJwk: JwkType,
    publicJwk: JwkType,
    privateCurve: Curve,
): ByteArray {
    require(publicJwk.x != null) { "OKP public key must have 'x' parameter for key agreement" }
    require(privateJwk.d != null) { "OKP private key must have 'd' parameter for key agreement" }
    require(isOkpCurve(privateCurve)) { "Curve $privateCurve is not an OKP curve" }
    val privateAsJwk =
        com.sphereon.crypto.core.jose.Jwk
            .from(privateJwk)
    val publicAsJwk =
        com.sphereon.crypto.core.jose.Jwk
            .from(publicJwk)
    val xdhPrivateKey = privateAsJwk.toXdhPrivateKey(curve = privateCurve)
    val xdhPublicKey = publicAsJwk.toXdhPublicKey(curve = privateCurve)
    return xdhPrivateKey.sharedSecretGenerator().generateSharedSecretToByteArray(xdhPublicKey)
}

/**
 * Generates an ephemeral EC key pair for ECDH key agreement.
 *
 * Returns the key pair as DER-encoded bytes for both public and private keys.
 *
 * @param curve The elliptic curve to use (P-256, P-384, or P-521)
 * @return Pair of (publicKeyDer, privateKeyDer) byte arrays
 */
@OptIn(DelicateCryptographyApi::class)
internal suspend fun generateEphemeralEcdhKeyPair(curve: EC.Curve): Pair<ByteArray, ByteArray> {
    val provider = CryptographyProvider.Default
    val ecdh = provider.get(ECDH)

    // Generate key pair
    val keyPair = ecdh.keyPairGenerator(curve).generateKey()

    // Encode to DER format
    val publicKeyDer = keyPair.publicKey.encodeToByteArray(EC.PublicKey.Format.DER)
    val privateKeyDer = keyPair.privateKey.encodeToByteArray(EC.PrivateKey.Format.DER)

    return Pair(publicKeyDer, privateKeyDer)
}
