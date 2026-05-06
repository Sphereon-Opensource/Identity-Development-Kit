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

package com.sphereon.did.methods.webvh.resolver

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.did.methods.webvh.model.WebvhLogEntry
import com.sphereon.did.methods.webvh.model.WebvhParameters
import com.sphereon.did.methods.webvh.model.WebvhVersionId
import com.sphereon.did.methods.webvh.model.WebvhWitnessFile
import com.sphereon.did.methods.webvh.scid.WebvhEntryHasher
import com.sphereon.did.methods.webvh.scid.WebvhPreRotationHasher
import com.sphereon.did.methods.webvh.scid.WebvhScidComputer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WebvhLogReplayer>())
class WebvhLogReplayerImpl(
    private val proofVerifier: WebvhEntryProofVerifier,
    private val witnessProofVerifier: WebvhWitnessProofVerifier,
) : WebvhLogReplayer {
    private val json =
        Json {
            encodeDefaults = false
            explicitNulls = false
        }

    override suspend fun replay(
        did: String,
        entries: List<WebvhLogEntry>,
        witnessFile: WebvhWitnessFile?,
        selector: ReplaySelector,
    ): IdkResult<ReplayResult, IdkError> {
        if (entries.isEmpty()) {
            return Err(IdkError.fromString("Empty did:webvh log", code = ERROR_PARSING))
        }
        var state = ReplayState(currentIsoUtc())
        for ((index, entry) in entries.withIndex()) {
            val outcome = validateAndApplyEntry(entry, index, state)
            if (outcome.isErr) {
                return Err(outcome.error)
            }
            state = outcome.value
        }
        verifyWitnessFile(
            state.activeParams,
            witnessFile,
            state.replayed
                .last()
                .first.versionId
        ).getOrElseErr { return Err(it) }
        val (selectedEntry, selectedParams) =
            selectEntry(state.replayed, selector)
                ?: return Err(IdkError.fromString("Replay selector did not match any entry", code = ERROR_NOT_FOUND))
        return Ok(
            ReplayResult(
                didDocument = selectedEntry.state,
                selectedEntry = selectedEntry,
                activeParameters = selectedParams,
            ),
        )
    }

    /**
     * Per-entry validation: versionId structure, parameter merge + sticky
     * rules, genesis SCID, versionTime monotonicity, entry hash, pre-rotation
     * chain, proof signature against active updateKeys.
     */
    private suspend fun validateAndApplyEntry(
        entry: WebvhLogEntry,
        index: Int,
        state: ReplayState,
    ): IdkResult<ReplayState, IdkError> {
        val versionNumber = index + 1
        val versionId =
            WebvhVersionId.tryParse(entry.versionId)
                ?: return Err(IdkError.fromString("Entry $versionNumber has invalid versionId '${entry.versionId}'", code = ERROR_PARSING))
        if (versionId.versionNumber != versionNumber) {
            return Err(
                IdkError.fromString(
                    "Entry $versionNumber has versionId '${entry.versionId}' (expected version=$versionNumber)",
                    code = ERROR_PARSING,
                ),
            )
        }
        val merged =
            mergeParameters(state.activeParams, entry.parameters, isGenesis = (state.prevEntry == null))
                .getOrElseErr { return Err(it) }
        if (state.prevEntry == null) {
            verifyScid(entry, merged).getOrElseErr { return Err(it) }
        }
        validateVersionTime(entry, state, versionNumber).getOrElseErr { return Err(it) }
        validateEntryHash(entry, versionId, state, merged, versionNumber).getOrElseErr { return Err(it) }
        val activeUpdateKeys =
            activeUpdateKeysFor(state, merged, versionNumber)
                .getOrElseErr { return Err(it) }
        verifyEntryProof(entry, activeUpdateKeys, versionNumber).getOrElseErr { return Err(it) }

        state.replayed.add(entry to merged)
        return Ok(state.copy(activeParams = merged, prevEntry = entry))
    }

    private fun validateVersionTime(
        entry: WebvhLogEntry,
        state: ReplayState,
        versionNumber: Int,
    ): IdkResult<Unit, IdkError> {
        if (entry.versionTime > state.nowIso) {
            return Err(
                IdkError.fromString(
                    "Entry $versionNumber versionTime '${entry.versionTime}' is in the future (now=${state.nowIso})",
                    code = ERROR_PARSING,
                ),
            )
        }
        val prior = state.prevEntry
        if (prior != null && entry.versionTime <= prior.versionTime) {
            return Err(
                IdkError.fromString(
                    "Entry $versionNumber versionTime '${entry.versionTime}' is not strictly after prior '${prior.versionTime}'",
                    code = ERROR_PARSING,
                ),
            )
        }
        return Ok(Unit)
    }

    private fun validateEntryHash(
        entry: WebvhLogEntry,
        versionId: WebvhVersionId,
        state: ReplayState,
        merged: WebvhParameters,
        versionNumber: Int,
    ): IdkResult<Unit, IdkError> {
        val predecessor =
            state.prevEntry?.versionId
                ?: requireNotNull(merged.scid) { "merged.scid must be non-null at genesis" }
        val expectedHash = recomputeEntryHash(entry, predecessor)
        if (versionId.entryHash != expectedHash) {
            return Err(
                IdkError.fromString(
                    "Entry $versionNumber versionId hash mismatch: log says ${versionId.entryHash}, recomputed $expectedHash",
                    code = ERROR_PARSING,
                ),
            )
        }
        return Ok(Unit)
    }

    /**
     * Determine which `updateKeys` set authorizes the entry's proof.
     * Pre-rotation rule (spec §3.3.1): when active prior had `nextKeyHashes`,
     * the entry signs with this entry's `updateKeys` (rotation-then-sign);
     * otherwise the previous entry's `updateKeys` apply.
     */
    private fun activeUpdateKeysFor(
        state: ReplayState,
        merged: WebvhParameters,
        versionNumber: Int,
    ): IdkResult<List<String>, IdkError> {
        val prior = state.activeParams
        val priorNextKeyHashes = prior.nextKeyHashes
        val preRotationActive = state.prevEntry != null && !priorNextKeyHashes.isNullOrEmpty()
        if (preRotationActive) {
            verifyPreRotation(merged, priorNextKeyHashes!!, versionNumber).getOrElseErr { return Err(it) }
            return Ok(merged.updateKeys.orEmpty())
        }
        return Ok(prior.updateKeys.orEmpty().ifEmpty { merged.updateKeys.orEmpty() })
    }

    /**
     * Sticky parameter merge per `did:webvh` v1.0 §3.2.
     */
    private fun mergeParameters(
        prior: WebvhParameters,
        change: WebvhParameters,
        isGenesis: Boolean,
    ): IdkResult<WebvhParameters, IdkError> {
        val problem =
            if (isGenesis) {
                genesisProblem(change)
            } else {
                nonGenesisProblem(prior, change)
            }
        if (problem != null) {
            return Err(IdkError.fromString(problem, code = ERROR_PARSING))
        }
        if (isGenesis) {
            return Ok(change)
        }
        return Ok(
            prior.copy(
                method = change.method ?: prior.method,
                updateKeys = change.updateKeys ?: prior.updateKeys,
                nextKeyHashes = change.nextKeyHashes ?: prior.nextKeyHashes,
                witness = change.witness ?: prior.witness,
                watchers = change.watchers ?: prior.watchers,
                deactivated = change.deactivated ?: prior.deactivated,
                portable = change.portable ?: prior.portable,
                ttl = change.ttl ?: prior.ttl,
            ),
        )
    }

    /**
     * Recompute the genesis SCID by substituting placeholders, JCS-canonicalizing,
     * and comparing the result to `parameters.scid`.
     */
    private fun verifyScid(
        entry: WebvhLogEntry,
        params: WebvhParameters
    ): IdkResult<Unit, IdkError> {
        val scid = params.scid ?: return Err(IdkError.fromString("genesis missing scid", code = ERROR_PARSING))
        val entryWithoutProof = entry.copy(proof = emptyList(), versionId = WebvhScidComputer.PLACEHOLDER)
        val asJson = json.encodeToJsonElement(WebvhLogEntry.serializer(), entryWithoutProof).jsonObject
        val cleaned = JsonObject(asJson - PROOF)
        val placeholderForm = cleaned.toString().replace(scid, WebvhScidComputer.PLACEHOLDER)
        val placeholderEntry = json.parseToJsonElement(placeholderForm).jsonObject
        val recomputed = WebvhScidComputer.computeScid(placeholderEntry)
        if (recomputed != scid) {
            return Err(
                IdkError.fromString(
                    "Genesis SCID mismatch: log says $scid, recomputed $recomputed",
                    code = ERROR_PARSING,
                ),
            )
        }
        return Ok(Unit)
    }

    private fun recomputeEntryHash(
        entry: WebvhLogEntry,
        predecessor: String
    ): String {
        val entryForHash = entry.copy(versionId = predecessor, proof = emptyList())
        val asJson = json.encodeToJsonElement(WebvhLogEntry.serializer(), entryForHash).jsonObject
        val cleaned = JsonObject(asJson - PROOF)
        return WebvhEntryHasher.computeEntryHash(cleaned)
    }

    private fun verifyPreRotation(
        merged: WebvhParameters,
        prevPriorNextKeyHashes: List<String>,
        versionNumber: Int,
    ): IdkResult<Unit, IdkError> {
        for (mk in merged.updateKeys.orEmpty()) {
            val hashed = WebvhPreRotationHasher.hashMultikey(mk)
            if (hashed !in prevPriorNextKeyHashes) {
                return Err(
                    IdkError.fromString(
                        "Entry $versionNumber pre-rotation violation: updateKey '$mk' (hash=$hashed) not in prior nextKeyHashes",
                        code = ERROR_PARSING,
                    ),
                )
            }
        }
        return Ok(Unit)
    }

    private suspend fun verifyEntryProof(
        entry: WebvhLogEntry,
        activeUpdateKeys: List<String>,
        versionNumber: Int,
    ): IdkResult<Unit, IdkError> {
        if (entry.proof.isEmpty()) {
            return Err(IdkError.fromString("Entry $versionNumber has no proof", code = ERROR_PROOF_VERIFICATION))
        }
        if (activeUpdateKeys.isEmpty()) {
            return Err(
                IdkError.fromString(
                    "Entry $versionNumber has no active updateKeys to verify against",
                    code = ERROR_PROOF_VERIFICATION,
                ),
            )
        }
        val anyValid = proofVerifier.anyProofValid(entry, activeUpdateKeys)
        return if (anyValid) {
            Ok(Unit)
        } else {
            Err(
                IdkError.fromString(
                    "Entry $versionNumber: no proof verified against active updateKeys",
                    code = ERROR_PROOF_VERIFICATION,
                ),
            )
        }
    }

    private suspend fun verifyWitnessFile(
        params: WebvhParameters,
        witnessFile: WebvhWitnessFile?,
        latestVersionId: String,
    ): IdkResult<Unit, IdkError> {
        val config = params.witness ?: return Ok(Unit)
        if (config.threshold <= 0) {
            return Ok(Unit)
        }
        val file =
            witnessFile ?: return Err(
                IdkError.fromString(
                    "Witness threshold ${config.threshold} configured but did-witness.json not provided",
                    code = ERROR_PROOF_VERIFICATION,
                ),
            )
        // Per spec §3.4: count only proofs that (a) are signed by a witness DID
        // declared in `parameters.witness`, AND (b) actually verify against that
        // witness's `did:key` public key. A bare DID-presence count would let a
        // controller forge witness endorsements by inventing verificationMethods.
        val authorizedWitnessDids = config.witnesses.map { it.id }.toSet()
        val applicableProofs = file.proofs.filter { it.versionId == latestVersionId }
        val validatedWitnessDids = mutableSetOf<String>()
        for (entry in applicableProofs) {
            for (proof in entry.proof) {
                val witnessDid = proof.verificationMethod.substringBefore('#')
                if (witnessDid !in authorizedWitnessDids) {
                    continue
                }
                if (witnessProofVerifier.isValidWitnessProof(latestVersionId, proof)) {
                    validatedWitnessDids.add(witnessDid)
                }
            }
        }
        if (validatedWitnessDids.size < config.threshold) {
            return Err(
                IdkError.fromString(
                    "Witness threshold not met for $latestVersionId: " +
                        "${validatedWitnessDids.size} valid witness signature(s), need ${config.threshold}",
                    code = ERROR_PROOF_VERIFICATION,
                ),
            )
        }
        return Ok(Unit)
    }

    private fun selectEntry(
        replayed: List<Pair<WebvhLogEntry, WebvhParameters>>,
        selector: ReplaySelector,
    ): Pair<WebvhLogEntry, WebvhParameters>? =
        when (selector) {
            ReplaySelector.LATEST -> replayed.last()
            is ReplaySelector.ByVersionId -> replayed.firstOrNull { it.first.versionId == selector.versionId }
            is ReplaySelector.ByVersionNumber -> replayed.getOrNull(selector.versionNumber - 1)
            is ReplaySelector.ByVersionTime -> replayed.lastOrNull { it.first.versionTime <= selector.versionTime }
        }

    private fun currentIsoUtc(): String = Clock.System.now().toString()

    private data class ReplayState(
        val nowIso: String,
        val activeParams: WebvhParameters = WebvhParameters(),
        val prevEntry: WebvhLogEntry? = null,
        val replayed: MutableList<Pair<WebvhLogEntry, WebvhParameters>> = mutableListOf(),
    )

    companion object {
        private const val PROOF = "proof"
        private const val ERROR_PARSING = "PARSING_ERROR"
        private const val ERROR_PROOF_VERIFICATION = "PROOF_VERIFICATION_ERROR"
        private const val ERROR_NOT_FOUND = "NOT_FOUND"
    }
}

private inline fun <V, E> IdkResult<V, E>.getOrElseErr(onErr: (E) -> Nothing): V =
    if (isOk) {
        value
    } else {
        onErr(error)
    }

private fun genesisProblem(change: WebvhParameters): String? =
    when {
        change.method.isNullOrBlank() -> "Genesis entry MUST set parameters.method"
        change.scid.isNullOrBlank() -> "Genesis entry MUST set parameters.scid"
        change.updateKeys.isNullOrEmpty() -> "Genesis entry MUST set parameters.updateKeys"
        else -> null
    }

private fun nonGenesisProblem(
    prior: WebvhParameters,
    change: WebvhParameters
): String? =
    when {
        change.scid != null && change.scid != prior.scid -> {
            "parameters.scid MUST NOT change after genesis"
        }

        change.portable == true && prior.portable != true -> {
            "parameters.portable can only be set true in entry 1"
        }

        !prior.nextKeyHashes.isNullOrEmpty() && change.updateKeys == null -> {
            "parameters.updateKeys is required when pre-rotation is active"
        }

        else -> {
            null
        }
    }
