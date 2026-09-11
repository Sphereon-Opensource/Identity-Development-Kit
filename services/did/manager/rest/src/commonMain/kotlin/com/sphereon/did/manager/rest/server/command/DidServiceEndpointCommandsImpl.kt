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
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.requireJsonBody
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.command.AddDidServiceServiceCommand
import com.sphereon.did.manager.command.CreateDidServiceInput
import com.sphereon.did.manager.command.DidIdInput
import com.sphereon.did.manager.command.DidServiceListResponse
import com.sphereon.did.manager.command.DidServiceUpdateBody
import com.sphereon.did.manager.command.GetDidServiceInput
import com.sphereon.did.manager.command.GetDidServiceServiceCommand
import com.sphereon.did.manager.command.ListDidServicesServiceCommand
import com.sphereon.did.manager.command.RemoveDidServiceServiceCommand
import com.sphereon.did.manager.command.UpdateDidServiceInput
import com.sphereon.did.manager.command.UpdateDidServiceServiceCommand
import com.sphereon.did.models.DidService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(ListDidServicesEndpointCommand.COMMAND_ID)
class ListDidServicesEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: ListDidServicesServiceCommand,
) : HttpEndpointCommandAdapter(
        id = ListDidServicesEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ListDidServicesEndpointCommand.ENDPOINT,
    ),
    ListDidServicesEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val did = applyDuring(args).withExtractedParams(endpoint.pathPattern).requirePathParam("did").getOrElse { return Err(it) }
        val response = serviceCommand.execute(DidIdInput(did)).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(DidServiceListResponse.serializer(), response)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(AddDidServiceEndpointCommand.COMMAND_ID)
class AddDidServiceEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: AddDidServiceServiceCommand,
) : HttpEndpointCommandAdapter(
        id = AddDidServiceEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = AddDidServiceEndpointCommand.ENDPOINT,
    ),
    AddDidServiceEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val service = req.requireJsonBody<DidService>(endpointJson).getOrElse { return Err(it) }
        val response = serviceCommand.execute(CreateDidServiceInput(did = did, service = service)).getOrElse { return Err(it) }
        return Ok(jsonResponse(201, endpointJson.encodeToString(DidService.serializer(), response)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(GetDidServiceEndpointCommand.COMMAND_ID)
class GetDidServiceEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: GetDidServiceServiceCommand,
) : HttpEndpointCommandAdapter(
        id = GetDidServiceEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetDidServiceEndpointCommand.ENDPOINT,
    ),
    GetDidServiceEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val serviceId = req.requirePathParam("serviceId").getOrElse { return Err(it) }
        val response = serviceCommand.execute(GetDidServiceInput(did = did, serviceId = serviceId)).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(DidService.serializer(), response)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(UpdateDidServiceEndpointCommand.COMMAND_ID)
class UpdateDidServiceEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: UpdateDidServiceServiceCommand,
) : HttpEndpointCommandAdapter(
        id = UpdateDidServiceEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = UpdateDidServiceEndpointCommand.ENDPOINT,
    ),
    UpdateDidServiceEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val serviceId = req.requirePathParam("serviceId").getOrElse { return Err(it) }
        val body = req.requireJsonBody<DidServiceUpdateBody>(endpointJson).getOrElse { return Err(it) }
        val response =
            serviceCommand
                .execute(UpdateDidServiceInput(did = did, serviceId = serviceId, body = body))
                .getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(DidService.serializer(), response)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(RemoveDidServiceEndpointCommand.COMMAND_ID)
class RemoveDidServiceEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: RemoveDidServiceServiceCommand,
) : HttpEndpointCommandAdapter(
        id = RemoveDidServiceEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = RemoveDidServiceEndpointCommand.ENDPOINT,
    ),
    RemoveDidServiceEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val serviceId = req.requirePathParam("serviceId").getOrElse { return Err(it) }
        serviceCommand.execute(GetDidServiceInput(did = did, serviceId = serviceId)).getOrElse { return Err(it) }
        return Ok(GenericHttpResponse(statusCode = 204, headers = emptyMap(), body = null))
    }
}
