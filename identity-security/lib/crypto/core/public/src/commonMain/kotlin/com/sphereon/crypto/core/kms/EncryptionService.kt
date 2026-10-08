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

package com.sphereon.crypto.core.kms

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfoType

/**
 * Service interface for cryptographic encryption operations.
 *
 * Provides methods for:
 * - Content encryption/decryption (AEAD - Authenticated Encryption with Associated Data)
 * - Key wrapping/unwrapping (for encrypting symmetric keys with asymmetric keys)
 * - Key agreement (ECDH for deriving shared secrets)
 *
 * This interface complements SimpleSignatureService and provides the cryptographic
 * primitives needed for JWE (JSON Web Encryption) support.
 */
@JsExportCompat
interface EncryptionService {
    // ========================================================================
    // Content Encryption/Decryption Operations
    // ========================================================================

    /**
     * Encrypts plaintext using authenticated encryption with associated data (AEAD).
     *
     * This operation is used for content encryption in JWE, where the actual
     * message payload is encrypted using a content encryption key (CEK).
     *
     * @param keyInfo The key info containing the encryption key (typically a symmetric key)
     * @param plaintext The data to encrypt
     * @param algorithm The content encryption algorithm to use (e.g., A256GCM)
     * @param additionalAuthenticatedData Optional AAD that will be authenticated but not encrypted
     * @return EncryptionResult containing ciphertext, IV, and authentication tag
     * @throws IllegalArgumentException If the key type doesn't support encryption or algorithm is not supported
     */

    suspend fun encrypt(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray? = null,
    ): EncryptionResult

    /**
     * Decrypts ciphertext that was encrypted with authenticated encryption (AEAD).
     *
     * This operation is used for content decryption in JWE, verifying the
     * authentication tag and decrypting the ciphertext.
     *
     * @param keyInfo The key info containing the decryption key
     * @param ciphertext The encrypted data
     * @param algorithm The content encryption algorithm used during encryption
     * @param iv The initialization vector used during encryption
     * @param authTag The authentication tag generated during encryption
     * @param additionalAuthenticatedData Optional AAD that was authenticated during encryption
     * @return The decrypted plaintext as a byte array
     * @throws IllegalArgumentException If the key type doesn't support decryption or algorithm is not supported
     * @throws SecurityException If authentication fails (invalid tag)
     */

    suspend fun decrypt(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray? = null,
    ): ByteArray

    // ========================================================================
    // Key Wrapping/Unwrapping Operations
    // ========================================================================

    /**
     * Wraps (encrypts) a symmetric key using an asymmetric key or another symmetric key.
     *
     * This operation is used in JWE to encrypt the Content Encryption Key (CEK)
     * with the recipient's public key or a shared key-encryption key.
     *
     * Common algorithms:
     * - RSA-OAEP family: Wrap with RSA public key
     * - AES-KW family: Wrap with AES key-encryption key
     * - AES-GCMKW: Wrap with AES-GCM
     *
     * @param wrappingKeyInfo The key info containing the key-encryption key (KEK)
     * @param keyToWrap The raw bytes of the key to be wrapped (typically the CEK)
     * @param algorithm The key wrapping algorithm to use
     * @return The wrapped key as a byte array (encrypted key material)
     * @throws IllegalArgumentException If the wrapping key doesn't support the algorithm
     */

    suspend fun wrapKey(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: KeyWrapAlgorithm,
    ): ByteArray

    /**
     * Unwraps (decrypts) a symmetric key that was wrapped using wrapKey.
     *
     * This operation is used in JWE to decrypt the Content Encryption Key (CEK)
     * using the recipient's private key or a shared key-encryption key.
     *
     * @param unwrappingKeyInfo The key info containing the key-decryption key
     * @param wrappedKey The wrapped key bytes (encrypted key material)
     * @param algorithm The key wrapping algorithm that was used
     * @return The unwrapped key as a byte array (plaintext key material)
     * @throws IllegalArgumentException If the unwrapping key doesn't support the algorithm
     * @throws SecurityException If decryption fails
     */

    suspend fun unwrapKey(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: KeyWrapAlgorithm,
    ): ByteArray

    // ========================================================================
    // Key Agreement Operations
    // ========================================================================

    /**
     * Performs Elliptic Curve Diffie-Hellman (ECDH) key agreement.
     *
     * This operation is used in JWE with ECDH-ES algorithms to derive a shared
     * secret between two parties. The shared secret can be used directly as
     * the CEK (ECDH-ES) or to wrap the CEK (ECDH-ES+AxxxKW).
     *
     * @param privateKeyInfo The local party's private key info
     * @param publicKeyInfo The remote party's public key info (ephemeral key in JWE)
     * @param algorithm The key agreement algorithm (ECDH-ES variants)
     * @param keyDataLen The desired length of derived key material in bits (required for some algorithms)
     * @return The derived shared secret as a byte array
     * @throws IllegalArgumentException If keys are not compatible or algorithm not supported
     * @throws SecurityException If key agreement fails
     */

    suspend fun performKeyAgreement(
        privateKeyInfo: KeyInfoType<*>,
        publicKeyInfo: KeyInfoType<*>,
        algorithm: KeyAgreementAlgorithm,
        keyDataLen: Int? = null,
    ): ByteArray
}

/**
 * Result of an encryption operation, containing all components needed for decryption.
 *
 * For AEAD (Authenticated Encryption with Associated Data) algorithms like AES-GCM,
 * all three components are required to decrypt the ciphertext.
 *
 * @property ciphertext The encrypted data
 * @property iv The initialization vector (nonce) used for encryption
 * @property authTag The authentication tag for verifying data integrity and authenticity
 */
@JsExportCompat
data class EncryptionResult(
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val authTag: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as EncryptionResult

        if (!ciphertext.contentEquals(other.ciphertext)) {
            return false
        }
        if (!iv.contentEquals(other.iv)) {
            return false
        }
        if (!authTag.contentEquals(other.authTag)) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + authTag.contentHashCode()
        return result
    }
}
