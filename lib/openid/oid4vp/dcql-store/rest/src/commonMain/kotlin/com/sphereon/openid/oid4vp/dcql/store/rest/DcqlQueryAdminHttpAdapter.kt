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

package com.sphereon.openid.oid4vp.dcql.store.rest

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.OpenApiHints
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.dcql.store.http.CreateDcqlQueryEndpointCommand
import com.sphereon.openid.oid4vp.dcql.store.http.DeleteDcqlQueryEndpointCommand
import com.sphereon.openid.oid4vp.dcql.store.http.GetDcqlQueryEndpointCommand
import com.sphereon.openid.oid4vp.dcql.store.http.ListDcqlQueriesEndpointCommand
import com.sphereon.openid.oid4vp.dcql.store.http.PatchDcqlQueryEndpointCommand
import com.sphereon.openid.oid4vp.dcql.store.http.ReplaceDcqlQueryEndpointCommand
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * HTTP adapter for the DCQL query configuration administration endpoints.
 *
 * Mounts the following endpoints at `/api/v1/oid4vp/dcql`:
 * - `GET    /dcql`            list configurations
 * - `POST   /dcql`            create a configuration
 * - `GET    /dcql/{queryId}`  read a configuration
 * - `PUT    /dcql/{queryId}`  replace a configuration
 * - `PATCH  /dcql/{queryId}`  partially update a configuration
 * - `DELETE /dcql/{queryId}`  delete a configuration
 *
 * Auth is the OIDC bearer token; the tenant is resolved from the session, never from headers.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class DcqlQueryAdminHttpAdapter(
    execution: SessionExecution,
    private val listCommand: ListDcqlQueriesEndpointCommand,
    private val createCommand: CreateDcqlQueryEndpointCommand,
    private val getCommand: GetDcqlQueryEndpointCommand,
    private val replaceCommand: ReplaceDcqlQueryEndpointCommand,
    private val patchCommand: PatchDcqlQueryEndpointCommand,
    private val deleteCommand: DeleteDcqlQueryEndpointCommand,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = "/api/v1/oid4vp",
            ),
    ) {
    override val endpointCommands: List<HttpEndpointCommand> =
        listOf(
            listCommand,
            createCommand,
            getCommand,
            replaceCommand,
            patchCommand,
            deleteCommand,
        )

    override val openApiHints =
        OpenApiHints(
            tags = setOf("oid4vp-dcql"),
            operationIdPrefix = "oid4vpDcql",
        )

    /**
     * DI graph interface for accessing the adapter from the session graph.
     */
    @ContributesTo(SessionScope::class)
    interface Graph {
        val dcqlQueryAdminHttpAdapter: DcqlQueryAdminHttpAdapter
    }

    companion object {
        const val ID = "oid4vp.dcql.http.adapter"
    }
}
