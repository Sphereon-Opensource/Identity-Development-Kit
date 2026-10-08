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

import com.sphereon.core.api.IdkResult
import com.sphereon.did.methods.webvh.model.WebvhLogEntry
import com.sphereon.did.methods.webvh.model.WebvhParameters
import com.sphereon.did.methods.webvh.model.WebvhWitnessFile
import com.sphereon.did.models.DidDocument

/**
 * Replays a `did:webvh` v1.0 log, validating each entry against the spec
 * (entry hash, versionTime monotonicity, SCID, proof signatures over the
 * active updateKeys, pre-rotation `nextKeyHashes` chain, and witness
 * threshold), and returns the resolved DID document at a chosen version.
 */
interface WebvhLogReplayer {
    suspend fun replay(
        did: String,
        entries: List<WebvhLogEntry>,
        witnessFile: WebvhWitnessFile? = null,
        selector: ReplaySelector = ReplaySelector.LATEST,
    ): IdkResult<ReplayResult, com.sphereon.core.api.error.IdkError>
}

/**
 * Which entry to surface from the replayed log.
 */
sealed interface ReplaySelector {
    data object LATEST : ReplaySelector

    data class ByVersionId(
        val versionId: String
    ) : ReplaySelector

    data class ByVersionNumber(
        val versionNumber: Int
    ) : ReplaySelector

    data class ByVersionTime(
        val versionTime: String
    ) : ReplaySelector
}

/**
 * Successful replay result. [didDocument] is the state at the selected
 * entry, with all its predecessors validated. [activeParameters] is the
 * effective sticky-parameter set at that entry (after inheritance).
 */
data class ReplayResult(
    val didDocument: DidDocument,
    val selectedEntry: WebvhLogEntry,
    val activeParameters: WebvhParameters,
)
