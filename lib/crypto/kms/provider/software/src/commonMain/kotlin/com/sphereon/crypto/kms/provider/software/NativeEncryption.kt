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

package com.sphereon.crypto.kms.provider.software

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.interop.resolveEcdsaKmpCurve
import com.sphereon.crypto.core.interop.toDerEcdsaPrivateKeyBytes
import com.sphereon.crypto.core.interop.toDerEcdsaPublicKeyBytes
import com.sphereon.crypto.core.interop.toDerRSAPrivateKeyBytes
import com.sphereon.crypto.core.interop.toDerRSAPublicKeyBytes
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.jose.JwaKeyType
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

/**
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
 * Helper function to get public key in DER format from KeyInfo.
 */
private fun extractPublicKeyDer(keyInfo: KeyInfoType<*>): ByteArray? {
    val key = keyInfo.key ?: return null
    return when (key) {
        is JwkType -> {
            // Use existing utility to convert JWK to DER format for RSA
            toDerRSAPublicKeyBytes(key)
        }
        else -> null
    }
}

/**
 * Helper function to get private key in DER format from KeyInfo.
 */
private fun extractPrivateKeyDer(keyInfo: KeyInfoType<*>): ByteArray? {
    val key = keyInfo.key ?: return null
    return when (key) {
        is JwkType -> {
            // Use existing utility to convert JWK to DER format for RSA
            toDerRSAPrivateKeyBytes(key = key)
        }
        else -> null
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
    additionalAuthenticatedData: ByteArray?
): EncryptionResult? {
    return try {
        when (algorithm) {
            "A128GCM", "A192GCM", "A256GCM" -> encryptAesGcm(keyInfo, plaintext, algorithm, additionalAuthenticatedData)
            "A128CBC-HS256", "A192CBC-HS384", "A256CBC-HS512" -> {
                throw UnsupportedOperationException("AES-CBC-HMAC not yet implemented")
            }
            else -> null
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("Encryption failed for algorithm $algorithm: ${e.message}", e)
    }
}

@OptIn(DelicateCryptographyApi::class)
private suspend fun encryptAesGcm(
    keyInfo: KeyInfoType<*>,
    plaintext: ByteArray,
    algorithm: String,
    aad: ByteArray?
): EncryptionResult {
    val keyBytes = extractRawKeyBytes(keyInfo) ?: throw IllegalArgumentException("Key must have raw bytes for encryption")

    val expectedKeySize = when (algorithm) {
        "A128GCM" -> 16
        "A192GCM" -> 24
        "A256GCM" -> 32
        else -> throw IllegalArgumentException("Unsupported AES-GCM algorithm: $algorithm")
    }

    if (keyBytes.size != expectedKeySize) {
        throw IllegalArgumentException("Key size ${keyBytes.size} does not match expected size $expectedKeySize for $algorithm")
    }

    val provider = CryptographyProvider.Default
    val aesKey = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
    val iv = CryptographyRandom.nextBytes(12)
    val cipher = aesKey.cipher(tagSize = 128.bits)
    val combined = cipher.encryptWithIv(iv = iv, plaintext = plaintext, associatedData = aad)

    val tagLength = 16
    val ciphertext = combined.copyOfRange(0, combined.size - tagLength)
    val authTag = combined.copyOfRange(combined.size - tagLength, combined.size)

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
    additionalAuthenticatedData: ByteArray?
): ByteArray? {
    return try {
        when (algorithm) {
            "A128GCM", "A192GCM", "A256GCM" -> decryptAesGcm(keyInfo, ciphertext, algorithm, iv, authTag, additionalAuthenticatedData)
            "A128CBC-HS256", "A192CBC-HS384", "A256CBC-HS512" -> {
                throw UnsupportedOperationException("AES-CBC-HMAC not yet implemented")
            }
            else -> null
        }
    } catch (e: Exception) {
        throw IllegalStateException("Decryption failed for algorithm $algorithm: ${e.message}", e)
    }
}

@OptIn(DelicateCryptographyApi::class)
private suspend fun decryptAesGcm(
    keyInfo: KeyInfoType<*>,
    ciphertext: ByteArray,
    algorithm: String,
    iv: ByteArray,
    authTag: ByteArray,
    aad: ByteArray?
): ByteArray {
    val keyBytes = extractRawKeyBytes(keyInfo) ?: throw IllegalArgumentException("Key must have raw bytes for decryption")

    val expectedKeySize = when (algorithm) {
        "A128GCM" -> 16
        "A192GCM" -> 24
        "A256GCM" -> 32
        else -> throw IllegalArgumentException("Unsupported AES-GCM algorithm: $algorithm")
    }

    if (keyBytes.size != expectedKeySize) {
        throw IllegalArgumentException("Key size ${keyBytes.size} does not match expected size $expectedKeySize for $algorithm")
    }

    val provider = CryptographyProvider.Default
    val aesKey = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
    val combined = ciphertext + authTag
    val cipher = aesKey.cipher(tagSize = 128.bits)

    return cipher.decryptWithIv(iv = iv, ciphertext = combined, associatedData = aad)
}

/**
 * Wraps a key using RSA-OAEP or other key wrapping algorithms.
 */
@OptIn(DelicateCryptographyApi::class)
internal suspend fun wrapKeyWithNativeKey(
    wrappingKeyInfo: KeyInfoType<*>,
    keyToWrap: ByteArray,
    algorithm: String
): ByteArray? {
    return try {
        when {
            algorithm.startsWith("RSA-OAEP") -> wrapKeyRsaOaep(wrappingKeyInfo, keyToWrap, algorithm)
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
            else -> null
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("Key wrapping failed for algorithm $algorithm: ${e.message}", e)
    }
}

@OptIn(DelicateCryptographyApi::class)
private suspend fun wrapKeyRsaOaep(
    wrappingKeyInfo: KeyInfoType<*>,
    keyToWrap: ByteArray,
    algorithm: String
): ByteArray {
    val publicKeyDer = extractPublicKeyDer(wrappingKeyInfo)
        ?: throw IllegalArgumentException("Wrapping key must have a public key in DER format")

    val digest = when (algorithm) {
        "RSA-OAEP" -> SHA1
        "RSA-OAEP-256" -> SHA256
        "RSA-OAEP-384" -> SHA384
        "RSA-OAEP-512" -> SHA512
        else -> throw IllegalArgumentException("Unsupported RSA-OAEP variant: $algorithm")
    }

    val provider = CryptographyProvider.Default
    val rsaOaep = provider.get(RSA.OAEP)
    val publicKey = rsaOaep.publicKeyDecoder(digest).decodeFromByteArray(RSA.PublicKey.Format.DER, publicKeyDer)
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
    algorithm: String
): ByteArray? {
    return try {
        when {
            algorithm.startsWith("RSA-OAEP") -> unwrapKeyRsaOaep(unwrappingKeyInfo, wrappedKey, algorithm)
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
            else -> null
        }
    } catch (e: Exception) {
        throw IllegalStateException("Key unwrapping failed for algorithm $algorithm: ${e.message}", e)
    }
}

@OptIn(DelicateCryptographyApi::class)
private suspend fun unwrapKeyRsaOaep(
    unwrappingKeyInfo: KeyInfoType<*>,
    wrappedKey: ByteArray,
    algorithm: String
): ByteArray {
    val privateKeyDer = extractPrivateKeyDer(unwrappingKeyInfo)
        ?: throw IllegalArgumentException("Unwrapping key must have a private key in DER format")

    val digest = when (algorithm) {
        "RSA-OAEP" -> SHA1
        "RSA-OAEP-256" -> SHA256
        "RSA-OAEP-384" -> SHA384
        "RSA-OAEP-512" -> SHA512
        else -> throw IllegalArgumentException("Unsupported RSA-OAEP variant: $algorithm")
    }

    val provider = CryptographyProvider.Default
    val rsaOaep = provider.get(RSA.OAEP)
    val privateKey = rsaOaep.privateKeyDecoder(digest).decodeFromByteArray(RSA.PrivateKey.Format.DER, privateKeyDer)
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
internal val AES_KW_DEFAULT_IV = byteArrayOf(
    0xA6.toByte(), 0xA6.toByte(), 0xA6.toByte(), 0xA6.toByte(),
    0xA6.toByte(), 0xA6.toByte(), 0xA6.toByte(), 0xA6.toByte()
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
    algorithm: String
): ByteArray {
    val keyBytes = extractRawKeyBytes(wrappingKeyInfo)
        ?: throw IllegalArgumentException("Wrapping key must have raw bytes for AES-KW")

    // Validate KEK size matches algorithm
    val expectedKeySize = when (algorithm) {
        "A128KW" -> 16
        "A192KW" -> 24
        "A256KW" -> 32
        else -> throw IllegalArgumentException("Unsupported AES-KW algorithm: $algorithm")
    }

    if (keyBytes.size != expectedKeySize) {
        throw IllegalArgumentException(
            "KEK size ${keyBytes.size} does not match expected size $expectedKeySize for $algorithm"
        )
    }

    // Validate key to wrap (must be multiple of 8 bytes, minimum 16 bytes per RFC 3394)
    if (keyToWrap.size < 16) {
        throw IllegalArgumentException("Key to wrap must be at least 16 bytes, got ${keyToWrap.size}")
    }
    if (keyToWrap.size % 8 != 0) {
        throw IllegalArgumentException("Key to wrap must be a multiple of 8 bytes, got ${keyToWrap.size}")
    }

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
    algorithm: String
): ByteArray {
    val keyBytes = extractRawKeyBytes(unwrappingKeyInfo)
        ?: throw IllegalArgumentException("Unwrapping key must have raw bytes for AES-KW")

    // Validate KEK size matches algorithm
    val expectedKeySize = when (algorithm) {
        "A128KW" -> 16
        "A192KW" -> 24
        "A256KW" -> 32
        else -> throw IllegalArgumentException("Unsupported AES-KW algorithm: $algorithm")
    }

    if (keyBytes.size != expectedKeySize) {
        throw IllegalArgumentException(
            "KEK size ${keyBytes.size} does not match expected size $expectedKeySize for $algorithm"
        )
    }

    // Validate wrapped key size (must be multiple of 8 bytes, minimum 24 bytes)
    if (wrappedKey.size < 24) {
        throw IllegalArgumentException("Wrapped key must be at least 24 bytes, got ${wrappedKey.size}")
    }
    if (wrappedKey.size % 8 != 0) {
        throw IllegalArgumentException("Wrapped key must be a multiple of 8 bytes, got ${wrappedKey.size}")
    }

    return aesKeyUnwrap(keyBytes, wrappedKey)
}

/**
 * AES Key Wrap algorithm as per RFC 3394.
 * Platform-specific: uses AES-ECB on JVM/Apple, WebCrypto AES-KW on JS/wasmJs.
 */
internal expect suspend fun aesKeyWrap(kek: ByteArray, plaintext: ByteArray): ByteArray

/**
 * AES Key Unwrap algorithm as per RFC 3394.
 * Platform-specific: uses AES-ECB on JVM/Apple, WebCrypto AES-KW on JS/wasmJs.
 *
 * @throws IllegalStateException If integrity check fails (wrong key or corrupted data)
 */
internal expect suspend fun aesKeyUnwrap(kek: ByteArray, ciphertext: ByteArray): ByteArray

/**
 * XORs a byte array with a counter value (big-endian).
 * This is used for the t value in RFC 3394.
 */
internal fun xorWithCounter(data: ByteArray, counter: Long) {
    // XOR the counter into the last bytes of the 8-byte block (big-endian)
    var t = counter
    for (i in 7 downTo 0) {
        data[i] = (data[i].toInt() xor (t and 0xFF).toInt()).toByte()
        t = t shr 8
        if (t == 0L) break
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
    algorithm: String
): ByteArray {
    // Validate algorithm is ECDH-based
    require(algorithm.startsWith("ECDH")) {
        "Algorithm must be ECDH-based, got: $algorithm"
    }

    // Extract keys as JWK
    val privateJwk = when (val key = privateKeyInfo.key) {
        is JwkType -> key
        else -> throw IllegalArgumentException("Private key must be a JWK for ECDH key agreement")
    }

    val publicJwk = when (val key = publicKeyInfo.key) {
        is JwkType -> key
        else -> throw IllegalArgumentException("Public key must be a JWK for ECDH key agreement")
    }

    // Validate key types
    require(privateJwk.kty == JwaKeyType.EC) {
        "Private key must be an EC key, got: ${privateJwk.kty}"
    }
    require(publicJwk.kty == JwaKeyType.EC) {
        "Public key must be an EC key, got: ${publicJwk.kty}"
    }

    // Validate public key has x,y coordinates
    require(publicJwk.x != null && publicJwk.y != null) {
        "Public key must have 'x' and 'y' parameters for key agreement"
    }

    // Check if private key has 'd' parameter
    if (privateJwk.d == null) {
        // Try native keychain ECDH (iOS only)
        val alias = privateKeyInfo.alias
        if (alias != null) {
            val nativeResult = com.sphereon.crypto.kms.performNativeKeychainEcdh(alias, publicJwk)
            if (nativeResult != null) {
                return nativeResult
            }
        }
        throw IllegalArgumentException(
            "Private key must have 'd' parameter for key agreement, or be a native keychain key. " +
            "Alias: ${alias ?: "not set"}"
        )
    }

    // Get curves and validate they match
    val privateCurve = privateJwk.crv?.let { Curve.fromJose(it) } ?: Curve.P_256
    val publicCurve = publicJwk.crv?.let { Curve.fromJose(it) } ?: Curve.P_256

    require(privateCurve == publicCurve) {
        "Private and public key curves must match. Private: $privateCurve, Public: $publicCurve"
    }

    // Convert curve to whyoleg format
    val curve = resolveEcdsaKmpCurve(privateCurve)

    // Convert keys to DER format
    val privateKeyDer = toDerEcdsaPrivateKeyBytes(privateJwk)
    val publicKeyDer = toDerEcdsaPublicKeyBytes(publicJwk)

    // Get ECDH algorithm from provider
    val provider = CryptographyProvider.Default
    val ecdh = provider.get(ECDH)

    // Decode keys
    val ecdhPrivateKey = ecdh.privateKeyDecoder(curve).decodeFromByteArray(EC.PrivateKey.Format.DER, privateKeyDer)
    val ecdhPublicKey = ecdh.publicKeyDecoder(curve).decodeFromByteArray(EC.PublicKey.Format.DER, publicKeyDer)

    // Perform key agreement - get shared secret generator from private key
    val sharedSecretGenerator = ecdhPrivateKey.sharedSecretGenerator()

    // Derive the shared secret using the peer's public key
    return sharedSecretGenerator.generateSharedSecretToByteArray(ecdhPublicKey)
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
