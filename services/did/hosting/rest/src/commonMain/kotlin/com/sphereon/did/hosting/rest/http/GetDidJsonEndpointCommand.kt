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

package com.sphereon.did.hosting.rest.http

import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.EndpointAuthPolicy
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.did.hosting.rest.DidHostingApiConstants
import com.sphereon.did.hosting.rest.DidHostingApiConstants.CommandIds
import com.sphereon.did.hosting.rest.DidHostingApiConstants.Tags

/**
 * `GET /.well-known/did.json` (and `/<path>/did.json`) — return the hosted DID document for this host
 * and path. Public, unauthenticated, cacheable. The host + path identify a [web location]
 * [com.sphereon.did.utils.WebLocation]; the request is dispatched to whichever contributed
 * `DidHostingProvider` manages it: a did:web record is served verbatim, a did:webvh record is served
 * as its did:web companion translation (with `alsoKnownAs` back to the did:webvh).
 */
interface GetDidJsonEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = CommandIds.HTTP_GET_DID_JSON
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPatterns = DidHostingApiConstants.DID_JSON_PATH_PATTERNS,
                produces = setOf(MediaType.Custom(DidHostingApiConstants.DID_JSON_MEDIA_TYPE)),
                commandId = COMMAND_ID,
                operationId = "getDidDocument",
                tags = setOf(Tags.DID_HOSTING),
                summary = "Resolve the hosted DID document (did.json) for this host and path",
                authPolicy = EndpointAuthPolicy.PUBLIC,
            )
    }
}
