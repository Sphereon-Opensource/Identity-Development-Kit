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

package com.sphereon.oauth2.server.authorization.impl.http

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.MutableOAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceResolver
import com.sphereon.oauth2.server.authorization.command.authorization.AuthorizeCallbackHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.authorization.AuthorizeHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.authorization.IaeHttpEndpointCommand
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Authorization surface for the OAuth2 Authorization Server.
 *
 * Routes:
 * - `GET /authorize` (RFC 6749 §3.1; browser-facing entry)
 * - `GET /authorize/callback` (post-login resume)
 * - `POST /iae` (OID4VCI 1.1 §6 Interactive Authorization Endpoint)
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class OAuth2AuthorizationHttpAdapter(
    execution: SessionExecution,
    asInstanceResolver: OAuth2ServerInstanceResolver,
    asInstanceIdProvider: MutableOAuth2ServerInstanceIdProvider,
    private val authorizeEndpointCommand: AuthorizeHttpEndpointCommand,
    private val authorizeCallbackEndpointCommand: AuthorizeCallbackHttpEndpointCommand,
    private val iaeEndpointCommand: IaeHttpEndpointCommand,
) : AbstractOAuth2HttpAdapter(
        id = ID,
        execution = execution,
        mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/"),
        asInstanceResolver = asInstanceResolver,
        asInstanceIdProvider = asInstanceIdProvider,
        // /authorize, /authorize/callback, /iae are issuer-path-prefixed:
        //   https://saas.com/<tenantOrAsSlug>/authorize
        // Leading peel up to depth 2 covers (tenantSlug, asInstanceSlug) pairs.
        tenantPathPolicy = TenantPathPolicy.LeadingSlug(maxDepth = 2),
    ) {
    companion object {
        const val ID: String = "OAUTH2_AS_AUTHORIZATION"
    }

    override val endpointCommands: List<HttpEndpointCommand> =
        listOf(
            authorizeEndpointCommand,
            authorizeCallbackEndpointCommand,
            iaeEndpointCommand,
        )
}
