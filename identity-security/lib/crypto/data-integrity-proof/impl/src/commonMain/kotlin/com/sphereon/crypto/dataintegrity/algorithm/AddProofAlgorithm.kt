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
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.model.ProofOptions
import com.sphereon.crypto.dataintegrity.registry.CryptosuiteRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Orchestrator for the W3C VC-DI 1.0 §4.2 "Add Proof" and §4.3
 * "Add Proof Set/Chain" algorithms.
 *
 * Iterates through the supplied [ProofOptions] in order, calling the matching
 * cryptosuite's `createProof` for each. For chain entries (those whose
 * `previousProof` is non-null), populates the cryptosuite's input document
 * with the referenced prior proofs as required by §4.3 step 6 — this is what
 * binds chain links cryptographically to their predecessors.
 */
interface AddProofAlgorithm {
    suspend fun addProofs(
        unsecuredDocument: JsonObject,
        proofs: List<ProofOptions>,
    ): IdkResult<JsonObject, IdkError>
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AddProofAlgorithm>())
class AddProofAlgorithmImpl(
    private val registry: CryptosuiteRegistry,
) : AddProofAlgorithm {
    private val json =
        Json {
            encodeDefaults = false
            explicitNulls = false
        }

    override suspend fun addProofs(
        unsecuredDocument: JsonObject,
        proofs: List<ProofOptions>,
    ): IdkResult<JsonObject, IdkError> {
        validateInputs(unsecuredDocument, proofs).getOrElse { return Err(it) }

        val produced = mutableListOf<JsonObject>()
        for ((index, options) in proofs.withIndex()) {
            val newProof =
                signOneProof(unsecuredDocument, options, produced, index)
                    .getOrElse { return Err(it) }
            produced.add(json.encodeToJsonElement(DataIntegrityProof.serializer(), newProof).jsonObject)
        }
        return Ok(JsonObject(unsecuredDocument + (PROOF to assembleProofValue(produced, proofs))))
    }

    private fun validateInputs(
        unsecuredDocument: JsonObject,
        proofs: List<ProofOptions>,
    ): IdkResult<Unit, IdkError> {
        if (proofs.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "AddProof: no proof options supplied"))
        }
        if (unsecuredDocument.containsKey(PROOF)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "AddProof: input must be unsecured (no `proof` field)"))
        }
        return Ok(Unit)
    }

    private suspend fun signOneProof(
        unsecuredDocument: JsonObject,
        options: ProofOptions,
        produced: List<JsonObject>,
        index: Int,
    ): IdkResult<DataIntegrityProof, IdkError> {
        val matchingProofs =
            collectMatchingProofs(options.previousProof, produced, index)
                .getOrElse { return Err(it) }
        val inputDocument: JsonObject =
            if (matchingProofs.isEmpty()) {
                unsecuredDocument
            } else {
                JsonObject(unsecuredDocument + (PROOF to JsonArray(matchingProofs)))
            }
        val creator =
            registry.getCreator(options.cryptosuite)
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "AddProof: no cryptosuite creator registered for '${options.cryptosuite}'",
                    ),
                )
        val newProof = creator.createProof(inputDocument, options).getOrElse { return Err(it) }
        validateGeneratedProof(newProof, options).getOrElse { return Err(it) }
        return Ok(newProof)
    }

    private fun assembleProofValue(
        produced: List<JsonObject>,
        options: List<ProofOptions>
    ): JsonElement {
        val singleProof = produced.size == 1 && options.single().previousProof == null
        return if (singleProof) {
            produced.single()
        } else {
            JsonArray(produced)
        }
    }

    private fun collectMatchingProofs(
        previousProof: List<String>?,
        produced: List<JsonObject>,
        currentIndex: Int,
    ): IdkResult<List<JsonObject>, IdkError> {
        if (previousProof.isNullOrEmpty()) {
            return Ok(emptyList())
        }
        val matched = mutableListOf<JsonObject>()
        for (id in previousProof) {
            val match =
                produced.firstOrNull { entry ->
                    val idPrim = entry["id"]?.jsonPrimitive
                    idPrim != null && idPrim.isString && idPrim.content == id
                }
            if (match == null) {
                return Err(
                    IdkError.fromString(
                        message = "AddProof: previousProof '$id' (chain entry $currentIndex) not produced earlier in the chain",
                        code = ERROR_CODE_GENERATION,
                    ),
                )
            }
            matched.add(match)
        }
        return Ok(matched)
    }

    private fun validateGeneratedProof(
        proof: DataIntegrityProof,
        options: ProofOptions,
    ): IdkResult<Unit, IdkError> {
        val problem =
            when {
                proof.type.isBlank() || proof.verificationMethod.isBlank() || proof.proofValue.isBlank() -> {
                    "cryptosuite returned proof with missing type/verificationMethod/proofValue"
                }

                options.domain != null && proof.domain != options.domain -> {
                    "proof.domain '${proof.domain}' does not match options.domain '${options.domain}'"
                }

                options.domainSet != null && proof.domainSet != options.domainSet -> {
                    "proof.domain set does not match options.domainSet"
                }

                options.challenge != null && proof.challenge != options.challenge -> {
                    "proof.challenge does not match options.challenge"
                }

                else -> {
                    null
                }
            }
        if (problem != null) {
            return Err(IdkError.fromString(message = "AddProof: $problem", code = ERROR_CODE_GENERATION))
        }
        return Ok(Unit)
    }

    companion object {
        private const val PROOF = "proof"
        private const val ERROR_CODE_GENERATION = "PROOF_GENERATION_ERROR"
    }
}

private inline fun <V, E> IdkResult<V, E>.getOrElse(onErr: (E) -> Nothing): V =
    if (isOk) {
        value
    } else {
        onErr(error)
    }
