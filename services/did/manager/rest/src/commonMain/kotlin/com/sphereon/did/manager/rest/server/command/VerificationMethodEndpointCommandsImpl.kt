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
import com.sphereon.did.manager.command.AddVerificationMethodServiceCommand
import com.sphereon.did.manager.command.CreateVerificationMethodInput
import com.sphereon.did.manager.command.DidIdInput
import com.sphereon.did.manager.command.GetVerificationMethodInput
import com.sphereon.did.manager.command.GetVerificationMethodServiceCommand
import com.sphereon.did.manager.command.ListVerificationMethodsServiceCommand
import com.sphereon.did.manager.command.RemoveVerificationMethodServiceCommand
import com.sphereon.did.manager.command.UpdateVerificationMethodInput
import com.sphereon.did.manager.command.UpdateVerificationMethodServiceCommand
import com.sphereon.did.manager.command.VerificationMethodCreateBody
import com.sphereon.did.manager.command.VerificationMethodListResponse
import com.sphereon.did.manager.command.VerificationMethodResponse
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonObject

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(ListVerificationMethodsEndpointCommand.COMMAND_ID)
class ListVerificationMethodsEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: ListVerificationMethodsServiceCommand,
) : HttpEndpointCommandAdapter(
        id = ListVerificationMethodsEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ListVerificationMethodsEndpointCommand.ENDPOINT,
    ),
    ListVerificationMethodsEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val did = applyDuring(args).withExtractedParams(endpoint.pathPattern).requirePathParam("did").getOrElse { return Err(it) }
        val response = serviceCommand.execute(DidIdInput(did)).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(VerificationMethodListResponse.serializer(), response)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(AddVerificationMethodEndpointCommand.COMMAND_ID)
class AddVerificationMethodEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: AddVerificationMethodServiceCommand,
) : HttpEndpointCommandAdapter(
        id = AddVerificationMethodEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = AddVerificationMethodEndpointCommand.ENDPOINT,
    ),
    AddVerificationMethodEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val body = req.requireJsonBody<VerificationMethodCreateBody>(endpointJson).getOrElse { return Err(it) }
        val response = serviceCommand.execute(CreateVerificationMethodInput(did = did, body = body)).getOrElse { return Err(it) }
        return Ok(jsonResponse(201, endpointJson.encodeToString(VerificationMethodResponse.serializer(), response)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(GetVerificationMethodEndpointCommand.COMMAND_ID)
class GetVerificationMethodEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: GetVerificationMethodServiceCommand,
) : HttpEndpointCommandAdapter(
        id = GetVerificationMethodEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetVerificationMethodEndpointCommand.ENDPOINT,
    ),
    GetVerificationMethodEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val methodId = req.requirePathParam("methodId").getOrElse { return Err(it) }
        val response = serviceCommand.execute(GetVerificationMethodInput(did = did, methodId = methodId)).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(VerificationMethodResponse.serializer(), response)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(UpdateVerificationMethodEndpointCommand.COMMAND_ID)
class UpdateVerificationMethodEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: UpdateVerificationMethodServiceCommand,
) : HttpEndpointCommandAdapter(
        id = UpdateVerificationMethodEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = UpdateVerificationMethodEndpointCommand.ENDPOINT,
    ),
    UpdateVerificationMethodEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val methodId = req.requirePathParam("methodId").getOrElse { return Err(it) }
        val raw = req.requireJsonBody<JsonObject>(endpointJson).getOrElse { return Err(it) }
        val response =
            serviceCommand
                .execute(UpdateVerificationMethodInput(did = did, methodId = methodId, rawBody = raw))
                .getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(VerificationMethodResponse.serializer(), response)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(RemoveVerificationMethodEndpointCommand.COMMAND_ID)
class RemoveVerificationMethodEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: RemoveVerificationMethodServiceCommand,
) : HttpEndpointCommandAdapter(
        id = RemoveVerificationMethodEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = RemoveVerificationMethodEndpointCommand.ENDPOINT,
    ),
    RemoveVerificationMethodEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val methodId = req.requirePathParam("methodId").getOrElse { return Err(it) }
        serviceCommand.execute(GetVerificationMethodInput(did = did, methodId = methodId)).getOrElse { return Err(it) }
        return Ok(GenericHttpResponse(statusCode = 204, headers = emptyMap(), body = null))
    }
}
