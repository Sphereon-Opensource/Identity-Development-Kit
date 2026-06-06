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
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetIssuerMetadataEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.Oid4vciErrorRenderer
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Command-backed HTTP adapter for OID4VCI issuer metadata endpoint.
 *
 * - **GET /.well-known/openid-credential-issuer** — Credential issuer metadata discovery
 *
 * This adapter uses an empty base path because the .well-known endpoint must
 * mount at the server root per OID4VCI 1.1 Section 13.2.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class Oid4vciIssuerMetadataHttpAdapter(
    execution: SessionExecution,
    slugLookup: RoutableSlugLookup,
    tenantIdProvider: MutableResolvedTenantIdProvider,
    private val getMetadataCommand: GetIssuerMetadataEndpointCommand,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = "",
            ),
        tenantPathPolicy = TenantPathPolicy.WellKnownSuffix(maxDepth = 2),
        errorRenderer = Oid4vciErrorRenderer(),
    ) {
    override val routableSlugLookup: RoutableSlugLookup = slugLookup
    override val resolvedTenantIdProvider: MutableResolvedTenantIdProvider = tenantIdProvider

    companion object {
        const val ID: String = "OID4VCI_ISSUER_METADATA"
    }

    override val endpointCommands: List<HttpEndpointCommand> =
        listOf(
            getMetadataCommand,
        )

    @ContributesTo(SessionScope::class)
    interface Graph {
        val oid4vciIssuerMetadataHttpAdapter: Oid4vciIssuerMetadataHttpAdapter
    }
}
