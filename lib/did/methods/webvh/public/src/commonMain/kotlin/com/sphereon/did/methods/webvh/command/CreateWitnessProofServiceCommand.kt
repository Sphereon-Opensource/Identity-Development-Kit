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
import com.sphereon.did.methods.webvh.model.WebvhWitnessProof
import kotlinx.serialization.Serializable

/**
 * Witness-side: signs a versionId of a `did:webvh` log entry with the
 * witness's own Ed25519 KMS key, producing a single
 * [WebvhWitnessProof] for transmission back to the DID controller.
 *
 * Per `did:webvh` v1.0 §3.4 a witness MUST be a `did:key`. The IDK does not
 * define the transport between witnesses and controllers; once the witness
 * has produced its proof, how it reaches the controller (REST callback,
 * email, message bus, etc.) is operational integration above the SDK.
 */
interface CreateWitnessProofServiceCommand :
    ServiceCommand<CreateWitnessProofInput, CreateWitnessProofOutput, com.sphereon.core.api.error.IdkError>,
    PublicApiCommand {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "did.webvh.create-witness-proof"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/{did}/witness-proofs",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                operationId = "createWebvhWitnessProof",
                tags = setOf("did:webvh"),
                summary = "Witness signs a did:webvh versionId",
            )
    }
}

@Serializable
data class CreateWitnessProofInput(
    /** The DID being witnessed. */
    val did: String,
    /** The exact versionId string (e.g. `1-z6Mk…`) the witness is signing. */
    val versionId: String,
    /** KMS reference to the witness's own Ed25519 key. */
    val signingKeyRef: String,
    /**
     * Optional `did:key` for the witness. If null, derived from the public
     * key associated with [signingKeyRef].
     */
    val witnessDid: String? = null,
)

@Serializable
data class CreateWitnessProofOutput(
    val witnessProof: WebvhWitnessProof,
)
