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
import com.sphereon.did.manager.command.AddAlsoKnownAsServiceCommand
import com.sphereon.did.manager.command.ListAlsoKnownAsServiceCommand
import com.sphereon.did.manager.command.RemoveAlsoKnownAsServiceCommand

// ========== GET /api/did/v1/identifiers/{did}/also-known-as ==========
interface ListAlsoKnownAsEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = ListAlsoKnownAsServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/identifiers/{did}/also-known-as",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "listAlsoKnownAs",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("AlsoKnownAs"),
                summary = "List alsoKnownAs entries",
            )
    }
}

// ========== POST /api/did/v1/identifiers/{did}/also-known-as ==========
interface AddAlsoKnownAsEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = AddAlsoKnownAsServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/identifiers/{did}/also-known-as",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "addAlsoKnownAs",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("AlsoKnownAs"),
                summary = "Add an alsoKnownAs entry",
            )
    }
}

// ========== DELETE /api/did/v1/identifiers/{did}/also-known-as/{akaId} ==========
interface RemoveAlsoKnownAsEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = RemoveAlsoKnownAsServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "/identifiers/{did}/also-known-as/{akaId}",
                operationId = "removeAlsoKnownAs",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("AlsoKnownAs"),
                summary = "Remove an alsoKnownAs entry",
            )
    }
}
