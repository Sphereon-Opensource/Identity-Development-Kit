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

package com.sphereon.openid.oid4vci.issuer.impl.http.describe

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.openid.oid4vci.issuer.impl.http.Oid4vciIssuerMetadataHttpAdapter
import com.sphereon.openid.oid4vci.issuer.impl.http.Oid4vciIssuerMetadataLegacyPrefixHttpAdapter
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetIssuerMetadataEndpointCommand
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * AppScope descriptor provider for [Oid4vciIssuerMetadataHttpAdapter].
 *
 * Reads `oid4vci.issuer.identifier` from app config at boot and emits a single
 * descriptor whose [com.sphereon.core.api.http.describe.HttpEndpointDescriptor.pathPatterns]
 * covers every URL the metadata endpoint should answer at:
 *
 * - **Bare-host issuer** (`https://host`) — `/.well-known/openid-credential-issuer`.
 * - **Path-bearing issuer** (`https://host/<issuer-path>`) — both
 *   `/.well-known/openid-credential-issuer/<issuer-path>` (RFC 8414 §3 spec form)
 *   and `/<issuer-path>/.well-known/openid-credential-issuer` (legacy prefix), with
 *   the bare URL deliberately omitted so the catalog never advertises a discovery
 *   URL for an issuer identifier that doesn't actually exist at that host root.
 *
 * NOTE: this provider now emits only the OID4VCI 1.0 suffix form; the legacy
 * prefix form is registered by [Oid4vciIssuerMetadataLegacyPrefixDescriptorProvider].
 *
 * Both this provider and [Oid4vciIssuerMetadataHttpAdapter] resolve the URL set
 * via [GetIssuerMetadataEndpointCommand.specDescriptorFor]; the helper keeps the
 * runtime endpoint and the catalog descriptor in lockstep.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class Oid4vciIssuerMetadataDescriptorProvider(
    private val appConfig: AppConfigService,
) : HttpAdapterDescriptorProvider {
    override val id: String = Oid4vciIssuerMetadataHttpAdapter.ID

    override fun describe(): HttpAdapterDescription {
        val issuerIdentifier = appConfig.getPropertyAsString(ISSUER_IDENTIFIER_KEY).orEmpty()
        return HttpAdapterDescription(
            id = id,
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = "",
                    tenantPathPolicy = TenantPathPolicy.WellKnownSuffix(maxDepth = 2),
                ),
            endpoints = listOf(GetIssuerMetadataEndpointCommand.specDescriptorFor(issuerIdentifier)),
        )
    }

    private companion object {
        const val ISSUER_IDENTIFIER_KEY = "oid4vci.issuer.identifier"
    }
}

@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class Oid4vciIssuerMetadataLegacyPrefixDescriptorProvider(
    private val appConfig: AppConfigService,
) : HttpAdapterDescriptorProvider {
    override val id: String = Oid4vciIssuerMetadataLegacyPrefixHttpAdapter.ID

    override fun describe(): HttpAdapterDescription {
        val issuerIdentifier = appConfig.getPropertyAsString(ISSUER_IDENTIFIER_KEY).orEmpty()
        return HttpAdapterDescription(
            id = id,
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = "",
                    // `required = false` so the slug-less default-tenant path
                    // (`/<issuer-path>/.well-known/openid-credential-issuer`) is served
                    // via the dispatcher's depth-0 (no-peel) candidate. Wallets (e.g.
                    // okhttp-based) request this legacy prefix form WITHOUT a tenant
                    // slug; `required = true` only registered `/{tenant}/<issuer-path>/...`
                    // so the bare path 404'd and the wallet never got issuer metadata.
                    // Tenant-slug forms still match via the depth-1+ peel candidates.
                    tenantPathPolicy = TenantPathPolicy.LeadingSlug(maxDepth = 2, required = false),
                ),
            endpoints = listOf(GetIssuerMetadataEndpointCommand.legacyPrefixDescriptorFor(issuerIdentifier)),
        )
    }

    private companion object {
        const val ISSUER_IDENTIFIER_KEY = "oid4vci.issuer.identifier"
    }
}
