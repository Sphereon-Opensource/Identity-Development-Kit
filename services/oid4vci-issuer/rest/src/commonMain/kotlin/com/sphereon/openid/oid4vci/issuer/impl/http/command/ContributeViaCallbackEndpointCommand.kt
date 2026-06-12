/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.http.command

import com.sphereon.attribute.flow.AttributeRecord
import com.sphereon.attribute.pipeline.LookupKey
import com.sphereon.attribute.pipeline.Oid4vciPipelinePhase
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
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.credential.issuance.pipeline.callback.CallbackCoordinator
import com.sphereon.credential.issuance.pipeline.callback.CallbackTokenClaims
import com.sphereon.credential.issuance.pipeline.callback.CallbackTokenService
import com.sphereon.credential.issuance.pipeline.command.ContributeAttributesArgs
import com.sphereon.credential.issuance.pipeline.command.ContributeAttributesCommand
import com.sphereon.credential.issuance.pipeline.command.ContributeAttributesResult
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Inbound async-callback contribution endpoint for an `AttributeSource` whose binding has
 * `callbackStyle = ASYNC_CALLBACK`.
 *
 * POST /sessions/{correlationId}/callbacks/{callbackToken}
 *
 * The path's `callbackToken` is the opaque capability artefact minted by the issuer when the
 * source was originally dispatched. It is validated against the [CallbackTokenService]; the
 * decoded claims must agree with the path's correlation id (the token-binding cross-check).
 * On success the request body is fed into the pipeline as a [Oid4vciPipelinePhase.DEFERRED]
 * contribution (a callback always arrives after the synchronous `/credential` window).
 *
 * When a [CallbackCoordinator] is on the classpath the endpoint also notifies it after the
 * contribution lands, so a `/credential` waiter holding its response open within the
 * source's `syncWaitWindow` can complete synchronously instead of falling through to a
 * deferred response.
 */
interface ContributeViaCallbackEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.protocol.contribute-via-callback"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/backend/sessions/{correlationId}/callbacks/{callbackToken}",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "contributeViaCallback",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer"),
                summary = "Contribute attributes via an async-callback capability token",
            )
    }
}

@Serializable
internal data class ContributeViaCallbackRequestBody(
    @SerialName("attributes")
    val attributes: List<AttributeRecord> = emptyList(),
    @SerialName("lookup_keys")
    val lookupKeys: List<LookupKey> = emptyList(),
)

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ContributeViaCallbackEndpointCommand>())
class ContributeViaCallbackEndpointCommandImpl(
    execution: SessionExecution,
    private val callbackTokenService: CallbackTokenService? = null,
    private val contributeAttributesCommand: ContributeAttributesCommand? = null,
    private val callbackCoordinator: CallbackCoordinator? = null,
) : HttpEndpointCommandAdapter(
        id = ContributeViaCallbackEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ContributeViaCallbackEndpointCommand.ENDPOINT,
    ),
    ContributeViaCallbackEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val tokenService =
            callbackTokenService
                ?: return Err(IdkError.COMMAND_DISABLED_ERROR(commandId = ContributeViaCallbackEndpointCommand.COMMAND_ID))
        val contributeCommand =
            contributeAttributesCommand
                ?: return Err(IdkError.COMMAND_DISABLED_ERROR(commandId = ContributeAttributesCommand.COMMAND_ID))

        val request = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val resolved = resolveAndAuthorise(request, tokenService).getOrElse { return Err(it) }
        val body = decodeBody(request).getOrElse { return Err(it) }

        val result =
            contributeCommand.execute(
                ContributeAttributesArgs(
                    correlationId = resolved.correlationId,
                    phase = Oid4vciPipelinePhase.DEFERRED,
                    attributes = body.attributes,
                    lookupKeys = body.lookupKeys,
                ),
            )

        if (result.isOk) {
            callbackCoordinator?.notifyContribution(resolved.correlationId, resolved.claims.sourceId)
        }

        return result.map { ok ->
            GenericHttpResponse(
                statusCode = 200,
                headers = JSON_HEADERS,
                body = protocolJson.encodeToString(ContributeAttributesResult.serializer(), ok),
            )
        }
    }

    /** Bundles the path correlation id and the token claims that authorised the callback. */
    private data class ResolvedCallback(
        val correlationId: String,
        val claims: CallbackTokenClaims,
    )

    /** Extract path params, validate the token, and assert the token-binding cross-check. */
    private suspend fun resolveAndAuthorise(
        request: GenericHttpRequest,
        tokenService: CallbackTokenService,
    ): IdkResult<ResolvedCallback, IdkError> {
        val correlationId = request.requirePathParam("correlationId").getOrElse { return Err(it) }
        val callbackToken = request.requirePathParam("callbackToken").getOrElse { return Err(it) }
        val claims = tokenService.validate(callbackToken).getOrElse { return Err(it) }
        if (claims.correlationId != correlationId) {
            return Err(
                IdkError.UNAUTHORIZED_ERROR(
                    message = "Callback token correlationId does not match the request path",
                ),
            )
        }
        return Ok(ResolvedCallback(correlationId = correlationId, claims = claims))
    }

    /** Parse the callback contribution body, returning a structured error on malformed JSON. */
    private fun decodeBody(request: GenericHttpRequest): IdkResult<ContributeViaCallbackRequestBody, IdkError> =
        try {
            Ok(protocolJson.decodeFromString<ContributeViaCallbackRequestBody>(request.body ?: "{}"))
        } catch (expected: Exception) {
            Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Malformed callback contribution request: ${expected.message}",
                ),
            )
        }
}
