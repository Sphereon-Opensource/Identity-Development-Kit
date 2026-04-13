/*
 * Copyright (c) 2026 Sphereon International B.V.
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
 */

package com.sphereon.crypto.core.kms.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.core.generic.DigestAlg
import kotlinx.serialization.Serializable

// ============================================================================
// GenerateMac Command (AWS KMS GenerateMac pattern)
// ============================================================================

/**
 * Arguments for generating a MAC (Message Authentication Code).
 *
 * Follows the AWS KMS GenerateMac pattern: given a key ID and message,
 * compute an HMAC digest.
 *
 * @property keyId The ID/alias of the HMAC key in the KMS
 * @property message The data to compute the MAC over
 * @property digestAlgorithm The hash algorithm to use (SHA-256, SHA-384, SHA-512)
 * @property providerId Optional KMS provider to use (defaults to key's provider)
 */
@Serializable
data class GenerateMacArgs(
    val keyId: String,
    val message: ByteArray,
    val digestAlgorithm: DigestAlg = DigestAlg.SHA256,
    val providerId: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }
        other as GenerateMacArgs
        return keyId == other.keyId &&
            message.contentEquals(other.message) &&
            digestAlgorithm == other.digestAlgorithm &&
            providerId == other.providerId
    }

    override fun hashCode(): Int {
        var result = keyId.hashCode()
        result = 31 * result + message.contentHashCode()
        result = 31 * result + digestAlgorithm.hashCode()
        result = 31 * result + (providerId?.hashCode() ?: 0)
        return result
    }
}

/**
 * Result of a MAC generation operation.
 *
 * The MAC is returned both as raw bytes and as a multibase-encoded multihash string
 * for self-describing storage and comparison.
 *
 * @property mac The raw MAC bytes (multihash-encoded: <varint code> + <varint length> + <digest>)
 * @property macMultibase The multibase-encoded multihash string (e.g., "f1220..." for hex sha2-256)
 * @property digestAlgorithm The hash algorithm that was used
 */
@Serializable
data class GenerateMacResult(
    val mac: ByteArray,
    val macMultibase: String,
    val digestAlgorithm: DigestAlg,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }
        other as GenerateMacResult
        return mac.contentEquals(other.mac) &&
            macMultibase == other.macMultibase &&
            digestAlgorithm == other.digestAlgorithm
    }

    override fun hashCode(): Int {
        var result = mac.contentHashCode()
        result = 31 * result + macMultibase.hashCode()
        result = 31 * result + digestAlgorithm.hashCode()
        return result
    }
}

/**
 * Command for generating a MAC (Message Authentication Code) via the KMS.
 *
 * This follows the AWS KMS GenerateMac pattern. The KMS holds the symmetric
 * HMAC key; consumers provide the message and receive the MAC result.
 * MAC output is multihash-encoded (self-describing hash format).
 */
interface GenerateMacCommand : ServiceCommand<GenerateMacArgs, GenerateMacResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "kms.mac.generate"
    }
}

// ============================================================================
// VerifyMac Command (AWS KMS VerifyMac pattern)
// ============================================================================

/**
 * Arguments for verifying a MAC.
 *
 * @property keyId The ID/alias of the HMAC key in the KMS
 * @property message The original data
 * @property mac The MAC to verify (multihash-encoded bytes)
 * @property digestAlgorithm The hash algorithm to use
 * @property providerId Optional KMS provider to use
 */
@Serializable
data class VerifyMacArgs(
    val keyId: String,
    val message: ByteArray,
    val mac: ByteArray,
    val digestAlgorithm: DigestAlg = DigestAlg.SHA256,
    val providerId: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }
        other as VerifyMacArgs
        return keyId == other.keyId &&
            message.contentEquals(other.message) &&
            mac.contentEquals(other.mac) &&
            digestAlgorithm == other.digestAlgorithm &&
            providerId == other.providerId
    }

    override fun hashCode(): Int {
        var result = keyId.hashCode()
        result = 31 * result + message.contentHashCode()
        result = 31 * result + mac.contentHashCode()
        result = 31 * result + digestAlgorithm.hashCode()
        result = 31 * result + (providerId?.hashCode() ?: 0)
        return result
    }
}

/**
 * Result of a MAC verification operation.
 *
 * @property isValid Whether the MAC is valid
 */
@Serializable
data class VerifyMacResult(
    val isValid: Boolean,
)

/**
 * Command for verifying a MAC via the KMS.
 */
interface VerifyMacCommand : ServiceCommand<VerifyMacArgs, VerifyMacResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "kms.mac.verify"
    }
}
