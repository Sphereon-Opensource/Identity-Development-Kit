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
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.command.DidIdInput
import com.sphereon.did.manager.command.GetCachedDidDocumentServiceCommand
import com.sphereon.did.manager.command.InvalidateDidDocumentServiceCommand
import com.sphereon.did.manager.command.ResolveAndCacheDidServiceCommand
import com.sphereon.did.models.DidDocument
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetDidDocumentEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class GetDidDocumentEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: GetCachedDidDocumentServiceCommand,
) : HttpEndpointCommandAdapter(
        id = GetDidDocumentEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetDidDocumentEndpointCommand.ENDPOINT,
    ),
    GetDidDocumentEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val did = applyDuring(args).withExtractedParams(endpoint.pathPattern).requirePathParam("did").getOrElse { return Err(it) }
        val doc = serviceCommand.execute(DidIdInput(did)).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(DidDocument.serializer(), doc)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RefreshDidDocumentEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class RefreshDidDocumentEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: ResolveAndCacheDidServiceCommand,
) : HttpEndpointCommandAdapter(
        id = RefreshDidDocumentEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = RefreshDidDocumentEndpointCommand.ENDPOINT,
    ),
    RefreshDidDocumentEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val did = applyDuring(args).withExtractedParams(endpoint.pathPattern).requirePathParam("did").getOrElse { return Err(it) }
        val document = serviceCommand.execute(DidIdInput(did)).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(DidDocument.serializer(), document)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<InvalidateDidDocumentEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class InvalidateDidDocumentEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: InvalidateDidDocumentServiceCommand,
) : HttpEndpointCommandAdapter(
        id = InvalidateDidDocumentEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = InvalidateDidDocumentEndpointCommand.ENDPOINT,
    ),
    InvalidateDidDocumentEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val did = applyDuring(args).withExtractedParams(endpoint.pathPattern).requirePathParam("did").getOrElse { return Err(it) }
        serviceCommand.execute(DidIdInput(did)).getOrElse { return Err(it) }
        return Ok(GenericHttpResponse(statusCode = 204, headers = emptyMap(), body = null))
    }
}
