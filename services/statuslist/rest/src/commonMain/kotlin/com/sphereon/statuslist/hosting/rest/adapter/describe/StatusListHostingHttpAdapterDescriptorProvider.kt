/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.statuslist.hosting.rest.adapter.describe

import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.statuslist.hosting.rest.StatusListHostingConfig
import com.sphereon.statuslist.hosting.rest.http.GetStatusListTokenByCorrelationIdEndpointCommand
import com.sphereon.statuslist.hosting.rest.http.GetStatusListTokenByIdEndpointCommand
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * AppScope descriptor provider for the public status-list hosting endpoints. Required alongside the
 * SessionScope [com.sphereon.statuslist.hosting.rest.adapter.StatusListHostingHttpAdapter] so the
 * routes register at server startup (the SessionScope adapter only handles dispatch). Reads the same
 * configurable [StatusListHostingConfig.basePath] as the adapter so the catalog and dispatch agree.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class StatusListHostingHttpAdapterDescriptorProvider(
    private val hostingConfig: StatusListHostingConfig,
) : HttpAdapterDescriptorProvider {
    override val id: String = "statuslist-hosting"

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
                    GetStatusListTokenByIdEndpointCommand.ENDPOINT,
                    GetStatusListTokenByCorrelationIdEndpointCommand.ENDPOINT,
                ).map { it.copy(pathPatterns = it.pathPatterns.map { p -> hostingConfig.basePath + p }) },
        )
}
