/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.okd.server

import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.data.store.okd.server.command.OkdDeleteDocumentCommand
import com.sphereon.data.store.okd.server.command.OkdGetDocumentCommand
import com.sphereon.data.store.okd.server.command.OkdGetDocumentMetadataCommand
import com.sphereon.data.store.okd.server.command.OkdGetPersonCommand
import com.sphereon.data.store.okd.server.command.OkdListPersonsCommand
import com.sphereon.data.store.okd.server.command.OkdServiceMetadataCommand
import com.sphereon.data.store.okd.server.command.OkdUpdateDocumentCommand
import com.sphereon.data.store.okd.server.command.OkdUploadDocumentCommand
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * App-scope descriptor for the OKD HTTP adapter.
 *
 * Provides metadata for route registration, OpenAPI generation, and collision detection
 * WITHOUT instantiating session-scoped commands.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdHttpAdapterDescriptorProvider", exact = true)
class OkdHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = OkdHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/okd"),
            endpoints =
                listOf(
                    OkdGetDocumentMetadataCommand.ENDPOINT,
                    OkdGetDocumentCommand.ENDPOINT,
                    OkdUpdateDocumentCommand.ENDPOINT,
                    OkdDeleteDocumentCommand.ENDPOINT,
                    OkdUploadDocumentCommand.ENDPOINT,
                    OkdGetPersonCommand.ENDPOINT,
                    OkdListPersonsCommand.ENDPOINT,
                    OkdServiceMetadataCommand.ENDPOINT,
                ),
        )
}
