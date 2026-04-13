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
 */

package com.sphereon.core.api.service

import com.sphereon.core.api.http.describe.HttpEndpointDescriptor

/**
 * Marker interface for ServiceCommands that expose a public-facing REST API.
 *
 * IDK commands implement this to declare their public HTTP binding via an
 * [HttpEndpointDescriptor] that carries method, path, media types, tags, summary,
 * and the transport-level commandId.
 *
 * For commands WITHOUT a public REST API (internal-only), don't implement this.
 *
 * **Examples:**
 * ```kotlin
 * interface GetKeyServiceCommand :
 *     ServiceCommand<GetKeyInput, GetKeyResponse>,
 *     PublicApiCommand {
 *
 *     companion object {
 *         const val COMMAND_ID = "kms.keys.get"
 *         val ENDPOINT = HttpEndpointDescriptor(
 *             method = HttpMethod.GET,
 *             pathPattern = "/keys/{aliasOrKid}",
 *             produces = setOf(MediaType.ApplicationJson),
 *             commandId = COMMAND_ID,
 *             operationId = "getKey",
 *             tags = setOf("Keys"),
 *             summary = "Get a key by alias or kid"
 *         )
 *     }
 *
 *     override val commandId get() = COMMAND_ID
 *     override val httpEndpoint get() = ENDPOINT
 * }
 * ```
 *
 * **Key design points:**
 * - This interface lives in IDK and only declares public REST API bindings.
 * - Internal transport bindings (HTTP RPC, gRPC) are NOT declared here.
 * - EDK derives internal bindings via [TransportConventionResolver] from command identity.
 * - EDK handles internal transport binding resolution automatically.
 * - All HTTP metadata (method, path, media types, tags, summary) lives in [httpEndpoint].
 */
interface PublicApiCommand {
    /** HTTP endpoint descriptor with method, path, media types, tags, summary. */
    val httpEndpoint: HttpEndpointDescriptor
}
