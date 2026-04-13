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

import io.konform.validation.Invalid
import io.konform.validation.Valid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for TransactionData models and validation.
 *
 * OpenID4VP 1.0 Section 5.1.2 defines transaction_data as:
 * "An array of base64url-encoded JSON objects, each representing a transaction data entry."
 */
class TransactionDataTest {
    private val json = Json { ignoreUnknownKeys = true }

    // =============================================================================
    // TransactionDataEntry Tests
    // =============================================================================

    @Test
    fun createSimpleTransactionDataEntry() {
        val entry =
            TransactionDataEntry(
                type = "openbanking",
                credentialIds = listOf("pid_credential"),
            )

        assertEquals("openbanking", entry.type)
        assertEquals(listOf("pid_credential"), entry.credentialIds)
        assertNull(entry.transactionDataHashesAlg)
        assertNull(entry.additionalProperties)
    }

    @Test
    fun createTransactionDataEntryWithHashAlgorithms() {
        val entry =
            TransactionDataEntry(
                type = "openbanking",
                credentialIds = listOf("pid_credential"),
                transactionDataHashesAlg = listOf("sha-256", "sha-384"),
            )

        assertEquals(listOf("sha-256", "sha-384"), entry.transactionDataHashesAlg)
    }

    @Test
    fun createTransactionDataEntryWithAdditionalProperties() {
        val additionalProps =
            buildJsonObject {
                put("payee_name", "ACME Corp")
                put("amount", "100.00")
                put("currency", "EUR")
            }

        val entry =
            TransactionDataEntry(
                type = "openbanking",
                credentialIds = listOf("pid_credential"),
                additionalProperties = additionalProps,
            )

        assertNotNull(entry.additionalProperties)
        assertEquals("ACME Corp", entry.additionalProperties!!["payee_name"].toString().trim('"'))
    }

    @Test
    fun getAllowedHashAlgorithmsReturnsDefaultWhenNotSpecified() {
        val entry =
            TransactionDataEntry(
                type = "test",
                credentialIds = listOf("cred1"),
            )

        assertEquals(listOf("sha-256"), entry.getAllowedHashAlgorithms())
    }

    @Test
    fun getAllowedHashAlgorithmsReturnsSpecifiedAlgorithms() {
        val entry =
            TransactionDataEntry(
                type = "test",
                credentialIds = listOf("cred1"),
                transactionDataHashesAlg = listOf("sha-384", "sha-512"),
            )

        assertEquals(listOf("sha-384", "sha-512"), entry.getAllowedHashAlgorithms())
    }

    @Test
    fun serializeAndDeserializeTransactionDataEntry() {
        val originalEntry =
            TransactionDataEntry(
                type = "openbanking",
                credentialIds = listOf("pid_credential"),
                transactionDataHashesAlg = listOf("sha-256"),
                additionalProperties =
                    buildJsonObject {
                        put("payee_name", "ACME Corp")
                    },
            )

        val jsonString = json.encodeToString(TransactionDataEntry.serializer(), originalEntry)
        val deserializedEntry = json.decodeFromString(TransactionDataEntry.serializer(), jsonString)

        assertEquals(originalEntry.type, deserializedEntry.type)
        assertEquals(originalEntry.credentialIds, deserializedEntry.credentialIds)
        assertEquals(originalEntry.transactionDataHashesAlg, deserializedEntry.transactionDataHashesAlg)
    }

    @Test
    fun parseTransactionDataEntryFromJson() {
        val jsonString =
            """
            {
              "type": "openbanking",
              "credential_ids": ["pid_credential"],
              "transaction_data_hashes_alg": ["sha-256"]
            }
            """.trimIndent()

        val entry = json.decodeFromString(TransactionDataEntry.serializer(), jsonString)

        assertEquals("openbanking", entry.type)
        assertEquals(listOf("pid_credential"), entry.credentialIds)
        assertEquals(listOf("sha-256"), entry.transactionDataHashesAlg)
    }

    @Test
    fun parseTransactionDataEntryWithMultipleCredentialIds() {
        val jsonString =
            """
            {
              "type": "mdoc_handover",
              "credential_ids": ["pid_credential", "mDL", "eID"]
            }
            """.trimIndent()

        val entry = json.decodeFromString(TransactionDataEntry.serializer(), jsonString)

        assertEquals(listOf("pid_credential", "mDL", "eID"), entry.credentialIds)
    }

    // =============================================================================
    // TransactionDataHashes Tests
    // =============================================================================

    @Test
    fun createTransactionDataHashesWithDefaultAlgorithm() {
        val hashes =
            TransactionDataHashes(
                transactionDataHashes = listOf("aGVsbG8gd29ybGQ"),
            )

        assertEquals(listOf("aGVsbG8gd29ybGQ"), hashes.transactionDataHashes)
        assertEquals("sha-256", hashes.transactionDataHashesAlg)
    }

    @Test
    fun createTransactionDataHashesWithCustomAlgorithm() {
        val hashes =
            TransactionDataHashes(
                transactionDataHashes = listOf("aGVsbG8gd29ybGQ"),
                transactionDataHashesAlg = "sha-384",
            )

        assertEquals("sha-384", hashes.transactionDataHashesAlg)
    }

    // =============================================================================
    // TransactionDataHashAlgorithm Tests
    // =============================================================================

    @Test
    fun transactionDataHashAlgorithmFromValueReturnsCorrectEnum() {
        assertEquals(TransactionDataHashAlgorithm.SHA_256, TransactionDataHashAlgorithm.fromValue("sha-256"))
        assertEquals(TransactionDataHashAlgorithm.SHA_384, TransactionDataHashAlgorithm.fromValue("sha-384"))
        assertEquals(TransactionDataHashAlgorithm.SHA_512, TransactionDataHashAlgorithm.fromValue("sha-512"))
    }

    @Test
    fun transactionDataHashAlgorithmFromValueReturnsNullForUnknown() {
        assertNull(TransactionDataHashAlgorithm.fromValue("md5"))
        assertNull(TransactionDataHashAlgorithm.fromValue("sha-1"))
    }

    @Test
    fun transactionDataHashAlgorithmIsSupportedChecksCorrectly() {
        assertTrue(TransactionDataHashAlgorithm.isSupported("sha-256"))
        assertTrue(TransactionDataHashAlgorithm.isSupported("sha-384"))
        assertTrue(TransactionDataHashAlgorithm.isSupported("sha-512"))
        assertFalse(TransactionDataHashAlgorithm.isSupported("md5"))
        assertFalse(TransactionDataHashAlgorithm.isSupported("sha-1"))
    }

    // =============================================================================
    // Validation Tests
    // =============================================================================

    @Test
    fun validateTransactionDataEntryAcceptsValidEntry() {
        val entry =
            TransactionDataEntry(
                type = "openbanking",
                credentialIds = listOf("pid_credential"),
                transactionDataHashesAlg = listOf("sha-256"),
            )

        val result = validateTransactionDataEntry(entry)
        assertTrue(result is Valid)
    }

    @Test
    fun validateTransactionDataEntryRejectsEmptyType() {
        val entry =
            TransactionDataEntry(
                type = "",
                credentialIds = listOf("pid_credential"),
            )

        val result = validateTransactionDataEntry(entry)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("type") || it.message.contains("empty") })
    }

    @Test
    fun validateTransactionDataEntryRejectsBlankType() {
        val entry =
            TransactionDataEntry(
                type = "   ",
                credentialIds = listOf("pid_credential"),
            )

        val result = validateTransactionDataEntry(entry)
        assertTrue(result is Invalid)
    }

    @Test
    fun validateTransactionDataEntryRejectsEmptyCredentialIds() {
        val entry =
            TransactionDataEntry(
                type = "openbanking",
                credentialIds = emptyList(),
            )

        val result = validateTransactionDataEntry(entry)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("credential") || it.message.contains("At least one") })
    }

    @Test
    fun validateTransactionDataEntryRejectsEmptyCredentialIdInList() {
        val entry =
            TransactionDataEntry(
                type = "openbanking",
                credentialIds = listOf("valid", ""),
            )

        val result = validateTransactionDataEntry(entry)
        assertTrue(result is Invalid)
    }

    @Test
    fun validateTransactionDataEntryRejectsUnsupportedHashAlgorithm() {
        val entry =
            TransactionDataEntry(
                type = "openbanking",
                credentialIds = listOf("pid_credential"),
                transactionDataHashesAlg = listOf("sha-256", "md5"),
            )

        val result = validateTransactionDataEntry(entry)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("hash") || it.message.contains("supported") })
    }

    @Test
    fun validateTransactionDataHashesAcceptsValidHashes() {
        val hashes =
            TransactionDataHashes(
                transactionDataHashes = listOf("aGVsbG8gd29ybGQ"),
                transactionDataHashesAlg = "sha-256",
            )

        val result = validateTransactionDataHashes(hashes)
        assertTrue(result is Valid)
    }

    @Test
    fun validateTransactionDataHashesRejectsEmptyHashesList() {
        val hashes =
            TransactionDataHashes(
                transactionDataHashes = emptyList(),
            )

        val result = validateTransactionDataHashes(hashes)
        assertTrue(result is Invalid)
    }

    @Test
    fun validateTransactionDataHashesRejectsUnsupportedAlgorithm() {
        val hashes =
            TransactionDataHashes(
                transactionDataHashes = listOf("aGVsbG8gd29ybGQ"),
                transactionDataHashesAlg = "md5",
            )

        val result = validateTransactionDataHashes(hashes)
        assertTrue(result is Invalid)
    }

    // =============================================================================
    // ParsedTransactionDataEntry Tests
    // =============================================================================

    @Test
    fun createParsedTransactionDataEntry() {
        val entry =
            TransactionDataEntry(
                type = "openbanking",
                credentialIds = listOf("pid_credential"),
            )

        val parsed =
            ParsedTransactionDataEntry(
                transactionData = entry,
                transactionDataIndex = 0,
                encoded = "eyJ0eXBlIjoib3BlbmJhbmtpbmciLCJjcmVkZW50aWFsX2lkcyI6WyJwaWRfY3JlZGVudGlhbCJdfQ",
            )

        assertEquals(entry, parsed.transactionData)
        assertEquals(0, parsed.transactionDataIndex)
        assertNotNull(parsed.encoded)
    }

    // =============================================================================
    // Verified Transaction Data Tests
    // =============================================================================

    @Test
    fun createVerifiedTransactionDataEntry() {
        val entry =
            TransactionDataEntry(
                type = "openbanking",
                credentialIds = listOf("pid_credential"),
            )

        val parsed =
            ParsedTransactionDataEntry(
                transactionData = entry,
                transactionDataIndex = 0,
                encoded = "base64url",
            )

        val verified =
            VerifiedTransactionDataEntry(
                transactionDataEntry = parsed,
                credentialId = "pid_credential",
                presentations =
                    listOf(
                        VerifiedTransactionDataPresentation(
                            presentationIndex = 0,
                            hash = "computed_hash",
                            hashAlg = "sha-256",
                            credentialHashIndex = 0,
                        ),
                    ),
            )

        assertEquals("pid_credential", verified.credentialId)
        assertEquals(1, verified.presentations.size)
        assertEquals("sha-256", verified.presentations[0].hashAlg)
    }

    // =============================================================================
    // Error Type Tests
    // =============================================================================

    @Test
    fun transactionDataErrorParseErrorContainsDetails() {
        val error =
            TransactionDataError.ParseError(
                message = "Failed to decode base64url",
                index = 1,
                cause = IllegalArgumentException("Invalid character"),
            )

        assertEquals("Failed to decode base64url", error.message)
        assertEquals(1, error.index)
        assertNotNull(error.cause)
    }

    @Test
    fun transactionDataErrorHashMismatchErrorContainsVerificationDetails() {
        val error =
            TransactionDataError.HashMismatchError(
                message = "Hash does not match",
                transactionDataIndex = 0,
                credentialId = "pid_credential",
                presentationIndex = 0,
                expectedHash = "expected",
                actualHashes = listOf("actual1", "actual2"),
            )

        assertEquals(0, error.transactionDataIndex)
        assertEquals("pid_credential", error.credentialId)
        assertEquals("expected", error.expectedHash)
        assertEquals(2, error.actualHashes.size)
    }

    @Test
    fun transactionDataErrorNoMatchingCredentialErrorContainsCredentialInfo() {
        val error =
            TransactionDataError.NoMatchingCredentialError(
                message = "No matching credential found",
                transactionDataIndex = 0,
                credentialIds = listOf("cred1", "cred2"),
            )

        assertEquals(listOf("cred1", "cred2"), error.credentialIds)
    }

    @Test
    fun transactionDataErrorUnsupportedHashAlgorithmErrorContainsSupportedList() {
        val error =
            TransactionDataError.UnsupportedHashAlgorithmError(
                message = "Unsupported algorithm",
                algorithm = "md5",
            )

        assertEquals("md5", error.algorithm)
        assertTrue(error.supportedAlgorithms.contains("sha-256"))
        assertTrue(error.supportedAlgorithms.contains("sha-384"))
        assertTrue(error.supportedAlgorithms.contains("sha-512"))
    }

    // =============================================================================
    // OpenID4VP Spec Example Tests
    // =============================================================================

    @Test
    fun parseOpenID4VPSpecExampleOpenbankingTransaction() {
        // Example from OpenID4VP 1.0 Section 5.1.2
        val jsonString =
            """
            {
              "type": "openbanking",
              "credential_ids": ["pid_credential"],
              "transaction_data_hashes_alg": ["sha-256"]
            }
            """.trimIndent()

        val entry = json.decodeFromString(TransactionDataEntry.serializer(), jsonString)

        assertEquals("openbanking", entry.type)
        assertEquals(listOf("pid_credential"), entry.credentialIds)
        assertEquals(listOf("sha-256"), entry.transactionDataHashesAlg)

        val validationResult = validateTransactionDataEntry(entry)
        assertTrue(validationResult is Valid)
    }

    @Test
    fun defaultHashAlgorithmConstantIsSha256() {
        assertEquals("sha-256", TransactionDataEntry.DEFAULT_HASH_ALGORITHM)
    }
}
