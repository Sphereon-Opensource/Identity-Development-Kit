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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.requireJsonBody
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.command.AddControllerServiceCommand
import com.sphereon.did.manager.command.ControllerListResponse
import com.sphereon.did.manager.command.CreateControllerInput
import com.sphereon.did.manager.command.DeleteControllerInput
import com.sphereon.did.manager.command.DidControllerView
import com.sphereon.did.manager.command.DidIdInput
import com.sphereon.did.manager.command.ListControllersServiceCommand
import com.sphereon.did.manager.command.RemoveControllerServiceCommand
import com.sphereon.did.manager.command.StringValueBody
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListControllersEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class ListControllersEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: ListControllersServiceCommand,
) : HttpEndpointCommandAdapter(
        id = ListControllersEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ListControllersEndpointCommand.ENDPOINT,
    ),
    ListControllersEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val did = applyDuring(args).withExtractedParams(endpoint.pathPattern).requirePathParam("did").getOrElse { return Err(it) }
        val response = serviceCommand.execute(DidIdInput(did)).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(ControllerListResponse.serializer(), response)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AddControllerEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class AddControllerEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: AddControllerServiceCommand,
) : HttpEndpointCommandAdapter(
        id = AddControllerEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = AddControllerEndpointCommand.ENDPOINT,
    ),
    AddControllerEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val body = req.requireJsonBody<StringValueBody>(endpointJson).getOrElse { return Err(it) }
        val rec = serviceCommand.execute(CreateControllerInput(did = did, body = body)).getOrElse { return Err(it) }
        return Ok(jsonResponse(201, endpointJson.encodeToString(DidControllerView.serializer(), rec)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RemoveControllerEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class RemoveControllerEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: RemoveControllerServiceCommand,
) : HttpEndpointCommandAdapter(
        id = RemoveControllerEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = RemoveControllerEndpointCommand.ENDPOINT,
    ),
    RemoveControllerEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val controllerId = req.requirePathParam("controllerId").getOrElse { return Err(it) }
        serviceCommand.execute(DeleteControllerInput(did = did, controllerId = controllerId)).getOrElse { return Err(it) }
        return Ok(GenericHttpResponse(statusCode = 204, headers = emptyMap(), body = null))
    }
}
