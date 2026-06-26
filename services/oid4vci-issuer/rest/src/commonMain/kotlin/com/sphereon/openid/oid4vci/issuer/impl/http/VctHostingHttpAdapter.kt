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
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.OpenApiHints
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetVctTypeMetadataEndpointCommand
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * PUBLIC HTTP adapter that hosts SD-JWT VC type metadata (VCT documents), mounted at
 * [BASE_PATH] (`/public/schema/vct`). Serves `GET /public/schema/vct/{vctId}`.
 *
 * Deliberately separate from the `/oid4vci` protocol surface and unversioned: the `vct` URL a wallet
 * dereferences is embedded in issued credentials, so it must stay stable across upgrades. The
 * `/public/` prefix marks it as the cacheable, unauthenticated hosting surface (mirroring
 * `/public/statuslists`).
 *
 * Unlike a status list, a VCT is NOT globally unique — multiple tenants may advertise the same `vct`
 * (e.g. a shared credit-card VCT used by an issuer and its branded partners). Resolution is therefore
 * tenant-scoped: this adapter carries no tenant-path slug; the tenant is resolved from the request
 * (subdomain) and the principal stays anonymous, and the bound
 * [com.sphereon.openid.oid4vci.issuer.config.VctTypeMetadataProvider] resolves within that tenant's
 * config. (The EDK request-instance-aware replacement additionally selects the issuer instance.)
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class VctHostingHttpAdapter(
    execution: SessionExecution,
    private val vctTypeMetadataCommand: GetVctTypeMetadataEndpointCommand,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = BASE_PATH,
            ),
    ) {
    override val endpointCommands: List<HttpEndpointCommand> =
        listOf(
            vctTypeMetadataCommand,
        )

    override val openApiHints =
        OpenApiHints(
            tags = setOf("SD-JWT VC Type Metadata"),
            operationIdPrefix = "vctHosting",
        )

    companion object {
        const val ID: String = "oid4vci-vct-hosting"

        /**
         * Public, unauthenticated hosting mount for SD-JWT VC type metadata. Unversioned and stable:
         * issued credentials embed `<base>/public/schema/vct/<vctId>`, so this must not change across upgrades.
         */
        const val BASE_PATH: String = "/public/schema/vct"
    }
}
