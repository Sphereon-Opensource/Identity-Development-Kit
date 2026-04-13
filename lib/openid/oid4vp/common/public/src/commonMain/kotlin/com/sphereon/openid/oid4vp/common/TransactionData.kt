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

package com.sphereon.openid.oid4vp.common

import com.sphereon.core.compat.JsExportCompat
import io.konform.validation.Validation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Transaction Data Entry
 *
 * OpenID4VP 1.0 Final Section 5.1.2:
 * "The transaction_data parameter is an array of base64url-encoded JSON objects,
 * each representing a transaction data entry."
 *
 * Each transaction data entry specifies:
 * - type: The type of transaction data (e.g., "openbanking", "mdoc_handover")
 * - credential_ids: Which credentials from the DCQL query this applies to
 * - transaction_data_hashes_alg: Allowed hash algorithms (default: ["sha-256"])
 * - Additional type-specific fields
 *
 * Example:
 * ```json
 * {
 *   "type": "openbanking",
 *   "credential_ids": ["pid_credential"],
 *   "transaction_data_hashes_alg": ["sha-256"],
 *   "payee_name": "ACME Corp",
 *   "amount": "100.00",
 *   "currency": "EUR"
 * }
 * ```
 *
 * @property type The type identifier for this transaction data
 * @property credentialIds List of DCQL credential query IDs this applies to
 * @property transactionDataHashesAlg Allowed hash algorithms (default: sha-256)
 * @property additionalProperties Additional type-specific properties
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TransactionDataEntry", exact = true)
@Serializable
@JsExportCompat
data class TransactionDataEntry(
    val type: String,
    @SerialName("credential_ids")
    val credentialIds: List<String>,
    @SerialName("transaction_data_hashes_alg")
    val transactionDataHashesAlg: List<String>? = null,
    /**
     * Additional type-specific properties.
     * These are any properties beyond the standard fields.
     */
    val additionalProperties: JsonObject? = null,
) {
    /**
     * Get the allowed hash algorithms, defaulting to sha-256.
     */
    fun getAllowedHashAlgorithms(): List<String> = transactionDataHashesAlg ?: listOf(DEFAULT_HASH_ALGORITHM)

    companion object {
        /**
         * Default hash algorithm per OpenID4VP 1.0 spec
         */
        const val DEFAULT_HASH_ALGORITHM = "sha-256"
    }
}

/**
 * Parsed Transaction Data Entry
 *
 * Represents a parsed transaction data entry with its original encoded form
 * and index in the transaction_data array.
 *
 * @property transactionData The parsed transaction data entry
 * @property transactionDataIndex The index of this entry in the original array
 * @property encoded The original base64url-encoded string
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParsedTransactionDataEntry", exact = true)
@Serializable
@JsExportCompat
data class ParsedTransactionDataEntry(
    val transactionData: TransactionDataEntry,
    val transactionDataIndex: Int,
    val encoded: String,
)

/**
 * Transaction Data Hashes from a presentation
 *
 * Contains the transaction_data_hashes claim from a KB-JWT or mdoc DeviceAuth.
 *
 * @property transactionDataHashes List of base64url-encoded hashes
 * @property transactionDataHashesAlg Hash algorithm used (default: sha-256)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TransactionDataHashes", exact = true)
@JsExportCompat
data class TransactionDataHashes(
    @SerialName("transaction_data_hashes")
    val transactionDataHashes: List<String>,
    @SerialName("transaction_data_hashes_alg")
    val transactionDataHashesAlg: String = TransactionDataEntry.DEFAULT_HASH_ALGORITHM,
)

/**
 * Verified Transaction Data Entry
 *
 * Result of verifying a transaction data entry against presentations.
 *
 * @property transactionDataEntry The original parsed transaction data entry
 * @property credentialId The credential ID that matched
 * @property presentations List of presentations that matched this entry
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifiedTransactionDataEntry", exact = true)
@JsExportCompat
data class VerifiedTransactionDataEntry(
    val transactionDataEntry: ParsedTransactionDataEntry,
    val credentialId: String,
    val presentations: List<VerifiedTransactionDataPresentation>,
)

/**
 * A single presentation that verified a transaction data entry.
 *
 * @property presentationIndex Index of the presentation in the vp_token
 * @property hash The computed hash that matched
 * @property hashAlg The hash algorithm used
 * @property credentialHashIndex Index of the hash in transaction_data_hashes
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifiedTransactionDataPresentation", exact = true)
@JsExportCompat
data class VerifiedTransactionDataPresentation(
    val presentationIndex: Int,
    val hash: String,
    val hashAlg: String,
    val credentialHashIndex: Int,
)

/**
 * Hash algorithm for transaction data
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TransactionDataHashAlgorithm", exact = true)
@Serializable
@JsExportCompat
enum class TransactionDataHashAlgorithm(
    val value: String,
) {
    @SerialName("sha-256")
    SHA_256("sha-256"),

    @SerialName("sha-384")
    SHA_384("sha-384"),

    @SerialName("sha-512")
    SHA_512("sha-512"),
    ;

    companion object {
        fun fromValue(value: String): TransactionDataHashAlgorithm? = entries.find { it.value == value }

        fun isSupported(value: String): Boolean = fromValue(value) != null
    }
}

// =============================================================================
// Konform Validation
// =============================================================================

/**
 * Validate a TransactionDataEntry
 */
val validateTransactionDataEntry: Validation<TransactionDataEntry> =
    Validation {
        TransactionDataEntry::type {
            constrain("Transaction data type must not be empty") { it.isNotBlank() }
        }

        TransactionDataEntry::credentialIds {
            constrain("At least one credential_id must be specified") { it.isNotEmpty() }
            constrain("All credential IDs must be non-empty") { ids -> ids.all { it.isNotBlank() } }
        }

        TransactionDataEntry::transactionDataHashesAlg ifPresent {
            constrain("All hash algorithms must be supported") { algs ->
                algs.all { TransactionDataHashAlgorithm.isSupported(it) }
            }
        }
    }

/**
 * Validate TransactionDataHashes from a presentation
 */
val validateTransactionDataHashes: Validation<TransactionDataHashes> =
    Validation {
        TransactionDataHashes::transactionDataHashes {
            constrain("At least one transaction data hash must be present") { it.isNotEmpty() }
            constrain("All hashes must be non-empty") { hashes -> hashes.all { it.isNotBlank() } }
        }

        TransactionDataHashes::transactionDataHashesAlg {
            constrain("Hash algorithm must be supported") { alg ->
                TransactionDataHashAlgorithm.isSupported(alg)
            }
        }
    }

// =============================================================================
// Error Types
// =============================================================================

/**
 * Transaction data error types
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TransactionDataError", exact = true)
sealed interface TransactionDataError {
    val message: String

    /**
     * Failed to parse transaction data
     */
    data class ParseError(
        override val message: String,
        val index: Int? = null,
        val cause: Throwable? = null,
    ) : TransactionDataError

    /**
     * Transaction data validation failed
     */
    data class ValidationError(
        override val message: String,
        val index: Int? = null,
        val errors: List<String> = emptyList(),
    ) : TransactionDataError

    /**
     * Transaction data hash verification failed
     */
    data class HashMismatchError(
        override val message: String,
        val transactionDataIndex: Int,
        val credentialId: String,
        val presentationIndex: Int,
        val expectedHash: String? = null,
        val actualHashes: List<String> = emptyList(),
    ) : TransactionDataError

    /**
     * No matching credential found for transaction data entry
     */
    data class NoMatchingCredentialError(
        override val message: String,
        val transactionDataIndex: Int,
        val credentialIds: List<String>,
    ) : TransactionDataError

    /**
     * Unsupported hash algorithm
     */
    data class UnsupportedHashAlgorithmError(
        override val message: String,
        val algorithm: String,
        val supportedAlgorithms: List<String> = TransactionDataHashAlgorithm.entries.map { it.value },
    ) : TransactionDataError
}
