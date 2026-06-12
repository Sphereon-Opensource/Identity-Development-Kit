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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
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
import com.sphereon.credential.issuance.pipeline.command.FailPipelineSourceArgs
import com.sphereon.credential.issuance.pipeline.command.FailPipelineSourceCommand
import com.sphereon.credential.issuance.pipeline.command.FailPipelineSourceResult
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Endpoint command for marking a pipeline source's contribution as failed.
 *
 * POST /sessions/{correlationId}/fail
 *
 * Body carries the source id and an optional reason. The session is resolved from the path
 * correlationId; the record is appended to the session bag via [FailPipelineSourceCommand].
 */
interface FailPipelineSourceEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.protocol.fail-pipeline-source"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/backend/sessions/{correlationId}/fail",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "failPipelineSource",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer"),
                summary = "Mark a pipeline source's contribution as failed",
            )
    }
}

@Serializable
internal data class FailPipelineSourceRequestBody(
    @SerialName("source_id")
    val sourceId: String,
    @SerialName("reason")
    val reason: String? = null,
)

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FailPipelineSourceEndpointCommand>())
class FailPipelineSourceEndpointCommandImpl(
    execution: SessionExecution,
    private val failPipelineSourceCommand: FailPipelineSourceCommand? = null,
) : HttpEndpointCommandAdapter(
        id = FailPipelineSourceEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = FailPipelineSourceEndpointCommand.ENDPOINT,
    ),
    FailPipelineSourceEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        if (failPipelineSourceCommand == null) {
            return Err(IdkError.COMMAND_DISABLED_ERROR(commandId = FailPipelineSourceCommand.COMMAND_ID))
        }

        val request = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val correlationId = request.requirePathParam("correlationId").getOrElse { return Err(it) }

        val body =
            try {
                protocolJson.decodeFromString<FailPipelineSourceRequestBody>(request.body ?: "{}")
            } catch (expected: Exception) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Malformed fail-pipeline-source request: ${expected.message}",
                    ),
                )
            }

        return failPipelineSourceCommand
            .execute(
                FailPipelineSourceArgs(
                    correlationId = correlationId,
                    sourceId = body.sourceId,
                    reason = body.reason,
                ),
            ).map { result ->
                GenericHttpResponse(
                    statusCode = 200,
                    headers = JSON_HEADERS,
                    body = protocolJson.encodeToString(FailPipelineSourceResult.serializer(), result),
                )
            }
    }
}
