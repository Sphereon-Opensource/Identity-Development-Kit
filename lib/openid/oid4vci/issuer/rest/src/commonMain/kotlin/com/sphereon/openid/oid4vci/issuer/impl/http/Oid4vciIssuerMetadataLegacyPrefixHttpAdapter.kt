/*
 * Â© 2026 Sphereon International B.V.
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
import com.sphereon.core.api.http.command.HttpEndpointCommandRegistry
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.RoutableSlugLookup
import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.context.MutableResolvedTenantIdProvider
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.config.MutableOid4vciIssuerInstanceIdProvider
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerInstanceResolver
import com.sphereon.openid.oid4vci.issuer.impl.http.command.Oid4vciErrorRenderer
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Legacy-prefix metadata adapter for wallets that request
 * `/<issuer-path>/.well-known/openid-credential-issuer`.
 *
 * OID4VCI 1.0 uses the suffix form
 * `/.well-known/openid-credential-issuer/<issuer-path>`. This adapter exists only
 * to keep older wallet integrations working while still resolving tenant slugs
 * through the same DB-backed path-peel mechanism as protocol endpoints.
 */
@Inject
@SingleIn(SessionScope::class)
class Oid4vciIssuerMetadataLegacyPrefixHttpAdapter(
    execution: SessionExecution,
    endpointCommandRegistry: HttpEndpointCommandRegistry,
    slugLookup: RoutableSlugLookup,
    tenantIdProvider: MutableResolvedTenantIdProvider,
    issuerInstanceResolver: Oid4vciIssuerInstanceResolver,
    issuerInstanceIdProvider: MutableOid4vciIssuerInstanceIdProvider,
) : AbstractOid4vciIssuerHttpAdapter(
        id = ID,
        execution = execution,
        endpointCommandRegistry = endpointCommandRegistry,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = "",
            ),
        tenantPathPolicy = TenantPathPolicy.LeadingSlug(maxDepth = 2, required = true),
        issuerInstanceResolver = issuerInstanceResolver,
        issuerInstanceIdProvider = issuerInstanceIdProvider,
        slugLookup = slugLookup,
        tenantIdProvider = tenantIdProvider,
    ) {
    companion object {
        const val ID: String = "oid4vci.issuer-metadata-legacy.http"
    }

}
