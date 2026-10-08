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

package com.sphereon.did.methods.webvh.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.did.models.DidDocument
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * `did:webvh` v1.0 §3 log entry.
 *
 * One JSON object per `did.jsonl` line; the resolver replays entries in
 * document order to reconstruct DID document state at any version.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("WebvhLogEntry", exact = true)
@JsExportCompat
@Serializable
data class WebvhLogEntry(
    /** `<n>-<entryHash>` per spec §3.1. */
    val versionId: String,
    /** ISO-8601 UTC; strictly increasing across the log; ≤ now at resolution time. */
    val versionTime: String,
    /** Sticky parameters (inheriting from prior entries). */
    val parameters: WebvhParameters,
    /** DID document state at this version. */
    val state: DidDocument,
    /**
     * One or more Data Integrity proofs over the entry. Per spec: signed by
     * a key authorized in the active `updateKeys` (the previous entry's keys
     * unless pre-rotation is active, in which case this entry's keys).
     *
     * Defaulted to `emptyList()` so the entry can be modelled mid-construction
     * (placeholder substitution, hash computation) before the proof is
     * attached. Replay/verification rejects entries that arrive over the
     * wire with an empty proof list.
     */
    val proof: List<DataIntegrityProof> = emptyList(),
)
