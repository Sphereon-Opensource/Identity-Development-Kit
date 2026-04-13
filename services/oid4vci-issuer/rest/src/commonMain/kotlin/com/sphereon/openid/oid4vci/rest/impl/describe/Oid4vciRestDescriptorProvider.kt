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

package com.sphereon.openid.oid4vci.rest.impl.describe

import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.openid.oid4vci.rest.CreateCredentialOfferEndpointCommand
import com.sphereon.openid.oid4vci.rest.DeleteCredentialOfferEndpointCommand
import com.sphereon.openid.oid4vci.rest.GetCredentialOfferStatusEndpointCommand
import com.sphereon.openid.oid4vci.rest.impl.Oid4vciRestHttpAdapter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * AppScope descriptor provider for [Oid4vciRestHttpAdapter].
 *
 * Provides metadata about the OID4VCI backend REST endpoints so the
 * HttpAdapterCatalog can route incoming requests to this adapter.
 *
 * Endpoints:
 * - POST /oid4vci/backend/credential/offers — Create credential offer
 * - GET /oid4vci/backend/credential/offers/{correlation_id} — Get offer status
 * - DELETE /oid4vci/backend/credential/offers/{correlation_id} — Delete offer
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class Oid4vciRestDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = Oid4vciRestHttpAdapter.ID

    private val basePath = "/oid4vci"

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = basePath,
                ),
            endpoints =
                listOf(
                    CreateCredentialOfferEndpointCommand.ENDPOINT,
                    GetCredentialOfferStatusEndpointCommand.ENDPOINT,
                    DeleteCredentialOfferEndpointCommand.ENDPOINT,
                ).map { endpoint ->
                    endpoint.copy(pathPattern = basePath + endpoint.pathPattern)
                },
        )
}
