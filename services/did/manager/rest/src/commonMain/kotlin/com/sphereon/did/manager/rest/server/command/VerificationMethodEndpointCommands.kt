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
import com.sphereon.did.manager.command.AddVerificationMethodServiceCommand
import com.sphereon.did.manager.command.GetVerificationMethodServiceCommand
import com.sphereon.did.manager.command.ListVerificationMethodsServiceCommand
import com.sphereon.did.manager.command.RemoveVerificationMethodServiceCommand
import com.sphereon.did.manager.command.UpdateVerificationMethodServiceCommand

// ========== GET /api/dids/v1/dids/{did}/verification-methods ==========
interface ListVerificationMethodsEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = ListVerificationMethodsServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/dids/{did}/verification-methods",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "listVerificationMethods",
                commandId = COMMAND_ID,
                tags = setOf("VerificationMethods"),
                summary = "List verification methods on a DID",
            )
    }
}

// ========== POST /api/dids/v1/dids/{did}/verification-methods ==========
interface AddVerificationMethodEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = AddVerificationMethodServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/dids/{did}/verification-methods",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "addVerificationMethod",
                commandId = COMMAND_ID,
                tags = setOf("VerificationMethods"),
                summary = "Add a verification method to a DID",
            )
    }
}

// ========== GET /api/dids/v1/dids/{did}/verification-methods/{methodId} ==========
interface GetVerificationMethodEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = GetVerificationMethodServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/dids/{did}/verification-methods/{methodId}",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getVerificationMethod",
                commandId = COMMAND_ID,
                tags = setOf("VerificationMethods"),
                summary = "Get a verification method by id",
            )
    }
}

// ========== PATCH /api/dids/v1/dids/{did}/verification-methods/{methodId} ==========
interface UpdateVerificationMethodEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = UpdateVerificationMethodServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.PATCH,
                pathPattern = "/dids/{did}/verification-methods/{methodId}",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "updateVerificationMethod",
                commandId = COMMAND_ID,
                tags = setOf("VerificationMethods"),
                summary = "Update a verification method",
            )
    }
}

// ========== DELETE /api/dids/v1/dids/{did}/verification-methods/{methodId} ==========
interface RemoveVerificationMethodEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = RemoveVerificationMethodServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "/dids/{did}/verification-methods/{methodId}",
                operationId = "removeVerificationMethod",
                commandId = COMMAND_ID,
                tags = setOf("VerificationMethods"),
                summary = "Remove a verification method from a DID",
            )
    }
}
