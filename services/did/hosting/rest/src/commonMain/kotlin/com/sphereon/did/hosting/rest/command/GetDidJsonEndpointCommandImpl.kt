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

package com.sphereon.did.hosting.rest.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.response.ResponseBuilder
import com.sphereon.di.session.SessionScope
import com.sphereon.did.hosting.DidHostingRegistry
import com.sphereon.did.hosting.rest.DidHostingApiConstants
import com.sphereon.did.hosting.rest.DidHostingConfig
import com.sphereon.did.hosting.rest.http.GetDidJsonEndpointCommand
import com.sphereon.did.utils.WebLocation
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Serves the hosted `did.json` for the request's host + path.
 *
 * - The host comes from the `Host` header (already validated/punycode'd at the edge); the tenant from
 *   the internal base-tenant header the tenant-resolution layer stamped (null in single-tenant IDK).
 * - The path segments come from the matched depth pattern's `s1..sN` params (empty for `.well-known`).
 * - These compose a [WebLocation], resolved by the method-agnostic [DidHostingRegistry].
 *
 * Renders the raw DID document JSON as `application/did+json` with a `Cache-Control` derived from the
 * method's TTL. 404 when no DID is hosted at the location; 410 when the DID was deactivated.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetDidJsonEndpointCommand>())
class GetDidJsonEndpointCommandImpl(
    execution: SessionExecution,
    private val registry: DidHostingRegistry,
    private val hostingConfig: DidHostingConfig,
) : HttpEndpointCommandAdapter(
        id = GetDidJsonEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetDidJsonEndpointCommand.ENDPOINT,
    ),
    GetDidJsonEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        val hostHeader =
            (request.headers["Host"] ?: request.headers["host"])?.takeIf { it.isNotBlank() }
                ?: return Ok(ResponseBuilder.badRequest("Missing Host header"))
        val (host, port) = parseHostPort(hostHeader)

        val tenantId = request.headers[CommandBackedHttpAdapter.INTERNAL_BASE_TENANT_HEADER]
        val pathSegments = DidHostingApiConstants.PATH_SEGMENT_PARAMS.mapNotNull { request.pathParameters[it] }
        val webLocation = WebLocation.fromRequest(host = host, port = port, pathSegments = pathSegments)

        val hosted =
            registry.resolveDidJson(tenantId, webLocation).getOrElse { return Err(it) }
                ?: return Ok(ResponseBuilder.notFound("No DID document is hosted at '$webLocation'"))

        if (hosted.deactivated) {
            return Ok(ResponseBuilder.error(410, "GONE", "The DID hosted at '$webLocation' has been deactivated"))
        }

        val maxAge = hosted.cacheMaxAgeSeconds ?: hostingConfig.defaultCacheMaxAgeSeconds
        return Ok(
            ResponseBuilder.bytesResponse(
                data = hosted.json.encodeToByteArray(),
                contentType = DidHostingApiConstants.DID_JSON_MEDIA_TYPE,
                cacheControl = "public, max-age=$maxAge",
            ),
        )
    }

    /**
     * Split a `Host` header into host + optional numeric port. IPv6 literals are not split (did:web
     * forbids IP hosts), and a non-numeric tail is treated as part of the host.
     */
    private fun parseHostPort(hostHeader: String): Pair<String, Int?> {
        val trimmed = hostHeader.trim()
        if (trimmed.startsWith("[")) return trimmed to null
        val idx = trimmed.lastIndexOf(':')
        if (idx <= 0) return trimmed to null
        val port = trimmed.substring(idx + 1).toIntOrNull() ?: return trimmed to null
        return trimmed.substring(0, idx) to port
    }
}
