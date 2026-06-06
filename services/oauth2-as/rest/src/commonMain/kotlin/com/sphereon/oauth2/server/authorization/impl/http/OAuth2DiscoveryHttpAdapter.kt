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
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.context.MutableResolvedTenantIdProvider
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.MutableOAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceResolver
import com.sphereon.oauth2.server.authorization.command.discovery.JwksHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.discovery.OAuth2ServerMetadataHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.discovery.OpenidDiscoveryHttpEndpointCommand
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Discovery surface for the OAuth2 Authorization Server.
 *
 * Routes:
 * - `GET /.well-known/oauth-authorization-server` (RFC 8414)
 * - `GET /.well-known/oauth-authorization-server/{tenant-path}` (RFC 8414)
 * - `GET /.well-known/openid-configuration` (OpenID Connect Discovery 1.0)
 * - `GET /.well-known/openid-configuration/{tenant-path}` (OpenID Connect Discovery 1.0)
 * - `GET /.well-known/jwks.json`
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class OAuth2DiscoveryHttpAdapter(
    execution: SessionExecution,
    asInstanceResolver: OAuth2ServerInstanceResolver,
    asInstanceIdProvider: MutableOAuth2ServerInstanceIdProvider,
    slugLookup: RoutableSlugLookup,
    tenantIdProvider: MutableResolvedTenantIdProvider,
    private val oauth2ServerMetadataCommand: OAuth2ServerMetadataHttpEndpointCommand,
    private val openidDiscoveryCommand: OpenidDiscoveryHttpEndpointCommand,
    private val jwksCommand: JwksHttpEndpointCommand,
) : AbstractOAuth2HttpAdapter(
        id = ID,
        execution = execution,
        mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/"),
        asInstanceResolver = asInstanceResolver,
        asInstanceIdProvider = asInstanceIdProvider,
        slugLookup = slugLookup,
        tenantIdProvider = tenantIdProvider,
    ) {
    companion object {
        const val ID: String = "OAUTH2_AS_DISCOVERY"
    }

    override val endpointCommands: List<HttpEndpointCommand> =
        listOf(
            oauth2ServerMetadataCommand,
            openidDiscoveryCommand,
            jwksCommand,
        )
}
