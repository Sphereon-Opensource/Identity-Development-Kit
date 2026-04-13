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
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

// ============================================================================
// CreateRawSignature Command
// ============================================================================

/**
 * Arguments for creating a raw digital signature.
 *
 * @property keyInfo The key info containing the signing key (must include private key)
 * @property input The data to be signed
 * @property requireX5Chain Whether the X.509 certificate chain should be included/required
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateRawSignatureArgs", exact = true)
@JsExportCompat
@Serializable
data class CreateRawSignatureArgs(
    @kotlinx.serialization.Transient
    val keyInfo: KeyInfoType<*>? = null,
    val input: ByteArray = byteArrayOf(),
    val requireX5Chain: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CreateRawSignatureArgs

        if (keyInfo != other.keyInfo) return false
        if (!input.contentEquals(other.input)) return false
        if (requireX5Chain != other.requireX5Chain) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyInfo?.hashCode() ?: 0
        result = 31 * result + input.contentHashCode()
        result = 31 * result + requireX5Chain.hashCode()
        return result
    }
}

/**
 * Result of a signature creation operation.
 *
 * @property signature The generated raw digital signature
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateRawSignatureResult", exact = true)
@JsExportCompat
@Serializable
data class CreateRawSignatureResult(
    val signature: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CreateRawSignatureResult

        return signature.contentEquals(other.signature)
    }

    override fun hashCode(): Int {
        return signature.contentHashCode()
    }
}

/**
 * Command interface for creating a raw digital signature.
 *
 * This command wraps the KeyManagerService.createRawSignature operation,
 * providing uniform logging, auditing, authorization, and plugin capabilities.
 *
 * Example usage:
 * ```kotlin
 * val args = CreateRawSignatureArgs(
 *     keyInfo = myPrivateKeyInfo,
 *     input = dataToSign,
 *     requireX5Chain = false
 * )
 * val result = createRawSignatureCommand.execute(args)
 * if (result.isOk) {
 *     val signature = result.value.signature
 *     // Use signature...
 * }
 * ```
 */
interface CreateRawSignatureCommand :
    ServiceCommand<CreateRawSignatureArgs, CreateRawSignatureResult> {

    companion object {
        const val COMMAND_ID = "kms.signature.create"
    }

    override val commandId: String get() = COMMAND_ID
}

// ============================================================================
// VerifyRawSignature Command
// ============================================================================

/**
 * Arguments for verifying a raw digital signature.
 *
 * @property keyInfo The key info containing the verification key (public key)
 * @property input The original data that was signed
 * @property signature The signature to verify
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyRawSignatureArgs", exact = true)
@JsExportCompat
@Serializable
data class VerifyRawSignatureArgs(
    @kotlinx.serialization.Transient
    val keyInfo: KeyInfoType<*>? = null,
    val input: ByteArray = byteArrayOf(),
    val signature: ByteArray = byteArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as VerifyRawSignatureArgs

        if (keyInfo != other.keyInfo) return false
        if (!input.contentEquals(other.input)) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyInfo?.hashCode() ?: 0
        result = 31 * result + input.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}

/**
 * Result of a signature verification operation.
 *
 * @property isValid Whether the signature is valid
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyRawSignatureResult", exact = true)
@JsExportCompat
@Serializable
data class VerifyRawSignatureResult(
    val isValid: Boolean
)

/**
 * Command interface for verifying a raw digital signature.
 *
 * This command wraps the KeyManagerService.isValidRawSignature operation,
 * providing uniform logging, auditing, authorization, and plugin capabilities.
 *
 * Example usage:
 * ```kotlin
 * val args = VerifyRawSignatureArgs(
 *     keyInfo = publicKeyInfo,
 *     input = originalData,
 *     signature = signatureToVerify
 * )
 * val result = verifyRawSignatureCommand.execute(args)
 * if (result.isOk) {
 *     if (result.value.isValid) {
 *         println("Signature is valid")
 *     } else {
 *         println("Signature is invalid")
 *     }
 * }
 * ```
 */
interface VerifyRawSignatureCommand :
    ServiceCommand<VerifyRawSignatureArgs, VerifyRawSignatureResult> {

    companion object {
        const val COMMAND_ID = "kms.signature.verify"
    }

    override val commandId: String get() = COMMAND_ID
}
