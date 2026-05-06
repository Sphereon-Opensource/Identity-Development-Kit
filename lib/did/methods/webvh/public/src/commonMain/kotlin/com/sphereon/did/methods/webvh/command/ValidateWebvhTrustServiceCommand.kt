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

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.PublicApiCommand
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.trust.core.model.TrustValidationResult
import kotlinx.serialization.Serializable

/**
 * Anchors a `did:webvh` DID against the IDK trust framework. Bridges the
 * webvh REST surface to the generic trust commands so callers don't have to
 * know that webvh DIDs validate via `trust.did.validate` (method allow-list +
 * trustedDids match + controller match) under the hood.
 *
 * The command does NOT replay the log; cryptographic trust within the log
 * (signatures, pre-rotation, witness threshold) is enforced separately by
 * `did.webvh.replay-log`. This command answers the orthogonal question:
 * "is this DID anchored to a configured trust source?"
 *
 * Configuration (read by the underlying `trust.did.validate`):
 * - `trust.anchors.did.allowedMethods` - DID method allow-list (must include
 *   `webvh` for any webvh DID to pass).
 * - `trust.anchors.did.trustedDids` - explicit list of trusted DIDs and/or
 *   trusted controllers.
 *
 * Per-call overrides are accepted via [ValidateWebvhTrustInput.allowedMethods]
 * and [ValidateWebvhTrustInput.trustedDids]; when null the configured anchors
 * apply.
 */
interface ValidateWebvhTrustServiceCommand :
    ServiceCommand<ValidateWebvhTrustInput, TrustValidationResult, IdkError>,
    PublicApiCommand {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "did.webvh.validate-trust"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/{did}/trust",
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "validateWebvhTrust",
                tags = setOf("did:webvh"),
                summary = "Validate trust anchoring for a did:webvh DID",
            )
    }
}

@Serializable
data class ValidateWebvhTrustInput(
    val did: String,
    /**
     * Override the configured DID method allow-list for this call. When null,
     * `trust.anchors.did.allowedMethods` applies. Always include `webvh` if
     * the webvh DID should pass the gate.
     */
    val allowedMethods: List<String>? = null,
    /**
     * Override the configured trusted-DIDs list for this call. When null,
     * `trust.anchors.did.trustedDids` applies. May contain the webvh DID
     * itself or any of its controller DIDs.
     */
    val trustedDids: List<String>? = null,
)
