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
import com.sphereon.did.methods.webvh.model.WebvhWitnessConfig
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.DidService
import com.sphereon.did.models.VerificationPurpose
import kotlinx.serialization.Serializable

/**
 * Mints a new `did:webvh` DID, producing the genesis log entry and the full
 * `did.jsonl` content ready to be published at the resolver URL.
 */
interface CreateWebvhDidServiceCommand :
    ServiceCommand<CreateWebvhDidInput, CreateWebvhDidOutput, com.sphereon.core.api.error.IdkError>,
    PublicApiCommand {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "did.webvh.create"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "createWebvhDid",
                tags = setOf("did:webvh"),
                summary = "Mint a new did:webvh DID",
            )
    }
}

@Serializable
data class CreateWebvhDidInput(
    /** Web host the DID will resolve from (e.g. `example.com`). Punycode is applied automatically. */
    val domain: String,
    /** Optional URL path segments under the host. Empty -> log served at `/.well-known/did.jsonl`. */
    val path: List<String> = emptyList(),
    /** Optional explicit port (decoded back to `%3A<port>` in the DID). */
    val port: Int? = null,
    /**
     * KMS references (alias or kid) of the Ed25519 keys signing the genesis
     * entry's Data Integrity proof. The first entry is used as the active
     * signing key for the proof on this entry.
     */
    val updateKeyRefs: List<String>,
    /**
     * Multikey strings (e.g. `z6Mk…`) of the public keys in [updateKeyRefs],
     * in the same order. These go into `parameters.updateKeys`. The caller
     * is responsible for ensuring `updateMultikeys[i]` is the multikey form
     * of the public key corresponding to `updateKeyRefs[i]`.
     */
    val updateMultikeys: List<String>,
    /** Pre-rotation: multikey strings of the keys allowed to appear in the *next* entry's updateKeys. */
    val nextKeyMultikeys: List<String> = emptyList(),
    /** Optional witness configuration (threshold + witness DIDs). */
    val witness: WebvhWitnessConfig? = null,
    /** Optional watcher URLs. */
    val watchers: List<String> = emptyList(),
    /** Allow future portability moves. May only be true on creation per spec §3.2. */
    val portable: Boolean = false,
    /** Cache TTL hint, seconds. Spec default 3600; 0 disables caching. */
    val ttlSeconds: Long? = null,
    /** Verification purposes to assign the genesis update key in the DID document. */
    val verificationPurposes: List<VerificationPurpose> =
        listOf(
            VerificationPurpose.AUTHENTICATION,
            VerificationPurpose.ASSERTION_METHOD,
        ),
    /** Optional service entries to embed in the genesis DID document. */
    val services: List<DidService> = emptyList(),
)

@Serializable
data class CreateWebvhDidOutput(
    val did: String,
    val didDocument: DidDocument,
    val logEntry: WebvhLogEntry,
    /** Canonical JSONL content (one entry, trailing newline). Publish at the resolver URL. */
    val logJsonl: String,
    /**
     * Optional `did:web` companion document derived from the webvh state per
     * spec "Publishing a Parallel did:web DID". Always populated unless the
     * controller has set `did.webvh.publish-did-web-companion=false`. When
     * present, publish at the resolver URL with filename `did.json` (sibling
     * to `did.jsonl`).
     */
    val didWebDocument: DidDocument? = null,
    /** Canonical JSON serialisation of [didWebDocument], ready to publish. */
    val didWebJson: String? = null,
)
