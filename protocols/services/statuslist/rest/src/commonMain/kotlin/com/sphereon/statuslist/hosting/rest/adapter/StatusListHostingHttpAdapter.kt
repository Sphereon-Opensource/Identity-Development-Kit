/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.statuslist.hosting.rest.adapter

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommandRegistry
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.OpenApiHints
import com.sphereon.di.session.SessionScope
import com.sphereon.statuslist.hosting.rest.StatusListHostingApiConstants
import com.sphereon.statuslist.hosting.rest.StatusListHostingConfig
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * PUBLIC HTTP adapter that hosts the signed status-list token, mounted at `/public/statuslists`. Serves
 * the by-correlationId token endpoint (`GET /public/statuslists/{correlationId}`). It is
 * unauthenticated and cacheable: the deployment's auth layer leaves this mount open, and the
 * response carries a `Cache-Control` header. It serves the RAW signed token a verifier resolves.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpAdapter>())
@StringKey(StatusListHostingHttpAdapter.ID)
class StatusListHostingHttpAdapter(
    execution: SessionExecution,
    endpointCommandRegistry: HttpEndpointCommandRegistry,
    hostingConfig: StatusListHostingConfig,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        endpointCommandRegistry = endpointCommandRegistry,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = hostingConfig.basePath,
            ),
    ) {

    override val openApiHints =
        OpenApiHints(
            tags = setOf(StatusListHostingApiConstants.Tags.STATUS_LIST_HOSTING),
            operationIdPrefix = "statusListHosting",
        )

    companion object {
        const val ID = "statuslist.hosting.http"
    }
}
