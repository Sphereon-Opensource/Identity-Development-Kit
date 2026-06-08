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
 *
 */

package com.sphereon.did.hosting.rest.adapter.describe

import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.did.hosting.rest.DidHostingConfig
import com.sphereon.did.hosting.rest.http.GetDidJsonEndpointCommand
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * AppScope descriptor provider for the public DID hosting endpoint. Required alongside the
 * SessionScope [com.sphereon.did.hosting.rest.adapter.DidHostingHttpAdapter] so the routes register at
 * server startup. Reads the same [DidHostingConfig.basePath] as the adapter so the catalog and
 * dispatch agree on every depth pattern.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class DidHostingHttpAdapterDescriptorProvider(
    private val hostingConfig: DidHostingConfig,
) : HttpAdapterDescriptorProvider {
    override val id: String = "did-hosting"

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = hostingConfig.basePath,
                ),
            endpoints =
                listOf(GetDidJsonEndpointCommand.ENDPOINT)
                    .map { it.copy(pathPatterns = it.pathPatterns.map { p -> hostingConfig.basePath + p }) },
        )
}
