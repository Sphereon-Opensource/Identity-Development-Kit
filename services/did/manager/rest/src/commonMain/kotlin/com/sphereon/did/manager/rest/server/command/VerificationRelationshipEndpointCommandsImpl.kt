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
import com.sphereon.core.api.http.command.optionalQueryParam
import com.sphereon.core.api.http.command.requireJsonBody
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.VerificationRelationship
import com.sphereon.did.manager.command.AddVerificationRelationshipBody
import com.sphereon.did.manager.command.AddVerificationRelationshipInput
import com.sphereon.did.manager.command.AddVerificationRelationshipServiceCommand
import com.sphereon.did.manager.command.ListVerificationRelationshipsInput
import com.sphereon.did.manager.command.ListVerificationRelationshipsServiceCommand
import com.sphereon.did.manager.command.RemoveVerificationRelationshipInput
import com.sphereon.did.manager.command.RemoveVerificationRelationshipServiceCommand
import com.sphereon.did.manager.command.VerificationRelationshipListResponse
import com.sphereon.did.models.VerificationPurpose
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListVerificationRelationshipsEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class ListVerificationRelationshipsEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: ListVerificationRelationshipsServiceCommand,
) : HttpEndpointCommandAdapter(
        id = ListVerificationRelationshipsEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ListVerificationRelationshipsEndpointCommand.ENDPOINT,
    ),
    ListVerificationRelationshipsEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val purposeParam = req.optionalQueryParam("purpose").getOrElse { return Err(it) }
        val purpose =
            purposeParam?.let { raw ->
                VerificationPurpose.entries.firstOrNull { it.value == raw || it.name.equals(raw, ignoreCase = true) }
                    ?: return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message =
                                "Unknown verification purpose: '$raw'. Valid values: " +
                                    VerificationPurpose.entries.joinToString { it.value },
                        ),
                    )
            }
        val response =
            serviceCommand
                .execute(ListVerificationRelationshipsInput(did = did, purpose = purpose))
                .getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(VerificationRelationshipListResponse.serializer(), response)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AddVerificationRelationshipEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class AddVerificationRelationshipEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: AddVerificationRelationshipServiceCommand,
) : HttpEndpointCommandAdapter(
        id = AddVerificationRelationshipEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = AddVerificationRelationshipEndpointCommand.ENDPOINT,
    ),
    AddVerificationRelationshipEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val body = req.requireJsonBody<AddVerificationRelationshipBody>(endpointJson).getOrElse { return Err(it) }
        val response =
            serviceCommand.execute(AddVerificationRelationshipInput(did = did, body = body)).getOrElse { return Err(it) }
        return Ok(jsonResponse(201, endpointJson.encodeToString(VerificationRelationship.serializer(), response)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RemoveVerificationRelationshipEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class RemoveVerificationRelationshipEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: RemoveVerificationRelationshipServiceCommand,
) : HttpEndpointCommandAdapter(
        id = RemoveVerificationRelationshipEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = RemoveVerificationRelationshipEndpointCommand.ENDPOINT,
    ),
    RemoveVerificationRelationshipEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val req = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val relationshipId = req.requirePathParam("relationshipId").getOrElse { return Err(it) }
        serviceCommand.execute(RemoveVerificationRelationshipInput(did = did, relationshipId = relationshipId)).getOrElse { return Err(it) }
        return Ok(GenericHttpResponse(statusCode = 204, headers = emptyMap(), body = null))
    }
}
