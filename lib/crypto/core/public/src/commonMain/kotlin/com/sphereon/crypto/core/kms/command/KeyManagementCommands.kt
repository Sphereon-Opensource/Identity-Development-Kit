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
 *
 */

package com.sphereon.crypto.core.kms.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.x509.Certificate
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.native.ObjCName
// ============================================================================
// GenerateKey Command
// ============================================================================

/**
 * Arguments for generating a new cryptographic key pair.
 *
 * @property providerId The KMS provider ID to use (uses default if null)
 * @property alias Optional alias for the key
 * @property use The intended use of the key (sig, enc)
 * @property keyOperations The allowed operations for this key
 * @property alg The signature algorithm to use
 * @property keyVisibility The visibility of the key (PUBLIC or PRIVATE)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("GenerateKeyArgs", exact = true)
@JsExportCompat
@Serializable
data class
GenerateKeyArgs
    @JvmOverloads
    constructor(
        val providerId: String? = null,
        val alias: String? = null,
        @kotlinx.serialization.Transient
        val use: JwkUse? = null,
        @kotlinx.serialization.Transient
        val keyOperations: Array<out KeyOperations>? = null,
        @kotlinx.serialization.Transient
        val alg: SignatureAlgorithm? = null,
        @kotlinx.serialization.Transient
        val keyVisibility: KeyVisibility? = KeyVisibility.PUBLIC,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || this::class != other::class) {
                return false
            }

            other as GenerateKeyArgs

            if (providerId != other.providerId) {
                return false
            }
            if (alias != other.alias) {
                return false
            }
            if (use != other.use) {
                return false
            }
            if (keyOperations != null) {
                if (other.keyOperations == null) {
                    return false
                }
                if (!keyOperations.contentEquals(other.keyOperations)) {
                    return false
                }
            } else if (other.keyOperations != null) {
                return false
            }
            if (alg != other.alg) {
                return false
            }
            if (keyVisibility != other.keyVisibility) {
                return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = providerId?.hashCode() ?: 0
            result = 31 * result + (alias?.hashCode() ?: 0)
            result = 31 * result + (use?.hashCode() ?: 0)
            result = 31 * result + (keyOperations?.contentHashCode() ?: 0)
            result = 31 * result + (alg?.hashCode() ?: 0)
            result = 31 * result + (keyVisibility?.hashCode() ?: 0)
            return result
        }
    }

/**
 * Result of a key generation operation.
 *
 * @property keyPair The generated managed key pair
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("GenerateKeyResult", exact = true)
@JsExportCompat
data class
GenerateKeyResult
    @JvmOverloads
    constructor(
        @kotlinx.serialization.Transient
        val keyPair: ManagedKeyPair? = null,
    )

/**
 * Command interface for generating a new cryptographic key pair.
 *
 * This command wraps the KeyManagerService.generateKey operation,
 * providing uniform logging, auditing, authorization, and plugin capabilities.
 */
@JsExportCompat
interface GenerateKeyCommand : ServiceCommand<GenerateKeyArgs, GenerateKeyResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "kms.key.generate"
    }
}

// ============================================================================
// ListKeys Command
// ============================================================================

/**
 * Arguments for listing all keys in the key store.
 *
 * @property providerId Optional provider ID filter
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ListKeysArgs", exact = true)
@JsExportCompat
@Serializable
data class
ListKeysArgs
    @JvmOverloads
    constructor(
        val providerId: String? = null,
    )

/**
 * Result of a list keys operation.
 *
 * @property keys Array of metadata-only key references (no key material).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ListKeysResult", exact = true)
@JsExportCompat
data class
ListKeysResult
    @JvmOverloads
    constructor(
        @kotlinx.serialization.Transient
        val keys: Array<ManagedKeyReference> = emptyArray(),
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || this::class != other::class) {
                return false
            }

            other as ListKeysResult

            return keys.contentEquals(other.keys)
        }

        override fun hashCode(): Int = keys.contentHashCode()
    }

/**
 * Command interface for listing all keys in the key store.
 *
 * This command wraps the KeyManagerService.listKeys operation,
 * providing uniform logging, auditing, authorization, and plugin capabilities.
 */
@JsExportCompat
interface ListKeysCommand : ServiceCommand<ListKeysArgs, ListKeysResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "kms.key.list"
    }
}

// ============================================================================
// GetKey Command
// ============================================================================

/**
 * Arguments for retrieving a specific key from the key store.
 *
 * @property keyInfo The key info identifying the key to retrieve
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("GetKeyArgs", exact = true)
@JsExportCompat
@Serializable
data class
GetKeyArgs
    @JvmOverloads
    constructor(
        @kotlinx.serialization.Transient
        val keyInfo: KeyInfoType<*>? = null,
    )

/**
 * Result of a get key operation.
 *
 * @property key The managed key info
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("GetKeyResult", exact = true)
@JsExportCompat
data class
GetKeyResult
    @JvmOverloads
    constructor(
        @kotlinx.serialization.Transient
        val key: ManagedKeyInfoType<*>? = null,
    )

/**
 * Command interface for retrieving a specific key from the key store.
 *
 * This command wraps the KeyManagerService.getKey operation,
 * providing uniform logging, auditing, authorization, and plugin capabilities.
 */
@JsExportCompat
interface GetKeyCommand : ServiceCommand<GetKeyArgs, GetKeyResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "kms.key.get"
    }
}

// ============================================================================
// StoreKey Command
// ============================================================================

/**
 * Arguments for storing a key in the key store.
 *
 * @property keyInfo The resolved key info to store
 * @property providerId The KMS provider ID where the key will be stored
 * @property alias The alias for the key
 * @property certChain Optional certificate chain to store with the key
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("StoreKeyArgs", exact = true)
@JsExportCompat
@Serializable
data class
StoreKeyArgs
    @JvmOverloads
    constructor(
        @kotlinx.serialization.Transient
        val keyInfo: ResolvedKeyInfoType<*>? = null,
        val providerId: String = "",
        val alias: String = "",
        @kotlinx.serialization.Transient
        val certChain: Array<Certificate>? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || this::class != other::class) {
                return false
            }

            other as StoreKeyArgs

            if (keyInfo != other.keyInfo) {
                return false
            }
            if (providerId != other.providerId) {
                return false
            }
            if (alias != other.alias) {
                return false
            }
            if (certChain != null) {
                if (other.certChain == null) {
                    return false
                }
                if (!certChain.contentEquals(other.certChain)) {
                    return false
                }
            } else if (other.certChain != null) {
                return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = keyInfo?.hashCode() ?: 0
            result = 31 * result + providerId.hashCode()
            result = 31 * result + alias.hashCode()
            result = 31 * result + (certChain?.contentHashCode() ?: 0)
            return result
        }
    }

/**
 * Result of a store key operation.
 *
 * @property key The stored managed key info
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("StoreKeyResult", exact = true)
@JsExportCompat
data class
StoreKeyResult
    @JvmOverloads
    constructor(
        @kotlinx.serialization.Transient
        val key: ManagedKeyInfoType<*>? = null,
    )

/**
 * Command interface for storing a key in the key store.
 *
 * This command wraps the KeyManagerService.storeKey operation,
 * providing uniform logging, auditing, authorization, and plugin capabilities.
 */
@JsExportCompat
interface StoreKeyCommand : ServiceCommand<StoreKeyArgs, StoreKeyResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "kms.key.store"
    }
}

// ============================================================================
// DeleteKey Command
// ============================================================================

/**
 * Arguments for deleting a key from the key store.
 *
 * @property keyInfo The key info identifying the key to delete
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteKeyArgs", exact = true)
@JsExportCompat
@Serializable
data class
DeleteKeyArgs
    @JvmOverloads
    constructor(
        @kotlinx.serialization.Transient
        val keyInfo: KeyInfoType<*>? = null,
    )

/**
 * Result of a delete key operation.
 *
 * @property deleted Whether the key was successfully deleted
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteKeyResult", exact = true)
@JsExportCompat
@Serializable
data class DeleteKeyResult(
    val deleted: Boolean,
)

/**
 * Command interface for deleting a key from the key store.
 *
 * This command wraps the KeyManagerService.deleteKey operation,
 * providing uniform logging, auditing, authorization, and plugin capabilities.
 */
@JsExportCompat
interface DeleteKeyCommand : ServiceCommand<DeleteKeyArgs, DeleteKeyResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "kms.key.delete"
    }
}
