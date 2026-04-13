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

package com.sphereon.openid.oid4vp.common.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.common.CredentialTransactionHashes
import com.sphereon.openid.oid4vp.common.ParseTransactionDataArgs
import com.sphereon.openid.oid4vp.common.ParseTransactionDataCommand
import com.sphereon.openid.oid4vp.common.TransactionDataEntry
import com.sphereon.openid.oid4vp.common.TransactionDataError
import com.sphereon.openid.oid4vp.common.TransactionDataHashAlgorithm
import com.sphereon.openid.oid4vp.common.VerifiedTransactionDataEntry
import com.sphereon.openid.oid4vp.common.VerifiedTransactionDataPresentation
import com.sphereon.openid.oid4vp.common.VerifyTransactionDataArgs
import com.sphereon.openid.oid4vp.common.VerifyTransactionDataCommand
import com.sphereon.openid.oid4vp.common.VerifyTransactionDataCommandService
import com.sphereon.openid.oid4vp.common.VerifyTransactionDataResult
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of VerifyTransactionDataCommand.
 *
 * Verifies transaction data against presentation hashes.
 *
 * OpenID4VP 1.0 Section 7.4:
 * "If transaction_data was sent in the Authorization Request, the Verifier MUST
 * verify that the Wallet has correctly included transaction_data_hashes in the
 * presentation."
 *
 * Verification process:
 * 1. Parse all transaction data entries
 * 2. For each entry:
 *    a. Find credentials that match the entry's credential_ids
 *    b. Compute hash of the original encoded transaction data
 *    c. Verify hash exists in the credential's transaction_data_hashes
 * 3. Return verification result with matched entries
 */
@Inject
@SingleIn(SessionScope::class)
class VerifyTransactionDataCommandImpl(
    private val parseTransactionDataCommand: ParseTransactionDataCommand,
    execution: SessionExecution,
) : TypedServiceCommandAdapter<VerifyTransactionDataArgs, VerifyTransactionDataResult>(
        commandId = VerifyTransactionDataCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifyTransactionDataArgs>(),
        outputTypeToken = typeToken<VerifyTransactionDataResult>(),
    ),
    VerifyTransactionDataCommand,
    VerifyTransactionDataCommandService {
    override val commandId: String get() = VerifyTransactionDataCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyTransactionDataArgs

    override suspend fun verifyTransactionData(
        transactionData: List<String>,
        credentials: CredentialTransactionHashes,
    ): IdkResult<VerifyTransactionDataResult, IdkError> = execute(VerifyTransactionDataArgs(transactionData, credentials))

    override suspend fun doExecute(
        args: VerifyTransactionDataArgs,
        applyDuring: (VerifyTransactionDataArgs) -> VerifyTransactionDataArgs,
    ): IdkResult<VerifyTransactionDataResult, IdkError> {
        val processedArgs = applyDuring(args)
        val transactionData = processedArgs.transactionData
        val credentials = processedArgs.credentials

        if (transactionData.isEmpty()) {
            return Ok(
                VerifyTransactionDataResult(
                    verified = true,
                    verifiedEntries = emptyList(),
                ),
            )
        }

        // 1. Parse all transaction data entries
        val parseResult =
            parseTransactionDataCommand
                .execute(ParseTransactionDataArgs(transactionData))
                .getOrElse { return Err(it) }

        val parsedEntries = parseResult.entries
        val verifiedEntries = mutableListOf<VerifiedTransactionDataEntry>()
        val errors = mutableListOf<TransactionDataError>()

        // 2. Verify each transaction data entry
        for (parsedEntry in parsedEntries) {
            val verificationResult =
                verifyEntry(
                    parsedEntry.transactionData,
                    parsedEntry.transactionDataIndex,
                    parsedEntry.encoded,
                    credentials,
                )

            when (verificationResult) {
                is VerifyEntryResult.Success -> {
                    verifiedEntries.add(
                        VerifiedTransactionDataEntry(
                            transactionDataEntry = parsedEntry,
                            credentialId = verificationResult.credentialId,
                            presentations = verificationResult.presentations,
                        ),
                    )
                }

                is VerifyEntryResult.Error -> {
                    errors.add(verificationResult.error)
                }
            }
        }

        val allVerified = errors.isEmpty() && verifiedEntries.size == parsedEntries.size

        if (allVerified) {
            log.info("Successfully verified ${verifiedEntries.size} transaction data entries")
        } else {
            log.warn("Transaction data verification failed: ${errors.size} errors")
        }

        return Ok(
            VerifyTransactionDataResult(
                verified = allVerified,
                verifiedEntries = verifiedEntries,
                errors = errors,
            ),
        )
    }

    /**
     * Verify a single transaction data entry against credentials.
     */
    private fun verifyEntry(
        entry: TransactionDataEntry,
        index: Int,
        encoded: String,
        credentials: CredentialTransactionHashes,
    ): VerifyEntryResult {
        val allowedAlgs = entry.getAllowedHashAlgorithms()

        // Compute hashes for all allowed algorithms
        val hashes = mutableMapOf<String, String>()
        for (algString in allowedAlgs) {
            val alg = TransactionDataHashAlgorithm.fromValue(algString) ?: continue
            val hashBytes = computeHash(encoded, alg)
            hashes[algString] = hashBytes.encodeToBase64Url()
        }

        // Check each credential ID from the entry
        for (credentialId in entry.credentialIds) {
            val credentialHashes = credentials[credentialId] ?: continue

            val presentations = mutableListOf<VerifiedTransactionDataPresentation>()

            for ((presentationIndex, hashesFromPresentation) in credentialHashes.withIndex()) {
                val algFromPresentation = hashesFromPresentation.transactionDataHashesAlg

                // Verify the algorithm is allowed
                if (algFromPresentation !in allowedAlgs) {
                    return VerifyEntryResult.Error(
                        TransactionDataError.UnsupportedHashAlgorithmError(
                            message =
                                "Transaction data entry at index $index for credential " +
                                    "'$credentialId' presentation $presentationIndex uses algorithm " +
                                    "'$algFromPresentation' which is not in allowed algorithms: $allowedAlgs",
                            algorithm = algFromPresentation,
                        ),
                    )
                }

                // Get the computed hash for this algorithm
                val computedHash = hashes[algFromPresentation]
                if (computedHash == null) {
                    return VerifyEntryResult.Error(
                        TransactionDataError.UnsupportedHashAlgorithmError(
                            message = "Unsupported hash algorithm '$algFromPresentation' for transaction data entry at index $index",
                            algorithm = algFromPresentation,
                        ),
                    )
                }

                // Find the matching hash index
                val hashIndex = hashesFromPresentation.transactionDataHashes.indexOf(computedHash)
                if (hashIndex == -1) {
                    return VerifyEntryResult.Error(
                        TransactionDataError.HashMismatchError(
                            message = "Transaction data entry at index $index does not have a matching hash in credential '$credentialId' presentation $presentationIndex",
                            transactionDataIndex = index,
                            credentialId = credentialId,
                            presentationIndex = presentationIndex,
                            expectedHash = computedHash,
                            actualHashes = hashesFromPresentation.transactionDataHashes,
                        ),
                    )
                }

                presentations.add(
                    VerifiedTransactionDataPresentation(
                        presentationIndex = presentationIndex,
                        hash = computedHash,
                        hashAlg = algFromPresentation,
                        credentialHashIndex = hashIndex,
                    ),
                )
            }

            // If we found presentations, return success
            if (presentations.isNotEmpty()) {
                return VerifyEntryResult.Success(
                    credentialId = credentialId,
                    presentations = presentations,
                )
            }
        }

        // No matching credentials found
        return VerifyEntryResult.Error(
            TransactionDataError.NoMatchingCredentialError(
                message = "Transaction data entry at index $index does not have a matching hash in any of the submitted credentials",
                transactionDataIndex = index,
                credentialIds = entry.credentialIds,
            ),
        )
    }

    /**
     * Compute hash of the encoded transaction data.
     */
    private fun computeHash(
        encoded: String,
        algorithm: TransactionDataHashAlgorithm,
    ): ByteArray {
        val data = encoded.encodeToByteArray()
        val digestAlg =
            when (algorithm) {
                TransactionDataHashAlgorithm.SHA_256 -> DigestAlg.SHA256
                TransactionDataHashAlgorithm.SHA_384 -> DigestAlg.SHA384
                TransactionDataHashAlgorithm.SHA_512 -> DigestAlg.SHA512
            }
        return hash(data, digestAlg)
    }

    /**
     * Result of verifying a single entry.
     */
    private sealed interface VerifyEntryResult {
        data class Success(
            val credentialId: String,
            val presentations: List<VerifiedTransactionDataPresentation>,
        ) : VerifyEntryResult

        data class Error(
            val error: TransactionDataError,
        ) : VerifyEntryResult
    }
}
