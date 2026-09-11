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

package com.sphereon.openid.oid4vp.auth.impl.http

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommandRegistry
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.OpenApiHints
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * HTTP adapter for OID4VP Authentication Bridge endpoints.
 *
 * Mounts the following endpoints at /auth/oid4vp:
 * - POST /sessions - Create a new authentication session
 * - GET /sessions/{sessionId}/status - Poll session status
 * - POST /sessions/{sessionId}/complete - Complete authentication
 * - POST /sessions/{sessionId}/idv/initiate - Initiate identity verification
 * - GET /sessions/{sessionId}/idv/status - Get IDV status
 * - POST /sessions/{sessionId}/reconciliation/complete - Complete reconciliation with pre-extracted claims from STS
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpAdapter>())
@StringKey(Oid4vpAuthHttpAdapter.ID)
class Oid4vpAuthHttpAdapter(
    execution: SessionExecution,
    endpointCommandRegistry: HttpEndpointCommandRegistry,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        endpointCommandRegistry = endpointCommandRegistry,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = "/auth/oid4vp",
            ),
    ) {

    override val openApiHints =
        OpenApiHints(
            tags = setOf("oid4vp-auth"),
            operationIdPrefix = "oid4vpAuth",
        )

    /**
     * DI graph interface for accessing the adapter from the session graph.
     */
    @ContributesTo(SessionScope::class)
    interface Graph {
        val oid4vpAuthHttpAdapter: Oid4vpAuthHttpAdapter
    }

    companion object {
        const val ID = "oid4vp.auth.adapter"
    }
}
