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
import com.sphereon.core.api.http.command.optionalBoolQueryParam
import com.sphereon.core.api.http.command.optionalIntQueryParam
import com.sphereon.core.api.http.command.optionalQueryParam
import com.sphereon.core.api.http.command.requireJsonBody
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.DidDeactivateOptions
import com.sphereon.did.manager.DidFilter
import com.sphereon.did.manager.DidRole
import com.sphereon.did.manager.DidSortField
import com.sphereon.did.manager.DidUpdateOptions
import com.sphereon.did.manager.ManagedDid
import com.sphereon.did.manager.SortDirection
import com.sphereon.did.manager.command.CreateDidInput
import com.sphereon.did.manager.command.CreateDidServiceCommand
import com.sphereon.did.manager.command.DeactivateDidInput
import com.sphereon.did.manager.command.DeactivateDidRequest
import com.sphereon.did.manager.command.DeactivateDidServiceCommand
import com.sphereon.did.manager.command.DeleteDidServiceCommand
import com.sphereon.did.manager.command.Did
import com.sphereon.did.manager.command.DidIdInput
import com.sphereon.did.manager.command.GetDidInput
import com.sphereon.did.manager.command.GetDidServiceCommand
import com.sphereon.did.manager.command.ListDidsOutput
import com.sphereon.did.manager.command.ListDidsServiceCommand
import com.sphereon.did.manager.command.ReplaceDidBody
import com.sphereon.did.manager.command.ReplaceDidInput
import com.sphereon.did.manager.command.ReplaceDidServiceCommand
import com.sphereon.did.manager.command.ResolveDidInput
import com.sphereon.did.manager.command.ResolveDidServiceCommand
import com.sphereon.did.manager.command.TrackExternalDidInput
import com.sphereon.did.manager.command.TrackExternalDidServiceCommand
import com.sphereon.did.manager.command.UpdateDidInput
import com.sphereon.did.manager.command.UpdateDidServiceCommand
import com.sphereon.did.manager.command.toWire
import com.sphereon.did.manager.rest.server.DidManagerRestConfig
import com.sphereon.did.resolver.DidResolutionResult
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateDidEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class CreateDidEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: CreateDidServiceCommand,
) : HttpEndpointCommandAdapter(
        id = CreateDidEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = CreateDidEndpointCommand.ENDPOINT,
    ),
    CreateDidEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val input = request.requireJsonBody<CreateDidInput>(endpointJson).getOrElse { return Err(it) }
        val created = serviceCommand.execute(input).getOrElse { return Err(it) }
        return Ok(jsonResponse(201, endpointJson.encodeToString(Did.serializer(), created.toWire(emptySet()))))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListDidsEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class ListDidsEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: ListDidsServiceCommand,
    private val restConfig: DidManagerRestConfig,
) : HttpEndpointCommandAdapter(
        id = ListDidsEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ListDidsEndpointCommand.ENDPOINT,
    ),
    ListDidsEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val role =
            request.optionalQueryParam("role").getOrElse { return Err(it) }?.let { raw ->
                runCatching { DidRole.valueOf(raw.uppercase()) }.getOrElse {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Unknown role '$raw'; expected one of ${DidRole.entries.joinToString { it.name }}.",
                        ),
                    )
                }
            }
        val includeDeactivated = request.optionalBoolQueryParam("includeDeactivated").getOrElse { return Err(it) } ?: false
        val includeDeleted = request.optionalBoolQueryParam("includeDeleted").getOrElse { return Err(it) } ?: false
        val page = request.optionalIntQueryParam("page", min = 0).getOrElse { return Err(it) } ?: 0
        // OpenAPI: size has min=1 and a deployment-configurable max; default
        // DidFilter.DEFAULT_PAGE_SIZE when omitted. The cap is read from DidManagerRestConfig
        // so deployments can tune it via didManager.rest.maxPageSize without forking.
        val size =
            request
                .optionalIntQueryParam("size", min = 1, max = restConfig.maxPageSize)
                .getOrElse { return Err(it) } ?: DidFilter.DEFAULT_PAGE_SIZE
        val sort = parseSortField(request).getOrElse { return Err(it) }
        val sortDirection = parseSortDirection(request).getOrElse { return Err(it) }
        val filter =
            DidFilter(
                method = request.optionalQueryParam("method").getOrElse { return Err(it) },
                alias = request.optionalQueryParam("alias").getOrElse { return Err(it) },
                role = role,
                search = request.optionalQueryParam("search").getOrElse { return Err(it) },
                includeDeactivated = includeDeactivated,
                includeDeleted = includeDeleted,
                page = page,
                size = size,
                sort = sort,
                sortDirection = sortDirection,
                // Pass the raw `?expand=` value through; the service command parses it into a
                // typed Set<DidExpand> and projects ManagedDid → wire-shape Did accordingly. Validation
                // (unknown values → 400) happens in the service-command parser.
                expand = request.optionalQueryParam("expand").getOrElse { return Err(it) },
            )
        val results = serviceCommand.execute(filter).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(ListDidsOutput.serializer(), results)))
    }
}

/**
 * Translates a JSON-Merge-Patch field into the internal [DidUpdateOptions] "" = clear / null =
 * unchanged convention. Only string and `null` JSON values are accepted; numbers, booleans,
 * objects, and arrays are rejected with `ILLEGAL_ARGUMENT_ERROR` (which the dispatcher renders
 * as 400) so the caller fails fast instead of silently coercing.
 *
 *  - key absent        → `Ok(null)`  (unchanged)
 *  - key present, null → `Ok("")`    (clear)
 *  - key present, "x"  → `Ok("x")`   (set)
 *  - key present, non-string non-null → `Err(ILLEGAL_ARGUMENT_ERROR)`
 */
private fun kotlinx.serialization.json.JsonObject.toPatchString(key: String,): IdkResult<String?, IdkError> {
    val element = this[key] ?: return Ok(null)
    if (element is kotlinx.serialization.json.JsonNull) return Ok("")
    val primitive = element as? kotlinx.serialization.json.JsonPrimitive
    if (primitive == null || !primitive.isString) {
        return Err(
            IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Field '$key' must be a string or explicit null; got: $element",
            ),
        )
    }
    return Ok(primitive.content)
}

/**
 * Parses the optional `sort` query parameter into a typed [DidSortField]. Accepted wire
 * values mirror the OpenAPI enum (`createdAt`, `updatedAt`, `did`, `method`, `alias`).
 * Defaults to [DidSortField.CREATED_AT] when omitted.
 */
private fun parseSortField(request: GenericHttpRequest): IdkResult<DidSortField, IdkError> {
    val raw = request.optionalQueryParam("sort").getOrElse { return Err(it) } ?: return Ok(DidSortField.CREATED_AT)
    return when (raw) {
        "createdAt" -> {
            Ok(DidSortField.CREATED_AT)
        }

        "updatedAt" -> {
            Ok(DidSortField.UPDATED_AT)
        }

        "did" -> {
            Ok(DidSortField.DID)
        }

        "method" -> {
            Ok(DidSortField.METHOD)
        }

        "alias" -> {
            Ok(DidSortField.ALIAS)
        }

        else -> {
            Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message =
                        "Invalid value for query parameter 'sort': '$raw'. " +
                            "Expected one of [createdAt, updatedAt, did, method, alias].",
                ),
            )
        }
    }
}

/**
 * Parses the optional `sortDirection` query parameter into a typed [SortDirection]. Accepted
 * wire values mirror the OpenAPI enum (`ASC`, `DESC`). Defaults to [SortDirection.DESC] when
 * omitted (newest-first sort).
 */
private fun parseSortDirection(request: GenericHttpRequest): IdkResult<SortDirection, IdkError> {
    val raw = request.optionalQueryParam("sortDirection").getOrElse { return Err(it) } ?: return Ok(SortDirection.DESC)
    return when (raw.uppercase()) {
        "ASC" -> {
            Ok(SortDirection.ASC)
        }

        "DESC" -> {
            Ok(SortDirection.DESC)
        }

        else -> {
            Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Invalid value for query parameter 'sortDirection': '$raw'. Expected ASC or DESC.",
                ),
            )
        }
    }
}

// ========== POST /api/dids/v1/dids/external — trackExternalDid ==========

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<TrackExternalDidEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class TrackExternalDidEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: TrackExternalDidServiceCommand,
) : HttpEndpointCommandAdapter(
        id = TrackExternalDidEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = TrackExternalDidEndpointCommand.ENDPOINT,
    ),
    TrackExternalDidEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val body = request.requireJsonBody<TrackExternalDidInput>(endpointJson).getOrElse { return Err(it) }
        val tracked = serviceCommand.execute(body).getOrElse { return Err(it) }
        return Ok(jsonResponse(201, endpointJson.encodeToString(Did.serializer(), tracked.toWire(emptySet()))))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetDidEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class GetDidEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: GetDidServiceCommand,
) : HttpEndpointCommandAdapter(
        id = GetDidEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetDidEndpointCommand.ENDPOINT,
    ),
    GetDidEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        // Spec contract: response is the lightweight `Did`. ?expand=document,keys (or all)
        // additively populates the optional projections on the returned object. Pass the raw
        // expand value through; the service command parses + projects.
        val expand = req.optionalQueryParam("expand").getOrElse { return Err(it) }
        val result = serviceCommand.execute(GetDidInput(did = did, expand = expand)).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(Did.serializer(), result)))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UpdateDidEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class UpdateDidEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: UpdateDidServiceCommand,
) : HttpEndpointCommandAdapter(
        id = UpdateDidEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = UpdateDidEndpointCommand.ENDPOINT,
    ),
    UpdateDidEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        // OpenAPI `DidUpdateRequest` is JSON-Merge-Patch shape: `null` clears, omitted = unchanged.
        // The internal [DidUpdateOptions] uses `""` to mean clear and `null` to mean unchanged,
        // so we manually translate per-field to preserve the wire semantics.
        val body = req.requireJsonBody<kotlinx.serialization.json.JsonObject>(endpointJson).getOrElse { return Err(it) }
        val options =
            DidUpdateOptions(
                alias = body.toPatchString("alias").getOrElse { return Err(it) },
                canonicalId = body.toPatchString("canonicalId").getOrElse { return Err(it) },
            )
        val updated = serviceCommand.execute(UpdateDidInput(did = did, options = options)).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(Did.serializer(), updated.toWire(emptySet()))))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ReplaceDidEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class ReplaceDidEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: ReplaceDidServiceCommand,
) : HttpEndpointCommandAdapter(
        id = ReplaceDidEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ReplaceDidEndpointCommand.ENDPOINT,
    ),
    ReplaceDidEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val body = req.requireJsonBody<ReplaceDidBody>(endpointJson).getOrElse { return Err(it) }
        val refreshed = serviceCommand.execute(ReplaceDidInput(did = did, body = body)).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(Did.serializer(), refreshed.toWire(emptySet()))))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeleteDidEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class DeleteDidEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: DeleteDidServiceCommand,
) : HttpEndpointCommandAdapter(
        id = DeleteDidEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = DeleteDidEndpointCommand.ENDPOINT,
    ),
    DeleteDidEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val did = request.withExtractedParams(endpoint.pathPattern).requirePathParam("did").getOrElse { return Err(it) }
        serviceCommand.execute(DidIdInput(did)).getOrElse { return Err(it) }
        return Ok(GenericHttpResponse(statusCode = 204, headers = emptyMap(), body = null))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeactivateDidEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class DeactivateDidEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: DeactivateDidServiceCommand,
) : HttpEndpointCommandAdapter(
        id = DeactivateDidEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = DeactivateDidEndpointCommand.ENDPOINT,
    ),
    DeactivateDidEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        val options =
            if (req.body.isNullOrBlank()) {
                DidDeactivateOptions()
            } else {
                req.requireJsonBody<DeactivateDidRequest>(endpointJson).getOrElse { return Err(it) }.options
                    ?: DidDeactivateOptions()
            }
        val deactivated = serviceCommand.execute(DeactivateDidInput(did = did, options = options)).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(Did.serializer(), deactivated.toWire(emptySet()))))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ResolveDidEndpointCommand>())
@ContributesIntoSet(SessionScope::class, binding = binding<HttpEndpointCommand>())
class ResolveDidEndpointCommandImpl(
    execution: SessionExecution,
    private val serviceCommand: ResolveDidServiceCommand,
) : HttpEndpointCommandAdapter(
        id = ResolveDidEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ResolveDidEndpointCommand.ENDPOINT,
    ),
    ResolveDidEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams(endpoint.pathPattern)
        val did = req.requirePathParam("did").getOrElse { return Err(it) }
        // Use GenericHttpRequest.accept which already handles header-name case (Accept/accept).
        val accept = req.accept.firstOrNull()
        val result = serviceCommand.execute(ResolveDidInput(did = did, accept = accept)).getOrElse { return Err(it) }
        return Ok(jsonResponse(200, endpointJson.encodeToString(DidResolutionResult.serializer(), result)))
    }
}
