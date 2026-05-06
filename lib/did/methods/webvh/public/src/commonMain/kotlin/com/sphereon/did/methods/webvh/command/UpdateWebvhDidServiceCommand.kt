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
import com.sphereon.did.models.DidDocument
import kotlinx.serialization.Serializable

/**
 * Appends a new entry to an existing `did:webvh` log. Caller supplies the
 * full prior log so the command can validate parameter inheritance and
 * compute the predecessor versionId for the entry hash.
 */
interface UpdateWebvhDidServiceCommand :
    ServiceCommand<UpdateWebvhDidInput, UpdateWebvhDidOutput, com.sphereon.core.api.error.IdkError>,
    PublicApiCommand {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.UPDATE
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "did.webvh.update"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.PUT,
                pathPattern = "/{did}",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "updateWebvhDid",
                tags = setOf("did:webvh"),
                summary = "Append an update entry to a did:webvh log",
            )
    }
}

@Serializable
data class UpdateWebvhDidInput(
    val did: String,
    val existingLog: List<WebvhLogEntry>,
    /** New DID document state to publish. */
    val newDocument: DidDocument,
    /**
     * Parameter changes for this entry. Fields left null inherit from the
     * prior entry; explicit empty list clears the prior value (e.g.
     * `updateKeys = emptyList()` locks the DID against further updates).
     * For pre-rotation: set [WebvhParameters.updateKeys] (multikeys) and
     * [WebvhParameters.nextKeyHashes] explicitly here when rotating.
     */
    val parameterChanges: WebvhParameters = WebvhParameters(),
    /** KMS refs of the keys signing the new entry's Data Integrity proof. */
    val signingKeyRefs: List<String>,
    /** ISO-8601 UTC timestamp; defaults to now. MUST be > prior entry's versionTime. */
    val versionTime: String? = null,
)

@Serializable
data class UpdateWebvhDidOutput(
    val newEntry: WebvhLogEntry,
    /** Canonical JSONL content for the full updated log (existing + new entry). */
    val newLogJsonl: String,
    /**
     * Optional `did:web` companion DID document built from the new entry's
     * state. Always populated unless `did.webvh.publish-did-web-companion=false`.
     * Publish as `did.json` next to `did.jsonl`.
     */
    val didWebDocument: com.sphereon.did.models.DidDocument? = null,
    /** Canonical JSON serialisation of [didWebDocument]. */
    val didWebJson: String? = null,
)
