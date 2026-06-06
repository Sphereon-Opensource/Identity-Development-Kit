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
import com.sphereon.did.manager.command.AddVerificationRelationshipServiceCommand
import com.sphereon.did.manager.command.ListVerificationRelationshipsServiceCommand
import com.sphereon.did.manager.command.RemoveVerificationRelationshipServiceCommand

// ========== GET /api/dids/v1/dids/{did}/verification-relationships ==========
interface ListVerificationRelationshipsEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = ListVerificationRelationshipsServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/dids/{did}/verification-relationships",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "listVerificationRelationships",
                commandId = COMMAND_ID,
                tags = setOf("VerificationRelationships"),
                summary = "List verification relationships on a DID",
            )
    }
}

// ========== POST /api/dids/v1/dids/{did}/verification-relationships ==========
interface AddVerificationRelationshipEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = AddVerificationRelationshipServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/dids/{did}/verification-relationships",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "addVerificationRelationship",
                commandId = COMMAND_ID,
                tags = setOf("VerificationRelationships"),
                summary = "Add a verification relationship linking a verification method to a purpose",
            )
    }
}

// ========== DELETE /api/dids/v1/dids/{did}/verification-relationships/{relationshipId} ==========
interface RemoveVerificationRelationshipEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = RemoveVerificationRelationshipServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "/dids/{did}/verification-relationships/{relationshipId}",
                operationId = "removeVerificationRelationship",
                commandId = COMMAND_ID,
                tags = setOf("VerificationRelationships"),
                summary = "Remove a verification relationship",
            )
    }
}
