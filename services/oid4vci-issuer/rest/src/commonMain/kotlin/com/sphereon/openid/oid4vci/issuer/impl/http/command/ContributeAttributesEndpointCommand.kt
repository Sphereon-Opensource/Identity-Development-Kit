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

import com.sphereon.attribute.flow.AttributeRecord
import com.sphereon.attribute.pipeline.LookupKey
import com.sphereon.attribute.pipeline.Oid4vciPipelinePhase
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
 * Endpoint command for pushing attributes and lookup keys into a pipeline session.
 *
 * POST /sessions/{correlationId}/attributes
 *
 * Lets an external system contribute attributes and lookup keys into an active issuance
 * pipeline session at the CREDENTIAL_REQUEST phase. The correlationId is taken from the
 * URL path; the attributes and lookup keys come from the request body.
 */
interface ContributeAttributesEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.protocol.contribute-attributes"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/sessions/{correlationId}/attributes",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "contributeAttributes",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer"),
                summary = "Contribute attributes to a pipeline session",
            )
    }
}

@Serializable
internal data class ContributeAttributesRequestBody(
    @SerialName("attributes")
    val attributes: List<AttributeRecord> = emptyList(),
    @SerialName("lookup_keys")
    val lookupKeys: List<LookupKey> = emptyList(),
)

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ContributeAttributesEndpointCommand>())
class ContributeAttributesEndpointCommandImpl(
    execution: SessionExecution,
    private val contributeAttributesCommand: ContributeAttributesCommand? = null,
) : HttpEndpointCommandAdapter(
        id = ContributeAttributesEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ContributeAttributesEndpointCommand.ENDPOINT,
    ),
    ContributeAttributesEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        if (contributeAttributesCommand == null) {
            return Err(
                IdkError.COMMAND_DISABLED_ERROR(
                    commandId = ContributeAttributesCommand.COMMAND_ID,
                ),
            )
        }

        val request = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val correlationId = request.requirePathParam("correlationId").getOrElse { return Err(it) }

        val body =
            try {
                protocolJson.decodeFromString<ContributeAttributesRequestBody>(request.body ?: "{}")
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed contribute-attributes request: ${expected.message}"))
            }

        return contributeAttributesCommand
            .execute(
                ContributeAttributesArgs(
                    correlationId = correlationId,
                    phase = Oid4vciPipelinePhase.CREDENTIAL_REQUEST,
                    attributes = body.attributes,
                    lookupKeys = body.lookupKeys,
                ),
            ).map { result ->
                GenericHttpResponse(
                    statusCode = 200,
                    headers = JSON_HEADERS,
                    body = protocolJson.encodeToString(ContributeAttributesResult.serializer(), result),
                )
            }
    }
}
