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

package com.sphereon.openid.oid4vp.verifier.impl.http.describe

import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.openid.oid4vp.verifier.impl.http.Oid4vpVerifierHttpAdapter
import com.sphereon.openid.oid4vp.verifier.impl.http.command.DirectPostResponseEndpointCommand
import com.sphereon.openid.oid4vp.verifier.impl.http.command.GetRequestObjectEndpointCommand
import com.sphereon.openid.oid4vp.verifier.impl.http.command.PostRequestObjectEndpointCommand
import com.sphereon.openid.oid4vp.verifier.impl.http.command.ReadyEndpointCommand
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * AppScope descriptor provider for [Oid4vpVerifierHttpAdapter].
 *
 * Provides metadata about the verifier's request_uri endpoints so the
 * [HttpAdapterCatalog] can route incoming requests to this adapter.
 *
 * Endpoints:
 * - GET  /oid4vp/request-uri/{correlationId} — Fetch request object by request_uri
 * - POST /oid4vp/request-uri/{correlationId} — Fetch request object with wallet metadata
 * - POST /oid4vp/auth/response — Handle direct_post authorization response from wallet
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class Oid4vpVerifierDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = Oid4vpVerifierHttpAdapter.ID

    private val basePath = "/oid4vp"

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = basePath,
                    tenantPathPolicy = TenantPathPolicy.LeadingSlug(maxDepth = 1),
                ),
            endpoints =
                listOf(
                    GetRequestObjectEndpointCommand.ENDPOINT,
                    PostRequestObjectEndpointCommand.ENDPOINT,
                    DirectPostResponseEndpointCommand.ENDPOINT,
                    ReadyEndpointCommand.ENDPOINT,
                ).map { endpoint ->
                    endpoint.copy(pathPatterns = endpoint.pathPatterns.map { basePath + it })
                },
        )
}
