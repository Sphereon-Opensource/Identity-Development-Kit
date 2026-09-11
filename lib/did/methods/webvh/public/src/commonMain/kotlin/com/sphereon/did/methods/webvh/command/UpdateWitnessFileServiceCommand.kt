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
import com.sphereon.did.methods.webvh.model.WebvhWitnessFile
import com.sphereon.did.methods.webvh.model.WebvhWitnessProof
import kotlinx.serialization.Serializable

/**
 * Controller-side: merges incoming witness proofs into a `did-witness.json`
 * file. Per spec, controllers SHOULD prune to the latest proof per witness
 * before publishing, since a witness proof on a versionId implies
 * endorsement of all earlier entries.
 */
interface UpdateWitnessFileServiceCommand :
    ServiceCommand<UpdateWitnessFileInput, UpdateWitnessFileOutput, com.sphereon.core.api.error.IdkError>,
    PublicApiCommand {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.UPDATE
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "did.webvh.update-witness-file"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.PUT,
                pathPattern = "/{did}/witness-file",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                operationId = "updateWebvhWitnessFile",
                tags = setOf("did:webvh"),
                summary = "Merge witness proofs into did-witness.json",
            )
    }
}

@Serializable
data class UpdateWitnessFileInput(
    val did: String,
    /** The current witness file, if one exists. Null on first publish. */
    val existingWitnessFile: WebvhWitnessFile? = null,
    /** Witness proofs received out-of-band from witnesses. */
    val incomingProofs: List<WebvhWitnessProof>,
)

@Serializable
data class UpdateWitnessFileOutput(
    val witnessFile: WebvhWitnessFile,
    /** Canonical JSON content for the witness file. Publish at the witness URL. */
    val witnessFileJson: String,
)
