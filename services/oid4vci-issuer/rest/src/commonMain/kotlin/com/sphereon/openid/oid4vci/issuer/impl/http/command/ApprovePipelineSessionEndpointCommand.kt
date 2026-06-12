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

import com.sphereon.attribute.flow.AttributeEvidence
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
import com.sphereon.credential.issuance.pipeline.command.ApprovalDecision
import com.sphereon.credential.issuance.pipeline.command.ApprovePipelineSessionArgs
import com.sphereon.credential.issuance.pipeline.command.ApprovePipelineSessionCommand
import com.sphereon.credential.issuance.pipeline.command.ApprovePipelineSessionResult
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Endpoint command for applying an approval-gate decision to a pipeline session.
 *
 * POST /sessions/{correlationId}/approve
 *
 * The correlationId is taken from the URL path. The decision, optional reason, and optional
 * supporting evidence are supplied in the request body. The approver identity is resolved from
 * the [SessionExecution]; it is never supplied as a request argument.
 */
interface ApprovePipelineSessionEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.protocol.approve-pipeline-session"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/backend/sessions/{correlationId}/approve",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "approvePipelineSession",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer"),
                summary = "Apply an approval-gate decision to a pipeline session",
            )
    }
}

@Serializable
internal data class ApprovePipelineSessionRequestBody(
    @SerialName("decision")
    val decision: ApprovalDecision,
    @SerialName("reason")
    val reason: String? = null,
    @SerialName("evidence")
    val evidence: AttributeEvidence? = null,
)

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ApprovePipelineSessionEndpointCommand>())
class ApprovePipelineSessionEndpointCommandImpl(
    execution: SessionExecution,
    private val approvePipelineSessionCommand: ApprovePipelineSessionCommand? = null,
) : HttpEndpointCommandAdapter(
        id = ApprovePipelineSessionEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ApprovePipelineSessionEndpointCommand.ENDPOINT,
    ),
    ApprovePipelineSessionEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        if (approvePipelineSessionCommand == null) {
            return Err(IdkError.COMMAND_DISABLED_ERROR(commandId = ApprovePipelineSessionCommand.COMMAND_ID))
        }

        val request = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val correlationId = request.requirePathParam("correlationId").getOrElse { return Err(it) }

        val body =
            try {
                protocolJson.decodeFromString<ApprovePipelineSessionRequestBody>(request.body ?: "{}")
            } catch (expected: Exception) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Malformed approve-pipeline-session request: ${expected.message}",
                    ),
                )
            }

        return approvePipelineSessionCommand
            .execute(
                ApprovePipelineSessionArgs(
                    correlationId = correlationId,
                    decision = body.decision,
                    reason = body.reason,
                    evidence = body.evidence,
                ),
            ).map { result ->
                GenericHttpResponse(
                    statusCode = 200,
                    headers = JSON_HEADERS,
                    body = protocolJson.encodeToString(ApprovePipelineSessionResult.serializer(), result),
                )
            }
    }
}
