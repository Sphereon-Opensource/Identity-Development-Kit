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
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
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
    override fun describe(): HttpAdapterDescription = HttpAdapterDescription(
        id = id,
        mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/okd"),
        endpoints = listOf(
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
