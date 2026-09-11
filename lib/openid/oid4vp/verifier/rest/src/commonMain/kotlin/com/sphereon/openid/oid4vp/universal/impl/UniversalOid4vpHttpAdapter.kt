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

package com.sphereon.openid.oid4vp.universal.impl

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommandRegistry
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.verifier.config.MutableOid4vpVerifierInstanceIdProvider
import com.sphereon.openid.oid4vp.verifier.config.Oid4vpVerifierInstanceResolver
import com.sphereon.openid.oid4vp.verifier.impl.http.AbstractOid4vpVerifierHttpAdapter
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.binding

/**
 * Command-backed HTTP adapter for Universal OID4VP REST API endpoints.
 *
 * This adapter provides the standardized Universal OID4VP endpoints for
 * external system integration:
 *
 * - **POST /oid4vp/backend/auth/requests** - Create authorization request
 * - **GET /oid4vp/backend/auth/requests/{correlation_id}** - Get status
 * - **DELETE /oid4vp/backend/auth/requests/{correlation_id}** - Delete session
 *
 * ## Integration
 *
 * The adapter builds on the existing OID4VP verifier implementation,
 * using its services and stores without duplication.
 *
 * ## Notes
 *
 * - Framework-agnostic (Spring, Ktor, serverless, etc.)
 * - Authentication is delegated to the platform server
 * - All operations are tenant-scoped via session context
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpAdapter>())
@StringKey(UniversalOid4vpHttpAdapter.ID)
class UniversalOid4vpHttpAdapter(
    execution: SessionExecution,
    endpointCommandRegistry: HttpEndpointCommandRegistry,
    verifierInstanceResolver: Oid4vpVerifierInstanceResolver,
    verifierInstanceIdProvider: MutableOid4vpVerifierInstanceIdProvider,
) : AbstractOid4vpVerifierHttpAdapter(
        id = ID,
        execution = execution,
        endpointCommandRegistry = endpointCommandRegistry,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = "/oid4vp",
            ),
        verifierInstanceResolver = verifierInstanceResolver,
        verifierInstanceIdProvider = verifierInstanceIdProvider,
    ) {
    companion object {
        const val ID: String = "oid4vp.universal.http"

        /**
         * Base path for Universal OID4VP backend endpoints.
         */
        const val BACKEND_BASE_PATH: String = "/oid4vp/backend/auth/requests"
    }

    /**
     * Endpoint commands for Universal OID4VP.
     */

    /**
     * Contributes this adapter as a property to the SessionGraph.
     */
    @ContributesTo(SessionScope::class)
    interface Graph {
        val universalOid4vpHttpAdapter: UniversalOid4vpHttpAdapter
    }
}
