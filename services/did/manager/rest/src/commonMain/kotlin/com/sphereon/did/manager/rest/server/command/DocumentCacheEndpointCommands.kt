/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.manager.rest.server.command

import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.did.manager.command.GetCachedDidDocumentServiceCommand
import com.sphereon.did.manager.command.InvalidateDidDocumentServiceCommand
import com.sphereon.did.manager.command.ResolveAndCacheDidServiceCommand

// ========== GET /api/did/v1/identifiers/{did}/document ==========
interface GetDidDocumentEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = GetCachedDidDocumentServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/identifiers/{did}/document",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getDidDocument",
                commandId = COMMAND_ID,
                tags = setOf("DocumentCache"),
                summary = "Get the current DID document",
            )
    }
}

// ========== POST /api/did/v1/identifiers/{did}/document/refresh ==========
interface RefreshDidDocumentEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = ResolveAndCacheDidServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/identifiers/{did}/document/refresh",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "refreshDidDocument",
                commandId = COMMAND_ID,
                tags = setOf("DocumentCache"),
                summary = "Force a re-resolution of an EXTERNAL DID's document",
            )
    }
}

// ========== DELETE /api/did/v1/identifiers/{did}/document/cache ==========
interface InvalidateDidDocumentEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = InvalidateDidDocumentServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "/identifiers/{did}/document/cache",
                operationId = "invalidateDidDocument",
                commandId = COMMAND_ID,
                tags = setOf("DocumentCache"),
                summary = "Invalidate the cached document for a DID",
            )
    }
}
