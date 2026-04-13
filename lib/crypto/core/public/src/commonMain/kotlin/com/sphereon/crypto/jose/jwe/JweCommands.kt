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
 */

package com.sphereon.crypto.jose.jwe

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

// ============================================================================
// Request Types
// ============================================================================

/**
 * Options for JWE creation behavior
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJweOpts", exact = true)
@JsExportCompat
@Serializable
data class CreateJweOpts(
    val compress: Boolean = false, // Use DEF compression
    @Contextual
    val protectedHeaderOverrides: JweHeader? = null, // Additional protected header params
    @Contextual
    val unprotectedHeader: JweHeader? = null, // Unprotected header (for JSON formats)
)

/**
 * Arguments for preparing a JWE (common functionality for all JWE creation)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PrepareJweArgs", exact = true)
@JsExportCompat
@Serializable
data class PrepareJweArgs(
    // Plaintext to encrypt (required, but transient so needs default)
    @kotlinx.serialization.Transient
    val plaintext: ByteArray? = null,
    // Recipient's identifier (for key encryption) (required, but transient so needs default)
    @kotlinx.serialization.Transient
    val recipient: ManagedIdentifierOptsOrResult? = null,
    // Key encryption algorithm ("alg" header)
    val keyEncryptionAlg: String, // e.g., "RSA-OAEP", "ECDH-ES+A256KW"
    // Content encryption algorithm ("enc" header)
    val contentEncryptionAlg: String, // e.g., "A256GCM", "A128CBC-HS256"
    // Options for JWE creation
    val opts: CreateJweOpts = CreateJweOpts(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as PrepareJweArgs

        if (plaintext != null) {
            if (other.plaintext == null) {
                return false
            }
            if (!plaintext.contentEquals(other.plaintext)) {
                return false
            }
        } else if (other.plaintext != null) {
            return false
        }
        if (recipient != other.recipient) {
            return false
        }
        if (keyEncryptionAlg != other.keyEncryptionAlg) {
            return false
        }
        if (contentEncryptionAlg != other.contentEncryptionAlg) {
            return false
        }
        if (opts != other.opts) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = plaintext?.contentHashCode() ?: 0
        result = 31 * result + (recipient?.hashCode() ?: 0)
        result = 31 * result + keyEncryptionAlg.hashCode()
        result = 31 * result + contentEncryptionAlg.hashCode()
        result = 31 * result + opts.hashCode()
        return result
    }
}

/**
 * Prepared JWE object ready for encryption
 * Contains all necessary information to create a JWE
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PreparedJwe", exact = true)
@JsExportCompat
data class PreparedJwe(
    val header: JweHeader,
    @kotlinx.serialization.Transient
    val plaintext: ByteArray? = null,
    @kotlinx.serialization.Transient
    val cek: ByteArray? = null, // Content Encryption Key
    @kotlinx.serialization.Transient
    val recipient: ManagedIdentifierOptsOrResult? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as PreparedJwe

        if (header != other.header) {
            return false
        }
        if (plaintext != null) {
            if (other.plaintext == null) {
                return false
            }
            if (!plaintext.contentEquals(other.plaintext)) {
                return false
            }
        } else if (other.plaintext != null) {
            return false
        }
        if (cek != null) {
            if (other.cek == null) {
                return false
            }
            if (!cek.contentEquals(other.cek)) {
                return false
            }
        } else if (other.cek != null) {
            return false
        }
        if (recipient != other.recipient) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + (plaintext?.contentHashCode() ?: 0)
        result = 31 * result + (cek?.contentHashCode() ?: 0)
        result = 31 * result + (recipient?.hashCode() ?: 0)
        return result
    }
}

/**
 * Arguments for creating a JWE compact serialization
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJweCompactArgs", exact = true)
@JsExportCompat
@Serializable
data class CreateJweCompactArgs(
    // Prepared JWE object (required, but transient so needs default)
    @kotlinx.serialization.Transient
    val preparedJwe: PreparedJwe? = null,
    // Optional: Additional authenticated data (AAD)
    @kotlinx.serialization.Transient
    val aad: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as CreateJweCompactArgs

        if (preparedJwe != other.preparedJwe) {
            return false
        }
        if (aad != null) {
            if (other.aad == null) {
                return false
            }
            if (!aad.contentEquals(other.aad)) {
                return false
            }
        } else if (other.aad != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = preparedJwe?.hashCode() ?: 0
        result = 31 * result + (aad?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Arguments for creating JWE JSON serializations (flattened or general)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJweJsonArgs", exact = true)
@JsExportCompat
@Serializable
data class CreateJweJsonArgs(
    // Prepared JWE object (required, but transient so needs default)
    @kotlinx.serialization.Transient
    val preparedJwe: PreparedJwe? = null,
    // Optional: Additional authenticated data (AAD)
    @kotlinx.serialization.Transient
    val aad: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as CreateJweJsonArgs

        if (preparedJwe != other.preparedJwe) {
            return false
        }
        if (aad != null) {
            if (other.aad == null) {
                return false
            }
            if (!aad.contentEquals(other.aad)) {
                return false
            }
        } else if (other.aad != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = preparedJwe?.hashCode() ?: 0
        result = 31 * result + (aad?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Per-recipient information for multi-recipient JWE
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JweRecipientInfo", exact = true)
@JsExportCompat
@Serializable
data class JweRecipientInfo(
    // Recipient's identifier
    @kotlinx.serialization.Transient
    val recipient: ManagedIdentifierOptsOrResult? = null,
    // Per-recipient unprotected header
    @Contextual
    val perRecipientHeader: JweHeader? = null,
)

/**
 * Arguments for creating a general JSON JWE with multiple recipients
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJweJsonGeneralArgs", exact = true)
@JsExportCompat
@Serializable
data class CreateJweJsonGeneralArgs(
    // Base prepared JWE (uses first recipient's key encryption algorithm)
    @kotlinx.serialization.Transient
    val preparedJwe: PreparedJwe? = null,
    // Additional recipients (beyond the first one in preparedJwe)
    @kotlinx.serialization.Transient
    val additionalRecipients: List<JweRecipientInfo>? = null,
    // Optional: Additional authenticated data (AAD)
    @kotlinx.serialization.Transient
    val aad: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as CreateJweJsonGeneralArgs

        if (preparedJwe != other.preparedJwe) {
            return false
        }
        if (additionalRecipients != other.additionalRecipients) {
            return false
        }
        if (aad != null) {
            if (other.aad == null) {
                return false
            }
            if (!aad.contentEquals(other.aad)) {
                return false
            }
        } else if (other.aad != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = preparedJwe?.hashCode() ?: 0
        result = 31 * result + (additionalRecipients?.hashCode() ?: 0)
        result = 31 * result + (aad?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Arguments for decrypting a JWE
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DecryptJweArgs", exact = true)
@JsExportCompat
@Serializable
data class DecryptJweArgs(
    // JWE to decrypt (compact or JSON string) (required, but transient so needs default)
    @kotlinx.serialization.Transient
    val jwe: Jwe? = null,
    // Decryption key (our private key) (required, but transient so needs default)
    @kotlinx.serialization.Transient
    val decryptor: ManagedIdentifierOptsOrResult? = null,
)

/**
 * Result of JWE decryption
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JweDecryptionResult", exact = true)
@JsExportCompat
data class JweDecryptionResult(
    @kotlinx.serialization.Transient
    val plaintext: ByteArray? = null,
    val header: JweHeader,
    @kotlinx.serialization.Transient
    val aad: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as JweDecryptionResult

        if (plaintext != null) {
            if (other.plaintext == null) {
                return false
            }
            if (!plaintext.contentEquals(other.plaintext)) {
                return false
            }
        } else if (other.plaintext != null) {
            return false
        }
        if (header != other.header) {
            return false
        }
        if (aad != null) {
            if (other.aad == null) {
                return false
            }
            if (!aad.contentEquals(other.aad)) {
                return false
            }
        } else if (other.aad != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = plaintext?.contentHashCode() ?: 0
        result = 31 * result + header.hashCode()
        result = 31 * result + (aad?.contentHashCode() ?: 0)
        return result
    }
}

// ============================================================================
// Command Interfaces
// ============================================================================

/**
 * Command for preparing a JWE object (common functionality for all JWE creation)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PrepareJweCommand", exact = true)
@JsExportCompat
interface PrepareJweCommand : ServiceCommand<PrepareJweArgs, PreparedJwe> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "crypto.jwe.prepare"
    }
}

/**
 * Command service interface for preparing JWE
 * Not exported to JS as it has suspend functions
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PrepareJweCommandService", exact = true)
interface PrepareJweCommandService {
    suspend fun prepareJwe(args: PrepareJweArgs): IdkResult<PreparedJwe, IdkError>
}

/**
 * Command for creating a compact JWE
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJweCompactCommand", exact = true)
@JsExportCompat
interface CreateJweCompactCommand : ServiceCommand<CreateJweCompactArgs, JweCompact> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "crypto.jwe.compact"
    }
}

/**
 * Command service interface for compact JWE
 * Not exported to JS as it has suspend functions
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJweCompactCommandService", exact = true)
interface CreateJweCompactCommandService {
    suspend fun createJweCompact(args: CreateJweCompactArgs): IdkResult<JweCompact, IdkError>
}

/**
 * Command for creating a flattened JSON JWE
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJweJsonFlattenedCommand", exact = true)
@JsExportCompat
interface CreateJweJsonFlattenedCommand : ServiceCommand<CreateJweJsonArgs, JweJsonFlattened> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "crypto.jwe.flattened"
    }
}

/**
 * Command service interface for flattened JSON JWE
 * Not exported to JS as it has suspend functions
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJweJsonFlattenedCommandService", exact = true)
interface CreateJweJsonFlattenedCommandService {
    suspend fun createJweJsonFlattened(args: CreateJweJsonArgs): IdkResult<JweJsonFlattened, IdkError>
}

/**
 * Command for creating a general JSON JWE with multiple recipients
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJweJsonGeneralCommand", exact = true)
@JsExportCompat
interface CreateJweJsonGeneralCommand : ServiceCommand<CreateJweJsonGeneralArgs, JweJsonGeneral> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "crypto.jwe.general"
    }
}

/**
 * Command service interface for general JSON JWE
 * Not exported to JS as it has suspend functions
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJweJsonGeneralCommandService", exact = true)
interface CreateJweJsonGeneralCommandService {
    suspend fun createJweJsonGeneral(args: CreateJweJsonGeneralArgs): IdkResult<JweJsonGeneral, IdkError>
}

/**
 * Command for decrypting a JWE
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DecryptJweCommand", exact = true)
@JsExportCompat
interface DecryptJweCommand : ServiceCommand<DecryptJweArgs, JweDecryptionResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "crypto.jwe.decrypt"
    }
}

/**
 * Command service interface for JWE decryption
 * Not exported to JS as it has suspend functions
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DecryptJweCommandService", exact = true)
interface DecryptJweCommandService {
    suspend fun decryptJwe(args: DecryptJweArgs): IdkResult<JweDecryptionResult, IdkError>
}
