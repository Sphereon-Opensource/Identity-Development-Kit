/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.crypto.kms.rest.server.adapter.command

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpAdapterMount
import dev.zacsweers.metro.Named
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet

/**
 * Command-backed HTTP adapter for KMS Resolvers API.
 *
 * **REFERENCE IMPLEMENTATION**: This adapter demonstrates the command-backed pattern where:
 * - Each endpoint is a standalone injectable [HttpEndpointCommand]
 * - Commands are **injected** via constructor (not instantiated)
 * - The adapter aggregates endpoint commands and routes requests to them
 * - Full command lifecycle integration (enablement, extensions, session hooks)
 *
 * ## DI Pattern
 *
 * This follows the same pattern as regular services with commands:
 * - Command interfaces are defined separately
 * - Command implementations use @ContributesBinding
 * - The adapter receives commands via constructor injection
 * - Commands are NOT instantiated directly
 *
 * ## Comparison with RoutedHttpAdapter
 *
 * The original [com.sphereon.crypto.kms.rest.server.adapter.ResolversHttpAdapter] uses `RoutedHttpAdapter`
 * with inline lambda handlers. This command-backed version separates concerns:
 *
 * | Aspect | RoutedHttpAdapter | CommandBackedHttpAdapter |
 * |--------|-------------------|--------------------------|
 * | Handler location | Inline lambdas | Separate command classes |
 * | Enablement | Per-adapter only | Per-endpoint |
 * | Extensions | Not supported | Before/during/after hooks |
 * | Session lifecycle | Not integrated | Full Scoped integration |
 * | Testability | Test whole adapter | Test individual endpoints |
 * | DI pattern | N/A | Commands are injected |
 * | Complexity | Lower | Higher |
 *
 * **When to use CommandBackedHttpAdapter:**
 * - You need per-endpoint enablement/feature flags
 * - You want command execution extensions (logging, metrics, etc.)
 * - You need session lifecycle hooks on endpoints
 * - You want to inject individual endpoints for testing
 *
 * **When to use RoutedHttpAdapter:**
 * - Simple adapters with straightforward handlers
 * - No need for per-endpoint lifecycle management
 * - Minimal boilerplate is preferred
 *
 * ## Endpoints
 *
 * - **GET /resolvers** - List all resolvers ([ListResolversEndpointCommand])
 * - **GET /resolvers/{resolverId}** - Get resolver details ([GetResolverEndpointCommand])
 * - **POST /resolvers/{resolverId}/resolve** - Resolve a public key ([ResolvePublicKeyEndpointCommand])
 *
 * @see ResolversHttpAdapter for the simpler RoutedHttpAdapter version
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class CommandBackedResolversHttpAdapter(
    execution: SessionExecution,
    // Commands are INJECTED, not instantiated
    private val listResolversCommand: ListResolversEndpointCommand,
    private val getResolverCommand: GetResolverEndpointCommand,
    private val resolvePublicKeyCommand: ResolvePublicKeyEndpointCommand
) : CommandBackedHttpAdapter(
    id = ID,
    execution = execution,
    mount = HttpAdapterMount(
        serverPrefix = "",
        adapterBasePath = "/resolvers"
    )
) {
    companion object {
        const val ID = "KMS-RESOLVERS-COMMAND"
    }

    /**
     * Endpoint commands for this adapter.
     *
     * Commands are injected via constructor following the standard IDK DI pattern.
     * This allows:
     * - Individual command testing
     * - Command replacement via DI bindings
     * - Proper lifecycle management
     */
    override val endpointCommands: List<HttpEndpointCommand> = listOf(
        listResolversCommand,
        getResolverCommand,
        resolvePublicKeyCommand
    )

    /**
     * Contributes this adapter as a property to the SessionComponent.
     *
     * This allows the adapter to be individually injectable (not just via Set multibinding).
     */
    @ContributesTo(SessionScope::class)
    interface Component {
        val commandBackedResolversHttpAdapter: CommandBackedResolversHttpAdapter
    }
}
