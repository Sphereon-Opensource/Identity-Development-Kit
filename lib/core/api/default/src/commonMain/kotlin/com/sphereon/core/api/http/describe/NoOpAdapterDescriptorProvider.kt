package com.sphereon.core.api.http.describe

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet

/**
 * AppScope descriptor provider for SignaturesHttpAdapter.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class NoOpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = ID

    override fun describe(): HttpAdapterDescription = HttpAdapterDescription(
        id = id,
        mount = HttpAdapterMount(
            serverPrefix = "",
            adapterBasePath = ""
        ),
        endpoints = listOf()
    )

    companion object {
        const val ID = "__NO_OP__"
    }
}
