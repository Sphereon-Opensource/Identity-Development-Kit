/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.statuslist.hosting.rest.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.response.ResponseBuilder
import com.sphereon.di.session.SessionScope
import com.sphereon.statuslist.StatusListRef
import com.sphereon.statuslist.StatusListToken
import com.sphereon.statuslist.command.GetStatusListTokenCommand
import com.sphereon.statuslist.hosting.rest.StatusListHostingApiConstants
import com.sphereon.statuslist.hosting.rest.StatusListHostingApiConstants.CommandIds
import com.sphereon.statuslist.hosting.rest.http.GetStatusListTokenByCorrelationIdEndpointCommand
import com.sphereon.statuslist.hosting.rest.http.GetStatusListTokenByIdEndpointCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

private const val PARAM_ID = "id"
private const val PARAM_CORRELATION_ID = "correlationId"

/**
 * Shared base for the hosting endpoints: resolves a [StatusListRef] from the URL, delegates to the
 * IDK [GetStatusListTokenCommand], and renders the RAW signed token as the response body with the
 * token's own media type and a `Cache-Control: public, max-age=<ttl>` header. The token string is
 * emitted verbatim (UTF-8 bytes) so the body is the exact URI a verifier resolves — never a JSON
 * envelope.
 */
abstract class AbstractGetStatusListTokenEndpointCommand(
    id: String,
    execution: SessionExecution,
    endpoint: HttpEndpointDescriptor,
    private val service: GetStatusListTokenCommand,
) : HttpEndpointCommandAdapter(
        id = id,
        execution = execution,
        endpoint = endpoint,
    ) {
    /** Build the reference (by id or correlationId) from the matched request. */
    protected abstract fun resolveRef(request: GenericHttpRequest): IdkResult<StatusListRef, IdkError>

    final override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val ref = resolveRef(request).getOrElse { return Err(it) }
        return service.execute(ref).map { token -> token.toRawResponse() }
    }

    private fun StatusListToken.toRawResponse(): GenericHttpResponse {
        val maxAge = ttlSeconds ?: StatusListHostingApiConstants.DEFAULT_CACHE_MAX_AGE_SECONDS
        return ResponseBuilder.bytesResponse(
            // rawBytes() serves the binary CWT (COSE_Sign1) verbatim, or the UTF-8 JWS for JWT/VC-JWT.
            data = rawBytes(),
            contentType = contentType,
            cacheControl = "public, max-age=$maxAge",
        )
    }
}

/** `GET /statuslists/{id}` — resolve the token by technical id. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetStatusListTokenByIdEndpointCommand>())
class GetStatusListTokenByIdEndpointCommandImpl(
    execution: SessionExecution,
    service: GetStatusListTokenCommand,
) : AbstractGetStatusListTokenEndpointCommand(
        id = CommandIds.HTTP_GET_TOKEN_BY_ID,
        execution = execution,
        endpoint = GetStatusListTokenByIdEndpointCommand.ENDPOINT,
        service = service,
    ),
    GetStatusListTokenByIdEndpointCommand {
    override fun resolveRef(request: GenericHttpRequest): IdkResult<StatusListRef, IdkError> {
        val id = request.requirePathParam(PARAM_ID).getOrElse { return Err(it) }
        return Ok(StatusListRef(id = id))
    }
}

/** `GET /statuslists/by/{correlationId}` — resolve the token by business correlation id. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetStatusListTokenByCorrelationIdEndpointCommand>())
class GetStatusListTokenByCorrelationIdEndpointCommandImpl(
    execution: SessionExecution,
    service: GetStatusListTokenCommand,
) : AbstractGetStatusListTokenEndpointCommand(
        id = CommandIds.HTTP_GET_TOKEN_BY_CORRELATION_ID,
        execution = execution,
        endpoint = GetStatusListTokenByCorrelationIdEndpointCommand.ENDPOINT,
        service = service,
    ),
    GetStatusListTokenByCorrelationIdEndpointCommand {
    override fun resolveRef(request: GenericHttpRequest): IdkResult<StatusListRef, IdkError> {
        val correlationId = request.requirePathParam(PARAM_CORRELATION_ID).getOrElse { return Err(it) }
        return Ok(StatusListRef(correlationId = correlationId))
    }
}
