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
import com.sphereon.did.manager.command.AddEquivalentIdServiceCommand
import com.sphereon.did.manager.command.ListEquivalentIdsServiceCommand
import com.sphereon.did.manager.command.RemoveEquivalentIdServiceCommand

// ========== GET /api/did/v1/identifiers/{did}/equivalent-ids ==========
interface ListEquivalentIdsEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = ListEquivalentIdsServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/identifiers/{did}/equivalent-ids",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "listEquivalentIds",
                commandId = COMMAND_ID,
                tags = setOf("EquivalentIds"),
                summary = "List equivalent identifiers",
            )
    }
}

// ========== POST /api/did/v1/identifiers/{did}/equivalent-ids ==========
interface AddEquivalentIdEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = AddEquivalentIdServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/identifiers/{did}/equivalent-ids",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "addEquivalentId",
                commandId = COMMAND_ID,
                tags = setOf("EquivalentIds"),
                summary = "Add an equivalent identifier",
            )
    }
}

// ========== DELETE /api/did/v1/identifiers/{did}/equivalent-ids/{equivalentId} ==========
interface RemoveEquivalentIdEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = RemoveEquivalentIdServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "/identifiers/{did}/equivalent-ids/{equivalentId}",
                operationId = "removeEquivalentId",
                commandId = COMMAND_ID,
                tags = setOf("EquivalentIds"),
                summary = "Remove an equivalent identifier",
            )
    }
}
