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
import kotlinx.serialization.Serializable

/**
 * Appends a deactivation entry to an existing `did:webvh` log: sets
 * `parameters.deactivated = true` per spec §3.6. After publishing this
 * entry, no further updates are permitted.
 */
interface DeactivateWebvhDidServiceCommand :
    ServiceCommand<DeactivateWebvhDidInput, DeactivateWebvhDidOutput, com.sphereon.core.api.error.IdkError>,
    PublicApiCommand {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.DELETE
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "did.webvh.deactivate"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "/{did}",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "deactivateWebvhDid",
                tags = setOf("did:webvh"),
                summary = "Deactivate a did:webvh DID",
            )
    }
}

@Serializable
data class DeactivateWebvhDidInput(
    val did: String,
    val existingLog: List<WebvhLogEntry>,
    val signingKeyRefs: List<String>,
    val versionTime: String? = null,
)

@Serializable
data class DeactivateWebvhDidOutput(
    val deactivationEntry: WebvhLogEntry,
    val newLogJsonl: String,
    /**
     * Optional `did:web` companion DID document for the deactivated state.
     * Useful for `did:web`-only resolvers to see that the DID is now
     * deactivated. Always populated unless
     * `did.webvh.publish-did-web-companion=false`.
     */
    val didWebDocument: com.sphereon.did.models.DidDocument? = null,
    val didWebJson: String? = null,
)
