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
import com.sphereon.core.api.http.command.RoutableSlugLookup
import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.context.MutableResolvedTenantIdProvider
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.MutableOAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceResolver
import com.sphereon.oauth2.server.authorization.command.introspection.IntrospectionHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.par.ParHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.revocation.RevocationHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.token.TokenHttpEndpointCommand
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Token surface for the OAuth2 Authorization Server.
 *
 * Routes:
 * - `POST /token` (RFC 6749 §3.2)
 * - `POST /introspect` (RFC 7662)
 * - `POST /revoke` (RFC 7009)
 * - `POST /par` (RFC 9126; gated behind the per-server `par` FeaturePolicy)
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class OAuth2TokenHttpAdapter(
    execution: SessionExecution,
    asInstanceResolver: OAuth2ServerInstanceResolver,
    asInstanceIdProvider: MutableOAuth2ServerInstanceIdProvider,
    slugLookup: RoutableSlugLookup,
    tenantIdProvider: MutableResolvedTenantIdProvider,
    private val tokenEndpointCommand: TokenHttpEndpointCommand,
    private val introspectionEndpointCommand: IntrospectionHttpEndpointCommand,
    private val revocationEndpointCommand: RevocationHttpEndpointCommand,
    private val parEndpointCommand: ParHttpEndpointCommand,
) : AbstractOAuth2HttpAdapter(
        id = ID,
        execution = execution,
        mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/"),
        asInstanceResolver = asInstanceResolver,
        asInstanceIdProvider = asInstanceIdProvider,
        slugLookup = slugLookup,
        tenantIdProvider = tenantIdProvider,
        // /token, /introspect, /revoke, /par are issuer-path-prefixed.
        tenantPathPolicy = TenantPathPolicy.LeadingSlug(maxDepth = 2),
    ) {
    companion object {
        const val ID: String = "oauth2.as.token"
    }

    override val endpointCommands: List<HttpEndpointCommand> =
        listOf(
            tokenEndpointCommand,
            introspectionEndpointCommand,
            revocationEndpointCommand,
            parEndpointCommand,
        )
}
