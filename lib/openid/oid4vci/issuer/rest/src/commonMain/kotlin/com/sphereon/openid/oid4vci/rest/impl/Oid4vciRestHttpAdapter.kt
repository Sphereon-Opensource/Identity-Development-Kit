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

package com.sphereon.openid.oid4vci.rest.impl

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommandRegistry
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Command-backed HTTP adapter for OID4VCI backend REST API endpoints.
 *
 * - **POST /api/oid4vci/v1/backend/credential/offers** - Create credential offer
 * - **GET /api/oid4vci/v1/backend/credential/offers/{correlation_id}** - Get status
 * - **DELETE /api/oid4vci/v1/backend/credential/offers/{correlation_id}** - Delete session
 *
 * Framework-agnostic. Authentication is delegated to the platform server.
 * All operations are tenant-scoped via session context.
 */
@Inject
@SingleIn(SessionScope::class)
class Oid4vciRestHttpAdapter(
    execution: SessionExecution,
    endpointCommandRegistry: HttpEndpointCommandRegistry,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        endpointCommandRegistry = endpointCommandRegistry,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = "/api/oid4vci/v1",
            ),
    ) {
    companion object {
        const val ID: String = "oid4vci.backend.http"

        const val BACKEND_BASE_PATH: String = "/api/oid4vci/v1/backend/credential/offers"
    }

    @ContributesTo(SessionScope::class)
    interface Graph {
        val oid4vciRestHttpAdapter: Oid4vciRestHttpAdapter
    }
}
