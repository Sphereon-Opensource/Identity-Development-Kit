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
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.manager.MethodCapabilitySummary
import com.sphereon.did.manager.command.GetMethodCapabilitiesServiceCommand
import com.sphereon.did.manager.command.GetMethodCapabilitySummaryServiceCommand
import com.sphereon.did.manager.command.ListSupportedMethodsServiceCommand
import com.sphereon.did.manager.command.MethodCapabilityListResponse
import com.sphereon.did.manager.command.MethodInput
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
@StringKey(ListSupportedMethodsEndpointCommand.COMMAND_ID)
class ListSupportedMethodsEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: ListSupportedMethodsServiceCommand,
) : HttpEndpointCommandAdapter(
        id = ListSupportedMethodsEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ListSupportedMethodsEndpointCommand.ENDPOINT,
    ),
    ListSupportedMethodsEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        applyDuring(args)
        val response = serviceCommand.execute(Unit).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, capabilityEndpointJson.encodeToString(MethodCapabilityListResponse.serializer(), response)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(GetMethodCapabilitiesEndpointCommand.COMMAND_ID)
class GetMethodCapabilitiesEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: GetMethodCapabilitiesServiceCommand,
) : HttpEndpointCommandAdapter(
        id = GetMethodCapabilitiesEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetMethodCapabilitiesEndpointCommand.ENDPOINT,
    ),
    GetMethodCapabilitiesEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val method = applyDuring(args).withExtractedParams(endpoint.pathPattern).requirePathParam("method").getOrElse { return Err(it) }
        val response = serviceCommand.execute(MethodInput(method)).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, capabilityEndpointJson.encodeToString(DidMethodCapabilities.serializer(), response)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(GetMethodCapabilitySummaryEndpointCommand.COMMAND_ID)
class GetMethodCapabilitySummaryEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: GetMethodCapabilitySummaryServiceCommand,
) : HttpEndpointCommandAdapter(
        id = GetMethodCapabilitySummaryEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetMethodCapabilitySummaryEndpointCommand.ENDPOINT,
    ),
    GetMethodCapabilitySummaryEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val method = applyDuring(args).withExtractedParams(endpoint.pathPattern).requirePathParam("method").getOrElse { return Err(it) }
        val response = serviceCommand.execute(MethodInput(method)).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, capabilityEndpointJson.encodeToString(MethodCapabilitySummary.serializer(), response)))
    }
}
