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
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.rest.CreateCredentialOfferEndpointCommand
import com.sphereon.openid.oid4vci.rest.DeleteCredentialOfferEndpointCommand
import com.sphereon.openid.oid4vci.rest.GetCredentialOfferStatusEndpointCommand
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Command-backed HTTP adapter for OID4VCI backend REST API endpoints.
 *
 * - **POST /oid4vci/backend/credential/offers** - Create credential offer
 * - **GET /oid4vci/backend/credential/offers/{correlation_id}** - Get status
 * - **DELETE /oid4vci/backend/credential/offers/{correlation_id}** - Delete session
 *
 * Framework-agnostic. Authentication is delegated to the platform server.
 * All operations are tenant-scoped via session context.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class Oid4vciRestHttpAdapter(
    execution: SessionExecution,
    private val createCommand: CreateCredentialOfferEndpointCommand,
    private val getStatusCommand: GetCredentialOfferStatusEndpointCommand,
    private val deleteCommand: DeleteCredentialOfferEndpointCommand,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = "/oid4vci",
            ),
    ) {
    companion object {
        const val ID: String = "OID4VCI_REST"

        const val BACKEND_BASE_PATH: String = "/oid4vci/backend/credential/offers"
    }

    override val endpointCommands: List<HttpEndpointCommand> =
        listOf(
            createCommand,
            getStatusCommand,
            deleteCommand,
        )

    @ContributesTo(SessionScope::class)
    interface Graph {
        val oid4vciRestHttpAdapter: Oid4vciRestHttpAdapter
    }
}
