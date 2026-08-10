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

package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.RoutableSlugLookup
import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.context.MutableResolvedTenantIdProvider
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetJwtVcIssuerMetadataRootEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetJwtVcIssuerMetadataScopedEndpointCommand
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * HTTP adapter for SD-JWT VC Issuer Metadata (draft-ietf-oauth-sd-jwt-vc §3.5).
 *
 * Mounts two routes at the server root:
 *   - `GET /.well-known/jwt-vc-issuer`              — bare, for root-hosted issuers
 *   - `GET /.well-known/jwt-vc-issuer/{issuer_path}` — path-scoped per RFC 8615
 *
 * The JWKS exposed here uses the same kid-resolution pipeline as the credential
 * issuance path, so `cnf`-verifying wallets find the issuer's signing key under a
 * kid byte-identical to the credential's JWT-header `kid`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class JwtVcIssuerMetadataHttpAdapter(
    execution: SessionExecution,
    slugLookup: RoutableSlugLookup,
    tenantIdProvider: MutableResolvedTenantIdProvider,
    private val rootCommand: GetJwtVcIssuerMetadataRootEndpointCommand,
    private val scopedCommand: GetJwtVcIssuerMetadataScopedEndpointCommand,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = "",
            ),
        tenantPathPolicy = TenantPathPolicy.WellKnownSuffix(maxDepth = 2),
    ) {
    override val routableSlugLookup: RoutableSlugLookup = slugLookup
    override val resolvedTenantIdProvider: MutableResolvedTenantIdProvider = tenantIdProvider

    companion object {
        const val ID: String = "oid4vci.jwt-vc-metadata.http"
    }

    override val endpointCommands: List<HttpEndpointCommand> = listOf(rootCommand, scopedCommand)

    @ContributesTo(SessionScope::class)
    interface Graph {
        val jwtVcIssuerMetadataHttpAdapter: JwtVcIssuerMetadataHttpAdapter
    }
}
