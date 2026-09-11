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
import com.sphereon.did.manager.command.GetMethodCapabilitiesServiceCommand
import com.sphereon.did.manager.command.GetMethodCapabilitySummaryServiceCommand
import com.sphereon.did.manager.command.ListSupportedMethodsServiceCommand

// ========== GET /api/did/v1/methods ==========
interface ListSupportedMethodsEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = ListSupportedMethodsServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/methods",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "listSupportedMethods",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("Capabilities"),
                summary = "List all registered DID methods with their capabilities",
            )
    }
}

// ========== GET /api/did/v1/methods/{method}/capabilities ==========
interface GetMethodCapabilitiesEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = GetMethodCapabilitiesServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/methods/{method}/capabilities",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getMethodCapabilities",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("Capabilities"),
                summary = "Get full capability detail for a DID method",
            )
    }
}

// ========== GET /api/did/v1/methods/{method}/capabilities/summary ==========
interface GetMethodCapabilitySummaryEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = GetMethodCapabilitySummaryServiceCommand.COMMAND_ID
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/methods/{method}/capabilities/summary",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getMethodCapabilitySummary",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("Capabilities"),
                summary = "Get a simplified capability matrix for a DID method",
            )
    }
}
