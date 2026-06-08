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
import com.sphereon.did.manager.command.AddControllerServiceCommand
import com.sphereon.did.manager.command.ListControllersServiceCommand
import com.sphereon.did.manager.command.RemoveControllerServiceCommand

// ========== GET /api/did/v1/identifiers/{did}/controllers ==========
interface ListControllersEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = ListControllersServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/identifiers/{did}/controllers",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "listControllers",
                commandId = COMMAND_ID,
                tags = setOf("Controllers"),
                summary = "List controllers",
            )
    }
}

// ========== POST /api/did/v1/identifiers/{did}/controllers ==========
interface AddControllerEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = AddControllerServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/identifiers/{did}/controllers",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "addController",
                commandId = COMMAND_ID,
                tags = setOf("Controllers"),
                summary = "Add a controller",
            )
    }
}

// ========== DELETE /api/did/v1/identifiers/{did}/controllers/{controllerId} ==========
interface RemoveControllerEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = RemoveControllerServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "/identifiers/{did}/controllers/{controllerId}",
                operationId = "removeController",
                commandId = COMMAND_ID,
                tags = setOf("Controllers"),
                summary = "Remove a controller",
            )
    }
}
