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
 */

package com.sphereon.openid.oid4vp.common

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

// =============================================================================
// ParseTransactionDataCommand - Parse base64url-encoded transaction data
// =============================================================================

/**
 * Arguments for parsing transaction data.
 *
 * @property transactionData Array of base64url-encoded transaction data strings
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParseTransactionDataArgs", exact = true)
@JsExportCompat
data class ParseTransactionDataArgs(
    val transactionData: List<String>
)

/**
 * Result of parsing transaction data.
 *
 * @property entries List of parsed transaction data entries
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParseTransactionDataResult", exact = true)
@JsExportCompat
data class ParseTransactionDataResult(
    val entries: List<ParsedTransactionDataEntry>
)

/**
 * Command to parse base64url-encoded transaction data from an authorization request.
 *
 * OpenID4VP 1.0 Section 5.1.2:
 * "The transaction_data parameter is an array of base64url-encoded JSON objects."
 *
 * This command:
 * 1. Decodes each base64url string to JSON
 * 2. Parses each JSON object into a TransactionDataEntry
 * 3. Validates each entry
 * 4. Returns parsed entries with original encoded form and index
 */
interface ParseTransactionDataCommand : ServiceCommand<ParseTransactionDataArgs, ParseTransactionDataResult> {
    companion object {
        const val COMMAND_ID = "oid4vp.transaction.parse"
    }

    override val commandId: String get() = COMMAND_ID
}

// =============================================================================
// VerifyTransactionDataCommand - Verify transaction data against presentations
// =============================================================================

/**
 * Transaction data hashes from a credential presentation.
 *
 * Maps credential query ID to the transaction data hashes found in that credential's
 * KB-JWT or DeviceAuth.
 */
typealias CredentialTransactionHashes = Map<String, List<TransactionDataHashes>>

/**
 * Arguments for verifying transaction data.
 *
 * @property transactionData Raw base64url-encoded transaction data strings
 * @property credentials Map of credential ID to transaction data hashes from presentations
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyTransactionDataArgs", exact = true)
@JsExportCompat
data class VerifyTransactionDataArgs(
    val transactionData: List<String>,
    val credentials: CredentialTransactionHashes
)

/**
 * Result of verifying transaction data.
 *
 * @property verified Whether all transaction data entries were verified
 * @property verifiedEntries List of verified transaction data entries
 * @property errors Verification errors (if any)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyTransactionDataResult", exact = true)
@JsExportCompat
data class VerifyTransactionDataResult(
    val verified: Boolean,
    val verifiedEntries: List<VerifiedTransactionDataEntry>,
    val errors: List<TransactionDataError> = emptyList()
)

/**
 * Command to verify transaction data against presentations.
 *
 * OpenID4VP 1.0 Section 7.4:
 * "If transaction_data was sent in the Authorization Request, the Verifier MUST
 * verify that the Wallet has correctly included transaction_data_hashes in the
 * presentation."
 *
 * This command:
 * 1. Parses the transaction data entries
 * 2. For each entry, finds matching credentials by credential_ids
 * 3. Computes hash of the original encoded transaction data
 * 4. Verifies the hash exists in the credential's transaction_data_hashes
 */
interface VerifyTransactionDataCommand : ServiceCommand<VerifyTransactionDataArgs, VerifyTransactionDataResult> {
    companion object {
        const val COMMAND_ID = "oid4vp.transaction.verify"
    }

    override val commandId: String get() = COMMAND_ID
}

// =============================================================================
// Service interfaces for convenience
// =============================================================================

/**
 * Service interface for transaction data parsing.
 */
interface ParseTransactionDataCommandService {
    /**
     * Parse transaction data from base64url-encoded strings.
     *
     * @param transactionData List of base64url-encoded transaction data strings
     * @return Parsed transaction data entries
     */
    suspend fun parseTransactionData(
        transactionData: List<String>
    ): IdkResult<ParseTransactionDataResult, IdkError>
}

/**
 * Service interface for transaction data verification.
 */
interface VerifyTransactionDataCommandService {
    /**
     * Verify transaction data against presentation hashes.
     *
     * @param transactionData Raw base64url-encoded transaction data strings
     * @param credentials Map of credential ID to transaction data hashes from presentations
     * @return Verification result
     */
    suspend fun verifyTransactionData(
        transactionData: List<String>,
        credentials: CredentialTransactionHashes
    ): IdkResult<VerifyTransactionDataResult, IdkError>
}
