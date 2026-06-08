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

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.OpenApiHints
import com.sphereon.di.session.SessionScope
import com.sphereon.statuslist.hosting.rest.StatusListHostingApiConstants
import com.sphereon.statuslist.hosting.rest.StatusListHostingConfig
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * SIMPLE, by-index status-list admin HTTP adapter, mounted at the dedicated
 * [StatusListHostingConfig.managementBasePath] (default `/api/statuslist/v1`), kept entirely separate
 * from the public token hosting adapter so the cacheable hosting surface stays free of mutating routes:
 *
 * - GET  <managementBasePath>/{id}/entries/{index}        — read the status of a single index.
 * - POST <managementBasePath>/{id}/entries/{index}/revoke — revoke the credential at an index.
 * - POST <managementBasePath>/{id}/clear                  — reset the list to all-valid.
 *
 * This is the open-core admin surface (no business keys); the EDK `lib-statuslist-management-rest`
 * is the durable, authenticated, business-key management API. Unauthenticated here; a production
 * deployment guards these behind admin auth.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class StatusListAdminHttpAdapter(
    execution: SessionExecution,
    hostingConfig: StatusListHostingConfig,
    getStatus: GetStatusListEntryStatusEndpointCommand,
    revoke: RevokeStatusListEntryEndpointCommand,
    clear: ClearStatusListEndpointCommand,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = hostingConfig.managementBasePath,
            ),
    ) {
    override val endpointCommands: List<HttpEndpointCommand> = listOf(getStatus, revoke, clear)

    override val openApiHints =
        OpenApiHints(
            tags = setOf(StatusListHostingApiConstants.Tags.STATUS_LIST_ADMIN),
            operationIdPrefix = "statusListAdmin",
        )

    companion object {
        const val ID = "statuslist-admin"
    }
}
