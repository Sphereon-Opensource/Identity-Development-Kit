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
import com.sphereon.did.manager.command.AddKeyMappingServiceCommand
import com.sphereon.did.manager.command.CreateKeyMappingInput
import com.sphereon.did.manager.command.DeleteKeyMappingInput
import com.sphereon.did.manager.command.DidIdInput
import com.sphereon.did.manager.command.KeyMappingCreateBody
import com.sphereon.did.manager.command.KeyMappingListResponse
import com.sphereon.did.manager.command.KeyMappingResponse
import com.sphereon.did.manager.command.ListKeyMappingsServiceCommand
import com.sphereon.did.manager.command.RemoveKeyMappingServiceCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListKeyMappingsEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class ListKeyMappingsEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: ListKeyMappingsServiceCommand,
) : HttpEndpointCommandAdapter(
        id = ListKeyMappingsEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ListKeyMappingsEndpointCommand.ENDPOINT,
    ),
    ListKeyMappingsEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val did = applyDuring(args).withExtractedParams(endpoint.pathPattern).requirePathParam("did").getOrElse { return Err(it) }
        val response = serviceCommand.execute(DidIdInput(did)).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(KeyMappingListResponse.serializer(), response)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AddKeyMappingEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class AddKeyMappingEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: AddKeyMappingServiceCommand,
) : HttpEndpointCommandAdapter(
        id = AddKeyMappingEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = AddKeyMappingEndpointCommand.ENDPOINT,
    ),
    AddKeyMappingEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val body = req.requireJsonBody<KeyMappingCreateBody>(endpointJson).getOrElse { return Err(it) }
        val response = serviceCommand.execute(CreateKeyMappingInput(did = did, body = body)).getOrElse { return Err(it) }
        return Ok(jsonResponse(201, endpointJson.encodeToString(KeyMappingResponse.serializer(), response)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RemoveKeyMappingEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class RemoveKeyMappingEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: RemoveKeyMappingServiceCommand,
) : HttpEndpointCommandAdapter(
        id = RemoveKeyMappingEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = RemoveKeyMappingEndpointCommand.ENDPOINT,
    ),
    RemoveKeyMappingEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val mappingId = req.requirePathParam("mappingId").getOrElse { return Err(it) }
        serviceCommand.execute(DeleteKeyMappingInput(did = did, mappingId = mappingId)).getOrElse { return Err(it) }
        return Ok(GenericHttpResponse(statusCode = 204, headers = emptyMap(), body = null))
    }
}
