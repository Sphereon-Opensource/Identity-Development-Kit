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
import com.sphereon.credential.issuance.pipeline.command.GetSessionAttributesArgs
import com.sphereon.credential.issuance.pipeline.command.GetSessionAttributesCommand
import com.sphereon.credential.issuance.pipeline.command.GetSessionAttributesResult
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Endpoint command for reading back the accumulated attributes of a pipeline session.
 *
 * GET /sessions/{correlationId}/attributes
 *
 * Returns the session bag's attribute data records as JSON, plus the names (NOT values) of any
 * lookup keys promoted to attribute paths. The correlationId is taken from the URL path.
 * Lookup key values are PII and are never returned in the clear over REST.
 */
interface GetSessionAttributesEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.protocol.get-session-attributes"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/backend/sessions/{correlationId}/attributes",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getSessionAttributes",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer"),
                summary = "Read accumulated attributes for a pipeline session",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetSessionAttributesEndpointCommand>())
class GetSessionAttributesEndpointCommandImpl(
    execution: SessionExecution,
    private val getSessionAttributesCommand: GetSessionAttributesCommand? = null,
) : HttpEndpointCommandAdapter(
        id = GetSessionAttributesEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetSessionAttributesEndpointCommand.ENDPOINT,
    ),
    GetSessionAttributesEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        if (getSessionAttributesCommand == null) {
            return Err(
                IdkError.COMMAND_DISABLED_ERROR(
                    commandId = GetSessionAttributesCommand.COMMAND_ID,
                ),
            )
        }

        val request = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val correlationId = request.requirePathParam("correlationId").getOrElse { return Err(it) }

        return getSessionAttributesCommand
            .execute(
                GetSessionAttributesArgs(
                    correlationId = correlationId,
                ),
            ).map { result ->
                GenericHttpResponse(
                    statusCode = 200,
                    headers = JSON_HEADERS,
                    body = protocolJson.encodeToString(GetSessionAttributesResult.serializer(), result),
                )
            }
    }
}
