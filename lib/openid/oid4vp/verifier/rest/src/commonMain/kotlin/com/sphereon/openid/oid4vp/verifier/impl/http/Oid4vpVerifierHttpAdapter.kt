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

package com.sphereon.openid.oid4vp.verifier.impl.http

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.verifier.impl.http.command.DirectPostResponseEndpointCommand
import com.sphereon.openid.oid4vp.verifier.impl.http.command.GetRequestObjectEndpointCommand
import com.sphereon.openid.oid4vp.verifier.impl.http.command.PostRequestObjectEndpointCommand
import com.sphereon.openid.oid4vp.verifier.impl.http.command.ReadyEndpointCommand
import com.sphereon.openid.oid4vp.verifier.config.MutableOid4vpVerifierInstanceIdProvider
import com.sphereon.openid.oid4vp.verifier.config.Oid4vpVerifierInstanceResolver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Command-backed HTTP adapter for OpenID4VP RP (Verifier) endpoints.
 *
 * This adapter provides endpoints that must be hosted by a platform server
 * for OID4VP verification flows:
 *
 * - **GET /oid4vp/request-uri/{correlationId}** - Fetch request object by request_uri
 * - **POST /oid4vp/request-uri/{correlationId}** - Fetch request object with wallet metadata
 *
 * ## Pattern
 *
 * Uses the CommandBackedHttpAdapter pattern where:
 * - Each endpoint is a standalone injectable [HttpEndpointCommand]
 * - Commands are injected via constructor (not instantiated)
 * - Full command lifecycle integration (enablement, extensions, session hooks)
 *
 * ## Notes
 *
 * - This adapter is framework-agnostic and can be hosted by Spring, Ktor, serverless, etc.
 * - The platform/server project is responsible for exposing the HTTP endpoint and delegating to this adapter.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class Oid4vpVerifierHttpAdapter(
    execution: SessionExecution,
    verifierInstanceResolver: Oid4vpVerifierInstanceResolver,
    verifierInstanceIdProvider: MutableOid4vpVerifierInstanceIdProvider,
    // Commands are INJECTED via DI
    private val getRequestObjectCommand: GetRequestObjectEndpointCommand,
    private val postRequestObjectCommand: PostRequestObjectEndpointCommand,
    private val directPostResponseCommand: DirectPostResponseEndpointCommand,
    private val readyCommand: ReadyEndpointCommand,
) : AbstractOid4vpVerifierHttpAdapter(
        id = ID,
        execution = execution,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = "/oid4vp",
            ),
        verifierInstanceResolver = verifierInstanceResolver,
        verifierInstanceIdProvider = verifierInstanceIdProvider,
    ) {
    companion object {
        const val ID: String = "oid4vp.verifier.http"

        /**
         * Base path for serving request objects by reference.
         *
         * Wallets will fetch the request object from a `request_uri` URL. The host application
         * can choose any URL shape; the handler will extract the correlation id from the last
         * path segment. This adapter standardizes on:
         *
         * - `GET  /oid4vp/request-uri/{correlationId}`
         * - `POST /oid4vp/request-uri/{correlationId}` (for `request_uri_method=post`)
         */
        const val REQUEST_URI_PREFIX: String = "/oid4vp/request-uri/"
    }

    /**
     * Endpoint commands for OID4VP request_uri handling.
     *
     * Commands are injected via constructor following the standard IDK DI pattern.
     */
    override val endpointCommands: List<HttpEndpointCommand> =
        listOf(
            getRequestObjectCommand,
            postRequestObjectCommand,
            directPostResponseCommand,
            readyCommand,
        )

    /**
     * Contributes this adapter as a property to the SessionGraph.
     */
    @ContributesTo(SessionScope::class)
    interface Graph {
        val oid4VpVerifierHttpAdapter: Oid4vpVerifierHttpAdapter
    }
}
