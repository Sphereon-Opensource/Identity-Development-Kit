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
import com.sphereon.did.manager.command.AddAlsoKnownAsServiceCommand
import com.sphereon.did.manager.command.AlsoKnownAsListResponse
import com.sphereon.did.manager.command.CreateAlsoKnownAsInput
import com.sphereon.did.manager.command.DeleteAlsoKnownAsInput
import com.sphereon.did.manager.command.DidAlsoKnownAsView
import com.sphereon.did.manager.command.DidIdInput
import com.sphereon.did.manager.command.ListAlsoKnownAsServiceCommand
import com.sphereon.did.manager.command.RemoveAlsoKnownAsServiceCommand
import com.sphereon.did.manager.command.StringValueBody
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListAlsoKnownAsEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class ListAlsoKnownAsEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: ListAlsoKnownAsServiceCommand,
) : HttpEndpointCommandAdapter(
        id = ListAlsoKnownAsEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ListAlsoKnownAsEndpointCommand.ENDPOINT,
    ),
    ListAlsoKnownAsEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val did = applyDuring(args).withExtractedParams(endpoint.pathPattern).requirePathParam("did").getOrElse { return Err(it) }
        val response = serviceCommand.execute(DidIdInput(did)).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(AlsoKnownAsListResponse.serializer(), response)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AddAlsoKnownAsEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class AddAlsoKnownAsEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: AddAlsoKnownAsServiceCommand,
) : HttpEndpointCommandAdapter(
        id = AddAlsoKnownAsEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = AddAlsoKnownAsEndpointCommand.ENDPOINT,
    ),
    AddAlsoKnownAsEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val body = req.requireJsonBody<StringValueBody>(endpointJson).getOrElse { return Err(it) }
        val rec = serviceCommand.execute(CreateAlsoKnownAsInput(did = did, body = body)).getOrElse { return Err(it) }
        return Ok(jsonResponse(201, endpointJson.encodeToString(DidAlsoKnownAsView.serializer(), rec)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RemoveAlsoKnownAsEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class RemoveAlsoKnownAsEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: RemoveAlsoKnownAsServiceCommand,
) : HttpEndpointCommandAdapter(
        id = RemoveAlsoKnownAsEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = RemoveAlsoKnownAsEndpointCommand.ENDPOINT,
    ),
    RemoveAlsoKnownAsEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val akaId = req.requirePathParam("akaId").getOrElse { return Err(it) }
        serviceCommand.execute(DeleteAlsoKnownAsInput(did = did, akaId = akaId)).getOrElse { return Err(it) }
        return Ok(GenericHttpResponse(statusCode = 204, headers = emptyMap(), body = null))
    }
}
