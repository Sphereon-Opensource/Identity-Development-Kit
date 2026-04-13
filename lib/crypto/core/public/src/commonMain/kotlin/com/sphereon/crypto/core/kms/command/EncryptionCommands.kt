/*
 * Copyright (c) 2025 Sphereon International B.V.
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

package com.sphereon.crypto.core.kms.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.KeyAgreementAlgorithm
import com.sphereon.crypto.core.kms.KeyWrapAlgorithm
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

// ============================================================================
// Encrypt Command
// ============================================================================

/**
 * Arguments for encrypting plaintext using authenticated encryption (AEAD).
 *
 * @property keyInfo The key info containing the encryption key (typically a symmetric key)
 * @property plaintext The data to encrypt
 * @property algorithm The content encryption algorithm to use (e.g., A256GCM)
 * @property additionalAuthenticatedData Optional AAD that will be authenticated but not encrypted
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("EncryptArgs", exact = true)
@JsExportCompat
@Serializable
data class EncryptArgs(
    @kotlinx.serialization.Transient
    val keyInfo: KeyInfoType<*>? = null,
    val plaintext: ByteArray = byteArrayOf(),
    val algorithm: ContentEncryptionAlgorithm = ContentEncryptionAlgorithm.A256GCM,
    val additionalAuthenticatedData: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as EncryptArgs

        if (keyInfo != other.keyInfo) return false
        if (!plaintext.contentEquals(other.plaintext)) return false
        if (algorithm != other.algorithm) return false
        if (additionalAuthenticatedData != null) {
            if (other.additionalAuthenticatedData == null) return false
            if (!additionalAuthenticatedData.contentEquals(other.additionalAuthenticatedData)) return false
        } else if (other.additionalAuthenticatedData != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyInfo?.hashCode() ?: 0
        result = 31 * result + plaintext.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + (additionalAuthenticatedData?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Result of an encryption operation.
 *
 * @property ciphertext The encrypted data
 * @property iv The initialization vector used for encryption
 * @property authTag The authentication tag for verifying data integrity
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("EncryptResult", exact = true)
@JsExportCompat
@Serializable
data class EncryptResult(
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val authTag: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as EncryptResult

        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!authTag.contentEquals(other.authTag)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + authTag.contentHashCode()
        return result
    }
}

/**
 * Command interface for encrypting plaintext using authenticated encryption.
 *
 * This command wraps the KeyManagerService.encrypt operation,
 * providing uniform logging, auditing, authorization, and plugin capabilities.
 */
interface EncryptCommand :
    ServiceCommand<EncryptArgs, EncryptResult> {

    companion object {
        const val COMMAND_ID = "kms.encryption.encrypt"
    }

    override val commandId: String get() = COMMAND_ID
}

// ============================================================================
// Decrypt Command
// ============================================================================

/**
 * Arguments for decrypting ciphertext encrypted with authenticated encryption.
 *
 * @property keyInfo The key info containing the decryption key
 * @property ciphertext The encrypted data
 * @property algorithm The content encryption algorithm used during encryption
 * @property iv The initialization vector used during encryption
 * @property authTag The authentication tag generated during encryption
 * @property additionalAuthenticatedData Optional AAD that was authenticated during encryption
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DecryptArgs", exact = true)
@JsExportCompat
@Serializable
data class DecryptArgs(
    @kotlinx.serialization.Transient
    val keyInfo: KeyInfoType<*>? = null,
    val ciphertext: ByteArray = byteArrayOf(),
    val algorithm: ContentEncryptionAlgorithm = ContentEncryptionAlgorithm.A256GCM,
    val iv: ByteArray = byteArrayOf(),
    val authTag: ByteArray = byteArrayOf(),
    val additionalAuthenticatedData: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DecryptArgs

        if (keyInfo != other.keyInfo) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (algorithm != other.algorithm) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!authTag.contentEquals(other.authTag)) return false
        if (additionalAuthenticatedData != null) {
            if (other.additionalAuthenticatedData == null) return false
            if (!additionalAuthenticatedData.contentEquals(other.additionalAuthenticatedData)) return false
        } else if (other.additionalAuthenticatedData != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyInfo?.hashCode() ?: 0
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + authTag.contentHashCode()
        result = 31 * result + (additionalAuthenticatedData?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Result of a decryption operation.
 *
 * @property plaintext The decrypted data
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DecryptResult", exact = true)
@JsExportCompat
@Serializable
data class DecryptResult(
    val plaintext: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DecryptResult

        return plaintext.contentEquals(other.plaintext)
    }

    override fun hashCode(): Int {
        return plaintext.contentHashCode()
    }
}

/**
 * Command interface for decrypting ciphertext.
 *
 * This command wraps the KeyManagerService.decrypt operation,
 * providing uniform logging, auditing, authorization, and plugin capabilities.
 */
interface DecryptCommand :
    ServiceCommand<DecryptArgs, DecryptResult> {

    companion object {
        const val COMMAND_ID = "kms.encryption.decrypt"
    }

    override val commandId: String get() = COMMAND_ID
}

// ============================================================================
// WrapKey Command
// ============================================================================

/**
 * Arguments for wrapping (encrypting) a symmetric key.
 *
 * @property wrappingKeyInfo The key info containing the key-encryption key (KEK)
 * @property keyToWrap The raw bytes of the key to be wrapped (typically the CEK)
 * @property algorithm The key wrapping algorithm to use
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("WrapKeyArgs", exact = true)
@JsExportCompat
@Serializable
data class WrapKeyArgs(
    @kotlinx.serialization.Transient
    val wrappingKeyInfo: KeyInfoType<*>? = null,
    val keyToWrap: ByteArray = byteArrayOf(),
    val algorithm: KeyWrapAlgorithm = KeyWrapAlgorithm.A256KW
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as WrapKeyArgs

        if (wrappingKeyInfo != other.wrappingKeyInfo) return false
        if (!keyToWrap.contentEquals(other.keyToWrap)) return false
        if (algorithm != other.algorithm) return false

        return true
    }

    override fun hashCode(): Int {
        var result = wrappingKeyInfo?.hashCode() ?: 0
        result = 31 * result + keyToWrap.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        return result
    }
}

/**
 * Result of a key wrapping operation.
 *
 * @property wrappedKey The wrapped key as a byte array
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("WrapKeyResult", exact = true)
@JsExportCompat
@Serializable
data class WrapKeyResult(
    val wrappedKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as WrapKeyResult

        return wrappedKey.contentEquals(other.wrappedKey)
    }

    override fun hashCode(): Int {
        return wrappedKey.contentHashCode()
    }
}

/**
 * Command interface for wrapping a symmetric key.
 *
 * This command wraps the KeyManagerService.wrapKey operation,
 * providing uniform logging, auditing, authorization, and plugin capabilities.
 */
interface WrapKeyCommand :
    ServiceCommand<WrapKeyArgs, WrapKeyResult> {

    companion object {
        const val COMMAND_ID = "kms.encryption.wrap"
    }

    override val commandId: String get() = COMMAND_ID
}

// ============================================================================
// UnwrapKey Command
// ============================================================================

/**
 * Arguments for unwrapping (decrypting) a wrapped symmetric key.
 *
 * @property unwrappingKeyInfo The key info containing the key-decryption key
 * @property wrappedKey The wrapped key bytes (encrypted key material)
 * @property algorithm The key wrapping algorithm that was used
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("UnwrapKeyArgs", exact = true)
@JsExportCompat
@Serializable
data class UnwrapKeyArgs(
    @kotlinx.serialization.Transient
    val unwrappingKeyInfo: KeyInfoType<*>? = null,
    val wrappedKey: ByteArray = byteArrayOf(),
    val algorithm: KeyWrapAlgorithm = KeyWrapAlgorithm.A256KW
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as UnwrapKeyArgs

        if (unwrappingKeyInfo != other.unwrappingKeyInfo) return false
        if (!wrappedKey.contentEquals(other.wrappedKey)) return false
        if (algorithm != other.algorithm) return false

        return true
    }

    override fun hashCode(): Int {
        var result = unwrappingKeyInfo?.hashCode() ?: 0
        result = 31 * result + wrappedKey.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        return result
    }
}

/**
 * Result of a key unwrapping operation.
 *
 * @property unwrappedKey The unwrapped key as a byte array
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("UnwrapKeyResult", exact = true)
@JsExportCompat
@Serializable
data class UnwrapKeyResult(
    val unwrappedKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as UnwrapKeyResult

        return unwrappedKey.contentEquals(other.unwrappedKey)
    }

    override fun hashCode(): Int {
        return unwrappedKey.contentHashCode()
    }
}

/**
 * Command interface for unwrapping a symmetric key.
 *
 * This command wraps the KeyManagerService.unwrapKey operation,
 * providing uniform logging, auditing, authorization, and plugin capabilities.
 */
interface UnwrapKeyCommand :
    ServiceCommand<UnwrapKeyArgs, UnwrapKeyResult> {

    companion object {
        const val COMMAND_ID = "kms.encryption.unwrap"
    }

    override val commandId: String get() = COMMAND_ID
}

// ============================================================================
// PerformKeyAgreement Command
// ============================================================================

/**
 * Arguments for performing ECDH key agreement.
 *
 * @property privateKeyInfo The local party's private key info
 * @property publicKeyInfo The remote party's public key info (ephemeral key in JWE)
 * @property algorithm The key agreement algorithm (ECDH-ES variants)
 * @property keyDataLen The desired length of derived key material in bits (required for some algorithms)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PerformKeyAgreementArgs", exact = true)
@JsExportCompat
@Serializable
data class PerformKeyAgreementArgs(
    @kotlinx.serialization.Transient
    val privateKeyInfo: KeyInfoType<*>? = null,
    @kotlinx.serialization.Transient
    val publicKeyInfo: KeyInfoType<*>? = null,
    val algorithm: KeyAgreementAlgorithm = KeyAgreementAlgorithm.ECDH_ES,
    val keyDataLen: Int? = null
)

/**
 * Result of a key agreement operation.
 *
 * @property sharedSecret The derived shared secret
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PerformKeyAgreementResult", exact = true)
@JsExportCompat
@Serializable
data class PerformKeyAgreementResult(
    val sharedSecret: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PerformKeyAgreementResult

        return sharedSecret.contentEquals(other.sharedSecret)
    }

    override fun hashCode(): Int {
        return sharedSecret.contentHashCode()
    }
}

/**
 * Command interface for performing ECDH key agreement.
 *
 * This command wraps the KeyManagerService.performKeyAgreement operation,
 * providing uniform logging, auditing, authorization, and plugin capabilities.
 */
interface PerformKeyAgreementCommand :
    ServiceCommand<PerformKeyAgreementArgs, PerformKeyAgreementResult> {

    companion object {
        const val COMMAND_ID = "kms.encryption.agree"
    }

    override val commandId: String get() = COMMAND_ID
}
