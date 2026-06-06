/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.statuslist.hosting.rest.admin

import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.statuslist.hosting.rest.StatusListHostingConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * AppScope descriptor provider for the simple by-index status-list admin endpoints. Required
 * alongside the SessionScope [StatusListAdminHttpAdapter] so the routes register at server startup
 * (the AppScope catalog must not instantiate Session-scoped adapters); without it the dispatcher
 * 404s before reaching the adapter. Reads the same configurable [StatusListHostingConfig.basePath]
 * as the adapter so catalog and dispatch agree.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class StatusListAdminHttpAdapterDescriptorProvider(
    private val hostingConfig: StatusListHostingConfig,
) : HttpAdapterDescriptorProvider {
    override val id: String = StatusListAdminHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = hostingConfig.basePath,
                ),
            endpoints =
                listOf(
                    GetStatusListEntryStatusEndpointCommand.ENDPOINT,
                    RevokeStatusListEntryEndpointCommand.ENDPOINT,
                    ClearStatusListEndpointCommand.ENDPOINT,
                ).map { it.copy(pathPatterns = it.pathPatterns.map { p -> hostingConfig.basePath + p }) },
        )
}
