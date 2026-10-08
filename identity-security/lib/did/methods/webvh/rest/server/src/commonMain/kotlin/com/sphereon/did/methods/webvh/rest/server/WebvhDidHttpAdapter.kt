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

package com.sphereon.did.methods.webvh.rest.server

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommandRegistry
import com.sphereon.core.api.http.command.PublicApiHttpAdapter
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Exposes the `did:webvh` lifecycle service commands at `/api/v1/did/webvh`.
 *
 * Resolution is handled separately via the existing
 * `UniversalResolverHttpAdapter` (`/1.0/identifiers/...`); this adapter is
 * only needed by deployments that host the lifecycle (controller-side
 * create / update / deactivate / update-witness-file) or the witness-side
 * `create-witness-proof` over REST. Verifier-only deployments omit this
 * module entirely so they do not pull in ktor-server transitively.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpAdapter>())
@StringKey(WebvhDidHttpAdapter.ID)
class WebvhDidHttpAdapter(
    execution: SessionExecution,
    endpointCommandRegistry: HttpEndpointCommandRegistry,
) : PublicApiHttpAdapter(
        id = ID,
        sessionExecution = execution,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = BASE_PATH,
            ),
        endpointCommandRegistry = endpointCommandRegistry,
    ) {

    companion object {
        const val ID = "did.webvh.http"
        const val BASE_PATH = "/api/v1/did/webvh"
    }
}
