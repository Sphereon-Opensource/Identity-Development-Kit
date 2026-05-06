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
 *
 */

package com.sphereon.crypto.dataintegrity.algorithm

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.dataintegrity.cryptosuite.CryptosuiteVerification
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.model.DataIntegrityVerificationResult
import com.sphereon.crypto.dataintegrity.registry.CryptosuiteRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Orchestrator for the W3C VC-DI 1.0 §4.4 "Verify Proof" and §4.5
 * "Verify Proof Sets and Chains" algorithms.
 *
 * For each proof on the secured document, builds the input document the
 * cryptosuite needs (secured doc minus `proof`, with `proof` set to the
 * matching prior proofs the entry references via `previousProof`), looks
 * up the cryptosuite verifier from [CryptosuiteRegistry], and aggregates
 * per-proof outcomes into a single [DataIntegrityVerificationResult].
 */
interface VerifyProofAlgorithm {
    suspend fun verify(
        securedDocument: JsonObject,
        expectedProofPurpose: String? = null,
        expectedMediaType: String? = null,
    ): IdkResult<DataIntegrityVerificationResult, IdkError>
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<VerifyProofAlgorithm>())
class VerifyProofAlgorithmImpl(
    private val registry: CryptosuiteRegistry,
) : VerifyProofAlgorithm {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    override suspend fun verify(
        securedDocument: JsonObject,
        expectedProofPurpose: String?,
        expectedMediaType: String?,
    ): IdkResult<DataIntegrityVerificationResult, IdkError> {
        val proofObjects =
            extractProofObjects(securedDocument)
                .getOrElse { failureMessage -> return Ok(failedResult(failureMessage)) }

        val unsecuredDocument = JsonObject(securedDocument - PROOF)
        val accumulator = ProofAccumulator()

        for (proofJson in proofObjects) {
            verifyOneProof(
                proofJson = proofJson,
                proofObjects = proofObjects,
                unsecuredDocument = unsecuredDocument,
                expectedProofPurpose = expectedProofPurpose,
                accumulator = accumulator,
            )
        }
        return Ok(accumulator.toResult(unsecuredDocument, expectedMediaType))
    }

    /**
     * Validates the `proof` field's structure and returns the proofs as a
     * list. On failure returns a single [PARSING_ERROR] message via the
     * second branch of the result.
     */
    private fun extractProofObjects(securedDocument: JsonObject,): ResultOrFailure<List<JsonObject>> {
        val proofValue =
            securedDocument[PROOF]
                ?: return ResultOrFailure.Failure("PARSING_ERROR: securedDocument has no `proof` field")
        return when (proofValue) {
            is JsonObject -> {
                ResultOrFailure.Success(listOf(proofValue))
            }

            is JsonArray -> {
                if (proofValue.any { it !is JsonObject }) {
                    ResultOrFailure.Failure("PARSING_ERROR: proof array contains a non-object element")
                } else {
                    ResultOrFailure.Success(proofValue.map { it.jsonObject })
                }
            }

            else -> {
                ResultOrFailure.Failure("PARSING_ERROR: proof must be an object or array")
            }
        }
    }

    private suspend fun verifyOneProof(
        proofJson: JsonObject,
        proofObjects: List<JsonObject>,
        unsecuredDocument: JsonObject,
        expectedProofPurpose: String?,
        accumulator: ProofAccumulator,
    ) {
        val proof =
            decodeProof(proofJson) ?: run {
                accumulator.recordFailure("PROOF_VERIFICATION_ERROR: failed to parse proof", "Unparseable proof")
                return
            }
        val structureError = validateRequiredFields(proof, expectedProofPurpose)
        if (structureError != null) {
            accumulator.recordFailure(structureError.first, structureError.second)
            return
        }
        val matchingProofs = collectMatchingProofs(proof.previousProof, proofObjects)
        if (matchingProofs.isErr) {
            accumulator.recordFailure(matchingProofs.error.message.defaultMessage, "previousProof linkage error")
            return
        }
        val cryptosuiteInput = buildCryptosuiteInput(unsecuredDocument, matchingProofs.value)
        val verifier =
            registry.getVerifier(proof.cryptosuite) ?: run {
                accumulator.recordFailure(
                    "PROOF_VERIFICATION_ERROR: no cryptosuite verifier registered for '${proof.cryptosuite}'",
                    "Unknown cryptosuite",
                )
                return
            }
        val verifierResult = verifier.verifyProof(cryptosuiteInput, proof)
        if (verifierResult.isErr) {
            accumulator.recordFailure(
                "PROOF_VERIFICATION_ERROR: ${verifierResult.error.message.defaultMessage}",
                "Verifier returned IdkError",
            )
            return
        }
        accumulator.recordResult(proof, verifierResult.value)
    }

    private fun decodeProof(proofJson: JsonObject): DataIntegrityProof? =
        try {
            json.decodeFromJsonElement(DataIntegrityProof.serializer(), proofJson)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    /**
     * Returns null when the proof is structurally valid; otherwise a
     * (verbose, summary) error pair to aggregate.
     */
    private fun validateRequiredFields(
        proof: DataIntegrityProof,
        expectedProofPurpose: String?,
    ): Pair<String, String>? {
        if (proof.type.isBlank() || proof.verificationMethod.isBlank() || proof.proofValue.isBlank()) {
            return "PROOF_VERIFICATION_ERROR: proof is missing type/verificationMethod/proofValue" to
                "Required field missing"
        }
        if (expectedProofPurpose != null && proof.proofPurpose.value != expectedProofPurpose) {
            return "PROOF_VERIFICATION_ERROR: expected proofPurpose '$expectedProofPurpose', got '${proof.proofPurpose.value}'" to
                "proofPurpose mismatch"
        }
        return null
    }

    private fun buildCryptosuiteInput(
        unsecuredDocument: JsonObject,
        matchingProofs: List<JsonObject>,
    ): JsonObject =
        if (matchingProofs.isEmpty()) {
            unsecuredDocument
        } else {
            JsonObject(unsecuredDocument + (PROOF to JsonArray(matchingProofs)))
        }

    private fun collectMatchingProofs(
        previousProof: List<String>?,
        allProofs: List<JsonObject>,
    ): IdkResult<List<JsonObject>, IdkError> {
        if (previousProof.isNullOrEmpty()) {
            return Ok(emptyList())
        }
        val matched = mutableListOf<JsonObject>()
        for (id in previousProof) {
            val match =
                allProofs.firstOrNull { entry ->
                    val idPrim = entry["id"]?.jsonPrimitive
                    idPrim != null && idPrim.isString && idPrim.content == id
                }
            if (match == null) {
                return Err(
                    IdkError.fromString(
                        message = "PROOF_VERIFICATION_ERROR: previousProof '$id' not present in document",
                        code = "PROOF_VERIFICATION_ERROR",
                    ),
                )
            }
            matched.add(match)
        }
        return Ok(matched)
    }

    private fun failedResult(message: String): DataIntegrityVerificationResult =
        DataIntegrityVerificationResult(
            verified = false,
            verifiedDocument = null,
            mediaType = null,
            errors = listOf(message),
            proofs = emptyList(),
        )

    companion object {
        private const val PROOF = "proof"
    }
}

/**
 * Accumulates per-proof outcomes during a [VerifyProofAlgorithmImpl.verify] run,
 * keeping the orchestrator loop a flat sequence of `verifyOneProof` calls.
 */
private class ProofAccumulator {
    private val perProofResults = mutableListOf<CryptosuiteVerification>()
    private val verifiedProofs = mutableListOf<DataIntegrityProof>()
    private val errors = mutableListOf<String>()

    fun recordFailure(
        verboseError: String,
        summary: String
    ) {
        errors.add(verboseError)
        perProofResults.add(CryptosuiteVerification(verified = false, errors = listOf(summary)))
    }

    fun recordResult(
        proof: DataIntegrityProof,
        result: CryptosuiteVerification
    ) {
        perProofResults.add(result)
        errors.addAll(result.errors)
        if (result.verified) {
            verifiedProofs.add(proof)
        }
    }

    fun toResult(
        unsecuredDocument: JsonObject,
        expectedMediaType: String?,
    ): DataIntegrityVerificationResult {
        val combinedVerified = perProofResults.isNotEmpty() && perProofResults.all { it.verified }
        return DataIntegrityVerificationResult(
            verified = combinedVerified,
            verifiedDocument =
                if (combinedVerified) {
                    unsecuredDocument
                } else {
                    null
                },
            mediaType =
                if (combinedVerified) {
                    expectedMediaType
                } else {
                    null
                },
            warnings = emptyList(),
            errors = errors,
            proofs =
                if (combinedVerified) {
                    verifiedProofs
                } else {
                    emptyList()
                },
        )
    }
}

/**
 * Local variant of [IdkResult] for orchestration steps that only need a
 * success value or a single error message string.
 */
private sealed class ResultOrFailure<out T> {
    data class Success<T>(
        val value: T
    ) : ResultOrFailure<T>()

    data class Failure(
        val message: String
    ) : ResultOrFailure<Nothing>()
}

private inline fun <T> ResultOrFailure<T>.getOrElse(onFailure: (String) -> Nothing): T =
    when (this) {
        is ResultOrFailure.Success -> value
        is ResultOrFailure.Failure -> onFailure(message)
    }
