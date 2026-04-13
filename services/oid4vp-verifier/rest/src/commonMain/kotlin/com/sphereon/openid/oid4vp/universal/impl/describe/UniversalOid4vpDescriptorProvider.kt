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

package com.sphereon.openid.oid4vp.universal.impl.describe

import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.openid.oid4vp.universal.CreateAuthRequestEndpointCommand
import com.sphereon.openid.oid4vp.universal.DeleteAuthRequestEndpointCommand
import com.sphereon.openid.oid4vp.universal.GetAuthRequestStatusEndpointCommand
import com.sphereon.openid.oid4vp.universal.impl.UniversalOid4vpHttpAdapter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * AppScope descriptor provider for UniversalOid4vpHttpAdapter.
 *
 * This provides metadata-only information about the adapter's endpoints,
 * allowing the HttpAdapterCatalog to be built at startup without instantiating
 * SessionScope adapters.
 *
 * The Universal OID4VP API exposes the following endpoints:
 * - POST /oid4vp/backend/auth/requests - Create authorization request
 * - GET /oid4vp/backend/auth/requests/{correlation_id} - Get request status
 * - DELETE /oid4vp/backend/auth/requests/{correlation_id} - Delete request
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class UniversalOid4vpDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = UniversalOid4vpHttpAdapter.ID

    private val basePath = "/oid4vp"

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
                    CreateAuthRequestEndpointCommand.ENDPOINT,
                    GetAuthRequestStatusEndpointCommand.ENDPOINT,
                    DeleteAuthRequestEndpointCommand.ENDPOINT,
                ).map { endpoint ->
                    // Prepend adapter base path for dispatcher matching.
                    // Endpoint commands define patterns relative to the adapter's base path,
                    // but the dispatcher expects full paths for candidate selection.
                    endpoint.copy(pathPattern = basePath + endpoint.pathPattern)
                },
        )
}
