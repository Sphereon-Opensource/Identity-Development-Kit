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

import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.openid.oid4vci.issuer.impl.http.Oid4vciIssuerProtocolHttpAdapter
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetCredentialOfferEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetVctTypeMetadataEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.HandleCredentialEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.HandleDeferredCredentialEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.HandleNotificationEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.IssueNonceEndpointCommand
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * AppScope descriptor provider for [Oid4vciIssuerProtocolHttpAdapter].
 *
 * Provides metadata about the issuer protocol endpoints so the
 * HttpAdapterCatalog can route incoming requests to this adapter.
 *
 * Endpoints:
 * - GET /oid4vci/credentials/offers/{offerId}
 * - POST /oid4vci/nonce
 * - POST /oid4vci/credential
 * - POST /oid4vci/deferredCredential
 * - POST /oid4vci/notification
 * - GET /oid4vci/vct/{vctId}  (public SD-JWT VC type metadata, optional VctTypeMetadataProvider)
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class Oid4vciIssuerProtocolDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = Oid4vciIssuerProtocolHttpAdapter.ID

    private val basePath = "/oid4vci"

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = basePath,
                    tenantPathPolicy = TenantPathPolicy.LeadingSlug(maxDepth = 2),
                ),
            endpoints =
                listOf(
                    GetCredentialOfferEndpointCommand.ENDPOINT,
                    IssueNonceEndpointCommand.ENDPOINT,
                    HandleCredentialEndpointCommand.ENDPOINT,
                    HandleDeferredCredentialEndpointCommand.ENDPOINT,
                    HandleNotificationEndpointCommand.ENDPOINT,
                    GetVctTypeMetadataEndpointCommand.ENDPOINT,
                ).map { endpoint ->
                    endpoint.copy(pathPatterns = endpoint.pathPatterns.map { basePath + it })
                },
        )
}
