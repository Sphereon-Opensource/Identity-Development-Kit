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
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.data.store.okd.server.command.OkdDeleteDocumentCommand
import com.sphereon.data.store.okd.server.command.OkdGetDocumentCommand
import com.sphereon.data.store.okd.server.command.OkdGetDocumentMetadataCommand
import com.sphereon.data.store.okd.server.command.OkdGetPersonCommand
import com.sphereon.data.store.okd.server.command.OkdListPersonsCommand
import com.sphereon.data.store.okd.server.command.OkdServiceMetadataCommand
import com.sphereon.data.store.okd.server.command.OkdUpdateDocumentCommand
import com.sphereon.data.store.okd.server.command.OkdUploadDocumentCommand
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
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
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdHttpAdapter", exact = true)
class OkdHttpAdapter(
    execution: SessionExecution,
    private val getDocumentCommand: OkdGetDocumentCommand,
    private val updateDocumentCommand: OkdUpdateDocumentCommand,
    private val deleteDocumentCommand: OkdDeleteDocumentCommand,
    private val uploadDocumentCommand: OkdUploadDocumentCommand,
    private val getDocumentMetadataCommand: OkdGetDocumentMetadataCommand,
    private val listPersonsCommand: OkdListPersonsCommand,
    private val getPersonCommand: OkdGetPersonCommand,
    private val serviceMetadataCommand: OkdServiceMetadataCommand,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/okd"),
    ) {
    override val endpointCommands: List<HttpEndpointCommand> =
        listOf(
            getDocumentMetadataCommand, // Must be before getDocumentCommand (more specific path)
            getDocumentCommand,
            updateDocumentCommand,
            deleteDocumentCommand,
            uploadDocumentCommand,
            getPersonCommand, // Must be before listPersonsCommand (more specific path)
            listPersonsCommand,
            serviceMetadataCommand,
        )

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
