/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.statuslist.hosting.rest.http

import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.statuslist.StatusListContentTypes
import com.sphereon.statuslist.hosting.rest.StatusListHostingApiConstants.CommandIds
import com.sphereon.statuslist.hosting.rest.StatusListHostingApiConstants.Paths
import com.sphereon.statuslist.hosting.rest.StatusListHostingApiConstants.Tags

private val HOSTING_TAGS = setOf(Tags.STATUS_LIST_HOSTING)

/**
 * Media types a hosted status-list token can carry. Declared on the descriptor's `produces` for
 * OpenAPI / content negotiation; the actual response `Content-Type` is set per-response from the
 * resolved token's own `contentType`.
 */
private val TOKEN_MEDIA_TYPES =
    setOf<MediaType>(
        MediaType.Custom(StatusListContentTypes.STATUSLIST_JWT),
        MediaType.Custom(StatusListContentTypes.STATUSLIST_CWT),
        MediaType.Custom(StatusListContentTypes.VC_JWT),
    )

/**
 * `GET /public/statuslists/{correlationId}` returns the raw signed status-list token for the list
 * with this business correlation id. Public, unauthenticated, cacheable. Delegates to
 * `statuslist.token.get`.
 */
interface GetStatusListTokenByCorrelationIdEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = CommandIds.HTTP_GET_TOKEN_BY_CORRELATION_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = Paths.TOKEN_BY_CORRELATION_ID,
                produces = TOKEN_MEDIA_TYPES,
                commandId = COMMAND_ID,
                operationId = "getStatusListTokenByCorrelationId",
                tags = HOSTING_TAGS,
                summary = "Resolve the signed status-list token by correlation id",
            )
    }
}
