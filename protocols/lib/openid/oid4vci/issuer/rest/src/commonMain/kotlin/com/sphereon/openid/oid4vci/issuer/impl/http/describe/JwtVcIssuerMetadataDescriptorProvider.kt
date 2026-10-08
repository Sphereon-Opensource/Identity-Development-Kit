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
import com.sphereon.openid.oid4vci.issuer.impl.http.JwtVcIssuerMetadataHttpAdapter
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetJwtVcIssuerMetadataRootEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetJwtVcIssuerMetadataScopedEndpointCommand
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * AppScope descriptor provider for [JwtVcIssuerMetadataHttpAdapter].
 *
 * Registers the two SD-JWT VC Issuer Metadata endpoints with the HTTP adapter catalog:
 * - GET /.well-known/jwt-vc-issuer
 * - GET /.well-known/jwt-vc-issuer/{issuer_path}
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class JwtVcIssuerMetadataDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = JwtVcIssuerMetadataHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = "",
                    tenantPathPolicy = TenantPathPolicy.WellKnownSuffix(maxDepth = 2),
                ),
            endpoints =
                listOf(
                    GetJwtVcIssuerMetadataRootEndpointCommand.ENDPOINT,
                    GetJwtVcIssuerMetadataScopedEndpointCommand.ENDPOINT,
                ),
        )
}
