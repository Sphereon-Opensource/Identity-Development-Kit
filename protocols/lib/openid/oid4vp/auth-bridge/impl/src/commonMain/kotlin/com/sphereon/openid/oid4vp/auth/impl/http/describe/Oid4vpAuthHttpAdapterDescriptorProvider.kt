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

package com.sphereon.openid.oid4vp.auth.impl.http.describe

import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.openid.oid4vp.auth.http.CompleteOid4vpAuthCommand
import com.sphereon.openid.oid4vp.auth.http.CompleteReconciliationWithClaimsCommand
import com.sphereon.openid.oid4vp.auth.http.CreateOid4vpAuthSessionCommand
import com.sphereon.openid.oid4vp.auth.http.GetOid4vpAuthStatusCommand
import com.sphereon.openid.oid4vp.auth.http.GetOid4vpIdvStatusCommand
import com.sphereon.openid.oid4vp.auth.http.InitiateOid4vpIdvCommand
import com.sphereon.openid.oid4vp.auth.impl.http.Oid4vpAuthHttpAdapter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * AppScope descriptor provider for [Oid4vpAuthHttpAdapter].
 *
 * Provides metadata-only information about the adapter's endpoints,
 * allowing the HttpAdapterCatalog to be built at startup without instantiating
 * SessionScope adapters.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class Oid4vpAuthHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = Oid4vpAuthHttpAdapter.ID

    private val basePath = "/auth/oid4vp"

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
                    CreateOid4vpAuthSessionCommand.ENDPOINT,
                    GetOid4vpAuthStatusCommand.ENDPOINT,
                    CompleteOid4vpAuthCommand.ENDPOINT,
                    InitiateOid4vpIdvCommand.ENDPOINT,
                    GetOid4vpIdvStatusCommand.ENDPOINT,
                    CompleteReconciliationWithClaimsCommand.ENDPOINT,
                ).map { endpoint ->
                    // Prepend adapter base path for dispatcher matching. Endpoint
                    // commands define patterns relative to the adapter's base path,
                    // but the dispatcher expects full paths; multi-pattern descriptors
                    // need the prefix on every entry.
                    endpoint.copy(pathPatterns = endpoint.pathPatterns.map { basePath + it })
                },
        )
}
