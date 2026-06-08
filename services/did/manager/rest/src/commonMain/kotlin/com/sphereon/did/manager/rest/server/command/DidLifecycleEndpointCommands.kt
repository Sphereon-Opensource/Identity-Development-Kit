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
import com.sphereon.did.manager.command.CreateDidServiceCommand
import com.sphereon.did.manager.command.DeactivateDidServiceCommand
import com.sphereon.did.manager.command.DeleteDidServiceCommand
import com.sphereon.did.manager.command.GetDidServiceCommand
import com.sphereon.did.manager.command.ListDidsServiceCommand
import com.sphereon.did.manager.command.ReplaceDidServiceCommand
import com.sphereon.did.manager.command.ResolveDidServiceCommand
import com.sphereon.did.manager.command.TrackExternalDidServiceCommand
import com.sphereon.did.manager.command.UpdateDidServiceCommand

/*
 * HTTP endpoint commands for the DID lifecycle (create, list, import, get, update, replace,
 * delete, deactivate, resolve). Each impl is a thin shim: parse the request, delegate to the
 * matching IDK-20 service command, serialize the result. Paths are relative to the adapter
 * mount basePath `/api/did/v1`.
 */

// ========== POST /api/did/v1/identifiers — createDid ==========
interface CreateDidEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = CreateDidServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/identifiers",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "createDid",
                commandId = COMMAND_ID,
                tags = setOf("Dids"),
                summary = "Create a new DID",
            )
    }
}

// ========== GET /api/did/v1/identifiers — listDids ==========
interface ListDidsEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = ListDidsServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/identifiers",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "listDids",
                commandId = COMMAND_ID,
                tags = setOf("Dids"),
                summary = "List DIDs",
            )
    }
}

interface TrackExternalDidEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = TrackExternalDidServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/identifiers/external",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "trackExternalDid",
                commandId = COMMAND_ID,
                tags = setOf("Dids"),
                summary = "Track an externally-managed DID",
            )
    }
}

// ========== GET /api/did/v1/identifiers/{did} — getDid ==========
interface GetDidEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = GetDidServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/identifiers/{did}",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getDid",
                commandId = COMMAND_ID,
                tags = setOf("Dids"),
                summary = "Get a DID by identifier",
            )
    }
}

// ========== PATCH /api/did/v1/identifiers/{did} — updateDid ==========
interface UpdateDidEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = UpdateDidServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.PATCH,
                pathPattern = "/identifiers/{did}",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "updateDid",
                commandId = COMMAND_ID,
                tags = setOf("Dids"),
                summary = "Partially update a DID (JSON Merge Patch)",
            )
    }
}

// ========== PUT /api/did/v1/identifiers/{did} — replaceDid ==========
interface ReplaceDidEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = ReplaceDidServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.PUT,
                pathPattern = "/identifiers/{did}",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "replaceDid",
                commandId = COMMAND_ID,
                tags = setOf("Dids"),
                summary = "Replace a DID's declarative collections",
            )
    }
}

// ========== DELETE /api/did/v1/identifiers/{did} — deleteDid ==========
interface DeleteDidEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = DeleteDidServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "/identifiers/{did}",
                operationId = "deleteDid",
                commandId = COMMAND_ID,
                tags = setOf("Dids"),
                summary = "Soft-delete a DID locally",
            )
    }
}

// ========== POST /api/did/v1/identifiers/{did}/actions/deactivate — deactivateDid ==========
interface DeactivateDidEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = DeactivateDidServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/identifiers/{did}/actions/deactivate",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "deactivateDid",
                commandId = COMMAND_ID,
                tags = setOf("Dids"),
                summary = "Deactivate a DID on its network",
            )
    }
}

// ========== GET /api/did/v1/identifiers/{did}/resolve — resolveDid ==========
interface ResolveDidEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = ResolveDidServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/identifiers/{did}/resolve",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "resolveDid",
                commandId = COMMAND_ID,
                tags = setOf("Dids"),
                summary = "Resolve a DID to its DID Document via the resolver registry",
            )
    }
}
