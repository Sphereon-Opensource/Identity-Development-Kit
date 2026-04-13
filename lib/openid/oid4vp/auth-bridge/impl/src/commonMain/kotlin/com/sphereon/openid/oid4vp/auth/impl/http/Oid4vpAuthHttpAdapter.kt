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
 *
 */

package com.sphereon.openid.oid4vp.auth.impl.http

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.OpenApiHints
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.auth.http.CompleteOid4vpAuthCommand
import com.sphereon.openid.oid4vp.auth.http.CreateOid4vpAuthSessionCommand
import com.sphereon.openid.oid4vp.auth.http.GetOid4vpAuthStatusCommand
import com.sphereon.openid.oid4vp.auth.http.GetOid4vpIdvStatusCommand
import com.sphereon.openid.oid4vp.auth.http.HandleOid4vpIdvCallbackCommand
import com.sphereon.openid.oid4vp.auth.http.InitiateOid4vpIdvCommand
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn

/**
 * HTTP adapter for OID4VP Authentication Bridge endpoints.
 *
 * Mounts the following endpoints at /auth/oid4vp:
 * - POST /sessions - Create a new authentication session
 * - GET /sessions/{sessionId}/status - Poll session status
 * - POST /sessions/{sessionId}/complete - Complete authentication
 * - POST /sessions/{sessionId}/idv/initiate - Initiate identity verification
 * - GET /sessions/{sessionId}/idv/status - Get IDV status
 * - GET /sessions/{sessionId}/idv/callback - Handle IDV OIDC callback
 */
@Inject
@Named(Oid4vpAuthHttpAdapter.ID)
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class Oid4vpAuthHttpAdapter(
    execution: SessionExecution,
    private val createSessionCommand: CreateOid4vpAuthSessionCommand,
    private val getStatusCommand: GetOid4vpAuthStatusCommand,
    private val completeCommand: CompleteOid4vpAuthCommand,
    private val initiateIdvCommand: InitiateOid4vpIdvCommand,
    private val getIdvStatusCommand: GetOid4vpIdvStatusCommand,
    private val handleIdvCallbackCommand: HandleOid4vpIdvCallbackCommand
) : CommandBackedHttpAdapter(
    id = ID,
    execution = execution,
    mount = HttpAdapterMount(
        serverPrefix = "",
        adapterBasePath = "/auth/oid4vp"
    )
) {
    companion object {
        const val ID = "oid4vp.auth.http.adapter"
    }

    override val endpointCommands: List<HttpEndpointCommand> = listOf(
        createSessionCommand,
        getStatusCommand,
        completeCommand,
        initiateIdvCommand,
        getIdvStatusCommand,
        handleIdvCallbackCommand
    )

    override val openApiHints = OpenApiHints(
        tags = setOf("oid4vp-auth"),
        operationIdPrefix = "oid4vpAuth"
    )

    /**
     * DI component interface for accessing the adapter from the session component.
     */
    @ContributesTo(SessionScope::class)
    interface Component {
        val oid4vpAuthHttpAdapter: Oid4vpAuthHttpAdapter
    }
}
