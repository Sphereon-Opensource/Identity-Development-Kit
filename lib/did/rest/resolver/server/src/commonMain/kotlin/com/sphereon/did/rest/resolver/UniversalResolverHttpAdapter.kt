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
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesIntoSet
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
 * This adapter follows the command-backed pattern where:
 * - Each endpoint is a standalone injectable [HttpEndpointCommand]
 * - Commands are **injected** via constructor (not instantiated)
 * - The adapter aggregates endpoint commands and routes requests to them
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
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class UniversalResolverHttpAdapter(
    execution: SessionExecution,
    // Commands are INJECTED, not instantiated
    private val resolveDidCommand: ResolveDidEndpointCommand,
    private val getMethodsCommand: GetResolverMethodsEndpointCommand,
    private val getPropertiesCommand: GetResolverPropertiesEndpointCommand,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = "/1.0", // DIF spec base path, commands use relative paths
            ),
    ) {
    companion object {
        const val ID = "DID-UNIVERSAL-RESOLVER"
    }

    /**
     * Endpoint commands for this adapter.
     *
     * Commands are injected via constructor following the standard IDK DI pattern.
     */
    override val endpointCommands: List<HttpEndpointCommand> =
        listOf(
            resolveDidCommand,
            getMethodsCommand,
            getPropertiesCommand,
        )

    /**
     * Contributes this adapter as a property to the SessionGraph.
     */
    @ContributesTo(SessionScope::class)
    interface Graph {
        val universalResolverHttpAdapter: UniversalResolverHttpAdapter
    }
}
