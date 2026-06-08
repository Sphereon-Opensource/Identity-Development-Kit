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
import com.sphereon.did.manager.command.AddKeyMappingServiceCommand
import com.sphereon.did.manager.command.ListKeyMappingsServiceCommand
import com.sphereon.did.manager.command.RemoveKeyMappingServiceCommand

// ========== GET /api/did/v1/identifiers/{did}/key-mappings ==========
interface ListKeyMappingsEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = ListKeyMappingsServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/identifiers/{did}/key-mappings",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "listKeyMappings",
                commandId = COMMAND_ID,
                tags = setOf("KeyMappings"),
                summary = "List key mappings on a DID",
            )
    }
}

// ========== POST /api/did/v1/identifiers/{did}/key-mappings ==========
interface AddKeyMappingEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = AddKeyMappingServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/identifiers/{did}/key-mappings",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "addKeyMapping",
                commandId = COMMAND_ID,
                tags = setOf("KeyMappings"),
                summary = "Create a key mapping",
            )
    }
}

// ========== DELETE /api/did/v1/identifiers/{did}/key-mappings/{mappingId} ==========
interface RemoveKeyMappingEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = RemoveKeyMappingServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "/identifiers/{did}/key-mappings/{mappingId}",
                operationId = "removeKeyMapping",
                commandId = COMMAND_ID,
                tags = setOf("KeyMappings"),
                summary = "Delete a key mapping",
            )
    }
}
