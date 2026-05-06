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

package com.sphereon.did.methods.webvh.command

import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.PublicApiCommand
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.did.methods.webvh.model.WebvhLogEntry
import com.sphereon.did.methods.webvh.model.WebvhParameters
import com.sphereon.did.methods.webvh.model.WebvhWitnessFile
import com.sphereon.did.models.DidDocument
import kotlinx.serialization.Serializable

/**
 * Validates a fully-formed `did:webvh` log + optional witness file and
 * returns the resolved DID document at a chosen version. Offline equivalent
 * of `did.webvh.fetch-log` (which fetches + parses + replays in one step):
 * use this when you already hold the log JSONL and want validation +
 * resolution without the HTTP fetch.
 *
 * Per spec §3 the replay verifies versionId chain, SCID derivation,
 * versionTime monotonicity, entry-hash, pre-rotation chain, proof
 * signatures against active updateKeys, and witness threshold (with
 * cryptographic verification of each witness signature per §3.4).
 */
interface ReplayWebvhLogServiceCommand :
    ServiceCommand<ReplayWebvhLogInput, ReplayWebvhLogOutput, com.sphereon.core.api.error.IdkError>,
    PublicApiCommand {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "did.webvh.replay-log"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/{did}/replay-log",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "replayWebvhLog",
                tags = setOf("did:webvh"),
                summary = "Validate a did:webvh log and resolve a chosen entry's state",
            )
    }
}

@Serializable
data class ReplayWebvhLogInput(
    val did: String,
    /** Pre-parsed log entries — the caller has already fetched and decoded `did.jsonl`. */
    val entries: List<WebvhLogEntry>,
    /** Optional `did-witness.json`. Required when any entry declares a non-zero witness threshold. */
    val witnessFile: WebvhWitnessFile? = null,
    /**
     * Optional version selector. At most one of [versionId], [versionTime], or
     * [versionNumber] may be set; if none are provided, the latest entry is
     * returned (the typical resolver behaviour).
     */
    val versionId: String? = null,
    val versionTime: String? = null,
    val versionNumber: Int? = null,
)

@Serializable
data class ReplayWebvhLogOutput(
    /** DID document state at the selected entry. */
    val didDocument: DidDocument,
    /** The entry from the log that was selected. */
    val selectedEntry: WebvhLogEntry,
    /** Effective sticky-parameter set at the selected entry (after inheritance). */
    val activeParameters: WebvhParameters,
)
