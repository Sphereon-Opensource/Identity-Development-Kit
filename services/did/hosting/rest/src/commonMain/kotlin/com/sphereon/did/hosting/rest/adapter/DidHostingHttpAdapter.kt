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

package com.sphereon.did.hosting.rest.adapter

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.OpenApiHints
import com.sphereon.di.session.SessionScope
import com.sphereon.did.hosting.rest.DidHostingApiConstants
import com.sphereon.did.hosting.rest.DidHostingConfig
import com.sphereon.did.hosting.rest.http.GetDidJsonEndpointCommand
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * PUBLIC HTTP adapter that hosts did:web / did:webvh documents, mounted at the host root (so the
 * `did.json` lands at `/.well-known/did.json` and `/<path>/did.json` exactly where a resolver fetches
 * it). Unauthenticated and cacheable. Method-agnostic: it carries one did.json endpoint that delegates
 * to the `DidHostingRegistry`, which fans out to the contributed providers.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class DidHostingHttpAdapter(
    execution: SessionExecution,
    hostingConfig: DidHostingConfig,
    getDidJson: GetDidJsonEndpointCommand,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = hostingConfig.basePath,
            ),
    ) {
    override val endpointCommands: List<HttpEndpointCommand> = listOf(getDidJson)

    override val openApiHints =
        OpenApiHints(
            tags = setOf(DidHostingApiConstants.Tags.DID_HOSTING),
            operationIdPrefix = "didHosting",
        )

    companion object {
        const val ID = "did-hosting"
    }
}
