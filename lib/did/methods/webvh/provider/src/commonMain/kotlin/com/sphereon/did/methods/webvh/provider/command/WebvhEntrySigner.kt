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

package com.sphereon.did.methods.webvh.provider.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.dataintegrity.command.AddProofInput
import com.sphereon.crypto.dataintegrity.command.AddProofServiceCommand
import com.sphereon.crypto.dataintegrity.eddsajcs2022.EddsaJcs2022Cryptosuite
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.model.ProofOptions
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.did.methods.webvh.model.WebvhLogEntry
import com.sphereon.did.methods.webvh.scid.WebvhEntryHasher
import dev.zacsweers.metro.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Shared helper used by Create / Update / Deactivate to compute the entry
 * hash for an unsigned log entry, derive the final `versionId`, and attach
 * a single eddsa-jcs-2022 Data Integrity proof signed by the supplied key
 * reference.
 */
@Inject
class WebvhEntrySigner(
    private val addProofCommand: AddProofServiceCommand,
) {
    private val json =
        Json {
            encodeDefaults = false
            explicitNulls = false
            prettyPrint = false
        }

    /**
     * @param entryWithoutProof a fully-formed entry with `proof = emptyList()` and
     *   `versionId` set to the predecessor versionId (SCID for entry 1, prior
     *   entry's versionId otherwise). The caller is responsible for placeholder
     *   substitution before calling this method.
     * @param versionNumber 1-based version number for the new entry.
     * @param signingKeyRef KMS ref of the signing key.
     * @param verificationMethod DID URL pointing to the public key for
     *   `signingKeyRef` inside the entry's state (e.g. `did:webvh:...#key-1`).
     */
    suspend fun signEntry(
        entryWithoutProof: WebvhLogEntry,
        versionNumber: Int,
        signingKeyRef: String,
        verificationMethod: String,
        createdAt: String,
    ): IdkResult<WebvhLogEntry, IdkError> {
        // Entry hash uses the entry with predecessor versionId baked in and proof removed.
        val asJson = json.encodeToJsonElement(WebvhLogEntry.serializer(), entryWithoutProof).jsonObject
        val cleaned = JsonObject(asJson - PROOF_FIELD)
        val entryHash = WebvhEntryHasher.computeEntryHash(cleaned)
        val finalVersionId = "$versionNumber-$entryHash"

        // Re-emit entry with finalVersionId for the proof-signing step.
        val entryReadyToSign = entryWithoutProof.copy(versionId = finalVersionId)
        val unsecuredJson = json.encodeToJsonElement(WebvhLogEntry.serializer(), entryReadyToSign).jsonObject
        val unsecuredCleaned = JsonObject(unsecuredJson - PROOF_FIELD)

        val proofOptions =
            ProofOptions(
                cryptosuite = EddsaJcs2022Cryptosuite.ID,
                verificationMethod = verificationMethod,
                proofPurpose = ProofPurpose.ASSERTION_METHOD,
                signingKeyRef = signingKeyRef,
                created = createdAt,
            )
        val signed = addProofCommand.execute(AddProofInput(unsecuredDocument = unsecuredCleaned, proofs = listOf(proofOptions)))
        if (signed.isErr) {
            return Err(signed.error)
        }
        val securedDoc = signed.value.securedDocument
        val proofElement =
            securedDoc[PROOF_FIELD]
                ?: return Err(IdkError.fromString("AddProof returned secured document without proof", code = ERROR_PROOF_GENERATION))
        val proofObj =
            proofElement as? JsonObject
                ?: return Err(IdkError.fromString("Webvh entry signing produced a non-single proof", code = ERROR_PROOF_GENERATION))
        val diProof =
            try {
                json.decodeFromJsonElement(DataIntegrityProof.serializer(), proofObj)
            } catch (expected: Exception) {
                return Err(IdkError.fromString("Failed to decode signed proof: ${expected.message}", code = ERROR_PROOF_GENERATION, exception = expected))
            }

        return Ok(entryReadyToSign.copy(proof = listOf(diProof)))
    }

    private companion object {
        private const val PROOF_FIELD = "proof"
        private const val ERROR_PROOF_GENERATION = "PROOF_GENERATION_ERROR"
    }
}
