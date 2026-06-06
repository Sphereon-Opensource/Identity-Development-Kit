/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import com.sphereon.credential.issuance.pipeline.command.EvaluateAttributeCompletenessArgs
import com.sphereon.credential.issuance.pipeline.command.EvaluateAttributeCompletenessCommand
import com.sphereon.credential.issuance.pipeline.command.EvaluateAttributeCompletenessResult
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Endpoint command for evaluating attribute completeness of a pipeline session.
 *
 * GET /sessions/{correlationId}/completeness
 *
 * Returns a verdict per credential-claims binding: whether the session's accumulated attribute
 * bag satisfies every mandatory path, and whether deferral is recommended for incomplete bindings.
 * The correlationId is taken from the URL path.
 */
interface EvaluateCompletenessEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.protocol.evaluate-completeness"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/sessions/{correlationId}/completeness",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "evaluateCompleteness",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer"),
                summary = "Evaluate attribute completeness for a pipeline session",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<EvaluateCompletenessEndpointCommand>())
class EvaluateCompletenessEndpointCommandImpl(
    execution: SessionExecution,
    private val evaluateAttributeCompletenessCommand: EvaluateAttributeCompletenessCommand? = null,
) : HttpEndpointCommandAdapter(
        id = EvaluateCompletenessEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = EvaluateCompletenessEndpointCommand.ENDPOINT,
    ),
    EvaluateCompletenessEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        if (evaluateAttributeCompletenessCommand == null) {
            return Err(
                IdkError.COMMAND_DISABLED_ERROR(
                    commandId = EvaluateAttributeCompletenessCommand.COMMAND_ID,
                ),
            )
        }

        val request = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val correlationId = request.requirePathParam("correlationId").getOrElse { return Err(it) }

        return evaluateAttributeCompletenessCommand
            .execute(
                EvaluateAttributeCompletenessArgs(
                    correlationId = correlationId,
                ),
            ).map { result ->
                GenericHttpResponse(
                    statusCode = 200,
                    headers = JSON_HEADERS,
                    body = protocolJson.encodeToString(EvaluateAttributeCompletenessResult.serializer(), result),
                )
            }
    }
}
