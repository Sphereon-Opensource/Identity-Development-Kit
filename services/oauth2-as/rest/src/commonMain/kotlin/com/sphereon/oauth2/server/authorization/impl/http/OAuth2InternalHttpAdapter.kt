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
import com.sphereon.oauth2.server.authorization.command.token.ProvisionSigningKeyHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.token.RegisterPreAuthorizedCodeHttpEndpointCommand
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Internal cross-service surface for the OAuth2 Authorization Server.
 *
 * Routes:
 * - `POST /internal/preauth/register` (called by the OID4VCI issuer in separate-process
 *   deployments; Basic-auth protected via `oauth2.servers.{id}.internal-clients`)
 * - `POST /internal/provision/signing-key` (called by the platform at tenant registration to
 *   provision the per-tenant AS signing key into THIS AS's KMS; Basic-auth protected)
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class OAuth2InternalHttpAdapter(
    execution: SessionExecution,
    asInstanceResolver: OAuth2ServerInstanceResolver,
    asInstanceIdProvider: MutableOAuth2ServerInstanceIdProvider,
    slugLookup: RoutableSlugLookup,
    tenantIdProvider: MutableResolvedTenantIdProvider,
    private val registerPreAuthorizedCodeCommand: RegisterPreAuthorizedCodeHttpEndpointCommand,
    private val provisionSigningKeyCommand: ProvisionSigningKeyHttpEndpointCommand,
) : AbstractOAuth2HttpAdapter(
        id = ID,
        execution = execution,
        mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/"),
        asInstanceResolver = asInstanceResolver,
        asInstanceIdProvider = asInstanceIdProvider,
        slugLookup = slugLookup,
        tenantIdProvider = tenantIdProvider,
        tenantPathPolicy = TenantPathPolicy.LeadingSlug(maxDepth = 2),
    ) {
    companion object {
        const val ID: String = "oauth2.as.internal"
    }

    override val endpointCommands: List<HttpEndpointCommand> =
        listOf(registerPreAuthorizedCodeCommand, provisionSigningKeyCommand)
}
