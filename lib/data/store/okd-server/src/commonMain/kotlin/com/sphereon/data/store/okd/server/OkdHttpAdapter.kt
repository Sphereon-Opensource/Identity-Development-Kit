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

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommandRegistry
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * OKD HTTP adapter that exposes blob store operations as OKD-compliant REST endpoints.
 *
 * External educational systems (KRS, SVS, BPV modules) talk OKD to this adapter.
 * Internally, all operations delegate to [BlobService], so any blob store backend
 * (filesystem, memory, S3, SharePoint) serves as an OKD-compliant DMS.
 * Mounted at `/okd` — endpoints:
 * - `GET  /okd/documents/{documentId}` — Download binary
 * - `PATCH /okd/documents/{documentId}` — Replace content
 * - `DELETE /okd/documents/{documentId}` — Delete document
 * - `GET  /okd/documents/{documentId}/metadata` — Get metadata
 * - `GET  /okd/persons` — List persons
 * - `GET  /okd/persons/{personId}` — Get person by ID
 * - `GET  /okd/` — Service metadata
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpAdapter>())
@StringKey(OkdHttpAdapter.ID)
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdHttpAdapter", exact = true)
class OkdHttpAdapter(
    execution: SessionExecution,
    endpointCommandRegistry: HttpEndpointCommandRegistry,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        endpointCommandRegistry = endpointCommandRegistry,
        mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/okd"),
    ) {

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("OkdHttpAdapterGraph", exact = true)
    @ContributesTo(SessionScope::class)
    interface Graph {
        val okdHttpAdapter: OkdHttpAdapter
    }

    companion object {
        const val ID = "okd.dms.http"
    }
}
