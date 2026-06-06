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

package com.sphereon.openid.oid4vp.dcql.store.rest.describe

import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.openid.oid4vp.dcql.store.http.CreateDcqlQueryEndpointCommand
import com.sphereon.openid.oid4vp.dcql.store.http.DeleteDcqlQueryEndpointCommand
import com.sphereon.openid.oid4vp.dcql.store.http.GetDcqlQueryEndpointCommand
import com.sphereon.openid.oid4vp.dcql.store.http.ListDcqlQueriesEndpointCommand
import com.sphereon.openid.oid4vp.dcql.store.http.PatchDcqlQueryEndpointCommand
import com.sphereon.openid.oid4vp.dcql.store.http.ReplaceDcqlQueryEndpointCommand
import com.sphereon.openid.oid4vp.dcql.store.rest.DcqlQueryAdminHttpAdapter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * AppScope descriptor provider for [DcqlQueryAdminHttpAdapter].
 *
 * Provides metadata-only information about the adapter's endpoints, allowing the
 * HttpAdapterCatalog to be built at startup without instantiating SessionScope adapters.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class DcqlQueryAdminHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = DcqlQueryAdminHttpAdapter.ID

    private val basePath = "/api/v1/oid4vp"

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
                    ListDcqlQueriesEndpointCommand.ENDPOINT,
                    CreateDcqlQueryEndpointCommand.ENDPOINT,
                    GetDcqlQueryEndpointCommand.ENDPOINT,
                    ReplaceDcqlQueryEndpointCommand.ENDPOINT,
                    PatchDcqlQueryEndpointCommand.ENDPOINT,
                    DeleteDcqlQueryEndpointCommand.ENDPOINT,
                ).map { endpoint ->
                    endpoint.copy(pathPatterns = endpoint.pathPatterns.map { basePath + it })
                },
        )
}
