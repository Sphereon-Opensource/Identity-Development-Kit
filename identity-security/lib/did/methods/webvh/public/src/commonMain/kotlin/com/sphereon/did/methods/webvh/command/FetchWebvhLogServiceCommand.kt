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
import com.sphereon.did.methods.webvh.model.WebvhWitnessFile
import kotlinx.serialization.Serializable

/**
 * Read-only utility: fetches a DID's log (and optionally its witness file)
 * via HTTPS, applies an optional version filter, and returns the parsed
 * entries. Implementation lives in `:lib:did:methods:webvh:resolver`.
 */
interface FetchWebvhLogServiceCommand :
    ServiceCommand<FetchWebvhLogInput, FetchWebvhLogOutput, com.sphereon.core.api.error.IdkError>,
    PublicApiCommand {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "did.webvh.fetch-log"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/{did}/log",
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                operationId = "fetchWebvhLog",
                tags = setOf("did:webvh"),
                summary = "Fetch a did:webvh log",
            )
    }
}

@Serializable
data class FetchWebvhLogInput(
    val did: String,
    /** Filter to a specific versionId; null means return all entries. */
    val versionId: String? = null,
    /** Filter to entries up to and including this ISO-8601 UTC timestamp. */
    val versionTime: String? = null,
    /** Filter to a specific 1-based version number. */
    val versionNumber: Int? = null,
    /** When true, also fetch the witness file (`did-witness.json`). */
    val includeWitnessFile: Boolean = false,
    /** When true, the resolver may serve from its log cache (default true). */
    val useCache: Boolean = true,
)

@Serializable
data class FetchWebvhLogOutput(
    val entries: List<WebvhLogEntry>,
    val witnessFile: WebvhWitnessFile? = null,
)
