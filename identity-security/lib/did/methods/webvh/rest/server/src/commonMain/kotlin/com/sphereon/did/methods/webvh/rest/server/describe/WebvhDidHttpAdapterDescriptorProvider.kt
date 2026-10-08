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

package com.sphereon.did.methods.webvh.rest.server.describe

import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.StaticPublicApiDescriptor
import com.sphereon.did.methods.webvh.command.CreateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.CreateWitnessProofServiceCommand
import com.sphereon.did.methods.webvh.command.DeactivateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.FetchWebvhLogServiceCommand
import com.sphereon.did.methods.webvh.command.ReplayWebvhLogServiceCommand
import com.sphereon.did.methods.webvh.command.UpdateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.UpdateWitnessFileServiceCommand
import com.sphereon.did.methods.webvh.rest.server.WebvhDidHttpAdapter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * App-scoped descriptor exposing the `did:webvh` REST adapter to the
 * `HttpAdapterCatalog` at startup, so route collision detection runs
 * before any session is created.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class WebvhDidHttpAdapterDescriptorProvider :
    StaticPublicApiDescriptor(
        adapterId = WebvhDidHttpAdapter.ID,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = WebvhDidHttpAdapter.BASE_PATH,
            ),
        endpoints =
            listOf(
                CreateWebvhDidServiceCommand.ENDPOINT,
                UpdateWebvhDidServiceCommand.ENDPOINT,
                DeactivateWebvhDidServiceCommand.ENDPOINT,
                CreateWitnessProofServiceCommand.ENDPOINT,
                UpdateWitnessFileServiceCommand.ENDPOINT,
                FetchWebvhLogServiceCommand.ENDPOINT,
                ReplayWebvhLogServiceCommand.ENDPOINT,
            ),
    )
