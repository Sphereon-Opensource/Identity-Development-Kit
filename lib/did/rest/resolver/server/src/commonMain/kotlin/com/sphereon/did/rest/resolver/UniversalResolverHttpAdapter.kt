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

package com.sphereon.did.rest.resolver

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommandRegistry
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.StaticPublicApiDescriptor
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * DIF Universal Resolver compatible HTTP adapter.
 *
 * Provides a REST API for resolving DIDs to DID Documents following the
 * DIF Universal Resolver HTTP API specification.
 *
 * ## Endpoints (base path: /1.0)
 *
 * - **GET /1.0/identifiers/{identifier}** - Resolve a DID to its DID Document
 * - **GET /1.0/methods** - List supported DID methods
 * - **GET /1.0/properties** - Get resolver properties and capabilities
 *
 * Note: The /1.0 base path is configured on this adapter. Commands define relative paths.
 *
 * ## DI Pattern
 *
 * This adapter follows the command-backed pattern where each endpoint is a standalone lazy
 * command resolved only after AppScope route selection.
 *
 * ## Usage
 *
 * The adapter is automatically registered via DI. To use it:
 *
 * 1. Include the `lib-did-rest-resolver-server` module as a dependency
 * 2. Ensure DID method resolvers are registered via [DidResolverRegistry]
 * 3. The adapter will be available at the configured mount path
 *
 * @see <a href="https://github.com/decentralized-identity/universal-resolver">DIF Universal Resolver</a>
 * @see <a href="https://w3c-ccg.github.io/did-resolution/">DID Resolution Spec</a>
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpAdapter>())
@StringKey(UniversalResolverHttpAdapter.ID)
class UniversalResolverHttpAdapter(
    execution: SessionExecution,
    endpointCommandRegistry: HttpEndpointCommandRegistry,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        endpointCommandRegistry = endpointCommandRegistry,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = "/1.0", // DIF spec base path, commands use relative paths
            ),
    ) {
    companion object {
        const val ID = "did.resolver.http"
    }

    /**
     * Contributes this adapter as a property to the SessionGraph.
     */
    @ContributesTo(SessionScope::class)
    interface Graph {
        val universalResolverHttpAdapter: UniversalResolverHttpAdapter
    }
}

@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class UniversalResolverHttpAdapterDescriptorProvider :
    StaticPublicApiDescriptor(
        adapterId = UniversalResolverHttpAdapter.ID,
        mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/1.0"),
        endpoints =
            listOf(
                ResolveDidEndpointCommand.ENDPOINT,
                GetResolverMethodsEndpointCommand.ENDPOINT,
                GetResolverPropertiesEndpointCommand.ENDPOINT,
            ),
    )
