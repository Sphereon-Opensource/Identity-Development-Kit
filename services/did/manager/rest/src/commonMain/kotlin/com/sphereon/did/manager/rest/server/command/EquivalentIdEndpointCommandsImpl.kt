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
import com.sphereon.did.manager.command.AddEquivalentIdServiceCommand
import com.sphereon.did.manager.command.CreateEquivalentIdInput
import com.sphereon.did.manager.command.DeleteEquivalentIdInput
import com.sphereon.did.manager.command.DidEquivalentIdView
import com.sphereon.did.manager.command.DidIdInput
import com.sphereon.did.manager.command.EquivalentIdListResponse
import com.sphereon.did.manager.command.ListEquivalentIdsServiceCommand
import com.sphereon.did.manager.command.RemoveEquivalentIdServiceCommand
import com.sphereon.did.manager.command.StringValueBody
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListEquivalentIdsEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class ListEquivalentIdsEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: ListEquivalentIdsServiceCommand,
) : HttpEndpointCommandAdapter(
        id = ListEquivalentIdsEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ListEquivalentIdsEndpointCommand.ENDPOINT,
    ),
    ListEquivalentIdsEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val did = applyDuring(args).withExtractedParams(endpoint.pathPattern).requirePathParam("did").getOrElse { return Err(it) }
        val response = serviceCommand.execute(DidIdInput(did)).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(EquivalentIdListResponse.serializer(), response)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AddEquivalentIdEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class AddEquivalentIdEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: AddEquivalentIdServiceCommand,
) : HttpEndpointCommandAdapter(
        id = AddEquivalentIdEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = AddEquivalentIdEndpointCommand.ENDPOINT,
    ),
    AddEquivalentIdEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val body = req.requireJsonBody<StringValueBody>(endpointJson).getOrElse { return Err(it) }
        val rec = serviceCommand.execute(CreateEquivalentIdInput(did = did, body = body)).getOrElse { return Err(it) }
        return Ok(jsonResponse(201, endpointJson.encodeToString(DidEquivalentIdView.serializer(), rec)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RemoveEquivalentIdEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class RemoveEquivalentIdEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: RemoveEquivalentIdServiceCommand,
) : HttpEndpointCommandAdapter(
        id = RemoveEquivalentIdEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = RemoveEquivalentIdEndpointCommand.ENDPOINT,
    ),
    RemoveEquivalentIdEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val equivalentId = req.requirePathParam("equivalentId").getOrElse { return Err(it) }
        serviceCommand.execute(DeleteEquivalentIdInput(did = did, equivalentIdRowId = equivalentId)).getOrElse { return Err(it) }
        return Ok(GenericHttpResponse(statusCode = 204, headers = emptyMap(), body = null))
    }
}
