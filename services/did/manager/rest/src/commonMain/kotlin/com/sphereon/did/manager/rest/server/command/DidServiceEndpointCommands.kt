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
import com.sphereon.did.manager.command.AddDidServiceServiceCommand
import com.sphereon.did.manager.command.GetDidServiceServiceCommand
import com.sphereon.did.manager.command.ListDidServicesServiceCommand
import com.sphereon.did.manager.command.RemoveDidServiceServiceCommand
import com.sphereon.did.manager.command.UpdateDidServiceServiceCommand

// ========== GET /api/dids/v1/dids/{did}/services ==========
interface ListDidServicesEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = ListDidServicesServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/dids/{did}/services",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "listDidServices",
                commandId = COMMAND_ID,
                tags = setOf("DidServices"),
                summary = "List services on a DID",
            )
    }
}

// ========== POST /api/dids/v1/dids/{did}/services ==========
interface AddDidServiceEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = AddDidServiceServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/dids/{did}/services",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "addDidService",
                commandId = COMMAND_ID,
                tags = setOf("DidServices"),
                summary = "Add a service to a DID",
            )
    }
}

// ========== GET /api/dids/v1/dids/{did}/services/{serviceId} ==========
interface GetDidServiceEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = GetDidServiceServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/dids/{did}/services/{serviceId}",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getDidService",
                commandId = COMMAND_ID,
                tags = setOf("DidServices"),
                summary = "Get a service by id",
            )
    }
}

// ========== PATCH /api/dids/v1/dids/{did}/services/{serviceId} ==========
interface UpdateDidServiceEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = UpdateDidServiceServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.PATCH,
                pathPattern = "/dids/{did}/services/{serviceId}",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "updateDidService",
                commandId = COMMAND_ID,
                tags = setOf("DidServices"),
                summary = "Update a service",
            )
    }
}

// ========== DELETE /api/dids/v1/dids/{did}/services/{serviceId} ==========
interface RemoveDidServiceEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = RemoveDidServiceServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "/dids/{did}/services/{serviceId}",
                operationId = "removeDidService",
                commandId = COMMAND_ID,
                tags = setOf("DidServices"),
                summary = "Remove a service from a DID",
            )
    }
}
