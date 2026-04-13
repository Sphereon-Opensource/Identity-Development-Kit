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
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.common.ParseTransactionDataArgs
import com.sphereon.openid.oid4vp.common.ParseTransactionDataCommand
import com.sphereon.openid.oid4vp.common.ParseTransactionDataCommandService
import com.sphereon.openid.oid4vp.common.ParseTransactionDataResult
import com.sphereon.openid.oid4vp.common.ParsedTransactionDataEntry
import com.sphereon.openid.oid4vp.common.TransactionDataEntry
import com.sphereon.openid.oid4vp.common.validateTransactionDataEntry
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.konform.validation.Invalid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Implementation of ParseTransactionDataCommand.
 *
 * Parses base64url-encoded transaction data from authorization requests.
 *
 * OpenID4VP 1.0 Section 5.1.2:
 * "The transaction_data parameter is an array of base64url-encoded JSON objects,
 * each representing a transaction data entry."
 *
 * Parsing process:
 * 1. Decode each base64url string to UTF-8 JSON string
 * 2. Parse JSON into TransactionDataEntry
 * 3. Validate each entry using konform validation
 * 4. Return list of parsed entries with original encoded form and index
 */
@Inject
@SingleIn(SessionScope::class)
class ParseTransactionDataCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<ParseTransactionDataArgs, ParseTransactionDataResult>(
        commandId = ParseTransactionDataCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ParseTransactionDataArgs>(),
        outputTypeToken = typeToken<ParseTransactionDataResult>(),
    ),
    ParseTransactionDataCommand,
    ParseTransactionDataCommandService {
    override val commandId: String get() = ParseTransactionDataCommand.COMMAND_ID

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    override suspend fun supports(args: Any): Boolean = args is ParseTransactionDataArgs

    override suspend fun parseTransactionData(transactionData: List<String>): IdkResult<ParseTransactionDataResult, IdkError> = execute(ParseTransactionDataArgs(transactionData))

    override suspend fun doExecute(
        args: ParseTransactionDataArgs,
        applyDuring: (ParseTransactionDataArgs) -> ParseTransactionDataArgs,
    ): IdkResult<ParseTransactionDataResult, IdkError> {
        val processedArgs = applyDuring(args)
        val transactionData = processedArgs.transactionData

        if (transactionData.isEmpty()) {
            return Ok(ParseTransactionDataResult(emptyList()))
        }

        val parsedEntries = mutableListOf<ParsedTransactionDataEntry>()

        for ((index, encoded) in transactionData.withIndex()) {
            // 1. Decode base64url to UTF-8 string
            val jsonString =
                try {
                    encoded.decodeFromBase64Url().decodeToString()
                } catch (expected: Exception) {
                    log.error("Failed to decode base64url transaction data at index $index: ${expected.message}")
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Failed to decode transaction data at index $index: Invalid base64url encoding",
                            throwable = expected,
                        ),
                    )
                }

            // 2. Parse JSON
            val jsonObject =
                try {
                    json.parseToJsonElement(jsonString) as? JsonObject
                        ?: return Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "Transaction data at index $index is not a JSON object",
                            ),
                        )
                } catch (expected: Exception) {
                    log.error("Failed to parse JSON transaction data at index $index: ${expected.message}")
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Failed to parse transaction data at index $index: Invalid JSON",
                            throwable = expected,
                        ),
                    )
                }

            // 3. Extract required fields
            val type =
                jsonObject["type"]?.jsonPrimitive?.contentOrNull
                    ?: return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Transaction data at index $index is missing required 'type' field",
                        ),
                    )

            val credentialIds =
                try {
                    jsonObject["credential_ids"]?.jsonArray?.map { it.jsonPrimitive.content }
                        ?: return Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "Transaction data at index $index is missing required 'credential_ids' field",
                            ),
                        )
                } catch (expected: Exception) {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Transaction data at index $index has invalid 'credential_ids' format: expected array of strings",
                        ),
                    )
                }

            val transactionDataHashesAlg =
                try {
                    jsonObject["transaction_data_hashes_alg"]?.jsonArray?.map { it.jsonPrimitive.content }
                } catch (expected: Exception) {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Transaction data at index $index has invalid 'transaction_data_hashes_alg' format: expected array of strings",
                        ),
                    )
                }

            // 4. Build additional properties (everything except standard fields)
            val standardFields = setOf("type", "credential_ids", "transaction_data_hashes_alg")
            val additionalProperties = jsonObject.filterKeys { it !in standardFields }
            val additionalPropsObject =
                if (additionalProperties.isNotEmpty()) {
                    JsonObject(additionalProperties)
                } else {
                    null
                }

            // 5. Create TransactionDataEntry
            val entry =
                TransactionDataEntry(
                    type = type,
                    credentialIds = credentialIds,
                    transactionDataHashesAlg = transactionDataHashesAlg,
                    additionalProperties = additionalPropsObject,
                )

            // 6. Validate the entry
            val validationResult = validateTransactionDataEntry(entry)
            if (validationResult is Invalid) {
                val errors = validationResult.errors.map { "${it.dataPath}: ${it.message}" }
                log.error("Transaction data at index $index failed validation: $errors")
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Transaction data at index $index is invalid: ${errors.joinToString(", ")}",
                    ),
                )
            }

            // 7. Add to result
            parsedEntries.add(
                ParsedTransactionDataEntry(
                    transactionData = entry,
                    transactionDataIndex = index,
                    encoded = encoded,
                ),
            )
        }

        log.debug("Successfully parsed ${parsedEntries.size} transaction data entries")
        return Ok(ParseTransactionDataResult(parsedEntries))
    }
}
